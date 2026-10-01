"""Real installed Java distribution; no GUI and no SDK process management."""

import os
import queue
import re
import shutil
import subprocess
import threading
from contextlib import contextmanager
from pathlib import Path

import pytest

from libjadx import Client

ROOT = Path(__file__).resolve().parents[3]


@pytest.fixture
def service(tmp_path):
    executable = Path(
        os.environ.get(
            "LIBJADX_DISTRIBUTION", ROOT / "build/install/libjadx/bin/libjadx"
        )
    )
    assert executable.exists(), "Run ./gradlew installDist --offline first"
    source = ROOT / "tests/fixtures/native-project/sample.jar"
    shutil.copy2(source, tmp_path / "sample.jar")

    @contextmanager
    def start(project=None):
        lines = []
        addresses = queue.Queue()
        process = subprocess.Popen(
            [
                str(executable),
                "--project" if project else "--input",
                str(project or (tmp_path / "sample.jar")),
                "--allowed-root",
                str(tmp_path),
                "--port",
                "0",
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            env={**os.environ, "XDG_STATE_HOME": str(tmp_path / "state")},
        )

        def read():
            for line in process.stdout:
                lines.append(line)
                if match := re.search(
                    r"LibJadx listening on (http://127\.0\.0\.1:\d+)", line
                ):
                    addresses.put(match[1])

        reader = threading.Thread(target=read, daemon=True)
        reader.start()
        url = None
        try:
            url = addresses.get(timeout=30)
            with Client(url) as client:
                client.wait_ready(timeout=30)
            yield url, tmp_path
        except Exception as exc:
            exc.add_note("Service log:\n" + "".join(lines))
            raise
        finally:
            if process.poll() is None and url is not None:
                try:
                    with Client(url, timeout=2) as client:
                        client.shutdown("discard")
                except Exception:
                    pass
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)
            reader.join(timeout=2)
            process.stdout.close()

    return start
