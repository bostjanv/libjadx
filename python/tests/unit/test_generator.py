"""Mandatory spike: execute stock generated difficult schemas, not just inspect files."""

import importlib
import json
import re
from datetime import datetime
from pathlib import Path
from uuid import UUID

import httpx
import pytest
import yaml

from libjadx import lowlevel
from libjadx._generated.api.analysis import search
from libjadx._generated.api.service import get_liveness
from libjadx.lowlevel import models as m

ROOT = Path(__file__).resolve().parents[3]
SPEC = yaml.safe_load((ROOT / "openapi/openapi.yaml").read_text())


def cases():
    found = set()

    def walk(node):
        if isinstance(node, dict):
            if "$ref" in node.get("schema", {}):
                schema = node["schema"]["$ref"].split("/")[-1]
                for example in node.get("examples", {}).values():
                    if "externalValue" in example:
                        found.add((Path(example["externalValue"]).name, schema))
            for v in node.values():
                walk(v)
        elif isinstance(node, list):
            for v in node:
                walk(v)

    walk(SPEC)
    return sorted(found)


@pytest.mark.parametrize("filename,schema", cases())
def test_reviewed_examples(filename, schema):
    body = json.loads((ROOT / "openapi/examples" / filename).read_text())
    raw = getattr(m, schema).from_dict(body)
    assert isinstance(raw.to_dict(), dict)
    # Every supplied field survives; optional server defaults may be added.
    for key, value in body.items():
        if key.endswith("At") and value is not None:
            assert datetime.fromisoformat(raw.to_dict()[key]) == datetime.fromisoformat(
                value.replace("Z", "+00:00")
            )
        else:
            assert raw.to_dict()[key] == value


def test_all_operations_import_sync_async():
    from libjadx.errors import ERROR_TYPES

    assert set(ERROR_TYPES) == {str(code) for code in m.ApiErrorCode}
    operations = [
        v
        for path in SPEC["paths"].values()
        for v in path.values()
        if isinstance(v, dict) and "operationId" in v
    ]
    assert len(operations) == 22
    for op in operations:
        name = re.sub(r"(?<!^)(?=[A-Z])", "_", op["operationId"]).lower()
        module = importlib.import_module(
            f"libjadx._generated.api.{op['tags'][0].lower()}.{name}"
        )
        assert callable(module.sync_detailed)
        assert callable(module.asyncio_detailed)


@pytest.mark.parametrize(
    "status,fixture,cls",
    [
        (200, "search-partial.json", m.SearchPage),
        (202, "job-queued.json", m.Job),
        (409, "external-conflict.json", m.ErrorEnvelope),
        (422, "edit-related-unsupported.json", m.ErrorEnvelope),
        (429, "job-resource-limit.json", m.ErrorEnvelope),
        (503, "project-busy.json", m.ErrorEnvelope),
    ],
)
def test_generated_transport(status, fixture, cls, example):
    client = lowlevel.Client("http://127.0.0.1/api/v1")
    http = httpx.Client(
        base_url="http://127.0.0.1/api/v1",
        transport=httpx.MockTransport(
            lambda r: httpx.Response(status, json=example(fixture))
        ),
    )
    client.set_httpx_client(http)
    with http:
        response = search.sync_detailed(client=client, body=m.SearchRequest("a"))
        assert response.status_code == status
        assert isinstance(response.parsed, cls)


@pytest.mark.asyncio
async def test_generated_async_alternate_success(example):
    async with httpx.AsyncClient(
        base_url="http://127.0.0.1/api/v1",
        transport=httpx.MockTransport(
            lambda r: httpx.Response(202, json=example("job-queued.json"))
        ),
    ) as http:
        client = lowlevel.Client("http://127.0.0.1/api/v1").set_async_httpx_client(http)
        result = await search.asyncio_detailed(client=client, body=m.SearchRequest("a"))
        assert isinstance(result.parsed, m.Job)


def test_recursive_errors_opaque_json_int64_dates_uuid(example):
    details = {"unknown": [None, {"bool": True, "n": 2**60}]}
    error = {
        "code": "STALE_REVISION",
        "message": "stale",
        "details": details,
        "causes": [{"code": "PROJECT_BUSY", "message": "busy", "details": details}],
        "itemErrors": [{"index": 2, "code": "X", "message": "x", "details": details}],
    }
    raw = m.ApiError.from_dict(error)
    assert raw.to_dict() == error
    doc = example("job-queued.json")
    doc.update(logicalRevision=2**60, result=details)
    job = m.Job.from_dict(doc)
    assert isinstance(job.job_id, UUID)
    assert job.created_at.utcoffset().total_seconds() == 0
    assert job.started_at is None
    assert job.logical_revision == 2**60
    assert job.result.to_dict() == details
    pending = m.PendingEdits.from_dict(
        {
            "sessionId": doc["sessionId"],
            "logicalRevision": 2**60,
            "dirty": True,
            "codeData": details,
        }
    )
    assert pending.code_data.to_dict() == details


def test_const_discriminated_oneof(example):
    batch = m.EditBatchRequest.from_dict(example("edit-parameter-request.json"))
    assert isinstance(batch.items[0], m.RenameParameterOperation)
    batch = m.EditBatchRequest.from_dict(example("edit-batch-request.json"))
    assert isinstance(batch.items[0], m.RenameOperation)
    assert isinstance(batch.items[1], m.SetCommentOperation)
    with pytest.raises(ValueError):
        m.RenameOperation.from_dict({"kind": "FUTURE", "target": {}, "newName": "a"})
    with pytest.raises(ValueError):
        m.JobState("FUTURE")


def test_generated_default_error_and_unexpected(example):
    client = lowlevel.Client("http://127.0.0.1/api/v1")
    response = httpx.Response(418, json=example("external-conflict.json"))
    assert isinstance(
        get_liveness._parse_response(client=client, response=response), m.ErrorEnvelope
    )
    assert search._parse_response(client=client, response=response) is None


def test_installed_distribution_when_requested():
    import os

    import libjadx

    if os.environ.get("LIBJADX_EXPECT_INSTALLED"):
        assert "site-packages" in str(Path(libjadx.__file__))
        assert ROOT / "python/src/libjadx" not in Path(libjadx.__file__).parents


def test_documented_examples_compile_and_lowlevel_namespace():
    source = (ROOT / "python/README.md").read_text()
    for code in re.findall(r"```python\n(.*?)```", source, re.DOTALL):
        compile(code, "python/README.md", "exec")
    assert callable(lowlevel.api.symbols.list_classes.sync_detailed)
    assert callable(lowlevel.api.service.get_job_events.asyncio_detailed)


def test_unknown_nullable_enum_behavior(example):
    doc = example("job-queued.json")
    doc["completeness"] = "FUTURE"
    # Stock nullable enum unions preserve raw strings; primary enums reject.
    assert m.Job.from_dict(doc).completeness == "FUTURE"


def test_reviewed_sse_example_against_generated_event():
    from libjadx._sse import Parser

    example = (ROOT / "openapi/examples/job-events.sse").read_bytes()
    events = Parser(UUID("a142b85f-c8ed-4651-a087-0417147c18ca"), None).feed(example)
    assert len(events) == 3 and events[-1].state == "SUCCEEDED"
    assert all(isinstance(e.progress, m.JobProgress) for e in events)
