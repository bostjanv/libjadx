# PR #16 review: verified native replay negative evidence

**Acceptance outcome B.** The independent original family is COMPLETE and
explicit records rename/persist every requested member, but hot service replay
also changes unrelated bridge aliases. Fresh native reopen restores different
aliases. The required nonmember and hot/fresh gate fails, so all explicit
propagation remains UNSUPPORTED with existing presence-based 422 rejection.

Requested base and checkout HEAD at validation:
`4c4805ec30de6114e477dad1c7ab0b35fad60f36`. Results cover the implementation
snapshot on that base, identified by the fingerprint below. Committing and
publishing the review branch does not change that tested implementation.
No remote CI result is asserted here.
Implementation/fixture fingerprint:
`sha256:2744012f22072bf6f1aa23c0cd7af14d76691c143e915930ef3efeb1aa94db32`.
Its sorted path/content manifest is `/tmp/libjadx-pr16-implementation-hash.json`;
it includes build registration, both edit test classes, the validator and the
three retained owned Java fixture sources. Documentation is excluded and
checked separately after recording results.

## Implemented and contract state

- Added `PropagatedNativeReplayTest`: seven independently COMPLETE family shapes,
  cold/hot real service batches, read-only controls, exact original native records,
  every requested member's effective alias/emitted declaration token, every raw
  nonmember alias, different-seed record idempotence, explicit save/fresh reopen.
- Added eight service-produced Joined fixtures (each original seed first, cold
  and hot) and separate `propagatedEditReplayGuiDiagnosticTest` for actual matching
  GUI Save As and fresh original-key reopen. It tests the failure, not accepted
  `propagateRelated` admission.
- Extended clean/dirty HTTP rejection captures and the independent edit validator
  to include numeric, array and object flags alongside true/false/null/string.
  Fourteen fresh captured responses retain indexed 422 rejection and no staging.
- Updated the required documentation and ADR with the replay matrix and smallest
  follow-up. No production Java, DTO, OpenAPI, accepted examples, dependency lock,
  native format or mapping import/export behavior changed.

The accepted prototype was withdrawn when the nonmember gate failed. No immutable
group plan or exclusive-verification addition is shipped; no group atomicity,
affectedRefs success receipt, mapping-group support or new mutation endpoint is
advertised. Legacy per-declaration receipts retain empty affectedRefs. Existing
legacy prefix/fault, revisions, source/scoped, external-change and lifecycle
regressions are renewed, rather than claimed as new propagated transaction tests.

## Pinned source and negative evidence

Jadx **1.5.6**, source **28ff15e4ae69950aebea110a13e5ab895d234dfc**; JDK
**21.0.12.1**, Gradle **8.14.3**, Linux; actual GUI executable
`/tmp/libjadx-rerun-jadx-1.5.6/bin/jadx-gui` reports **1.5.6**. Eleven source
files under `/tmp/libjadx-pr13-source` were byte-compared with fresh downloads
from that exact commit. `/tmp/libjadx-pr16-source-audit.json` records all hashes,
including the eight mandated paths and ClassNode/JadxDecompiler/AttributeStorage.
[Pinned links and source reasoning](phase-5-propagated-edits.md) identify the
user-rename listener, global collision pass, unload state and implicit related
alias application. No source from master or libghidra was used.

Owned fixtures: `RelatedFixture` compiles `tests/fixtures/related/Hierarchy.java`
into two JARs; `HierarchyFixture.visibility` compiles the existing p/Visibility
and q/Other sources; owned smali assembles a direct-DEX interface/implementation.
The retained Joined family is ExtendedLeft/Joined/SeparateLeft/SeparateRight,
with full original `joined(I)I` keys. No incomplete Jadx candidate group supplies
membership authority.

The failing original nonmembers are CovariantLeaf `value()Ljava/lang/Object;`
and `value()Ljava/lang/String;`. A generated read-only control keeps `value`.
The generated primary engine's native replay changes both aliases to `m0value`,
and emitted Java includes `public String m0value()`. Explicit save contains
exactly the family records; a fresh reopen has both aliases `value` and emits
`public String value()`. Cold legacy staging is a separate agreeing control.
The same hot failure appears for the chain, shared-root diamond, default and
four-member Joined families. Inherited/simple/DEX controls agree; no public
subset is enabled on those observations.

During investigation, a provisional unchanged-nonmember assertion failed for
four of seven warmed replay cases; its original XML is preserved at
`/tmp/libjadx-pr16-stabilized-replay-failure.xml`. Earlier cold comparisons also
caught generic codegen's `generic2` → `generic` change; the final read-only
control distinguishes that from mutation replay. Initial test fixture errors
(native getter name, missing runtime input paths, unavailable nonmember source
position) were corrected. None of the provisional API/GUI passes or initial
failed commands counts as final accepted propagation proof. The final tests
explicitly assert the observed discrepancy, so a passing diagnostic remains
negative feasibility evidence.

## Final commands and outcomes

All commands below are author-run. The Python validators use an independent
JSON Schema/OpenAPI implementation; there was no separate reviewer rerun or
remote CI/status proof. Exact versions were checked with `java -version`,
`./gradlew --version`, the GUI's `--version`, and the validator Python's
`--version` (Python **3.14.4**).

```bash
./gradlew test --offline \
  --tests 'dev.libjadx.*Propagat*' \
  --tests 'dev.libjadx.*Hierarchy*' \
  --tests 'dev.libjadx.*Related*' \
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
  --offline --rerun-tasks -x test

/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-edit-contract.py
/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-mapping-export-contract.py
/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-mapping-import-contract.py
/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-search-contract.py

git diff --check
git diff --check origin/main...HEAD
```

| Final gate | Outcome |
|---|---|
| Focused suite | BUILD SUCCESSFUL in 1m 52s; 21 classes, 102 tests: 100 passed, two opt-in GUI skips, zero failures/errors |
| Clean full check | BUILD SUCCESSFUL in 5m 44s; 71 classes, 368 tests: 360 passed, eight opt-in GUI skips, zero failures/errors |
| Installed standalone distribution | BUILD SUCCESSFUL in 1s; clean full outputs were up to date; packaged-process regressions passed in full check |
| Actual matching GUI tasks | BUILD SUCCESSFUL in 3m 49s; all eight dedicated tasks executed one passing test, zero skips/failures/errors; 16 actual Save As runs total, including eight new cold/hot seed fixtures |
| Edit validator | OpenAPI 3.1 valid; 11 examples, 10 live HTTP responses, four injected legacy service results, 18 scoped HTTP responses and 14 propagation rejection captures valid |
| Mapping export validator | Three examples and 31 live HTTP responses valid |
| Mapping import validator | Four examples and 41 live HTTP responses valid |
| Search validator | Four examples and 33 live HTTP responses valid |
| Whitespace and final fingerprint | Working-tree and origin/main...HEAD checks passed; all three new files checked separately; implementation manifest still matches |

The GUI command deliberately excludes the ordinary test task after the clean
full suite has prepared fresh fixtures. Every GUI Save As task and dedicated
reverse test is rerun; ordinary opt-in skips do not count as GUI passes. The new
negative diagnostic must be distinguished from a hypothetical accepted
`propagatedEditGuiRoundTripTest`, which is not implemented/enabled under outcome B.

Reports and captures are under `build/test-results/`, `build/reports/tests/`,
`build/{edit,related,scoped-edit}-contract-responses/` and the existing mapping/
search capture directories. New per-family evidence and actual GUI project/log
paths are listed in [the replay artifact inventory](phase-5-propagated-edits.md).
Focused XML counts are in `/tmp/libjadx-pr16-focused-final-counts.json`; full
counts are in `/tmp/libjadx-pr16-clean-final-counts.json`; each dedicated GUI
task's counts are in `/tmp/libjadx-pr16-gui-final-counts.json`. Terminal logs are
`/tmp/libjadx-pr16-{focused,clean,distribution,gui}-final.log`.

## Persistence and next slice

The diagnostic checks that legacy batches do not autosave, exactly one logical
revision is published for the initial member-record batch, and explicit native
save/fresh reopen persists every original family key. Existing native/scoped/
mapping GUI gates renew comment/parameter/unknown-field preservation evidence;
known GUI unknown-field loss remains an upstream limitation. GUI Save As does
not invoke or establish compatibility with GUI rename-dialog canonicalization.
No pending edits are exported to a persistent LibJadx sidecar or journal.

The smallest follow-up is safe engine-replay feasibility for generated owners,
including unchanged nonmembers, before any group admission/controller is shipped.
A proved immutable-state engine rebuild or replay ordering could be investigated
without upgrading Jadx or inventing unrelated native edits. If that gate passes,
all single-lease, collision, immutable-plan, bounded-family, private staging,
truthful prefix-fault, live HTTP and accepted-service GUI gates still apply.
Local-variable editing and the Phase 6 exit decision remain separate. This PR
makes no architecture change and requires no additional approval to retain the
explicitly mandated unsupported outcome.
