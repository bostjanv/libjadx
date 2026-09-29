# PR #18 review: fresh-equivalent replacement-engine edit publication

**Outcome A.** Production publication, fresh equivalence, failure/lease semantics,
full regression and actual matching-GUI gates passed. Propagation remains unsupported.

Implementation is on `pr-18-replacement-publication`, based on the requested
merged PR #17 commit `d3d0ef4a4959e6f8d96f11babd57a4a6dd389631`. The workspace
initially held PR #17's topic head; fetching main confirmed its tree was identical
and the patch was moved to a new branch at the exact target base. The final implementation fingerprint is
`sha256:d272120c5c91c98bdc80866f4787cadfea7b3bcae0f6286918d777f9c0556ce9`;
`/tmp/pr18-implementation-final-manifest.json` records 17 changed implementation/
contract/test paths and hashes. Documentation is validated separately. No dependency,
SDK, wire-schema or persistence-format change is introduced.

## Implemented and evidence

Production declaration/parameter/comment batches and mapping imports now use a
repository-bound complete native candidate, one fresh production engine, and a
single commit/publication under the existing project-exclusive lease. Reload and
mapping rebuild share the helper. Automatic aliases are derived; explicit native,
mapping and scoped intent remains authoritative. The decision is recorded in
DESIGN, IMPLEMENTATION, ADR 0001, safe replay notes, README and the OpenAPI operation
description. Detailed ordering and test coverage are in
[replacement publication](phase-5-replacement-publication.md).

Pinned Jadx 1.5.6 source is `28ff15e4ae69950aebea110a13e5ab895d234dfc`.
The local `/tmp/libjadx-pr13-source` files for decompiler/arguments/root/InfoStorage,
rename/override/comment/scoped visitors and mapping passes were inspected and all
16 hashes matched `/tmp/libjadx-pr17-source-audit.json`. That is a local hash
comparison against the retained commit-pinned audit, not a newly downloaded or
master-source claim. The production adapter retains normal plugin/census/verifier
initialization. JDK 21.0.12.1, Gradle 8.14.3, Linux; actual GUI executable
`/tmp/libjadx-rerun-jadx-1.5.6/bin/jadx-gui`, matching CLI reports 1.5.6.

New gates are `CodeDataStagingTest`, `ReplacementPublicationTest`,
`ReplacementStateTest`, `ReplacementCostTest`, and
`replacementEditGuiRoundTripTest`. The old PR #17 replay implementation is
retained exactly in `SafeReplayStrategyTest` as test-only negative evidence;
production has no replay method. ReturnClash now explicitly proves the approved
String alias change to value with exactly one native integer-method edit and
fresh/save/reopen agreement. PR #16 production diagnostic batches now agree
with fresh engines and GUI reopen; its old hot-root counterexample remains in
the strategy test. Existing fault tests were migrated from obsolete listener
replay to pre-commit candidate loading, with READY and unchanged data assertions.

## Final validation

Final implementation commands and counts apply to the fingerprint above.
Earlier development runs exposed obsolete engine/replay assertions and one
latch-test common-pool starvation; these were fixed. Earlier counts are not
used as final evidence.

```bash
./gradlew test --offline \
  --tests 'dev.libjadx.*Replacement*' --tests 'dev.libjadx.*Replay*' \
  --tests 'dev.libjadx.*Propagat*' --tests 'dev.libjadx.*Hierarchy*' \
  --tests 'dev.libjadx.app.*Edit*' --tests 'dev.libjadx.app.*Scoped*' \
  --tests dev.libjadx.project.CodeDataStagingTest \
  --tests dev.libjadx.app.OpenApiDocumentTest

./gradlew clean check --offline --rerun-tasks
./gradlew installDist --offline

JADX_GUI=/tmp/libjadx-rerun-jadx-1.5.6/bin/jadx-gui \
  ./gradlew guiRoundTripTest rawGuiRoundTripTest nativeEditGuiRoundTripTest \
  mappingExportGuiRoundTripTest mappingImportGuiRoundTripTest \
  scopedEditGuiRoundTripTest relatedPropagationGuiRoundTripTest \
  propagatedEditReplayGuiDiagnosticTest safeReplayGuiDiagnosticTest \
  replacementEditGuiRoundTripTest --offline --rerun-tasks -x test

/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-edit-contract.py
/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-mapping-export-contract.py
/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-mapping-import-contract.py
/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-search-contract.py

git diff --check
git diff --check origin/main...HEAD
```

The GUI command reuses fixture preparation from the successful clean check,
reruns every dedicated Save As/reverse task, and never counts a skip as a pass.
Build reports/captures are final-patch outputs under `build/test-results`,
`build/reports/tests`, existing contract-response directories and
`build/replacement-edit-gui-fixture`. Logs and count snapshots are under
`/tmp/pr18-*-final.*`. No remote CI or independent review is claimed.


| Final gate | Result |
|---|---|
| Focused suite | BUILD SUCCESSFUL in 3m 48s; 32 classes, 140 tests, 136 passed, four opt-in skips, zero failures/errors |
| Clean full check | BUILD SUCCESSFUL in 8m 14s; 77 classes, 404 tests, 394 passed, ten opt-in skips, zero failures/errors |
| Distribution | BUILD SUCCESSFUL in 1s; full check rebuilt installDist and executed packaged process/restart gates |
| Edit contract | OpenAPI 3.1 valid; 11 examples, ten live HTTP responses, four injected results, 18 scoped responses and 14 propagation rejection responses valid |
| Mapping export/import contracts | Three/four examples and 31/41 fresh live HTTP responses valid |
| Search contract | Four examples and 33 fresh live HTTP responses valid |
| Actual matching GUI | BUILD SUCCESSFUL in 4m 19s; all ten dedicated tasks passed, zero skips/failures/errors; 18 actual Save As runs |
| Whitespace and patch identity | Working-tree, origin/main...HEAD and new-file whitespace checks passed; implementation hashes unchanged |

Counts are saved in `/tmp/pr18-{focused,clean,gui}-final-counts.json` and logs in
`/tmp/pr18-{focused,clean,distribution,gui}-final.log`. All HTTP captures were
regenerated by clean check; no retained responses or prior-head results were
counted.

## Final-run production cost observations

The table records one sample per case in the clean full test JVM. C/H means the
old engine was cold/fully generated. Times are milliseconds and include service
prevalidation/staging/commit/publication/cleanup, excluding HTTP transport.
The no-op is a valid request for the current name, not an empty invalid batch.
It builds zero candidates; every effective 1/64-item batch builds one.

| Classes | Old | Items | Service | Construct | Census | Load | Bind |
|---:|---|---:|---:|---:|---:|---:|---:|
| 17 | C | 0 | 7.59 | 0.00 | 0.00 | 0.00 | 0.00 |
| 17 | C | 1 | 245.02 | 0.06 | 2.82 | 232.26 | 1.06 |
| 17 | C | 64 | 333.26 | 0.15 | 3.35 | 255.17 | 1.15 |
| 17 | H | 0 | 1.40 | 0.00 | 0.00 | 0.00 | 0.00 |
| 17 | H | 1 | 479.84 | 0.15 | 2.97 | 467.41 | 1.46 |
| 17 | H | 64 | 469.65 | 0.07 | 1.86 | 427.58 | 1.04 |
| 129 | C | 0 | 6.90 | 0.00 | 0.00 | 0.00 | 0.00 |
| 129 | C | 1 | 182.92 | 0.13 | 8.63 | 161.54 | 1.67 |
| 129 | C | 64 | 370.76 | 0.06 | 8.48 | 301.44 | 4.19 |
| 129 | H | 0 | 2.62 | 0.00 | 0.00 | 0.00 | 0.00 |
| 129 | H | 1 | 250.28 | 0.06 | 7.96 | 234.08 | 2.98 |
| 129 | H | 64 | 401.59 | 0.14 | 13.90 | 342.70 | 1.82 |
| 513 | C | 0 | 19.36 | 0.00 | 0.00 | 0.00 | 0.00 |
| 513 | C | 1 | 327.72 | 0.05 | 26.99 | 285.42 | 5.59 |
| 513 | C | 64 | 481.98 | 0.17 | 20.33 | 396.87 | 11.08 |
| 513 | H | 0 | 2.88 | 0.00 | 0.00 | 0.00 | 0.00 |
| 513 | H | 1 | 315.02 | 0.10 | 16.79 | 274.22 | 14.59 |
| 513 | H | 64 | 408.31 | 0.15 | 25.87 | 322.03 | 12.36 |

Maximum summed heap-pool peaks for 17/129/513 classes were approximately
349.0/354.5/327.5 MiB. These include the old engine, test harness, prior allocations
and GC effects; they are not incremental replacement memory or RSS guarantees.
`build/replacement-publication-probe/performance.json` contains all before/after
heap observations and phase timings. Historical copy/census/load measurements
were rerun in `SafeReplayCostTest` and remain in
`build/safe-replay-probe/performance.json`. No hidden async rebuild, persistent
cache or inferred production resource cap is added from these small fixtures.

## PR-description checklist

1. Automatic recomputation is recorded in DESIGN, IMPLEMENTATION, ADR 0001,
   safe replay notes, README and OpenAPI operation description.
2. Explicit native declaration renames/comments, mapping aliases/comments,
   supported parameter renames and retained VAR records are authoritative intent.
3. Collision/deobfuscation and other generated aliases without explicit persistence
   are rebuildable; recomputation creates no user edit record.
4. `stageCodeData` stages full private copies with owner/revision/persistence
   baseline and single-consumption protection for `commitCodeData`.
5. Candidate construction, census, load, hierarchy binding and consistency finish
   before native commit. Throwable swap preparation also precedes commit.
6. Post-commit edit publication contains only assignments; metadata/status/receipt
   allocations happen first. Fatal JVM errors use FAILED/supervised shutdown,
   with no rollback guarantee. No ordinary throwable post-commit seam remains.
7. Exclusive read admission plus the repository monitor spanning commit/swap
   prevents observing new repository with old engine.
8. The same locks prevent observing new engine with old repository. Latches cover
   load/commit/swap preparation, blocked repository readers and old cleanup.
9. Old close exceptions are logged and retain the successful publication;
   fatal cleanup errors use the existing fatal policy.
10. Each effective batch or prefix increments logical/index/publication once.
11. No-op preserves engine, revisions, dirty, source/search identities and files.
12. Private staging failure at item N may publish its verified prefix once,
    retaining APPLIED/FAILED/SKIPPED results for the actual published state.
13. Candidate failure for that prefix publishes nothing and returns no APPLIED
    receipt; the old project remains READY for ordinary exceptions.
14. ReturnClash's untouched String method recomputes to value without a native
    record. The integer record alone has intValueAlias; original full keys remain
    independent and explicit-save/fresh reopen agree.
15. Joined/CovariantLeaf production hot/cold and every-first-record diagnostics
    agree with fresh reconstruction. The old in-place bridge failure remains
    executable in the retained PR #17 strategy control.
16. Replacement binds its own census/hierarchy verifier. Escaped old handles
    remain confined to their old callbacks.
17. Class/method/field/LINE/parameter/VAR/Tiny state is compared against a separate
    fresh oracle, including source/annotations/search/references and hierarchy.
18. Actual matching-GUI production replacement Save As passed. The mixed project
    retains nine typed rename records, two LINE comments and relative state.tiny;
    fresh Java/aliases agree. Headless root/codeData/node unknown fields survive;
    all three are lost by matching GUI serialization, separately observed.
19. Every propagateRelated presence remains rejected, including malformed values.
20. `edit.related_propagation` remains UNSUPPORTED; affectedRefs remains empty.
21. PR #19 still requires same-lease COMPLETE verification, immutable all-owner
    collision plans, exact native records/affectedRefs and propagation HTTP/native/
    restart/actual-GUI admission gates. Locals and parameter propagation remain
    unsupported. No Phase 5.2 or SDK release-completion claim is made.

Together with dedicated GUI execution, all **404 distinct tests** executed
successfully. The new positive fixture is
`build/replacement-edit-gui-fixture/{replacement,gui-resaved}.jadx`, with its
copied actual GUI log at `actual-gui.log`. Normalized typed native records and
full fresh-oracle source/metadata agree before and after Save As. This proves
owned fixture compatibility, not every Jadx-supported format or GUI widget.

## Persistence and next milestone

Edits remain in memory until explicit native save. Current-process source reflects
replacement edits; discard/restart and discard/reload reconstruct persisted intent
and fresh aliases, while save/restart retains intent under a new session. Relative
paths and headless unknown fields retain their existing rules. Matching GUI loss
is documented rather than attributed to headless save. There is no autosave,
sidecar, persistent index, replacement cache, journal or project switching.

PR #19 is the next group-admission milestone; no further human alias decision is
required for this approved replacement path. Phase 5.2 and the Python release gate
are not declared complete by PR #18.
