import json
from uuid import uuid4

import pytest

from libjadx import (
    AsyncClient,
    AsyncJob,
    AsyncSearchCursor,
    Client,
    Job,
    SearchCursor,
    SymbolRef,
)
from libjadx._generated.api.analysis import decompile_java
from libjadx._generated.api.service import get_capabilities, get_liveness
from libjadx._generated.api.symbols import list_classes, resolve_symbol
from libjadx.errors import (
    ExternalModificationConflictError,
    IncompleteAnalysisError,
    InvalidRequestError,
    NotFoundError,
    StaleRevisionError,
)
from libjadx.lowlevel import models as m

pytestmark = pytest.mark.integration
REF = SymbolRef.class_("Lprobe/Sample;")
METHOD = SymbolRef.method("Lprobe/Sample;", "answer", "()I")


def test_generated_lowlevel_real_service(service):
    with service() as (url, root), Client(url) as client:
        assert get_liveness.sync(client=client.lowlevel).status == "ALIVE"
        assert isinstance(get_capabilities.sync(client=client.lowlevel), m.Capabilities)
        page = list_classes.sync(client=client.lowlevel, page_size=1)
        assert isinstance(page, m.ClassPage) and page.items[0].ref == REF
        result = resolve_symbol.sync(
            client=client.lowlevel, body=m.SymbolResolveRequest(METHOD)
        )
        assert result.outcome == "RESOLVED" and result.symbol.ref == METHOD
        code = decompile_java.sync(client=client.lowlevel, body=m.DecompileRequest(REF))
        assert isinstance(code, m.DecompileResult) and "42" in code.source
        # Stable unsupported 422 exercised through generated endpoint too.
        error = decompile_java.sync_detailed(
            client=client.lowlevel, body=m.DecompileRequest(REF, representation="SMALI")
        )
        assert error.status_code == 422 and isinstance(error.parsed, m.ErrorEnvelope)


def test_sync_workflow_save_restart(service):
    with service() as (url, root), Client(url) as client:
        client.wait_ready()
        project = client.project
        before = project.snapshot()
        assert not before.dirty
        classes = list(project.classes(page_size=1))
        assert classes[0].ref == REF
        assert project.resolve(METHOD).outcome == "RESOLVED"
        assert "42" in project.decompile(METHOD).source
        assert classes[0].decompile().source
        cursor = project.search("Sample", domains=["CLASS_NAME"])
        assert isinstance(cursor, SearchCursor) and list(cursor)
        result = project.rename(classes[0], "SdkSample")
        assert result.outcome == "APPLIED" and not result.saved
        assert project.snapshot().dirty and not (root / "saved.jadx").exists()
        assert "SdkSample" in project.decompile(REF).source
        saved = project.save(str(root / "saved.jadx"))
        assert not saved.dirty
        native = json.loads((root / "saved.jadx").read_text())
        assert native["codeData"]["renames"][0]["newName"] == "SdkSample"
        assert client.shutdown("refuse_if_dirty").policy == "refuse_if_dirty"
    with service(root / "saved.jadx") as (url, root), Client(url) as client:
        assert "SdkSample" in client.project.decompile(REF).source
        client.project.snapshot()
        assert not client.project.reload().dirty


async def test_async_workflow_and_generated_async(service):
    with service() as (url, root):
        async with AsyncClient(url) as client:
            await client.wait_ready()
            assert (
                await get_liveness.asyncio(client=client.lowlevel)
            ).status == "ALIVE"
            assert await client.capabilities()
            project = client.project
            await project.snapshot()
            classes = [cls async for cls in project.classes(page_size=1)]
            assert classes[0].ref == REF
            assert (await project.resolve(METHOD)).outcome == "RESOLVED"
            assert (await classes[0].decompile()).source
            result = await project.search("Sample", domains=["CLASS_NAME"])
            assert isinstance(result, AsyncSearchCursor)
            assert [hit async for hit in result]
            refs = await project.references(METHOD)
            assert refs.coverage.status == "PARTIAL" and refs.diagnostics
            with pytest.raises(IncompleteAnalysisError):
                await project.references(METHOD, strict=True)
            assert (
                await project.rename(classes[0], "AsyncSample")
            ).outcome == "APPLIED"
            assert (await project.snapshot()).dirty
            assert not (root / "async.jadx").exists()
            assert not (await project.save(str(root / "async.jadx"))).dirty
            await client.shutdown("refuse_if_dirty")


def test_two_client_stale_revision_no_retry(service):
    with service() as (url, root), Client(url) as a, Client(url) as b:
        a.project.snapshot()
        b.project.snapshot()
        observed = b.project.logical_revision
        a.project.rename(REF, "ClientA")
        with pytest.raises(StaleRevisionError) as caught:
            b.project.rename(REF, "ClientB")
        assert caught.value.http_status == 409 and caught.value.code == "STALE_REVISION"
        assert b.project.logical_revision == observed
        assert "ClientA" in a.project.decompile(REF).source
        assert "ClientB" not in a.project.decompile(REF).source
        b.project.refresh()
        assert b.project.logical_revision == observed + 1
        b.project.rename(REF, "ClientB")
        assert a.project.snapshot().revisions.logical_revision == observed + 2


async def test_async_stale_revision(service):
    with service() as (url, root):
        async with AsyncClient(url) as a, AsyncClient(url) as b:
            await a.project.snapshot()
            await b.project.snapshot()
            await a.project.rename(REF, "ClientA")
            with pytest.raises(StaleRevisionError):
                await b.project.rename(REF, "ClientB")
            await b.project.refresh()
            await b.project.rename(REF, "ClientB")


def test_external_modification_preserves_pending_details(service):
    with service() as (url, root), Client(url) as client:
        client.project.snapshot()
        client.project.save(str(root / "external.jadx"))
    with service(root / "external.jadx") as (url, root), Client(url) as client:
        client.project.snapshot()
        client.project.rename(REF, "PendingSample")
        native = root / "external.jadx"
        native.write_text(native.read_text() + "\n")
        with pytest.raises(ExternalModificationConflictError) as caught:
            client.project.save()
        assert caught.value.code == "EXTERNAL_MODIFICATION_CONFLICT"
        assert caught.value.retryable is False
        assert client.project.pending_edits().dirty
        assert client.project.snapshot().dirty


def test_partial_strict_references_and_errors(service):
    with service() as (url, root), Client(url) as client:
        result = client.project.references(METHOD)
        assert result.coverage.status == "PARTIAL" and result.diagnostics
        with pytest.raises(IncompleteAnalysisError) as caught:
            client.project.references(METHOD, strict=True)
        assert caught.value.code == "INCOMPLETE_ANALYSIS"
        with pytest.raises(IncompleteAnalysisError):
            client.project.decompile(REF, include_raw_debug_lines=True, strict=True)
        with pytest.raises(NotFoundError):
            client.job(uuid4())
        with pytest.raises(InvalidRequestError):
            client.project.resolve(SymbolRef.class_("bad"))


def test_real_search_job_sse_poll_cancel(service):
    with service() as (url, root), Client(url) as client:
        result = client.project.search(
            "42", domains=["SOURCE_TEXT"], require_complete=True
        )
        assert isinstance(result, Job)
        events = list(result.events())
        assert events[0].type_ == "job.queued" and events[-1].state == "SUCCEEDED"
        assert [e.sequence for e in events] == sorted(set(e.sequence for e in events))
        assert result.wait(timeout=30).state == "SUCCEEDED"
        assert result.result is not None
        # Completed-before-cancel stays successful; do not fabricate CANCELLED.
        assert result.cancel().state == "SUCCEEDED"
        cursor = client.project.search("42", domains=["SOURCE_TEXT"], strict=True)
        assert cursor.complete and list(cursor)
        assert (
            list(result.events(last_event_id=str(events[-2].sequence)))[-1].sequence
            == events[-1].sequence
        )


async def test_async_real_job_sse_wait_cancel(service):
    with service() as (url, root):
        async with AsyncClient(url) as client:
            result = await client.project.build_search_index()
            assert isinstance(result, AsyncJob)
            events = [e async for e in result.events()]
            assert events[-1].state == "SUCCEEDED"
            assert (await result.wait(timeout=30)).state == "SUCCEEDED"
            assert (await result.cancel()).state == "SUCCEEDED"


def test_mapping_import_updates_cached_revision_before_save(service):
    with service() as (url, root), Client(url) as client:
        client.project.snapshot()
        mapping = root / "import.tiny"
        mapping.write_text(
            "tiny\t2\t0\toriginal\tmapped\nc\tprobe/Sample\tprobe/MappedSample\n"
        )
        receipt = client.project.import_mappings(str(mapping))
        assert receipt.outcome == "APPLIED" and not receipt.saved
        assert client.project.logical_revision == receipt.after_logical_revision
        assert client.project.index_revision == receipt.after_index_revision
        exported = client.project.export_mappings(str(root / "export.tiny"))
        assert exported.project_mutated is False
        assert "MappedSample" in (root / "export.tiny").read_text()
        assert not client.project.save(str(root / "mapped.jadx")).dirty
