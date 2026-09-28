"""Independently validate reviewed examples and freshly captured export HTTP responses."""
import json
from pathlib import Path

import yaml
from jsonschema import Draft202012Validator
from openapi_spec_validator import validate

root = Path(__file__).resolve().parents[1]
spec = yaml.safe_load((root / "openapi/openapi.yaml").read_text())
validate(spec)
examples = {
    "mapping-export-request.json": "MappingExportRequest",
    "mapping-export-receipt.json": "MappingExportReceipt",
    "mapping-export-unsupported.json": "ErrorEnvelope",
}
def check(schema, value):
    Draft202012Validator({"$ref": f"#/components/schemas/{schema}", "components": spec["components"]}).validate(value)

for name, schema in examples.items():
    check(schema, json.loads((root / "openapi/examples" / name).read_text()))
responses = sorted((root / "build/mapping-export-contract-responses").glob("*.json"))
assert responses, "Run MappingExportEndpointsTest on final head first"
statuses = set()
for path in responses:
    record = json.loads(path.read_text())
    statuses.add(record["status"])
    check(record["schema"], record["body"])
assert {200, 400, 403, 409, 415, 422, 429, 500, 503} <= statuses, statuses
print(f"OpenAPI 3.1 valid; {len(examples)} export examples and {len(responses)} live HTTP responses valid; statuses={sorted(statuses)}")
