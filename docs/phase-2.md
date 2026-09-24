# Phase 2 native lifecycle notes

The native project repository owns the in-memory `.jadx` JSON tree. Explicit save
updates that native file and preserves unrelated GUI and unknown root fields.
For raw input, no project file exists until `POST /api/v1/project/save` supplies
an unused `.jadx` target under an allowed root. A later save uses that path.

The service checks the project and referenced mapping file content against the
baseline observed at load or last save. A staged mapping change also checks
the new mapping file against the content observed when it was selected. An
external change returns `EXTERNAL_MODIFICATION_CONFLICT`; pending code data and
the mapping path can be recovered from the transient export response. Reload
requires `discardUnsaved: true` when the in-memory state is dirty.

`sessionId` and logical/index counters exist for this process only. The
persisted identity hashes native project, mapping and input bytes. Inputs over
32 MiB are hashed in a background virtual thread. During hashing, the identity
is null and its state is `PENDING`; a read failure sets `FAILED`.
Temporary analysis produces a source snapshot ID and settings fingerprint.
Cursor validation is deferred until the search/index endpoints exist.

The mapping path is the currently supported native persistent setting. A
successful change rebuilds Jadx before becoming active and retains unsaved
renames/comments. Decompilation mode can be used only in isolated temporary
analysis; the pinned native project model does not persist it. Temporary
instances use a deep code-data copy and are limited to one active instance.
A failed mapping rebuild leaves the previous analysis, revisions and unsaved
edits active; the failed replacement instance is closed.

Save uses ordinary native JSON writing semantics. If a process stops during a
write, the file may be truncated; users should keep their own backup for
important projects. External change detection is optimistic and cannot prevent
a simultaneous write by an uncooperative GUI process.

Jadx 1.5.6 source evidence: `JadxArgs.setCodeData`,
`JadxArgs.setDecompilationMode`, `JadxArgs.setUserRenamesMappingsPath`, and
`JadxDecompiler.reloadCodeData` in the pinned
[`jadx-core` source](https://github.com/skylot/jadx/tree/4c0ac37699aa8c9803f1c73cfaacd9205acb044b/jadx-core/src/main/java/jadx/api).
