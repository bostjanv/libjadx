# Phase 5.2 native declaration editing slice

The service accepts `POST /api/v1/edits/batch` for exact original class,
method and field declarations. `RENAME` changes a display alias and
`SET_COMMENT` upserts one native `LINE` declaration comment. Edits remain in
memory until `/api/v1/project/save` or an accepted shutdown with `save`.
The native `.jadx` file, input JARs and mapping file are untouched by a batch.

## Request and identity

The [reviewed request](../openapi/examples/edit-batch-request.json) supplies
1–64 ordered items. A body is limited to 64 KiB. `expectedSessionId` and
`expectedLogicalRevision` must appear together; omission means the operation
uses the revision admitted by its exclusive lease. Supplying stale values
returns `409 STALE_REVISION` without retrying. The public `SymbolRef` always
contains an original JVM class descriptor and, for members, the original
name and complete descriptor. A method's return type is part of that key.
Aliases never become keys, and per-input identity is rejected because Jadx's
duplicate-definition origin is unverified.

An alias is a nonreserved ASCII Java identifier up to 128 characters. This is
intentionally narrower than all Java identifiers until Unicode, `$`, and
inner-class behavior are probed across supported inputs. Constructors, class
initializers, synthetic and bridge methods, and synthetic fields/classes are
not renameable. The service rejects visible package, owner-field and
same-argument method alias collisions against the proposed final batch state.
`LINE` comments are one line, at most 4096 code points and 16 KiB UTF-8; control
characters, block-comment delimiters and other native styles are rejected. An upsert matches the native
`(nodeRef, codeRef=null, style=LINE)` key and retains comments of other styles.
If an existing project has multiple renames or LINE comments with the same
native declaration key, the edit is rejected as ambiguous without mutation.

Prevalidation resolves all original refs inside one exclusive runtime
admission and rejects malformed, duplicate, missing, ambiguous, unsupported,
stale or conflicting items before native code-data replacement. Class alias
collisions use the lightweight class catalog. Only owners requested by member
edits have their member declarations loaded; class-only edits do not load
members. Pinned Jadx may decompile a requested member owner and its dependencies,
but the service does not enumerate members of unrelated classes. For example,
a valid first rename followed by `newName:"bad-name"` returns `400
INVALID_REQUEST`, `itemErrors[0].index:1`, with revision 0, dirty false and
the native file unchanged. The
[reviewed rejection](../openapi/examples/edit-batch-rejected.json) shows the
error shape. No-op requests return `NO_CHANGE` and `SKIPPED/NO_CHANGE`, with
unchanged logical/index revisions and dirty state.

Normal effective batches use one native replacement and one Jadx code-data
notification followed by unloading generated owner code caches;
logical and index revisions each advance once. A deterministic test hook
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
old source snapshot.

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

## Review verification — 2026-09-28

The two findings in [PR #10's review](https://github.com/bostjanv/libjadx/pull/10#pullrequestreview-5332178341)
are covered by the owner-scoped catalog and no-applied staging-failure tests
above. Direct JDK 21 compilation passed for all 63 production and 48 test
source files using cached, locked dependencies. A temporary JUnit Platform
1.12.2 launcher selected 29 test classes that do not need HTTP or an actual GUI:
105 tests passed, zero failed. That includes the 33-test focused edit/native
suite, search invalidation, isolated temporary analysis, native restart/conflict
checks, operation coordination and job cancellation tests. The launcher and
classpath/selector files are temporary verification artifacts, not project state.

Commands run for direct verification (logs in `build/review-verification/`):

```bash
javac --release 21 -encoding UTF-8 -cp "$(cat /tmp/libjadx-review-classpath)" -d build/review-verification/main @build/review-verification/main-sources.txt
javac --release 21 -encoding UTF-8 -cp "build/review-verification/main:$(cat /tmp/libjadx-review-classpath)" -d build/review-verification/test @build/review-verification/test-sources.txt
javac --release 21 -cp "$(cat /tmp/libjadx-review-classpath)" -d build/review-verification /tmp/LibJadxReviewTests.java
java -Duser.home=/tmp/libjadx-review-home -Djava.awt.headless=true -cp "build/review-verification:build/review-verification/main:build/review-verification/test:$(cat /tmp/libjadx-review-classpath)" LibJadxReviewTests $(cat /tmp/libjadx-review-selected-tests)
PYTHONPATH=/tmp/libjadx-review-python /home/alice/projects/delavnica3/.venv/bin/python tests/validate-edit-contract.py
git diff --check
```

OpenAPI 3.1 validation passed for six reviewed edit examples and four newly
captured, serialized fault-injection results. The validator also accepted the
ten **retained** HTTP captures from the previous implementation; those are not
fresh HTTP evidence for this patch. `git diff --check` passed.

The attempted focused Gradle command was
`./gradlew test --offline --tests dev.libjadx.app.EditBatchServiceTest --tests dev.libjadx.app.EditBatchEndpointsTest`.
The updated sandbox prevents writing the normal Gradle cache. Retrying with
`GRADLE_USER_HOME=/tmp/libjadx-review-gradle`, `--no-daemon` and
`-Dorg.gradle.jvmargs=` failed before task execution because Gradle's lock
coordination cannot open a socket (`Could not determine a usable wildcard IP`).
A loopback bind probe confirmed `PermissionError: Operation not permitted`.
Seventeen HTTP/lifecycle/actual-GUI test classes were therefore excluded from
the direct run. Gradle `check`, fresh live HTTP/installed-distribution tests and
actual matching-GUI round trips remain to be rerun in an environment permitting
sockets. The native edit fixture was explicitly saved again through the service;
the earlier GUI evidence above has not been renewed for this patch.

The advanced Phase 5.2 gates below remain the next work; these review fixes do
not introduce a design change or claim the complete milestone passed.

## Remaining Phase 5.2 gates

Native mapping **attachment** remains available through `/project/settings`.
Strict safe Tiny v2 **export** now has a separate endpoint and evidence in
[mapping export](phase-5-mapping-export.md). It creates a new output without
saving or attaching it. Mapping import/merge remains deferred. Parameter/local identities, native
persistence and stale source behavior are not proven. Related-method
propagation is not enabled because candidate enumeration is not a proof of
complete GUI-compatible edits. See
[ADR 0001](adr/0001-defer-advanced-native-edits.md).
These remain Phase 5.2 exit criteria; this slice does not declare the overall
milestone complete.
