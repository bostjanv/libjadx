# PR #17 review: safe native code-data replay / replacement-engine feasibility

**Outcome B.** Four executable strategies were investigated; none passes both
fresh equivalence and unchanged raw nonmembers for every supported edit.
Production Java, existing tests, OpenAPI, DTOs, SDK scope, dependency locks and
persistence behavior are unchanged. Related propagation remains UNSUPPORTED.

Requested base / checkout HEAD: `01462d69de506f4b0709775508fdad82bf00632d`.
Results cover the final implementation patch on this base, fingerprint
`sha256:d74ee47b41194ecfca4dc9ba71fb02f64f8bbeb637530b06486feef4d778e823`.
The sorted path/content manifest is `/tmp/libjadx-pr17-implementation-hash.json`;
it includes build task registration, both new tests, retained PR #16 test and
owned fixture sources/helpers. Documentation is checked separately. No remote
CI or independent reviewer rerun is claimed. Committing and publishing this
patch does not change the validated implementation fingerprint.

## Implemented and pinned evidence

SafeReplayStrategyTest adds the exact old-path negative control, all Joined
first-record orders and hot/cold comparisons, raw aliases (including bridges),
Java/comments/scoped-variable/source-annotation snapshots, member-search records
and outgoing reference dependencies. It rejects listener-first for lost mapping
comments/Override output, owner-only for bridge aliases and stale Caller Java,
and generic replacement for changed untouched automatic ReturnClash alias.
Complete native/mapping/scoped-state reconstruction and matching-GUI saved-record
diagnostics are distinct positive observations, not adoption gates.

SafeReplayCostTest records copy/census/load/heap observations at three owned
sizes and 0/1/64 candidate records with a prior engine live. build.gradle.kts
adds only the separate safeReplayGuiDiagnosticTest and its actual GUI Save As
preparation task. There is no safeReplayGuiRoundTripTest under Outcome B.

[Full strategy matrix, semantic oracle and pinned source links](phase-5-safe-replay.md)
explain the blocker. Sixteen source files were byte-compared with fresh downloads
at Jadx **1.5.6** source **28ff15e4ae69950aebea110a13e5ab895d234dfc**;
`/tmp/libjadx-pr17-source-audit.json` records the hashes. Local source is
`/tmp/libjadx-pr13-source`. JDK **21.0.12.1**, Gradle **8.14.3**, Linux amd64;
matching GUI `/tmp/libjadx-rerun-jadx-1.5.6/bin/jadx-gui`, matching CLI --version
**1.5.6**; validator Python **3.14.4** with the repository's pinned requirements.

A provisional production replacement was tested and rejected. Its unchanged
return-only overload regression failed `expected m0value but was value`.
`/tmp/libjadx-pr17-replacement-compatibility-failure.xml` preserves that failure;
`/tmp/libjadx-pr17-implementation.log` records the 79-test provisional run.
The other two failures targeted the obsolete replay fault hook. All provisional
changes were removed. Final validation applies to the retained Outcome B patch.

## Final implementation commands

```bash
./gradlew test --offline \
  --tests 'dev.libjadx.*Replay*' \
  --tests 'dev.libjadx.*Propagat*' \
  --tests 'dev.libjadx.*Hierarchy*' \
  --tests 'dev.libjadx.app.*Edit*' \
  --tests 'dev.libjadx.app.*Scoped*' \
  --tests dev.libjadx.app.OpenApiDocumentTest

./gradlew clean check --offline --rerun-tasks
./gradlew installDist --offline

JADX_GUI=/tmp/libjadx-rerun-jadx-1.5.6/bin/jadx-gui \
  ./gradlew guiRoundTripTest rawGuiRoundTripTest \
  nativeEditGuiRoundTripTest mappingExportGuiRoundTripTest \
  mappingImportGuiRoundTripTest scopedEditGuiRoundTripTest \
  relatedPropagationGuiRoundTripTest propagatedEditReplayGuiDiagnosticTest \
  safeReplayGuiDiagnosticTest --offline --rerun-tasks -x test

/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-edit-contract.py
/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-mapping-export-contract.py
/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-mapping-import-contract.py
/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-search-contract.py

git diff --check
git diff --check origin/main...HEAD
```

The GUI command excludes the ordinary test task only after clean check has
prepared current fixtures; all nine Save As/reverse tasks are rerun. No opt-in
skip is counted as a GUI pass. New files are checked for whitespace separately.

| Final gate | Result |
|---|---|
| Focused suite | BUILD SUCCESSFUL in 2m 24s; 25 classes, 108 tests: 105 passed, three opt-in GUI skips, zero failures/errors |
| Clean full regression | BUILD SUCCESSFUL in 6m 26s; 73 classes, 376 tests: 367 passed, nine opt-in GUI skips, zero failures/errors |
| Installed distribution | BUILD SUCCESSFUL in 2s; full check rebuilt the package and ran packaged process/restart tests |
| Actual matching GUI | BUILD SUCCESSFUL in 4m 29s; all nine dedicated tasks passed one test each, zero skips/failures/errors; 17 actual Save As runs |
| Edit contract | OpenAPI 3.1 valid; 11 examples, 10 live HTTP responses, four injected service results, 18 scoped responses and 14 propagation rejection responses valid |
| Mapping export contract | Three examples and 31 live HTTP responses valid |
| Mapping import contract | Four examples and 41 live HTTP responses valid |
| Search contract | Four examples and 33 live HTTP responses valid |
| Whitespace / patch identity | Working-tree and origin/main...HEAD checks passed; new files checked separately; final implementation fingerprint unchanged |

Counts are retained in `/tmp/libjadx-pr17-{focused,clean,gui}-final-counts.json`. Together with the nine dedicated GUI executions, all 376 distinct tests ran successfully.
Gradle logs are `/tmp/libjadx-pr17-{focused,clean,distribution,gui}-final.log`.
Reports are under `build/test-results/` and `build/reports/tests/`; clean removed
previous captures before the new live HTTP/native/packaged gates. No retained
old-head response is counted as fresh validation.

## Final-run performance observations

Times below are milliseconds; C/H means prior owners cold/generated. Each row
is one sample in the same warmed test JVM. Zero-item controls only copy data;
load and census are avoided. These are private candidate costs, excluding public
prevalidation, repository commit and HTTP transport.

| Raw classes | Prior state | Items | Copy | Extra census | Construct/load/bind |
|---:|---|---:|---:|---:|---:|
| 17 | C | 0 | 0.035 | 0 | 0 |
| 17 | C | 1 | 0.132 | 1.52 | 246.49 |
| 17 | C | 64 | 1.145 | 3.29 | 235.31 |
| 17 | H | 0 | 0.029 | 0 | 0 |
| 17 | H | 1 | 0.213 | 2.41 | 214.95 |
| 17 | H | 64 | 0.550 | 2.76 | 203.35 |
| 129 | C | 0 | 0.032 | 0 | 0 |
| 129 | C | 1 | 0.173 | 8.48 | 307.30 |
| 129 | C | 64 | 1.586 | 15.37 | 853.53 |
| 129 | H | 0 | 0.145 | 0 | 0 |
| 129 | H | 1 | 0.207 | 19.30 | 394.56 |
| 129 | H | 64 | 0.409 | 6.50 | 394.36 |
| 513 | C | 0 | 0.041 | 0 | 0 |
| 513 | C | 1 | 0.207 | 45.67 | 394.18 |
| 513 | C | 64 | 1.798 | 50.48 | 534.60 |
| 513 | H | 0 | 0.140 | 0 | 0 |
| 513 | H | 1 | 0.186 | 30.45 | 269.72 |
| 513 | H | 64 | 0.592 | 26.47 | 377.43 |

Maximum summed heap-pool peak samples for 17/129/513 classes were approximately
321.5/373.0/373.5 MiB. These include the old engine and test harness/previous
allocations, not incremental replacement memory or an RSS guarantee. Full rows
with before/after heap are in `build/safe-replay-probe/performance.json`.
The 64-record case requires one load, not 64 loads. No production latency or
heap admission limit is proposed on these small-JVM observations.


The new GUI diagnostic passed on `build/safe-replay-gui-diagnostic/diagnostic.jadx`
and `gui-resaved.jadx`; its copied GUI log is `actual-gui.log`. The reverse test
asserts original record `value()I -> intValueAlias`, unchanged comment and relative
`return-clash.jar` reference, and Java declaring `String value()` rather than the
hot service's `m0value`. Unknown root loss is an explicit matching-GUI observation;
headless nested-node/root preservation is independently asserted before GUI save.
This closes the diagnostic gate, not the failed generic adoption gate.

## Persistence, state and next milestone

The new ordinary service diagnostic preserves the untouched automatic alias in
memory, has one logical/index increment and leaves native/input bytes unchanged
before explicit save. Headless save preserves nested unknown JSON; fresh native
and GUI Save As reopening recompute that alias. Existing actual GUI gates retain
relative mappings, scoped/declaration edits and known unknown-field loss. Nothing
is autosaved and no persisted project sidecar/cache/journal is introduced.

No safe replacement publication is installed. Existing native replay's post-commit
FAILED lifecycle and prefix/fault receipts remain; no rollback, verifier rebinding,
successful replacement fault matrix or new stale-token guarantee is claimed.
Those implementation/admission gates depend on resolving the preservation rule.

The smallest next decision is whether to allow fresh recomputation of automatic
aliases on unedited declarations when an edit removes a collision. Measured cost
is not the blocker. Approval would unblock implementing and proving the generic
replacement primitive, before actual related-group admission. Otherwise another
bounded strategy is needed. No constraint relaxation, upgrade probe, bridge
exclusion or Phase 6 scope decision is made silently.

## PR-description checklist

1. Current replay, listener-first, exact-owner invalidation and fresh replacement were tested; no extra ordering was proposed.
2. The old hot CovariantLeaf value -> m0value failure remains reproducible on the actual default replay method and unchanged PR #16 service tests.
3. The oracle is a separately loaded engine with identical ordered inputs, mapping, settings and a complete deep native snapshot, compared through immutable raw/source/metadata/search/reference data.
4. No generic strategy preserves every nonmember AND matches fresh loading. Replacement fixes Joined bridges but fails the return-only nonmember.
5. Owned complete native class/method/field/LINE/parameter/VAR/mapping reconstruction passes; this does not override item 4.
6. Joined replacement agrees cold and after generation, for every first record. Collision alias recomputation fails preservation cold and hot.
7. No replacement is published; candidate load/publication failures remain future acceptance work. Production replay failure still fails the runtime after repository commit.
8. Existing repository code data becomes authoritative before in-place replay. That ordering was not changed under Outcome B.
9. No new repository/engine consistency guarantee is claimed; the existing FAILED path covers replay exceptions after commit.
10. Ordinary effective batches retain one native commit/logical/index increment; no-op behavior is unchanged. No replacement publication occurs.
11. Existing source/search invalidation semantics are unchanged and renewed by regressions.
12. Owned-size measurements are recorded below; they are not large-project latency or heap guarantees.
13. Actual GUI commands/results are recorded below; the new task diagnoses automatic alias recomputation after Save As, not safe service replacement.
14. Every propagateRelated presence remains rejected with 422 UNSUPPORTED_CAPABILITY.
15. edit.related_propagation remains UNSUPPORTED with its existing evidence string.
16. Before PR #18 group admission, resolve automatic-alias preservation and prove generic publication/fault/lease/service-GUI gates, then same-lease COMPLETE verification, collision planning, private group staging and exact affectedRefs.
