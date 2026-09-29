# PR #14 review: preserve related-method completeness counterexamples

Outcome **B** from the supplied work order. A fully resolved owned interface
hierarchy proves that Jadx 1.5.6 omits a relevant branch from related-method
candidates. Explicit propagation remains `422 UNSUPPORTED_CAPABILITY` and
`edit.related_propagation` remains UNSUPPORTED. Ordinary method RENAME preserves
its existing implicit candidate alias behavior and empty affectedRefs.

## Base, tested implementation and upstream

- Requested/fetched base: `69bec509b38de762c0ba6c317755e7254358d3d6`.
- Initial validation ran on the complete implementation worktree at base
  `69bec509b38de762c0ba6c317755e7254358d3d6`. The branch is
  `phase5-related-method-propagation`; final base/head SHAs are recorded in the
  GitHub PR description after committing and rebasing.
- Original checkout was PR #13 branch head
  `e6530657f4d2dc2b7c962b2401d9e5efc1135469`; origin/main was fetched and a new
  branch created from the specified merged base before any changes.
- Pinned Jadx source: `28ff15e4ae69950aebea110a13e5ab895d234dfc`, release 1.5.6.
  All eight required local source files were byte-compared against fresh raw
  downloads at that exact commit; `/tmp/libjadx-pr14-source-audit.json` records
  paths and SHA-256 values. No dependency, lock or toolchain changed.
- Tested implementation fingerprint:
  `sha256:53466609477d1c2fb58642873ef5334dbd382b8d9f943dedac16dd972f081abf`.
  It hashes compact, sort_keys JSON containing sorted `{path, sha256}` records
  for the 13 changed files under src/tests/openapi plus build.gradle.kts.
  `/tmp/libjadx-pr14-implementation-hash.json` contains the manifest. Evidence
  documentation is excluded so recording final results does not alter the hash.

## Implemented and evidence

The [related propagation evidence](phase-5-related-propagation.md) contains exact
original groups and pinned source links. Owned Java covers chains/siblings,
abstract/interface/default/diamond declarations, independent interface branches,
overloads, private/static exclusions, covariance/bridge, inner methods, two input
JARs, missing/external ancestors and a discarded duplicate original definition.
All group assertions use original owners and full method descriptors.

Joined's `joined(I)I` candidate group contains Joined, ExtendedLeft and SeparateLeft
but omits SeparateRight. The omitted branch is resolved, editable and has no
DONT_RENAME marker; starting from its declaration returns an empty group.
Duplicate original input and missing-parent probes show further uncertainty.
Positive simple-chain/default/shared-root-diamond observations are retained
without converting them into a production completeness guarantee.

Two diagnostic native strategies compare one seed record with one record for each
of the four **owned known** members. The seed strategy has different hot/fresh
aliases and leaves the right branch at its attached mappedRight alias. Explicit
records for all four replay in these fixtures, but candidate discovery cannot
produce their exhaustive membership. No native strategy is adopted in production.
GUI JMethod's builder removes only candidate declaration records; MTH_ARG and VAR
keys and the omitted branch record survive. Save As behavior is checked separately
with the actual GUI, not inferred from that builder probe.

Eight live HTTP rejection captures assert zero effective staging in mixed batches
with clean and dirty state: native/pending data, complete revision snapshot,
search identity, source snapshot, unrelated cold class and disk hashes remain
unchanged. The unsupported field is rejected even for false/null/string values;
it remains excluded from the accepted schema. OpenAPI now documents this and the
legacy implicit behavior; a reviewed negative request and unsupported response
are independently validated. Capability evidence and error text identify the
actual completeness blocker. No accepted request/response shape expanded.

## Exact final verification

```bash
./gradlew test --offline --tests 'dev.libjadx.*Related*' --tests 'dev.libjadx.*Override*' --tests 'dev.libjadx.app.*Edit*' --tests 'dev.libjadx.app.*Scoped*' --tests dev.libjadx.app.OpenApiDocumentTest
./gradlew clean check --offline --rerun-tasks
./gradlew installDist --offline
JADX_GUI=/tmp/libjadx-rerun-jadx-1.5.6/bin/jadx-gui ./gradlew guiRoundTripTest rawGuiRoundTripTest nativeEditGuiRoundTripTest mappingExportGuiRoundTripTest mappingImportGuiRoundTripTest scopedEditGuiRoundTripTest relatedPropagationGuiRoundTripTest --offline
/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-edit-contract.py
/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-mapping-export-contract.py
/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-mapping-import-contract.py
/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-search-contract.py
git diff --check
git diff --check origin/main...HEAD
```

Focused command: **73 tests**, 72 passed, one opt-in GUI skip, zero failures/errors;
passed in 1m 49s. Clean check: **66 classes / 316 tests**, 309 passed, seven opt-in
GUI skips, zero failures/errors; passed in 5m 46s. Existing real-Jadx edit/scoped,
external-conflict, restart/discard, concurrency/cancellation, malformed-input,
search and installed-distribution checks passed. installDist passed (up-to-date).
The packaged runtime remains headless, with no GUI runtime dependencies.

Dedicated GUI command passed in **2m 43s**. All seven dedicated tasks executed
one test each, with **zero failures, errors or skips**. The ordinary suite was
up-to-date from clean check. All 316 distinct tests passed across the ordinary
and dedicated suites; the normal seven skips alone are not counted as GUI proof.
The actual executable reported 1.5.6 via
`/tmp/libjadx-rerun-jadx-1.5.6/bin/jadx-gui --version`.

The related task starts the actual GUI twice under Xvfb and uses xdotool for
File > Save project as. Logs are
`build/related-gui-fixture/seed/actual-gui.log` and
`build/related-gui-fixture/members/actual-gui.log`; both report 23 loaded classes,
44 methods and 138 instructions. GUI-resaved projects in those directories are
`gui-resaved.jadx`. Fresh headless original-key checks pass for every owned group
member and its exact alias, with emitted Java and unrelated edits checked.

GUI Save As **preserved** the native group representation: seed has precisely
`related.Hierarchy$Joined:joined(I)I`; members has precisely ExtendedLeft, Joined,
SeparateLeft and SeparateRight original `joined(I)I` records, each with
renamedJoined. It did not canonicalize four records to one. The seed's omitted
SeparateRight remains mappedRight after the GUI round trip. The unrelated native
class alias, comment, MTH_ARG parameter name, mapping attachment and relative
input paths survive. GUI serialization removes futureRoot/futureCodeData;
headless save retains both, as separately asserted. This is Save As evidence,
not a claim of visual inspection or actual GUI rename-dialog execution.

All four independent validators passed against captures from the clean run:

| Validator | Reviewed examples | Fresh HTTP captures | Additional results |
|---|---:|---:|---|
| Editing | 11 | 10 legacy + 18 scoped + 8 related rejection | Four injected legacy staging results; unsupported flag schema rejection |
| Mapping export | 3 | 31 | Strict Tiny v2 gates |
| Mapping import | 4 | 41 | Conflict-safe merge gates |
| Search | 4 | 33 | Partial/complete and invalidation gates |

Normal skips are not GUI passes. Reports are under build/reports/tests and
build/test-results; fresh rejection captures are build/related-contract-responses;
the full original-ref candidate census is build/related-probe/original-groups.txt.
The initial whitespace check covered the complete working patch, including new
files. After committing and rebasing, the triple-dot whitespace check is repeated
against the actual PR diff. GitHub publication does not change the tested
implementation fingerprint.
Environment: Linux, JDK 21.0.12.1, Gradle 8.14.3, Jadx 1.5.6, Python 3.14.4.

## Persistence, scope and next milestone

The diagnostic probes check unchanged native bytes until explicit save; both JARs
and the attached mapping retain their bytes. Explicitly saved projects reopen with
exact original-key aliases, unrelated comments/parameter edits and relative paths.
Headless unknown JSON preservation and upstream GUI unknown-field loss are
distinguished. The actual related GUI task is a **counterexample persistence
diagnostic**, not a successful propagated-service request gate for outcome A.

Initial probes using nonpublic convertMethodNode failed compilation; the probes
now use pinned public getJavaNodeByRef. Optimistic hot one-seed alias assertions
failed and were replaced with assertions of the actual failure behavior. An early
focused run had one test failure from inspecting capability reason instead of its
existing evidence field; corrected before the final successful focused/clean runs.
No earlier failure or opt-in skip is counted as a successful release gate.

All-owner group conflict preflight, group-size budgets, group-private staging
faults and propagated successful/no-op/partial/distribution transactions are
**not implemented or claimed** after the mandatory completeness failure.
Legacy equivalents remain regression-tested. The existing 64-entry affectedRefs
cap, strict mapping formats, scoped/catch protections and explicit-save policy
are unchanged. No local editing, Python SDK, autosave or new persisted data added.

Smallest compatible next propagation task: a bounded original-input census and
independent hierarchy completeness verifier, then all-owner conflict/staging and
actual propagated-service GUI gates. A separate local-variable feasibility/ADR
may proceed. Phase 5.2 remains incomplete; moving to Phase 6 still requires human
approval of the remaining Phase 5.2 exit interpretation. No architectural change
or human decision is required to retain this evidence-only capability boundary.
