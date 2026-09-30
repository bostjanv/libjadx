"""Fail on unrepresented, stale or unqualified contract operations."""

import json
import subprocess
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]
spec = yaml.safe_load((ROOT / "openapi/openapi.yaml").read_text())
operations = {
    op["operationId"]
    for methods in spec["paths"].values()
    for op in methods.values()
    if isinstance(op, dict) and "operationId" in op
}
data = json.loads(
    Path(
        sys.argv[1] if len(sys.argv) > 1 else ROOT / "docs/pr-22-route-matrix.json"
    ).read_text()
)
assert (
    data["head"]
    == subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True
    ).strip()
), "Stale release evidence"
assert set(data["records"]) == operations
for operation, row in data["records"].items():
    for surface in (
        "raw_http",
        "generated_sync",
        "generated_async",
        "high_level_sync",
        "high_level_async",
    ):
        assert row["surfaces"].get(surface) and row["positive_surfaces"].get(surface), (
            operation,
            surface,
        )
    for key in (
        "positive_case",
        "negative_case",
        "provenance_checked",
        "error_checked",
    ):
        assert row[key] is True, (operation, key)
print(f"{len(operations)}/{len(operations)} operations qualified on all five surfaces")
