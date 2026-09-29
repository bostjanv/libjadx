"""Validate the reviewed editing contract and captured real HTTP responses.

Run EditBatchEndpointsTest, EditBatchServiceTest and ScopedParameterEndpointsTest first. Requires PyYAML, jsonschema and
openapi-spec-validator in the selected Python environment.
"""
import json
from pathlib import Path

import yaml
from jsonschema import Draft202012Validator
from openapi_spec_validator import validate

root = Path(__file__).resolve().parents[1]
spec = yaml.safe_load((root / "openapi/openapi.yaml").read_text())
validate(spec)

examples = {
    "edit-batch-request.json": "EditBatchRequest",
    "edit-parameter-request.json": "EditBatchRequest",
    "edit-parameter-applied.json": "EditBatchResult",
    "edit-parameter-no-change.json": "EditBatchResult",
    "decompile-variables.json": "DecompileResult",
    "edit-batch-applied.json": "EditBatchResult",
    "edit-batch-no-change.json": "EditBatchResult",
    "edit-batch-partial.json": "EditBatchResult",
    "edit-batch-partial-no-applied.json": "EditBatchResult",
    "edit-batch-rejected.json": "ErrorEnvelope",
    "edit-related-unsupported.json": "ErrorEnvelope",
}
for name, schema in examples.items():
    document = {"$ref": f"#/components/schemas/{schema}", "components": spec["components"]}
    Draft202012Validator(document).validate(json.loads((root / "openapi/examples" / name).read_text()))

responses = sorted((root / "build/edit-contract-responses").glob("*.json"))
assert responses, "Run EditBatchEndpointsTest first to capture live HTTP responses"
statuses = set()
for path in responses:
    record = json.loads(path.read_text())
    statuses.add(record["status"])
    document = {"$ref": f'#/components/schemas/{record["schema"]}', "components": spec["components"]}
    Draft202012Validator(document).validate(record["body"])

required = {200, 400, 403, 404, 409, 415, 422, 429, 503}
assert required <= statuses, (required - statuses, statuses)
failures = sorted((root / "build/edit-service-results").glob("partial-*.json"))
assert len(failures) == 4, "Run EditBatchServiceTest to capture both failure positions with clean/dirty state"
for path in failures:
    document = {"$ref": "#/components/schemas/EditBatchResult", "components": spec["components"]}
    Draft202012Validator(document).validate(json.loads(path.read_text()))
print(f"OpenAPI 3.1 valid; {len(examples)} edit examples, {len(responses)} live HTTP responses and {len(failures)} injected service results valid; statuses={sorted(statuses)}")

scoped = sorted((root / "build/scoped-edit-contract-responses").glob("*.json"))
assert len(scoped) == 18, "Run ScopedParameterEndpointsTest to capture all fresh HTTP responses"
scoped_statuses = set()
for path in scoped:
    record = json.loads(path.read_text())
    scoped_statuses.add(record["status"])
    document = {"$ref": f'#/components/schemas/{record["schema"]}', "components": spec["components"]}
    Draft202012Validator(document).validate(record["body"])
assert {200, 400, 404, 409, 415, 422, 429, 503, 500} <= scoped_statuses
for name in ("409-revision", "409-snapshot"):
    assert json.loads((root / f"build/scoped-edit-contract-responses/{name}.json").read_text())["body"]["error"]["code"] == "STALE_REVISION"
assert json.loads((root / "build/scoped-edit-contract-responses/422-ambiguous.json").read_text())["body"]["error"]["code"] == "INVALID_ENTITY_ID"
assert json.loads((root / "build/scoped-edit-contract-responses/422-catch.json").read_text())["body"]["error"]["code"] == "UNSUPPORTED_CAPABILITY"
catch_source = json.loads((root / "build/scoped-edit-contract-responses/200-catch-source.json").read_text())["body"]
assert "catch (NumberFormatException unused)" in catch_source["source"]
assert len(catch_source["variables"]) == 1
assert catch_source["variables"][0]["persistability"] == "UNSUPPORTED"
assert catch_source["variables"][0]["parameterIndex"] is None
# Independently enforce the conditional revision requirement on scoped requests.
request_schema = {"$ref": "#/components/schemas/EditBatchRequest", "components": spec["components"]}
missing = json.loads((root / "openapi/examples/edit-parameter-request.json").read_text())
missing.pop("expectedSessionId"); missing.pop("expectedLogicalRevision")
assert not Draft202012Validator(request_schema).is_valid(missing)
print(f"{len(scoped)} scoped live HTTP responses valid; statuses={sorted(scoped_statuses)}")

# Evidence-only PR #14/#16: deliberately rejected requests, never a supported flag.
related_request = json.loads((root / "openapi/examples/edit-related-rejected-request.json").read_text())
validator = Draft202012Validator(request_schema)
for flag in (True, False, None, "yes", 7, [], {}):
    related_request["items"][0]["propagateRelated"] = flag
    assert not validator.is_valid(related_request)
related_request["items"][0].pop("propagateRelated")
validator.validate(related_request)  # Legacy declaration request remains accepted.
related = sorted((root / "build/related-contract-responses").glob("*.json"))
assert len(related) == 14, "Run RelatedPropagationEndpointsTest for clean/dirty presence-rejection captures"
for path in related:
    record = json.loads(path.read_text())
    assert record["status"] == 422
    Draft202012Validator({"$ref": "#/components/schemas/ErrorEnvelope", "components": spec["components"]}).validate(record["body"])
    assert record["body"]["error"]["code"] == "UNSUPPORTED_CAPABILITY"
    assert record["body"]["error"]["details"]["itemErrors"][0]["index"] == 1
print(f"{len(related)} related-propagation rejection responses valid; propagation remains unsupported")
