# LibJadx

**LibJadx is an experimental, standalone, headless Java service for accessing [Jadx](https://github.com/skylot/jadx) from local HTTP clients.** It is intended to make reverse-engineering workflows scriptable through a versioned REST/JSON API and, in a later milestone, a Python SDK.

> **Project status — early development.** Startup, native project lifecycle, jobs, explicit shutdown, and original symbol lookup are implemented. Decompiled source, search, and HTTP editing remain future work. See [Current functionality](#current-functionality) before integrating it.

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
- Controlled startup/shutdown behavior, including bounded waiting for loader cleanup on ordinary shutdown and separate handling of fatal startup errors.

**Important distinction:** Native project edits and GUI round trips are exercised by probes/tests. An explicit HTTP save exists; HTTP rename/comment editing is still planned. Some capability entries describe proven Jadx integration rather than an available endpoint.

| HTTP endpoint | Current behavior |
| --- | --- |
| `GET /api/v1/health/live` | Process liveness (`ALIVE`); does **not** imply the project is ready. |
| `GET /api/v1/status` | Current project lifecycle (`LOADING`, `READY`, `FAILED`, and shutdown states), progress, and safe error details. |
| `GET /api/v1/capabilities` | Jadx version and evidence-backed capability status. |
| `GET /api/v1/project`, `POST /api/v1/project/save`, `POST /api/v1/project/reload`, `GET/PATCH /api/v1/project/settings`, `POST /api/v1/project/pending-edits/export` | Native project state, explicit persistence, reload, mapping configuration, and transient edit export. |
| `GET /api/v1/jobs/{id}`, `POST /api/v1/jobs/{id}/cancel`, `GET /api/v1/jobs/{id}/events` | Process-local job polling, cooperative cancellation, and SSE. |
| `POST /api/v1/shutdown` | Graceful local shutdown with `discard` (default), `save`, or `refuse_if_dirty`; active work returns `PROJECT_BUSY`. |
| `GET /api/v1/classes`, `POST /api/v1/symbols/resolve` | Jadx-visible class pages and exact original class/member lookup. Input provenance may be unavailable. |
| Other planned analysis and edit routes | Structured `PROJECT_NOT_READY` while loading, a non-retryable load failure after failed initialization, or `OPERATION_NOT_IMPLEMENTED` after readiness. |

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

See [`docs/configuration.md`](docs/configuration.md) for startup, path handling, response-state, and shutdown details.

## Development and tests

Run the Java tests and compile the service:

```bash
./gradlew clean test compileJava
```

The repository includes unit/HTTP lifecycle tests, Jadx feasibility probes, and native-project compatibility fixtures. A separate **opt-in** test checks a real save-and-reopen round trip using the matching **Jadx 1.5.6** GUI. On a Linux machine with `xvfb-run`, `xdotool`, and the matching GUI installed, run:

```bash
JADX_GUI=/absolute/path/to/jadx-gui ./gradlew guiRoundTripTest
```

The opt-in GUI test is not part of a normal `./gradlew test` run. Results from the small fixture and GUI round-trip probes do not establish correctness for all Jadx-supported input formats or all edit types.

Before contributing, read [`AGENTS.md`](AGENTS.md), [`DESIGN.md`](DESIGN.md), and [`IMPLEMENTATION.md`](IMPLEMENTATION.md). Changes to public behavior should update the OpenAPI contract and include relevant regression tests.

## Architecture and roadmap

The intended architecture separates application lifecycle and HTTP transport from native project persistence, Jadx-version-sensitive integration, analysis, in-memory search, and operation scheduling. The current Gradle application is the initial implementation slice; the full logical architecture is documented in [`DESIGN.md`](DESIGN.md).

Completed early milestones cover native save/reload and external-change detection, revisions, coordinated operations, process-local jobs, shutdown, and original symbol lookup. Next slices cover Java/Smali output and references, incremental search and supported edits, then a generated-transport/handwritten-convenience Python SDK. Extended CFG and resource capabilities depend on further tests against the pinned Jadx release.

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

## License and attribution

LibJadx depends on [Jadx](https://github.com/skylot/jadx) and other third-party libraries, each subject to its own license. **No LibJadx project license file is present in the repository at the time this README was drafted**; do not assume that Jadx's license also licenses LibJadx. Review the repository's licensing status and third-party notices before redistributing a build.
