import asyncio
import inspect
import json
from types import SimpleNamespace
from uuid import UUID

import httpx
import pytest

import libjadx
from libjadx import (
    AsyncClient,
    AsyncJob,
    AsyncSearchCursor,
    Client,
    Job,
    SearchCursor,
    SymbolRef,
)
from libjadx._common import RevisionCache
from libjadx._sse import MAX_LINE, Parser
from libjadx.errors import (
    ERROR_TYPES,
    EventHistoryExpiredError,
    JobCancelled,
    JobFailed,
    JobWaitTimeout,
    ServiceLoadFailed,
    StaleRevisionError,
    TransportError,
    UnexpectedResponseError,
    WaitReadyTimeout,
)
from libjadx.lowlevel import models as m

URL = "http://127.0.0.1:18777"


async def call(value):
    return await value if inspect.isawaitable(value) else value


async def collect(iterator):
    if hasattr(iterator, "__aiter__"):
        return [item async for item in iterator]
    return list(iterator)


@pytest.fixture(params=[False, True], ids=["sync", "async"])
async def sdk(request):
    clients = []

    def make(handler):
        http_type = httpx.AsyncClient if request.param else httpx.Client
        sdk_type = AsyncClient if request.param else Client
        http = http_type(
            base_url=URL + "/api/v1", transport=httpx.MockTransport(handler)
        )
        client = sdk_type(URL, http_client=http)
        clients.append(client)
        return client

    yield make
    for client in clients:
        await call(
            client.aclose() if isinstance(client, AsyncClient) else client.close()
        )


def status(state, error=None):
    result = {
        "state": state,
        "stage": "loading",
        "progress": {"completed": 0, "total": 0, "unit": "files"},
        "inputs": [],
    }
    if error:
        result["error"] = error
    return result


def error(code, message="failure"):
    return {
        "error": {
            "code": code,
            "message": message,
            "retryable": True,
            "requestId": "body-id",
            "details": {"x": [None, {"n": 3}]},
            "causes": [{"code": "PROJECT_BUSY", "message": "cause"}],
            "itemErrors": [
                {"index": 1, "code": code, "message": "item", "details": {"y": True}}
            ],
        }
    }


@pytest.mark.parametrize("name", libjadx.__all__)
def test_public_imports(name):
    assert getattr(libjadx, name)


@pytest.mark.parametrize("url", [URL, URL + "/", URL + "/api/v1", URL + "/api/v1/"])
def test_base_url_construction_no_network(url):
    client = Client(url)
    assert client.base_url == URL + "/api/v1"
    client.close()
    client.close()


async def test_readiness_context_cleanup_no_mutation(sdk):
    seen = []
    states = iter(["LOADING", "READY"])

    def handler(req):
        seen.append((req.method, req.url.path))
        return httpx.Response(200, json=status(next(states)))

    client = sdk(handler)
    assert not seen
    if isinstance(client, AsyncClient):
        async with client:
            assert (await client.wait_ready(poll_interval=0.001)).state == "READY"
        await client.aclose()
        assert client.lowlevel.get_async_httpx_client().is_closed
    else:
        with client:
            assert client.wait_ready(poll_interval=0.001).state == "READY"
        client.close()
        assert client.lowlevel.get_httpx_client().is_closed
    assert seen == [("GET", "/api/v1/status")] * 2
    with pytest.raises(TransportError):
        await call(client.status())


async def test_context_exception_cleanup(sdk):
    client = sdk(lambda r: pytest.fail("Cleanup must not call server"))
    with pytest.raises(RuntimeError):
        if isinstance(client, AsyncClient):
            async with client:
                raise RuntimeError("test")
        else:
            with client:
                raise RuntimeError("test")
    assert client._closed


async def test_readiness_failed_timeout(sdk):
    client = sdk(
        lambda r: httpx.Response(
            200, json=status("FAILED", error("PROJECT_LOAD_FAILED")["error"])
        )
    )
    with pytest.raises(ServiceLoadFailed) as caught:
        await call(client.wait_ready())
    assert caught.value.error.details == {"x": [None, {"n": 3}]}
    client = sdk(lambda r: httpx.Response(200, json=status("LOADING")))
    with pytest.raises(WaitReadyTimeout):
        await call(client.wait_ready(timeout=0))


@pytest.mark.parametrize("code", ERROR_TYPES)
async def test_every_error_preserves_metadata(sdk, code):
    client = sdk(
        lambda r: httpx.Response(
            409, json=error(code), headers={"X-Request-Id": "header-id"}
        )
    )
    with pytest.raises(ERROR_TYPES[code]) as caught:
        await call(client.project.snapshot())
    exc = caught.value
    assert exc.http_status == 409 and exc.code == code and exc.retryable
    assert exc.details == {"x": [None, {"n": 3}]}
    assert exc.causes[0]["code"] == "PROJECT_BUSY"
    assert exc.item_errors[0]["details"] == {"y": True}
    assert exc.request_id == "body-id" and exc.header_request_id == "header-id"


async def test_stale_mutation_no_retry_and_revision_tracking(sdk, example):
    seen = []
    project = example("project.json")

    def handler(req):
        seen.append(req)
        if req.method == "GET":
            return httpx.Response(200, json=project)
        return httpx.Response(409, json=error("STALE_REVISION"))

    client = sdk(handler)
    await call(client.project.snapshot())
    with pytest.raises(StaleRevisionError):
        await call(client.project.rename(SymbolRef.class_("Lprobe/Sample;"), "Renamed"))
    assert len(seen) == 2
    body = json.loads(seen[-1].content)
    assert body["expectedLogicalRevision"] == project["revisions"]["logicalRevision"]
    assert body["expectedSessionId"] == project["revisions"]["sessionId"]
    assert client.project.logical_revision == 2
    await call(client.project.refresh())
    assert len(seen) == 3


def test_revision_cache_monotonic_across_out_of_order_responses_and_boots():
    cache = RevisionCache()

    def observe(session, logical, index=None):
        cache.observe(
            SimpleNamespace(
                session_id=session, logical_revision=logical, index_revision=index
            )
        )

    observe("a", 7, 9)
    observe("a", 3, 4)
    assert (cache.logical_revision, cache.index_revision) == (7, 9)
    observe("b", 0, 0)
    observe("a", 100, 100)
    assert (cache.session_id, cache.logical_revision, cache.index_revision) == (
        "b",
        0,
        0,
    )


async def test_success_receipts_save_override_unconditional(sdk, example):
    requests = []

    def handler(req):
        requests.append(req)
        if req.url.path.endswith("save"):
            return httpx.Response(200, json=example("project.json"))
        return httpx.Response(200, json=example("edit-batch-applied.json"))

    client = sdk(handler)
    result = await call(
        client.project.rename(SymbolRef.class_("Lprobe/Example;"), "Edited")
    )
    assert result.saved is False and result.outcome == "APPLIED"
    assert client.project.logical_revision == 8 and client.project.index_revision == 8
    await call(
        client.project.rename(
            SymbolRef.class_("Lprobe/Example;"),
            "Edited",
            expected_session_id=None,
            expected_logical_revision=None,
        )
    )
    assert "expectedSessionId" not in json.loads(requests[-1].content)
    await call(
        client.project.rename(
            SymbolRef.class_("Lprobe/Example;"),
            "Edited",
            expected_session_id=UUID(int=1),
            expected_logical_revision=1,
        )
    )
    assert json.loads(requests[-1].content)["expectedLogicalRevision"] == 1
    assert all(not r.url.path.endswith("save") for r in requests)
    await call(client.project.save("/server/sample.jadx"))
    assert json.loads(requests[-1].content)["targetPath"] == "/server/sample.jadx"


async def test_lazy_class_pages_filters_repeated_and_stale(sdk, example):
    seen = []
    page = example("classes-first-page.json")

    def handler(req):
        seen.append(req)
        return httpx.Response(200, json=page)

    client = sdk(handler)
    iterator = client.project.classes(
        package_prefix="probe", name_contains="Fixture", page_size=1
    )
    assert not seen
    with pytest.raises(UnexpectedResponseError):
        await collect(iterator)
    assert len(seen) == 2
    assert seen[1].url.params["cursor"] == page["nextCursor"]
    assert seen[1].url.params["packagePrefix"] == "probe"
    assert seen[1].url.params["nameContains"] == "Fixture"
    seen.clear()

    def stale(req):
        seen.append(req)
        return (
            httpx.Response(200, json=page)
            if len(seen) == 1
            else httpx.Response(409, json=error("STALE_REVISION"))
        )

    client = sdk(stale)
    with pytest.raises(StaleRevisionError):
        await collect(client.project.class_pages())
    assert len(seen) == 2


async def test_search_partial_cursor_and_job(sdk, example):
    page = example("search-partial.json")
    page.update(nextCursor="next", pageComplete=False)
    seen = []

    def handler(req):
        seen.append(req)
        return httpx.Response(200, json=page)

    client = sdk(handler)
    cursor = await call(client.project.search("TODO", domains=["SOURCE_TEXT"]))
    assert isinstance(cursor, (SearchCursor, AsyncSearchCursor)) and not cursor.complete
    assert cursor.coverage[0].pending == 2 and cursor.result_snapshot_id
    with pytest.raises(UnexpectedResponseError):
        await collect(cursor)
    assert len(seen) == 2
    assert cursor.query.query == "TODO" and cursor.query.cursor != "next"
    client = sdk(lambda r: httpx.Response(202, json=example("job-queued.json")))
    result = await call(client.project.search("TODO", require_complete=True))
    assert isinstance(result, (Job, AsyncJob)) and result.state == "QUEUED"


@pytest.mark.parametrize(
    "state,exc",
    [
        ("SUCCEEDED", None),
        ("FAILED", JobFailed),
        ("CANCELLED", JobCancelled),
        ("CANCELLING", JobWaitTimeout),
    ],
)
async def test_job_poll_terminal_and_timeout(sdk, example, state, exc):
    doc = example("job-queued.json")
    seen = []

    def handler(req):
        seen.append(req)
        result = dict(doc, state=state if len(seen) > 1 else "QUEUED")
        return httpx.Response(200, json=result)

    client = sdk(handler)
    job = await call(client.job(doc["jobId"]))
    if exc:
        with pytest.raises(exc):
            await call(job.wait(timeout=0))
    else:
        assert (await call(job.wait(timeout=0))).state == "SUCCEEDED"
    assert len(seen) == 2 and all(r.method == "GET" for r in seen)


async def test_cancel_preserves_actual_state(sdk, example):
    doc = example("job-queued.json")
    client = sdk(
        lambda r: httpx.Response(
            200, json=dict(doc, state="CANCELLING" if r.method == "POST" else "QUEUED")
        )
    )
    job = await call(client.job(doc["jobId"]))
    assert (await call(job.cancel())).state == "CANCELLING"


def frames(example):
    doc = example("job-queued.json")

    def frame(n, type_, state):
        data = {
            "jobId": doc["jobId"],
            "sequence": n,
            "type": type_,
            "at": doc["createdAt"],
            "state": state,
            "progress": doc["progress"],
            "resultUrl": "/api/v1/jobs/" + doc["jobId"],
        }
        return f"id: {n}\nevent: {type_}\ndata: {json.dumps(data)}\n\n".encode()

    return (
        frame(1, "job.queued", "QUEUED")
        + b": heartbeat\n\n"
        + frame(2, "job.completed", "SUCCEEDED")
    )


async def test_sse_replay_bounds_and_semantic_errors(sdk, example):
    doc = example("job-queued.json")
    seen = []

    def handler(req):
        seen.append(req)
        if req.url.path.endswith("events"):
            return httpx.Response(
                200,
                content=frames(example),
                headers={"content-type": "text/event-stream"},
            )
        return httpx.Response(200, json=doc)

    client = sdk(handler)
    job = await call(client.job(doc["jobId"]))
    events = await collect(job.events(last_event_id="0"))
    assert [e.sequence for e in events] == [1, 2]
    assert seen[-1].headers["Last-Event-ID"] == "0"
    client = sdk(
        lambda r: (
            httpx.Response(409, json=error("EVENT_HISTORY_EXPIRED"))
            if r.url.path.endswith("events")
            else httpx.Response(200, json=doc)
        )
    )
    job = await call(client.job(doc["jobId"]))
    with pytest.raises(EventHistoryExpiredError):
        await collect(job.events(last_event_id="1"))


@pytest.mark.parametrize(
    "kind",
    ["oversized", "bad_json", "duplicate", "wrong_job", "wrong_event", "bad_utf8"],
)
def test_sse_protocol_rejection(kind, example):
    job_id = UUID(example("job-queued.json")["jobId"])
    parser = Parser(job_id, None)
    frame = frames(example)
    data = {
        "oversized": b"x" * (MAX_LINE + 1),
        "bad_json": b"id: 1\nevent: job.queued\ndata: nope\n\n",
        "duplicate": frame.split(b": heartbeat")[0] * 2,
        "wrong_job": frame.replace(str(job_id).encode(), str(UUID(int=1)).encode()),
        "wrong_event": frame.replace(b"event: job.queued", b"event: job.started"),
        "bad_utf8": b"\xff\n",
    }[kind]
    with pytest.raises(UnexpectedResponseError):
        parser.feed(data)


def test_sse_chunk_boundaries(example):
    parser = Parser(UUID(example("job-queued.json")["jobId"]), None)
    events = []
    for byte in frames(example).replace(b"\n", b"\r\n"):
        events.extend(parser.feed(bytes([byte])))
    assert [e.sequence for e in events] == [1, 2] and parser.terminal


async def test_transport_and_decode_errors(sdk):
    def failure(r):
        raise httpx.ConnectError("refused", request=r)

    with pytest.raises(TransportError):
        await call(sdk(failure).status())
    with pytest.raises(UnexpectedResponseError):
        await call(sdk(lambda r: httpx.Response(200, content=b"not-json")).status())


async def test_async_local_cancellation_does_not_cancel_server(example):
    calls = []
    entered = asyncio.Event()

    async def handler(req):
        calls.append(req)
        if len(calls) == 1:
            return httpx.Response(200, json=example("job-queued.json"))
        entered.set()
        await asyncio.Event().wait()

    http = httpx.AsyncClient(
        base_url=URL + "/api/v1", transport=httpx.MockTransport(handler)
    )
    async with AsyncClient(URL, http_client=http) as client:
        job = await client.job(example("job-queued.json")["jobId"])
        task = asyncio.create_task(job.wait())
        await entered.wait()
        task.cancel()
        with pytest.raises(asyncio.CancelledError):
            await task
    assert all(req.method == "GET" for req in calls)


async def test_snapshot_parameter_and_related_preconditions(sdk, example):
    seen = []

    def handler(req):
        seen.append(json.loads(req.content))
        return httpx.Response(200, json=example("edit-parameter-applied.json"))

    client = sdk(handler)
    ref = SymbolRef.method("Lprobe/Variables;", "instance", "(IJDLjava/lang/String;)I")
    with pytest.raises(ValueError):
        await call(
            client.project.rename_parameter(
                ref, 1, "wideCount", source_snapshot_id="sha256:" + "a" * 64
            )
        )
    assert not seen
    await call(
        client.project.rename_parameter(
            ref,
            1,
            "wideCount",
            source_snapshot_id="sha256:" + "a" * 64,
            expected_session_id=UUID(int=1),
            expected_logical_revision=3,
        )
    )
    assert seen[0]["items"][0]["parameterIndex"] == 1
    assert seen[0]["items"][0]["sourceSnapshotId"] == "sha256:" + "a" * 64
    with pytest.raises(ValueError):
        await call(
            client.project.rename(
                SymbolRef.class_("Lprobe/Sample;"), "a", propagate_related=True
            )
        )
    assert len(seen) == 1


async def test_result_provenance_and_bounded_repr(sdk, example):
    source = example("decompile-partial.json")
    client = sdk(lambda r: httpx.Response(200, json=source))
    result = await call(client.project.decompile(SymbolRef.class_("Lprobe/Sample;")))
    assert result.status == "PARTIAL" and result.diagnostics == source["diagnostics"]
    assert result.raw.to_dict()["sourceSnapshotId"] == source["sourceSnapshotId"]
    assert len(repr(result)) < 200 and source["source"] not in repr(result)
    pending = {
        "sessionId": str(UUID(int=1)),
        "logicalRevision": 3,
        "dirty": True,
        "codeData": {"unknown": [None, {"large": "x" * 10000}]},
    }
    client = sdk(lambda r: httpx.Response(200, json=pending))
    result = await call(client.project.pending_edits())
    assert result.code_data.to_dict() == pending["codeData"] and len(repr(result)) < 200


async def test_reference_cursor_preserves_query(sdk, example):
    page = example("references-method.json")
    page.update(nextCursor="next", pageComplete=False)
    seen = []

    def handler(req):
        seen.append(json.loads(req.content))
        return httpx.Response(200, json=page)

    client = sdk(handler)
    query = m.ReferenceQuery(
        SymbolRef.method("Lprobe/Sample;", "answer", "()I"),
        m.ReferenceQueryDirection.INCOMING,
    )
    with pytest.raises(UnexpectedResponseError):
        await collect(client.project.reference_pages(query))
    assert len(seen) == 2 and seen[1]["cursor"] == "next"
    assert query.cursor is libjadx.lowlevel.types.UNSET


class ClosingStream(httpx.SyncByteStream, httpx.AsyncByteStream):
    def __init__(self, data):
        self.data = data
        self.closed = False

    def __iter__(self):
        yield self.data
        raise AssertionError("Should close before reading next chunk")

    async def __aiter__(self):
        yield self.data
        raise AssertionError("Should close before reading next chunk")

    def close(self):
        self.closed = True

    async def aclose(self):
        self.closed = True


async def test_sse_early_close_releases_stream(sdk, example):
    doc = example("job-queued.json")
    first = frames(example).split(b": heartbeat")[0]
    stream = ClosingStream(first)
    client = sdk(
        lambda r: (
            httpx.Response(
                200, stream=stream, headers={"content-type": "text/event-stream"}
            )
            if r.url.path.endswith("events")
            else httpx.Response(200, json=doc)
        )
    )
    job = await call(client.job(doc["jobId"]))
    events = job.events()
    if isinstance(client, AsyncClient):
        assert (await anext(events)).sequence == 1
        await events.aclose()
    else:
        assert next(events).sequence == 1
        events.close()
    assert stream.closed


async def test_abandoned_class_cursor_does_not_prefetch(sdk, example):
    seen = []

    def handler(req):
        seen.append(req)
        return httpx.Response(200, json=example("classes-first-page.json"))

    client = sdk(handler)
    iterator = client.project.classes()
    if isinstance(client, AsyncClient):
        symbol = await anext(iterator)
        await iterator.aclose()
    else:
        symbol = next(iterator)
        iterator.close()
    assert symbol.ref.original_class_descriptor == "Lprobe/SymbolFixture$1;"
    assert len(seen) == 1


async def test_settings_mapping_request_parity_and_revision_updates(sdk, example):
    seen = []

    def handler(req):
        seen.append(req)
        if req.url.path.endswith("export"):
            return httpx.Response(200, json=example("mapping-export-receipt.json"))
        if req.url.path.endswith("import"):
            return httpx.Response(200, json=example("mapping-import-applied.json"))
        if req.url.path.endswith("reload"):
            return httpx.Response(200, json=example("project.json"))
        return httpx.Response(
            200,
            json={
                "mappingsPath": "/server/a.tiny",
                "decompilationMode": "AUTO",
                "revisions": example("project.json")["revisions"],
            },
        )

    client = sdk(handler)
    await call(client.project.settings())
    await call(client.project.update_settings(None))
    assert json.loads(seen[-1].content)["mappingsPath"] is None
    exported = await call(client.project.export_mappings("/server/export.tiny"))
    assert exported.project_mutated is False
    imported = await call(client.project.import_mappings("/server/import.tiny"))
    assert imported.saved is False and imported.mapping_attached is False
    assert client.project.logical_revision == imported.after_logical_revision
    assert client.project.index_revision == imported.after_index_revision
    body = json.loads(seen[-1].content)
    assert body["mode"] == "MERGE_FAIL_ON_CONFLICT" and body["format"] == "TINY_V2"
    await call(client.project.reload(discard_unsaved=True))
    assert json.loads(seen[-1].content)["discardUnsaved"] is True
    assert all(not r.url.path.endswith("save") for r in seen)


async def test_http_timeout_is_distinct_from_connection_failure(sdk):
    from libjadx.errors import RequestTimeoutError

    def handler(req):
        raise httpx.ReadTimeout("deadline", request=req)

    with pytest.raises(RequestTimeoutError) as caught:
        await call(sdk(handler).status())
    assert isinstance(caught.value.__cause__, httpx.ReadTimeout)


def test_sse_multiline_frame_limit(example):
    parser = Parser(UUID(example("job-queued.json")["jobId"]), None)
    parser.feed(b":" + b"x" * (MAX_LINE // 2) + b"\n")
    with pytest.raises(UnexpectedResponseError):
        parser.feed(b":" + b"x" * (MAX_LINE // 2))
