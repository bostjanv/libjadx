# LibJadx

**LibJadx is an experimental, standalone, headless Java service for accessing [Jadx](https://github.com/skylot/jadx) from local HTTP clients.** It is intended to make reverse-engineering workflows scriptable through a versioned REST/JSON API and, in a later milestone, a Python SDK.

> **Project status — early development.** Startup, native project lifecycle, jobs, explicit shutdown, original symbol lookup, Java decompilation, incremental search and native declaration editing are implemented. Advanced editing remains gated by Jadx and GUI probes. See [Current functionality](#current-functionality) before integrating it.

LibJadx is an independent project built on Jadx. It is not an official Jadx component and does not provide libghidra API or protocol compatibility.

## Current functionality

The implementation currently includes:

- A fixed-project service: one existing native `.jadx` project **or** one or more local input files per process, selected at startup. Multiple clients can connect to the same service; there is no project-switching endpoint.
- A loopback-only HTTP listener, started before potentially expensive Jadx initialization. The default address is `127.0.0.1:18777`.
- CLI, environment-variable, and optional YAML configuration, including canonical-path checks against allowed filesystem roots.
- Asynchronous project loading with lifecycle/status reporting, state-specific error responses, request IDs, and a capability-evidence endpoint.
- Native `.jadx` feasibility code and tests for project-relative inputs, mapping references, in-memory class renames/comments, preservation of selected unknown JSON fields, and a save/reopen round trip through the matching Jadx GUI.
- Explicit native save, reload, mapping-path updates, transient pending-edit export, bounded job polling/cancellation/SSE, and requested shutdown policies.
- Bounded listing of Jadx-visible classes and exact lookup of classes, methods, and fields by original JVM descriptors. Current aliases are separate from identity.
- Class-oriented Java source with validated token annotations, source snapshots, and method excerpts only where the pinned Jadx metadata and boundary checks agree. Per-request mode overrides use an isolated engine.
- Validated in-memory batches for original class/method/field renames and single-line native declaration comments. Explicit save is required for durability. Safe Tiny v2 export creates a new local artifact, bounded Tiny v2 import stages pending edits, and verified parameter targets support snapshot-bound renames. Local edits and override propagation remain unsupported.
- Controlled startup/shutdown behavior, including bounded waiting for loader cleanup on ordinary shutdown and separate handling of fatal startup errors.

Native declaration edits and a matching-GUI save/reopen round trip are exercised by real Jadx tests. The capability endpoint identifies narrower support and remaining unverified edit forms.

| HTTP endpoint | Current behavior |
| --- | --- |
| `GET /api/v1/health/live` | Process liveness (`ALIVE`); does **not** imply the project is ready. |
| `GET /api/v1/status` | Current project lifecycle (`LOADING`, `READY`, `FAILED`, and shutdown states), progress, and safe error details. |
| `GET /api/v1/capabilities` | Jadx version and evidence-backed capability status. |
| `GET /api/v1/project`, `POST /api/v1/project/save`, `POST /api/v1/project/reload`, `GET/PATCH /api/v1/project/settings`, `POST /api/v1/project/pending-edits/export` | Native project state, explicit persistence, reload, mapping configuration, and transient edit export. |
| `POST /api/v1/project/mappings/import` | Strict conflict-safe local Tiny v2 merge into unsaved native declaration edits. |
| `POST /api/v1/project/mappings/export` | Strict verified Tiny v2 declaration export to a new local file, without changing project state. |
| `GET /api/v1/jobs/{id}`, `POST /api/v1/jobs/{id}/cancel`, `GET /api/v1/jobs/{id}/events` | Process-local job polling, cooperative cancellation, and SSE. |
| `POST /api/v1/shutdown` | Graceful local shutdown with `discard` (default), `save`, or `refuse_if_dirty`; active work returns `PROJECT_BUSY`. |
| `GET /api/v1/classes`, `POST /api/v1/symbols/resolve` | Jadx-visible class pages and exact original class/member lookup. Input provenance may be unavailable. |
| `POST /api/v1/decompile` | Java source for an original class or method ref. Method requests include the containing class source and either a verified excerpt or an explicit range-unavailable fallback. |
| `POST /api/v1/edits/batch` | Validate all items, then stage native class/method/field renames and LINE declaration comments under one exclusive admission. Mutations remain unsaved until explicit native save. |
| Other planned analysis routes | Structured `PROJECT_NOT_READY` while loading, a non-retryable load failure after failed initialization, or `OPERATION_NOT_IMPLEMENTED` after readiness. |

Unknown paths return `NOT_FOUND`. The API contract is in [`openapi/openapi.yaml`](openapi/openapi.yaml).

## Requirements

- **JDK 21** to build and run LibJadx.
- A local filesystem input supported by the pinned Jadx release, or an existing compatible native `.jadx` project.
- Network access for the first Gradle dependency resolution, unless the required artifacts are already cached.

The Gradle wrapper pins Gradle **8.14.3**. The current build pins **Jadx 1.5.6**, **Jetty 12.1.13**, and **Jackson 2.21.7**, with Gradle dependency locking. See [`docs/compatibility.md`](docs/compatibility.md) for the exact dependency and upstream-source record.

## Quick start

Clone the repository and run the service against a local file:

```bash
git clone https://github.com/bostjanv/libjadx.git
cd libjadx
./gradlew run --args="--input /absolute/path/to/sample.jar --allowed-root /absolute/path/to"
```

On Windows, use `gradlew.bat` instead of `./gradlew` and Windows filesystem paths.

To open an existing Jadx project instead:

```bash
./gradlew run --args="--project /absolute/path/to/sample.jadx --port 18777"
```

An existing project can refer to multiple inputs and a mapping file. Those paths are resolved relative to the native project as appropriate and checked against the allowed roots. When no roots are explicitly supplied, roots are derived from the project/input parent directories.

When the process starts, query the diagnostic API in another terminal:

```bash
curl http://127.0.0.1:18777/api/v1/health/live
curl http://127.0.0.1:18777/api/v1/status
curl http://127.0.0.1:18777/api/v1/capabilities
curl 'http://127.0.0.1:18777/api/v1/classes?pageSize=50'
curl -H 'Content-Type: application/json' -d '{"ref":{"kind":"CLASS","originalClassDescriptor":"Lprobe/Sample;"}}' http://127.0.0.1:18777/api/v1/symbols/resolve
curl -H 'Content-Type: application/json' -d '{"ref":{"kind":"CLASS","originalClassDescriptor":"Lprobe/Sample;"},"representation":"JAVA"}' http://127.0.0.1:18777/api/v1/decompile
```

The listener is available while the project loads. Poll `/status` until it reports `READY`, or inspect the safe error information if it reports `FAILED`. A liveness response alone is not a readiness check.

To request a clean shutdown after work finishes:

```bash
curl -X POST http://127.0.0.1:18777/api/v1/shutdown -H 'Content-Type: application/json' -d '{"policy":"refuse_if_dirty"}'
```

To build a local application distribution with launch scripts:

```bash
./gradlew installDist
./build/install/libjadx/bin/libjadx --input /absolute/path/to/sample.jar
```

The application distribution is a **local build artifact**, not a published release or a claim that the planned analysis API is complete.

## Configuration

Choose exactly one startup mode: `--project PATH`, or one or more `--input PATH` arguments. Other options are `--config PATH`, `--port PORT`, `--bind 127.0.0.1`, and repeatable `--allowed-root PATH`. Use `--help` or `--version` for CLI information.

A minimal optional YAML configuration:

```yaml
project: /work/sample.jadx
bind: 127.0.0.1
port: 18777
allowedRoots:
  - /work
```

Configuration precedence is **command line > environment > YAML > defaults**. The project/input selection comes from the highest-precedence source specifying it; project and input selections are not combined across sources.

Supported environment variables are `LIBJADX_PROJECT`, `LIBJADX_INPUT`, `LIBJADX_BIND`, `LIBJADX_PORT`, and `LIBJADX_ALLOWED_ROOT`. List-valued environment variables use the platform's path separator.

LibJadx currently accepts only `127.0.0.1` as the bind address and provides no authentication. It is designed for trusted local use; do not expose the listener to untrusted networks or forward it through a public proxy. Paths are canonicalized, including symlinks, before allowed-root checks.

Class pagination uses an owner-only cursor signing key in `$XDG_STATE_HOME/libjadx/cursor-signing.key` (default `~/.local/state/libjadx/cursor-signing.key`). This is operational state outside native `.jadx` project persistence. See [Phase 4.1 notes](docs/phase-4-symbol-identity.md) for cursor behavior.

Java source is read-only and limited to 4 MiB UTF-8 per request. Source offsets are UTF-16 indices in the exact returned Java text. See [Phase 4.2 notes](docs/phase-4-decompiled-source.md) for method-range proof and metadata limits. Basic references, incremental search and native declaration editing are available; Smali remains planned.

`POST /api/v1/search` queries class names immediately and member/emitted-Java
text as classes are processed. It reports partial coverage until every eligible
Jadx-visible class or owner is indexed. `POST /api/v1/search/build-index`
starts the complete-index job; `strict` rejects partial results, while
`requireComplete` returns a job to poll. Safe source regex uses RE2/J.
Indexes and cursors are memory-only and disappear on restart. See
[Phase 5.1 search](docs/phase-5-search.md) for exact semantics and limits.

`POST /api/v1/edits/batch` accepts 1–64 exact original-declaration edits and
returns per-item results plus before/after revisions. `RENAME` supports ordinary
classes, methods and fields; `SET_COMMENT` supports one-line `LINE` comments.
Prevalidation failure changes nothing, and a no-op leaves revisions unchanged.
See [Phase 5.2 editing](docs/phase-5-editing.md) for a request example, native
identity rules, persistence proof and unsupported cases.

To export current aliases and LINE declaration comments, including unsaved
edits and accepted attached Tiny v2 mappings, send `targetPath`, `format:
"TINY_V2"`, `expectedSessionId` and `expectedLogicalRevision` to
`POST /api/v1/project/mappings/export`. The absolute `.tiny` destination must
be new, with an existing nonsymlink parent under allowed roots. Unsupported or
ambiguous source entries fail before publication. Export does not save or
attach the file. See [mapping export evidence and limits](docs/phase-5-mapping-export.md).

See [`docs/configuration.md`](docs/configuration.md) for startup, path handling, response-state, and shutdown details.

## Development and tests

Run the Java tests and compile the service:

```bash
./gradlew clean test compileJava
```

The repository includes unit/HTTP lifecycle tests, Jadx feasibility probes, and native-project compatibility fixtures. A separate **opt-in** test checks a real save-and-reopen round trip using the matching **Jadx 1.5.6** GUI. On a Linux machine with `xvfb-run`, `xdotool`, and the matching GUI installed, run:

```bash
JADX_GUI=/absolute/path/to/jadx-gui ./gradlew guiRoundTripTest
JADX_GUI=/absolute/path/to/jadx-gui ./gradlew nativeEditGuiRoundTripTest
JADX_GUI=/absolute/path/to/jadx-gui ./gradlew mappingExportGuiRoundTripTest
JADX_GUI=/absolute/path/to/jadx-gui ./gradlew scopedEditGuiRoundTripTest
```

The opt-in GUI test is not part of a normal `./gradlew test` run. Results from the small fixture and GUI round-trip probes do not establish correctness for all Jadx-supported input formats or all edit types.

Before contributing, read [`AGENTS.md`](AGENTS.md), [`DESIGN.md`](DESIGN.md), and [`IMPLEMENTATION.md`](IMPLEMENTATION.md). Changes to public behavior should update the OpenAPI contract and include relevant regression tests.

## Architecture and roadmap

The intended architecture separates application lifecycle and HTTP transport from native project persistence, Jadx-version-sensitive integration, analysis, in-memory search, and operation scheduling. The current Gradle application is the initial implementation slice; the full logical architecture is documented in [`DESIGN.md`](DESIGN.md).

Completed slices cover native save/reload and external-change detection, revisions, coordinated operations, process-local jobs, shutdown, original symbol lookup, Java source, basic references, incremental search, native declaration editing and strict Tiny v2 export/import. Snapshot-bound parameter renames are available for the proved AUTO/RESTRUCTURE signature subset. Local editing and related-method propagation remain outstanding; the Python SDK follows in Phase 6. Smali, CFG and resource capabilities depend on further tests against the pinned Jadx release.

The long-term design retains **one project per process**, native Jadx persistence, explicit saves, and no HTTP file uploads. Planned endpoints and behavior must not be mistaken for features already delivered.

For implementation sequencing and known technical limits, see [`IMPLEMENTATION.md`](IMPLEMENTATION.md) and [`docs/feasibility-matrix.md`](docs/feasibility-matrix.md).

## Project documentation

- [`DESIGN.md`](DESIGN.md) — approved architecture, behavior, and future API design.
- [`IMPLEMENTATION.md`](IMPLEMENTATION.md) — phased work plan and acceptance criteria.
- [`AGENTS.md`](AGENTS.md) — instructions for coding agents and contributors.
- [`openapi/openapi.yaml`](openapi/openapi.yaml) — current experimental HTTP contract.
- [`docs/configuration.md`](docs/configuration.md) — local service configuration and startup behavior.
- [`docs/compatibility.md`](docs/compatibility.md) — pinned upstream and build dependencies.
- [`docs/feasibility-matrix.md`](docs/feasibility-matrix.md) — tested Jadx behavior, limitations, and follow-up probes.
- [`docs/phase-4-symbol-identity.md`](docs/phase-4-symbol-identity.md) — original identity, paging, provenance, and pinned-source evidence.
- [`docs/phase-5-search.md`](docs/phase-5-search.md) — incremental search, coverage, jobs, cursors and pinned-source evidence.
- [`docs/phase-5-editing.md`](docs/phase-5-editing.md) — native declaration editing, batch semantics, GUI evidence and remaining gates.
- [`docs/phase-5-scoped-editing.md`](docs/phase-5-scoped-editing.md) — snapshot-bound parameter targets, native persistence and unsupported local forms.

## License and attribution

LibJadx depends on [Jadx](https://github.com/skylot/jadx) and other third-party libraries, each subject to its own license. **No LibJadx project license file is present in the repository at the time this README was drafted**; do not assume that Jadx's license also licenses LibJadx. Review the repository's licensing status and third-party notices before redistributing a build.

### Basic references (Phase 4.3)

`POST /api/v1/references/query` accepts original class/method/field refs and an
explicit `INCOMING` or `OUTGOING` direction (fields: incoming only). For example:

```json
{"ref":{"kind":"METHOD","originalClassDescriptor":"Lprobe/Sample;","originalName":"caller","originalDescriptor":"()I"},"direction":"OUTGOING","includeSourceSites":true}
```

Results report Jadx-observed method pairs, unresolved original method descriptors,
field users and class dependencies. Optional source sites are verified against a
P4.2 Java snapshot. Coverage is always partial; READ/WRITE and original offsets
are unavailable. Signed pagination detects changes in observed graph content,
including those caused by decompilation without project edits. Queries do not
save or dirty native state. See [semantics, limits and evidence](docs/phase-4-references.md).

`POST /api/v1/project/mappings/import` reads an existing local `.tiny` file with
required `sourcePath`, `format:"TINY_V2"`, `mode:"MERGE_FAIL_ON_CONFLICT"`,
`expectedSessionId` and `expectedLogicalRevision`. All records are validated
before one in-memory commit. Matching aliases/comments are unchanged; differing
values or collisions reject the whole import. A new LINE comment or an exact
attached prefix plus one native LINE suffix is supported. An equivalent file
or valid header-only file returns `NO_CHANGE` without invalidating caches.
The receipt distinguishes parsed records from effective alias/comment edits.
Import neither attaches nor writes the source, and requires explicit native
save for persistence. See [supported semantics and gates](docs/phase-5-mapping-import.md).

### Scoped parameter renames (PR #13)

`/decompile` returns `variables` with exact declaration ranges, original method
refs, parameter indexes and explicit persistability. Use a `SUPPORTED` parameter
entry in `/edits/batch`:

```json
{"expectedSessionId":"<current UUID>","expectedLogicalRevision":0,"items":[{"kind":"RENAME_PARAMETER","method":{"kind":"METHOD","originalClassDescriptor":"Lprobe/Variables;","originalName":"instance","originalDescriptor":"(IJDLjava/lang/String;)I"},"parameterIndex":1,"sourceSnapshotId":"<current sha256 snapshot>","newName":"wideCount"}]}
```

Indexes count original parameters from zero, excluding `this`; `long` and
`double` each count once. A snapshot from an override or before a mutation,
reload, settings rebuild, save publication or restart cannot authorize an edit.
All declared variable names in a method are conservatively treated as overlapping.
Local renames, constructors, synthetic/bridge methods, bodyless/annotated or
transformed signatures and unproved generic forms fail closed. Methods with
unverified catch declarations, including Jadx's unannotated unused catch arguments,
also expose unsupported parameter targets and reject edits before staging. Changes remain
in memory until explicit native save. Tiny export continues to reject scoped
code refs. See [evidence and limits](docs/phase-5-scoped-editing.md).
