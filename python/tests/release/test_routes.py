"""All contract operations through five surfaces, plus stateful wire oracles."""

import json
import time
from uuid import uuid4

import pytest
from release_support import OPERATIONS, ROUTES, SURFACES, Surface, assert_fidelity

CLASS = {"kind": "CLASS", "originalClassDescriptor": "Lprobe/Variables;"}
METHOD = {
    "kind": "METHOD",
    "originalClassDescriptor": "Lprobe/Variables;",
    "originalName": "single",
    "originalDescriptor": "(I)I",
}
FIELD = {
    "kind": "FIELD",
    "originalClassDescriptor": "Lprobe/Variables;",
    "originalName": "field",
    "originalDescriptor": "I",
}
FAN = {
    "kind": "METHOD",
    "originalClassDescriptor": "Lprobe/ReferenceFixture;",
    "originalName": "fanout",
    "originalDescriptor": "()V",
}
GROUP = {
    "kind": "METHOD",
    "originalClassDescriptor": "Lrelated/Hierarchy$Joined;",
    "originalName": "joined",
    "originalDescriptor": "(I)I",
}


def pre(service):
    snapshot = service.http.get("/api/v1/project").json()
    return {
        "expectedSessionId": snapshot["revisions"]["sessionId"],
        "expectedLogicalRevision": snapshot["revisions"]["logicalRevision"],
    }


def wait_job(service, job):
    deadline = time.monotonic() + 40
    while True:
        value = service.http.get("/api/v1/jobs/" + job["jobId"]).json()
        if value["state"] in ("SUCCEEDED", "FAILED", "CANCELLED"):
            return value
        assert time.monotonic() < deadline, value
        time.sleep(0.02)


@pytest.mark.parametrize("surface", SURFACES)
async def test_operation_inventory_and_vertical_slice(factory, surface):
    with factory(holds=("startup",), ready=False) as service:
        service.entered("startup")
        api = Surface(service, surface)
        try:
            assert (await api.call("getLiveness", expected=200))["status"] == "ALIVE"
            assert (await api.call("getStatus", expected=200))["state"] == "LOADING"
            await api.call("getProject", expected=503)
            service.release("startup")
            service.ready()
            assert (await api.call("getStatus", expected=200))["state"] == "READY"
            caps = await api.call("getCapabilities", expected=200)
            assert (
                caps["serverVersion"] == "0.1.0-alpha.1"
                and caps["jadxVersion"] == "1.5.6"
            )
            capabilities = {c["name"]: c["status"] for c in caps["capabilities"]}
            assert capabilities["edit.local_rename"] == "UNSUPPORTED"
            assert capabilities["edit.related_propagation"] == "PARTIAL"
            assert capabilities["edit.parameter_rename"] == "SUPPORTED"
            snapshot = await api.call("getProject", expected=200)
            assert not snapshot["dirty"]
            assert snapshot["revisions"]["logicalRevision"] == 0
            await api.call("getProjectSettings", expected=200)
            first = await api.call("listClasses", query={"pageSize": 1}, expected=200)
            assert len(first["items"]) == 1 and first["nextCursor"]
            second = await api.call(
                "listClasses",
                query={"pageSize": 1, "cursor": first["nextCursor"]},
                expected=200,
            )
            assert (
                first["items"][0]["ref"]["originalClassDescriptor"]
                < second["items"][0]["ref"]["originalClassDescriptor"]
            )
            for ref in (CLASS, METHOD, FIELD):
                resolved = await api.call(
                    "resolveSymbol", body={"ref": ref}, expected=200
                )
                assert resolved["outcome"] == "RESOLVED" and resolved["symbol"]["ref"][
                    "originalName"
                ] == ref.get("originalName")
            missing = await api.call(
                "resolveSymbol",
                body={
                    "ref": {
                        "kind": "CLASS",
                        "originalClassDescriptor": "Lmissing/None;",
                    }
                },
                expected=200,
            )
            assert missing["outcome"] == "NOT_FOUND"
            ambiguous = await api.call(
                "resolveSymbol",
                body={"ref": {**CLASS, "inputIdentity": "sha256:" + "0" * 64}},
                expected=200,
            )
            assert ambiguous["outcome"] == "PROVENANCE_UNAVAILABLE"
            code = await api.call("decompileJava", body={"ref": CLASS}, expected=200)
            assert (
                code["sourceSnapshotId"] and code["variables"] and code["annotations"]
            )
            method = await api.call("decompileJava", body={"ref": METHOD}, expected=200)
            assert method["methodRangeAvailable"] and method["methodSource"]
            temporary = await api.call(
                "decompileJava",
                body={"ref": CLASS, "decompilationMode": "SIMPLE"},
                expected=200,
            )
            assert (
                temporary["effectiveSettings"]["fingerprint"]
                != code["effectiveSettings"]["fingerprint"]
            )
            refs = await api.call(
                "queryReferences",
                body={"ref": FAN, "direction": "OUTGOING", "pageSize": 1},
                expected=200,
            )
            assert (
                refs["coverage"]["status"] == "PARTIAL"
                and refs["diagnostics"]
                and refs["nextCursor"]
            )
            await api.call(
                "queryReferences",
                body={"ref": FAN, "direction": "OUTGOING", "strict": True},
                expected=409,
            )
            await api.call(
                "decompileJava",
                body={"ref": CLASS, "representation": "SMALI"},
                expected=422,
            )
            await api.call(
                "decompileJava",
                body={"ref": CLASS, "includeRawDebugLines": True, "strict": True},
                expected=409,
            )
            search = await api.call(
                "search",
                body={"query": "Hierarchy", "domains": ["CLASS_NAME"], "pageSize": 1},
                expected=200,
            )
            assert search["hits"] and search["nextCursor"]
            partial = await api.call(
                "search",
                body={"query": "single", "domains": ["MEMBER_NAME"]},
                expected=200,
            )
            assert any(c["state"] != "COMPLETE" for c in partial["coverage"])
            job = await api.call(
                "search",
                body={
                    "query": "qualification",
                    "domains": ["SOURCE_TEXT"],
                    "requireComplete": True,
                },
                expected=202,
            )
            terminal = wait_job(service, job)
            assert terminal["state"] == "SUCCEEDED", terminal
            polled = await api.call("getJob", job_id=job["jobId"], expected=200)
            assert_fidelity(terminal, polled)
            events = await api.call("getJobEvents", job_id=job["jobId"], expected=200)
            assert (
                events[-1]["state"] == polled["state"]
                and events[-1]["jobId"] == polled["jobId"]
            )
            replay = await api.call(
                "getJobEvents",
                job_id=job["jobId"],
                headers={"Last-Event-ID": str(events[-2]["sequence"])},
                expected=200,
            )
            assert_fidelity(events[-1:], replay)
            cancelled = await api.call("cancelJob", job_id=job["jobId"], expected=200)
            assert cancelled["state"] == "SUCCEEDED"
            for event_id in ("bad", "999999"):
                await api.call(
                    "getJobEvents",
                    job_id=job["jobId"],
                    headers={"Last-Event-ID": event_id},
                    expected=400,
                )
            await api.call("getJob", job_id=uuid4(), expected=404)
            await api.call("getJobEvents", job_id=uuid4(), expected=404)
            await api.call("cancelJob", job_id=uuid4(), expected=404)
            status = await api.call(
                "buildSearchIndex", body={"domains": ["SOURCE_TEXT"]}, expected=200
            )
            assert (
                status["logicalRevision"] == 0
                and status["indexSnapshotId"]
                and status["indexGeneration"] >= 1
            )
            rebuilt = await api.call(
                "buildSearchIndex",
                body={"domains": ["MEMBER_NAME"], "force": True},
                expected=202,
            )
            assert wait_job(service, rebuilt)["state"] == "SUCCEEDED"
            complete = await api.call(
                "search",
                body={"query": "single", "domains": ["MEMBER_NAME"], "strict": True},
                expected=200,
            )
            assert all(c["state"] == "COMPLETE" for c in complete["coverage"])
            # Export creates an artifact, not native save. Import stages native intent.
            export = await api.call(
                "exportMappings",
                body={
                    "targetPath": str(service.root / "before.tiny"),
                    "format": "TINY_V2",
                    **pre(service),
                },
                expected=200,
            )
            assert export["projectMutated"] is False
            mapping = service.root / "import Ž space.tiny"
            mapping.write_text(
                "tiny\t2\t0\toriginal\tmapped\nc\tprobe/Unicode\tprobe/MappedUnicode\n"
            )
            receipt = await api.call(
                "importMappings",
                body={
                    "sourcePath": str(mapping),
                    "format": "TINY_V2",
                    "mode": "MERGE_FAIL_ON_CONFLICT",
                    **pre(service),
                },
                expected=200,
            )
            assert receipt["outcome"] == "APPLIED" and not receipt["saved"]
            pending = await api.call("exportPendingEdits", expected=200)
            assert pending["dirty"] and pending["codeData"]["renames"]
            assert not (service.root / "saved.jadx").exists()
            await api.call(
                "saveProject",
                body={"targetPath": str(service.root / "saved.jadx"), **pre(service)},
                expected=200,
            )
            await api.call(
                "updateProjectSettings",
                body={"mappingsPath": None, **pre(service)},
                expected=200,
            )
            # A full batch validates before committing once. Native source is unchanged until save.
            items = [
                {"kind": "RENAME", "target": ref, "newName": name}
                for ref, name in (
                    (CLASS, "QualifiedVariables"),
                    (METHOD, "qualifiedSingle"),
                    (FIELD, "qualifiedField"),
                )
            ]
            items.append(
                {
                    "kind": "SET_COMMENT",
                    "target": CLASS,
                    "style": "LINE",
                    "comment": "Živjo 😀 qualified",
                }
            )
            before = pre(service)
            applied = await api.call(
                "applyEditBatch", body={"items": items, **before}, expected=200
            )
            assert (
                applied["outcome"] == "APPLIED"
                and applied["logicalRevisionAfter"]
                == before["expectedLogicalRevision"] + 1
                and not applied["saved"]
            )
            assert all(i["status"] == "APPLIED" for i in applied["items"])
            source = await api.call("decompileJava", body={"ref": CLASS}, expected=200)
            assert "QualifiedVariables" in source["source"] and "😀" in source["source"]
            stable_pending = service.http.post(
                "/api/v1/project/pending-edits/export", json={}
            ).json()
            isolated = await api.call(
                "decompileJava",
                body={"ref": CLASS, "decompilationMode": "SIMPLE"},
                expected=200,
            )
            assert (
                "QualifiedVariables" in isolated["source"]
                and "😀" in isolated["source"]
            )
            assert (
                service.http.post(
                    "/api/v1/project/pending-edits/export", json={}
                ).json()
                == stable_pending
            )
            assert (await api.call("decompileJava", body={"ref": CLASS}, expected=200))[
                "sourceSnapshotId"
            ] == source["sourceSnapshotId"]
            for operation, arguments in (
                (
                    "listClasses",
                    {"query": {"pageSize": 1, "cursor": first["nextCursor"]}},
                ),
                (
                    "search",
                    {
                        "body": {
                            "query": "Hierarchy",
                            "domains": ["CLASS_NAME"],
                            "pageSize": 1,
                            "cursor": search["nextCursor"],
                        }
                    },
                ),
                (
                    "queryReferences",
                    {
                        "body": {
                            "ref": FAN,
                            "direction": "OUTGOING",
                            "pageSize": 1,
                            "cursor": refs["nextCursor"],
                        }
                    },
                ),
                (
                    "decompileJava",
                    {
                        "body": {
                            "ref": CLASS,
                            "expectedSourceSnapshotId": code["sourceSnapshotId"],
                        }
                    },
                ),
                ("applyEditBatch", {"body": {"items": items, **before}}),
            ):
                await api.call(operation, expected=409, **arguments)
            before_export = pre(service)
            current_export = await api.call(
                "exportMappings",
                body={
                    "targetPath": str(service.root / "qualified.tiny"),
                    "format": "TINY_V2",
                    **before_export,
                },
                expected=200,
            )
            assert (
                not current_export["projectMutated"] and pre(service) == before_export
            )
            import hashlib

            assert hashlib.sha256(
                (service.root / "qualified.tiny").read_bytes()
            ).hexdigest() == current_export["sha256"].removeprefix("sha256:")
            assert not current_export["omissions"]
            param = {
                "kind": "RENAME_PARAMETER",
                "method": METHOD,
                "parameterIndex": 0,
                "sourceSnapshotId": source["sourceSnapshotId"],
                "newName": "qualifiedInput",
            }
            result = await api.call(
                "applyEditBatch", body={"items": [param], **pre(service)}, expected=200
            )
            assert result["outcome"] == "APPLIED"
            propagated = await api.call(
                "applyEditBatch",
                body={
                    "items": [
                        {
                            "kind": "RENAME",
                            "target": GROUP,
                            "newName": "qualifiedJoined",
                            "propagateRelated": True,
                        }
                    ],
                    **pre(service),
                },
                expected=200,
            )
            affected = propagated["items"][0]["affectedRefs"]
            assert len(affected) == 4
            assert {r["originalClassDescriptor"] for r in affected} == {
                "Lrelated/Hierarchy$" + n + ";"
                for n in ("ExtendedLeft", "Joined", "SeparateLeft", "SeparateRight")
            }
            rejected_export = await api.call(
                "exportMappings",
                body={
                    "targetPath": str(service.root / "scoped-unavailable.tiny"),
                    "format": "TINY_V2",
                    **pre(service),
                },
                expected=422,
            )
            assert rejected_export["error"]["details"]["omissions"]
            assert not (service.root / "scoped-unavailable.tiny").exists()
            saved = await api.call(
                "saveProject",
                body={"targetPath": str(service.root / "saved.jadx"), **pre(service)},
                expected=200,
            )
            assert not saved["dirty"]
            native = json.loads((service.root / "saved.jadx").read_text())
            assert (
                len(
                    [
                        r
                        for r in native["codeData"]["renames"]
                        if r["newName"] == "qualifiedJoined"
                    ]
                )
                == 4
            )
            refreshed = await api.call(
                "reloadProject",
                body={"discardUnsaved": False, **pre(service)},
                expected=200,
            )
            assert not refreshed["dirty"]
            await api.call(
                "shutdownService", body={"policy": "refuse_if_dirty"}, expected=202
            )
            assert service.process.wait(timeout=10) == 0
            root = service.root
        finally:
            await api.close()
    with factory(kind="saved", root=root) as restarted:
        api = Surface(restarted, surface)
        try:
            code = await api.call("decompileJava", body={"ref": CLASS}, expected=200)
            assert (
                "QualifiedVariables" in code["source"]
                and "qualifiedInput" in code["source"]
            )
            assert (await api.call("getProject"))["revisions"]["sessionId"] != snapshot[
                "revisions"
            ]["sessionId"]
            await api.call("getJob", job_id=job["jobId"], expected=404)
            assert set(OPERATIONS) <= set(ROUTES)
            assert all(surface in ROUTES[op]["surfaces"] for op in OPERATIONS)
        finally:
            await api.close()


def test_wrong_methods_fixed_project_and_wire_validation(factory):
    with factory() as service:
        for operation, (method, path, _) in OPERATIONS.items():
            path = path.replace("{jobId}", str(uuid4()))
            wrong = "DELETE"
            response = service.http.request(wrong, "/api/v1" + path)
            assert (
                response.status_code == 405
                and response.json()["error"]["code"] == "METHOD_NOT_ALLOWED"
            )
            assert method in response.headers["allow"]
            row = ROUTES.setdefault(operation, {})
            row["negative_case"] = row["error_checked"] = True
        for path in ("/project/open", "/project/switch", "/upload"):
            assert service.http.post("/api/v1" + path, json={}).status_code == 404
        for body in (
            '{"query":"a","unknown":true}',
            '{"query":',
            '{"query":"a","query":"b"}',
            "[]",
        ):
            response = service.http.post(
                "/api/v1/search",
                content=body,
                headers={"Content-Type": "application/json"},
            )
            assert (
                response.status_code == 400
                and response.json()["error"]["code"] == "INVALID_REQUEST"
            )
        assert (
            service.http.post(
                "/api/v1/search", content="{}", headers={"Content-Type": "text/plain"}
            ).status_code
            == 415
        )
        assert (
            service.http.post(
                "/api/v1/search",
                content=" " * 65537,
                headers={"Content-Type": "application/json"},
            ).status_code
            == 400
        )
        assert (
            "access-control-allow-origin"
            not in service.http.get("/api/v1/project").headers
        )
        for query in (
            "pageSize=-1",
            "pageSize=201",
            "pageSize=1&pageSize=2",
            "cursor=bad",
            "cursor=" + "a" * 5000,
        ):
            assert service.http.get("/api/v1/classes?" + query).status_code == 400
