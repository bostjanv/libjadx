"""Pinned OpenAPI 3.1 validator plus all referenced JSON and SSE examples."""

import json
from pathlib import Path

import yaml
from jsonschema import Draft202012Validator, FormatChecker
from openapi_spec_validator import validate

ROOT = Path(__file__).resolve().parents[1]
spec = yaml.safe_load((ROOT / "openapi/openapi.yaml").read_text())
validate(spec)
operations = [
    op["operationId"]
    for methods in spec["paths"].values()
    for op in methods.values()
    if isinstance(op, dict) and "operationId" in op
]
assert len(operations) == len(set(operations)), "Duplicate operationId"
assert spec["info"]["version"] == "0.1.0-experimental"
examples = set()


def walk(node):
    if isinstance(node, dict):
        if "schema" in node and "examples" in node:
            for example in node["examples"].values():
                if "externalValue" not in example:
                    continue
                path = (ROOT / "openapi" / example["externalValue"]).resolve()
                assert (
                    path.is_relative_to(ROOT / "openapi/examples") and path.is_file()
                ), path
                examples.add(path.name)
                if path.suffix == ".json":
                    Draft202012Validator(
                        {**node["schema"], "components": spec["components"]},
                        format_checker=FormatChecker(),
                    ).validate(json.loads(path.read_text()))
        if "externalValue" in node:
            assert (ROOT / "openapi" / node["externalValue"]).is_file()
        for value in node.values():
            walk(value)
    elif isinstance(node, list):
        for value in node:
            walk(value)


walk(spec)
frames = (ROOT / "openapi/examples/job-events.sse").read_text().split("\n\n")
events = [
    json.loads(line[6:])
    for frame in frames
    for line in frame.splitlines()
    if line.startswith("data: ")
]
assert events
for event in events:
    Draft202012Validator(
        {"$ref": "#/components/schemas/JobEvent", "components": spec["components"]},
        format_checker=FormatChecker(),
    ).validate(event)
print(
    f"OpenAPI 3.1 valid; {len(operations)} unique operations; {len(examples)} referenced JSON examples; {len(events)} SSE frames"
)
