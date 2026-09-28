# Phase 5.2: Tiny v2 mapping export evidence

The feasibility probe uses Jadx 1.5.6 commit
`28ff15e4ae69950aebea110a13e5ab895d234dfc` and mapping-io 0.8.0.
It does not call `MappingExporter.exportMappings`.

| Observation | Executable evidence / pinned source |
|---|---|
| Attached aliases plus unsaved class/method/field aliases and LINE comments survive serialization and fresh-engine loading | `JadxMappingExportProbeTest.declarationsAndLineCommentsRoundTripWithAttachedMappingsAndUnsavedEdits`; original JVM keys, including field type, remain independent of aliases. |
| Full return descriptors distinguish `value()I` and `value()Ljava/lang/String;` | `returnTypeOnlyOverloadsRetainFullDescriptors`, owned `SymbolFixtureSupport.returnTypeClashJar`. |
| Native aliases override attached aliases | Observed by the first probe; `ApplyMappingsPass` runs before `RenameVisitor` / `UserRenames`. |
| Attached and native LINE comments are additive | `attachedAndNativeCommentsAreAdditiveRatherThanReplacement`; joining in that order with a newline reproduces the exact Java in a fresh engine. Replacing the attached comment would lose data. |
| The loaded mapping tree loses duplicate/unknown input records | `readerNormalizesDuplicateDeclarationsAndIgnoresUnknownRecords`; the exporter must also verify the baseline file before trusting a loaded tree. |
| Inversion changes source namespace; multiple destinations log a load-pass failure with no tree | `namespaceInversionAndMultipleDestinationsCannotBeExportedAsVerifiedOriginalKeys`; both sources yield strict 422 export rejection. |
| Args/locals have mapping-tree structures but lack this slice's verified native identity | `codecHasNoNativeCommentStyleAndRetainsUnverifiedArgumentAndVariableStructures`; reject these structures, native code refs, non-LINE styles and unresolved declarations. |

Pinned source files inspected: `jadx-plugins/jadx-rename-mappings/src/main/java/jadx/plugins/mappings/{RenameMappingsData.java,load/LoadMappingsPass.java,load/ApplyMappingsPass.java,save/MappingExporter.java}`;
`jadx-core/src/main/java/jadx/api/data/impl/{JadxNodeRef,JadxCodeRename,JadxCodeComment}.java`;
`jadx-core/src/main/java/jadx/core/dex/visitors/{rename/UserRenames,AttachCommentsVisitor}.java`;
`jadx-core/src/main/java/jadx/core/codegen/utils/CodeGenUtils.java`.
Commit-pinned links are recorded in the PR work order and compatibility document.

Codec signatures verified against the resolved 0.8.0 JAR:
`MappingReader.read(Reader, MappingFormat, MappingVisitor)`,
`MappingWriter.create(Writer, MappingFormat)` and
`MappingTreeView.accept(MappingVisitor)`. After copying a tree, a new
`visitNamespaces` pass is needed before overlay visits; otherwise its visitor
namespace map has been reset. Tiny v2 has one comment value per declaration
and no Jadx comment-style field. `RenameMappingsData.getTree(RootNode)` is
available in the already packaged headless mapping plugin.

## Implemented boundary

`POST /api/v1/project/mappings/export` requires the four fields in the reviewed
request example. `MappingExportService` obtains one exclusive read/export
lease, checks session/revision before source capture, and never commits native
data or publishes a cache revision. `NativeProjectRepository` checks accepted
native/mapping fingerprints, bounds and copies the current native code data,
and captures the exact accepted mapping bytes. A final baseline check precedes
publication. The source is the current logical state, not an original-input census.

`JadxMappingExportAdapter` checks Tiny 2.0 record structure before using the
reader, rejects duplicate/unknown records, and compares the baseline parse to
the loaded tree. It preserves a single namespace pair and declaration aliases
and LINE comments. It uses `RootNode.resolveRawClass` and original short IDs;
`ClassNode.getMethods/getFields` return existing metadata, unlike the public
`JavaClass` methods which may decompile. Only implicated owners are inspected,
with at most 10000 member metadata entries per owner. No source is generated.

Unsupported original keys, incompatible effective aliases, namespace inversion
or other mapping-plugin overrides, extra namespaces, metadata, arguments,
locals, native code refs, duplicate native keys and non-LINE comments return
422 with a bounded diagnostic category. The first detected unsupported element
is reported; its count is a detected count, not an exhaustive omission census.
Raw files with malformed/unknown records cannot silently become a success.
Pinned Jadx can log a mapping prepare-pass failure and continue loading with
no attached tree (proved for multiple destination namespaces). The exporter
rejects the validated source itself; `load()` returning is not acceptance
evidence. Failures that stop project initialization retain the existing
FAILED/not-ready lifecycle response.

Limits: 10000 output declaration/comment records, 4 MiB accepted mapping and
encoded output, 16384 characters per string, 4096 path characters, 64 KiB HTTP
request, and a conservative 16 MiB intermediate budget. Native project baseline
hashing is capped at 16 MiB. The budget charges 1024 bytes per parsed entry,
2048 bytes per native element, 16 bytes per copied character, source buffers
and output buffers; it is an allocation estimate, not a JVM heap measurement.
The memory ceiling may reject a request below an independent byte/entry cap.
Receipt comment counts count Tiny comment records, including joined comments.
Counts and digest come from reparsed owned staging bytes.

`SafeMappingOutput` rejects protected input/native/mapping paths, existing
targets, symlink components and disallowed real parents. It stages a uniquely
created regular file, uses mode 0600 on POSIX, forces/closes it, verifies it,
and uses `Files.createLink` for atomic no-clobber publication. Existing-name
failure is exercised on the actual destination filesystem with an owned name.
Failure removes only the owned staging inode. SecureDirectoryStream cleanup
can still reach that inode if its directory has moved. A replaced staging
path is retained. A failure after link creation returns 500 with
`published:true` and the actual target path, never deleting the destination.

Parent identities are rechecked before and after publication; staging identity
and bytes are rechecked before the link. Java's portable API provides no
directory-relative hard-link primitive, so a hostile directory/inode change
in the final syscall window is not eliminated. Content fingerprints likewise
do not lock out an external writer after the last baseline check. Windows and
macOS have not been verified; filesystems without usable identity/hard-link
behavior fail closed. These are explicit local filesystem limits, not a claim
of concurrent GUI collaboration or complete TOCTOU protection.

The attached-comment regression required a narrow existing-runtime fix:
unload owner caches **before** code-data listeners reapply mapping comments.
The previous order cleared those comments after replay. This does not change
admission, persistence, revisions or export behavior.

## Verification commands and artifacts

```bash
./gradlew test --offline --tests 'dev.libjadx.*Mapping*' --tests 'dev.libjadx.app.*Edit*' --tests dev.libjadx.app.OpenApiDocumentTest
./gradlew clean check --offline --rerun-tasks
JADX_GUI=/tmp/libjadx-rerun-jadx-1.5.6/bin/jadx-gui ./gradlew guiRoundTripTest rawGuiRoundTripTest nativeEditGuiRoundTripTest mappingExportGuiRoundTripTest --offline --rerun-tasks
./gradlew installDist --offline
/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-mapping-export-contract.py
/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-edit-contract.py
/tmp/libjadx-rerun-contract-venv/bin/python tests/validate-search-contract.py
git diff --check origin/main...HEAD
```

The initial five feasibility probes passed. A subsequent focused run passed
the mapping/edit regressions and validated 27 fresh HTTP responses independently
(200/400/403/409/415/422/429/500/503). All four actual matching-GUI round trips
passed during implementation; this is preliminary evidence, not substituted
for final-head checks. Final-head command outcomes, totals and capture count
are recorded in the PR description. Reports are in `build/test-results` and
`build/reports/tests`; captures in `build/mapping-export-contract-responses`;
the GUI native copy, receipt and exported Tiny file in
`build/mapping-export-gui-fixture`. Its expected Tiny digest is
`sha256:2a56f240bb903a815dd8e6455badc3686eef23d8a3453a3cfc67aaeb8644351a`.
The GUI executable reports 1.5.6. Its resaved mapping-only native project has
empty code data; headless reopen verifies all aliases and six comment texts
by original class/member IDs. Packaged-service checks launch `installDist`
without GUI JARs and verify export retains dirty/native state until explicit save.

Mapping import and scoped/propagated renames remain separate work. This finishes
only the export sub-slice, not all Phase 5.2 or a release gate for every input.
