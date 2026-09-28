"""Independently validate reviewed examples and freshly captured import HTTP responses."""
import json
from pathlib import Path

import yaml
from jsonschema import Draft202012Validator
from openapi_spec_validator import validate

root = Path(__file__).resolve().parents[1]
spec = yaml.safe_load((root / "openapi/openapi.yaml").read_text())
validate(spec)
examples = {
    "mapping-import-request.json": "MappingImportRequest",
    "mapping-import-applied.json": "MappingImportReceipt",
    "mapping-import-no-change.json": "MappingImportReceipt",
    "mapping-import-conflict.json": "ErrorEnvelope",
}
def check(schema, value):
    Draft202012Validator({"$ref": f"#/components/schemas/{schema}", "components": spec["components"]}).validate(value)

for name, schema in examples.items():
    check(schema, json.loads((root / "openapi/examples" / name).read_text()))
responses = sorted((root / "build/mapping-import-contract-responses").glob("*.json"))
assert responses, "Run MappingImportEndpointsTest on final head first"
statuses = set()
for path in responses:
    record = json.loads(path.read_text())
    statuses.add(record["status"])
    check(record["schema"], record["body"])
assert {200, 400, 403, 404, 409, 415, 422, 429, 500, 503} <= statuses, statuses
print(f"OpenAPI 3.1 valid; {len(examples)} import examples and {len(responses)} live HTTP responses valid; statuses={sorted(statuses)}")
