# AGENTS.md — LibJadx Coding-Agent Instructions

> **Read this file first.** Then read [DESIGN.md](DESIGN.md) and [IMPLEMENTATION.md](IMPLEMENTATION.md) before changing code. If the three documents appear inconsistent, apply the priority rules below and record the issue; do not silently change architecture.
>
> Status: implementation handoff, 2026-09-24. The repository described here is a proposed structure, not a claim that the modules already exist.

## Mission

Build **LibJadx**, an independently designed, standalone, headless Java service exposing Jadx decompilation and analysis via a versioned REST/JSON API, plus a Python SDK. The target is broad reverse-engineering functionality analogous in purpose to libghidra **where Jadx can actually support it**, not protocol or SDK compatibility with libghidra.

The human-approved architectural decisions in [DESIGN.md](DESIGN.md) are fixed unless an implementation blocker requires an explicit design change. Follow the ordered, test-gated work packages in [IMPLEMENTATION.md](IMPLEMENTATION.md). **Do not begin by implementing the entire API**: complete the native-project round-trip and Jadx capability probes first.

## Instruction precedence

1. Current explicit human instructions and subsequently approved design changes.
2. This file's non-negotiable constraints.
3. `DESIGN.md` for behavior and public contracts.
4. `IMPLEMENTATION.md` for implementation sequence and acceptance tests.
5. Existing repository conventions, if compatible.

When a requirement is infeasible with the pinned Jadx release, gather proof in a minimal test, mark the capability unsupported or degraded, and write an ADR/proposed design change. Do not invent Jadx API behavior or silently substitute approximate data for exact results.

## Non-negotiable product constraints

- **Exactly one fixed project per process.** The project or input path is mandatory at process startup. Do not add multi-project session management, runtime project switching, or project IDs to ordinary API paths. Multiple projects run in independent processes on separate ports. Multiple clients of one process share its active state.
- **Headless standalone Java only.** No GUI plugin or desktop GUI at runtime. A GUI installation may be used in tests to validate native project round trips. Ship a standalone Java distribution and launch scripts; Docker is not part of the agreed initial distribution.
- **Local files, never HTTP uploads.** Open existing `.jadx` projects or any input format supported by the pinned Jadx release from filesystem paths supplied at startup. Preserve their original locations. Validate configured allowed roots, symlinks and destructive paths.
- **Native Jadx persistence only.** No SQLite/PostgreSQL database, proprietary project file, persistent HTTP job journal, custom persisted search index, LibJadx-specific fields in `.jadx`, or background autosaves. Reuse Jadx-native project/mapping/cache formats where they genuinely work. Server config and explicit portable exports are separate operational artifacts, not project-state stores.
- **Explicit save.** All edits initially change in-memory project state. On ordinary close, unsaved edits are discarded by default. Shutdown permits explicit `discard`, `save`, or `refuse_if_dirty` policy. Never claim a change survived restart unless a native save succeeded.
- **Detect external modification before saving.** Refuse to overwrite a changed native project/mappings file. Support exporting the pending edits in a transient API response and explicitly reloading the project. Do not implement automatic concurrent GUI collaboration or assume external tools honor our locks.
- **Serve readiness immediately.** Bind the HTTP listener before large-project loading finishes; expose LOADING / READY / FAILED and load-progress information. Business endpoints return a structured not-ready error until initialization succeeds.
- **HTTP:** versioned REST/JSON, maintained OpenAPI contract with CI validation, synchronous lightweight operations, asynchronous heavy jobs, polling and SSE, typed errors and explicit partial-result semantics. Bind `127.0.0.1` by default; no authentication is required initially. Do not silently enable remote binding.
- **Jadx version:** at implementation start, identify the latest stable upstream release, pin its exact artifact versions / commit as applicable, record the result in dependency locks and `docs/compatibility.md`, and never pull `master` at build time. Use public APIs first; isolate version-sensitive internal APIs behind adapters.
- **Python:** generate the low-level typed transport from the reviewed OpenAPI contract; maintain handwritten synchronous, asynchronous and object-oriented convenience layers.
- **Experimental API until 1.0.** Every potentially breaking contract change must update OpenAPI, SDK generation, fixtures and contract tests in the same change.

## Source and license hygiene

- Jadx is the implementation dependency and primary engineering reference. Check its exact pinned source before using an API; don't guess signatures from current `master`.
- libghidra is a **functional inspiration only**, not a dependency or implementation template. Do **not** copy its protobuf schemas, API relationships, code, architecture, documentation, tests or client implementations into LibJadx. Its current Human-Origin Source License restricts materially derived or API-compatible replacements without permission. If anyone proposes direct reuse, stop that portion of work and request a human license review/permission. Design independent HTTP and domain contracts from Jadx's capabilities and product requirements.
- Preserve upstream license notices and third-party attribution; review distribution licenses before packaging. Never claim libghidra or Jadx endorses LibJadx.

## Required working method

1. **Inspect first.** Identify pinned Jadx source, inspect public and relevant internal APIs, and write down evidence (source path and pinned revision). Convert each uncertainty into a small executable feasibility test.
2. **Work in slices.** Implement one deliverable milestone from `IMPLEMENTATION.md` at a time. Keep transport, business logic, native-project serialization and Jadx-version-sensitive code behind different interfaces.
3. **Contract before controller.** Propose or update `openapi/openapi.yaml`, reviewed request/response examples and typed error definitions before wiring each endpoint; validate contract against the server and generated Python transport.
4. **Test with real Jadx.** Mocks are insufficient for native project saves, renames, bytecode offsets, CFGs, concurrency or cancellation. Use actual input fixtures and pinned upstream binaries.
5. **Preserve native compatibility.** For `.jadx` files, preserve fields not explicitly edited, relative-path handling and native mapping semantics. A GUI round-trip must remain possible. Never equate `JadxDecompiler.save()` (source/resource export) with saving a `.jadx` GUI project.
6. **Do not overpromise unsupported capabilities.** Result-level capabilities and `EXACT`, `APPROXIMATE`, `UNAVAILABLE`/`FAILED` statuses are preferable to invented offsets, incorrect provenance or fake CFGs.
7. **Keep updates reviewable.** Make small commits or coherent patches, record assumptions, add tests and report exact commands/results. Do not claim tests passed unless you ran them.
8. **No silent scope expansion.** Do not add GUI integration, uploads, server-side persistent metadata, remote listening, extra SDKs, automatic project saves or runtime project switching without human approval.

## Expected repository layout

```text
libjadx/
  AGENTS.md
  DESIGN.md
  IMPLEMENTATION.md
  docs/
    compatibility.md           # filled in after pinning Jadx
    adr/                       # justified departures and technical decisions
  openapi/
    openapi.yaml               # reviewed external contract
  app/                         # main, CLI, config, lifecycle
  http/                        # controllers, JSON mapping, SSE
  core/                        # transport-independent models/services
  jadx-adapter/                # pinned Jadx façade, internal adapters
  project/                     # native .jadx / mappings / export
  analysis/                    # code, symbols, xrefs, CFG, resources
  search/                      # in-memory incremental indexes
  scheduler/                   # jobs, coordination, cancellation
  python/                      # generated transport + handwritten API
  tests/                       # integration and GUI round-trip fixtures
```

These are **logical** modules. Start with fewer physical Gradle modules if simpler, but retain clean package/interface boundaries. Use Gradle Kotlin DSL and a JDK compatible with the selected Jadx release; pin and document both.

## Critical implementation invariants

- The source of truth for class/method/field identities is the original descriptor **plus input identity when genuinely recoverable**. Aliases are not identities. If Jadx merges duplicate definitions, disclose that provenance limitation instead of fabricating unique source identities.
- Local-variable identities are source-snapshot-scoped; parameters prefer original positional identity. Reject edits against outdated snapshots.
- Source positions are valid only for their code snapshot and effective settings. Keep bytecode offsets and source offsets in separate coordinate systems. Expose mapping availability/precision.
- Persisted identity is content-derived from relevant native files and inputs; logical and index revisions are per-running-process. A session/boot identifier prevents accidental reuse of stale revision tokens after restart.
- A mutation invalidates affected in-memory caches immediately. Recompute lazily, with optional background refresh. Search defaults to explicitly labeled partial coverage, with an option to require complete coverage.
- Temporary decompiler override requests get a **single-operation, read-only isolated Jadx instance** initialized from an immutable snapshot of the current logical state, **including unsaved renames and comments**. Never mutate or share mutable code-data objects with the primary instance.
- Concurrent class reads are enabled only after safety tests on the pinned version. Serialize writes, global passes and any unverified operation. Job cancellation is cooperative; a deadline must not be presented as proof of JVM-level interruption.
- Validate all edits in a batch before executing. If an unexpected failure causes partial application, return itemized applied/failed status. Never advertise full rollback unless proved.
- Close/shutdown and persistent configuration reload are rejected while conflicting work is active; a project-wide settings change, when accepted, immediately rebuilds affected Jadx state and retains unsaved edits.
- Native project save uses the pinned Jadx release's compatible serialization semantics; there is **no extra transactional backup/recovery layer** unless explicitly approved.

## Scope boundaries and acceptance

**First usable release:** startup/readiness, class/method enumeration, decompiled Java, basic references/navigation, incremental search, supported rename/comment mutations, explicit native save, synchronous/async Python access, structured errors, jobs/progress, contract tests and automated GUI project round-trip.

**Later advanced work:** Smali and deeper code metadata where not already available, instruction-level xrefs, raw/transformed CFGs, comprehensive resource queries, classpath-aware resolution, variable editing, and reproducible portable export. These remain in the design even when delayed past the first vertical slice.

**Quality gate:** run real-Jadx integration, OpenAPI contract tests, concurrency/cancellation, malformed-input, stale-revision, external-change, restart, and GUI round-trip suites. Report skipped tests and unsupported capabilities explicitly.

## Agent completion report format

For each finished milestone, report:

1. **Implemented:** endpoints/modules and changes in the public contract.
2. **Evidence:** pinned Jadx source links/paths, fixture names, tests and exact commands with outcomes.
3. **State:** behavior for unsupported/partial capabilities and remaining limitations.
4. **Persistence check:** how GUI round-trip and explicit-save behavior were verified, where relevant.
5. **Next milestone:** unblocked tasks and any human decision genuinely required.

If Phase 0 feasibility fails for a mandatory feature, stop dependent implementation, preserve the failing fixture and propose the smallest compatible design adjustment for human review. Do not quietly turn an unverified assumption into a contract guarantee.

## References

- Jadx upstream: https://github.com/skylot/jadx
- Jadx library usage: https://github.com/skylot/jadx/wiki/Use-jadx-as-a-library
- Native project model (verify against pinned release): https://github.com/skylot/jadx/blob/master/jadx-gui/src/main/java/jadx/gui/settings/data/ProjectData.java
- Native project serialization (verify against pinned release): https://github.com/skylot/jadx/blob/master/jadx-gui/src/main/java/jadx/gui/settings/JadxProject.java
- libghidra licensing reference **only**: https://github.com/0xeb/libghidra/blob/main/LICENSE
