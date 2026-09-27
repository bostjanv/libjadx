"""Validate the reviewed editing contract and captured real HTTP responses.

Run EditBatchEndpointsTest first. Requires PyYAML, jsonschema and
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
    "edit-batch-applied.json": "EditBatchResult",
    "edit-batch-no-change.json": "EditBatchResult",
    "edit-batch-partial.json": "EditBatchResult",
    "edit-batch-rejected.json": "ErrorEnvelope",
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
print(f"OpenAPI 3.1 valid; {len(examples)} edit examples and {len(responses)} live HTTP responses valid; statuses={sorted(statuses)}")
