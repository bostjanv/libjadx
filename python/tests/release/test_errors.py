"""Stable errors originate in Java, with correlation IDs and typed fidelity."""

import json
from uuid import uuid4

import pytest
from release_support import SURFACES, Surface
from test_routes import CLASS, METHOD, pre, wait_job


@pytest.mark.parametrize("surface", SURFACES)
async def test_request_security_limits_and_mapping_conflicts(factory, surface):
    with factory() as service:
        api = Surface(service, surface)
        try:
            await api.call("listClasses", query={"pageSize": -1}, expected=400)
            await api.call(
                "resolveSymbol",
                body={"ref": {**CLASS, "originalClassDescriptor": "bad"}},
                expected=400,
            )
            await api.call("getJob", job_id=uuid4(), expected=404)
            # Regex bound is an actual search RESOURCE_LIMIT, not a Python validation simulation.
            await api.call(
                "search",
                body={
                    "query": "a" * 257,
                    "domains": ["SOURCE_TEXT"],
                    "matchMode": "REGEX",
                },
                expected=429,
            )
            mapping = service.root / "mapping.tiny"
            mapping.write_text(
                "tiny\t2\t0\toriginal\tmapped\nc\tprobe/Variables\tprobe/MappingOne\n"
            )
            await api.call(
                "importMappings",
                body={
                    "sourcePath": str(mapping),
                    "format": "TINY_V2",
                    "mode": "MERGE_FAIL_ON_CONFLICT",
                    **pre(service),
                },
                expected=200,
            )
            mapping.write_text(
                "tiny\t2\t0\toriginal\tmapped\nc\tprobe/Variables\tprobe/MappingTwo\n"
            )
            conflict = await api.call(
                "importMappings",
                body={
                    "sourcePath": str(mapping),
                    "format": "TINY_V2",
                    "mode": "MERGE_FAIL_ON_CONFLICT",
                    **pre(service),
                },
                expected=409,
            )
            assert (
                conflict["error"]["code"] == "MAPPING_MERGE_CONFLICT"
                and conflict["error"]["details"]
            )
            outside = service.root.parent / "outside.tiny"
            outside.write_text("tiny\t2\t0\toriginal\tmapped\n")
            await api.call(
                "importMappings",
                body={
                    "sourcePath": str(outside),
                    "format": "TINY_V2",
                    "mode": "MERGE_FAIL_ON_CONFLICT",
                    **pre(service),
                },
                expected=403,
            )
        finally:
            await api.close()
    # Origin rejection has a documented route/status and comes from the actual server.
    with factory() as service:
        api = Surface(service, surface)
        try:
            await api.call(
                "shutdownService",
                body={"policy": "discard"},
                headers={"Origin": "https://untrusted.invalid"},
                expected=403,
            )
        finally:
            await api.close()
    # Known route wrong method, tested through generated detailed calls with an owned HTTPX request hook.
    with factory() as service:
        api = Surface(service, surface)
        try:
            await api.call("getProject", wire_method="DELETE", expected=405)
        finally:
            await api.close()
    # Planned router stub lies outside OpenAPI. Exercise the documented low-level escape;
    # the first-release default-error decoder is used, without adding a Phase 7 route/model.
    with factory() as service:
        api = Surface(service, surface)
        try:
            await api.call(
                "getProject",
                wire_path="/project/export",
                wire_method="POST",
                expected=501,
            )
        finally:
            await api.close()


@pytest.mark.parametrize("surface", SURFACES)
async def test_external_file_conflict_retains_pending_native_state(factory, surface):
    with factory(kind="native") as service:
        api = Surface(service, surface)
        try:
            ref = {"kind": "CLASS", "originalClassDescriptor": "Lprobe/Sample;"}
            await api.call(
                "applyEditBatch",
                body={
                    "items": [
                        {"kind": "RENAME", "target": ref, "newName": "PendingExternal"}
                    ],
                    **pre(service),
                },
                expected=200,
            )
            before = service.http.get("/api/v1/project").json()
            native = service.root / "sample.jar.jadx"
            external = native.read_bytes() + b"\n"
            native.write_bytes(external)
            result = await api.call("saveProject", body=pre(service), expected=409)
            assert result["error"]["code"] == "EXTERNAL_MODIFICATION_CONFLICT"
            assert native.read_bytes() == external
            assert service.http.get("/api/v1/project").json() == before
            pending = await api.call("exportPendingEdits", expected=200)
            assert pending["dirty"] and any(
                r["newName"] == "PendingExternal"
                for r in pending["codeData"]["renames"]
            )
        finally:
            await api.close()


@pytest.mark.parametrize("surface", SURFACES)
async def test_ambiguous_native_parameter_identity(factory, surface):
    with factory() as service:
        saved = service.root / "saved.jadx"
        service.http.post("/api/v1/project/save", json={"targetPath": str(saved)})
        root = service.root
    native = json.loads(saved.read_text())
    node = {
        "refType": "METHOD",
        "declClass": "probe.Variables",
        "shortId": "single(I)I",
    }
    # Verify pinned native serialization keys from an actual saved parameter entry.
    native["codeData"]["renames"] = [
        {
            "nodeRef": node,
            "codeRef": {"attachType": "MTH_ARG", "index": 0},
            "newName": name,
        }
        for name in ("first", "second")
    ]
    saved.write_text(json.dumps(native))
    with factory(kind="saved", root=root) as service:
        api = Surface(service, surface)
        try:
            source = await api.call("decompileJava", body={"ref": CLASS}, expected=200)
            result = await api.call(
                "applyEditBatch",
                body={
                    "items": [
                        {
                            "kind": "RENAME_PARAMETER",
                            "method": METHOD,
                            "parameterIndex": 0,
                            "sourceSnapshotId": source["sourceSnapshotId"],
                            "newName": "ambiguous",
                        }
                    ],
                    **pre(service),
                },
                expected=422,
            )
            assert result["error"]["code"] == "INVALID_ENTITY_ID"
        finally:
            await api.close()


@pytest.mark.parametrize("surface", SURFACES)
async def test_expired_event_history(factory, surface):
    with factory(kind="long") as service:
        api = Surface(service, surface)
        try:
            job = await api.call(
                "buildSearchIndex", body={"domains": ["SOURCE_TEXT"]}, expected=202
            )
            assert wait_job(service, job)["state"] == "SUCCEEDED"
            result = await api.call(
                "getJobEvents",
                job_id=job["jobId"],
                headers={"Last-Event-ID": "1"},
                expected=409,
            )
            assert result["error"]["code"] == "EVENT_HISTORY_EXPIRED"
            replay = await api.call("getJobEvents", job_id=job["jobId"], expected=200)
            assert replay[0]["sequence"] > 1 and replay[-1]["state"] == "SUCCEEDED"
        finally:
            await api.close()


@pytest.mark.parametrize("surface", SURFACES)
async def test_cancellation_pending_fault_uses_real_cancelling_job(factory, surface):
    # Owned Java fault response only while actual admitted work is CANCELLING.
    # Normal repeated cancel remains idempotent 200, as required by OpenAPI.
    with factory(holds=("job",), faults=("cancel-pending",)) as service:
        job = service.http.post(
            "/api/v1/search/build-index", json={"domains": ["SOURCE_TEXT"]}
        ).json()
        service.entered("job")
        api = Surface(service, surface)
        try:
            error = await api.call(
                "getProject",
                wire_path=f"/jobs/{job['jobId']}/cancel",
                wire_method="POST",
                expected=409,
            )
            assert error["error"]["code"] == "CANCELLATION_PENDING"
            assert error["error"]["details"]["state"] == "CANCELLING"
            assert (
                service.http.get("/api/v1/jobs/" + job["jobId"]).json()["state"]
                == "CANCELLING"
            )
        finally:
            service.release("job")
            await api.close()
        assert wait_job(service, job)["state"] == "CANCELLED"


@pytest.mark.parametrize("surface", SURFACES)
async def test_service_shutting_down_admission(factory, surface):
    with factory(holds=("shutdown",)) as service:
        response = service.http.post("/api/v1/shutdown", json={"policy": "discard"})
        assert response.status_code == 202
        service.entered("shutdown")
        api = Surface(service, surface)
        try:
            result = await api.call("getProject", expected=503)
            assert result["error"]["code"] == "SERVICE_SHUTTING_DOWN"
        finally:
            await api.close()
            service.release("shutdown")
        assert service.process.wait(timeout=10) == 0


@pytest.mark.parametrize("surface", SURFACES)
async def test_guarded_parameter_and_related_edits(factory, surface):
    from test_routes import GROUP

    with factory(kind="catch") as service:
        api = Surface(service, surface)
        try:
            ref = {"kind": "CLASS", "originalClassDescriptor": "Lprobe/UnusedCatch;"}
            source = await api.call("decompileJava", body={"ref": ref}, expected=200)
            assert "catch (NumberFormatException unused)" in source["source"]
            before = pre(service)
            result = await api.call(
                "applyEditBatch",
                body={
                    "items": [
                        {
                            "kind": "RENAME_PARAMETER",
                            "method": {
                                "kind": "METHOD",
                                "originalClassDescriptor": ref[
                                    "originalClassDescriptor"
                                ],
                                "originalName": "unusedCatch",
                                "originalDescriptor": "(I)I",
                            },
                            "parameterIndex": 0,
                            "sourceSnapshotId": source["sourceSnapshotId"],
                            "newName": "mustReject",
                        }
                    ],
                    **before,
                },
                expected=422,
            )
            assert (
                result["error"]["code"] == "UNSUPPORTED_CAPABILITY"
                and pre(service) == before
            )
        finally:
            await api.close()
    with factory() as service:
        api = Surface(service, surface)
        try:
            before = pre(service)
            group = {
                "kind": "RENAME",
                "target": GROUP,
                "newName": "qualifiedJoined",
                "propagateRelated": True,
            }
            await api.call(
                "applyEditBatch",
                body={
                    "items": [
                        group,
                        {
                            "kind": "RENAME",
                            "target": METHOD,
                            "newName": "ordinaryMixed",
                        },
                    ],
                    **before,
                },
                expected=400,
            )
            base = {
                "kind": "METHOD",
                "originalClassDescriptor": "Lrelated/Hierarchy$Base;",
                "originalName": "work",
                "originalDescriptor": "(I)I",
            }
            await api.call(
                "applyEditBatch",
                body={
                    "items": [
                        {
                            "kind": "RENAME",
                            "target": base,
                            "newName": "collision",
                            "propagateRelated": True,
                        }
                    ],
                    **before,
                },
                expected=400,
            )
            await api.call(
                "updateProjectSettings",
                body={"mappingsPath": None, "decompilationMode": "SIMPLE", **before},
                expected=422,
            )
            assert (
                pre(service) == before and not (await api.call("getProject"))["dirty"]
            )
        finally:
            await api.close()
