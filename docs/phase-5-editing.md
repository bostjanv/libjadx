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
stale or conflicting items before native code-data replacement. For example,
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
mid-batch. A reload failure after repository replacement is a hard failure
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

`nativeEditGuiRoundTripTest` prepares a raw-input native project through the
service, explicitly saves it, opens it with the matching Jadx 1.5.6 GUI,
resaves through the GUI, then reopens it headlessly. The reverse test checks
class/method/field native keys, three retained declaration comments, and
emitted Java containing all aliases/comments. The GUI automation proves
load and save compatibility; it does not assert a screenshot or inspect GUI
widgets for every declaration. The installed distribution test starts the
production launch script without a GUI JAR, edits, saves and restarts.

## Remaining Phase 5.2 gates

Native mapping **attachment** remains available through `/project/settings`.
Mapping import/export is deferred: the pinned exporter can delete an existing
output and catches failures internally, so transactional filesystem safety
and omission diagnostics are not proven. Parameter/local identities, native
persistence and stale source behavior are not proven. Related-method
propagation is not enabled because candidate enumeration is not a proof of
complete GUI-compatible edits. See
[ADR 0001](adr/0001-defer-advanced-native-edits.md).
These remain Phase 5.2 exit criteria; this slice does not declare the overall
milestone complete.
