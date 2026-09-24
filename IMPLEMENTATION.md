# LibJadx — Detailed Implementation Instructions for a Coding Agent

**Status:** execution plan derived from the approved architecture, 2026-09-24. This is an implementation specification, **not a claim that code, commands or tests already exist**.  
**Read first:** [AGENTS.md](AGENTS.md), then [DESIGN.md](DESIGN.md). If feasibility findings contradict a required feature, implement the documented capability/error path and escalate the smallest genuinely blocking design change; do not invent API support.

## 0. Execution rules and definition of done

Implement in the milestone order below. Each milestone must produce its listed code, executable tests and a short review report before dependent work begins. Maintain a living `docs/compatibility.md`, `docs/feasibility-matrix.md` and `docs/adr/` as soon as the repo exists. Do not implement GUI plugin support, multi-project management, HTTP uploads, SQLite/other custom project stores, autosave or unapproved libghidra compatibility.

**Milestone definition of done:** reviewed OpenAPI changes if public behavior changes; unit plus real-Jadx tests for Jadx-dependent behavior; negative/error tests; build/format/static checks; explicit record of unsupported/degraded capabilities; no regression of `.jadx` round trips or explicit-save semantics. Provide exact commands and results, not speculative pass reports.

**First-release gate:** vertical slice works end-to-end on real APK/DEX/JAR fixtures from Java and Python, with native project round-trip in actual jadx-gui, API contract coverage, concurrency and conflict tests. Advanced CFG precision and exhaustive Android resource analysis are separate, later milestones and must not block the first experimental release unless they invalidate foundational design assumptions.

## 1. Phase 0 — Pin dependencies and verify feasibility (mandatory gate)

**Do not begin the full HTTP implementation until Phase 0 succeeds.** Create tiny disposable probes/tests instead of a large framework.

### P0.1 Choose and freeze Jadx

1. At the *time implementation starts*, inspect upstream stable releases: https://github.com/skylot/jadx/releases. Exclude nightlies and prereleases. Record the exact stable tag, associated source revision and release date in `docs/compatibility.md`; record why that tag qualifies as stable.
2. Verify published Gradle/Maven coordinates and artifact availability for the exact release. Inspect the pinned `jadx-core`, `jadx-gui` project-model dependencies and required JDK. Do not assume current `master` method signatures or choose an arbitrary version from this document.
3. Pin the JDK toolchain and all Java/Python dependencies; enable Gradle dependency locking or a version catalog with deterministic versions. Preserve Jadx NOTICE/LICENSE in the standalone distribution as required.
4. Review current libghidra license **only for compliance**; do not reuse its code, proto/API schemas, internal design, examples or tests. The service's contract must be independently constructed from product requirements and Jadx's actual capabilities.

**Deliverables:** exact version/JDK table; licenses/NOTICE checklist; reproducible Gradle resolution; a minimal program that loads a small real input through pinned `JadxDecompiler` and lists/decompiles a class.

### P0.2 Native project round-trip spike — highest priority

Inspect at the **pinned** Jadx revision:

- `jadx-gui/.../settings/JadxProject.java` (`loadProjectData`, native JSON adapters and `save`), `.../settings/data/ProjectData.java`, `RelativePathTypeAdapter`, `JadxCodeData`/rename/comment models and user mapping modes.
- `jadx-core/.../api/JadxArgs.java` (`setCodeData`, input files and mapping config), `JadxDecompiler.reloadCodeData()` and native cache options.
- How jadx-gui serializes paths relative to the `.jadx` location, records user renames/comments, retains unrelated GUI fields and handles absent/moved input files.

Build a **throwaway headless spike** that:

1. Opens a `.jadx` fixture created by the **matching** jadx-gui, including class/method rename, code comment, native mapping reference, GUI view-state fields and multiple input references.
2. Loads its code data into `JadxDecompiler` without initializing GUI windows or requiring a graphical display.
3. Modifies a supported rename and comment in memory without creating/modifying any persistent project file.
4. Explicitly saves using the pinned native format semantics. Do not call `JadxDecompiler.save()` as if it saves `.jadx` projects; that exports decompiled source/resources.
5. Opens the resulting project in the **actual matching jadx-gui** (Xvfb or another suitable GUI test environment) and verifies the edit is visible and original GUI-only fields/path references survived. Then save from jadx-gui and reopen headlessly to verify the reverse direction.
6. Uses a fixture with unknown/forward-compatible JSON fields to verify whether the chosen codec preserves unmodified data. If direct reuse drops fields, use an isolated **native-format-preserving** serializer adapter that updates known keys while retaining the remaining native JSON tree; do **not** add LibJadx keys.
7. Exercises new-project creation from a raw APK: ensure no `.jadx` file is written before explicit `save`/`save_as`, then verify GUI opening after save.

The existing `JadxProject` class refers to GUI-specific code even though static project helpers are available; **test headless class loading** rather than assuming importing a `jadx-gui` dependency is automatically safe. If the pinned release cannot support an isolated native adapter without a GUI runtime, stop here, preserve the failing minimal fixture and propose a compatible alternative for approval.

**Tests:** `NativeProjectRoundTripIT`, `RelativePathRoundTripIT`, `UnknownFieldPreservationIT`, `NativeMappingRoundTripIT`, `NoAutosaveIT`, `RawInputSaveAsIT`, `ActualGuiRoundTripIT`.

**Pass condition:** an explicitly saved project opens in matching jadx-gui, shows its edits, and retains unrelated project metadata and paths. No custom project fields or persistent auxiliary database.

### P0.3 Probe analysis semantics and Jadx safety

Implement independent minimal probes over small APK/DEX/JAR fixtures and one intentionally problematic application:

| Probe | Evidence to capture | Decision enabled |
|---|---|---|
| Class/method lookup | Original descriptor vs alias, method signature format, package/inner-class behavior | Stable symbol identity |
| Multidex provenance | Duplicate class across input paths / embedded DEX entries; inspect whether loader preserves original definitions | Input identity guarantees; ambiguity behavior |
| Java metadata | Declaration offsets, use positions, source/debug mapping, inline-method boundaries | Source snapshot DTO and method excerpt extraction |
| Smali | Exact API and class/method coverage | Separate representation endpoint |
| Xrefs | Callers, callees, field reads/writes, unresolved refs and available bytecode offsets | Accuracy levels and reference kinds |
| CFG | Raw and transformed block/edge access and effect of code restructuring; distinguish DOT output from in-memory graph access | Per-representation capability |
| Editing | Class/method/field rename, comments, parameters/locals, related method propagation, native persistence | Edit capability matrix |
| Resource decoding | Manifest, decoded XML, string/assets and resolvable resource relationships | Android resource coverage |
| Concurrent decompile | Different-class parallel requests, same-class duplication, reads during mutation/reload | Lock scope and safe concurrency |
| Cancellation | Which passes honor interruption or cancellation; how uninterruptible work behaves | Truthful cancellation/deadline model |
| Caches | Available native cache backends; invalidation after rename/settings change | Search/cache integration |

For every probe, write `SUPPORTED`, `PARTIAL`, `UNSUPPORTED` or `UNKNOWN`, cite pinned source/fixture/test evidence and record the permitted API precision. Do not use the absence of one internal method as proof a capability is impossible; try supported alternatives without compromising the architecture.

**Exit gate for Phase 0:** native GUI round-trip passes, basic core APIs work, and every advanced feature has a capability status or a bounded follow-up investigation. Exact CFG/xref availability can remain partial, but must not be falsely advertised as complete.

## 2. Phase 1 — Repository and application foundation

### P1.1 Project scaffold

Create the independent Gradle Kotlin DSL build and the logical packages from `DESIGN.md`. Start with `app`, `core`, `jadx-adapter`, `project`, `http`, `analysis`, `search`, `scheduler`; split physical subprojects only where it improves dependency separation. Add Java/JDK toolchain pin, dependency locking, formatting/static analysis, JUnit 5, meaningful test fixture directories and a reproducible standalone distribution task.

Default HTTP choice: a compatible stable **embedded Jetty** release with Jackson JSON mapping and an explicit OpenAPI 3.1 file; verify versions and JDK compatibility against the pinned Jadx release. If another small server framework demonstrably simplifies SSE and graceful shutdown, record an ADR before adopting it. Keep service interfaces independent of Jetty/Jackson and do not expose Jadx objects as JSON DTOs.

Add initial docs:

- `docs/compatibility.md` — exact versions, unsupported input/capability notes, tested OS/JDK matrix.
- `docs/feasibility-matrix.md` — Phase 0 findings and pinned upstream source links.
- `docs/adr/` — any design deviations with decision, alternatives and consequences.
- `openapi/openapi.yaml` — reviewed external schema, initially status/errors/jobs/project/capabilities.

### P1.2 Fixed-project startup and configuration

Implement a `main` entry point with **required** `--project PATH` or `--input PATH`, optional `--config PATH`, `--bind` (loopback-only initially), `--port`, `--allowed-root` and standard help/version output. Config precedence: **CLI > environment > YAML/config file > defaults**. Do not add project switching or remote `open` operations. If multiple inputs are exposed via repeated `--input`, they form **one logical project**.

Suggested runtime state:

```text
STARTING -> LOADING -> READY
                     |-> FAILED
READY -> RELOADING -> READY/FAILED
STARTING/LOADING/READY/FAILED -> SHUTTING_DOWN -> STOPPED
```

Bind the HTTP listener **before** expensive project initialization. `GET /api/v1/health/live` works immediately and `GET /api/v1/status` reports `LOADING` with progress. A failed load keeps diagnostic endpoints available. All analysis/edit/save operations before READY return structured `PROJECT_NOT_READY` and reasonable `Retry-After` when applicable.

Validate canonical paths and configured allowed roots for project, inputs, mappings, classpath, and future export output. Guard symlink traversal; recheck sensitive paths before writing. Do not make GET endpoints mutate project state.

**Tests:** startup status during an intentionally slow load; missing input; disallowed path; port collision; invalid configuration; failed decompiler load; cannot switch project; two independent processes on different ports.

### P1.3 Contract, DTOs and errors

Define common API models **before controllers** in `openapi/openapi.yaml`. Avoid a `projectId` route segment. Cover status, capabilities, result provenance, partial coverage, revision tokens, paginated list, job, edit item, and nested error models. Provide an error-to-HTTP mapping from `DESIGN.md`; redact stack traces except explicit local debug mode. Use stable request IDs.

Automate OpenAPI lint/validation and request/response contract tests that run against the actual server. Freeze and regenerate Python transport after reviewed contract changes. Until version 1.0, breaking changes are allowed but must be recorded in a changelog and tests/SDK updated together.

## 3. Phase 2 — Native project lifecycle and revision tracker

### P2.1 Headless project repository

Create interfaces such as:

```java
interface NativeProjectRepository {
    NativeProject load(Path nativeProject);
    NativeProject newFromInput(List<Path> inputs);
    SaveResult save(NativeProject snapshot, Path target, FileFingerprint expected);
    NativeProject reload(Path sameProjectPath);
}
```

*Illustrative interfaces; verify exact Jadx and project-data types during Phase 0.* The adapter must:

- Read/write only **native** project content and supported native mapping formats.
- Preserve unknown and unrelated GUI fields; use the pinned version's relative-path rules; never silently discard missing input references on headless load.
- Distinguish an existing `.jadx` startup from a raw-input startup. For raw input, hold native project data in memory and require a documented explicit save target.
- Never save as a side effect of opening, renaming, indexing, HTTP query, job completion or routine shutdown.
- Treat native cache paths as rebuildable data, not authoritative edit storage.

### P2.2 Revisions, save and conflict

Build immutable `ProjectSnapshot` and `RevisionState` models:

- `sessionId`: UUID per process startup; exposed with every snapshot-bound response.
- `logicalRevision`: monotonic process-local mutation/configuration revision.
- `persistedRevision`: content-derived fingerprint of native project, native mappings and relevant input/dependency files. Compute large input hashes in background with a defined availability state.
- `indexRevision` per derived index and source snapshot ID/settings fingerprint per decompilation.
- `dirty`: whether the current native-saveable code data/settings differs from the last saved state.

`save` checks optional `expectedLogicalRevision` and **mandatory internal** on-disk baseline equality for native project/mapping files. If the files changed externally, return `EXTERNAL_MODIFICATION_CONFLICT` and preserve unsaved edits in memory. Do not attempt a three-way merge. Expose a transient `pending-edits/export` response and an explicit `reload` requiring `discard_unsaved: true` when dirty. On successful save, recompute the persisted fingerprint and mark the corresponding logical revision saved.

Follow pinned native saving behavior exactly; **do not add proprietary atomic-replace/backup guarantees**, which were explicitly excluded. State the durability limitation in documentation and error responses when partial native writes are possible.

**Tests:** dirty false at startup; true after rename; no files changed before save; saved aliases/comments survive reloading; native file hash conflict; mapping file hash conflict; pending-edit export; explicit discard/reload; crash before save loses edits; crash after successful save preserves them; source snapshot/cursor stale after mutation; restart invalidates session-local revision tokens.

### P2.3 Settings rebuild and temporary analysis

Create a `JadxEngineFactory` that constructs the primary or temporary instance from validated `EffectiveAnalysisConfig` and an **immutable deep copy** of native `JadxCodeData`. Do not share the mutable primary code-data collection with a temporary instance. On successful accepted project-wide settings change, build a replacement primary instance with the unsaved edit snapshot, then swap only after it loads successfully; invalidate source/index state and bump the logical revision. If currently busy, return `PROJECT_BUSY` rather than racing a reload.

One-operation temporary overrides use a temporary engine initialized from a snapshot **including the latest unsaved renames/comments**. Stamp output with the snapshot revision/settings hash. On completion or actual cancellation, release the temporary engine. Guard concurrent temporary instance count and memory budget. If a requested setting has no native project representation, it may be **temporary-only**; reject attempts to persist it and advertise that distinction via capabilities.

**Tests:** unsaved rename visible in temporary override source; primary source unaffected by override; primary logical revision stable on read-only override; resource cleanup after failure; concurrent mutation after temporary snapshot does not affect prior result; persistent settings update retains unsaved edits; unsupported native setting save fails clearly.

## 4. Phase 3 — Scheduler, cancellation and lifecycle correctness

### P3.1 Operation coordinator

Define read/write categories rather than allowing controllers to manipulate locks. Recommended categories:

- `CLASS_READ(classKey)`: parallel across distinct, proven-safe classes; deduplicate same-class decompilation.
- `INDEX_READ(snapshot)` and `QUERY_READ`: concurrent with compatible state only.
- `PROJECT_EXCLUSIVE`: mutations, native saves, global reload, unverified internal graph passes and teardown.
- `TEMPORARY_ANALYSIS(snapshot)`: isolated engine with independent limits and immutable inputs.

Start conservatively with project-wide serialization for uncertain Jadx operations, then enable class-level concurrency **only when Phase 0 proves safe**. Never hold a write lock while blocking on an SSE subscriber or waiting for an HTTP client.

### P3.2 Job registry and SSE

Implement an in-memory bounded job registry with QUEUED/RUNNING/CANCELLING/SUCCEEDED/FAILED/CANCELLED, progress (`done`, `total` when knowable, stage), result/diagnostic links or inline bounded results, per-job event buffer, cancellation token and execution deadline. Include a queue limit, TTL for completed results, bounded SSE buffers and a defined overflow/reconnect policy. Polling always works when SSE is disconnected.

`POST /api/v1/jobs/{id}/cancel` sets cancellation intent and prevents scheduling further class work. Interrupt only operations proven safe to interrupt; non-cooperative Jadx work may continue in CANCELLING beyond the caller's deadline. Do **not** return CANCELLED until execution has actually ceased. Keep job metadata/result volatile and discard it at restart; index work is recreated from the native project when needed.

### P3.3 Shutdown

`POST /api/v1/shutdown` accepts policy `discard` (default), `save`, or `refuse_if_dirty`. Reject shutdown while conflicting operations/jobs are active, returning `PROJECT_BUSY` and information on jobs to await/cancel. Once quiescent, apply policy, close the native project/engine, close HTTP listeners gracefully and release native caches/temp files. Process termination by OS/SIGKILL cannot guarantee saving; no automatic checkpoints exist.

**Tests:** requests before READY; simultaneous same-class/different-class queries; mutation while searching; concurrent client conflicting edits; cancellation before dispatch, between classes and during a known uninterruptible operation; SSE and polling parity; queue saturation; shutdown while busy; shutdown policies dirty/clean; complete JVM stop/restart.

## 5. Phase 4 — Symbols, decompilation and navigation

### P4.1 Identity and lookup

Define a **structured** `SymbolRef` independent of Jadx Java object identity. A class uses `(inputIdentity if recoverable, originalDescriptor)`; method/field references add original name and original signature. Expose deobfuscated/alias names separately. Use `POST /symbols/resolve` for complex descriptors and `GET /classes` for pagination/filtering. If multiple original definitions are merged or discarded by Jadx, expose `AMBIGUOUS`/`PROVENANCE_UNAVAILABLE` instead of fabricating unique IDs. Test multidex and inner/anonymous classes.

### P4.2 Decompiled source and metadata

Implement class-oriented Java retrieval first. Return code text, Jadx errors/warnings, source snapshot ID, effective settings, declaration and reference annotations, and optional raw debug lines. Method requests return **a verified slice of enclosing class source** where a reliable range exists; otherwise return the class-level result with `method_range_available=false` and diagnostics. Preserve separate location domains for decompiled source and original bytecode offsets.

Specify line/column and text-offset units in OpenAPI. Test CRLF/LF, Unicode supplementary characters, decompiler mode changes, inlining, partial decompile and no-code classes. Never claim exact mapping when optimization destroyed a one-to-one relationship. Add Smali and lower-level representations according to the proven adapter capability matrix.

### P4.3 Basic references

Use verified Jadx usage/dependency APIs for first-release callers/callees/class references, returning unresolved references as descriptors with diagnostics. When present, add exact instruction address/offset and source location as separate optional fields. Include capability granularity for `resolved_target`, `source_location`, `original_offset` and `reference_kind`; do not synthesize unavailable instruction offsets.

**Tests:** known call graph, field read/write fixture, inheritance/override fixture, missing external library, synthetic lambda/inlined method, reflection that cannot be statically resolved.

## 6. Phase 5 — Incremental search and editing vertical slice

### P5.1 Memory-only search

Index original symbols, aliases, annotations, constants/strings and resource metadata during lightweight preparation. As classes are requested/decompiled, index normalized source text and verified references. A complete-index **async job** enumerates all eligible classes, recording successful, skipped, failed and pending classes under a source/settings/revision fingerprint. `strict=true` or `require_complete=true` must not silently return a partial result; it can trigger/await the complete-index job or return a job reference.

Use offset paging only on stable snapshot-bound enumerations; use opaque, in-memory cursor state for expensive searches. Bound index memory and record evictions as degraded coverage. Invalidate relevant source/reference indexes immediately on edit, and refresh lazily or via a background job. Invalidate stale cursors deterministically.

**Tests:** partial search includes explicit coverage; strict search only succeeds with eligible complete coverage; one class fails to decompile; regex and string searches; mutation invalidates source-index revision; repeated pages stable or explicitly fail stale; index rebuilt on restart without sidecars.

### P5.2 Editing and native propagation

Build validated native-saveable editing operations in this order: class rename, method/field rename, comments, native mapping import/export; then supported parameter/local rename and related-method propagation. Verify each type through Phase 0 and actual GUI round-trip before advertising persistence. Use original entity references plus snapshot-scoped local variable references. Report propagated affected entities when known.

For `/edits/batch`, prevalidate **all items** (identity, supported persistence, syntax, expected revision, duplicate/conflicting operations). If prevalidation fails, apply none. During execution an unexpected failure may partially apply; return itemized `APPLIED`, `FAILED`, `SKIPPED` and the resulting logical revision. Do not implement an unproven rollback mechanism. Mutations invalidate affected cached results before responding.

The Python high-level API should supply `expected_revision` automatically from the client's latest known project snapshot while allowing low-level callers to use explicitly defined unconditional semantics. The server always serializes mutations and detects native on-disk conflicts before save, even if a request omits an expected revision.

**Tests:** single rename and native save; related override propagation where native-supported; unsupported local rename rejected; prevalidation failure no edits; forced mid-batch failure accurately describes partial application; stale logical revision; source/cache invalidation; restart discards unsaved edits; GUI round-trip after explicit save.

## 7. Phase 6 — Python SDK and end-to-end experimental release

### P6.1 Generated and handwritten layers

Generate the low-level synchronous and asynchronous clients and typed models from the **reviewed** OpenAPI contract. Do not hand-edit generated files. Maintain handwritten:

- `Client` / `AsyncClient` for connection and readiness; timeouts and typed errors.
- `Project` exposing the one fixed project's revision, capabilities, save, reload and settings.
- `JavaClass` / `JavaMethod` / `SymbolRef` convenience wrappers, with explicit source-snapshot identity.
- `SearchCursor` / async iterator exposing completeness, invalidation and pagination errors.
- `Job` providing poll, await completion, cancel and SSE progress iteration, with polling fallback.

Use Python typing and a supported minimum interpreter version chosen at implementation start; record it in the compatibility matrix. Avoid hidden project opens, hidden autosaves, silently discarded partial results and stale-revision retries that mutate unexpected entities. Provide examples for basic analysis, batch rename + explicit save, async complete search and cancellation.

### P6.2 Cross-language and release tests

Run a real service in subprocess integration tests. Exercise every first-release route from raw HTTP, generated low-level Python, handwritten sync Python and async Python. Verify identical typed error codes, provenance fields, source snapshots, strict/partial behavior and SSE/polling outcomes. Generate the Python package and Java standalone distribution reproducibly.

**First experimental release passes only if:**

1. One fixed input/project can be started from CLI; status is reachable while loading; cannot switch projects.
2. Symbol enumeration, Java decompilation, basic xrefs and indexed search work on real inputs with honest partial diagnostics.
3. Native-supported rename/comment operations update in-memory state, do not autosave, save explicitly, and survive a verified **actual jadx-gui** reopen.
4. Revisions, stale cursors, unexpected batch partial failure and external native-file conflicts are reported without data loss by silent overwrite.
5. Real concurrent client tests, deadlines/cancellation tests and lifecycle/shutdown tests pass for the proven Jadx concurrency configuration.
6. OpenAPI matches the server; generated and handwritten Python SDK tests pass; the standalone package contains correct licenses and launches without the GUI.

If any mandatory criterion fails, do not label the build a release candidate. Keep the previous milestone usable and report exact blockers.

## 8. Phase 7 — Extended capabilities (post-initial-release)

Only after the vertical slice passes:

1. **Instruction-level references:** verified original offsets and per-reference provenance; unresolved references remain first-class.
2. **CFGs:** independently report raw/original and transformed graphs with availability, block/edge IDs, edge kinds and method/settings provenance. Test try/catch, loops, switch, obfuscation and failed decompilations. A DOT dump is not a sufficient runtime API by itself.
3. **Richer representations:** Smali and versioned internal representations only where verified. Do not expose unversioned internal Jadx objects or misleading AST promises.
4. **Advanced annotations:** parameters/locals with snapshot-scoped identities, source-line and bytecode mapping precision, native persistability per operation.
5. **Resources and dependencies:** manifest/components/permissions, decoded and raw XML/assets, resolved resource relationships, configurable external classpaths and explicit unresolved symbols.
6. **Portable export:** ZIP with native `.jadx`, original inputs, native mappings and explicitly configured redistributable dependencies; rewrite supported relative links; report missing/non-redistributable entries. Test extraction/opening in actual matching jadx-gui. Export is an explicit artifact, never a new persistent workspace format.
7. **Optional process-isolated analysis:** preserve the internal engine boundary for later worker JVMs if proven necessary. Do not implement process-per-project prematurely; one fixed primary project remains the product contract.
8. **Optional explicit filesystem deletion:** review separately. There is no managed workspace to remove; any destructive CLI/API operation must preview exact native files, require explicit authorization and never infer deletion of original inputs.

## 9. Test fixtures and automated test matrix

Maintain **owned** or redistributable fixtures. Suggested fixtures:

- Tiny Java project compiled to JAR/class with overloaded methods, inheritance, interfaces, lambdas, inner classes, string literals and Unicode identifiers/comments.
- Known APK and DEX pair with manifest, permissions, components, decoded XML, images/assets and resource references.
- Multidex sample with duplicate descriptor provenance, if generation and redistribution allow it.
- Sample with absent external dependencies and explicit classpath recovery.
- Intentionally problematic/obfuscated input known to trigger warnings and partial decompilation.
- `.jadx` projects created **by pinned jadx-gui** containing native mappings, renames, comments, unrelated GUI fields, relative and absolute paths and unknown forward-compatible JSON fields.
- Long-running fixture for cancellation, load progress, bounded results and resource limits.

| Test class | Required behavior |
|---|---|
| Unit | DTO/revision invariants, path policy, batch validation, indexing coverage, error mapping, cancellation state machine |
| Native integration | Real Jadx load, original descriptors, code data reload, source locations, xrefs, resources, native caches |
| GUI round-trip | Headless save -> open in real matching jadx-gui -> inspect edits and unrelated fields -> GUI save -> headless reopen |
| API contract | Every endpoint checked against OpenAPI, positive/negative examples, all published error codes and strict/partial responses |
| Python | Generated sync+async transport and handwritten workflows against an actual local server |
| Concurrency | Same-class deduplication; proven different-class parallelism; global mutation serialization; multiple clients; shutdown while busy |
| Recovery | Restart discards unsaved edits/jobs/indexes but keeps successfully native-saved changes; failed load remains diagnosable |
| Security | Loopback bind, no upload routes, disallowed roots, symlinks, archive traversal, large malformed ZIPs and disabled CORS by default |
| Regression | Compare selected fixture symbols/decompile/mappings across each *explicitly approved* Jadx upgrade; capture unsupported features |

GUI round-trip tests must actually start or otherwise exercise the matching GUI in an environment with a display (Xvfb is acceptable). A unit test only invoking `ProjectData` is **not** a substitute for the approved GUI-round-trip release gate. Keep slow GUI/large-fixture tests in dedicated CI jobs if necessary, but make them required before tagged releases.

## 10. CI, packaging and documentation checklist

- Validate pinned Java/Python toolchains and dependency locks.
- Build/format/lint/test Gradle packages; run small fixture integration tests on every change and expanded GUI/concurrency/security suites before release.
- Validate OpenAPI schema and live-server request/response behavior; regenerate Python models and fail CI if generated code differs from committed outputs.
- Bundle Java runtime or document supported JDK explicitly; ship Unix/Windows launch scripts as appropriate; keep runtime free of GUI requirements. **Standalone Java distribution only** unless scope changes.
- Include upstream Jadx and dependency licenses/NOTICES and independent project attribution; do not copy libghidra code or schemas.
- Document CLI config precedence, loopback-only security boundary, allowed-root policy, input formats, limitation matrix, partial results, editing/save/conflict behavior, job cancellation truthfulness, and the Python SDK.
- Maintain a human-readable experimental API changelog; regenerate SDK/tests for contract-breaking changes before 1.0.

### Example local verification commands (create the corresponding Gradle tasks first)

```bash
./gradlew clean test
./gradlew integrationTest
./gradlew guiRoundTripTest      # dedicated display/Xvfb environment
./gradlew check
./gradlew installDist
# Python environment and package tooling chosen and pinned during setup:
python -m pytest python/tests
```

The above are **target commands**, not assertions that the tasks or tests are currently available. An agent may adapt exact task names while preserving the corresponding CI gates.

## 11. Required milestone handoff template

For each work package, write a concise report containing:

```text
Milestone:
Implemented (files, endpoint/model changes):
Pinned Jadx API evidence (commit/tag, source path):
Real fixtures exercised:
Commands run and exact outcomes:
GUI round-trip evidence (if project persistence changed):
Supported / partial / unsupported capability changes:
Behavior under restart / concurrency / conflicts:
Known failures and regression tests:
Approval required, if and only if an actual blocking design change exists:
Next unblocked work package:
```

Do not treat a checklist tick or written plan as test evidence. The coding agent's deliverable is working, verifiable code under the approved architecture, with truthful capability reporting and native Jadx project compatibility.
