"""Fail on stale, missing, or mock-only stable error-code evidence."""

import json
import subprocess
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[1]
spec = yaml.safe_load((ROOT / "openapi/openapi.yaml").read_text())
codes = set(spec["components"]["schemas"]["ApiError"]["properties"]["code"]["enum"])
data = json.loads(
    Path(
        sys.argv[1] if len(sys.argv) > 1 else ROOT / "docs/pr-22-error-matrix.json"
    ).read_text()
)
assert (
    data["head"]
    == subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True
    ).strip()
), "Stale release evidence"
assert set(data["records"]) == codes
for code, row in data["records"].items():
    assert row["httpStatuses"] and row["scenarios"]
    for surface in (
        "raw_http",
        "generated_sync",
        "generated_async",
        "high_level_sync",
        "high_level_async",
    ):
        assert row["surfaces"].get(surface) is True, (code, surface)
        scenarios = [s for s in row["scenarios"] if s["surface"] == surface]
        assert scenarios and all(
            s["wire"]["error"]["code"] == code and s["wire"]["error"]["requestId"]
            for s in scenarios
        )
        if surface.startswith("high"):
            assert all(s["exception"] for s in scenarios)
print(f"{len(codes)}/{len(codes)} stable error codes qualified on all five surfaces")
