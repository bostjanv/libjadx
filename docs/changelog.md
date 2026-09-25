# Experimental API changelog

## 2026-09-25 — Phase 4.1 original symbol identity

- Added `GET /api/v1/classes` with bounded original-name paging, inner and
  anonymous classes, and signed revision-bound cursors.
- Added `POST /api/v1/symbols/resolve` for exact original class, method, and
  field descriptors. Display aliases are separate; 200 outcomes distinguish
  resolved, missing, ambiguous, and unavailable input provenance.
- Class listings explicitly cover Jadx-visible declarations only. A two-JAR
  duplicate probe showed one surviving definition in pinned Jadx 1.5.6.


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
