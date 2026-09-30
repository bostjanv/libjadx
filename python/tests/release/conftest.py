"""Extracted-distribution process control, exclusively test infrastructure."""

import os
import queue
import re
import shutil
import subprocess
import threading
import time
from contextlib import contextmanager
from pathlib import Path

import httpx
import pytest

# pytest adds the test directory, not SDK source, to sys.path.
from release_support import PROCESS_OBSERVATIONS, ROOT, write_evidence

from libjadx import Client


class Service:
    def __init__(
        self,
        root,
        input_path,
        project,
        holds,
        faults,
        hooks,
        cli_args=None,
        env_overrides=None,
    ):
        self.started = time.monotonic()
        self.root = root
        self.hooks = root / "hooks"
        self.hooks.mkdir(exist_ok=True)
        for point in holds:
            self.hold(point)
        for point in faults:
            (self.hooks / f"{point}.fail").touch()
        executable = Path(os.environ["LIBJADX_DISTRIBUTION"]).resolve()
        assert executable.is_file(), executable
        assert "build/install" not in str(executable), (
            "Release tests require EXTRACTED candidate"
        )
        env = {k: v for k, v in os.environ.items() if not k.startswith("LIBJADX_")}
        env["XDG_STATE_HOME"] = str(root / "state")
        env["JAVA_TOOL_OPTIONS"] = (
            f'"-Dlibjadx.releaseTestDir={self.hooks}"' if hooks else ""
        )
        env.update(env_overrides or {})
        self.lines = []
        addresses = queue.Queue()
        self.process = subprocess.Popen(
            [str(executable)]
            + (
                cli_args
                if cli_args is not None
                else [
                    "--project" if project else "--input",
                    str(input_path),
                    "--allowed-root",
                    str(root),
                    "--port",
                    "0",
                ]
            ),
            cwd=root,
            env=env,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
        )

        def read():
            for line in self.process.stdout:
                self.lines.append(line)
                if match := re.search(
                    r"LibJadx listening on (http://127\.0\.0\.1:\d+)", line
                ):
                    addresses.put(match[1])

        self.reader = threading.Thread(target=read, daemon=True)
        self.reader.start()
        try:
            self.url = addresses.get(timeout=30)
        except queue.Empty:
            self.close()
            raise AssertionError("No listener: " + "".join(self.lines)) from None
        self.http = httpx.Client(base_url=self.url, trust_env=False, timeout=30)

    def hold(self, point):
        (self.hooks / f"{point}.entered").unlink(missing_ok=True)
        (self.hooks / f"{point}.hold").touch()

    def release(self, point):
        (self.hooks / f"{point}.hold").unlink(missing_ok=True)

    def entered(self, point):
        deadline = time.monotonic() + 30
        while not (self.hooks / f"{point}.entered").exists():
            assert self.process.poll() is None, "".join(self.lines)
            assert time.monotonic() < deadline, f"Gate {point} was not reached"
            time.sleep(0.01)

    def ready(self):
        with Client(self.url) as client:
            client.wait_ready(timeout=30)
        self.ready_seconds = round(time.monotonic() - self.started, 3)

    def close(self):
        observed_rss_kib = None
        proc_status = Path(f"/proc/{self.process.pid}/status")
        if proc_status.is_file():
            try:
                for line in proc_status.read_text().splitlines():
                    if line.startswith("VmRSS:"):
                        observed_rss_kib = int(line.split()[1])
            except FileNotFoundError:
                pass

        for path in self.hooks.glob("*.hold"):
            path.unlink(missing_ok=True)
        if hasattr(self, "http"):
            if self.process.poll() is None:
                try:
                    self.http.post("/api/v1/shutdown", json={"policy": "discard"})
                except httpx.HTTPError:
                    pass
            self.http.close()
        try:
            self.process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            self.process.kill()
            self.process.wait(timeout=5)
        self.reader.join(timeout=2)
        self.process.stdout.close()
        PROCESS_OBSERVATIONS.append(
            {
                "ready_seconds": getattr(self, "ready_seconds", None),
                "lifetime_seconds": round(time.monotonic() - self.started, 3),
                "observed_rss_kib": observed_rss_kib,
                "exit_code": self.process.returncode,
            }
        )


@pytest.fixture
def factory(tmp_path):
    assert os.environ.get("LIBJADX_DISTRIBUTION"), (
        "Use tests/release/validate-release-candidate.sh (no mandatory test skips)"
    )
    assert os.environ.get("LIBJADX_EXPECT_INSTALLED") == "1"
    import libjadx

    assert "site-packages" in libjadx.__file__, libjadx.__file__
    counter = 0

    @contextmanager
    def start(
        *,
        kind="jar",
        holds=(),
        faults=(),
        ready=True,
        root=None,
        hooks=True,
        cli_args=None,
        env_overrides=None,
        saved_name="saved.jadx",
    ):
        nonlocal counter
        counter += 1
        work = root or tmp_path / f"owned space Ž-{counter}"
        work.mkdir(exist_ok=True)
        source = {
            "jar": ROOT / "build/release-fixtures/qualification.jar",
            "long": ROOT / "build/release-fixtures/workload.jar",
            "dex": ROOT / "build/release-fixtures/owned.dex",
            "catch": ROOT / "build/release-fixtures/unused-catch.dex",
            "class": ROOT / "build/release-fixtures/small/classes/probe/Unicode.class",
        }
        if kind == "native":
            for file in (ROOT / "tests/fixtures/native-project").iterdir():
                if file.suffix != ".java":
                    shutil.copy2(file, work / file.name)
            path = work / "sample.jar.jadx"
        elif kind == "saved":
            path = work / saved_name
        else:
            path = work / source[kind].name
            shutil.copy2(source[kind], path)
        service = Service(
            work,
            path,
            kind in ("native", "saved"),
            holds,
            faults,
            hooks,
            cli_args,
            env_overrides,
        )
        try:
            if ready:
                service.ready()
            yield service
        except Exception as error:
            error.add_note("Java log:\n" + "".join(service.lines))
            raise
        finally:
            service.close()

    return start


def pytest_sessionfinish(session, exitstatus):
    write_evidence()
