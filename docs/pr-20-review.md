# PR #20 review — native local-variable rename retargeting feasibility

Target base: merged PR #19, `e26836d76b84750a8039944cb38383e2e88f5f7c`.
Jadx stays **1.5.6**, source **28ff15e4ae69950aebea110a13e5ab895d234dfc**,
Gradle **8.14.3**, JDK **21**. This records local implementation validation,
not remote CI or an independent human review.

## Implemented and decision

**Outcome B.** Local mutation remains UNSUPPORTED. The only production change
is capability evidence `NATIVE_VAR_RETARGETS_WITH_UNBOUND_GUI_SETTINGS`, with
persistence UNAVAILABLE. No request/response schema, operation, local edit ID,
controller, production verifier, SDK, mapping extension, dependency or lock
changes. The HTTP regression checks the published capability and 422 rejection
of an entire mixed batch without staging its otherwise valid declaration edit.

Owned JVM/direct-DEX fixtures and immutable test diagnostics exercise native
consumption, source declarations, four modes, settings, repeated/unloaded/fresh
generation, complete-state replacement, temporary analysis, explicit native
save/reopen, scoped ambiguity and encoding. Actual matching-GUI editor capture
and Save As use five explicit global configurations. The retained merged-SSA,
unannotated-catch, scoped stale-snapshot and external-change tests are unchanged.

The smallest counterexample is `LocalShapes.simpleRetarget(I)I`, native `(0,2)`:
the original loader applies the rename to `Math.abs(input + 43)`; enabling normal
GUI Use dx/d8 applies the identical record to its **square**. The current emitted
target has exact metadata, one SSA definition, a unique key, no phi and no
register reuse in the final tree. Matching GUI preserves the native record,
but global loader settings are absent from `.jadx`. A source snapshot cannot
bind the record's future meaning. See [complete evidence and pinned source
links](phase-5-local-rename-feasibility.md) and [ADR 0001](adr/0001-defer-advanced-native-edits.md).

Positive same-mode replay and some tested stable keys are retained. The decision
is **no safe product subset proved**, not impossibility of every conceivable
DEX-only subset. A four-mode verifier alone misses the loader counterexample;
cross-loader original-local provenance is not proved. No heuristic is shipped.
Phase 5.2 closes with local editing deliberately unsupported; Phase 6 SDK work
is next. No architecture decision or permission is required by this outcome.

## PR-description checklist

1. Native persistence is exact original method `JadxNodeRef` plus packed
   `JadxCodeRef(VAR, reg << 16 | ssa)` and newName; there is no settings binding.
2. Pinned `CodeRenameVisitor` decodes and finds the first matching MethodNode SSA
   variable, then names its CodeVar. The read-only test observer measures that
   actual consumption before shrink/codegen.
3. Equal integer keys do not establish semantic sameness. The loader
   counterexample proves different computations with the identical key.
4. The original merged fixture still asserts RESTRUCTURE `(5,1)` versus SIMPLE
   `(5,0)` for the corresponding declaration. Its test is neither deleted nor weakened.
5. FALLBACK runs no scoped native rename consumer and emits no VarNode declarations
   in these fixtures. The source-control comparison verifies inertness separately.
6. Added straight-line, sequential reuse, branch/phi/loop, nested, generated
   pressure, used catch, switch/finally, wide, generic/object and bridge/bodyless shapes.
7. `LocalShapes.java` compiles with `-g:none` to JVM JAR; `LocalDex.smali` assembles
   to direct DEX. DX conversion of the JVM is a separate loader-setting probe.
8. All originating locals replay on cached/repeated generation, unrelated JVM/DEX
   owner generation and explicit unload/reprocess. Unload loses the JVM generic
   signature: the test reports raw List and checks local intent rather than
   claiming full-source equality for that state.
9. Native test-seam injection followed by an ordinary supported comment exercises
   real PR #18 publication. Complete native state and full source/declaration/SSA
   snapshots, including the separate unrelated owner, match an independent fresh
   engine. No public local mutation is used.
10. Explicit native save and reopen preserve every pending fixture local record
    and reproduce the fresh source/diagnostic oracle; dirty state never writes early.
11. Packaged local-edit restart is Outcome A only and is not claimed. Existing
    supported-edit restart/discard tests remain in the full suite.
12. AUTO/default agrees with originating intent for every candidate.
13. RESTRUCTURE/default agrees with originating intent for every candidate.
14. SIMPLE has stable, changed-representative, inert and unverifiable cases; DX
    has proved retargetings. Changed diagnostic roles alone are not called retargets.
15. FALLBACK is measured as inert where no consumer or sentinel exists and source
    equals that settings' unedited control. GUI preserves the ignored records.
16. No local-to-parameter retarget was observed or claimed. The native consumer
    lacks a nonargument check; actual parameter VAR and mixed MTH_ARG/VAR states
    are tested and never translated to positional identities.
17. Yes: simple `(0,2)`, pressure `(0,2)` and wide `(0,3)` retarget under dx/d8;
    owned expression/consumer oracles distinguish their computations.
18. An unmatched record can remain unchanged and ignored. Tests compare source
    against the unedited mode control; GUI resave and switching back verify retention.
19. Matching GUI preserves all seven standard records in the diagnostic project.
    Its editor and reverse reopen under dx/d8 show retargeting, rather than record rewriting.
20. Actual GUI 1.5.6 runs explicit AUTO, RESTRUCTURE, SIMPLE, FALLBACK and AUTO+dx/d8
    global configs, MEMORY cache and two threads, with editor text and screenshots.
21. Neither decompilation mode nor useDx is a ProjectData field. They are global
    JadxSettingsData/CLI-derived settings, not a native binding on VAR.
22. No deterministic native-safe product admission predicate is proved.
23. No predicate is implemented. Exact, unique, one-definition, nonmerged/no-phi
    observations fail on the smallest owned counterexample under loader settings.
24. No semantic authorization is issued. Test-only fixed-loader definition/use
    diagnostics and owned semantic oracles are evidence, not persistence identity.
25. No public local target shape or editable local ID is added.
26. Existing snapshot-bound parameter admission remains mandatory. A local source
    snapshot alone cannot authorize settings-independent native persistence.
27. No local collision policy ships. Existing scoped catch/name collision tests remain.
28. No dynamic verifier or local batch budget is adopted; its performance gates
    are inapplicable to Outcome B. Private diagnostic engines are closed.
29. No local no-op rule ships. Existing explicit-record/no-op semantics are retained.
30. Tiny import/export retains its existing scoped-code-ref restrictions; no VAR extension.
31. No local/propagated batch is admitted. Existing PR #19 restrictions remain green.
32. Smallest executable counterexample: one-local `simpleRetarget(I)I`, `(0,2)`,
    `Math.abs(input + 43)` becomes the square of that result under GUI dx/d8.
33. Yes: under the approved persistence/settings boundary this closes the final
    Phase 5.2 feasibility decision with a deliberate unsupported capability.
34. Final evidence is `NATIVE_VAR_RETARGETS_WITH_UNBOUND_GUI_SETTINGS` (UNSUPPORTED,
    persistence UNAVAILABLE).
35. Exact final-patch commands, counts and artifacts are recorded below.

## Final-patch validation

Environment: Linux, OpenJDK **21.0.12.1**, Python **3.14.4**; matching GUI executable
`/tmp/libjadx-rerun-jadx-1.5.6/bin/jadx-gui`. These commands use
`GRADLE_USER_HOME=/tmp/libjadx-review-gradle` and the existing Python validation
venv `/tmp/libjadx-rerun-contract-venv`.

The ten implementation files are held unchanged through validation. Their SHA-256
manifest is `/tmp/pr20-final-implementation-manifest.json`, fingerprint
`15a09f24b0e6b00463ee2d392677cccd798a601bdc9757ee36d3864d5d5b9737`.
This includes build wiring, the capability change, five Java diagnostic/test
files, two owned fixture sources and the GUI harness. Documentation is updated
separately to record outcomes. Existing negative-control test/fixture files have
no diff against the target base.

```bash
export GRADLE_USER_HOME=/tmp/libjadx-review-gradle
./gradlew test --offline \
  --tests dev.libjadx.app.JadxVariableProbeTest \
  --tests 'dev.libjadx.*Local*' \
  --tests 'dev.libjadx.*Scoped*'
./gradlew clean check --offline --rerun-tasks
./gradlew installDist --offline
```

- Focused: BUILD SUCCESSFUL in **2m 29s**, **48 tests**, zero failures/errors,
  **six opt-in skips**. Log `/tmp/pr20-focused-final.log`.
- Clean check: BUILD SUCCESSFUL in **10m**, **485 tests**, zero failures/errors,
  **16 opt-in skips** (11 retained GUI reverse cases and five new configurations).
  Log `/tmp/pr20-clean-final.log`; XML/HTML under `build/test-results/test` and
  `build/reports/tests/test`. The dedicated run below executes these GUI cases.
- installDist: BUILD SUCCESSFUL in **4s**; packaging was already built by clean
  check's installed-process regressions. Log `/tmp/pr20-install-final.log`.

The regenerated matrix has **23 candidates** (16 JVM, seven direct DEX) and
**340 rows**: 193 SAME_LOCAL, 118 ABSENT_INERT, 20 UNVERIFIABLE, nine
DIFFERENT_LOCAL. No PARAMETER or AMBIGUOUS retarget is inferred where none was
observed. The nine DIFFERENT_LOCAL rows are three owned keys in three non-FALLBACK
DX modes. JSON includes original source/declaration/SSA snapshots and actual
native-consumer diagnostics at `build/local-rename-probe/mode-matrix.json`.

```bash
JADX_GUI=/tmp/libjadx-rerun-jadx-1.5.6/bin/jadx-gui ./gradlew \
  guiRoundTripTest rawGuiRoundTripTest nativeEditGuiRoundTripTest \
  mappingExportGuiRoundTripTest mappingImportGuiRoundTripTest \
  scopedEditGuiRoundTripTest relatedPropagationGuiRoundTripTest \
  propagatedEditReplayGuiDiagnosticTest safeReplayGuiDiagnosticTest \
  replacementEditGuiRoundTripTest propagatedEditGuiRoundTripTest \
  localRenameGuiDiagnosticTest --offline --rerun-tasks -x test
```

Final dedicated run: BUILD SUCCESSFUL in **6m 17s**, all **16 tests** across
**12 tasks**, no failures, errors or skips, with **27 actual GUI Save As operations**.
Logs: `/tmp/pr20-gui-final.log`; XML/HTML under each dedicated task's
`build/test-results/` and `build/reports/tests/` directory. The local diagnostic's
five configs, actual GUI logs, clipboard editor text, screenshots and resaved
projects are under `build/local-rename-gui-diagnostic/<scenario>/`.
Native records are compared in full and reverse source is checked under both
the generating settings and restored default AUTO. The native diagnostic
project remains ordinary Jadx data with no mode/loader binding.

`-x test` retains the
fresh fixtures produced by the full clean suite and avoids rerunning its normal
opt-in cases; all dedicated GUI Exec/Test tasks themselves rerun. The local
task is explicitly a negative diagnostic, not a positive local-edit gate.

```bash
/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-edit-contract.py
/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-mapping-export-contract.py
/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-mapping-import-contract.py
/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-search-contract.py
```

All four validators exit **0** and validate OpenAPI 3.1. Results:

| Validator | Reviewed examples / fresh captures |
|---|---|
| Edit | 16 examples, 20 declaration HTTP, four injected service results; 18 scoped HTTP; 36 related-propagation HTTP with exact family/no-op receipts |
| Mapping export | Three examples, 31 HTTP responses |
| Mapping import | Four examples, 41 HTTP responses |
| Search | Four examples, 33 HTTP responses |

Logs: `/tmp/pr20-edit-contract-final.log`,
`/tmp/pr20-mapping-export-contract-final.log`,
`/tmp/pr20-mapping-import-contract-final.log`, `/tmp/pr20-search-contract-final.log`.
Fresh HTTP capture artifacts remain in their `build/*contract-responses/` folders.

```bash
bash -n tests/local-rename-gui-diagnostic.sh
git diff --check
git diff --check origin/main...HEAD
```

All exit **0**. Additional checks pass for relative links in touched documentation,
whitespace in new files, the 28-file pinned source audit and the unchanged final
implementation manifest. At validation time HEAD was the target merge base;
`git diff --check` checked the tracked working patch and the separate new-file
check covered untracked additions. Normal opt-in skips are never counted as GUI passes.

## State and next milestone

Every local remains read-only and UNSUPPORTED, including potentially stable
fixture candidates. Synthetic/bridge/bodyless, duplicate scoped records,
malformed encoding, unannotated catches and stale/external state do not acquire
an edit path. Existing declaration/mapping/scoped/group operations and their
publication/error semantics remain intact.

Proceed to Phase 6 generated Python transport and handwritten synchronous,
asynchronous and object convenience layers. Local editing is an explicit pinned
limitation; it is not an outstanding prerequisite for that milestone.
