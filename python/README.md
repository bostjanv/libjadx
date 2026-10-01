# LibJadx Python SDK

`libjadx` 0.1.0a1 is an experimental HTTP client for a separately running,
standalone LibJadx Java service. It does not embed Jadx or use JPype. Minimum
Python is 3.11; the release gate covers Linux wheels on 3.11 and 3.14.
The matching Java candidate is `0.1.0-alpha.1`, with API `0.1.0-experimental`.
See [the qualification report](../docs/pr-22-review.md) for the validated source
revision and outcome. Phase 6.2 is complete and locally qualified; this package
is not tagged or published on PyPI. See the [project status](../README.md) and
[documentation index](../docs/README.md).

Build/install locally from the repository:

```bash
cd python
uv sync --frozen --all-groups  # tooling: uv 0.8.22
uv build
python -m pip install dist/libjadx-0.1.0a1-py3-none-any.whl
```

Start the Java distribution separately, for example with
`build/install/libjadx/bin/libjadx --input /work/sample.jar --allowed-root /work`.
One Java process owns one fixed project. The SDK never starts/downloads Java,
opens a different project, or uploads files. Every filesystem path is interpreted
by the server, under its allowed-root restrictions.

## Analysis and explicit persistence

The canonical URL is the service origin; `/api/v1` URLs are accepted too.
Constructors and imports perform no network I/O. Each client owns one HTTPX
connection pool; context exit closes it without saving, shutting down the service,
or cancelling jobs. Closing is idempotent. Injected `http_client` resources also
transfer ownership to the SDK and must have the normalized API base URL; their
headers, ordinary request timeout and proxy settings take precedence over
constructor settings. Job-event streams retain the pool's connect/write/pool
timeouts and disable only the read timeout.
`trust_env=False` prevents system proxies receiving local requests by default.

```python
from libjadx import Client, SymbolRef

with Client("http://127.0.0.1:18777", timeout=10.0) as client:
    client.wait_ready(timeout=30.0)
    project = client.project
    project.snapshot()  # observe session and revisions before editing
    ref = SymbolRef.class_("Lprobe/Sample;")
    result = project.decompile(ref)
    print(result.status, result.diagnostics)
    print(result.source)
    for cls in project.classes(package_prefix="probe", page_size=50):
        print(cls.ref, cls.display_name)
    receipt = project.rename(ref, "RenamedSample")
    print(receipt.outcome, receipt.items)  # APPLIED, NO_CHANGE or PARTIAL
    project.save("/work/sample.jadx")  # explicit server-side path
```

**Edits never autosave.** Call `project.save()` after a native path has been
established, or explicitly request `client.shutdown(policy="save")`. A first
save from raw input needs a target. `shutdown("discard")` is explicit too;
client cleanup does not shut down the service. Native writing has the server's
existing interruption/durability limits.

Original refs are identities; aliases/display names may change. Construct methods
and fields with full original descriptors:

```python
from libjadx import SymbolRef

method = SymbolRef.method("Lprobe/Sample;", "answer", "()I")
field = SymbolRef.field("Lcom/example/Main;", "value", "I")
```

`project.resolve(ref)` retains the typed RESOLVED/NOT_FOUND/AMBIGUOUS/
PROVENANCE_UNAVAILABLE result. `resolve_object(ref)` returns a JavaClass,
JavaMethod or JavaField for resolved refs, otherwise the same result. Async
projects return AsyncJavaClass/AsyncJavaMethod/AsyncJavaField wrappers.
Decompile returns a SourceResult with bounded repr and every typed wire field;
`.raw` gives the generated model. PendingEditsResult similarly preserves arbitrary
native `code_data` JSON through `.raw`/`.code_data.to_dict()`. Most other operations
return generated typed models directly, including full batch receipts.

Inspect status, diagnostics, capabilities and coverage: PARTIAL is a successful
analysis result, not proof of complete analysis. `strict=True` asks the server to
reject incomplete analysis. Source ranges index **UTF-16 code units**, not Python
string indexes or bytecode offsets. Snapshot metadata is valid only for its
session, revision and effective settings.

## Revisions and conflicts

Successful authoritative responses update the latest observed session/logical/index
revision. Updates are monotonic within a session; known retired sessions cannot
overwrite a newer boot. Search `index_generation` is a separate coordinate and
never becomes a project index revision. Mutations use this knowledge by default;
constructing a client or reading status does not fetch project revisions.

```python
from libjadx.errors import StaleRevisionError

try:
    project.rename(ref, "AnotherName")
except StaleRevisionError as error:
    print(error.code, error.details, error.item_errors, error.request_id)
    project.refresh()  # explicit; caller decides whether/how to try again
```

No mutation retries on stale revisions, busy state, external modification or
mapping conflicts. Exceptions preserve HTTP status, code, message, retryable,
body/header request IDs, details, causes and itemErrors; network failures are
TransportError, individual request timeouts RequestTimeoutError, and
schema/status failures UnexpectedResponseError. Readiness FAILED
raises ServiceLoadFailed (with status and typed error); deadline expiry raises
WaitReadyTimeout. Unknown primary enum values fail schema parsing pre-1.0.

Mutation helpers accept `expected_session_id` and `expected_logical_revision`
overrides. Explicit `None, None` omits preconditions only on routes that allow it
(declaration-only edit batches and save). Scoped/related edits, mappings, reload
and settings require an observed or explicitly supplied pair. No helper refreshes
and retries stale source metadata. Concurrent writes through one Project are not
coordinated client-side; the server may reject either request. A shared cursor
is single-use; do not iterate it concurrently. Broad arbitrary thread-safety of
all wrappers is not claimed.

`rename(..., propagate_related=True)` is METHOD-only and limited to the server's
conservative independently verified closed-input subset. Receipts retain exact
`affected_refs`; ordinary method rename does not certify exhaustive propagation.
`rename_parameter(method, index, name, source_snapshot_id=...)` is snapshot-bound,
zero-based original positional identity excluding `this`; wide arguments count
once. Native local-variable rename remains unsupported and has no helper.
`set_comment(ref, text)` stages one LINE declaration comment.

`export_mappings(path)` creates a new strict Tiny v2 artifact without saving or
attaching it. `import_mappings(path)` stages pending edits without saving.
`settings()`/`update_settings(mappings_path)` manage native mapping attachment;
`reload(discard_unsaved=True)` explicitly discards dirty state. `pending_edits()`
returns opaque native data without writing any file.

## Search, jobs and events

```python
from libjadx import Job

result = project.search("42", domains=["SOURCE_TEXT"], require_complete=True)
if isinstance(result, Job):  # 202: expensive work is explicit
    result.wait(timeout=30.0)
    result = project.search("42", domains=["SOURCE_TEXT"], strict=True)
for hit in result:
    print(hit.matched_text)
print(result.coverage, result.diagnostics)
```

SearchCursor retains the original query, result snapshot ID, latest page `.raw`,
coverage and diagnostics. `complete` describes analysis coverage; page exhaustion
is `.raw.page_complete`. Iteration fetches pages lazily; stale and repeated cursors
raise without restarting. `classes()` is lazy too; `class_pages()` exposes raw
scope/completeness, with optional `max_pages`. `reference_pages(query)` iterates
bounded observed relationships and preserves per-page coverage and diagnostics.

`build_search_index()` returns SearchIndexStatus or Job. Jobs expose `.raw`, ID,
state, progress, completeness, diagnostics, typed error and generic JSON result.
`wait()` polls and returns the terminal wire snapshot; FAILED raises JobFailed,
CANCELLED raises JobCancelled, and timeout raises JobWaitTimeout without cancelling
server work. SUCCEEDED can still have PARTIAL completeness.

```python
job.cancel()  # actual server state can be CANCELLING, CANCELLED or SUCCEEDED
for snapshot in job.poll_updates(timeout=30.0):
    print(snapshot.state, snapshot.progress)
```

`job.events(last_event_id="0")` streams typed JobEvent objects with numeric,
monotonic IDs. Streams stop at terminal events; heartbeat comments are ignored.
SSE requests disable the read timeout so quiet jobs can wait for the server's
15-second heartbeat beyond the ordinary 10-second request timeout. Connect,
write and pool timeouts still use the configured HTTPX pool values; ordinary
request timeouts are unchanged. A silent stream can wait indefinitely; close
the iterator or cancel the consuming async task to stop local waiting.
UTF-8 lines and frames are capped at 1 MiB, incoming chunks processed in 16 KiB
pieces. Schema errors raise UnexpectedResponseError; interrupted transport raises
TransportError. Reconnect explicitly with the last observed sequence, or use
`poll_updates()`. EVENT_HISTORY_EXPIRED/NOT_FOUND/INVALID_REQUEST never trigger a
hidden polling fallback. Use `contextlib.closing(job.events())` (or `aclosing`
for async) when breaking early, to release the connection promptly.

## Async parity

```python
from libjadx import AsyncClient, SymbolRef


async def analyze():
    async with AsyncClient("http://127.0.0.1:18777") as client:
        await client.wait_ready()
        await client.project.snapshot()
        ref = SymbolRef.class_("Lprobe/Sample;")
        result = await client.project.decompile(ref)
        async for cls in client.project.classes():
            print(cls.display_name)
        return result
```

Async uses generated asynchronous HTTP requests directly. Await every ordinary
AsyncClient/AsyncProject/AsyncJob operation; iterate classes, class_pages,
reference_pages, SearchCursor, job events and poll_updates with `async for`.
`await project.search()` returns AsyncSearchCursor or AsyncJob.
Cancelling a Python coroutine stops only local waiting/HTTP activity; explicit
`await job.cancel()` requests server cancellation. Individual HTTP timeout and
readiness/job waiting deadline are separate; an in-flight request can last up to
its configured HTTP timeout.

## Generated escape hatch and development

```python
from libjadx.lowlevel import api, models

response = api.symbols.list_classes.sync_detailed(client=client.lowlevel, page_size=1)
print(response.status_code, response.parsed)
```

Use the facade imports shown above for low-level calls. Generated names are less stable
than the handwritten surface until 1.0. Low-level clients require the API prefix,
return ErrorEnvelope models instead of raising SDK API errors, and do not track
high-level revisions.

From the Python directory, `uv run python scripts/generate.py` replaces only
machine-owned `_generated`; `uv run python scripts/check_generated.py` compares
regeneration in a temporary directory without changing the checkout. The sole
input is `../openapi/openapi.yaml`, with committed generator config and patched
openapi-python-client 0.29.1. `scripts/validate.sh` runs locked installation,
regeneration, lint, strict handwritten typing, unit/live tests and packaging;
build the Java `installDist` first. Bootstrap downloads need network access;
with caches populated, use `uv sync --offline --frozen --all-groups`.

The SDK assumes a trusted local service, no authentication and a loopback listener.
Do not expose it publicly. Errors may contain project diagnostics; the SDK does
not sanitize a malicious server beyond protocol parsing and bounded SSE buffers.
No Windows/macOS test claim is made. No source license has been chosen yet;
local artifacts reserve contributors' rights (see LICENSE/THIRD_PARTY.md).
