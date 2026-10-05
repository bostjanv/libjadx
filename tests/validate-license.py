"""Offline first-party license and separate dependency-notice gate.

SPDX-License-Identifier: Apache-2.0
"""

import hashlib
import re
import runpy
import tomllib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
# Canonical Apache 2.0 text, verified from the existing upstream license bundle.
APACHE_SHA256 = "b40930bbcf80744c86c46a12bc9da056641d722716c378f5659b9e555ef833e1"
JAVA_NOTICES = (
    "JADX-LICENSE",
    "JADX-NOTICE",
    "MAPPING-IO-LICENSE",
    "MAPPING-IO-NOTICE",
    "RE2J-LICENSE",
)
PYTHON_NOTICES = ("OPENAPI-PYTHON-CLIENT-LICENSE", "THIRD_PARTY.md")


def validate(root):
    license_bytes = (root / "LICENSE").read_bytes()
    assert hashlib.sha256(license_bytes).hexdigest() == APACHE_SHA256, (
        "Noncanonical Apache-2.0 LICENSE"
    )
    assert (root / "python/LICENSE").read_bytes() == license_bytes, (
        "Python LICENSE differs"
    )
    metadata = tomllib.loads((root / "python/pyproject.toml").read_text())
    assert metadata["project"]["license"] == "Apache-2.0"
    assert set(metadata["project"]["license-files"]) == {"LICENSE", *PYTHON_NOTICES}
    for name in JAVA_NOTICES:
        assert (root / "licenses" / name).read_bytes(), name
    for name in PYTHON_NOTICES:
        assert (root / "python" / name).read_bytes(), name
    documentation = runpy.run_path(str(root / "tests/validate-documentation.py"))
    current = [root / name for name in documentation["CURRENT_DOCS"]]
    _, errors = documentation["audit"](root, current)
    assert not errors, "\n".join(errors)
    # First-party attribution is separate from dependency inventories. Negative
    # wording such as 'not a dependency' and historical reports remain valid.
    for name in (
        "build.gradle.kts",
        "gradle.lockfile",
        "python/pyproject.toml",
        "python/uv.lock",
        "python/THIRD_PARTY.md",
    ):
        assert "libghidra" not in (root / name).read_text().lower(), (
            f"libghidra in dependency inventory: {name}"
        )
    for path in (root / "licenses").iterdir():
        assert "libghidra" not in path.name.lower(), "libghidra bundled notice"
    for path in current:
        text = " ".join(path.read_text().lower().split())
        assert not re.search(
            r"libghidra (?:is (?:a |an )?|as (?:a |an )?)(?:bundled|runtime) dependency",
            text,
        ), f"libghidra dependency claim: {path.relative_to(root)}"


if __name__ == "__main__":
    validate(ROOT)
    print("Apache-2.0 license bytes, metadata, current docs and separate notices valid")
