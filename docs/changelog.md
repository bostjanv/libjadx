# Experimental API changelog

## 2026-09-28 — Phase 5.2 safe Tiny v2 export

- Added `POST /api/v1/project/mappings/export` with required session/revision
  preconditions, strict JSON and same-origin validation, bounded declarations
  and LINE comments, codec read-back and observed counts/digest.
- Export combines accepted attached Tiny v2 data with unsaved native edits,
  rejects unsupported/ambiguous sources, and publishes a new allowed-root
  file with no-clobber hard-link creation. It never saves or attaches output.
- Native code-data replay now unloads owner caches before notifying listeners,
  preserving attached mapping comments after edits. Mapping import and
  scoped/propagated edits remain separate follow-ups; Phase 5.2 is incomplete.
- The new contract, examples and live response validator ship together.
  Python transport generation remains scheduled for Phase 6.

## 2026-09-28 — Phase 5.2 review fixes

- Edit batches resolve class collisions from the lightweight catalog and load
  members only for requested declaring owners, retaining original-identity and
  ambiguity checks. Unrelated classes stay unprocessed during validation.
- `PARTIAL` now retains itemized staging failures even without an applied
  prefix. In that case logical/index revisions and dirty state are unchanged;
  earlier no-op items remain `SKIPPED/NO_CHANGE`. The result schema and outcome
  enum are unchanged. The generated Python transport remains a Phase 6 deliverable.

## 2026-09-27 — Phase 5.2 native declaration editing slice

- Added `POST /api/v1/edits/batch` for validated native class, method and
  field renames and LINE declaration comments. A batch is memory-only until
  explicit native save; no-op requests retain revisions and dirty state.
- Added per-item committed-prefix results for an unexpected staging failure,
  typed validation errors, strict JSON/origin/body limits and edit capabilities.
- Matching Jadx 1.5.6 GUI resave and headless reopen exercise all six
  declaration edit forms. Mapping export, parameter/local editing and related
  override propagation remain unsupported Phase 5.2 gates.

## 2026-09-27 — Phase 5.1 incremental search

- Added `POST /api/v1/search` for original and alias class/member names and
  exact emitted owner Java, with per-domain Jadx-visible coverage and UTF-16
  source ranges. `strict` rejects incomplete coverage.
- Added `POST /api/v1/search/build-index` and `requireComplete` submission to
  the existing process-local job, polling, cancellation and SSE protocol.
- Added bounded in-memory index generations and authenticated, expiring result
  cursors. Unsaved native edits, save publication, settings rebuild and reload
  invalidate old search views. No project data or index is written by search.
- Source regex uses pinned RE2/J 1.8; string-literal ownership, annotations,
  resources and original-input census remain unsupported or unverified.

## 2026-09-26 — Phase 4.3 reference navigation

- Added `POST /api/v1/references/query` for original method callers/callees,
  unresolved method descriptors, field users and class dependencies.
- Optional verified P4.2 source sites retain caller, target and source snapshot
  identity. Coverage is partial; read/write directions and offsets are unavailable.
- Added bounded content-hashed snapshots and authenticated cursors which become
  stale even when Jadx graph processing changes content at one logical revision.
- Cursors use a fixed-size last-edge digest, so repeated verified call sites
  remain pageable. One request reuses caller source metadata across its callees.
- Strict resolved queries return INCOMPLETE_ANALYSIS; domain misses remain 200.
  Native state, P4.2 source contracts and generated SDK scope are unchanged.

## 2026-09-25 — Phase 4.2 class Java source

- Added `POST /api/v1/decompile` for original class and method refs. Method
  requests return the containing emitted class source, plus a verified excerpt
  where Jadx's declaration and end markers agree with lexical boundaries.
- Added UTF-16 source offsets, code-point columns, process/revision/settings-
  bound source snapshots, validated declaration/reference token annotations,
  explicit per-result capability availability and bounded diagnostics.
- Different decompilation modes use a one-operation isolated engine that sees
  current unsaved native renames/comments without saving or changing the
  primary mode. Java is the only exposed representation in this route.
- Added typed `INCOMPLETE_ANALYSIS` and `RESOURCE_LIMIT` outcomes for strict
  coverage and oversized source/metadata; original debug lines and bytecode
  offsets remain unavailable pending pinned-fixture proof.

## 2026-09-25 — Phase 4.1 original symbol identity

- Added `GET /api/v1/classes` with bounded original-name paging, inner and
  anonymous classes, and signed revision-bound cursors.
- Added `POST /api/v1/symbols/resolve` for exact original class, method, and
  field descriptors. Display aliases are separate; 200 outcomes distinguish
  resolved, missing, ambiguous, and unavailable input provenance.
- Class listings explicitly cover Jadx-visible declarations only. A two-JAR
  duplicate probe showed one surviving definition in pinned Jadx 1.5.6.
- Cursor signatures are verified before stale-revision classification. The
  private operational signing key persists outside the native project so
  authentic cursors from a prior process still return `STALE_REVISION`.
- The standalone distribution now includes the pinned Jadx input plugins;
  installed-service tests load a real native project and verify cursors after
  a process restart.


## 2026-09-25 — Phase 3.3 shutdown policies

- Added `POST /api/v1/shutdown` with `discard`, `save`, and `refuse_if_dirty`.
  Active jobs or operations reject the request without implicit cancellation.
  Native save conflicts and failures keep the service available; accepted
  responses complete before the listener stops.

## 2026-09-25 — Phase 3.2 process-local jobs

- Added `GET /api/v1/jobs/{jobId}`, `POST /api/v1/jobs/{jobId}/cancel`, and
  `GET /api/v1/jobs/{jobId}/events` with bounded replay and `Last-Event-ID`.
  Job creation remains an internal typed API until a genuine heavy public
  operation exists.
- Job snapshots now include timestamps, source revision, nullable progress
  total, result completeness, bounded result and diagnostics, and cooperative
  cancellation reason. Polling and SSE events disappear after retention expiry
  or process restart.
- SSE stale history returns 409 `EVENT_HISTORY_EXPIRED`; subscriber and future
  job-submission saturation use 429 `RESOURCE_LIMIT`. Future job-producing
  endpoints must return 202 with a job `Location` header.
- Total result-byte pressure now evicts the oldest retained successful results
  before publishing a newly successful job; evicted IDs return 404. The
  cancellation route documents its cross-origin 403 response.
- Terminal count and result-byte eviction order is based on completion time,
  with deterministic completion-order ties, rather than submission order.
- Index/query read compatibility now also checks session and logical revision.
- The generated Python transport remains a Phase 6 deliverable.

## 2026-09-25 — Phase 3.1 operation admission

- Concurrent project mutations now fail promptly with HTTP 409
  `PROJECT_BUSY` and `retryable: true` while an incompatible operation is in
  flight. Requests are not implicitly queued.
- A reload refused because unsaved edits require an explicit discard still
  uses `PROJECT_BUSY`, with `retryable: false` until the caller changes its
  request. Loading and shutdown remain distinct HTTP 503 errors.
- No new endpoints were added. The generated Python transport remains a
  Phase 6 deliverable.

## 2026-09-24 — Phase 2 native lifecycle

- Added `GET /api/v1/project`, `GET/PATCH /api/v1/project/settings`,
  `POST /api/v1/project/save`, `POST /api/v1/project/reload`, and
  `POST /api/v1/project/pending-edits/export`.
- Changed `RevisionSet.logicalRevision` and `indexRevision` from strings to
  nonnegative integers. Added `persistedIdentityState`; `persistedIdentity`
  can be null while background hashing is pending or failed.
- Save revision preconditions now pair `expectedLogicalRevision` with
  `expectedSessionId`. The mapping-path update requires both.
- Reload requires the same session and logical-revision precondition before it
  can discard unsaved edits.
- First raw-input save-as uses exclusive target creation; a concurrent target
  creation is reported as `EXTERNAL_MODIFICATION_CONFLICT`.
- Added reviewed JSON examples under `openapi/examples/`. The generated Python
  transport does not exist yet; it remains a Phase 6 deliverable.
