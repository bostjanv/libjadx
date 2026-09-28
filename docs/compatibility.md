# Compatibility and pinned dependencies

Status: Phase 5.2 native editing slice, updated 2026-09-28.

## Jadx pin

| Component | Pin | Evidence |
|---|---|---|
| Jadx stable release | `1.5.6` | GitHub release is marked latest, published 2026-07-10, and is not a prerelease: <https://github.com/skylot/jadx/releases/tag/v1.5.6> |
| Jadx source commit | `28ff15e4ae69950aebea110a13e5ab895d234dfc` | The `v1.5.6` annotated tag object resolves to this commit; verified with GitHub Git refs and tag APIs on 2026-09-27. |
| Jadx annotated tag object | `4c0ac37699aa8c9803f1c73cfaacd9205acb044b` | The earlier handoff called this the source commit. It is the tag object and cannot be used as a source-file commit URL. |
| Jadx release source | [`v1.5.6`](https://github.com/skylot/jadx/releases/tag/v1.5.6) | Latest stable at implementation start; published 2026-07-10, not a prerelease |
| Maven coordinates | `io.github.skylot:jadx-core:1.5.6` and pinned headless runtime plugins; `io.github.skylot:jadx-gui:1.5.6` (test scope only) | The plugin set follows the matching CLI build file at the pinned source commit; resolved artifacts are in `gradle.lockfile` |
| Java toolchain | JDK 21 | Jadx 1.5.6's core compiles to Java 11 (`buildSrc/src/main/kotlin/jadx-java.gradle.kts`); JDK 21 is selected as the build/runtime baseline for LibJadx |
| Gradle | 8.14.3 | Pinned wrapper distribution with SHA-256 `bd71102213493060956ec229d946beee57158dbd89d0e62b91bca0fa2c5f3531`; selected for Kotlin DSL support and Java 21 compatibility |
| Embedded HTTP server | Eclipse Jetty `12.1.13` (`jetty-server`, `jetty-ee10-servlet`) | Official [Jetty downloads](https://jetty.org/download.html) list 12.1.13; the [12.1 documentation](https://jetty.org/docs/jetty/12.1/index.html) identifies this stable line as Java 17 based. Runtime baseline remains JDK 21. |
| JSON/YAML mapper | Jackson BOM `2.21.7` | [Jackson 2.21 release notes](https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.21) list 2.21.7 on 2026-09-21 and designate the 2.21 line LTS; used for HTTP JSON and YAML config. |

The 1.5.6 Git tag is `v1.5.6`, a stable, signed upstream release. No build-time dependency points to `master`. The `jadx-gui` dependency is test-only while headless native-project feasibility is being investigated; production code must not depend on a GUI runtime unless Phase 0 proves a headless approach requires it and the architecture is explicitly reviewed.

The installed distribution includes the 1.5.6 analysis, dex, Java input/conversion, Smali, mapping, Kotlin metadata, XAPK, AAB, APKM and APKS plugins listed in the [pinned CLI build](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-cli/build.gradle.kts). `jadx-core` alone has no input loaders in the distribution. `StandaloneDistributionTest` launches the installed service against a real native-project fixture and checks class loading and cursor behavior across two processes without the test-only GUI classpath.

## Source evidence inspected

The source archive was fetched from the `v1.5.6` tag and inspected at the commit above. Commit-pinned source links:

- [`JadxDecompiler.java`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/JadxDecompiler.java): public constructor, `load()`, `getClasses()`, `getClassesWithInners()`, original/alias class lookup and `reloadCodeData()` APIs; its class-level documentation shows the library loading/decompile flow.
- [`JadxArgs.java`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/JadxArgs.java): public `setCodeData(ICodeData)` API.
- [`JavaClass.java`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/JavaClass.java) and [`JavaMethod.java`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/JavaMethod.java): Java code and metadata, Smali, member listing and use references.
- [`JavaField.java`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/JavaField.java), [`MethodInfo.java`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/info/MethodInfo.java), [`FieldInfo.java`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/info/FieldInfo.java), and [`TypeGen.java`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/codegen/TypeGen.java): original member names and raw type signatures used by the isolated symbol adapter.
- [`JadxProject.java`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-gui/src/main/java/jadx/gui/settings/JadxProject.java): `loadProjectData(Path)` and `save()`; `saveAs(Path)` uses `MainWindow`/GUI cache services. `buildGson` applies native relative-path and code-data adapters.
- [`ProjectData.java`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-gui/src/main/java/jadx/gui/settings/data/ProjectData.java): native project fields include input files, tree expansions, `JadxCodeData`, tabs, mappings path, cache directory, live reload, search fields and plugin options.
- [`jadx-java.gradle.kts`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/buildSrc/src/main/kotlin/jadx-java.gradle.kts): Jadx main sources target Java 11; `settings.gradle.kts` requires Java 11 or newer to build.
- [`jadx-cli/build.gradle.kts`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-cli/build.gradle.kts): the pinned CLI's headless runtime plugin set used by the standalone LibJadx distribution.
- [`ICodeInfo.java`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/ICodeInfo.java), [`ICodeMetadata.java`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/metadata/ICodeMetadata.java), [`NodeDeclareRef.java`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/metadata/annotations/NodeDeclareRef.java), and [`NodeEnd.java`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/metadata/annotations/NodeEnd.java): exact Java string, code-position map, declaration node and unattributed end markers used by `JadxSourceAdapter`.
- [`ClassGen.java`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/codegen/ClassGen.java): emits `NodeEnd` after method body closing braces; lexical brace matching is required to associate an end marker with a specific original method.
- [`JadxError.java`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/attributes/nodes/JadxError.java) and [`JadxCommentsAttr.java`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/attributes/nodes/JadxCommentsAttr.java): pinned internal class/method diagnostics; exception causes and stack traces are not copied to HTTP responses.

## Non-Jadx build dependencies

| Dependency | Version | Scope / reason |
|---|---:|---|
| Jetty server and EE10 servlet | 12.1.13 | Embedded HTTP listener and servlet routing; production runtime |
| Jackson BOM / databind / YAML | 2.21.7 | API JSON responses and optional YAML configuration |
| JUnit Jupiter | 5.12.2 | Phase 0 executable feasibility probes |
| JUnit Platform Launcher | 1.12.2 | Gradle test runtime |
| RE2/J | 1.8 | Linear-time source regex; pinned in `gradle.lockfile`, included in the standalone runtime with its Go/RE2 license notice under `licenses/RE2J-LICENSE` |

Resolved transitive versions are recorded in [gradle.lockfile](../gradle.lockfile) after initial resolution. Jadx artifacts are pinned in Gradle declarations and locks. Review and regenerate that lock deliberately when changing dependencies.

## Current feasibility status

The public-core smoke probe, in-memory class rename/comment, native project JSON round-trip, unknown-field retention, and matching-GUI save/reopen have executable probes. Remaining P0.3 topics are listed with bounded follow-up probes in [feasibility-matrix.md](feasibility-matrix.md). Phase 2 adds native save/reload/conflict detection, process-scoped revisions, mapping-path rebuilds and isolated temporary decompilation mode. Phase 4.1 adds Jadx-visible class listing and original class/method/field resolution. Phase 4.2 adds class-oriented Java source, validated token annotations and verified method excerpts. Source-only internals are isolated in `JadxSourceAdapter`. Phase 5.1 adds memory-only class/member/emitted-Java search and a complete-index job. Phase 5.2 now includes validated native declaration editing; advanced edit gates remain open.

The Phase 5.2 declaration editing slice uses pinned `JadxNodeRef.forJavaNode`,
`JadxCodeRename`, `JadxCodeComment`, and `JadxDecompiler.reloadCodeData()`.
The last API only notifies listeners in 1.5.6; LibJadx also unloads generated
owner code caches after a committed edit so a previously read class cannot
return stale Java.
Pinned `JavaClass.getMethods()` and `getFields()` call `load()`, which can
decompile their owner. Review fixes restrict these calls to requested member
owners and use lightweight class metadata for class edits/collisions.
The owned `EditOwner.java` regression checks unrelated classes before edit
publication as well as after effective, rejected and no-op batches.
Real HTTP, installed-distribution and matching-GUI resave checks are recorded
in [editing evidence](phase-5-editing.md). Strict Tiny v2 export is isolated in
`JadxMappingExportAdapter`, using the already installed headless
`jadx-rename-mappings:1.5.6` plugin and `net.fabricmc:mapping-io:0.8.0` as
explicit compile dependencies. No resolved runtime version changed. The
codec's published POM specifies Apache 2.0; `licenses/MAPPING-IO-LICENSE` is
bundled in `installDist`. Its license was checked against mapping-io source
commit `5eb15ddbd3f1d8fbecccb79392192ff86b5c65f0`, whose `gradle.properties`
declares 0.8.0 (a version-bearing source commit, not a claimed release build
commit). Bounded Tiny v2 import uses the same strict parser and pinned declaration metadata;
see [import evidence](phase-5-mapping-import.md). No dependencies or locks changed. See [export evidence](phase-5-mapping-export.md)
and [ADR 0001](adr/0001-defer-advanced-native-edits.md).

Code-data replay unloads owners before notifying listeners. Pinned
`ClassNode.deepUnload` clears `CODE_COMMENTS`; the old order erased attached
mapping comments reapplied by `ApplyMappingsPass`. The export regression
compares exact Java before export and after fresh-engine mapping loading.

For Phase 5.1, the exact 1.5.6 Maven source JAR was inspected for public
`JavaClass.getOriginalTopParentClass`, `getTopParentClass`, `getCodeInfo`,
`getMethods`, `getFields`, `getAccessInfo` and `isNoCode`. The audited
`JadxSymbolAdapter` continues to isolate `MethodInfo`, `FieldInfo` and
`TypeGen.signature` for full original member descriptors. See
[Phase 5.1 search evidence](phase-5-search.md).

## Reproducing Phase 0 probes

```bash
./gradlew test
JADX_GUI=/path/to/jadx-gui ./gradlew guiRoundTripTest
JADX_GUI=/path/to/jadx-gui ./gradlew rawGuiRoundTripTest
```

The GUI task requires the matching 1.5.6 GUI executable, `xvfb-run` and `xdotool`. The base fixture under `tests/fixtures/native-project/` was created by Jadx 1.5.6 `jadx-gui`; its second relative input, Tiny v2 mapping reference and future JSON member were added for the headless adapter probe and loaded by the matching GUI.

Phase 4.3 proves bounded method/field/class relationships and missing external
method descriptors on the owned JAR fixture. Optional source sites verify both
caller and target. Graph mutation at unchanged logical revision is tested.
See [reference evidence and limits](phase-4-references.md).

The import replay regression additionally inspects pinned `JavaClass.unload`,
`ClassNode.unloadCode`, `ClassInfo.makeAliasRawFullName`, `AttrNode.remove`,
`ApplyMappingsPass` and `AttachCommentsVisitor`. Cold `NOT_LOADED` owners skip
unloading their declaration attributes; the adapter now clears CODE_COMMENTS
before mapping/native replay on all visible declaration nodes, avoiding repeated
attached comments across edits. This does not generate source. The import
retains explicit native save and the existing post-replacement FAILED lifecycle.
The independent validator environment is Python 3.14.4, PyYAML 6.0.3,
jsonschema 4.26.0 and openapi-spec-validator 0.9.0 (same environment as PR #11).
Exact direct validation packages are pinned in `tests/requirements-contract.txt`.

## PR #13 scoped variable evidence

No pin, toolchain, dependency or lock changed. Exact pinned source was fetched
into `/tmp/libjadx-pr13-source` from source commit
`28ff15e4ae69950aebea110a13e5ab895d234dfc`. The audited `JadxCodeRef`,
`JadxCodeRename`, `JavaVariable`, `VarNode`, `CodeRenameVisitor`, `MethodNode`,
`MethodGen`, `ProcessClass`, GUI `JVariable` and `JadxProject` establish the
consumer and serialization path. `MethodNode.initArguments` excludes `this`
from `getArgRegs` and counts wide arguments once; `MethodGen` emits definitions
in that order. Mutable argument registers unload after generation, so production
uses verified definitions in the same emitted metadata, without retaining
`DONT_UNLOAD_CLASS` (that flag is used only in probes).

Parameters use native original-method `MTH_ARG` keys and source-snapshot admission.
AUTO/RESTRUCTURE plain concrete signatures are the supported subset. Generic,
annotated, skipped/transformed, bodyless and synthetic/special forms fail closed.
Existing GUI VAR renames on the same method are conservatively ambiguous.
Merged locals change emitted SSA metadata under SIMPLE, and FALLBACK emits no
variable declarations; local renames remain unsupported. See
[scoped editing evidence](phase-5-scoped-editing.md) for commands and matching-GUI
results. GUI serialization drops unknown JSON fields, as established in Phase 0;
LibJadx's native codec preserves them before the GUI resave.
