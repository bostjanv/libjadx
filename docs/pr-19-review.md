# PR #19 review — verified related-method rename group admission

Target base is merged PR #18, `5f9ae2ea1d54a9cbe253f609c951023a03d253de`.
Jadx remains **1.5.6**, source **28ff15e4ae69950aebea110a13e5ab895d234dfc**;
Gradle **8.14.3**, JDK **21**. This review records the implementation and local
validation, not a remote CI run or an independently reviewed/merged PR.

## Implemented

The existing `/edits/batch` RENAME contract accepts optional boolean
`propagateRelated`, default false. True admits only independently COMPLETE
closed-input METHOD families with current session/revision preconditions.
Verification runs in the same exclusive edit callback, using its captured engine.
Immutable copied native plans, raw all-owner collisions, full-batch overlap checks,
private group staging and exact sorted receipts feed the unchanged PR #18 fresh
replacement publication path. Standard native records persist only on explicit
save. Capability `edit.related_propagation` is PARTIAL with evidence
`INDEPENDENT_CLOSED_INPUT_FAMILY_GUI_VERIFIED` and persistence
`MEMORY_ONLY_UNTIL_EXPLICIT_NATIVE_SAVE`. PARTIAL denotes the conservative subset;
it never denotes a partially admitted or truncated family.

OpenAPI, examples, typed DTOs, live HTTP captures and the Python contract validator
change together. There is no committed generated SDK yet, dependency/version/lock
change, new route, group metadata, journal, autosave or project switching.
Detailed rules are in [group admission](phase-5-related-group-admission.md).

## Pinned source and executable evidence

The retained `/tmp/libjadx-pr13-source` bundle was inspected. All 16 entries of
`/tmp/libjadx-pr17-source-audit.json` still match their recorded SHA-256 hashes;
this is a local comparison with the retained pinned audit, not a master download.
Relevant pinned upstream paths:

- [JavaClass](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/JavaClass.java):
  lazy `load`/`loadLists`, `getMethods`, DONT_GENERATE filtering and cached public lists.
- [ClassNode](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/nodes/ClassNode.java):
  raw `getMethods`, code unloading and retained original declarations.
- [JadxNodeRef](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/data/impl/JadxNodeRef.java):
  original declaring owner and full native short ID; mutable keys must be copied.
- [MethodInfo](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/info/MethodInfo.java):
  original versus alias names and full short IDs.
- [ClassModifier](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/visitors/ClassModifier.java):
  real synthetic bridge suppression.
- [UserRenames](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/visitors/rename/UserRenames.java)
  and [ApplyMappingsPass](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-plugins/jadx-rename-mappings/src/main/java/jadx/plugins/mappings/load/ApplyMappingsPass.java):
  exact native keys and mapping destination namespace zero. Existing PR #15
  census/verifier and PR #18 replacement/baseline evidence remains authoritative.

New tests are `RelatedGroupAdmissionTest`, `RelatedGroupStateTest`,
`RelatedGroupBoundsTest`, `RelatedGroupVerifierLifecycleTest`, and
`RelatedGroupGuiTest`; `RelatedPropagationEndpointsTest` now covers supported and
rejected requests. Fixtures include owned Hierarchy/Visibility Java, generic
hidden-bridge Java, generated bounded families and owned direct DEX. Existing
candidate incompleteness, old-replay negative controls, scoped/local restrictions,
replacement faults, mapping and explicit-save tests are preserved.

The expanded protected-family oracle initially differed only on a cached public
search entry for an omitted default constructor. Explicit aliases, raw identity
and source already agreed. Mirroring current-revision admission reads in the
fresh oracle reproduces exact full metadata; no production normalization or
constructor identity change was made. The raw bridge fixture keeps real codegen
flags with test-only DONT_UNLOAD_CLASS to prove the public list actually omits its
compiler bridge while raw collision admission rejects it.

## PR-description checklist

1. Verification is inside `withExclusiveEdit`, before staging and publication.
2. No raw verifier escapes. Edit context expires on callback return/failure and
   after commit; no cached COMPLETE can authorize another admission.
3. Only COMPLETE advances; all other statuses expose no partial members.
4. NOT_FOUND → 404; AMBIGUOUS_INPUT → 422 INVALID_ENTITY_ID; unsupported/missing/
   external/invalid hierarchy → 422 UNSUPPORTED_CAPABILITY; RESOURCE_LIMIT → 429;
   INPUT_CHANGED → 409 external conflict; FAILED/exception → 500 internal error.
5. Maximum family size is 64, inclusive.
6. Batch caps are four propagated items, 128 total members and 800000 reserved
   work. Each verification retains 10000 nodes/200000 work; no cross-admission cache.
7. Order is original class descriptor, original name, full original descriptor.
8. All Joined seeds return exactly ExtendedLeft, Joined, SeparateLeft, SeparateRight.
9. Each resolves uniquely to a current editable exact original native short ID.
10. One invisible/uneditable/unrepresentable member rejects the entire item.
11. Raw ClassNode inventory includes hidden synthetic and compiler bridge methods.
12. Every family owner checks alias plus argument descriptor, excluding return type.
13. Full proposed names detect collisions created by other batch items in both orders;
    current names conservatively reject obstacle-removal batches too.
14. Propagated groups cannot overlap, even with matching requested aliases.
15. Ordinary renames cannot overlap a family member.
16. Parameter edits cannot target a group member in the same batch. Class renames
    also cannot share a group batch; separate batches remain supported.
17. One standard native declaration rename per exact original family member.
18. Deep-copy/private staging plus first/middle/final injected failures prevent a
    partial group in a committed prefix.
19. No-op requires every member's explicit record already equals newName.
20. No-op constructs no replacement and retains revisions/dirty/native state.
21. APPLIED returns the exact sorted original family, including the seed once.
22. Verified NO_CHANGE returns the same exact family with SKIPPED/NO_CHANGE.
23. Failed and unexecuted groups return empty affectedRefs.
24. Ordinary RENAME retains empty affectedRefs and single-record implicit behavior.
25. Automatic aliases remain derived; no records freeze incidental aliases.
26. Publication uses ordinary PR #18 `EditContext.commit`, never in-place replay.
27. Input/mapping changes before verification or during load reject without publication;
    explicit reload rebuilds accepted baselines and fresh verification.
28. Explicit save/restart preserves every standard group record.
29. Unsaved restart and discard/reload remove pending group intent.
30. Positive matching-GUI Save As covers all four Joined seeds with unrelated state.
31. The final matching-GUI task list and results are recorded below.
32. Covariant/bridge propagation remains rejected.
33. Missing/external/duplicate hierarchies remain rejected.
34. Local rename remains unsupported; its existing negative gate is unchanged.
35. Parameter propagation remains unsupported; ordinary scoped editing is retained.
36. Final evidence string is INDEPENDENT_CLOSED_INPUT_FAMILY_GUI_VERIFIED (PARTIAL).
37. Final commands, counts and artifacts are recorded below; opt-in skips are never passes.

## Final validation

**Outcome A passed locally.** All results below use the final implementation,
contract and test patch. The 27-file manifest in
`/tmp/pr19-implementation-final-manifest.json` records implementation fingerprint
`2ec0f677d123da8708578a176141859ea5f1b1b35b44c3bcd36539fc3d76a6a0`;
each file was unchanged through final validation. Subsequent edits only finish
this documentation. No preliminary or historical GUI run counts here.

```bash
./gradlew test --offline \
  --tests 'dev.libjadx.*Related*' \
  --tests 'dev.libjadx.*Propagat*' \
  --tests 'dev.libjadx.*Hierarchy*' \
  --tests 'dev.libjadx.*Replacement*' \
  --tests 'dev.libjadx.app.*Edit*' \
  --tests dev.libjadx.app.OpenApiDocumentTest
./gradlew clean check --offline --rerun-tasks
./gradlew installDist --offline

JADX_GUI=/tmp/libjadx-rerun-jadx-1.5.6/bin/jadx-gui ./gradlew \
  guiRoundTripTest rawGuiRoundTripTest nativeEditGuiRoundTripTest \
  mappingExportGuiRoundTripTest mappingImportGuiRoundTripTest \
  scopedEditGuiRoundTripTest relatedPropagationGuiRoundTripTest \
  propagatedEditReplayGuiDiagnosticTest safeReplayGuiDiagnosticTest \
  replacementEditGuiRoundTripTest propagatedEditGuiRoundTripTest \
  --offline --rerun-tasks -x test

/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-edit-contract.py
/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-mapping-export-contract.py
/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-mapping-import-contract.py
/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-search-contract.py
git diff --check
git diff --check origin/main...HEAD
```

| Gate | Final result |
|---|---|
| Focused regression | BUILD SUCCESSFUL, 4m 18s; 33 classes, 172 tests: 168 passed, four opt-in GUI skips, zero failures/errors |
| Clean full check | BUILD SUCCESSFUL, 9m 38s; 83 classes, 461 tests: 450 passed, eleven opt-in GUI skips, zero failures/errors |
| Installed distribution | BUILD SUCCESSFUL, 3s; clean check also ran packaged startup/restart tests against installDist |
| All eleven dedicated GUI tasks above | BUILD SUCCESSFUL, 5m 52s; eleven tests passed, zero skips/failures/errors; 22 actual matching-1.5.6 GUI Save As tasks executed |
| Edit contract | OpenAPI 3.1 valid; 16 examples, 20 ordinary HTTP captures, four injected service results, 18 scoped HTTP captures and 32 propagated HTTP captures validated |
| Mapping export contract | OpenAPI 3.1 valid; three examples and 31 HTTP captures validated |
| Mapping import contract | OpenAPI 3.1 valid; four examples and 41 HTTP captures validated |
| Search contract | OpenAPI 3.1 valid; four examples and 33 HTTP captures validated |
| Patch hygiene | Both diff checks passed; all 15 new files passed a separate trailing-whitespace check; new documentation links resolve |

`-x test` reuses fixture preparation from the completed clean check. Every
dedicated GUI Exec and Test task was rerun; their XML reports explicitly have no
skips. The eleven opt-in skips in ordinary `test` are accounted for by these
eleven successful dedicated tasks. The new positive task checks all four Joined
seeds: ExtendedLeft, Joined, SeparateLeft and SeparateRight. Historical
counterexample diagnostics still assert their negative outcomes.

Local logs are `/tmp/pr19-focused-final.log`, `/tmp/pr19-clean-final.log`,
`/tmp/pr19-distribution-final.log`, `/tmp/pr19-gui-final.log`, and
`/tmp/pr19-{edit,mapping-export,mapping-import,search}-contract-final.log`.
XML counts are retained in `/tmp/pr19-{focused,clean,gui}-final-counts.json`.
GUI artifacts and individual `actual-gui.log` files are under the corresponding
`build/*gui*` fixture directories, including
`build/propagated-edit-gui-fixture/<seed>/gui-resaved.jadx`.

### Bounded performance observations

Final clean-run artifact: `build/related-group-probe/performance.json`.
Times below are milliseconds, rounded to three decimals. The resolution/inventory
measurement mirrors admission on warm metadata before the timed service request;
the service independently verifies again. These owned-fixture observations are
not large-project latency guarantees and do not justify cross-admission caching.

| Fixture | Members | Raw methods | Verification work | Verification ms | Resolution/inventory ms | Replacement load ms | End-to-end service ms |
|---|---:|---:|---:|---:|---:|---:|---:|
| Singleton | 1 | 2 | 8 | 0.957 | 1.039 | 231.064 | 241.751 |
| Joined | 4 | 5 | 75 | 1.498 | 0.512 | 159.672 | 167.085 |
| Five-member chain | 5 | 16 | 90 | 1.148 | 0.944 | 314.576 | 320.291 |
| Inclusive 64-member boundary | 64 | 127 | 1204 | 2.916 | 4.723 | 283.744 | 300.327 |
| Two independent groups | 8 | 14 | 128 | 1.310 | 0.627 | 267.857 | 274.691 |

## Persistence and next milestone

No file changes before explicit native save. GUI-resaved projects retain all four
family records, unrelated native/scoped/VAR/mapping aliases and comments, and
fresh-derived automatic aliases. Original relative inputs and mappings retain
the existing codec rules. Unknown fields survive headless save; matching GUI
unknown-field loss is separately documented. Saved restart retains intent under
a new session; unsaved restart/discard and discard/reload reconstruct disk state.

Remaining unsupported: covariance/bridges, external/missing/duplicate or unsupported
input hierarchies, local editing and parameter propagation. The next unblocked
work is the separate local-variable feasibility milestone or approved remaining
Phase 5.2 work; no new human architecture decision is required for this admission.
