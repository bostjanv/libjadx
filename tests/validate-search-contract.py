"""Validate OpenAPI 3.1, reviewed search examples, and captured real HTTP responses.

Run with Python containing openapi-spec-validator, jsonschema and PyYAML after
SearchEndpointsTest (or the full Gradle check). No network references are used.
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
    "search-request.json": "SearchRequest",
    "search-partial.json": "SearchPage",
    "search-complete.json": "SearchPage",
    "search-job-queued.json": "Job",
}
count = 0
for name, schema in examples.items():
    document = {"$ref": f"#/components/schemas/{schema}", "components": spec["components"]}
    Draft202012Validator(document).validate(json.loads((root / "openapi/examples" / name).read_text()))
    count += 1
responses = sorted((root / "build/search-contract-responses").glob("*.json"))
assert responses, "Run SearchEndpointsTest first to capture live HTTP responses"
statuses = set()
for path in responses:
    record = json.loads(path.read_text())
    statuses.add(record["status"])
    document = {"$ref": f'#/components/schemas/{record["schema"]}', "components": spec["components"]}
    Draft202012Validator(document).validate(record["body"])
assert {200, 202, 400, 409, 415, 422, 429, 503} <= statuses, statuses
print(f"OpenAPI 3.1 valid; {count} search examples and {len(responses)} live HTTP responses valid; statuses={sorted(statuses)}")
