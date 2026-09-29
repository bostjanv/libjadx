# Fresh-equivalent replacement-engine edit publication

**Current PR #19 boundary:** [verified related-method group admission](phase-5-related-group-admission.md)
now admits the independently COMPLETE conservative subset under the same exclusive
edit lease, with raw all-owner collisions, exact native records and affectedRefs.
Earlier unsupported-contract statements below describe historical milestones;
candidate/replay counterexamples remain valid. Local and parameter propagation
remain unsupported. Final validation is in [PR #19 review](pr-19-review.md).


PR #18 implements the approved automatic-alias decision on pinned Jadx 1.5.6,
source `28ff15e4ae69950aebea110a13e5ab895d234dfc`. Explicit native declaration
renames/comments, mapping aliases/comments, supported parameter renames and
existing VAR records remain intent. Collision/deobfuscation aliases without
explicit persistence are derived and may change after mutation. They are never
serialized as extra edits. The ordinary REST schema is unchanged; its description
records this behavior. There is no Python SDK change or dependency upgrade.

## Staging and ownership

`NativeProjectRepository.stageCodeData(completeData, expectedRevision)` checks the
revision and deep-copies the full native data and, for existing projects, a native
JSON document preserving unknown members. It retains the owning repository,
revision, accepted persistence generation, baseline JSON and candidate JSON. No repository assignment, dirty or
revision change, disk write or native JSON mutation occurs. Candidate accessors
return copies. Raw projects use the same in-memory native semantics.

The review follow-up retains streaming SHA-256 fingerprints for every ordered
input when the repository opens, separately from persisted-identity generation
and its asynchronous publication. Effective staging checks those accepted input
bytes and the current attached mapping before candidate construction. Commit
checks them again after loading and all throwable preparation checkpoints, before
any assignment. Attached mappings use their accepted unsaved attachment baseline
when present, rather than the last saved attachment. Changed or removed files
return `409 EXTERNAL_MODIFICATION_CONFLICT`; no candidate is constructed for a
preexisting conflict. A mid-load conflict closes the candidate and leaves the
old engine, pending edits, dirty state, revisions and source/search identities
unchanged. No-op continues to avoid loading or adopting external bytes.

Startup rechecks the accepted files after load before publishing READY. Explicit
reload captures new input fingerprints and verifies them after candidate load
before advancing the accepted baseline. Saves and mapping-setting rebuilds check
input equality and cannot advance that baseline. Reattaching the same mapping
path cannot accept an externally changed mapping implicitly. These streaming
checks add file I/O to effective edits and initialization; they do not stage
copies of input files, create a journal or provide filesystem locking. A writer
after the final check, or one that changes and restores bytes between checks,
remains outside optimistic fingerprint detection.

`commitCodeData(candidate)` rejects foreign, consumed, stale or mismatched
candidates, including a save that changed the persistence baseline without a
logical revision change. It prepares the receipt before authoritative assignments, consumes
the candidate once, and increments logical/index revisions once when effective.
A no-op changes no revision. It never writes a file. Headless JSON projection now
builds a private copy, so read/stage operations do not normalize the live tree.

The runtime supplies an independent deep code-data copy to new `JadxArgs` and
the normal production factory. Plugins, ordered inputs, mapping attachment,
effective settings, fresh root/InfoStorage/alias provider/listeners, census and
hierarchy binding follow startup's existing path. No old analysis object or
source/search/reference cache is reused.

## Publication and failures

`EditContext.commit` and reload/mapping rebuild share `publishReplacement`; the
caller already owns exclusive admission. No nested admission occurs. Edits keep
READY while the private candidate loads. Before commit, the helper verifies a
bound hierarchy verifier and the candidate arguments against an independently
copied expected state. Candidate construction/load/census/bind/consistency failure
closes the candidate, preserves the active engine, repository, dirty/revisions,
source/search identities and files, and returns the existing internal/server
error. A factory that fails before returning an engine owns its construction
cleanup; production construction has no load/census resources yet.

All throwable preparation checkpoints, including swap preparation, run before
commit. Under the repository monitor and lifecycle lock, the runtime verifies
its expected lifecycle/repository/old-engine identity, commits, assigns the
replacement, advances publication epoch once, publishes admission/search
identity and keeps READY. The repository monitor stays held through the swap:
optimistic repository reads cannot return new data with an old publication epoch.
Engine/search/source/temporary reads require coordinator admission and cannot
interleave. Candidate load and old close happen outside these locks. Rebuild
fingerprint checks still happen inside the admitted commit; unlike candidate
loading, that final commit holds the lifecycle lock to exclude shutdown between
commit and publication.

Admission/search identity records and the ready status are allocated before
commit. After commit the ordinary path consists solely of assignments. There is no normal throwable test checkpoint
between commit and assignment. A fatal JVM `Error` (including allocation failure)
uses the existing FAILED/fatal supervised-shutdown behavior; no rollback or
journal is claimed. Ordinary pre-commit exceptions retain READY. Fatal JVM errors
remain fatal, even before commit. Cleanup exceptions from an obsolete engine are
logged by class name and do not undo publication or falsify a successful receipt.
Fatal cleanup errors use the existing fatal runtime policy.

The exclusive lease remains held through old-engine close. All previously
admitted primary readers must have finished before this lease can be acquired;
new readers cannot retain the old engine. Temporary work that already finished
snapshot capture owns an independent engine. Deprecated borrowed `decompiler()`
access remains for historical probes; HTTP/product reads use scoped admission.

## Batch and identity semantics

Every item is validated before staging. Item staging still copies privately;
one replacement is loaded for the final effective batch/prefix. APPLIED/FAILED/
SKIPPED prefix receipts are returned only after that prefix publishes. If its
replacement fails, there is no applied receipt and no authoritative prefix.
No-op avoids replacement, dirty/revision changes and source/search invalidation.
One effective batch increments logical, index and publication counters once.
Source snapshots, class/search cursors and queued jobs carry the new identity;
stale revisions/snapshots fail through existing gates. No autosave or hidden
async rebuild/cache is introduced.

## Executable evidence

- `CodeDataStagingTest`: deep copy, private staging, raw/native behavior, single
  consumption, no-op, foreign/stale candidate refusal and no file changes.
- `ReplacementPublicationTest`: clean/dirty failures before construction, in the
  factory, census/load/bind, verifier/consistency checks, commit preparation and
  repository commit; argument mismatch; prefix load failure and truthful prefix;
  no-op/prevalidation; obsolete close exception; latch-held load/commit/swap
  preparation/old close with edit/save/reload/mapping/temporary/read/shutdown
  conflicts and stale revision checks.
- `ReplacementStateTest`: production mixed declaration/comment/instance/static/
  wide/overloaded parameter edits, retained VAR, attached Tiny class/method aliases
  and comments, native precedence, raw/native/relative/unknown fields, and old
  cold/owner/unrelated/all-source/source-search-reference-metadata histories.
  It compares all original inventories and raw aliases (including bridges), Java,
  comments, annotations, scoped metadata, member search and outgoing dependencies
  with a separately built fresh oracle, and independently bound hierarchy results.
  Mapping-path rebuild then edit, save/restart, discard/reload and new sessions
  are exercised too.
- `EditBatchServiceTest.returnTypeOnlyOverloadsHaveDistinctNativeKeys`: original
  full method keys remain distinct; only integer `value()I` has an explicit
  `intValueAlias` record. String `value()` recomputes from `m0value` to `value`;
  active/fresh and explicit-save/reopen source/aliases agree with one native edit.
- `PropagatedNativeReplayTest`: ordinary exact per-member diagnostic records now
  publish through replacement. Joined remains COMPLETE, requested aliases agree,
  CovariantLeaf stays fresh-equivalent across cold/hot history and every first
  record. No propagation API is implied. `SafeReplayStrategyTest` retains the
  exact old implementation in test code: hot full-family bridge divergence,
  listener-first mapping/Override loss, owner-only stale caller state, and the
  return-only old-root/fresh difference remain negative controls.
- `ReplacementCostTest`: actual service no-op/1/64-item latency and construction,
  census/load/bind timings for 17/129/513 classes, cold/hot old engines, approximate
  heap samples and exactly zero/one candidate construction. Historical
  `SafeReplayCostTest` remains executable. Measurements are observations, not
  large-project guarantees or incremental/RSS limits.

The oracle deliberately permits automatic alias changes from the old engine;
explicit complete native and mapping intent must reconstruct exactly. Unsafe
scoped forms, original identity limits, strict import/export restrictions, and
every `propagateRelated` presence rejection remain covered by existing suites.

## Native and actual GUI gate

`replacementEditGuiRoundTripTest` opens the production-prepared mixed project in
actual matching Jadx 1.5.6, performs Save As, and reopens it headlessly. Preparation
first generates Java/search/reference/variable metadata, applies the normal
service batch, compares the fresh oracle, checks native/input/mapping hashes,
and explicitly saves. It includes the return-only clash, unrelated bridge
fixture, attached mapping aliases/comments, retained VAR, all supported mixed
edits, relative input/mapping references and unknown root/codeData/node fields.
Headless save preserves unknown fields; matching-GUI serialization's known loss
is asserted separately. Exact typed native intent and regenerated Java/aliases
must match before/after GUI Save As. All previous GUI tasks remain.

Final commands, counts, source audit, actual GUI evidence and cost observations
are recorded in [PR #18 review](pr-18-review.md). A normal opt-in skip is never
counted as a GUI pass.

## Historical PR #18 handoff for PR #19 (superseded)

`edit.related_propagation` stays UNSUPPORTED. Boolean/null/string/number/array/
object flag presence stays rejected; affectedRefs remains empty for ordinary
edits. Replacement solves mutable-root publication, not group admission.
PR #19 must verify COMPLETE under the same current exclusive lease, build an
immutable group plan, preflight collisions on every owner, stage exact original
records, publish exact affectedRefs, and prove failures/no-op/native/restart/HTTP/
actual-GUI propagation. Parameter propagation, bridges/covariant families,
locals and missing/external/duplicate hierarchies remain separate constraints.
