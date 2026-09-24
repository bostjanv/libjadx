# Experimental API changelog

## 2026-09-24 — Phase 2 native lifecycle

- Added `GET /api/v1/project`, `GET/PATCH /api/v1/project/settings`,
  `POST /api/v1/project/save`, `POST /api/v1/project/reload`, and
  `POST /api/v1/project/pending-edits/export`.
- Changed `RevisionSet.logicalRevision` and `indexRevision` from strings to
  nonnegative integers. Added `persistedIdentityState`; `persistedIdentity`
  can be null while background hashing is pending or failed.
- Save revision preconditions now pair `expectedLogicalRevision` with
  `expectedSessionId`. The mapping-path update requires both.
- Added reviewed JSON examples under `openapi/examples/`. The generated Python
  transport does not exist yet; it remains a Phase 6 deliverable.
