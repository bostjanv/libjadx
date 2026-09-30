"""Deterministic lifecycle/admission/cancellation through the installed Java service."""

import asyncio
import concurrent.futures
import json
from contextlib import closing

import pytest
from release_support import SURFACES, Surface, assert_fidelity
from test_routes import CLASS, METHOD, pre, wait_job

from libjadx import AsyncClient, Client
from libjadx.errors import JobWaitTimeout, RequestTimeoutError, ServiceLoadFailed


@pytest.mark.parametrize("surface", SURFACES)
async def test_loading_failed_and_disabled_hooks(factory, surface):
    with factory(holds=("startup",), ready=False) as service:
        service.entered("startup")
        api = Surface(service, surface)
        try:
            assert (await api.call("getLiveness", expected=200))["status"] == "ALIVE"
            status = await api.call("getStatus", expected=200)
            assert (
                status["state"] == "LOADING"
                and status["stage"]
                and status["progress"]["total"] >= 1
            )
            await api.call("getProject", expected=503)
            async with AsyncClient(service.url) as observer:
                pending = asyncio.create_task(
                    observer.wait_ready(timeout=30, poll_interval=0.02)
                )
                await asyncio.sleep(0.08)
                assert not pending.done()
                service.release("startup")
                assert (await pending).state == "READY"
        finally:
            await api.close()
    with factory(faults=("startup",), ready=False) as service:
        with Client(service.url) as observer:
            with pytest.raises(ServiceLoadFailed) as failed:
                observer.wait_ready()
            assert failed.value.error.code == "PROJECT_LOAD_FAILED"
        api = Surface(service, surface)
        try:
            assert (await api.call("getStatus"))["state"] == "FAILED"
            assert (await api.call("getLiveness"))["status"] == "ALIVE"
            await api.call("getProject", expected=503)
            await api.call("decompileJava", body={"ref": CLASS}, expected=503)
            # The current shutdown contract requires READY. FAILED stays diagnostic;
            # SIGTERM exercises the ordinary JVM cleanup path without forced kill.
            await api.call("shutdownService", body={"policy": "discard"}, expected=503)
            service.process.terminate()
            assert service.process.wait(timeout=10) == 143
        finally:
            await api.close()
    with factory(faults=("startup",), hooks=False) as service:
        assert service.http.get("/api/v1/status").json()["state"] == "READY"


@pytest.mark.parametrize("surface", SURFACES)
@pytest.mark.parametrize("point", ("dispatch", "job"))
async def test_nonterminal_cancellation_and_busy_shutdown(factory, surface, point):
    with factory(holds=(point,)) as service:
        api = Surface(service, surface)
        try:
            job = await api.call(
                "buildSearchIndex", body={"domains": ["SOURCE_TEXT"]}, expected=202
            )
            service.entered(point)
            held = await api.call("getJob", job_id=job["jobId"], expected=200)
            assert held["state"] == ("QUEUED" if point == "dispatch" else "RUNNING")
            for policy in ("discard", "save", "refuse_if_dirty"):
                busy = await api.call(
                    "shutdownService", body={"policy": policy}, expected=409
                )
                assert busy["error"]["code"] == "PROJECT_BUSY"
                details = busy["error"]["details"]
                assert details["activeJobCount"] >= 1 and len(details["jobs"]) <= 16
                assert any(str(j["jobId"]) == job["jobId"] for j in details["jobs"])
                assert service.http.get("/api/v1/health/live").status_code == 200
            if point == "job":
                # Search owns CLASS_READ; writes and other Jadx reads fail fast.
                for operation, arguments in (
                    (
                        "applyEditBatch",
                        {
                            "items": [
                                {"kind": "RENAME", "target": CLASS, "newName": "Busy"}
                            ],
                            **pre(service),
                        },
                    ),
                    ("saveProject", {}),
                    ("reloadProject", {"discardUnsaved": True, **pre(service)}),
                    ("updateProjectSettings", {"mappingsPath": None, **pre(service)}),
                    ("decompileJava", {"ref": CLASS}),
                ):
                    await api.call(operation, body=arguments, expected=409)
            cancelled = await api.call("cancelJob", job_id=job["jobId"], expected=200)
            assert cancelled["state"] == (
                "QUEUED" if point == "dispatch" else "CANCELLING"
            )
            again = await api.call("cancelJob", job_id=job["jobId"], expected=200)
            assert again["state"] == cancelled["state"]
            # A gate owning work cannot truthfully report completion before release.
            assert service.process.poll() is None
            service.release(point)
            terminal = wait_job(service, job)
            assert (
                terminal["state"] == "CANCELLED"
                and terminal["cancellationReason"] == "USER"
            )
            events = await api.call("getJobEvents", job_id=job["jobId"], expected=200)
            assert events[-1]["state"] == terminal["state"]
            assert any(e["type"] == "job.cancel_requested" for e in events)
        finally:
            await api.close()


@pytest.mark.parametrize("surface", SURFACES)
async def test_real_failed_job_and_internal_fault_fidelity(factory, surface):
    with factory(faults=("job",)) as service:
        api = Surface(service, surface)
        try:
            job = await api.call(
                "buildSearchIndex", body={"domains": ["SOURCE_TEXT"]}, expected=202
            )
            terminal = wait_job(service, job)
            assert (
                terminal["state"] == "FAILED"
                and terminal["error"]["code"] == "INTERNAL_ERROR"
            )
            polled = await api.call("getJob", job_id=job["jobId"], expected=200)
            assert_fidelity(terminal, polled)
            before = service.http.get("/api/v1/project").json()
            (service.hooks / "http-project.fail").touch()
            error = await api.call("getProject", expected=500)
            assert error["error"]["code"] == "INTERNAL_ERROR"
            assert "Exception" not in error["error"]["message"]
            (service.hooks / "http-project.fail").unlink()
            assert service.http.get("/api/v1/project").json() == before
        finally:
            await api.close()


async def test_client_deadlines_task_and_stream_close_do_not_cancel(factory):
    with factory(holds=("job",)) as service:
        async with AsyncClient(service.url) as client:
            job = await client.project.build_search_index()
            service.entered("job")
            with pytest.raises(JobWaitTimeout):
                await job.wait(timeout=0.02, poll_interval=0.01)
            with pytest.raises(JobWaitTimeout):
                async for _ in job.poll_updates(timeout=0.02, poll_interval=0.01):
                    pass
            waiter = asyncio.create_task(job.wait(timeout=30, poll_interval=0.01))
            await asyncio.sleep(0.02)
            waiter.cancel()
            with pytest.raises(asyncio.CancelledError):
                await waiter
            events = job.events()
            assert (await anext(events)).state in ("QUEUED", "RUNNING")
            await events.aclose()
            assert (await job.refresh()).state == "RUNNING"
            with Client(service.url) as sync:
                sync_job = sync.job(job.id)
                with pytest.raises(JobWaitTimeout):
                    sync_job.wait(timeout=0.02, poll_interval=0.01)
                with pytest.raises(JobWaitTimeout):
                    list(sync_job.poll_updates(timeout=0.02, poll_interval=0.01))
                with closing(sync_job.events()) as frames:
                    next(frames)
                assert sync_job.refresh().state == "RUNNING"
            await job.cancel()
            assert (await job.refresh()).state == "CANCELLING"
            service.release("job")
            assert wait_job(service, {"jobId": str(job.id)})["state"] == "CANCELLED"
        service.hold("http-project")
        with Client(service.url, timeout=0.05) as slow:
            with pytest.raises(RequestTimeoutError):
                slow.project.snapshot()
        service.entered("http-project")
        service.release("http-project")
        assert service.http.get("/api/v1/project").status_code == 200


@pytest.mark.parametrize("surface", SURFACES)
async def test_partial_batch_failure_has_exact_prefix(factory, surface):
    with factory(faults=("edit-item-1",)) as service:
        api = Surface(service, surface)
        try:
            result = await api.call(
                "applyEditBatch",
                body={
                    "items": [
                        {"kind": "RENAME", "target": CLASS, "newName": "PrefixOnly"},
                        {"kind": "RENAME", "target": METHOD, "newName": "MustFail"},
                        {
                            "kind": "SET_COMMENT",
                            "target": CLASS,
                            "style": "LINE",
                            "comment": "MustSkip",
                        },
                    ],
                    **pre(service),
                },
                expected=200,
            )
            assert result["outcome"] == "PARTIAL"
            assert [item["status"] for item in result["items"]] == [
                "APPLIED",
                "FAILED",
                "SKIPPED",
            ]
            assert [item["index"] for item in result["items"]] == [0, 1, 2]
            assert result["items"][1]["message"] and result["logicalRevisionAfter"] == 1
            assert not (service.root / "saved.jadx").exists()
            await api.call(
                "saveProject",
                body={"targetPath": str(service.root / "saved.jadx"), **pre(service)},
                expected=200,
            )
            native = json.loads((service.root / "saved.jadx").read_text())
            assert len(native["codeData"]["renames"]) == 1
            assert native["codeData"]["renames"][0]["newName"] == "PrefixOnly"
            assert not native["codeData"]["comments"]
        finally:
            await api.close()


@pytest.mark.parametrize("surface", SURFACES)
async def test_same_different_class_and_mutation_admission(factory, surface):
    with factory() as service:
        api = Surface(service, surface)
        service.hold("primary-read")
        with concurrent.futures.ThreadPoolExecutor() as executor:
            # Raw owned request holds the real primary lease; tested surface competes.
            running = executor.submit(
                service.http.post, "/api/v1/decompile", json={"ref": CLASS}
            )
            service.entered("primary-read")
            try:
                for ref in (
                    CLASS,
                    {"kind": "CLASS", "originalClassDescriptor": "Lprobe/Unicode;"},
                ):
                    await api.call("decompileJava", body={"ref": ref}, expected=409)
                await api.call(
                    "applyEditBatch",
                    body={
                        "items": [
                            {"kind": "RENAME", "target": CLASS, "newName": "Busy"}
                        ],
                        **pre(service),
                    },
                    expected=409,
                )
                await api.call(
                    "shutdownService", body={"policy": "discard"}, expected=409
                )
            finally:
                service.release("primary-read")
                assert running.result(timeout=30).status_code == 200
        await api.close()
        service.hold("edit")
        with concurrent.futures.ThreadPoolExecutor() as executor:
            body = {
                "items": [
                    {"kind": "RENAME", "target": CLASS, "newName": "PublishedWhole"}
                ],
                **pre(service),
            }
            running = executor.submit(
                service.http.post, "/api/v1/edits/batch", json=body
            )
            service.entered("edit")
            try:
                assert (
                    service.http.post("/api/v1/edits/batch", json=body).status_code
                    == 409
                )
                assert (
                    service.http.post(
                        "/api/v1/decompile", json={"ref": CLASS}
                    ).status_code
                    == 409
                )
                assert (
                    service.http.get("/api/v1/project").json()["revisions"][
                        "logicalRevision"
                    ]
                    == 0
                )
            finally:
                service.release("edit")
                assert running.result(timeout=30).json()["outcome"] == "APPLIED"
        assert (
            "PublishedWhole"
            in service.http.post("/api/v1/decompile", json={"ref": CLASS}).json()[
                "source"
            ]
        )
