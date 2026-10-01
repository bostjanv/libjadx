"""Inspect local artifacts and produce evidence; no network or publication."""

import hashlib
import json
import tarfile
import zipfile
from pathlib import Path

PYTHON = Path(__file__).resolve().parents[1]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def generated_fingerprint() -> str:
    digest = hashlib.sha256()
    root = PYTHON / "src/libjadx/_generated"
    for path in sorted(root.rglob("*.py")):
        digest.update(
            str(path.relative_to(root)).encode() + b"\0" + path.read_bytes() + b"\0"
        )
    return digest.hexdigest()


def manifest() -> dict[str, object]:
    wheel = PYTHON / "dist/libjadx-0.1.0a1-py3-none-any.whl"
    sdist = PYTHON / "dist/libjadx-0.1.0a1.tar.gz"
    with zipfile.ZipFile(wheel) as archive:
        wheel_files = sorted(archive.namelist())
        assert "libjadx/py.typed" in wheel_files
        assert all(
            not p.endswith((".jar", ".class", ".pyc")) and "/tests/" not in p
            for p in wheel_files
        )
        metadata_path = next(p for p in wheel_files if p.endswith("/METADATA"))
        metadata = archive.read(metadata_path).decode()
        runtime = [
            line for line in metadata.splitlines() if line.startswith("Requires-Dist:")
        ]
        assert len(runtime) == 3, runtime
        assert "License-Expression: LicenseRef-Proprietary" in metadata
    with tarfile.open(sdist) as archive:
        sdist_files = sorted(m.name for m in archive.getmembers() if m.isfile())
    return {
        "openapi_sha256": sha256(PYTHON.parent / "openapi/openapi.yaml"),
        "generator_config_sha256": sha256(PYTHON / "generator.yaml"),
        "lockfile_sha256": sha256(PYTHON / "uv.lock"),
        "generated_tree_sha256": generated_fingerprint(),
        "generated_tree_algorithm": "SHA256 sorted POSIX relative *.py path + NUL + bytes + NUL",
        "wheel": {
            "filename": wheel.name,
            "sha256": sha256(wheel),
            "files": wheel_files,
            "runtime": runtime,
        },
        "sdist": {
            "filename": sdist.name,
            "sha256": sha256(sdist),
            "files": sdist_files,
        },
    }


if __name__ == "__main__":
    print(json.dumps(manifest(), indent=2))
