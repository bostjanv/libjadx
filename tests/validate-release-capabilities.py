"""Fail closed on stale or unqualified public capability snapshots."""

import json
import sys
from pathlib import Path

import yaml
from jsonschema import Draft202012Validator
from release.qualify import validate_evidence_identity

ROOT = Path(__file__).resolve().parents[1]
spec = yaml.safe_load((ROOT / "openapi/openapi.yaml").read_text())
media = spec["paths"]["/capabilities"]["get"]["responses"]["200"]["content"][
    "application/json"
]
expected = json.loads(
    (
        ROOT / "openapi" / media["examples"]["releaseCandidate"]["externalValue"]
    ).read_text()
)
data = json.loads(
    Path(
        sys.argv[1]
        if len(sys.argv) > 1
        else ROOT / "docs/release-evidence/0.1.0-alpha.1/capabilities.json"
    ).read_text()
)
validate_evidence_identity(data)
surfaces = {
    "raw_http",
    "generated_sync",
    "generated_async",
    "high_level_sync",
    "high_level_async",
}
assert set(data["records"]) == surfaces
for surface, record in data["records"].items():
    assert record["test_hooks_enabled"] is False, surface
    value = record["response"]
    Draft202012Validator(
        {**media["schema"], "components": spec["components"]}
    ).validate(value)
    assert set(value) == set(expected)
    assert value["serverVersion"] == expected["serverVersion"]
    assert value["jadxVersion"] == expected["jadxVersion"]
    entries = {entry["name"]: entry for entry in value["capabilities"]}
    assert len(entries) == len(value["capabilities"]), surface
    assert entries == {entry["name"]: entry for entry in expected["capabilities"]}, (
        surface
    )
print(
    f"{len(expected['capabilities'])} reviewed public capabilities qualified on all five ordinary-process surfaces"
)
