"""Fail on stale, missing, or mock-only stable error-code evidence."""

import json
import re
import sys
from pathlib import Path

import yaml
from release.qualify import validate_evidence_identity

ROOT = Path(__file__).resolve().parents[1]
spec = yaml.safe_load((ROOT / "openapi/openapi.yaml").read_text())
codes = set(spec["components"]["schemas"]["ApiError"]["properties"]["code"]["enum"])
data = json.loads(
    Path(
        sys.argv[1]
        if len(sys.argv) > 1
        else ROOT / "docs/release-evidence/0.1.0-alpha.1/error-matrix.json"
    ).read_text()
)
validate_evidence_identity(data)
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
        for scenario in scenarios:
            actual = next(
                (
                    operation["operationId"]
                    for path, methods in spec["paths"].items()
                    for method, operation in methods.items()
                    if method.upper() == scenario["wire_method"]
                    and isinstance(operation, dict)
                    and "operationId" in operation
                    and re.fullmatch(
                        re.sub(r"\{[^}]+\}", "[^/]+", path), scenario["wire_path"]
                    )
                ),
                None,
            )
            assert scenario["wire_operation"] == actual, (code, surface)
            assert scenario["decoder_operation"] == (
                None if surface == "raw_http" else scenario["operation"]
            )
        if surface.startswith("high"):
            assert all(s["exception"] for s in scenarios)
print(f"{len(codes)}/{len(codes)} stable error codes qualified on all five surfaces")
