# Phase 6.1 — Python SDK foundation

LibJadx 0.1.0a1 is one typed distribution containing stock generated transport
and handwritten sync/async APIs. The Java process remains separately started,
fixed-project and local. No Java code, native project behavior, Jadx pin or
OpenAPI schema changes in PR #21. The existing SSE example lacked its required
JobEvent.progress and the final empty-line delimiter; all three frames now
parse, with a parser regression. This corrects an example against the unchanged reviewed schema. A file-specific
Git whitespace attribute permits SSE's required final empty-line delimiter. Phase 6.2 is still required before release
qualification/publication. All rights reserved for LibJadx's own code; the
human selected this interim policy. Upstream notices remain separate.

## Generator compatibility and boundary

`python/scripts/generate.py` consumes only repository `openapi/openapi.yaml`,
using openapi-python-client **0.29.1**, patched for the arbitrary-code-generation
issue described by the [upstream release](https://github.com/openapi-generators/openapi-python-client/releases/tag/0.29.1)
and [versioned package metadata](https://pypi.org/project/openapi-python-client/0.29.1/).
The stock templates support the present 3.1 contract. No template, schema
preprocessing or generated-file edits. Configured post-hooks use pinned ruff
0.16.9 with isolated settings to prevent host configuration changing output.
`check_generated.py` regenerates in a temporary tree and compares names/bytes;
normal checking never rewrites the committed generated tree. Operational tool
caches and bytecode are excluded. Every one of the 22 current operation IDs has
importable sync and async detailed functions; a contract-derived test fails on
missing endpoints. `_generated` is machine-owned; handwritten modules are separate.

The mandatory spike executes reviewed externalValue JSON fixtures and generated
HTTPX transport for 200/202, typed 409/422/429/503, response refs/default errors,
nullable/oneOf/const, UUID, aware datetimes, int64 revisions, recursive error
causes/item details, opaque native JSON and generic job results. String and
boolean const fields become Literal with from_dict checks. Error causes remain
recursive typed models. Nullable enum generation produces redundant internal
union types; no handwritten duplicate enum hierarchy is introduced.
Required enums reject unknown values; nullable enum unions can pass through an
unknown string via their cast fallback. This is known experimental behavior,
not a forward-compatibility guarantee. Generated attrs constructors are not a
full JSON Schema validator (if/then, dependentRequired, bounds and regexes remain
server-authoritative). Generated model errors are mapped to protocol exceptions
by the high-level layer. Dates normalize Z to +00:00 with identical UTC meaning.

One generated `Capabilities.from_dict` temporary shadows the `capabilities`
list with the final object, causing mypy assignment errors under strict checking
but correct executed runtime behavior. Generated imports are followed silently;
**all handwritten modules** are strictly checked with full generated annotations
available. Nothing is manually patched. Deliberately generic Any only occurs for
schema-extensible JSON and the central generated-module invocation boundary.

Users import Client/AsyncClient and the small handwritten public surface from
`libjadx`. `libjadx.lowlevel` exposes generated `api`, `models`, `types` and
Client; high-level `.lowlevel` uses the same connection pool. Generator-specific
names are experimental and isolated from ordinary usage. Generated default
responses parse ErrorEnvelope; undefined statuses on explicit-status routes
remain unparsed and become UnexpectedResponseError in handwritten calls.

## State, identity and agency

Each project tracks observed session/logical/index revisions, under a small
lock. Responses may arrive out of order: revisions never decrease within a
session; observed retired sessions cannot replace a newer boot. This is a
convenience cache, not client-side coordination of simultaneous writes.
Search index generation remains independent of project index revision. Successful
mutation receipts expose APPLIED/NO_CHANGE/PARTIAL, per-item statuses,
affectedRefs and before/after revisions unchanged. Failures never refresh/retry.

Mutation defaults use the latest known pair; callers can override both fields.
Declaration batches/save may explicitly omit the pair with `None, None`, as
permitted by the server. Mandatory preconditions for scoped/related edits,
mapping operations, reload and settings require an observed or supplied pair;
no hidden HTTP refresh. `refresh()` is an explicit project GET. Rename parameter
requires supplied source snapshot and original positional index; it never
synthesizes a snapshot. Related renames expose only the conservative verified
subset. No local-variable edit helper.

Symbol wrappers retain their exact original generated SymbolRef and raw metadata.
Aliases are display values. `resolve` returns domain outcomes; `resolve_object`
wraps only a successful symbol. Sync/async symbol wrappers have separate typed
methods, delegating to their Project. SourceResult/PendingEditsResult preserve
all typed fields, raw provenance and arbitrary JSON with bounded repr. Their
raw generated models intentionally remain available, including generator repr.
Snapshot-bound variables/annotations remain tied to returned source metadata;
a caller reusing one must pass the original snapshot and accept server rejection.

Class enumeration, reference pages and search are lazy. Every page's partial
coverage/provenance is retained. Repeated cursors fail; stale cursors raise typed
errors without restarting. SearchCursor is single-use and retains the original
query (copied on access) and latest raw page. Coverage completeness and page
exhaustion are distinct. A 202 Search/Build response returns Job/AsyncJob;
waiting and requerying are explicit. No hidden index build behind a property.

No edit, mapping import, context exit, cleanup or job completion saves. Native
save is explicit; shutdown save is explicit. Mapping export creates a new
external artifact, import changes pending native state, neither is project save.
All server paths are interpreted under the Java allowed-root policy. UTF-16
ranges remain UTF-16 code units, never Python string slices.

## Jobs and transport

Polling is default for wait(); SUCCEEDED returns the terminal wire snapshot,
FAILED raises JobFailed with raw job/typed error, CANCELLED raises JobCancelled.
CANCELLING remains nonterminal. A client deadline never cancels server work;
async task cancellation stops only the local coroutine/request. cancel() returns
the real server snapshot (including success if work already finished).

SSE uses the same configured HTTPX pool. A job-specific bounded parser accepts
LF/CRLF UTF-8, comments, numeric increasing IDs, matching event type and job ID,
and typed JobEvent data. Lines/frames cap at 1 MiB; network chunks are processed
in 16 KiB pieces without HTTPX's minimum-chunk buffering, so progress arrives
while work runs. Streams close on terminal events. Caller close/aclose releases
the connection; callers breaking early should use closing/aclosing. Reconnect
with Last-Event-ID explicitly. Semantic error responses preserve typed codes
including EVENT_HISTORY_EXPIRED. Interrupted transport raises TransportError;
`poll_updates` is the explicit fallback, never automatic history reset.

Constructors/imports do no HTTP. HTTPX per-request timeout is separate from
readiness/job-wait deadline (an in-flight request can consume its HTTP timeout).
Default trust_env=False keeps loopback traffic out of environment proxies;
advanced callers can override or inject a preconfigured client whose normalized
API base URL must match. Injection transfers ownership and preserves its own
headers/timeout/proxy config. Close is idempotent and never changes server state.
No arbitrary wrapper thread-safety guarantee; concurrent reads use HTTPX async
transport, while concurrent writes remain server-authoritative.

## High-level parity and validation

| Capability | Sync | Async | PR #21 evidence |
|---|---|---|---|
| Readiness/liveness/status/capabilities | Client methods | await AsyncClient methods | unit + installed-server |
| Snapshot/refresh | Project methods | await AsyncProject methods | unit + installed-server |
| Classes/class pages | iterator | async iterator | unit + installed-server |
| Resolve/symbol wrappers | resolve/resolve_object | awaited equivalent | installed-server + unit |
| Decompile/source provenance | decompile | awaited equivalent | unit + installed-server |
| References/partial strict behavior | references/reference_pages | awaited/async iterator | unit + installed-server |
| Search/200 vs 202 | search/SearchCursor | awaited/AsyncSearchCursor | unit + installed-server |
| Build index | build_search_index | awaited equivalent | installed-server |
| Edit batch/declaration rename | apply_edits/rename | awaited equivalent | unit + installed-server |
| Parameter/comment/related helper requests | convenience methods | awaited equivalents | unit; underlying Java real-Jadx gates |
| Save/reload | save/reload | awaited equivalents | unit; live sync save/reload + async save |
| Settings/mappings/pending edits | Project methods | awaited equivalents | typed transport; pending opaque JSON unit/live; settings unit; mappings unit + installed-server |
| Job poll/wait/cancel/events | Job methods/iterators | AsyncJob methods/iterators | unit + installed-server |
| Explicit shutdown | Client.shutdown | await AsyncClient.shutdown | installed-server |

The real server fixture starts owned `sample.jar` from installDist on port 0,
parses the actual listener, isolates files and operational state, captures logs,
requests shutdown and only force-kills on cleanup timeout. Positive save/restart
and two-client no-hidden-retry assertions use real pinned Jadx. External native
modification keeps pending state. Reference coverage supplies a real PARTIAL/
strict error without inventing a failing decompiler fixture; simulated source
partial rendering stays an example-based unit test. SSE tests cover real replay,
terminal polling and reconnect; the small real job finishes before cancellation,
so nonterminal cancellation/history-expiry/bounds and early close have unit
plus existing Java evidence, not a claimed new large-fixture Python gate.

`python/scripts/validate.sh` is the CI-oriented aggregate after Java installDist;
Java-only check does not bootstrap Python. uv.lock pins universal environments,
and clean-wheel checks exercise actual site-packages on 3.11 and 3.14. Runtime
is HTTPX/attrs/typing-extensions; generator, lint/type/test/build/contract tools
are dev-only. Wheel includes py.typed/licenses and no Java artifacts/tests.

Phase 6.2 must expand to exhaustive route/error parity, larger cancellation and
concurrent lifecycle workflows, native/GUI release gates and reproducible Java/
Python release packaging. No external publication/tag/release is authorized.
Exact local commands, artifacts and limitations are in [PR #21 review](pr-21-review.md).
