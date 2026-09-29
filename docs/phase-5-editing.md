# Phase 5.2 native declaration editing slice

The service accepts `POST /api/v1/edits/batch` for exact original class,
method and field declarations. `RENAME` changes a display alias and
`SET_COMMENT` upserts one native `LINE` declaration comment. Edits remain in
memory until `/api/v1/project/save` or an accepted shutdown with `save`.
The native `.jadx` file, input JARs and mapping file are untouched by a batch.
Strict mapping [export](phase-5-mapping-export.md) and
[import](phase-5-mapping-import.md) have separate contracts and evidence.
PR #13 adds snapshot-bound parameter editing for its verified signature subset;
see [scoped editing](phase-5-scoped-editing.md). Local editing and related-method
propagation remain unsupported; PR #14 preserves executable completeness
counterexamples in [related propagation evidence](phase-5-related-propagation.md).
Phase 5.2 is incomplete.

Ordinary method RENAME retains pinned Jadx's implicit candidate alias propagation.
It may change related aliases, but the existing empty affectedRefs does not certify
a complete group. Explicit propagateRelated is rejected before staging, including
false or malformed values. The accepted request schema still excludes the flag.

## Request and identity

The [reviewed request](../openapi/examples/edit-batch-request.json) supplies
1–64 ordered items. A body is limited to 64 KiB. `expectedSessionId` and
`expectedLogicalRevision` must appear together; omission in a declaration-only batch means the operation
uses the revision admitted by its exclusive lease. Scoped parameter batches require
both preconditions plus each item's current source snapshot. Supplying stale values
returns `409 STALE_REVISION` without retrying. The public `SymbolRef` always
contains an original JVM class descriptor and, for members, the original
name and complete descriptor. A method's return type is part of that key.
Aliases never become keys. Exact per-input identity is rejected because Jadx
can collapse duplicate definitions and their original input provenance is
unavailable.

An alias is a nonreserved ASCII Java identifier up to 128 characters. This is
intentionally narrower than all Java identifiers until Unicode, `$`, and
inner-class behavior are probed across supported inputs. Constructors, class
initializers, synthetic and bridge methods, and synthetic fields/classes are
not editable through this endpoint. The service rejects visible package,
owner-field and same-argument method alias collisions against the proposed
final batch state.
`LINE` comments are one line, at most 4096 code points and 16 KiB UTF-8; control
characters, format characters, line/paragraph separators, unpaired surrogates,
block-comment delimiters and other native styles are rejected. An upsert matches
the native
`(nodeRef, codeRef=null, style=LINE)` key and retains comments of other styles.
If an existing project has multiple renames or LINE comments with the same
native declaration key, the edit is rejected as ambiguous without mutation.

Prevalidation resolves all original refs inside one exclusive runtime
admission and rejects malformed, duplicate, missing, ambiguous, unsupported,
stale or conflicting items before native code-data replacement. Class alias
collisions use the lightweight class catalog. During prevalidation, only owners
requested by member edits have their member declarations loaded; class-only
edits do not load members. Pinned Jadx may decompile a requested member owner and its dependencies,
but the service does not enumerate members of unrelated classes. For example,
a valid first rename followed by `newName:"bad-name"` returns `400
INVALID_REQUEST`, `itemErrors[0].index:1`, with revision 0, dirty false and
the native file unchanged. The
[reviewed rejection](../openapi/examples/edit-batch-rejected.json) shows the
error shape. No-op requests return `NO_CHANGE` and `SKIPPED/NO_CHANGE`, with
unchanged logical/index revisions and dirty state.

Normal effective batches use one native replacement, unload generated owner
code caches, clear declaration comment attributes, then notify Jadx code-data
listeners once. Logical and index revisions each advance once. A deterministic test hook
throws before staging item 1 after item 0 was staged. The service commits only
that safe prefix and returns `PARTIAL` with `APPLIED`, `FAILED`, `SKIPPED`,
revision 0→1, and only the first alias in pending native code data. This is
fault-injection evidence, not a claim that any native consumer routinely fails
mid-batch. A staging failure before any effective edit also returns `PARTIAL`,
with `FAILED` and subsequent `SKIPPED/NOT_EXECUTED` items. A preceding no-op
retains `SKIPPED/NO_CHANGE`; no native replacement or publication occurs, and
logical/index revisions and dirty state are unchanged, including already-dirty
projects. The [no-applied failure](../openapi/examples/edit-batch-partial-no-applied.json)
example shows that distinction. A reload failure after repository replacement is a hard failure
and leaves the runtime `FAILED`; it is never presented as a successful batch.
The [success](../openapi/examples/edit-batch-applied.json),
[no-op](../openapi/examples/edit-batch-no-change.json), and
[partial](../openapi/examples/edit-batch-partial.json) examples describe these
semantics.

## Pinned Jadx and persistence evidence

Jadx `v1.5.6` is pinned to source commit
`28ff15e4ae69950aebea110a13e5ab895d234dfc`. The earlier
`4c0ac37699aa8c9803f1c73cfaacd9205acb044b` is the annotated tag object,
not the source commit. The audited
[`JadxNodeRef`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/data/impl/JadxNodeRef.java)
constructs class refs from raw dotted names, method refs from original full
short IDs and field refs from original name and type. The
[`JadxCodeRename`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/data/impl/JadxCodeRename.java)
identity is node plus code ref; the
[`JadxCodeComment`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/data/impl/JadxCodeComment.java)
does not use that equality rule, so the adapter matches comments explicitly.

This extra unload is necessary because pinned
[`JadxDecompiler.reloadCodeData()`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/JadxDecompiler.java)
only notifies code-data listeners. A pre-decompiled class otherwise returned
old Java after a successful edit. `EditBatchEndpointsTest` now decompiles
before and after the batch and checks the updated aliases/comment plus a stale
old source snapshot. Caches are unloaded before listener replay so attached
mapping comments can be reapplied after unloading clears their attributes.
PR #12 also clears declaration `CODE_COMMENTS` on cold class/method/field nodes:
pinned `ClassNode.unloadCode()` skips that cleanup for a `NOT_LOADED` class.
Without the explicit cleanup, repeated replay duplicates attached comments.
The real-Jadx attached/native comment regression and pinned source evidence
are recorded in [mapping import](phase-5-mapping-import.md).

`EditBatchEndpointsTest` exercises real Jadx on `SymbolFixture.java` and on
the two-relative-input `sample.jar.jadx` fixture. It proves explicit save,
pending edit export, unchanged bytes before save, and retention of unknown
root, codeData, edited rename/comment and untouched entry fields. The native
file retains `sample.jar`, `second.jar` and `sample.tiny` relative paths.
`EditBatchServiceTest` checks distinct native keys for the two methods named
`value` with `()I` and `()Ljava/lang/String;` descriptors and a committed
prefix under staging fault injection. `SearchInvalidationTest`,
`DecompileEndpointsTest` and `ReferenceEndpointsTest` apply this service's
edits and check revised search/source/reference snapshots. `SearchBuildJobTest`
holds a worker with a queued complete-index job, applies an edit, then proves
the queued job fails `STALE_REVISION`; class cursors
become stale while a prevalidation failure leaves them usable. Search index
refresh remains lazy and coverage remains explicit.

The review regressions use the owned multi-class `EditOwner.java` fixture.
`EditBatchServiceTest` checks unrelated classes' pinned Jadx processing state
for class/method/field edits that apply, make no change or reject a later invalid
item. Effective edits are also checked before publication unloads code caches,
so unloading cannot hide eager processing. Class and same-owner method collision
tests retain the safety checks. Fault injection before item 0 and after a no-op
checks item indexes/statuses/messages, pending native data, revisions, dirty
state and zero additional Jadx reloads for clean and already-dirty projects.
`tests/validate-edit-contract.py` validates these serialized service results
separately from the actual HTTP responses captured by `EditBatchEndpointsTest`.

`nativeEditGuiRoundTripTest` prepares a raw-input native project through the
service, explicitly saves it, opens it with the matching Jadx 1.5.6 GUI,
resaves through the GUI, then reopens it headlessly. The reverse test checks
class/method/field native keys, three retained declaration comments, and
emitted Java containing all aliases/comments. The GUI automation proves
load and save compatibility; it does not assert a screenshot or inspect GUI
widgets for every declaration. The installed distribution test starts the
production launch script without a GUI JAR, edits, saves and restarts.

## Historical PR #10 verification — 2026-09-28

The counts in this section apply to native-edit review head
`aa48efc70ab5a2d5844f22aa6aaced8aa0f14362`. Current regression results, including
mapping export/import, are recorded separately below.

The two findings in [PR #10's review](https://github.com/bostjanv/libjadx/pull/10#pullrequestreview-5332178341)
are covered by the owner-scoped catalog and no-applied staging-failure tests
above. Direct JDK 21 compilation passed for all 63 production and 48 test
source files using cached, locked dependencies. A temporary JUnit Platform
1.12.2 launcher selected 29 test classes that do not need HTTP or an actual GUI:
105 tests passed, zero failed. That includes the 33-test focused edit/native
suite, search invalidation, isolated temporary analysis, native restart/conflict
checks, operation coordination and job cancellation tests. The launcher and
classpath/selector files are temporary verification artifacts, not project state.

In that restricted run, OpenAPI 3.1 validation passed for six reviewed edit
examples and four newly captured, serialized fault-injection results. The validator also accepted the
ten **retained** HTTP captures from the previous implementation; those are not
fresh HTTP evidence for that review head. `git diff --check` passed.

The attempted focused Gradle command was
`./gradlew test --offline --tests dev.libjadx.app.EditBatchServiceTest --tests dev.libjadx.app.EditBatchEndpointsTest`.
The earlier sandbox prevented writing the normal Gradle cache. Retrying with
`GRADLE_USER_HOME=/tmp/libjadx-review-gradle`, `--no-daemon` and
`-Dorg.gradle.jvmargs=` failed before task execution because Gradle's lock
coordination cannot open a socket (`Could not determine a usable wildcard IP`).
A loopback bind probe confirmed `PermissionError: Operation not permitted`.
Seventeen HTTP/lifecycle/actual-GUI test classes were therefore excluded from
that direct run. The native edit fixture was explicitly saved again through the
service, but that run did not renew the GUI evidence. The socket-capable rerun
below completes the previously blocked verification.

### Socket-capable rerun — 2026-09-28

The socket-capable rerun completed the verification requested in the
[follow-up review](https://github.com/bostjanv/libjadx/pull/10#pullrequestreview-5337529071)
on exact head `aa48efc70ab5a2d5844f22aa6aaced8aa0f14362`, using JDK
`21.0.12.1` and Gradle `8.14.3`. No production code, public contract or dependency
locks changed. `clean` removed the retained HTTP captures before this run, so
all responses below were freshly generated on this head.

| Command | Outcome |
|---|---|
| `./gradlew clean check --offline --rerun-tasks` | Passed in 3m 28s; 46 test classes, 190 tests discovered, 187 passed, zero failures/errors, three opt-in GUI skips. |
| `JADX_GUI=/tmp/libjadx-rerun-jadx-1.5.6/bin/jadx-gui ./gradlew guiRoundTripTest rawGuiRoundTripTest nativeEditGuiRoundTripTest --offline` | Passed in 47s; all three previously skipped tests ran, one per task, zero failures/errors/skips. The ordinary test task reused the successful clean run; all six GUI save/test tasks executed. |
| `/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-edit-contract.py` | OpenAPI 3.1 valid; six reviewed examples, ten fresh live HTTP responses and four fresh injected service results validated. HTTP statuses: 200/400/403/404/409/415/422/429/503. |
| `/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-search-contract.py` | OpenAPI 3.1 valid; four reviewed examples and 33 fresh live HTTP responses validated. HTTP statuses: 200/202/400/403/409/415/422/429/503. |
| `git diff --check` | Passed after recording these results. |

The full check rebuilt `installDist` and passed all four
`StandaloneDistributionTest` cases against newly launched packaged services,
including native edits, explicit save/restart, search jobs and stale cursors.
HTTP, lifecycle, concurrency/cancellation, malformed input, stale revisions,
native external-change/restart and owner-scoping/staging-failure regressions
ran through the normal Gradle suite. Together with the dedicated GUI tasks,
all 190 distinct tests executed successfully; no test remains unexecuted from
this suite.

The GUI executable came from the upstream `v1.5.6` release archive,
`jadx-1.5.6.zip`; `bin/jadx --version` returned `1.5.6`. Its downloaded SHA-256
`545ea2be9c242511bc145755cf4bda2485ade42966e096f8b4d3da2a230e8974`
matched the digest published in the upstream release metadata. Xvfb and
xdotool drove actual GUI open/save operations. The GUI-resaved native, raw-input
and declaration-edit fixtures were then reopened headlessly. The edit test
verified class/method/field native rename keys, all three declaration comments
and emitted Java after the matching GUI resave. These checks establish the
existing fixture round trips, with the GUI inspection limits described above.

That rerun generated reports in
`build/reports/tests/{test,guiRoundTripTest,rawGuiRoundTripTest,nativeEditGuiRoundTripTest}/index.html`,
with machine-readable results under `build/test-results/`, captures under
`build/edit-contract-responses/`, `build/edit-service-results/` and
`build/search-contract-responses/`, and GUI-resaved projects under
`build/native-roundtrip-fixture/`, `build/raw-roundtrip-fixture/` and
`build/edit-gui-fixture/`. These build paths are reused: subsequent clean checks
replace the historical artifacts with results from the newer code.
The temporary Python 3.14 environment used PyYAML `6.0.3`, jsonschema `4.26.0`
and openapi-spec-validator `0.9.0`. No remote CI, Windows or macOS result is
claimed.

The advanced Phase 5.2 gates below remain the next work; these review fixes do
not introduce a design change or claim the complete milestone passed.

## PR #12 regression verification

The initial mapping-import implementation at
`dc89572d018d790e692c2e90763187f4acb54715` passed a fresh
`./gradlew clean check --offline --rerun-tasks`: 267 tests discovered, 262 passed,
five opt-in GUI skips and zero failures/errors. A subsequent matching Jadx 1.5.6
GUI run reran the ordinary suite and executed all five dedicated GUI tests
without failures or skips, including `nativeEditGuiRoundTripTest`. Together,
all 267 distinct tests executed successfully. The edit validator accepted six
examples, ten fresh live HTTP responses and four injected service results.
These are subsequent regression results, not replacements for the historical
PR #10 head's counts.

The subsequent P2 review fix extends import preflight to detect collisions
caused by untouched descendants of a renamed outer class. Pinned-Jadx probes
and clean/dirty service regressions cover inner and nested descendants with
attached or native aliases, and the GUI reverse test checks the untouched
inner's qualified alias after resave. Exact final-code commands, environment
versions, current counts, fixture hashes and artifact paths are in
[PR #12 validation](pr-12-review.md); those gates are renewed after the fix.

## Remaining Phase 5.2 gates

Native mapping **attachment** is available through `/project/settings`.
Strict Tiny v2 **export** creates a new output without saving or attaching it;
bounded conflict-safe **import** stages native edits without saving or attaching
its source. Their separate evidence is linked above.

PR #13 adds original positional parameter identities with source-snapshot
admission and matching-GUI native persistence for verified plain signatures;
see [scoped editing](phase-5-scoped-editing.md). Local editing remains unsupported
after the merged-SSA mode-variation probe. Related-method propagation is not enabled
because candidate enumeration is not a proof of
complete GUI-compatible edits. See
[ADR 0001](adr/0001-defer-advanced-native-edits.md).
These remain Phase 5.2 exit criteria; this slice does not declare the overall
milestone complete.
