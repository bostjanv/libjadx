# PR #12: validated Tiny v2 mapping import into native in-memory edits

Branch: `phase5-2-safe-mapping-import`; confirmed base:
`6da173126a3b5a67fcc50b32400577230e19aab5` (merged PR #11).

An existing local Tiny v2 file can now be merged through
`POST /api/v1/project/mappings/import`. The request captures bounded bytes,
validates every record and original full declaration key, and preflights all
aliases/comments against current native and attached state before one in-memory
native commit. Matching values return `NO_CHANGE`; conflicts reject the entire
file. The receipt separates parsed records from applied/unchanged operations.
No source/native file is written or attached; persistence requires explicit save.

The shared strict parser preserves export output bytes. The cold-owner replay
fix prevents repeated attached comments across edits. Pure declaration name and
LINE validation is shared with batches. All transport, file capture, codec/plan
and admission/commit work remains separated. Dependencies and locks are unchanged.
Four OpenAPI examples, independent HTTP validation and real-Jadx/service,
installed-distribution, external-process race and matching-GUI tests accompany
this slice. Full evidence and pinned source references are in
[phase-5-mapping-import.md](phase-5-mapping-import.md).

Unsupported: extra/inverted namespaces, metadata/options, args/locals/code refs,
unknown/duplicate records, unsupported/synthetic/bridge/special targets, package
moves/changed inner aliases, arbitrary changed/composite comments and overwrite
policy. Jadx-generated aliases count as existing aliases. Input provenance is
limited to Jadx-visible definitions; duplicate original inputs can be collapsed.
File identity/hash checks are optimistic; external writers after the final check
remain a race. Scoped edits, propagation and Phase 6 Python SDK work remain open.

## Descendant collision review fix

The [P2 review finding](https://github.com/bostjanv/libjadx/pull/12#discussion_r4124651627)
was reproduced with the owned `tests/fixtures/mappings/Outer.java` hierarchy.
Attached `Other -> NewOuter$Inner` plus an incoming `Outer -> NewOuter` left the
outer itself unique but collided with its untouched inner. The same gap affects
nested `Outer$Inner$Deep` and existing native aliases.

Preflight now checks uniqueness of every resulting visible qualified class
alias whenever the plan changes a class alias. The catalog uses the pinned
parent-alias semantics and the existing aggregate allocation/string budget.
Collisions reject the complete alias/comment plan before staging, with
`409 MAPPING_MERGE_CONFLICT` / `ALIAS_COLLISION`. No-op, member-only and
comment-only plans retain their existing admission/publication behavior.
Direct inner alias edits remain unsupported. OpenAPI clarifies the existing
collision guarantee; request, receipt and error schemas are unchanged.

Real-Jadx probes prove implicit inner/nested requalification on replay and fresh
load without source generation. Service regressions cover attached and native
collision baselines on clean and already-dirty projects, with unchanged pending
data, revisions, search identity, class processing/alias metadata and file hashes,
and zero staging calls. A collision-free parent rename still commits once,
requalifies both descendant levels, is idempotent and survives explicit native
save/reopen with only one original outer rename record. A fresh HTTP capture
checks the typed 409 envelope; the actual GUI reverse test now also checks the
untouched inner's qualified alias after GUI resave.

Before the fix, the following regression command failed as expected in 15s:
nine tests discovered, four collision cases failed because no rejection was
thrown; the five probe/positive tests passed. The final gates below rerun the
regressions against the fix.

```bash
./gradlew test --offline --tests dev.libjadx.probes.JadxMappingImportProbeTest --tests 'dev.libjadx.app.MappingImportServiceTest.implicitDescendantAliasCollisionRejectsWholePlanBeforeStaging' --tests 'dev.libjadx.app.MappingImportServiceTest.collisionFreeParentRenameRequalifiesAllDescendantsAndSurvivesExplicitSave'
```

## Final validation

The final code was verified using JDK `21.0.12.1`, Gradle `8.14.3`, pinned Jadx
`1.5.6` and mapping-io `0.8.0`. No code changed after these final gates began.

| Exact command | Outcome |
|---|---|
| `./gradlew test --offline --tests 'dev.libjadx.*Mapping*' --tests 'dev.libjadx.app.*Edit*' --tests dev.libjadx.app.OpenApiDocumentTest` | PASS, 1m 58s; 27 classes, 126 discovered, 124 passed, two opt-in GUI skips, zero failures/errors. |
| `./gradlew clean check --offline --rerun-tasks` | PASS, 4m 12s; 58 classes, 275 discovered, 270 passed, five opt-in GUI skips, zero failures/errors. Rebuilt `installDist` and ran real packaged subprocesses. |
| `JADX_GUI=/tmp/libjadx-rerun-jadx-1.5.6/bin/jadx-gui ./gradlew guiRoundTripTest rawGuiRoundTripTest nativeEditGuiRoundTripTest mappingExportGuiRoundTripTest mappingImportGuiRoundTripTest --offline --rerun-tasks` | PASS, 5m 22s; reran the 275-test ordinary suite (270 passed, five opt-in skips), then all five actual GUI open/save/headless-reopen tests passed individually, zero failures/errors/skips in the dedicated tasks. |
| `./gradlew installDist --offline` | PASS, 3s, four tasks up-to-date after the GUI gate. |
| `/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-mapping-import-contract.py` | PASS; OpenAPI 3.1, four examples and 41 live HTTP captures; statuses 200/400/403/404/409/415/422/429/500/503. |
| `/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-mapping-export-contract.py` | PASS; three examples and 31 live HTTP captures; statuses 200/400/403/409/415/422/429/500/503. |
| `/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-edit-contract.py` | PASS; six examples, ten live HTTP captures and four injected service results; statuses 200/400/403/404/409/415/422/429/503. |
| `/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-search-contract.py` | PASS; four examples and 66 live HTTP captures; statuses 200/202/400/403/409/415/422/429/503. |
| `git diff --check origin/main...HEAD` | PASS; no whitespace errors in the committed PR patch. The working-tree and staged patch checks and each new file's `git diff --no-index --check /dev/null <path>` also passed. |

The five opt-in skips in the ordinary suite are the five GUI reverse-round-trip
tests; each subsequently passed in its dedicated task. Together these gates
executed all 275 distinct tests successfully. Search captures use UUID filenames:
the final clean check and the GUI command's ordinary-suite rerun each produced
33 responses, hence 66 final-code responses. Other capture suites replace their
own directories. The initial 267-test implementation run and the intentionally
failing pre-fix reproduction are not substituted for these renewed final-code
gates.

The validator environment uses Python `3.14.4` and
[`tests/requirements-contract.txt`](../tests/requirements-contract.txt):
PyYAML `6.0.3`, jsonschema `4.26.0`, openapi-spec-validator `0.9.0`.
`/tmp/libjadx-rerun-jadx-1.5.6/bin/jadx --version` reported `1.5.6`; the GUI
executable above comes from that same pinned distribution. Verification ran on
Linux amd64; no remote CI, Windows or macOS result is claimed.

## Persistence artifacts and hashes

The import fixture explicitly saved `imported.jadx` with its original
`attached.tiny` attachment, three imported native aliases and three additive
native LINE suffixes. Actual Jadx GUI wrote `gui-resaved.jadx`; a fresh headless
engine verified all original class/method/field keys, aliases and attached/native
comments exactly once, plus the untouched inner's requalified alias. Service and installed-distribution tests separately
verified unsaved discard/reload, shutdown-discard, explicit save/restart and
idempotent reimport. Import did not write or attach its source file.

Final SHA-256 values (generated artifacts remain under `build/`, not committed):

| Path | SHA-256 |
|---|---|
| `tests/fixtures/edits/EditOwner.java` | `44653f4a5ab3f0f2a2da64a07a0d06b10673fcd8dfe2a865b68ae43db7542a79` |
| `tests/fixtures/symbols/SymbolFixture.java` | `c24b529ac41e0362bcbb4e95adfd1db18b0f3b1f95930c596be3a65ec8e884af` |
| `tests/fixtures/mappings/Outer.java` | `0c3326516ae928173f3c29131c75a1e8ce30f881173383f3ee7230c2f757da0a` |
| `build/mapping-import-gui-fixture/symbols.jar` | `2afe771e24ce000de92dac3c635b5a893d1b493e7f830f0e03875d66ea204678` |
| `build/mapping-import-gui-fixture/second.jar` | `1e8b06be3a31c557368e34ea1437c6bd2a3d6160fe31ece000d57bf05646e775` |
| `build/mapping-import-gui-fixture/attached.tiny` | `2ec7d1f7d255b474402554e4325a0bbf81e3184f59a674ef8845c8b2d3352817` |
| `build/mapping-import-gui-fixture/incoming.tiny` | `91be03dd3eb903fda863980a0afc2cbe503ca3dc61d05d2c50dde9aea8024be6` |
| `build/mapping-import-gui-fixture/imported.jadx` | `97b223fb7a5b68f749f2db1d5f19a4c71907e7fdc088bc10f063b4cbe73ee0b7` |
| `build/mapping-import-gui-fixture/gui-resaved.jadx` | `f55559babb50826b42d463e33b912341229eeb96ee314c2d993e598c7bd49356` |
| `build/mapping-export-gui-fixture/exported.tiny` | `2a56f240bb903a815dd8e6455badc3686eef23d8a3453a3cfc67aaeb8644351a` (unchanged PR #11 export bytes) |

Reports: `build/test-results/test/`, `build/reports/tests/test/` and the matching
`guiRoundTripTest`, `rawGuiRoundTripTest`, `nativeEditGuiRoundTripTest`,
`mappingExportGuiRoundTripTest`, `mappingImportGuiRoundTripTest` subdirectories.
Captured responses: `build/mapping-import-contract-responses/`,
`build/mapping-export-contract-responses/`, `build/edit-contract-responses/`,
`build/search-contract-responses/`. The receipt is
`build/mapping-import-gui-fixture/receipt.json`; the standalone distribution is
`build/install/libjadx/` and contains no GUI runtime JAR.

Phase 5.2 remains incomplete: variable/parameter editing, scoped renames and
related-method propagation are the next native-edit work; Phase 6 SDK generation
remains separate. No human decision blocks this bounded import slice.
