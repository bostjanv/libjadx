# PR #13 review record

The original-head evidence below predates the P2 catch-declaration fix. Its
implementation fingerprint applies to that original patch only; the follow-up
validation is recorded separately at the end of this file.

## Change and revision

Base: `10d10048eb8254b77201a0ced9db845646eeebe9` (merged PR #12).
The implementation is published from `phase5-scoped-parameter-renames`;
the GitHub PR description records the exact final head SHA. Validation below
applies to the final implementation patch on this base. Publication changes
only this evidence text; the validated implementation remains identical.
The implementation fingerprint is
`sha256:2533f9a8f6437d7259f19b1e27fd3150143f398aee4652c1245379f95435cbf4`,
computed from 32 changed implementation/build/contract/test/fixture files in
sorted path order, hashing each UTF-8 path and file bytes with separate unsigned
8-byte big-endian length prefixes. Documentation files are excluded.

Supported subset: snapshot-bound native parameter renames for verified plain
AUTO/RESTRUCTURE concrete ordinary method signatures. Indexes exclude `this`
and count wide arguments once. Session/revision/source preconditions are required.
Local renames, annotated/bodyless/transformed/special/synthetic/bridge and
unproved generic forms, exact input provenance and related-method propagation
remain unsupported. Conservative method-wide name collision checks protect
both complete batches and staged prefixes. Phase 5.2 remains incomplete.

The implementation, identity lifetime, pinned source links and fixture limits
are detailed in [scoped editing evidence](phase-5-scoped-editing.md). No dependency,
lock, native format, mapping operation or listening policy was expanded.
Python SDK generation is explicitly outside this PR.

## Correctness failures and fixes

- The first raw probe tried to access method argument registers after normal
  Jadx source generation; pinned `ProcessClass` unloads those registers. Probes
  use the upstream test/debug retain flag to inspect construction, while the
  production adapter reads exact emitted definitions without retaining ASTs.
- Initial service positives rejected default AUTO because the implementation
  checked only RESTRUCTURE. Source/signature validation now accepts structured
  AUTO output as well. The same positive tests passed after correction.
- The nested native JSON regression failed before the codec fix at the assertion
  retaining `futureRename`: unknown members inside node/code refs defeated raw
  JSON key matching. Native typed identity matching plus nested unknown-member
  merging fixed it. The regression-only command
  `./gradlew test --offline --tests 'dev.libjadx.app.ScopedParameterServiceTest.scopedNativeEntryAndNestedUnknownFieldsSurviveExplicitSave'`
  failed one test before the fix. The exact post-fix command
  `./gradlew test --offline --tests 'dev.libjadx.app.ScopedParameterServiceTest.scopedNativeEntryAndNestedUnknownFieldsSurviveExplicitSave' --tests 'dev.libjadx.probes.NativeProjectDocumentTest'`
  then passed three tests, zero failures/errors/skips, in 12s.
- The HTTP search assertion initially expected `STALE_CURSOR`; the existing
  reviewed contract uses `STALE_REVISION`. The assertion was corrected to that
  existing vocabulary; no search contract was changed.

## Final validation commands

All acceptance commands run on the final implementation. Only this record and
other evidence text may be updated after the final test run.

```bash
./gradlew test --offline --tests 'dev.libjadx.*Scoped*' --tests 'dev.libjadx.*Variable*' --tests 'dev.libjadx.app.*Edit*' --tests dev.libjadx.app.OpenApiDocumentTest
./gradlew clean check --offline --rerun-tasks
JADX_GUI=/tmp/libjadx-rerun-jadx-1.5.6/bin/jadx-gui ./gradlew guiRoundTripTest rawGuiRoundTripTest nativeEditGuiRoundTripTest mappingExportGuiRoundTripTest mappingImportGuiRoundTripTest scopedEditGuiRoundTripTest --offline --rerun-tasks
./gradlew installDist --offline
PATH=/tmp/libjadx-rerun-contract-venv/bin:$PATH python tests/validate-edit-contract.py
PATH=/tmp/libjadx-rerun-contract-venv/bin:$PATH python tests/validate-mapping-export-contract.py
PATH=/tmp/libjadx-rerun-contract-venv/bin:$PATH python tests/validate-mapping-import-contract.py
PATH=/tmp/libjadx-rerun-contract-venv/bin:$PATH python tests/validate-search-contract.py
git diff --check origin/main...HEAD
git diff --check
```

The final focused run passed in 1m 14s: 17 classes, 60 tests discovered, 59 passed,
zero failures/errors and one opt-in scoped GUI skip. That skip is not counted
as GUI proof. The renewed clean suite passed in 4m 57s: 62 classes, 298 tests discovered,
292 passed, zero failures/errors and six opt-in GUI skips. The final GUI command
passed in 6m 24s, rerunning that ordinary suite and executing all six dedicated
GUI tasks: one test each, zero failures/errors/skips. All 298 distinct tests ran
successfully across the ordinary and dedicated tasks; no GUI skip is counted
as a pass.

`installDist --offline` passed in 7s with four tasks up-to-date after the GUI
command rebuilt the distribution. All four independent validators passed:

| Validator | Reviewed examples | Fresh HTTP captures | Additional service results |
|---|---:|---:|---:|
| Editing | 10 | 10 legacy + 16 scoped | 4 injected staging results |
| Mapping export | 3 | 31 | — |
| Mapping import | 4 | 41 | — |
| Search | 4 | 66 | — |

The 16 scoped captures cover 200/400/404/409/415/422/429/500/503, including
separate stale-revision, stale-snapshot, unsupported-local/parameter and
`422 INVALID_ENTITY_ID` duplicate-native-key cases. The validator also checks
that removing both revision preconditions from the reviewed scoped request is
invalid under the conditional OpenAPI schema.

`git diff --check origin/main...HEAD` and `git diff --check` passed. New files
also passed `git diff --no-index --check /dev/null <path>` checks. The final
implementation fingerprint was recomputed after validation and remained equal
to the value above. No implementation, contract, fixture or test changed after
these final gates; only evidence text was completed.

## Environment and artifacts

Linux, OpenJDK `21.0.12.1`, Gradle 8.14.3, Jadx 1.5.6. Matching GUI archive
SHA-256: `545ea2be9c242511bc145755cf4bda2485ade42966e096f8b4d3da2a230e8974`.
`/tmp/libjadx-rerun-jadx-1.5.6/bin/jadx --version` returned `1.5.6`.
Xvfb/xdotool drive actual GUI Save As. Python is 3.14.4 with the previously
pinned contract-validation packages in `tests/requirements-contract.txt`.
No Windows/macOS, DEX-variable or remote CI result is claimed.

Reports: `build/test-results/` and `build/reports/tests/`. Fresh HTTP captures:
`build/scoped-edit-contract-responses/`, alongside the existing edit/mapping/search
capture directories. Native GUI artifacts: `build/scoped-edit-gui-fixture/`,
including `scoped.jadx`, `gui-resaved.jadx`, `variables.jar`, `mapping.tiny`.
The GUI reverse check verifies every persisted MTH_ARG rename's exact emitted
parameter token by original full method key, plus mapped aliases, declaration
comments and original paths. The headless codec retains unknown native fields;
matching GUI unknown-field loss is the already documented upstream limitation.

Explicit-save evidence includes unchanged native bytes while dirty, native
save/headless reopen, three fresh installed-service JVMs proving save/restart,
unsaved discard and stale snapshot/session rejection, native/mapping external
conflicts and dirty declaration retention. Both JVM wide/static/instance and
overloaded parameter forms are exercised.

Next milestone: a separate pinned-Jadx completeness and GUI-persistence probe
for Phase 5.2 related-method/override propagation. Local persistence remains
an open gate; no architectural approval is required for this allowed
parameter-only implementation.

## P2 catch-declaration follow-up (2026-09-28)

Addresses [review 5343016889](https://github.com/bostjanv/libjadx/pull/13#pullrequestreview-5343016889)
against original head `eea3a1006fdd41c41836e82b42b8d32cba8cd134`.
The original implementation fingerprint above does not apply to this follow-up.

`JadxVariableAdapter` now checks every catch header inside the verified emitted
method range before advertising parameter persistence. The bounded lexical check
in `MethodRangeVerifier` requires an exact same-method VarNode declaration token,
skips comments/quoted literals/text blocks, and fails closed for missing metadata
or unfamiliar syntax. An affected method's parameters are `UNSUPPORTED` with
null indexes; edit batches return `422 UNSUPPORTED_CAPABILITY` before staging.
Verified catch declarations remain editable, with existing collision rejection.
OpenAPI descriptions, README, compatibility notes and the changelog document this
boundary. No schema shape, dependency, native format or persistence code changed.
Python SDK generation remains Phase 6.

Pinned source evidence is `BlockExceptionHandler.fixMoveExceptionInsn` and
`RegionGen.makeCatchBlock` at Jadx commit
`28ff15e4ae69950aebea110a13e5ab895d234dfc`, inspected under
`/tmp/libjadx-pr13-source/jadx-core/src/main/java/jadx/core/` and linked in
[scoped editing evidence](phase-5-scoped-editing.md). The owned
`tests/fixtures/variables/UnusedCatch.smali` discards the handler exception
without a move-exception instruction. Raw AUTO and RESTRUCTURE probes show that
`unused` has no variable metadata and that renaming the parameter to `unused`
silently changes the catch name to `unused2`. An initial unoptimized JVM trial
instead emitted an annotated `e`; the DEX fixture exercises the actual gap.

Before the fix, this focused command passed both raw probes and failed the service
regression because preflight reached staging rather than rejecting the batch:

```bash
./gradlew test --offline --tests 'dev.libjadx.app.JadxVariableProbeTest.unusedCatchNameHasNoVarNodeAndNativeParameterRenameChangesIt' --tests 'dev.libjadx.app.ScopedParameterServiceTest.unannotatedCatchDeclarationRejectsEntireBatchWithoutChangingState'
```

The final implementation passed these gates:

```bash
./gradlew test --offline --tests 'dev.libjadx.*Scoped*' --tests 'dev.libjadx.*Variable*' --tests 'dev.libjadx.app.SourceCoordinatesTest' --tests 'dev.libjadx.app.*Edit*' --tests dev.libjadx.app.OpenApiDocumentTest
./gradlew clean check --offline --rerun-tasks
JADX_GUI=/tmp/libjadx-rerun-jadx-1.5.6/bin/jadx-gui ./gradlew guiRoundTripTest rawGuiRoundTripTest nativeEditGuiRoundTripTest mappingExportGuiRoundTripTest mappingImportGuiRoundTripTest scopedEditGuiRoundTripTest --offline
/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-edit-contract.py
/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-mapping-export-contract.py
/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-mapping-import-contract.py
/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-search-contract.py
git diff --check
git diff --no-index --check /dev/null tests/fixtures/variables/UnusedCatch.smali
```

The focused run passed in 1m 15s: 18 classes, 68 tests, 67 passed and one opt-in
GUI skip. Clean check passed in 4m 47s: 62 classes, 303 tests, 297 passed and six
opt-in GUI skips. Both had zero failures/errors. The GUI command passed in
1m 35s: all six dedicated tasks ran one test each, zero failures/errors/skips.
The ordinary suite was up-to-date from the preceding clean run. All 303 distinct
tests passed across the ordinary and dedicated tasks; skips are not GUI evidence.

All independent validators passed against fresh captures: editing validated 10
examples, 10 legacy HTTP responses, 18 scoped responses and four injected service
results; export validated three examples/31 responses; import four examples/41
responses; search four examples/33 responses. The two added scoped captures
verify unsupported catch-method parameter metadata and the structured rejection.
An earlier edit-validator invocation ran before the capture task finished and
failed its expected-count assertion; the final invocation ran after clean check.
`git diff --check` returned zero. The new-fixture `--no-index --check` emitted no
whitespace diagnostics; its exit code was one because the file adds content.

Persistence evidence: the mixed-batch regression checks zero staging calls,
unchanged logical/index/project state, pending edits, native file bytes, emitted
source and unrelated-owner state. `Variables.java` adds a used catch declaration
and catch-like string text; their supported parameters pass successful renames,
and the actual scoped GUI resave/headless reopen gate covers them. Existing save,
restart/discard, external-conflict, concurrency/cancellation and native round-trip
tests all passed. Only evidence documentation changed after the final gates.
The environment remains Linux, JDK 21.0.12.1, Gradle 8.14.3, Jadx 1.5.6 and
Python 3.14.4. General DEX variable editability, Windows/macOS, local editing and
related-method propagation remain unverified or unsupported as previously stated.

Next unblocked milestone remains the separate Phase 5.2 related-method/override
propagation probe. This correction requires no architecture change or human decision.
