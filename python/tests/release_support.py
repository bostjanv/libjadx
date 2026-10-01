"""Shared live qualification oracle. Raw requests never use generated models."""

import importlib
import inspect
import json
import os
import re
from datetime import datetime
from pathlib import Path
from uuid import UUID

import httpx
import yaml
from jsonschema import Draft202012Validator, FormatChecker

from libjadx import AsyncClient, Client
from libjadx.errors import ERROR_TYPES, ApiError
from libjadx.lowlevel import models as m

ROOT = Path(__file__).resolve().parents[2]
SPEC = yaml.safe_load((ROOT / "openapi/openapi.yaml").read_text())
SURFACES = (
    "raw_http",
    "generated_sync",
    "generated_async",
    "high_level_sync",
    "high_level_async",
)
OPERATIONS = {
    value["operationId"]: (method.upper(), path, value)
    for path, methods in SPEC["paths"].items()
    for method, value in methods.items()
    if isinstance(value, dict) and "operationId" in value
}
ROUTES = {}
ERRORS = {}
CAPABILITIES = {}
PROCESS_OBSERVATIONS = []


def deref(value):
    while "$ref" in value:
        node = SPEC
        for key in value["$ref"].removeprefix("#/").split("/"):
            node = node[key]
        value = node
    return value


def response_schema(operation, status, media="application/json"):
    responses = OPERATIONS[operation][2]["responses"]
    response = deref(responses.get(str(status), responses.get("default", {})))
    assert "content" in response, (operation, status, "undocumented status")
    return response["content"][media]["schema"]


def validate(schema, body):
    Draft202012Validator(
        {**schema, "components": SPEC["components"]}, format_checker=FormatChecker()
    ).validate(body)


def semantic(value):
    """Normalize representation only. Never omit provenance, cursor or error fields."""
    if hasattr(value, "raw"):
        value = value.raw
    if hasattr(value, "to_dict"):
        value = value.to_dict()
    if isinstance(value, dict):
        return {key: semantic(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [semantic(item) for item in value]
    if isinstance(value, UUID):
        return str(value)
    if isinstance(value, datetime):
        return value.isoformat()
    if isinstance(value, str) and re.fullmatch(
        r"\d{4}-\d\d-\d\dT.*(?:Z|[+]00:00)", value
    ):
        return datetime.fromisoformat(value.replace("Z", "+00:00")).isoformat()
    return value


def assert_fidelity(wire, parsed):
    # The wire body for THIS invocation is the oracle, including its own request ID.
    # Different invocations have legitimately different server-generated correlation IDs.
    assert semantic(wire) == semantic(parsed)


def assert_release_capabilities(value):
    """Compare the full live snapshot with the reviewed public contract example."""
    example = OPERATIONS["getCapabilities"][2]["responses"]["200"]["content"][
        "application/json"
    ]["examples"]["releaseCandidate"]["externalValue"]
    expected = json.loads((ROOT / "openapi" / example).read_text())
    assert set(value) == set(expected)
    assert value["serverVersion"] == expected["serverVersion"]
    assert value["jadxVersion"] == expected["jadxVersion"]
    actual_entries = {entry["name"]: entry for entry in value["capabilities"]}
    assert len(actual_entries) == len(value["capabilities"]), "Duplicate capability"
    assert actual_entries == {
        entry["name"]: entry for entry in expected["capabilities"]
    }, "Public capability snapshot disagrees with the reviewed release contract"


def wire_operation(method, path):
    return next(
        (
            name
            for name, (verb, template, _) in OPERATIONS.items()
            if verb == method
            and re.fullmatch(re.sub(r"\{[^}]+\}", "[^/]+", template), path)
        ),
        None,
    )


def portable(value, root=None):
    if isinstance(value, dict):
        return {k: portable(v, root) for k, v in value.items()}
    if isinstance(value, list):
        return [portable(v, root) for v in value]
    if isinstance(value, str):
        value = value.replace(str(ROOT), "<repo>")
        if root:
            value = value.replace(str(root), "<tmp>").replace(
                str(root.parent), "<tmp-parent>"
            )
        return value
    return value


class Surface:
    """Invoke public surfaces, always validate against the actual Java wire response."""

    def __init__(self, service, name):
        self.service = service
        self.name = name
        self.last = None
        self.high = (
            AsyncClient(service.url) if name.endswith("async") else Client(service.url)
        )
        if name.endswith("async"):

            async def capture(response):
                if not response.headers.get("content-type", "").startswith(
                    "text/event-stream"
                ):
                    await response.aread()
                self.last = response

            pool = httpx.AsyncClient(
                base_url=self.high.base_url,
                trust_env=False,
                event_hooks={"response": [capture]},
            )
            self.high.lowlevel.set_async_httpx_client(pool)
        else:

            def capture(response):
                if not response.headers.get("content-type", "").startswith(
                    "text/event-stream"
                ):
                    response.read()
                self.last = response

            pool = httpx.Client(
                base_url=self.high.base_url,
                trust_env=False,
                event_hooks={"response": [capture]},
            )
            self.high.lowlevel.set_httpx_client(pool)

    async def close(self):
        if self.name.endswith("async"):
            await self.high.aclose()
        else:
            self.high.close()

    async def call(
        self,
        operation,
        body=None,
        query=None,
        job_id=None,
        headers=None,
        expected=None,
        wire_method=None,
        wire_path=None,
    ):
        method, path, op = OPERATIONS[operation]
        path = wire_path or path.replace("{jobId}", str(job_id))
        method = wire_method or method
        caught = None
        if self.name == "raw_http":
            response = self.service.http.request(
                method, "/api/v1" + path, json=body, params=query, headers=headers
            )
            self.last = response
            parsed = (
                response.text
                if operation == "getJobEvents" and response.status_code == 200
                else response.json()
            )
        else:
            pool = (
                self.high.lowlevel.get_async_httpx_client()
                if self.name.endswith("async")
                else self.high.lowlevel.get_httpx_client()
            )
            if wire_method or wire_path:
                if self.name.endswith("async"):

                    async def redirect(request):
                        request.method = method
                        request.url = request.url.copy_with(path="/api/v1" + path)
                else:

                    def redirect(request):
                        request.method = method
                        request.url = request.url.copy_with(path="/api/v1" + path)

                pool.event_hooks["request"].append(redirect)
            if headers:
                pool.headers.update(
                    {k: v for k, v in headers.items() if k != "Last-Event-ID"}
                )
            module_name = re.sub(r"(?<!^)(?=[A-Z])", "_", operation).lower()
            module = importlib.import_module(
                f"libjadx._generated.api.{op['tags'][0].lower()}.{module_name}"
            )
            kwargs = {}
            if body is not None:
                schema = deref(op["requestBody"])["content"]["application/json"][
                    "schema"
                ]["$ref"].split("/")[-1]
                kwargs["body"] = getattr(m, schema).from_dict(body)
            for key, value in (query or {}).items():
                kwargs[re.sub(r"(?<!^)(?=[A-Z])", "_", key).lower()] = value
            if job_id is not None:
                kwargs["job_id"] = UUID(str(job_id))
            if headers and "Last-Event-ID" in headers:
                kwargs["last_event_id"] = headers["Last-Event-ID"]
            if self.name.startswith("generated"):
                detailed = (
                    await module.asyncio_detailed(client=self.high.lowlevel, **kwargs)
                    if self.name.endswith("async")
                    else module.sync_detailed(client=self.high.lowlevel, **kwargs)
                )
                assert detailed.status_code == self.last.status_code
                assert dict(detailed.headers) == dict(self.last.headers)
                parsed = detailed.parsed
                if operation == "getJobEvents" and detailed.status_code == 200:
                    # Stock generator has no SSE decoder; validate bytes + native parser independently.
                    assert isinstance(parsed, str)
                    assert parsed == detailed.content.decode()
                else:
                    assert parsed is not None
                    assert (
                        type(parsed).__name__
                        == response_schema(operation, detailed.status_code)[
                            "$ref"
                        ].split("/")[-1]
                    )
            else:
                try:
                    parsed = await self._high(
                        operation, body or {}, query or {}, job_id, headers or {}
                    )
                except ApiError as error:
                    caught = error
                    parsed = {"error": error.raw.to_dict()}
                response = self.last
        response = self.last
        if (
            operation == "getJobEvents"
            and self.name.startswith("high")
            and caught is None
        ):
            # Compare to the same immutable retained Java event history, not a fabricated client snapshot.
            response = self.service.http.get("/api/v1" + path, headers=headers)
        assert response is not None
        status = response.status_code
        if expected is not None:
            assert status == expected, (operation, self.name, status, response.text)
        assert UUID(response.headers["x-request-id"])
        assert response.headers["cache-control"] == "no-store"
        if operation == "getJobEvents" and status == 200:
            assert response.headers["content-type"].startswith("text/event-stream")
            events = [
                json.loads(line[6:])
                for line in response.text.splitlines()
                if line.startswith("data: ")
            ]
            for event in events:
                validate({"$ref": "#/components/schemas/JobEvent"}, event)
            if self.name.startswith("high"):
                assert_fidelity(events, parsed)
            else:
                assert parsed == response.text
            value = events
        else:
            assert response.headers["content-type"].startswith("application/json")
            value = response.json()
            validate(response_schema(operation, status), value)
            assert_fidelity(value, parsed)
            if operation == "getCapabilities" and status == 200:
                assert_release_capabilities(value)
                # Prefer the actual ordinary-process observation over a hooked run.
                if self.name not in CAPABILITIES or not self.service.test_hooks_enabled:
                    CAPABILITIES[self.name] = {
                        "response": value,
                        "test_hooks_enabled": self.service.test_hooks_enabled,
                    }
            if status >= 400:
                error = value["error"]
                assert error["requestId"] == response.headers["x-request-id"]
                if caught:
                    assert type(caught) is ERROR_TYPES[error["code"]]
                    assert caught.http_status == status
                    assert (
                        caught.request_id
                        == caught.header_request_id
                        == error["requestId"]
                    )
                    assert caught.details == error.get("details")
                    assert caught.causes == error.get("causes", [])
                    assert caught.item_errors == error.get("itemErrors", [])
                row = ERRORS.setdefault(
                    error["code"], {"surfaces": {}, "httpStatuses": [], "scenarios": []}
                )
                row["surfaces"][self.name] = True
                if status not in row["httpStatuses"]:
                    row["httpStatuses"].append(status)
                row["scenarios"].append(
                    {
                        "operation": operation,
                        "decoder_operation": operation
                        if self.name != "raw_http"
                        else None,
                        "wire_operation": wire_operation(
                            response.request.method,
                            response.request.url.path.removeprefix("/api/v1"),
                        ),
                        "surface": self.name,
                        "wire": portable(value, self.service.root),
                        "exception": type(caught).__name__ if caught else None,
                        "wire_path": response.request.url.path.removeprefix("/api/v1"),
                        "wire_method": response.request.method,
                        "java_test_faults": [
                            p.stem for p in sorted(self.service.hooks.glob("*.fail"))
                        ],
                    }
                )
        record = ROUTES.setdefault(
            operation,
            {
                "surfaces": {},
                "positive_case": False,
                "negative_case": False,
                "provenance_checked": True,
                "error_checked": False,
            },
        )
        record["surfaces"][self.name] = record["surfaces"].get(self.name, 0) + 1
        if status < 400:
            positive = record.setdefault("positive_surfaces", {})
            positive[self.name] = positive.get(self.name, 0) + 1
        record["positive_case"] |= status < 400
        record["negative_case"] |= status >= 400
        record["error_checked"] |= status >= 400
        return value

    async def _high(self, operation, body, query, job_id, headers):
        p = self.high.project
        pre = {
            "expected_session_id": body.get("expectedSessionId"),
            "expected_logical_revision": body.get("expectedLogicalRevision"),
        }
        ref = m.SymbolRef.from_dict(body["ref"]) if "ref" in body else None
        methods = {
            "getLiveness": lambda: self.high.liveness(),
            "getStatus": lambda: self.high.status(),
            "getCapabilities": lambda: self.high.capabilities(),
            "getProject": lambda: p.snapshot(),
            "getProjectSettings": lambda: p.settings(),
            "exportPendingEdits": lambda: p.pending_edits(),
            "saveProject": lambda: p.save(body.get("targetPath"), **pre),
            "reloadProject": lambda: p.reload(
                discard_unsaved=body["discardUnsaved"], **pre
            ),
            "updateProjectSettings": lambda: p.update_settings(
                body["mappingsPath"], **pre
            ),
            "exportMappings": lambda: p.export_mappings(body["targetPath"], **pre),
            "importMappings": lambda: p.import_mappings(body["sourcePath"], **pre),
            "resolveSymbol": lambda: p.resolve(ref),
            "decompileJava": lambda: p.decompile(
                ref,
                **{
                    re.sub(r"(?<!^)(?=[A-Z])", "_", k).lower(): v
                    for k, v in body.items()
                    if k != "ref"
                },
            ),
            "queryReferences": lambda: p.references(
                ref,
                **{
                    re.sub(r"(?<!^)(?=[A-Z])", "_", k).lower(): v
                    for k, v in body.items()
                    if k != "ref"
                },
            ),
            "search": lambda: p.search(
                **{
                    re.sub(r"(?<!^)(?=[A-Z])", "_", k).lower(): v
                    for k, v in body.items()
                }
            ),
            "buildSearchIndex": lambda: p.build_search_index(
                m.SearchBuildRequest.from_dict(body)
            ),
            "applyEditBatch": lambda: p.apply_edits(
                m.EditBatchRequest.from_dict(body).items, **pre
            ),
            "shutdownService": lambda: self.high.shutdown(
                body.get("policy", "discard")
            ),
            "getJob": lambda: self.high.job(job_id),
        }
        if (
            operation == "decompileJava"
            and any(
                k in body
                for k in (
                    "representation",
                    "expectedSourceSnapshotId",
                    "expectedSessionId",
                )
            )
            or operation == "search"
            and "cursor" in body
            or operation == "updateProjectSettings"
            and "decompilationMode" in body
        ):
            # Public convenience deliberately exposes a subset; use documented transport escape.
            op = OPERATIONS[operation][2]
            name = re.sub(r"(?<!^)(?=[A-Z])", "_", operation).lower()
            module = importlib.import_module(
                f"libjadx._generated.api.{op['tags'][0].lower()}.{name}"
            )
            schema = deref(op["requestBody"])["content"]["application/json"]["schema"][
                "$ref"
            ].split("/")[-1]
            model = getattr(m, schema).from_dict(body)
            detailed = (
                await module.asyncio_detailed(client=self.high.lowlevel, body=model)
                if self.name.endswith("async")
                else module.sync_detailed(client=self.high.lowlevel, body=model)
            )
            if isinstance(detailed.parsed, m.ErrorEnvelope):
                from libjadx.errors import api_error

                raise api_error(
                    detailed.parsed.error, int(detailed.status_code), detailed.headers
                )
            return detailed.parsed
        if operation == "listClasses":
            options = {
                re.sub(r"(?<!^)(?=[A-Z])", "_", k).lower(): v for k, v in query.items()
            }
            if "cursor" in options:
                # Documented escape hatch: class_pages owns cursor advancement.
                from libjadx._generated.api.symbols import list_classes

                result = (
                    await list_classes.asyncio_detailed(
                        client=self.high.lowlevel, **options
                    )
                    if self.name.endswith("async")
                    else list_classes.sync_detailed(
                        client=self.high.lowlevel, **options
                    )
                )
                if isinstance(result.parsed, m.ErrorEnvelope):
                    from libjadx.errors import api_error

                    raise api_error(
                        result.parsed.error, int(result.status_code), result.headers
                    )
                return result.parsed
            iterator = p.class_pages(**options)
            return (
                await anext(iterator) if self.name.endswith("async") else next(iterator)
            )
        if (
            operation == "applyEditBatch"
            and len(body.get("items", [])) == 1
            and body["items"][0]["kind"] == "RENAME_PARAMETER"
        ):
            item = body["items"][0]
            result = p.rename_parameter(
                m.SymbolRef.from_dict(item["method"]),
                item["parameterIndex"],
                item["newName"],
                source_snapshot_id=item["sourceSnapshotId"],
                **pre,
            )
            return await result if inspect.isawaitable(result) else result
        if operation in ("cancelJob", "getJobEvents"):
            job = self.high.job(job_id)
            if inspect.isawaitable(job):
                job = await job
            if operation == "cancelJob":
                result = job.cancel()
            elif self.name.endswith("async"):
                return [
                    e
                    async for e in job.events(
                        last_event_id=headers.get("Last-Event-ID")
                    )
                ]
            else:
                return list(job.events(last_event_id=headers.get("Last-Event-ID")))
        else:
            result = methods[operation]()
        return await result if inspect.isawaitable(result) else result


def write_evidence():
    directory = os.environ.get("LIBJADX_RELEASE_EVIDENCE")
    if not directory:
        return
    output = Path(directory)
    output.mkdir(parents=True, exist_ok=True)
    import subprocess

    head = subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True
    ).strip()
    # Load stdlib-only qualification identity helper without adding SDK source to sys.path.
    from importlib.util import module_from_spec, spec_from_file_location

    module_spec = spec_from_file_location(
        "release_qualification_identity", ROOT / "tests/release/qualify.py"
    )
    identity = module_from_spec(module_spec)
    module_spec.loader.exec_module(identity)
    fingerprint = identity.source_hash()
    for row in ERRORS.values():
        # Retain one full sample per surface, plus the independently observed status inventory.
        unique = {}
        for scenario in row["scenarios"]:
            unique.setdefault(scenario["surface"], scenario)
        row["scenarios"] = list(unique.values())
        row["detailsVerified"] = row["requestIdsVerified"] = True
    (output / "observations.json").write_text(
        json.dumps({"head": head, "records": PROCESS_OBSERVATIONS}, indent=2) + "\n"
    )
    for name, data in (
        ("routes", ROUTES),
        ("errors", ERRORS),
        ("capabilities", CAPABILITIES),
    ):
        (output / f"{name}.json").write_text(
            json.dumps(
                {"head": head, "source_sha256": fingerprint, "records": data},
                indent=2,
                sort_keys=True,
            )
            + "\n"
        )
