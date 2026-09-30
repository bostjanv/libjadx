"""Generate exclusively from the repository-reviewed OpenAPI file."""

import shutil
import subprocess
import sys
import tempfile
from importlib.metadata import version
from pathlib import Path

PYTHON = Path(__file__).resolve().parents[1]
ROOT = PYTHON.parent
TARGET = PYTHON / "src/libjadx/_generated"


def generate(destination: Path) -> None:
    if version("openapi-python-client") != "0.29.1":
        raise SystemExit("Generation requires openapi-python-client==0.29.1")
    if version("ruff") != "0.16.9":
        raise SystemExit("Generation requires ruff==0.16.9")
    subprocess.run(
        [
            sys.executable,
            "-m",
            "openapi_python_client",
            "generate",
            "--path",
            str(ROOT / "openapi/openapi.yaml"),
            "--config",
            str(PYTHON / "generator.yaml"),
            "--meta",
            "none",
            "--output-path",
            str(destination),
        ],
        check=True,
        cwd=PYTHON,
    )
    # Tool cache is not generated source.
    shutil.rmtree(destination / ".ruff_cache", ignore_errors=True)


if __name__ == "__main__":
    with tempfile.TemporaryDirectory() as directory:
        output = Path(directory) / "_generated"
        generate(output)
        if TARGET.exists():
            shutil.rmtree(TARGET)
        shutil.copytree(output, TARGET)
