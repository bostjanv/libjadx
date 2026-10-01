# Compatibility and pinned dependencies

Status: v0.1 candidate locally release-qualified after PR #22; Phase 6.2 complete.
Candidate not externally published or tagged. Updated 2026-10-02.

## Current version matrix

| Component | Current pin / qualification |
| --- | --- |
| Java candidate | `0.1.0-alpha.1` |
| Python SDK | `0.1.0a1` |
| OpenAPI | `0.1.0-experimental` |
| Jadx | `1.5.6` |
| Jadx source | `28ff15e4ae69950aebea110a13e5ab895d234dfc` |
| JDK / Gradle | 21 / 8.14.3 |
| Python | >=3.11; Linux x86_64 qualification on CPython 3.11.13 and 3.14.4 |
| Generator | openapi-python-client 0.29.1 |

Phases 0–6 are complete for the defined first-release scope. Qualification applies
to PR #22's recorded source and artifacts; it is separate from external publication.
See the [documentation index](README.md) for current guidance and historical records.

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

The 1.5.6 Git tag is `v1.5.6`, a stable, signed upstream release. No build-time dependency points to `master`. The production distribution is headless and has no GUI runtime dependency. Matching `jadx-gui:1.5.6` is test/qualification infrastructure only; it was used for all native persistence gates.

The installed distribution includes the 1.5.6 analysis, dex, Java input/conversion, Smali, mapping, Kotlin metadata, XAPK, AAB, APKM and APKS plugins listed in the [pinned CLI build](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-cli/build.gradle.kts). Bundled plugin availability is not release qualification for every supported format: PR #22 qualified owned class/JAR/DEX/native fixtures, not the entire APK/bundle/conversion matrix. `jadx-core` alone has no input loaders in the distribution. `StandaloneDistributionTest` launches the installed service against a real native-project fixture and checks class loading and cursor behavior across two processes without the test-only GUI classpath.

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

PR #15 also uses the already shipped `jadx-java-input:1.5.6` and
`jadx-dex-input:1.5.6` at compile time. Only their compile/test-compile lock
membership changes; artifact versions and runtime distribution dependencies do
not change. The isolated adapter calls pinned internal `JavaClassReader` and
`DexFileLoader` and copies public input-API `IClassData` / `IMethodRef` metadata.
JVM class files, two-input JARs, direct DEX and DEX entries in JAR envelopes are
exercised. APK, nested archives, Smali/conversion and bundle census coverage are
unsupported; normal service input support is unaffected. See
[exact source, limits and subset](phase-5-hierarchy-verifier.md).

| Dependency | Version | Scope / reason |
|---|---:|---|
| Jetty server and EE10 servlet | 12.1.13 | Embedded HTTP listener and servlet routing; production runtime |
| Jackson BOM / databind / YAML | 2.21.7 | API JSON responses and optional YAML configuration |
| JUnit Jupiter | 5.12.2 | Phase 0 executable feasibility probes |
| JUnit Platform Launcher | 1.12.2 | Gradle test runtime |
| RE2/J | 1.8 | Linear-time source regex; pinned in `gradle.lockfile`, included in the standalone runtime with its Go/RE2 license notice under `licenses/RE2J-LICENSE` |

Resolved transitive versions are recorded in [gradle.lockfile](../gradle.lockfile) after initial resolution. Jadx artifacts are pinned in Gradle declarations and locks. Review and regenerate that lock deliberately when changing dependencies.

## Current compatibility and capability boundaries

The native codec, explicit save/reload/conflict handling, original identities,
Java snapshots, basic references, search, declaration/mapping/parameter/group
edits and Python sync/async clients are implemented. PR #22 is the final v0.1
qualification report below; the [feasibility matrix](feasibility-matrix.md)
distinguishes public capabilities from internal Jadx probes.

Local rename and public Smali are UNSUPPORTED. References and related propagation
are PARTIAL. Primary Jadx reads remain serialized/fail-fast PROJECT_BUSY;
multiple clients are supported. Cancellation is PARTIAL/cooperative and remains
CANCELLING until work stops, with no hard arbitrary-Jadx interruption guarantee.
CFG/resources/external-classpath/portable export work remains Phase 7.

## Historical compatibility investigations

The following Phase 0–5 and PR #13–#21 records describe the state at each milestone.
Their forward-looking statements and intermediate exclusions are historical;
final v0.1 support is summarized above and in the PR #22 section below.
Pinned source/fixture details remain in the linked subsystem records.

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
retains explicit native save. PR #18 candidate failures now preserve READY before any native commit.
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
Unused catch arguments can be emitted as NamedArg without VarNode metadata
(`BlockExceptionHandler.fixMoveExceptionInsn`, `RegionGen.makeCatchBlock`).
The owned `UnusedCatch.smali` probe proves the gap and native name reassignment
in AUTO/RESTRUCTURE. Parameter support now requires verified catch declaration
tokens within the same emitted method range; affected methods fail closed before
batch staging. Verified catch declarations keep parameter support.
Merged locals change emitted SSA metadata under SIMPLE, and FALLBACK emits no
variable declarations; local renames remain unsupported. See
[scoped editing evidence](phase-5-scoped-editing.md) for commands and matching-GUI
results. GUI serialization drops unknown JSON fields, as established in Phase 0;
LibJadx's native codec preserves them before the GUI resave.

## PR #14 related-method evidence

The same exact 1.5.6 source archive, Maven artifacts and JDK/toolchain are used;
no dependencies or locks changed. A fully resolved owned interface branch is
omitted from MethodOverrideAttr, and one-seed native replay differs between hot
and fresh engines. Original duplicate definitions remain collapsed. Explicit
propagation remains UNSUPPORTED. See [pinned paths, fixtures, native strategies
and actual GUI diagnostics](phase-5-related-propagation.md) and
[exact validation outcomes](pr-14-review.md).

## PR #16 complete-family replay evidence

The same Jadx 1.5.6 artifacts/source, JDK 21 and dependency locks remain pinned.
The historical PR #16 replay path unloads owners before notifying the global native
rename listeners. With previously generated owned hierarchy source, explicit
records for all independently verified family members also change unrelated
CovariantLeaf Object/String declaration aliases to `m0value`. Fresh native
reopen restores `value`, despite correct persisted records for the requested
family. This violates the hot/fresh nonmember safety gate. All explicit
propagation stays UNSUPPORTED; ordinary declaration editing is unchanged.
See [source reasoning and replay matrix](phase-5-propagated-edits.md) and
[PR #16 validation](pr-16-review.md). Actual GUI Save As is a diagnostic of
the saved records, not permission to admit unsafe propagation.

## Historical PR #17 safe replay feasibility (superseded below)

Outcome B; production replay and every accepted edit contract remain unchanged.
Fresh replacement reconstructs complete native/mapping/scoped state and fixes
PR #16's unrelated hot bridge aliases on the owned Joined fixture. It also
recomputes an unedited automatic collision alias in the existing return-only
fixture (`m0value` → `value` after renaming the integer-return declaration).
Current replay preserves that alias but fails fresh equivalence. Listener-first
loses mapping comments; owner-only leaves another owner's generated references
stale. No tested generic strategy passes every gate. Propagation still returns
422 on every flag presence, with its existing UNSUPPORTED capability evidence.

[Detailed strategies, pinned source, oracle and cost](phase-5-safe-replay.md) and
[final validation](pr-17-review.md) distinguish positive reconstruction probes
from the failed adoption gate. No engine swap, alias patch, native extra record,
autosave, API/SDK or dependency change is shipped. The next decision concerns
automatic nonmember alias recomputation; group admission remains dependent on
safe publication and its separate service/native/GUI gates.

## PR #18 approved alias semantics and replacement publication

This section supersedes PR #17's requirement to preserve every incidental alias.
Automatic Jadx aliases are derived analysis state: collision aliases,
deobfuscation aliases without explicit persistence, and other generated aliases
may be recomputed when an edit changes analysis. A declaration with no explicit
rename may therefore change its display alias without a separate user edit.
Native declaration renames, mapping aliases/comments, supported scoped renames,
retained VAR records and declaration comments remain authoritative. Original
identities, settings, ordered input references and unknown native fields retain
their existing preservation rules. Never add native records to freeze generated
aliases.

Effective native batches now privately stage complete code data, load one fresh
production engine, commit native data once, then publish that engine under the
existing exclusive lease. No-op retains the engine and revisions; candidate
failure publishes nothing. See [replacement publication](phase-5-replacement-publication.md) for ordering,
failures, oracle, persistence and validation. PR #19's
[verified group admission](phase-5-related-group-admission.md) now supplies same-lease
COMPLETE verification, immutable plans, all-owner raw collision checks and exact
native records/affectedRefs. Local editing remains unsupported.

## PR #19 — verified related-method rename group admission

This section supersedes prior propagation deferrals. Explicit METHOD RENAME
`propagateRelated: true` admits only independently COMPLETE closed-input families
verified synchronously against the captured current engine under one exclusive
edit lease. Immutable sorted original/native plans, raw all-owner collision
inventory, full-batch overlap checks and group-private atomic staging precede
PR #18 fresh replacement publication. Ordinary omitted/false retains one-record
semantics and empty affectedRefs. Applied and verified no-op groups return exact
original family refs; no-op requires every explicit member record already present.

Limits: 64 per family; four propagated items, 128 total members and 800000 reserved
verification work per batch. No class or ordinary method rename shares a group
batch; parameter edits on members reject. Field renames and declaration comments
may coexist. Raw hidden bridge/synthetic methods block collisions,
without becoming admitted members. Standard Jadx records persist only on explicit
save. Restart/discard/reload, accepted input/mapping conflicts and actual matching
GUI Save As retain their native-only behavior. Covariant/bridge, missing/external,
duplicate, local and parameter propagation remain unsupported. PR #20 subsequently closes Phase 5.2 with local rename
deliberately unsupported; Phase 6 subsequently completed in PR #21/#22. The investigation changed no dependency or lock.

See [admission, status mapping, ordering and persistence](phase-5-related-group-admission.md)
and [final-head validation](pr-19-review.md).

## PR #20 native local identity boundary

The [28-file source audit](pr-20-source-audit.json) records hashes against the
same pinned source archive. `CodeRenameVisitor` consumes method+VAR register/SSA
keys without settings or parameter/local binding. A one-definition, no-phi JVM
local retargets under matching GUI Use dx/d8; decompilation mode and input-loader
configuration are global GUI state, absent from ProjectData. Actual editor and
Save As diagnostics cover AUTO, RESTRUCTURE, SIMPLE, FALLBACK and AUTO+dx/d8.
The records survive, but their meaning is not settings-independent.

No native-safe product subset is proved. Capability remains UNSUPPORTED,
evidence `NATIVE_VAR_RETARGETS_WITH_UNBOUND_GUI_SETTINGS`, persistence UNAVAILABLE.
Phase 5.2 closed with local editing excluded; Phase 6 subsequently completed in PR #21/#22. No dependency,
lock, runtime GUI, schema or mapping-format change. See
[feasibility](phase-5-local-rename-feasibility.md) and [validation](pr-20-review.md).

## PR #21 Python SDK baseline

Distribution/import: `libjadx` **0.1.0a1**, HTTP-only, all rights reserved for now
(`LicenseRef-Proprietary`, human-selected interim policy). Jadx pin/source, Java
runtime and OpenAPI 0.1.0-experimental remain unchanged. This is Phase 6.1 SDK
foundation; subsequent PR #22 completed Phase 6.2 qualification. External publication remains separate.

| Component | Exact tested pin | Scope |
|---|---|---|
| Minimum Python | 3.11 | Package requires >=3.11; no 3.15 prerelease claim |
| Python interpreters | CPython 3.11.13 and 3.14.4 | Clean wheel environments on Linux x86_64 |
| Generator | openapi-python-client 0.29.1 | Patched security release; sole input openapi/openapi.yaml; stock templates |
| Environment/lock/build frontend | uv 0.8.22, uv.lock | Frozen universal dependency resolution, wheel/sdist |
| Build backend | hatchling 1.27.0 | Pinned isolated build backend |
| Runtime HTTP client | httpx 0.28.1 | Same connection pool for generated/high-level and SSE |
| Generated model runtime | attrs 26.1.0, typing-extensions 4.16.0 | Typed attrs models; no additional handwritten validation framework |
| Runtime transitives | anyio 4.15.1, certifi 2026.7.22, h11 0.16.0, httpcore 1.0.9, idna 3.20 | Locked wheel test environments |
| Type checker | mypy 1.19.1 | Strict handwritten modules; generated imports followed silently |
| Linter/formatter | ruff 0.16.9 | Isolated generator hooks; handwritten explicit config |
| Tests | pytest 8.4.2, pytest-asyncio 1.2.0 | Generated/model, sync/async mock and real subprocess tests |
| Independent contract validators | PyYAML 6.0.3, jsonschema 4.26.0, openapi-spec-validator 0.9.0 | Existing Java captures rerun unchanged |

All transitive build/test/generator versions and distribution hashes are locked
in `python/uv.lock`. Runtime dependency ranges are intentionally narrow; validation
uses the exact locked versions above. Network is required to bootstrap an empty
cache; frozen offline sync works after populating it. No Windows/macOS results
or external publication are claimed. [SDK architecture](phase-6-python-sdk.md)
records generator limitations; [review](pr-21-review.md) records actual commands,
counts and artifact fingerprints. PyPI name check returned 404 on 2026-09-30;
no package was published or reserved.

## Current PR #22 cross-language candidate qualification

Java `0.1.0-alpha.1` and Python `0.1.0a1` identify the same experimental candidate;
OpenAPI remains `0.1.0-experimental`. CLI and capabilities use one deterministic
Gradle-generated Java version resource. Jadx, JDK, Gradle, Python tooling and
all dependency locks retain their PR #21 pins. Linux x86_64 is the qualification
platform; Windows/macOS execution is unqualified (the batch launcher is audited).

The completed executable [release gate](phase-6-release-qualification.md) exercises all 22
operations on raw/generated/handwritten sync/async surfaces, all 19 stable errors,
real class/JAR/DEX/native input, explicit native persistence and process restarts.
[Current evidence](pr-22-review.md) determines qualification; historical PR results
are not substituted for this run. No runtime GUI, project store, authentication,
remote listening, extra transport or package publication is introduced.

Public release capabilities are reconciled with the service contract, not the
internal Phase-0 probes: `code.smali=UNSUPPORTED` (no public representation),
`analysis.concurrent_reads=UNSUPPORTED` (serialized/fail-fast primary reads), and
`analysis.cancellation=PARTIAL` (cooperative jobs, no hard arbitrary-Jadx interruption
guarantee). The full reviewed snapshot in `openapi/examples/capabilities.json` is
verified on every client surface and in ordinary processes with hooks disabled.
