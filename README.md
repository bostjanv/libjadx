# LibJadx

LibJadx is a standalone, headless Java service that exposes [Jadx](https://github.com/skylot/jadx) through a versioned REST/JSON API and a typed Python SDK for local reverse-engineering workflows.

> **Project status — experimental v0.1 candidate.** The defined first-release scope is feature-complete and locally release-qualified by PR #22 as Java `0.1.0-alpha.1` / Python `0.1.0a1` against Jadx 1.5.6. The candidate is not tagged or externally published on GitHub Releases, PyPI or Maven. Phase 7 is the extended-capability roadmap.

## Inspiration

LibJadx is inspired by [libghidra](https://github.com/0xeb/libghidra) and is modeled in spirit after its goal of making a reverse-engineering engine accessible through typed, scriptable APIs and client libraries. LibJadx applies that general product idea to Jadx.

LibJadx is an independent implementation. It does not copy or implement libghidra's API, protobuf contracts, wire protocol, data models, internal architecture, examples or tests, and it is not API/protocol compatible with libghidra. Neither libghidra nor Jadx endorses LibJadx.

## What works

- One fixed native `.jadx` project or local input set per process, shared by multiple clients.
- Loopback-only REST/JSON with immediate liveness, load progress and readiness reporting.
- Jadx-visible class enumeration and original class/method/field identities, separate from aliases.
- Java decompilation, source snapshots, verified token metadata and method excerpts where available.
- Basic references with explicit partial coverage, incremental search and complete-index jobs.
- Process-local jobs with polling, cooperative cancellation and SSE progress.
- Declaration renames/comments, strict Tiny v2 mapping import/export, snapshot-bound parameter renames and a conservative related-method propagation subset.
- Explicit native save, reload, pending-edit export and external-modification conflict detection.
- Generated typed Python transport and handwritten synchronous/asynchronous SDKs.
- Locally qualified standalone Java archives and Python wheel/sdist artifacts.

## Current limitations

The [public capability snapshot](openapi/examples/capabilities.json) is authoritative for formal statuses; see the [support summary](docs/README.md#compatibility-and-capabilities).

References, method excerpts, member/source search, native save and related propagation are `PARTIAL` within their documented boundaries. Exact input provenance and original debug-line/bytecode mappings are unverified. Local-variable rename and the public Smali representation are `UNSUPPORTED`. CFG and resource query APIs are not implemented; they remain Phase 7 work.

Multiple clients are supported. Primary Jadx reads are serialized, and conflicting primary-read admission fails with `PROJECT_BUSY`; parallel primary reads are `UNSUPPORTED`. Cancellation is `PARTIAL` and cooperative: `CANCELLING` remains until work stops. Client deadlines and stream closure do not request cancellation or guarantee hard interruption of arbitrary Jadx work.

## Quick start

Build and run with **JDK 21** and the pinned Gradle **8.14.3** wrapper. Dependency resolution needs network access on an empty cache. Jadx is pinned to **1.5.6**; exact sources and other dependencies are in [compatibility](docs/compatibility.md).

```bash
git clone https://github.com/bostjanv/libjadx.git
cd libjadx
./gradlew run --args="--input /absolute/path/to/sample.jar --allowed-root /absolute/path/to"
# Or open an existing native project:
./gradlew run --args="--project /absolute/path/to/sample.jadx --port 18777"
```

On Windows use `gradlew.bat` and Windows paths. Windows/macOS execution is not release-qualified; the recorded qualification platform is Linux x86_64.

In another terminal, poll status until `READY`; liveness alone is not readiness. If loading fails, status retains safe diagnostics.

```bash
curl http://127.0.0.1:18777/api/v1/health/live
curl http://127.0.0.1:18777/api/v1/status
curl 'http://127.0.0.1:18777/api/v1/classes?pageSize=50'
curl -H 'Content-Type: application/json' \
  -d '{"ref":{"kind":"CLASS","originalClassDescriptor":"Lprobe/Sample;"},"representation":"JAVA"}' \
  http://127.0.0.1:18777/api/v1/decompile
```

To build a local distribution with Unix/Windows launch scripts:

```bash
./gradlew installDist
./build/install/libjadx/bin/libjadx --input /absolute/path/to/sample.jar
```

The distribution is a locally built artifact from the qualified experimental candidate, not an externally published release. A JDK is not bundled. Phase 7 capabilities remain outside v0.1 scope. PR #22 qualification applies to its [recorded source revision and artifact hashes](docs/pr-22-review.md), not every later build.

## Python SDK

Phase 6.1 SDK foundation and Phase 6.2 release qualification are complete. Python **3.11** is the minimum; installed-wheel qualification covers **3.11.13** and **3.14.4** on Linux. Build/install locally; the package is not published on PyPI:

```bash
cd python
uv sync --frozen --all-groups
uv build
python -m pip install dist/libjadx-0.1.0a1-py3-none-any.whl
```

Connect to an already running Java service:

```python
from libjadx import Client, SymbolRef

with Client("http://127.0.0.1:18777") as client:
    client.wait_ready()
    result = client.project.decompile(SymbolRef.class_("Lprobe/Sample;"))
    print(result.source)
```

See [SDK usage](python/README.md), [SDK architecture](docs/phase-6-python-sdk.md) and the [completed release gate](docs/phase-6-release-qualification.md). The SDK preserves diagnostics and revisions, surfaces stale-state errors and never saves or retries mutations implicitly.

## API

All routes use `/api/v1`. These families cover the **22 implemented operations**; [OpenAPI](openapi/openapi.yaml) is authoritative for exact requests, responses and typed errors.

| Family | Operations (prefix omitted) |
| --- | --- |
| Process and readiness | `GET /health/live`, `GET /status`, `GET /capabilities`, `POST /shutdown` |
| Native project | `GET /project`, `POST /project/save`, `GET /project/settings`, `PATCH /project/settings`, `POST /project/reload`, `POST /project/pending-edits/export` |
| Mappings | `POST /project/mappings/import`, `POST /project/mappings/export` |
| Symbols and code | `GET /classes`, `POST /symbols/resolve`, `POST /decompile`, `POST /references/query` |
| Search and edits | `POST /search`, `POST /search/build-index`, `POST /edits/batch` |
| Jobs | `GET /jobs/{jobId}`, `POST /jobs/{jobId}/cancel`, `GET /jobs/{jobId}/events` |

Planned Phase 7 routes include `/project/export`, `/analysis/cfg` and `/resources/query`; a public Smali representation also requires a reviewed contract. These are outside the current operation inventory. Unknown paths return `NOT_FOUND`; recognized planned routes return `OPERATION_NOT_IMPLEMENTED` after readiness.

## Persistence and editing

Edits change in-memory native state first. **Explicit native save** is required for durability; ordinary close discards unsaved edits. Requested shutdown supports `discard` (default), `save` or `refuse_if_dirty`, and conflicting active work returns `PROJECT_BUSY`.

Batches prevalidate every item before application. Unexpected execution failures report itemized applied/failed/skipped status rather than promising rollback. Declaration renames and one-line `LINE` comments are supported; parameter renames require a current source snapshot and a verified AUTO/RESTRUCTURE plain-signature target. Related-method propagation admits only independently verified closed-input families and remains `PARTIAL`. Local rename is unsupported because native VAR records can retarget under ordinary GUI settings.

Automatic aliases are derived and may recompute after edits; explicit native/mapping intent remains authoritative. [Editing evidence](docs/phase-5-editing.md), [parameter limits](docs/phase-5-scoped-editing.md) and [group admission](docs/phase-5-related-group-admission.md) describe the precise restrictions.

Mapping import stages pending native edits; mapping attachment uses project settings; mapping export creates a new local Tiny v2 artifact without saving or attaching it. Save refuses changed native project/mapping files with `EXTERNAL_MODIFICATION_CONFLICT`. Pending-edit export returns transient native data, and reload requires explicit discard when dirty. No database, autosave, persistent job journal or custom project sidecar is used. Native writes have no extra transactional recovery layer.

## Configuration and security

Choose `--project PATH` or repeatable `--input PATH` at startup. Configure `--port`, `--allowed-root` and optional YAML through `--config`. Precedence is **CLI > environment > YAML > defaults**. Native references remain at their original locations; canonical paths, including symlinks, must satisfy allowed-root checks.

Only `127.0.0.1` is accepted as the bind address. There is no authentication; use a trusted local environment. Runtime project switching and HTTP uploads are absent. An owner-only cursor signing key is operational state outside native project persistence. See [configuration](docs/configuration.md) for options, key location and shutdown behavior.

## Development and testing

Read [AGENTS.md](AGENTS.md), [DESIGN.md](DESIGN.md) and [IMPLEMENTATION.md](IMPLEMENTATION.md) before contributing. Public changes update OpenAPI, generated transport and contract fixtures together.

```bash
./gradlew clean test check installDist
python/.venv/bin/python tests/validate-documentation.py
cd python
uv run --frozen python scripts/check_generated.py
```

The comprehensive release gate includes all **12 matching-GUI tasks** and cross-language installed-artifact tests:

```bash
JADX_GUI=/path/to/jadx-1.5.6/bin/jadx-gui \
  tests/release/validate-release-candidate.sh all --output /tmp/libjadx-release-evidence
```

See the [release runbook](docs/phase-6-release-qualification.md) for cached tools, interpreters and evidence rules. The matching GUI is qualification infrastructure only, absent from production runtime. Documentation cleanup preserves PR #22's evidence; its strict fingerprint validator also hashes documentation, so later documentation edits do not pass that evidence's freshness check. See the [documented finding](docs/phase-6-release-qualification.md#documentation-cleanup-and-evidence-freshness).

## Roadmap

Phases 0–6 are complete for v0.1. Phase 7 covers deeper reference provenance and offsets, raw/transformed CFGs, Smali and richer representations, advanced annotations, resources/external classpaths, portable native export, optional isolated analysis workers and separately reviewed filesystem deletion. Each needs proof against the pinned Jadx release. One project per process, native persistence, explicit save and filesystem-only inputs remain architectural invariants.

See [Phase 7](IMPLEMENTATION.md#8-phase-7--extended-capabilities-post-initial-release--roadmap); the long-term roadmap is not complete.

## Documentation

The [documentation index](docs/README.md) separates current guidance from historical evidence:

- User docs: [configuration](docs/configuration.md) and [Python usage](python/README.md).
- Architecture: [design](DESIGN.md), [implementation history and roadmap](IMPLEMENTATION.md), [domain terms](CONTEXT.md).
- SDK/API: [OpenAPI](openapi/openapi.yaml) and [SDK architecture](docs/phase-6-python-sdk.md).
- Compatibility and limitations: [pins](docs/compatibility.md) and [feasibility matrix](docs/feasibility-matrix.md).
- Release evidence: [runbook](docs/phase-6-release-qualification.md) and [PR #22 report](docs/pr-22-review.md).
- Historical milestone and PR records: [index](docs/README.md#historical-milestone-evidence).

## License and attribution

Jadx is the implementation dependency; upstream and bundled dependency notices are included under [licenses](licenses/JADX-NOTICE). LibJadx has no root open-source license grant; the Python package uses the interim reserved-rights policy documented in [python/LICENSE](python/LICENSE) and [python/THIRD_PARTY.md](python/THIRD_PARTY.md).

libghidra is product inspiration only, not a dependency or bundled component. Its [license](https://github.com/0xeb/libghidra/blob/main/LICENSE) requires human review before any proposed reuse; implementation mining/copying is prohibited by the agent rules.
