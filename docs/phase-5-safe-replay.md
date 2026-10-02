# Safe native replay: PR #17 historical evidence and PR #18 approved interpretation

> **Historical milestone record.** This document describes repository state at its named phase/PR. Its “next milestone,” “pending,” and support-status statements are historical. For current product state see the [documentation index](README.md), [compatibility](compatibility.md), [feasibility matrix](feasibility-matrix.md) and [PR #22 qualification](pr-22-review.md).

**Historical PR #17 outcome B (superseded by PR #18 below): no production mutation change.** No investigated generic strategy
passes both fresh-engine equivalence and unchanged raw nonmember aliases for all
currently supported declaration edits. Explicit propagation and local editing
remain unsupported. Jadx remains **1.5.6**, source
**28ff15e4ae69950aebea110a13e5ab895d234dfc**; dependencies, locks, OpenAPI,
DTOs, Python scope and production Java are unchanged.

## Two independent acceptance gates

PR #16's complete Joined family still fails the production hot replay path:
after generating Hierarchy Java, both original CovariantLeaf `value()`
declarations acquire `m0value`. Fresh native reopen has `value`. All four exact
Joined records and declaration tokens succeed. `PropagatedNativeReplayTest` is
unchanged and retains chain, diamond, default, inherited, simple JVM and direct
DEX controls, native save/restart and actual GUI diagnostics.

Replacement fixes that bridge example but fails a second, smaller gate already
encoded in `EditBatchServiceTest.returnTypeOnlyOverloadsHaveDistinctNativeKeys`.
The owned `SymbolFixtureSupport.returnTypeClashJar` creates a valid JVM class
with two original, nonsynthetic `value()` methods, returning int and String.
Both return descriptors are retained. Only the int declaration is edited.

| Logical transition | Int-return alias | Untouched String-return alias |
|---|---|---|
| Initial real engine, before/after source generation | value | m0value |
| Ordinary production RENAME of `value()I` | intValueAlias | m0value |
| Fresh engine with the same single native edit | intValueAlias | value |
| Private replacement with that native edit | intValueAlias | value |
| Explicit save / fresh native or actual GUI-resaved reopen | intValueAlias | value |

The native records contain only `value()I -> intValueAlias`. There is no native
record for the String-return method, no related family and no bridge. The
initial automatic alias exists because Java signatures cannot distinguish
return-only overloads; after the int rename, a fresh engine no longer needs it.
Keeping `m0value` fails the fresh oracle; recomputing `value` fails unchanged
nonmembers. This is not a load failure, cost rejection, missing mapping, or
unproved native persistence claim. The constraints conflict for this supported
edit under the pinned engine. We do not relax either gate.

A provisional generic replacement path was tested against the existing edit,
scoped and mapping suites. The unchanged-overload assertion failed
`expected m0value but was value`. Its XML is retained at
`/tmp/libjadx-pr17-replacement-compatibility-failure.xml`. The other two failures
were tests injecting the old replay hook, which a replacement implementation
would need to migrate. None counts as an accepted publication gate. All
provisional production and existing-test changes were reverted before final
validation; the new executable probe asserts the counterexample directly.

## Investigated strategies

`SafeReplayStrategyTest` runs identical deep-copied native candidates through
independent engines. No strategy is selected for production.

| Strategy | Executable result / rejection |
|---|---|
| 0 CURRENT | Calls the actual `ProjectRuntime.ProjectEngine.reloadCodeData` default method. Cold Joined agrees; hot Joined changes exactly the two raw CovariantLeaf method aliases. The return-only case preserves m0value and differs from fresh loading. |
| 1 LISTENER_FIRST | Sets copied candidate data, clears declaration comments, notifies listeners, then unloads all owners. Hot Joined keeps bridge aliases but drops emitted `@Override` declarations; full Java/metadata differ. Attached class comments also disappear after unload. The return-only case still differs from fresh. |
| 2 OWNERS_ONLY | Unloads exact top-level owners, derived from the owned original fixture keys, then uses the same global listener replay. Hot Hierarchy still changes both bridge aliases within that owner. A separate Target/Caller fixture leaves cached Caller Java using `target.call()` and `target.field` while fresh Java uses editedCall/editedField. This is not complete dependent invalidation. |
| 3 REPLACEMENT | Loads new JadxArgs/root/listeners/code data. Joined is fresh-equivalent for all four first-record orders, cold and hot, with unchanged bridge aliases. Complete native/mapping/scoped reconstruction agrees with a second fresh engine. Return-only collision removal changes an untouched raw alias, so generic adoption fails. |

No additional Strategy 4 ordering was proposed. No attributes are patched,
DONT_RENAME/MethodOverrideAttr changed, suffix aliases injected, unrelated native
records created, project types excluded or upstream dependency upgraded.
Owner-only is a deliberately limited diagnostic, not a claimed dependency
closure; its caller counterexample is sufficient to reject it as a generic path.

## Semantic oracle and edit-state coverage

The independent oracle constructs another fresh engine from the same ordered
input paths, mapping attachment, effective mode and a separately deep-copied
**complete** JadxCodeData. Source is generated in original top-level owner order.
`semantic` captures:

- Every Jadx-visible original class/method/field inventory key and effective alias, including
  raw synthetic/bridge nodes omitted by the visible JavaMethod list.
- Full generated Java, retaining declaration comments and scoped parameter tokens.
- Verified source declaration/reference annotations and scoped-variable DTOs from
  the existing source adapter, stamped with identical diagnostic identities.
- Sorted member-search adapter records and outgoing class-reference dependencies.

Process states are not semantic identity. A separately generated read-only
control distinguishes ordinary codegen changes from replay. The Joined test
asserts COMPLETE/four original refs, each requested declaration's alias and
actual emitted token, every possible member first, both hot/cold states and
independent census binding. Only the two bridge raw aliases differ in the hot
CURRENT negative control. Replacement's bound family remains COMPLETE.
Existing PR #15 tests retain incomplete-family, census and lifecycle boundaries.

The complete-state reconstruction probe uses RelatedFixture plus Variables:
native class/method/field aliases, multiple native records, LINE declaration
comments, unrelated native rename/comment, instance wide MTH_ARG, static wide
MTH_ARG, overloaded String parameter, declaration plus parameter on the same
method, mapped class and method aliases and mapping comments. It retains a native
VAR record on a different method; its reg/SSA key is recovered from real emitted
metadata instead of guessed. The public conservative refusal for competing VAR
keys remains exercised by existing scoped tests. AUTO/RESTRUCTURE/SIMPLE/FALLBACK
are compared; only the two supported structured modes assert editable parameter
tokens. This proves those reconstruction cases, not a generic adoption gate.

The new service/native diagnostic generates source before an ordinary supported
rename, verifies the original nonmember alias stays m0value in production,
checks one logical/index increment and no autosave, explicitly saves, and reopens
with a fresh engine observing value. Headless save preserves nested unknown
node-ref JSON and root fields; the input hash and relative path remain intact.
Existing native/scoped/mapping regressions cover dirty state, unknown JSON,
relative mapping paths, mapping rebuild then edit, restart, discard, no-op,
stale revision/source/search tokens, external changes and installed launch scripts.
No new acceptance claim is made for unsupported propagated transactions.

Artifacts under `build/safe-replay-probe/` contain Joined candidate/fresh snapshots
and differing keys for replay strategies, complete-state snapshots for each mode,
all eight cold/hot collision strategy comparisons, service/save comparisons,
GUI reopen evidence and performance.json. Build artifacts are diagnostic outputs,
not a persistent project index or state store.

## Pinned source audit

Local source: `/tmp/libjadx-pr13-source`. Sixteen files were byte-compared with
new commit-pinned upstream downloads; hashes are in
`/tmp/libjadx-pr17-source-audit.json`. No master source was used.

- [JadxDecompiler.java](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/JadxDecompiler.java): load resets plugin/root/class state, constructs a new RootNode and initializes passes; reloadCodeData only notifies root listeners. It is not a fresh reload.

- [ClassNode.java](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/nodes/ClassNode.java): unloadCode skips NOT_LOADED, otherwise removes cached Java and deepUnload reconstructs methods from clsData. MethodInfo is reused; not every reconstructed attribute equals original loaded state.

- [ProcessClass.java](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/ProcessClass.java): runs class prepasses for CLASS_UNLOADED and tracks generated/unloaded owners; code generation depends on current mutable analysis state.

- [RenameVisitor.java](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/visitors/rename/RenameVisitor.java): registers a global listener; UserRenames is followed by collision checks on all classes. Collision signatures exclude return types; the same-owner bridge guard depends on METHOD_OVERRIDE.

- [UserRenames.java](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/visitors/rename/UserRenames.java): groups original declaration records by owner, resolves raw keys and calls MethodNode.rename. It is not a bounded owner listener.

- [MethodNode.java](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/nodes/MethodNode.java): rename applies through current related nodes when METHOD_OVERRIDE exists, otherwise changes only MethodInfo alias.

- [MethodOverrideAttr.java](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/attributes/nodes/MethodOverrideAttr.java): is a pinned attribute carrying related nodes; plain attribute unload and rebuilding MethodNodes are different mechanisms.

- [OverrideMethodVisitor.java](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/visitors/OverrideMethodVisitor.java): initializes override relationships and may return before later independent branches, as established in PR #14; candidate membership is not authority.

- [RootNode.java](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/nodes/RootNode.java): owns listener order, InfoStorage and prepasses; init invokes the argument alias provider. Notification neither replaces the root nor reruns the entire original load pipeline.

- [InfoStorage.java](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/info/InfoStorage.java): interns MethodInfo/FieldInfo/ClassInfo within a root, retaining alias state across node reconstruction. New root construction does not carry that storage.

- [JadxArgs.java](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/JadxArgs.java): initializes a new DeobfAliasProvider per argument object; our fresh engines use new arguments and deep code-data copies.

- [DeobfAliasProvider.java](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/deobf/DeobfAliasProvider.java): owns generated alias counters. init sets maximum length, not a reset of all existing aliases/counters. Fresh args start counters at zero.

- [AttachCommentsVisitor.java](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/visitors/AttachCommentsVisitor.java): registers a code-data listener for comments, applying comments during class passes; replay does not independently regenerate Java.

- [CodeRenameVisitor.java](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/visitors/rename/CodeRenameVisitor.java): registers scoped rename data and applies original MTH_ARG/VAR keys during processing.

- [ApplyMappingsPass.java](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-plugins/jadx-rename-mappings/src/main/java/jadx/plugins/mappings/load/ApplyMappingsPass.java): applies mapped aliases/comments before RenameVisitor and registers replay. Deep unload after this replay erases attached declaration comments.

- [CodeMappingsPass.java](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-plugins/jadx-rename-mappings/src/main/java/jadx/plugins/mappings/load/CodeMappingsPass.java): registers mapping state and applies argument mappings before CodeRenameVisitor; native scoped precedence is retained in complete-state reconstruction.

## Publication, failure and admission state

No replacement primitive is shipped under Outcome B. The reviewed existing
rebuild architecture remains the appropriate starting point if the preservation
boundary is revised: stage immutable state, load privately, commit repository
state only after successful loading, publish under the lifecycle lock, then
close the old engine under the same exclusive admission. No alternative swap
architecture, background task or fatal-error workaround is installed here.

Current production batches still prevalidate all items and privately stage a
verified prefix; unexpected per-item failure retains APPLIED/FAILED/SKIPPED
receipts. An effective prefix commits repository data once and replays into the
existing engine. A replay failure after repository mutation still fails the
runtime; no rollback is claimed. No-op revisions/dirty/source/search identities,
exclusive admission, save/reload/settings conflicts and shutdown behavior are
unchanged and covered by renewed regressions.

New candidate-copy/factory/load/semantic/post-commit fault gates, atomic replacement
publication, old-engine close ownership and verifier rebinding acceptance tests
are deferred with the withdrawn primitive. Existing production fault/concurrency
coverage was not removed or weakened. This PR does not claim successful safe
publication, replacement rollback, zero repository/engine mismatch on current
replay failure, or successful stale-token semantics for an unimplemented swap.

## Cost and boundedness

SafeReplayCostTest measures owned generated JVM fixtures of 17, 129 and 513 raw
classes (eight methods per generated C class plus constructors). One prior
engine remains live; cold and already-generated owner variants are measured.
Candidates contain 1 or 64 exact declaration records. The 0-item control copies
native data without constructing an engine, modeling an avoided no-op rebuild;
it is not a measurement of public-service prevalidation. Timings separately
record code-data copy, raw census and construction/load/binding. Census is
captured before candidate load. These are single measurements in a warmed test
JVM, not latency guarantees or evidence for APK/large production projects.

Initial passing feasibility measurements were approximately 269–429 ms for
construction/load/binding and 9–84 ms for extra census. Whole test-JVM heap peak
samples were 263–315 MiB. Final-run values and exact commands are recorded in
[PR #17 review](pr-17-review.md). Heap values include the old engine, harness,
previous samples and GC effects; summed pool peaks are an approximate upper
sample, not an incremental or simultaneous RSS/heap guarantee.

No resource limit is imposed on ordinary editing because no replacement is
adopted. A future synchronous implementation should retain one replacement per
exclusive batch, existing 64-item/request budgets and bounded census limits;
no hidden async rebuild or persistent cache is proposed. The observed cost is
not the reason for Outcome B: alias preservation is the blocker.

## Actual matching GUI diagnostic and next decision

safeReplayGuiDiagnosticTest uses real matching Jadx 1.5.6 Save As on the new
service-produced collision project. It asserts the exact original int rename
record, preserved comment and relative input path, and fresh headless Java
with untouched String alias value. The hot service had m0value. GUI unknown-field
loss is distinguished from headless preservation. This is evidence of the
conflict, not an accepted service replacement round trip. All eight previous
GUI tasks remain. There is deliberately no safeReplayGuiRoundTripTest: Outcome B
changes no production publication.

Before PR #18, the smallest human decision is whether future fresh replacement
may recompute automatic aliases of unedited declarations when a rename removes
a collision. If approved, revise that specific preservation expectation and
implement/prove the internal primitive with safe publication, injected faults,
whole-state, lease, native/restart, packaged and actual service GUI gates. If the
constraint stands, investigate another bounded strategy; an upgrade probe would
need separate approval and cannot by itself resolve contradictory alias gates.
Do not move directly to group admission or exclude bridge projects.

Propagation remains UNSUPPORTED because neither unchanged COMPLETE membership
nor positive replacement reconstruction proves an admitted group transaction.
Same-exclusive-lease verification, all-owner collision preflight, immutable group
staging, exact affectedRefs, prefix/fault receipts and propagated service/native/
restart/GUI gates remain outstanding. Local editing and Phase 6 scope remain
separate decisions. No schema accepts propagateRelated in this PR.

## PR #18 approved alias semantics and replacement publication

This section supersedes PR #17's requirement to preserve every incidental alias.
Automatic Jadx aliases are derived analysis state: collision aliases,
deobfuscation aliases without explicit persistence, and other generated aliases
may be recomputed when an edit changes analysis. A declaration with no explicit
rename may therefore change its display alias without a separate user edit.
Native declaration renames, mapping aliases/comments, supported scoped renames,
retained VAR records and declaration comments remain authoritative. Original
identities, settings, ordered input references and unknown native fields retain
their existing preservation rules. Never add native records to freeze generated
aliases.

Effective native batches now privately stage complete code data, load one fresh
production engine, commit native data once, then publish that engine under the
existing exclusive lease. No-op retains the engine and revisions; candidate
failure publishes nothing. See [replacement publication](phase-5-replacement-publication.md) for ordering,
failures, oracle, persistence and validation. Related propagation and local
editing remain unsupported. PR #19 still requires same-lease COMPLETE verification,
immutable group plans, all-owner collision admission, exact original native
records and affectedRefs, and propagated HTTP/native/restart/actual-GUI gates.
