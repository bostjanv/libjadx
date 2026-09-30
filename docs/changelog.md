# Experimental API changelog

## 2026-10-01 — PR #22 cross-language release qualification

- Qualified Java `0.1.0-alpha.1` / Python `0.1.0a1` locally against pinned Jadx
  1.5.6; API remains `0.1.0-experimental`. No external publication or tag.
- Verified 22 operations and 19 stable errors across raw HTTP, generated sync/async
  and handwritten sync/async; 305 installed-wheel tests on each Python 3.11/3.14.
- Passed clean Java regression and all 12 actual matching-GUI gates with zero GUI
  skips and 27 Save As processes; retained local-rename unsupported diagnostics.
- Added bounded opt-in filesystem test hooks, full-field parity matrices,
  artifact/license audits and reproducible ZIP/TAR/wheel/sdist evidence.
- Preserved native explicit-save/restart, partial capabilities, serialized Jadx
  reads and conservative parameter/related subsets. See [qualification report](pr-22-review.md).

## 2026-09-30 — PR #21 Python SDK foundation

- Added one experimental `libjadx` 0.1.0a1 distribution: patched pinned
  openapi-python-client 0.29.1 transport for all 22 operations, generated models
  and handwritten synchronous/asynchronous Client/Project/Symbol/Search/Job APIs.
- Preserved exact original refs, partial/source provenance, full typed errors,
  monotonic revision knowledge, explicit save and no hidden conflict retries.
  Added lazy cursor guards and bounded typed SSE with explicit polling fallback.
- Locked tooling, deterministic regeneration, wheel/sdist artifacts, py.typed
  and reserved-rights metadata with upstream generator notice. Linux clean-wheel
  Python 3.11/3.14 generated/sync/async representative server workflows; see
  [exact evidence and limitations](pr-21-review.md).
- No Java/native behavior or OpenAPI schema changes, no PyPI publication, service
  startup/download, uploads, project switching or local-variable mutation.
  Corrected missing required progress in the existing SSE example.
  Phase 6.2 exhaustive cross-language release qualification remains pending.
- Fixed sync/async job-event streams to disable only their read timeout while
  preserving configured connect/write/pool and ordinary request timeouts.
  Quiet-stream regressions wait beyond the default 10-second request timeout
  before receiving a heartbeat and terminal event over real HTTPX sockets.

## 2026-09-30 — PR #20 native local-variable rename retargeting feasibility

- Outcome B: local rename remains UNSUPPORTED with evidence
  `NATIVE_VAR_RETARGETS_WITH_UNBOUND_GUI_SETTINGS`. No public local operation,
  identity, persistence layer, production verifier, SDK or dependency change.
- Expanded owned JVM/direct-DEX fixtures, native-consumer diagnostics, four-mode
  and GUI-input-setting matrix, same-mode replay/replacement/save/reopen probes,
  malformed/duplicate and GUI parameter VAR boundary tests. Original merged-SSA
  and unannotated-catch negatives remain unchanged.
- Matching 1.5.6 GUI editor capture and Save As in five controlled configurations
  confirm unchanged VAR records can target different computations under Use dx/d8.
- Phase 5.2 is complete with local editing deliberately excluded; Phase 6 Python
  SDK work is next. [Feasibility](phase-5-local-rename-feasibility.md) and
  [review](pr-20-review.md) record evidence and limitations.

## PR #19 — verified related-method rename group admission

- METHOD RENAME accepts boolean `propagateRelated` (default false). True requires
  current session/revision and independently COMPLETE closed-input membership.
- Raw per-owner method collisions, overlapping batches and scoped-member conflicts
  fail closed before staging. Whole groups stage privately as standard native records.
- A batch containing propagated groups rejects all ordinary METHOD renames with
  400 INVALID_REQUEST, including disjoint/no-op renames, because pinned Jadx may
  implicitly alias override candidates in another owner. Fields/comments remain
  allowed; ordinary method renames alone retain their existing behavior.
- APPLIED and verified NO_CHANGE return exact sorted affectedRefs. Explicit native
  save, fresh replacement, restart/discard and matching-GUI Save As are preserved.
- Per-family 64; per-batch four groups, 128 members and 800000 reserved work units.
  Unproved bridge/covariant/external/missing/duplicate/local families stay unsupported.


## 2026-09-29 — PR #18 external analysis change rejection

- Effective edits and mapping imports check every input and the attached mapping against accepted analysis fingerprints before and after candidate loading. External changes return `409 EXTERNAL_MODIFICATION_CONFLICT` and preserve the old engine, edits, revisions and source/search identities.
- Startup and explicit reload verify files across loading. Only explicit reload accepts changed input bytes; save and settings rebuild cannot refresh that baseline. No-op avoids replacement as before.
- Added real-Jadx preexisting-change, deletion, mid-load race, clean/dirty, reload and HTTP regressions. Request/response schemas, SDK scope, native serialization and dependency pins are unchanged.

## 2026-09-29 — PR #18 fresh-equivalent replacement-engine edit publication

- Effective native batches and mapping imports stage full native state, load one fresh production engine, then commit/publish under exclusive admission. Candidate failures preserve the old READY project; no-op retains engine and identities.
- Automatic aliases without native/mapping/scoped persistence are derived and may recompute; explicit edits remain authoritative. ReturnClash's untouched String alias becomes value without an extra record.
- Retained PR #17 replay negative controls and added production fault/lease/complete-state/fresh-oracle/performance tests plus a positive actual matching-GUI Save As gate.
- Logical/index/publication counters advance once per effective edit. Old-engine cleanup exceptions do not undo publication. Saves remain explicit.
- Public request/response schemas and SDK scope remain unchanged. Related propagation and local editing remain unsupported; PR #19 must implement group admission. See [publication](phase-5-replacement-publication.md) and [validation](pr-18-review.md).

## 2026-09-29 — PR #17 safe replay / replacement-engine feasibility

- Outcome B: no production replay/publication, OpenAPI, DTO, SDK or dependency change.
- Added executable comparisons for current replay, listener-first replay, exact-owner invalidation and fresh replacement, with raw bridge aliases, Java, source metadata, member-search data and reference dependencies.
- Retained the hot Joined negative control and added the minimal return-only overload counterexample: replacement recomputes an untouched automatic alias when the requested rename removes a collision.
- Added complete native/mapping/scoped-state reconstruction probes, owned-size load/census/heap measurements and an actual matching-GUI alias-recomputation diagnostic.
- Propagation and local editing stay unsupported. A decision about automatic nonmember alias recomputation is required before adopting generic replacement.

## 2026-09-29 — PR #16 complete-family replay negative evidence

- Retained `422 UNSUPPORTED_CAPABILITY` for every `propagateRelated` presence
  and `edit.related_propagation=UNSUPPORTED`; the public contract is unchanged.
- Added real EditBatchService explicit-record probes for seven independently
  COMPLETE family shapes. Hot chain/interface/default/Joined cases also change
  two unrelated CovariantLeaf aliases, which fresh native reopen restores.
- Added a separate actual GUI Save As diagnostic for service-produced legacy
  record batches from all four Joined seed positions, both cold and hot.
  This diagnostic does not count as an accepted propagated-service gate.
- Expanded clean/dirty HTTP rejection captures to numeric, array and object
  flags. Phase 5.2 and the local-variable editing decision remain incomplete.

## 2026-09-29 — PR #15 original-input hierarchy verification

- Added bounded immutable class/JAR/DEX declaration census using pinned Jadx input
  readers before duplicate collapse, preserving configured input origins internally.
- Added independent closed-input override-family verification, exact original
  refs, visibility/exclusion rules, conservative bridge/covariance refusal,
  missing/external/duplicate statuses and deterministic resource limits.
- Comparison detects PR #14's four-versus-three/empty candidate omission.
  Initialization/reload rebuild derived state; fingerprints invalidate changed
  inputs. Verification has no source generation or persistence side effects.
- Review follow-up confines runtime verifier handles to their admitting callback
  thread and lifetime; escaped handles cannot survive even unchanged-input reload.
  COMPLETE results remain evidence, requiring fresh verification and native
  staging under one current exclusive lease in PR #16.
- Public OpenAPI and mutation behavior are unchanged. Propagation and local
  editing remain unsupported; group transactions and persistence are PR #16.

## 2026-09-28 — PR #14 related-method negative feasibility evidence

- Preserved explicit propagation rejection and added owned closed-hierarchy,
  missing-parent, duplicate-input and bridge counterexamples. Pinned Jadx omits
  one resolved interface branch; native one-seed replay also varies with hot state.
- Added two native/actual-GUI diagnostic strategies, rejection captures and
  independent contract checks. Capability remains UNSUPPORTED; its evidence is
  now INCOMPLETE_PINNED_OVERRIDE_GROUP. Error messages identify the blocker.
- Clarified ordinary method RENAME's existing implicit candidate alias behavior
  and empty affectedRefs. No accepted edit schema or native format expanded.
  Phase 5.2 and local-variable feasibility remain incomplete.

## 2026-09-28 — PR #13 catch-declaration collision fix

- Parameter targets now fail closed when an emitted catch declaration has no
  verified variable metadata. Jadx's unannotated unused catch arguments can no
  longer bypass collision preflight and silently acquire a different name.
- Affected parameters report `UNSUPPORTED` with null `parameterIndex`; batches
  return `422 UNSUPPORTED_CAPABILITY` before staging. Verified catch declarations
  retain parameter support. Wire schemas and native persistence formats are unchanged.

## 2026-09-28 — PR #13 scoped parameter renames

- Added snapshot-bound `RENAME_PARAMETER` to edit batches, with mandatory
  session/revision preconditions and original semantic indexes excluding `this`.
- Added `decompile.variables` with exact emitted declaration ranges and explicit
  persistability. The field is now required; reviewed examples and validators
  changed together. Python transport generation remains Phase 6.
- Preserved full prevalidation, one native commit, no-op semantics, itemized
  staging failures and explicit-save behavior. Collisions conservatively include
  all declared variables in the same method, including pending batch names.
- Probed wide/static/instance/overloaded arguments, native persistence and GUI
  resave. Merged local SSA metadata differs between modes; local renames and
  propagation remain unsupported. Phase 5.2 is incomplete.


## 2026-09-28 — Phase 5.2 bounded Tiny v2 import/merge

- Added `/project/mappings/import` with mandatory source/format/mode/session/revision,
  strict captured-source validation and a parsed/applied/unchanged receipt.
- Added conservative effective-state alias/comment merge, full original descriptor
  resolution and entire-plan prevalidation/private staging before one native commit.
- Added bounded read-only file capture and final source/native/attached checks;
  no file writes, attachments or autosaves. No-ops preserve cache identity.
- Fixed duplicate attached comments on cold-owner code-data replay in the Jadx
  adapter. Added HTTP, distribution, external-process race and matching-GUI
  save/reopen tests plus independent contract validation.
- Fixed import preflight for parent renames: all prospective visible qualified
  class aliases, including untouched inner/nested descendants, must be unique
  before staging. Descendant collisions return `409 MAPPING_MERGE_CONFLICT`;
  receipt/error schemas and the unsupported direct-inner-edit boundary are unchanged.
- Scoped/variable editing, propagation and Python SDK generation remain deferred;
  Phase 5.2 is incomplete. See `phase-5-mapping-import.md` for final gate outcomes.


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
