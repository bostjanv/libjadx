# PR #15 review: bounded original-input census and independent completeness

Outcome A for this prerequisite: independent completeness is established for the
documented conservative subset. **Propagation remains UNSUPPORTED.** This PR
implements verification infrastructure, not propagated rename transactions.

Requested base and checkout: `54a3fffbbe7680e22a66c7ef00a762ab6c2e7f29`.
Jadx remains 1.5.6 at source commit
`28ff15e4ae69950aebea110a13e5ab895d234dfc`. Twenty-three relevant source files under
`/tmp/libjadx-pr13-source` were byte-compared with fresh commit-pinned downloads;
the local source audit is `/tmp/libjadx-pr15-source-audit.json`. Source links and
the complete subset/limits are in [hierarchy verifier evidence](phase-5-hierarchy-verifier.md).
Validation applies to the final implementation worktree on this base; the
implementation fingerprint below identifies new and changed code/fixtures/locks.

## Implemented

`JadxInputCensusAdapter` uses strict, bounded original input readers before
primary class loading and retains all configured origins/duplicate definitions.
Core immutable records, forward/reverse graph and `RelatedHierarchyVerifier`
contain no mutable Jadx objects. The independent algorithm checks closed
hierarchy components, exact-return method families and visibility/exclusions.
`HierarchyComparison` observes candidates only afterward.

Production initialization/rebuild owns derived census state. Shared immutable
reads use runtime QUERY_READ admission; exclusive operations conflict. Content
fingerprints bracket capture, primary load and verification, with byte-limited
hashing that also refuses files growing during reading. Changed inputs require
explicit reload. Native declaration edits/save preserve the capture; mapping
rebuild and restart construct fresh state. No state is persisted or autosaved.

Public routes, schemas, capabilities, SDK scope and native formats are unchanged.
Existing JVM/DEX input artifacts gain compile visibility, with only corresponding
lock membership changes. Runtime versions and GUI isolation remain unchanged.

## Review follow-up

The nonblocking review of `a74efd0` identified that the generic hierarchy callback
could return its verifier after the query lease ended. An unchanged-input reload
would leave that immutable verifier's fingerprint checks successful even though
its engine had been superseded.

The runtime now supplies a callback handle confined to the admitting thread and
callback lifetime. It rejects cross-thread use and expires in `finally` before
the query lease is released, including when the callback fails. Expiration also
drops the captured verifier reference. Real-Jadx regressions retain a handle,
reject it before/after unchanged-input reload, reject background-thread use and
check callback-failure expiration plus subsequent reload admission. Lifecycle
identity assertions now compare owning engines rather than retain raw verifiers.

A returned immutable COMPLETE result can still be retained as historical
evidence. PR #16 must verify and stage under one current project-exclusive lease,
checking the current session/engine and expected revision; neither a cached
result nor a separate query lease authorizes native edits. Propagation remains
UNSUPPORTED, and the native replay/collision/persistence gates remain deferred.

## Review checklist

1. **Raw API:** pinned internal `JavaClassReader.loadClassData()` and
   `DexFileLoader.loadDexReaders()` / `DexReader.visitClasses()`, copying public
   `IClassData`/`IMethodRef` values. Bulk loaders swallow failures, so they are
   excluded from completeness authority.
2. **Formats exercised:** positive individual `.class`, two JVM JARs, two direct
   DEX inputs and embedded DEX in two JAR envelopes. APK/bundles/nested archives/
   Smali input/conversion are unsupported by this census.
3. **Duplicates retained:** yes, including divergent methods, identical full keys
   with different access/supertypes, duplicate interfaces and discarded edges.
   Origin is canonical configured path plus startup-list position. Jadx selects
   the first ordinary/raw-DEX definition, but prefers a later embedded classes.dex
   over classes2.dex; both are retained independently.
4. **Hard limits:** 16 inputs, 20,000 raw classes, 2,000 methods/class, 100,000
   total methods, 80,000 edges, eight definitions/descriptor, 100,000 archive
   entries, 32 MiB/file or expanded code entry, 128 MiB cumulative admitted bytes,
   eight million metadata characters; family <=64, 10,000 nodes, 200,000 work.
   Exact inclusive and one-less boundary tests cover these limits.
5. **Override rules:** original name/arguments plus identical return descriptors;
   public/protected/package visibility, interfaces/defaults/abstracts, class
   chains and inherited class implementations of interfaces. Private/static/
   special seeds are excluded; final/cycle/edge/visibility violations refuse.
   Siblings without a shared declaration and unrelated same-name/overloads stay
   separate. Full original descriptors remain output identity.
6. **Bridges/covariance:** fail closed for entire relevant structural components.
   Same-name synthetic/bridge declarations are checked across erased argument
   signatures. Object/String, generic bridges, synthetic lambda and unbridged
   covariant metadata are tested. Native/GUI bridge editability is not proved.
7. **Missing/external/duplicate:** explicit incomplete statuses and no member list.
   Object is external, with an explicit terminal rule only for signatures absent
   from its nonempty pinned classpath method table. Its own method signatures and
   all other external branches remain unverifiable. Closure is conservative.
8. **Seed independence:** every member of each tested complete family returns the
   same sorted full-ref set and COMPLETE status, including the 64-member boundary.
9. **PR #14 omission detected:** independent Joined family has four declarations;
   three seeds have three Jadx candidates, SeparateRight has none. Comparison
   reports JADX_CANDIDATE_MISMATCH, missing members and seed dependence.
10. **Source generation:** none during census/verification/comparison. Tests assert
    cold primary class states and unchanged code-data/aliases/revisions/files.
11. **Invalidation:** detected input content/path changes; explicit reload/rebuild
    reconstructs. Edits/save do not alter raw graph identity; mapping rebuild and
    mode-isolated engines retain equivalent original families. New runtime/session
    tests and existing packaged process-restart gates cover lifecycle; the internal
    verifier result is not exposed from subprocesses over HTTP.
12. **Public shape change:** none. OpenAPI and examples are unchanged.
13. **Propagation still unsupported:** yes; existing live 422 rejection/zero-staging
    tests remain mandatory. Ordinary RENAME behavior is unchanged.
14. **PR #16 evidence:** verification and staging under one current exclusive
    lease with session/engine/revision checks; COMPLETE-only admission,
    all-owner collisions, group-private
    native staging, exact affectedRefs, no-op/prefix/fault semantics, explicit save,
    headless restart/discard and actual matching-GUI propagated service gates,
    including resolution of the existing hot/fresh native replay discrepancy.

## Validation

Final implementation fingerprint (18 changed/new implementation, fixture and
dependency-lock files):
`sha256:28dd4a3b6108045a56951a72ab5a2a92dc585641e007a26aba944e05109284be`.
The sorted path/content manifest is `/tmp/libjadx-pr15-implementation-hash.json`.
Documentation is excluded from this fingerprint; it was checked again after the
gates. These results cover the final implementation snapshot on the requested
base. Committing and publishing that snapshot do not change the tested code.

Environment: Linux, OpenJDK 21.0.12.1, Gradle 8.14.3, matching Jadx GUI 1.5.6,
Python 3.14.4 in `/tmp/libjadx-rerun-contract-venv`.

Exact final commands:

```bash
./gradlew test --offline \
  --tests 'dev.libjadx.*Hierarchy*' \
  --tests 'dev.libjadx.*Related*' \
  --tests 'dev.libjadx.*Input*Census*' \
  --tests 'dev.libjadx.app.*Edit*' \
  --tests dev.libjadx.app.OpenApiDocumentTest

./gradlew clean check --offline --rerun-tasks
./gradlew installDist --offline

JADX_GUI=/tmp/libjadx-rerun-jadx-1.5.6/bin/jadx-gui \
  ./gradlew guiRoundTripTest rawGuiRoundTripTest \
  nativeEditGuiRoundTripTest mappingExportGuiRoundTripTest \
  mappingImportGuiRoundTripTest scopedEditGuiRoundTripTest \
  relatedPropagationGuiRoundTripTest --offline

/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-edit-contract.py
/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-mapping-export-contract.py
/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-mapping-import-contract.py
/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-search-contract.py

git diff --check
git diff --check origin/main...HEAD
```

| Gate | Final outcome |
|---|---|
| Focused evidence/regressions | 21 test classes, 94 tests: 93 passed, one opt-in GUI skip, zero failures/errors |
| Clean full check | BUILD SUCCESSFUL in 6m 23s; 70 test classes, 359 tests: 352 passed, seven opt-in GUI skips, zero failures/errors |
| Distribution | BUILD SUCCESSFUL; final clean outputs were up to date |
| Matching-GUI tasks | BUILD SUCCESSFUL in 2m 48s; each of all seven tasks executed one passing test, zero skips/failures/errors |
| Edit validator | OpenAPI 3.1 valid; 11 examples, 10 live HTTP responses, four injected service results, 18 scoped HTTP responses, eight propagation rejection responses valid |
| Mapping export validator | Three examples and 31 live HTTP responses valid |
| Mapping import validator | Four examples and 41 live HTTP responses valid |
| Search validator | Four examples and 33 live HTTP responses valid |
| Whitespace | Both commands passed; all 16 new files also passed `git diff --no-index --check /dev/null <path>` |

GUI tasks intentionally reuse the freshly rebuilt full-test output; their
fixture-saving tasks and dedicated tests executed, rather than rerunning the
ordinary suite again. The seven ordinary-suite skips do **not** count as GUI
passes: all seven were subsequently exercised through their actual GUI tasks.
The `origin/main...HEAD` check alone does not cover uncommitted changes; the
working-tree check and individual new-file checks cover this patch.

Final XML counts are recorded in `/tmp/libjadx-pr15-final-test-counts.json`;
focused counts are in `/tmp/libjadx-pr15-focused-counts.json`. Repository reports
are under `build/reports/tests/` and `build/test-results/`. Independent/Jadx
comparisons are in `build/hierarchy-probe/comparisons.txt`; actual related GUI
logs include `build/related-gui-fixture/{seed,members}/actual-gui.log`.

## Persistence and remaining scope

Verification hashes native/input bytes and checks pending state/revisions without
writing. Real native-edit/save/mapping/scoped/restart regressions and all seven
matching-GUI tasks renew existing persistence evidence. The related GUI gate is
still PR #14's seed-versus-all-owned-records diagnostic, not a propagated-service
success test. The census never appears in native JSON, inputs, sidecars or exports.

The closed subset intentionally rejects broad structural components with unresolved
branches and bridges even when a more advanced verifier might prove a smaller
family. Optimistic fingerprints do not eliminate an external change-and-restore
race. JVM parser allocations remain upstream behavior within byte admission;
the documented heap estimate is not a profiler measurement. Windows/macOS and
APK/bundle census support are not claimed. No new GUI gate is needed because
native mutation behavior is unchanged.

Initial probe corrections: a test used a nonexistent ClassNode alias accessor
and was changed to the inspected ClassInfo accessor. Direct DEX absolute labels
did not trigger SelectFromDuplicates's embedded-name preference; the retained
probe now distinguishes raw first-selection from later embedded classes.dex
selection. A generic String seed initially escaped an Object-argument bridge
check; the same-name bridge refusal and regression now cover both argument keys.
No initial failure or ordinary opt-in GUI skip counts as a final pass.

Final review also added a canonical-path identity guard so a validated input
replaced by a symlink before capture cannot be silently adopted. Capture and
later verification reject the changed identity. Final gates are renewed after
this correction; earlier clean/GUI results are not substituted.
Repeated canonical inputs retain one consistent content baseline, and the
runtime captures its immutable verifier under the lifecycle lock before use.

Next milestone is PR #16's group transaction/persistence gates. Phase 5.2 remains
incomplete; local editing is separate. No architectural approval is required for
this internal prerequisite, and no propagation enablement is authorized here.
