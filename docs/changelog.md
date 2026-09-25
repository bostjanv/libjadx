# Experimental API changelog

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
