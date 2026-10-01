# PR #13: snapshot-bound native parameter renames

**Current status:** PR #20 closes [local feasibility](phase-5-local-rename-feasibility.md)
with local rename deliberately unsupported and Phase 5.2 complete. PR #19 admits
its verified related-method subset. The original PR #13 counterexample and
historical scope below are preserved.

Base: merged PR #12, `10d10048eb8254b77201a0ced9db845646eeebe9`.
Jadx stays at 1.5.6, source commit
`28ff15e4ae69950aebea110a13e5ab895d234dfc`; JDK 21 and all dependency locks
are unchanged. This slice implements parameters only. Local renames and related
method/override propagation were unsupported at this historical PR #13 milestone.

## Verified parameter semantics and contract

`POST /api/v1/decompile` now always returns `variables` (possibly empty),
independent of declaration/reference annotation selection. Each discovered
entry contains its original method ref, exact emitted declaration token/range,
source snapshot, kind and persistability. This is partial emitted metadata,
not a census of original source variables. Locals have no edit ID. Entries
whose original positional identity is unverified carry a null parameter index.

`RENAME_PARAMETER` extends the existing `/edits/batch` operation union.
The request uses `method`, zero-based `parameterIndex`, `sourceSnapshotId` and
`newName`. Session and logical revision are mandatory for any batch containing
this operation; legacy declaration-only batches retain their existing optional
preconditions. Item results echo the containing method as `target`, plus the
parameter index and supplied snapshot. There is no additional mutation route.
Reviewed requests/results and `decompile-variables.json` are under
`openapi/examples/`; Java and independent Python validators cover them. Python
SDK generation remains the separately scheduled Phase 6 deliverable.

Indexes are original semantic positions: `this` is excluded and wide `long`
and `double` arguments count once. The executable fixture exercises static,
instance, zero/one/multiple/wide/object/primitive arguments, overloaded methods,
absent bytecode parameter names, generated temporaries, loops and branches.
Only AUTO/RESTRUCTURE, concrete ordinary nonsynthetic/nonbridge methods with
complete verified plain signatures and exact declaration metadata are editable.
Skipped/transformed, annotated, bodyless, special/constructor and unproved
generic signatures are omitted or marked unsupported. Exact per-input origin
remains unavailable where Jadx merges definitions.

Native persistence uses the original method `JadxNodeRef` plus native
`JadxCodeRef.forMthArg(index)`, never display names, source offsets or registers.
A duplicate native MTH_ARG key is ambiguous. A GUI-generated VAR rename or
other non-MTH_ARG scoped record on the same method is conservatively rejected,
because translating all native VAR keys into parameters has not been proved.

## Snapshot admission, collision policy and budgets

Every item is resolved and prevalidated under one exclusive project lease,
against metadata from the same `ICodeInfo` string used for the snapshot hash.
The hash binds session, logical revision, publication epoch, effective settings,
original emitted owner and exact UTF-16 source. A fresh revision cannot authorize
an old source snapshot. Mutation, settings rebuild, explicit reload, native-save
publication and restart invalidate prior snapshots. Temporary-mode metadata is
read-only; a SIMPLE snapshot cannot authorize a primary AUTO edit. No stale
retry or name-based target lookup exists.

All declared variable names within the containing method are conservatively
considered overlapping. Existing parameter/local names and proposed parameter
names must be unique. Because `variables` is partial metadata, each emitted catch
header is also checked lexically inside the verified method range. Its exact name
token must match a same-method VarNode declaration. Missing metadata or unfamiliar
catch syntax marks all parameters in that method `UNSUPPORTED` with null indexes;
parameter batches reject them with `422 UNSUPPORTED_CAPABILITY` before staging.
Verified catch declarations remain supported. Comments, quoted literals and text
blocks are skipped; names are never inferred as native edit identities.
Name swaps into an occupied name are rejected even if a
complete batch would free it: a staging failure could otherwise commit an unsafe
prefix. Field shadowing is allowed and the fixture checks emitted `this.field`.
Names use shared nonreserved ASCII Java identifier validation (128 characters).
Lexical scope precision is not claimed.

The existing 1–64 items/64 KiB HTTP bounds remain. Scoped source uses the
existing 4 MiB per-owner source bound, 200000 metadata annotations, 20000 variable
declarations, and an aggregate admitted-source budget of 16 MiB UTF-16 storage
and 20000 returned variables. Only requested owners are generated; the unrelated
owner remains unprocessed. Existing project-wide publication invalidation is
retained, and search coverage refresh remains lazy.

An effective batch stages private native data and commits once; revisions
advance once, dirty becomes true, and no file is written. No-ops retain cache,
revision and dirty identity. Fault injection reports the real applied prefix,
failed item and skipped suffix. Post-replacement replay failure returns a bounded
HTTP 500 and leaves the lifecycle FAILED; rollback is not advertised.

## Local feasibility and unsupported boundary

The owned `tests/fixtures/variables/Variables.java` fixture has reused JVM
registers, multiple same-type locals, branch declarations, loops, merged SSA
variables and generated expressions. `JadxVariableProbeTest` compares emitted
VarNode definitions after repeated generation, native save/reopen, code-data
replay and an unrelated native declaration rename. Ordinary RESTRUCTURE local
names survive those tested operations, which is narrower than a safe general
persistent-local guarantee.

The merged method's emitted local metadata changes from `(reg=5, ssa=1)` in
RESTRUCTURE to `(reg=5, ssa=0)` in SIMPLE. FALLBACK emits no variable declaration
metadata. The executable negative assertions preserve this evidence. A native
VAR key has no originating settings or snapshot binding in `.jadx`; these probes
do not establish settings-independent targeting or a safe general subset after
GUI configuration changes. Therefore `RENAME_LOCAL` remains
`422 UNSUPPORTED_CAPABILITY`, `edit.local_rename` remains UNSUPPORTED and no
opaque local IDs or persisted LibJadx fields are introduced. Propagation also
remains UNSUPPORTED. A future local slice needs additional retargeting and
matching-GUI evidence; this allowed parameter-only slice needs no architecture
change.

## Pinned source inspected

Exact source is locally available at `/tmp/libjadx-pr13-source`:

- [`BlockExceptionHandler`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/visitors/blocks/BlockExceptionHandler.java)
  `fixMoveExceptionInsn` creates `NamedArg("unused", ...)` when the handler has
  no move-exception instruction. [`RegionGen`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/codegen/RegionGen.java)
  `makeCatchBlock` attaches VarNode definitions for RegisterArg only; NamedArg
  is emitted via `NameGen.assignNamedArg` without variable metadata. The owned
  `UnusedCatch.smali` fixture discards the exception value without a move-exception.
  `JadxVariableProbeTest` verifies missing metadata and the silent `unused2`
  reassignment after a raw native parameter rename in AUTO and RESTRUCTURE.
  `ScopedParameterServiceTest` verifies full mixed-batch rejection without native,
  revision, pending-edit, source or unrelated-owner changes; HTTP captures verify
  unsupported parameter metadata and the structured rejection.
- [`JadxCodeRef`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/data/impl/JadxCodeRef.java)
  and `JadxCodeRename`: native MTH_ARG/VAR encoding and node-plus-code identity.
- [`CodeRenameVisitor`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/visitors/rename/CodeRenameVisitor.java):
  original method short-ID lookup, semantic argument list indexing and SSA consumer.
- [`MethodNode`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/nodes/MethodNode.java)
  `initArguments` separates this and advances register slots by type width;
  `getArgRegs` contains one entry per semantic argument.
- [`MethodGen`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/codegen/MethodGen.java)
  emits argument VarNode definitions in that order; skipped arguments are why
  count/signature verification must fail closed.
- [`VarNode`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/metadata/annotations/VarNode.java),
  `JavaVariable`, `InitCodeVariables`, SSA transformations and `ProcessClass`:
  merged CodeVars cache one VarNode, while argument registers unload after source
  generation. Only probes use the test/debug DONT_UNLOAD_CLASS flag.
- [`JVariable`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-gui/src/main/java/jadx/gui/treemodel/JVariable.java)
  emits VAR keys, including for parameters; this is separate from MTH_ARG support.
- [`JadxProject`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-gui/src/main/java/jadx/gui/settings/JadxProject.java)
  registers native scoped code-ref serialization. The server codec reuses native
  forms and preserves unknown JSON; it never calls source-export `save()`.

## Persistence and regression evidence

`ScopedParameterServiceTest` proves unsaved discard, explicit save/headless
reopen, stale source rejection after owner edits/settings/reload, native/mapping
external-change refusal and retention of unrelated dirty declaration edits.
`StandaloneDistributionTest.installedScopedParameterSaveRestartDiscardAndOldSnapshotRejection`
launches three fresh packaged headless JVMs, verifies saved parameters, loss of
unsaved changes and rejection of both stale sessions and old source hashes.
HTTP captures cover success/no-op, invalid name/preconditions, missing method,
stale revision/snapshot, unsupported targets/locals, content type, budgets,
lifecycle and injected replacement-load failure preserving READY, plus source/search invalidation.

The new nested-native-field regression failed before the codec fix:
`scopedNativeEntryAndNestedUnknownFieldsSurviveExplicitSave` lost `futureRename`
when unknown members appeared inside node/code references. The fix compares
native typed reference identity and merges unknown nested members. The exact
focused command then passed all three selected tests (the regression plus two
existing NativeProjectDocument tests). No persistence format was added.
Initial service tests also exposed the mistaken exclusion of AUTO; accepting
verified structured AUTO source corrected the positive tests.

`scopedEditGuiRoundTripTest` gets targets from public service metadata, stages
wide/static/instance/overloaded parameter renames plus a declaration alias and
comment, verifies unchanged native bytes while dirty, explicitly saves, drives
actual matching Jadx 1.5.6 GUI Save As, then reopens and verifies exact emitted
parameter declaration tokens by original method. Attached mapping, input paths,
comments and unrelated mapped aliases are checked. Unknown JSON survives the
headless save; the upstream GUI model drops unknown members, as Phase 0 already
established. GUI automation proves resave and headless reverse behavior; it does
not claim visual inspection of every rendered variable.

## Final validation

Final commands, suite counts and dedicated GUI outcomes are recorded in
[PR #13 review record](pr-13-review.md). Build reports are under
`build/reports/tests/` and `build/test-results/`; fresh scoped HTTP captures are
under `build/scoped-edit-contract-responses/`; the GUI-resaved project is
`build/scoped-edit-gui-fixture/gui-resaved.jadx`. Normal opt-in skips are not GUI
passes. Linux/JDK 21 is the verified environment; Windows/macOS and DEX variable
identity are not claimed.

The historical follow-up gates are now resolved by PR #19 group admission and
PR #20 unsupported-local decision. Next milestone: Phase 6 Python SDK.

PR #18 parameter batches now use [fresh replacement publication](phase-5-replacement-publication.md),
retaining the same source-snapshot and scoped-key restrictions. Complete native
parameter and existing VAR state is copied; no local-variable admission is added.
