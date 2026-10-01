"""Release orchestration and evidence; never invokes publication commands."""

import argparse
import hashlib
import io
import json
import os
import subprocess
import sys
import tarfile
import tempfile
import time
import zipfile
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[2]
VERSION = "0.1.0-alpha.1"
PY_VERSION = "0.1.0a1"
GUI_TASKS = [
    "guiRoundTripTest",
    "rawGuiRoundTripTest",
    "nativeEditGuiRoundTripTest",
    "mappingExportGuiRoundTripTest",
    "mappingImportGuiRoundTripTest",
    "scopedEditGuiRoundTripTest",
    "relatedPropagationGuiRoundTripTest",
    "propagatedEditReplayGuiDiagnosticTest",
    "safeReplayGuiDiagnosticTest",
    "replacementEditGuiRoundTripTest",
    "propagatedEditGuiRoundTripTest",
    "localRenameGuiDiagnosticTest",
]


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def head():
    return subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True
    ).strip()


def source_hash():
    # Covers ignored generated build artifacts' inputs, including uncommitted changes.
    paths = subprocess.check_output(
        ["git", "ls-files", "-co", "--exclude-standard"], cwd=ROOT, text=True
    ).splitlines()
    digest = hashlib.sha256()
    for name in sorted(set(paths)):
        if name.startswith("docs/pr-22-") or name == "docs/changelog.md":
            continue  # Evidence is separately identified; cannot hash a manifest into itself.
        path = ROOT / name
        if path.is_file():
            digest.update(name.encode() + b"\0" + path.read_bytes() + b"\0")
    return digest.hexdigest()


def validate_evidence_identity(record):
    """Accept current source or a descendant containing only committed evidence.

    A report cannot embed its own Git commit hash. Its immutable qualification
    head therefore remains the code head; any later evidence-only commit must
    preserve the exact source fingerprint and ancestry. Source changes fail closed.
    """
    assert record["source_sha256"] == source_hash(), "Superseded-source evidence"
    if record["head"] == head():
        return
    ancestor = subprocess.run(
        ["git", "merge-base", "--is-ancestor", record["head"], "HEAD"],
        cwd=ROOT,
        capture_output=True,
    )
    assert ancestor.returncode == 0, "Qualification head is not an ancestor"
    changes = subprocess.check_output(
        ["git", "diff", "--name-only", record["head"], "HEAD"], cwd=ROOT, text=True
    ).splitlines()
    assert all(
        name.startswith("docs/pr-22-") or name == "docs/changelog.md"
        for name in changes
    ), "Only evidence commits may follow the qualification head"


def xml_counts(directory):
    total = dict(tests=0, failures=0, errors=0, skipped=0)
    files = sorted(directory.glob("*.xml"))
    assert files, f"No test results: {directory}"
    for path in files:
        node = ElementTree.parse(path).getroot()
        for key in total:
            total[key] += int(node.attrib.get(key, 0))
    return {
        "suites": len(files),
        **total,
        "passed": total["tests"]
        - total["skipped"]
        - total["failures"]
        - total["errors"],
    }


def run(command, log, cwd=ROOT, env=None):
    print("RUN", " ".join(map(str, command)), flush=True)
    started = time.monotonic()
    with log.open("w") as stream:
        result = subprocess.run(
            list(map(str, command)),
            cwd=cwd,
            env={**os.environ, **(env or {})},
            stdout=stream,
            stderr=subprocess.STDOUT,
        )
    if result.returncode:
        print(log.read_text()[-8000:], flush=True)
        raise RuntimeError(f"Gate failed ({result.returncode}): {log.name}")
    return {
        "command": [str(v).replace(str(ROOT), "<repo>") for v in command],
        "seconds": round(time.monotonic() - started, 2),
        "status": "PASS",
    }


def artifact(path):
    # A TAR containing JARs can also pass is_zipfile(): choose the outer format.
    if path.suffix in (".zip", ".whl"):
        with zipfile.ZipFile(path) as archive:
            files = [p for p in archive.namelist() if not p.endswith("/")]
    else:
        with tarfile.open(path) as archive:
            files = [p.name for p in archive.getmembers() if p.isfile()]
    return {
        "filename": path.name,
        "bytes": path.stat().st_size,
        "sha256": sha(path),
        "file_count": len(files),
    }


def extract(path, directory):
    if path.suffix == ".zip":
        with zipfile.ZipFile(path) as archive:
            archive.extractall(directory)
            for entry in archive.infolist():
                target = directory / entry.filename
                if target.is_file():
                    target.chmod((entry.external_attr >> 16) & 0o777 or 0o644)
    else:
        with tarfile.open(path) as archive:
            archive.extractall(directory, filter="data")
    launcher = directory / f"libjadx-{VERSION}/bin/libjadx"
    assert launcher.is_file() and os.access(launcher, os.X_OK)
    return launcher


def audit_java(path):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        assert not any(
            "jadx-gui" in p or "/.git/" in p or "/tests/" in p or "/.gradle/" in p
            for p in names
        )
        for name in (
            "JADX-LICENSE",
            "JADX-NOTICE",
            "MAPPING-IO-LICENSE",
            "MAPPING-IO-NOTICE",
            "RE2J-LICENSE",
        ):
            assert any(p.endswith("/licenses/" + name) for p in names), name
        assert any(p.endswith("/bin/libjadx.bat") for p in names)
        for plugin in (
            "jadx-core",
            "jadx-dex-input",
            "jadx-java-input",
            "jadx-smali-input",
            "jadx-rename-mappings",
            "jadx-analysis",
            "jadx-java-convert",
            "jadx-kotlin-metadata",
            "jadx-kotlin-source-debug-extension",
            "jadx-xapk-input",
            "jadx-aab-input",
            "jadx-apkm-input",
            "jadx-apks-input",
        ):
            assert any(p.endswith(f"/{plugin}-1.5.6.jar") for p in names), plugin
        bat = next(p for p in names if p.endswith("/bin/libjadx.bat"))
        assert "dev.libjadx.app.LibJadxMain" in archive.read(bat).decode()
        own = next(p for p in names if p.endswith(f"/libjadx-{VERSION}.jar"))
        with zipfile.ZipFile(io.BytesIO(archive.read(own))) as jar:
            assert not any(
                "/probes/" in p or p.endswith("Test.class") for p in jar.namelist()
            )
            assert (
                jar.read("libjadx-build.properties").decode().strip()
                == "version=" + VERSION
            )


def core(uv, output):
    # Candidate hashes must describe committed artifact inputs.
    dirty = subprocess.check_output(
        ["git", "status", "--porcelain"], cwd=ROOT, text=True
    )
    assert not dirty.strip(), "Commit the candidate source before final qualification"
    evidence = {
        "head": head(),
        "source_sha256": source_hash(),
        "status": "NOT QUALIFIED",
        "commands": [],
    }

    def gate(command, name, **kwargs):
        record = run(command, output / f"{name}.log", **kwargs)
        evidence["commands"].append(record)

    try:
        gate(
            ["./gradlew", "clean", "check", "--offline", "--rerun-tasks"], "java-check"
        )
        evidence["java"] = xml_counts(ROOT / "build/test-results/test")
        gate(
            [
                "./gradlew",
                "installDist",
                "distZip",
                "distTar",
                "--offline",
                "--rerun-tasks",
            ],
            "java-dist",
        )
        gate(
            [uv, "sync", "--frozen", "--all-groups", "--offline"],
            "python-sync",
            cwd=ROOT / "python",
        )
        py = ROOT / "python/.venv/bin/python"
        gate([py, ROOT / "tests/release/test_evidence.py"], "evidence-regressions")
        for name in (
            "edit-contract",
            "mapping-export-contract",
            "mapping-import-contract",
            "search-contract",
            "openapi",
        ):
            gate([py, ROOT / f"tests/validate-{name}.py"], name)
        for command, name in (
            (
                [
                    uv,
                    "run",
                    "--frozen",
                    "--offline",
                    "python",
                    "scripts/check_generated.py",
                ],
                "generator",
            ),
            (
                [uv, "run", "--frozen", "--offline", "ruff", "format", "--check", "."],
                "format",
            ),
            ([uv, "run", "--frozen", "--offline", "ruff", "check", "."], "lint"),
            ([uv, "run", "--frozen", "--offline", "mypy", "src/libjadx"], "types"),
        ):
            gate(command, name, cwd=ROOT / "python")
        gate([uv, "build", "--offline"], "python-build", cwd=ROOT / "python")
        archives = [
            ROOT / f"build/distributions/libjadx-{VERSION}.{ext}"
            for ext in ("zip", "tar")
        ]
        archives += [
            ROOT / f"python/dist/libjadx-{PY_VERSION}-py3-none-any.whl",
            ROOT / f"python/dist/libjadx-{PY_VERSION}.tar.gz",
        ]
        initial = {path.name: artifact(path) for path in archives}
        audit_java(archives[0])
        gate([py, ROOT / "python/scripts/artifact_manifest.py"], "python-audit")
        # Rebuild after deleting only owned candidate outputs; retain dependency/tool caches.
        for path in archives:
            path.unlink()
        gate(
            ["./gradlew", "distZip", "distTar", "--offline", "--rerun-tasks"],
            "java-repeat",
        )
        gate([uv, "build", "--offline"], "python-repeat", cwd=ROOT / "python")
        assert initial == {p.name: artifact(p) for p in archives}, (
            "Candidate archives are not byte-identical"
        )
        evidence["artifacts"] = initial
        evidence["reproducible"] = True
        python_audit = json.loads((output / "python-audit.log").read_text())
        evidence["metadata"] = {
            "java_version": VERSION,
            "python_version": PY_VERSION,
            "api_version": "0.1.0-experimental",
            "jadx_version": "1.5.6",
            "jadx_source": "28ff15e4ae69950aebea110a13e5ab895d234dfc",
            "jdk_version": subprocess.check_output(
                ["java", "-version"], stderr=subprocess.STDOUT, text=True
            ).strip(),
            "gradle_version": "8.14.3",
            "gradle_lock_sha256": sha(ROOT / "gradle.lockfile"),
            "uv_version": subprocess.check_output([uv, "--version"], text=True).strip(),
            "python_build_audit": python_audit,
        }
        with tempfile.TemporaryDirectory(prefix="libjadx-release-") as temp:
            root = Path(temp)
            requirements = root / "requirements.txt"
            gate(
                [
                    uv,
                    "export",
                    "--project",
                    ROOT / "python",
                    "--frozen",
                    "--all-groups",
                    "--no-emit-project",
                    "--no-hashes",
                    "--output-file",
                    requirements,
                ],
                "requirements",
            )
            launcher = extract(archives[0], root / "zip")
            for version in ("3.11", "3.14"):
                envroot = root / ("python-" + version)
                gate(
                    [uv, "venv", "--python", version, envroot, "--offline"],
                    "venv-" + version,
                )
                interpreter = envroot / "bin/python"
                gate(
                    [
                        uv,
                        "pip",
                        "install",
                        "--offline",
                        "--python",
                        interpreter,
                        "-r",
                        requirements,
                        archives[2],
                    ],
                    "install-" + version,
                )

            def installed_tests(version):
                interpreter = root / ("python-" + version) / "bin/python"

                gate(
                    [
                        interpreter,
                        "-m",
                        "pytest",
                        "-q",
                        ROOT / "python/tests/unit",
                        ROOT / "python/tests/integration",
                        ROOT / "python/tests/release",
                        "--junitxml=" + str(output / ("python-" + version + ".xml")),
                    ],
                    "tests-" + version,
                    cwd=root,
                    env={
                        "LIBJADX_EXPECT_INSTALLED": "1",
                        "LIBJADX_DISTRIBUTION": str(launcher),
                        "LIBJADX_RELEASE_EVIDENCE": str(output / ("matrix-" + version)),
                    },
                )
                node = ElementTree.parse(
                    output / ("python-" + version + ".xml")
                ).getroot()
                evidence["python_" + version] = {
                    key: int(node[0].attrib[key])
                    for key in ("tests", "failures", "errors", "skipped")
                }
                assert evidence["python_" + version]["skipped"] == 0
                observations = json.loads(
                    (output / ("matrix-" + version) / "observations.json").read_text()
                )["records"]
                assert observations and all(
                    row["exit_code"] in (0, 143) for row in observations
                ), "Mandatory cleanup required a forced kill"
                evidence["python_" + version]["java_processes"] = len(observations)
                evidence["python_" + version]["max_observed_rss_kib"] = max(
                    row["observed_rss_kib"] or 0 for row in observations
                )
                evidence["python_" + version]["max_ready_seconds"] = max(
                    row["ready_seconds"] or 0 for row in observations
                )
                for family, filename in (
                    ("route", "routes.json"),
                    ("error", "errors.json"),
                    ("capabilities", "capabilities.json"),
                ):
                    validator = (
                        "validate-release-capabilities.py"
                        if family == "capabilities"
                        else f"validate-release-{family}-matrix.py"
                    )
                    gate(
                        [
                            py,
                            ROOT / "tests" / validator,
                            output / ("matrix-" + version) / filename,
                        ],
                        family + "-" + version,
                    )

            # Separate processes/venvs/ports/native copies; fixtures are read-only.
            with ThreadPoolExecutor(max_workers=2) as executor:
                list(executor.map(installed_tests, ("3.11", "3.14")))
            tar_launcher = extract(archives[1], root / "tar")
            gate(
                [
                    interpreter,
                    "-m",
                    "pytest",
                    "-q",
                    ROOT / "python/tests/release/test_routes.py",
                    "-k",
                    "raw_http or generated_async",
                    "--junitxml=" + str(output / "tar-smoke.xml"),
                ],
                "tar-smoke",
                cwd=root,
                env={
                    "LIBJADX_EXPECT_INSTALLED": "1",
                    "LIBJADX_DISTRIBUTION": str(tar_launcher),
                },
            )
        gate(["git", "diff", "--check"], "whitespace")
        assert (
            evidence["head"] == head() and evidence["source_sha256"] == source_hash()
        ), "Source changed during qualification"
        evidence["core_status"] = "PASS"
    except Exception as error:
        evidence["core_status"] = "FAIL"
        evidence["blocker"] = str(error)
        raise
    finally:
        (output / "core.json").write_text(json.dumps(evidence, indent=2) + "\n")


def gui(output):
    core_record = json.loads((output / "core.json").read_text())
    assert core_record["core_status"] == "PASS", "Run core before gui"
    validate_evidence_identity(core_record)
    assert (ROOT / "build/release-fixtures/qualification.jar").is_file(), (
        "Fresh clean-check fixtures are missing"
    )
    executable = os.environ.get("JADX_GUI")
    assert executable and Path(executable).is_file(), "Set JADX_GUI to matching 1.5.6"
    version = subprocess.check_output([executable, "--version"], text=True).strip()
    assert version == "1.5.6", version
    # Remove previous outputs; count actual fresh Save As outputs from this invocation.
    before = set(ROOT.glob("build/**/gui-resaved.jadx"))
    for path in before:
        path.unlink()
    record = run(
        [
            "./gradlew",
            "releaseGuiQualification",
            "--offline",
            "--rerun-tasks",
            "--no-parallel",
            "-x",
            "test",
        ],
        output / "gui.log",
    )
    rows = {task: xml_counts(ROOT / "build/test-results" / task) for task in GUI_TASKS}
    assert all(
        not row["failures"] and not row["errors"] and not row["skipped"]
        for row in rows.values()
    ), rows
    projects = sorted(ROOT.glob("build/**/gui-resaved.jadx"))
    actual_saves = [
        line
        for line in (output / "gui.log").read_text().splitlines()
        if line.startswith("RELEASE_GUI_SAVE_AS ")
    ]
    assert len(projects) == len(actual_saves) == 27, (len(projects), len(actual_saves))
    data = {
        "head": head(),
        "source_sha256": source_hash(),
        "status": "PASS",
        "version": version,
        "display": "xvfb-run -a -screen 0 1280x1024x24",
        "gui_launcher_sha256": sha(Path(executable)),
        "upstream_distribution_sha256": sha(Path(os.environ["JADX_GUI_ARCHIVE"]))
        if os.environ.get("JADX_GUI_ARCHIVE")
        else None,
        "command": record,
        "tasks": rows,
        "save_as_count": len(projects),
        "actual_gui_process_count": len(actual_saves),
        "save_as_log": [line.replace(str(ROOT), "<repo>") for line in actual_saves],
        "projects": {str(p.relative_to(ROOT)): sha(p) for p in projects},
    }
    (output / "gui.json").write_text(json.dumps(data, indent=2) + "\n")


def result(output):
    records = [
        json.loads((output / name).read_text()) for name in ("core.json", "gui.json")
    ]
    for record in records:
        validate_evidence_identity(record)
    assert records[0]["core_status"] == records[1]["status"] == "PASS"
    data = {
        "head": head(),
        "source_sha256": source_hash(),
        "status": "QUALIFIED",
        "qualification_heads": {"core": records[0]["head"], "gui": records[1]["head"]},
        "publication": "NOT PUBLISHED",
    }
    (output / "result.json").write_text(json.dumps(data, indent=2) + "\n")
    print("QUALIFIED locally; NOT PUBLISHED", flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "stage", choices=("core", "gui", "result", "all"), nargs="?", default="all"
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path(tempfile.gettempdir()) / "libjadx-release-evidence",
    )
    args = parser.parse_args()
    # clean removes build/, so keep logs/results outside it until the check returns.
    output = args.output.resolve()
    if output.is_relative_to(ROOT / "build"):
        raise SystemExit(
            "Use --output <tmp>/release-evidence (Gradle clean removes build/)"
        )
    output.mkdir(parents=True, exist_ok=True)
    if args.stage != "result":
        (output / "result.json").unlink(missing_ok=True)
    if args.stage in ("gui", "all"):
        (output / "gui.json").unlink(missing_ok=True)
    try:
        if args.stage in ("core", "all"):
            core(os.environ.get("UV_BIN", "uv"), output)
        if args.stage in ("gui", "all"):
            gui(output)
        if args.stage in ("result", "all"):
            result(output)
        elif args.stage == "core":
            print("Core qualification PASS; GUI outstanding; NOT QUALIFIED", flush=True)
    except Exception as error:
        print("NOT QUALIFIED:", error, flush=True)
        sys.exit(1)
