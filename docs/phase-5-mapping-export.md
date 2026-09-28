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

The implementation remains gated on strict source validation, bounded
allocation, output verification, no-clobber publication, actual matching GUI
resave and HTTP contract tests. Mapping import and scoped/propagated renames
remain separate work. No export support is inferred from these probes alone.
