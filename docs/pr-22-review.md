# PR #22 review — cross-language release qualification

Current status: **NOT QUALIFIED pending review-fix requalification**. The capability
snapshot correction supersedes the source fingerprint and results below. The full
candidate aggregate, including dedicated matching-GUI gates, must run again before
these evidence files are replaced and qualification is claimed.

Historical milestone: Phase 6.2, first experimental release qualification. **Outcome A at the earlier source below.**
Java candidate `0.1.0-alpha.1` / Python `0.1.0a1` is qualified at source commit
`f9803ca9c7608aa8909a5ab451580a9b439e133e`. API remains `0.1.0-experimental`.
Publication status: **NOT PUBLISHED**. No tag, GitHub release, artifact upload,
PyPI/Maven publication, remote CI result or independent reviewer run is claimed.

Base: `11b3a9d0f524d6be0053246603d581f8403571ea` (merged PR #21).
Jadx: `1.5.6`, pinned source `28ff15e4ae69950aebea110a13e5ab895d234dfc`.
JDK: OpenJDK `21.0.12.1+1-1-26.04.4-Ubuntu`; Gradle `8.14.3`; uv `0.8.22`.
Installed interpreters: CPython `3.11.13` and `3.14.4`, Linux.
Generator: `openapi-python-client 0.29.1`; Ruff `0.16.9`; mypy `1.19.1`;
pytest `8.4.2`; Hatchling `1.27.0`. Installed runtime dependencies: HTTPX
`0.28.1`, attrs `26.1.0`, typing-extensions `4.16.0`.

The final branch includes an evidence-only commit after the tested source head.
A report cannot contain its own Git commit hash. Every gate records the immutable
qualified source head and source fingerprint `d00927f8cdfdf646dbfb86af3eaf23b1be6b301d75f552f8bcdbc4a155ff72c3`.
Freshness validators accept a later Git descendant only when its complete source
fingerprint is unchanged and its diff contains exclusively `docs/pr-22-*` evidence
or `docs/changelog.md`. Code and artifact-input changes invalidate qualification.
The aggregate ran with a clean working tree at the source head above; earlier
runs are superseded and are not the evidence reported here.

## Implemented

Added a reproducible, fail-closed local release aggregate and installed-artifact
qualification for all 22 reviewed operations through raw HTTP, generated sync,
generated async, handwritten sync and handwritten async clients. Schema validation
and same-invocation comparisons preserve every wire field: provenance, diagnostics,
partial/strict outcomes, source snapshots, revisions, cursors, itemized receipts,
request IDs and typed error details. Only equivalent UUID/datetime representation
is normalized. The public OpenAPI paths, schemas and API version are unchanged.
Convenience-layer gaps use documented generated escape hatches, including pagination
and override payloads; they do not imply new handwritten convenience APIs.

All 19 published stable errors require an actual Java response on each surface.
Owned filesystem test hooks, disabled unless explicitly configured at JVM startup,
make lifecycle, admission, cancellation, streaming, staged-edit and server-error
conditions deterministic. They add no HTTP control endpoint, production default,
native project field or persistent metadata. Normal repeated cancellation remains
idempotent. `CANCELLATION_PENDING` is exercised only through the documented owned
Java fault while a real job is CANCELLING. `OPERATION_NOT_IMPLEMENTED` is an actual
Java response outside the 22 first-release routes. Generated default decoding for
these cases uses an explicitly documented test-owned HTTPX request hook.

CLI and capabilities share the Gradle-generated candidate version. Java archives
use fixed timestamps and order; required Jadx license/notice files are included.
The archive audit selects the outer format explicitly: a TAR containing a JAR can
also satisfy ZIP detection. A regression proves the correct outer file count.
A second executable regression proves evidence-only freshness acceptance and
source-change rejection using a real temporary Git repository.

## Gate results

Java `./gradlew clean check --offline --rerun-tasks`: **487 tests in 90 suites;
471 passed, 16 opt-in GUI skips, zero failures/errors**. The dedicated matching-GUI
aggregate then reran all 16 GUI tests with **zero skips**, as recorded below.
The two release-evidence regressions passed.

OpenAPI 3.1 validated all 22 unique operations, 35 referenced JSON examples and
three SSE example frames. Existing live validators passed unchanged:

- Edit: 16 examples, 20 live responses and four injected results; scoped: 18 live
  responses; related propagation: 36 live responses.
- Mapping export: three examples and 31 live responses.
- Mapping import: four examples and 41 live responses.
- Search: four examples and 33 live responses.

Frozen offline generation reproduced all 162 generated files. Ruff formatting and
lint passed; mypy passed for all 12 handwritten source modules. Both installed
interpreters passed the entire unit/integration/release suite, without skips:

| Interpreter | Passed | Failures / errors / skips | Owned Java processes | Maximum observed RSS | Maximum observed readiness |
| --- | ---: | --- | ---: | ---: | ---: |
| CPython 3.11.13 | 305 | 0 / 0 / 0 | 203 | 709,864 KiB | 10.701 s |
| CPython 3.14.4 | 305 | 0 / 0 / 0 | 203 | 683,448 KiB | 10.874 s |

Each interpreter covers 168 unit, 10 existing integration and 127 release tests.
Each independently validates **22/22 operations and 19/19 errors on all five
surfaces**. Tests run from a temporary directory against a clean installed wheel
and extracted Java ZIP. An additional extracted-TAR smoke passed both raw HTTP
and generated async vertical slices (two selected tests, four deliberately
unselected cases). Installed module/distribution version equality and extracted
CLI version were independently checked outside the checkout.

Resource observations are bounded fixture measurements, not performance guarantees.
All recorded process exits were 0 or 143; no mandatory case needed a forced kill.
Primary Jadx reads remain serialized; overlapping HTTP clients do not establish
parallel Jadx-read safety. Cold/warm source reads and the owned 145-class workload
are smoke coverage, not broad throughput claims.

Lifecycle and concurrency gates prove LOADING/READY/FAILED, disabled hooks in an
ordinary process, serialized same/different-class admission, two-client stale
conflicts, dirty/clean shutdown policies, busy rejection and restart. Cancellation
covers admitted QUEUED and RUNNING work and honest nonterminal CANCELLING. Client
deadlines, async task cancellation and stream closure do not silently cancel the
server job. Real Java SSE covers replay, expired history, 15-second heartbeats,
eight subscribers plus ninth-subscriber rejection, bounded queue overflow and
reconnect; quiet sync/async streams exceed the normal 10-second request timeout.
Private edit failure preserves the exact APPLIED prefix, FAILED item and SKIPPED
suffix, item messages and causes; the accepted prefix survives explicit save.

Security gates cover loopback binding, allowed roots, symlink escapes, malformed
startup/input/native data, CLI/environment/YAML precedence, malformed/duplicate/
unknown JSON fields, wrong content types, wrong methods with Allow headers,
numeric limits, absent CORS and forbidden upload/open/switch paths. The existing
oversized 64-KiB JSON behavior remains structured 400; no invented 429 is claimed.
Failed startup shutdown retains the existing structured 503; the test uses ordinary
SIGTERM cleanup rather than changing that contract.

## Persistence and matching GUI

Owned `qualification.jar`, `workload.jar`, `owned.dex`, `unused-catch.dex`,
`probe/Unicode.class`, and the existing native project fixture exercise genuine
pinned Jadx. Tests verify Unicode/space paths, original identities, unsaved
rename/comment snapshots in isolated overrides, native explicit save, dirty
shutdown discard/save/refuse, saved restart, mapping attach/remove/import/export,
opaque native JSON retention (including large integer values), stale parameter
snapshots, rejected generic/unused-catch/local cases and the conservative COMPLETE
related-method subset. External native, input and mapping changes reject mutation
or save without losing pending state. No background autosave, alternate native
format or extra transactional recovery layer was added.

Pinned adapter evidence remains in [compatibility](compatibility.md) and the
existing phase-5 probe reviews. Upstream source at the pin includes
`jadx-gui/src/main/java/jadx/gui/settings/JadxProject.java` and
`jadx-gui/src/main/java/jadx/gui/settings/data/ProjectData.java`; release fixtures
and runtime integration use those established native serialization semantics.
This qualification adds no new speculative Jadx API usage.

`./gradlew releaseGuiQualification --offline --rerun-tasks --no-parallel -x test`
passed all 12 tasks against actual Jadx GUI 1.5.6 under Xvfb. Every test retained
its positive or negative diagnostic expectation. The log records **27 actual
Save As processes and 27 freshly produced native projects**, whose hashes are
retained in [machine validation evidence](pr-22-validation.json).

| Matching-GUI task | Passed | Failed / errors | Skipped |
| --- | ---: | --- | ---: |
| `guiRoundTripTest` | 1 | 0 | 0 |
| `rawGuiRoundTripTest` | 1 | 0 | 0 |
| `nativeEditGuiRoundTripTest` | 1 | 0 | 0 |
| `mappingExportGuiRoundTripTest` | 1 | 0 | 0 |
| `mappingImportGuiRoundTripTest` | 1 | 0 | 0 |
| `scopedEditGuiRoundTripTest` | 1 | 0 | 0 |
| `relatedPropagationGuiRoundTripTest` | 1 | 0 | 0 |
| `propagatedEditReplayGuiDiagnosticTest` | 1 | 0 | 0 |
| `safeReplayGuiDiagnosticTest` | 1 | 0 | 0 |
| `replacementEditGuiRoundTripTest` | 1 | 0 | 0 |
| `propagatedEditGuiRoundTripTest` | 1 | 0 | 0 |
| `localRenameGuiDiagnosticTest` | 5 | 0 | 0 |

GUI launcher SHA-256: `994672f5ffa9af6aac8f384b4b7eae5fbffd91f28874dfa3688d8177f52dd14d`.
Cached upstream GUI distribution SHA-256:
`545ea2be9c242511bc145755cf4bda2485ade42966e096f8b4d3da2a230e8974`.

## Candidate artifacts

All four archives were built, deleted and rebuilt with identical bytes and hashes.
Manifest audit covers launcher versions, pinned plugins, licensing, `py.typed`,
Python metadata, dependency locks, generated-tree hash and exclusions of GUI,
Git/cache data and Java probe/test classes. The TAR has **69 outer files**.
The wheel has 181 files and sdist has 203; Python source tests are intentionally
included in the sdist, with development-only requirements separate from runtime.

| Archive | Bytes | Outer files | SHA-256 |
| --- | ---: | ---: | --- |
| `libjadx-0.1.0-alpha.1.zip` | 51,537,781 | 69 | `633df25165f908d50e76cc552c62e1cbc88c02442e7a635f6b362e68376eda6f` |
| `libjadx-0.1.0-alpha.1.tar` | 59,785,728 | 69 | `992ff9b822d2996886bb9455e92a3f7d01dcefbe3e2cb2a39447c1323894b024` |
| `libjadx-0.1.0a1-py3-none-any.whl` | 170,251 | 181 | `107b3bb4be278b4e3e7cf7633fb009d182c32537ceaa7969975594b76d5557f1` |
| `libjadx-0.1.0a1.tar.gz` | 164,552 | 203 | `9d68d7a187b7d36bccf93fa995de3567943d0df9076eb8ee2e242b99512bce80` |

Artifacts remain local under `build/distributions/` and `python/dist/`; binaries
are not committed. [Artifact manifest](pr-22-artifacts.json) retains full Python
file lists and build metadata. [Route matrix](pr-22-route-matrix.json),
[error matrix](pr-22-error-matrix.json), [validation record](pr-22-validation.json)
and [ordinary-process capabilities](pr-22-capabilities.json) retain normalized
observations from the qualified source. No personal home paths are included.

## Reproduce

With the pinned offline dependencies, uv, both interpreters and matching GUI
available, the author ran this exact aggregate from the repository root (tool and
output paths below use portable placeholders):

```sh
UV_BIN=<uv> RELEASE_PYTHON=<repo>/python/.venv/bin/python \
JADX_GUI=<jadx-1.5.6>/bin/jadx-gui \
JADX_GUI_ARCHIVE=<jadx-1.5.6.zip> \
tests/release/validate-release-candidate.sh all --output <evidence-dir>
```

The aggregate invokes these commands, each recorded with PASS and elapsed time:

```text
./gradlew clean check --offline --rerun-tasks
./gradlew installDist distZip distTar --offline --rerun-tasks
<uv> sync --frozen --all-groups --offline
<repo>/python/.venv/bin/python <repo>/tests/release/test_evidence.py
<repo>/python/.venv/bin/python <repo>/tests/validate-edit-contract.py
<repo>/python/.venv/bin/python <repo>/tests/validate-mapping-export-contract.py
<repo>/python/.venv/bin/python <repo>/tests/validate-mapping-import-contract.py
<repo>/python/.venv/bin/python <repo>/tests/validate-search-contract.py
<repo>/python/.venv/bin/python <repo>/tests/validate-openapi.py
<uv> run --frozen --offline python scripts/check_generated.py
<uv> run --frozen --offline ruff format --check .
<uv> run --frozen --offline ruff check .
<uv> run --frozen --offline mypy src/libjadx
<uv> build --offline
<repo>/python/.venv/bin/python <repo>/python/scripts/artifact_manifest.py
./gradlew distZip distTar --offline --rerun-tasks
<uv> build --offline
<uv> export --project <repo>/python --frozen --all-groups --no-emit-project --no-hashes --output-file <installed-temp>/requirements.txt
<uv> venv --python 3.11 <installed-temp>/python-3.11 --offline
<uv> pip install --offline --python <installed-temp>/python-3.11/bin/python -r <installed-temp>/requirements.txt <repo>/python/dist/libjadx-0.1.0a1-py3-none-any.whl
<uv> venv --python 3.14 <installed-temp>/python-3.14 --offline
<uv> pip install --offline --python <installed-temp>/python-3.14/bin/python -r <installed-temp>/requirements.txt <repo>/python/dist/libjadx-0.1.0a1-py3-none-any.whl
<installed-temp>/python-3.14/bin/python -m pytest -q <repo>/python/tests/unit <repo>/python/tests/integration <repo>/python/tests/release --junitxml=<evidence-dir>/python-3.14.xml
<repo>/python/.venv/bin/python <repo>/tests/validate-release-route-matrix.py <evidence-dir>/matrix-3.14/routes.json
<repo>/python/.venv/bin/python <repo>/tests/validate-release-error-matrix.py <evidence-dir>/matrix-3.14/errors.json
<installed-temp>/python-3.11/bin/python -m pytest -q <repo>/python/tests/unit <repo>/python/tests/integration <repo>/python/tests/release --junitxml=<evidence-dir>/python-3.11.xml
<repo>/python/.venv/bin/python <repo>/tests/validate-release-route-matrix.py <evidence-dir>/matrix-3.11/routes.json
<repo>/python/.venv/bin/python <repo>/tests/validate-release-error-matrix.py <evidence-dir>/matrix-3.11/errors.json
<installed-temp>/python-3.14/bin/python -m pytest -q <repo>/python/tests/release/test_routes.py -k raw_http or generated_async --junitxml=<evidence-dir>/tar-smoke.xml
git diff --check
./gradlew releaseGuiQualification --offline --rerun-tasks --no-parallel -x test
```

See [release qualification runbook](phase-6-release-qualification.md) for required
prerequisites, isolated fault hooks, source-freshness rules and supported escape
hatches. Committed matrices can be checked with
`python/.venv/bin/python tests/validate-release-route-matrix.py` and
`python/.venv/bin/python tests/validate-release-error-matrix.py`.

## Supported state and next milestone

Qualified locally: standalone headless loopback service, one fixed project,
class/JAR/DEX/native fixture inputs, Java source, explicitly partial references
and incremental search, declaration edits/comments, plain AUTO/RESTRUCTURE
parameter subset, conservative independently COMPLETE related families, explicit
native save, jobs/SSE and synchronous/asynchronous Python on Linux 3.11/3.14.
Only tested formats/platforms are qualified; bundled plugin availability does
not qualify untested containers or Windows execution.

Partial or unavailable: reference/search completeness, input provenance,
source-to-bytecode/debug coordinates, related-group scope and cooperative
cancellation during uninterruptible Jadx work. Capability statuses remain
conservative; cancellation stress does not upgrade the adapter's UNKNOWN
JVM-interruption capability. An internal Smali probe does not expose a Smali API.

Unsupported or outside this release: local rename (retained GUI retargeting
negative), remote hosting/authentication, uploads, runtime project switching,
CFG/resources/Smali API representations, portable project export and Python
service process management. Jobs/indexes stay in memory; native edits require
explicit save and retain the existing interruption limits.

Blocking issues: none in the required local qualification matrix. Phase 6.2 is
complete. A separate human instruction can authorize publication or the next
Phase 7 milestone; this implementation performs neither.
