# Compatibility and pinned dependencies

Status: Phase 4.1 symbol lookup, updated 2026-09-25.

## Jadx pin

| Component | Pin | Evidence |
|---|---|---|
| Jadx stable release | `1.5.6` | GitHub release is marked latest, published 2026-07-10, and is not a prerelease: <https://github.com/skylot/jadx/releases/tag/v1.5.6> |
| Jadx source commit | `4c0ac37699aa8c9803f1c73cfaacd9205acb044b` | Annotated `v1.5.6` tag resolved through GitHub's Git refs API on 2026-09-24 |
| Jadx release source | [`v1.5.6`](https://github.com/skylot/jadx/releases/tag/v1.5.6) | Latest stable at implementation start; published 2026-07-10, not a prerelease |
| Maven coordinates | `io.github.skylot:jadx-core:1.5.6` and pinned headless runtime plugins; `io.github.skylot:jadx-gui:1.5.6` (test scope only) | The plugin set follows the matching CLI build file at the pinned source commit; resolved artifacts are in `gradle.lockfile` |
| Java toolchain | JDK 21 | Jadx 1.5.6's core compiles to Java 11 (`buildSrc/src/main/kotlin/jadx-java.gradle.kts`); JDK 21 is selected as the build/runtime baseline for LibJadx |
| Gradle | 8.14.3 | Pinned wrapper distribution with SHA-256 `bd71102213493060956ec229d946beee57158dbd89d0e62b91bca0fa2c5f3531`; selected for Kotlin DSL support and Java 21 compatibility |
| Embedded HTTP server | Eclipse Jetty `12.1.13` (`jetty-server`, `jetty-ee10-servlet`) | Official [Jetty downloads](https://jetty.org/download.html) list 12.1.13; the [12.1 documentation](https://jetty.org/docs/jetty/12.1/index.html) identifies this stable line as Java 17 based. Runtime baseline remains JDK 21. |
| JSON/YAML mapper | Jackson BOM `2.21.7` | [Jackson 2.21 release notes](https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.21) list 2.21.7 on 2026-09-21 and designate the 2.21 line LTS; used for HTTP JSON and YAML config. |

The 1.5.6 Git tag is `v1.5.6`, a stable, signed upstream release. No build-time dependency points to `master`. The `jadx-gui` dependency is test-only while headless native-project feasibility is being investigated; production code must not depend on a GUI runtime unless Phase 0 proves a headless approach requires it and the architecture is explicitly reviewed.

The installed distribution includes the 1.5.6 analysis, dex, Java input/conversion, Smali, mapping, Kotlin metadata, XAPK, AAB, APKM and APKS plugins listed in the [pinned CLI build](https://github.com/skylot/jadx/blob/4c0ac37699aa8c9803f1c73cfaacd9205acb044b/jadx-cli/build.gradle.kts). `jadx-core` alone has no input loaders in the distribution. `StandaloneDistributionTest` launches the installed service against a real native-project fixture and checks class loading and cursor behavior across two processes without the test-only GUI classpath.

## Source evidence inspected

The source archive was fetched from the `v1.5.6` tag and inspected at the commit above. Commit-pinned source links:

- [`JadxDecompiler.java`](https://github.com/skylot/jadx/blob/4c0ac37699aa8c9803f1c73cfaacd9205acb044b/jadx-core/src/main/java/jadx/api/JadxDecompiler.java): public constructor, `load()`, `getClasses()`, `getClassesWithInners()`, original/alias class lookup and `reloadCodeData()` APIs; its class-level documentation shows the library loading/decompile flow.
- [`JadxArgs.java`](https://github.com/skylot/jadx/blob/4c0ac37699aa8c9803f1c73cfaacd9205acb044b/jadx-core/src/main/java/jadx/api/JadxArgs.java): public `setCodeData(ICodeData)` API.
- [`JavaClass.java`](https://github.com/skylot/jadx/blob/4c0ac37699aa8c9803f1c73cfaacd9205acb044b/jadx-core/src/main/java/jadx/api/JavaClass.java) and [`JavaMethod.java`](https://github.com/skylot/jadx/blob/4c0ac37699aa8c9803f1c73cfaacd9205acb044b/jadx-core/src/main/java/jadx/api/JavaMethod.java): Java code and metadata, Smali, member listing and use references.
- [`JavaField.java`](https://github.com/skylot/jadx/blob/4c0ac37699aa8c9803f1c73cfaacd9205acb044b/jadx-core/src/main/java/jadx/api/JavaField.java), [`MethodInfo.java`](https://github.com/skylot/jadx/blob/4c0ac37699aa8c9803f1c73cfaacd9205acb044b/jadx-core/src/main/java/jadx/core/dex/info/MethodInfo.java), [`FieldInfo.java`](https://github.com/skylot/jadx/blob/4c0ac37699aa8c9803f1c73cfaacd9205acb044b/jadx-core/src/main/java/jadx/core/dex/info/FieldInfo.java), and [`TypeGen.java`](https://github.com/skylot/jadx/blob/4c0ac37699aa8c9803f1c73cfaacd9205acb044b/jadx-core/src/main/java/jadx/core/codegen/TypeGen.java): original member names and raw type signatures used by the isolated symbol adapter.
- [`JadxProject.java`](https://github.com/skylot/jadx/blob/4c0ac37699aa8c9803f1c73cfaacd9205acb044b/jadx-gui/src/main/java/jadx/gui/settings/JadxProject.java): `loadProjectData(Path)` and `save()`; `saveAs(Path)` uses `MainWindow`/GUI cache services. `buildGson` applies native relative-path and code-data adapters.
- [`ProjectData.java`](https://github.com/skylot/jadx/blob/4c0ac37699aa8c9803f1c73cfaacd9205acb044b/jadx-gui/src/main/java/jadx/gui/settings/data/ProjectData.java): native project fields include input files, tree expansions, `JadxCodeData`, tabs, mappings path, cache directory, live reload, search fields and plugin options.
- [`jadx-java.gradle.kts`](https://github.com/skylot/jadx/blob/4c0ac37699aa8c9803f1c73cfaacd9205acb044b/buildSrc/src/main/kotlin/jadx-java.gradle.kts): Jadx main sources target Java 11; `settings.gradle.kts` requires Java 11 or newer to build.
- [`jadx-cli/build.gradle.kts`](https://github.com/skylot/jadx/blob/4c0ac37699aa8c9803f1c73cfaacd9205acb044b/jadx-cli/build.gradle.kts): the pinned CLI's headless runtime plugin set used by the standalone LibJadx distribution.

## Non-Jadx build dependencies

| Dependency | Version | Scope / reason |
|---|---:|---|
| Jetty server and EE10 servlet | 12.1.13 | Embedded HTTP listener and servlet routing; production runtime |
| Jackson BOM / databind / YAML | 2.21.7 | API JSON responses and optional YAML configuration |
| JUnit Jupiter | 5.12.2 | Phase 0 executable feasibility probes |
| JUnit Platform Launcher | 1.12.2 | Gradle test runtime |

Resolved transitive versions are recorded in [gradle.lockfile](../gradle.lockfile) after initial resolution. Jadx artifacts are pinned in Gradle declarations and locks. Review and regenerate that lock deliberately when changing dependencies.

## Current feasibility status

The public-core smoke probe, in-memory class rename/comment, native project JSON round-trip, unknown-field retention, and matching-GUI save/reopen have executable probes. Remaining P0.3 topics are listed with bounded follow-up probes in [feasibility-matrix.md](feasibility-matrix.md). Phase 2 adds native save/reload/conflict detection, process-scoped revisions, mapping-path rebuilds and isolated temporary decompilation mode. Phase 4.1 adds Jadx-visible class listing and original class/method/field resolution. Search and edit HTTP routes remain unimplemented.

## Reproducing Phase 0 probes

```bash
./gradlew test
JADX_GUI=/path/to/jadx-gui ./gradlew guiRoundTripTest
JADX_GUI=/path/to/jadx-gui ./gradlew rawGuiRoundTripTest
```

The GUI task requires the matching 1.5.6 GUI executable, `xvfb-run` and `xdotool`. The base fixture under `tests/fixtures/native-project/` was created by Jadx 1.5.6 `jadx-gui`; its second relative input, Tiny v2 mapping reference and future JSON member were added for the headless adapter probe and loaded by the matching GUI.
