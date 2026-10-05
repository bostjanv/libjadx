# Cross-language release qualification

PR #24 freshly qualified the Apache-2.0 alpha locally. The
[current validation record](release-evidence/0.1.0-alpha.1/validation.json)
identifies the exact qualified source HEAD and source SHA-256; the
[qualification report](release-evidence/0.1.0-alpha.1/review.md) records
commands, results and limits. PR #22 reports are immutable historical evidence.
Publication remains a separate explicit action.

## Current PR #24 qualification

**Status: PASSED locally under Apache-2.0; NOT PUBLISHED.** The
[current validation record](release-evidence/0.1.0-alpha.1/validation.json)
records the exact qualified source HEAD in `head` / `qualification_heads` and
its `source_sha256`. The [report](release-evidence/0.1.0-alpha.1/review.md),
[artifact manifest](release-evidence/0.1.0-alpha.1/artifacts.json),
[route matrix](release-evidence/0.1.0-alpha.1/route-matrix.json),
[error matrix](release-evidence/0.1.0-alpha.1/error-matrix.json) and
[capabilities](release-evidence/0.1.0-alpha.1/capabilities.json) are current
PR #24 evidence. This is author-run local qualification, with no remote CI or
independent reviewer rerun claimed.

Only that recorded source, or an evidence-only descendant that passes freshness
validation, is qualified. Ordinary documentation changes require a new source
commit and the full gate. Current status documents link to the validation record
for the source HEAD; it is recorded after the source commit has been tested,
without embedding a self-referential commit hash in the hashed documentation.

Product candidate: `0.1.0-alpha.1`
Python package: `0.1.0a1`
API contract: `0.1.0-experimental`
Jadx: `1.5.6` / `28ff15e4ae69950aebea110a13e5ab895d234dfc`

Candidate identity is separate from qualification and external publication.
This milestone prepares local archives only. No tags, uploads or publication.

The release harness exercises raw HTTP, generated detailed sync/async transport,
and handwritten sync/async APIs against extracted Java distributions and an
installed wheel outside the checkout. Native state always uses isolated owned
copies, port 0 and explicit saves. The operation inventory comes from OpenAPI.
The raw HTTP client constructs its own requests without generated models.

Release instrumentation is disabled unless the process is explicitly started
with `-Dlibjadx.releaseTestDir=<tmp>/hooks`. No HTTP request can configure it.
`<point>.hold` holds a bounded 90-second noninterruptible region, writes
`<point>.entered`, and continues when the controller removes the hold file.
Points: startup, primary-read (inside admission), edit (after prevalidation),
dispatch (before worker admission), job (inside the real index worker),
http-project (request timeout), shutdown (after 202 acknowledgement),
sse-delivery (after an actual event flush). `startup.fail`, `job.fail`, `edit-item-N.fail`
and `http-project.fail` are owned fault injection. `cancel-pending.fail` returns
the published CANCELLATION_PENDING error only after real cancellation reaches
CANCELLING. Normal repeated cancellation remains idempotent HTTP 200. All files are operational test
artifacts and are absent from native projects and public OpenAPI.

Primary Jadx reads remain conservatively serialized. Concurrent source operations
may return PROJECT_BUSY; this candidate does not claim parallel Jadx safety.
Cancellation intent stays CANCELLING while a held region owns its lease.
Client HTTP/wait deadlines, task cancellation and SSE disconnect never request
server cancellation. SSE read inactivity remains disabled while other timeouts
retain their configured values.

Public capabilities describe the service contract rather than an internal Jadx
feasibility probe. `code.smali=UNSUPPORTED` means no public Smali representation;
`analysis.concurrent_reads=UNSUPPORTED` means parallel primary Jadx reads are
disabled, with serialized/fail-fast PROJECT_BUSY admission (multiple HTTP clients
are supported). `analysis.cancellation=PARTIAL` describes cooperative queued/running
job cancellation and truthful CANCELLING until work stops; it does not guarantee
hard interruption of arbitrary Jadx work. The reviewed OpenAPI capability example
defines the complete candidate snapshot, including status, evidence and persistence.
Every live capabilities response on all five surfaces is checked against it.
Both interpreter runs must capture the same full snapshot from an ordinary process
with test hooks disabled; the aggregate validates those machine records separately.

Headless native saving retains unknown fields where promised; matching Jadx GUI
serialization may drop fields it does not recognize. Native write interruption
is not transactional. GUI qualification runs all twelve existing top-level gates
in serial order. Each actual Save As emits a process ID and output path; the
aggregate checks 27 matching log entries against 27 newly created projects.

Linux JDK 21 and CPython 3.11/3.14 are the intended qualification matrix. Installed
Jadx plugins and qualified input formats are reported separately. Local-variable
renames, remote hosting/auth, project switching/uploads, CFG/resources/Smali HTTP
representations, portable project export and Python process management remain
unsupported. Related propagation and references retain their partial boundaries.


## Repeatable gate

Bootstrap the exact cached Gradle dependencies and frozen uv environment first.
The qualification itself uses offline Gradle/uv and loopback requests. It does
not download runtime dependencies. Install JDK 21, uv 0.8.22, cached CPython
3.11 and 3.14, matching Jadx GUI 1.5.6, xvfb-run, xdotool, xclip and ImageMagick.
`UV_BIN` may select the uv executable; `RELEASE_PYTHON` selects the orchestration
interpreter (standard library only).

```bash
JADX_GUI=/path/to/jadx-1.5.6/bin/jadx-gui \
  tests/release/validate-release-candidate.sh all --output /tmp/libjadx-release-evidence
```

Stages `core`, `gui` and `result` allow separate invocations, in that order. `result` requires both
stages to pass with the exact source fingerprint and qualified source head.
Only a descendant containing evidence files under `docs/release-evidence/**` or
`docs/changelog.md` may
reuse the result; its Git ancestry and unchanged full source digest are verified.
This permits committing reports without claiming their self-referential commit
hash was tested. Any source or artifact-input change fails the freshness gate. Core alone prints
NOT QUALIFIED. A failed gate returns nonzero and retains its log and blocker.
Evidence lives outside build/ so `clean` cannot erase it. Core clean check prepares all owned GUI inputs at the exact source revision.
GUI requires that passing core evidence and runs all dedicated Exec/Test tasks
with `--rerun-tasks -x test`; the shared Java test task is excluded to preserve
those fresh inputs. Stale Save As outputs are removed.

Core runs clean Java check, distribution construction, license/documentation
validation (including documentation self-tests), four existing contract
validators, OpenAPI and external examples validation, frozen generator drift,
Ruff formatting/lint and mypy, artifact content/license audits, repeat builds,
and installed-wheel unit/integration/release suites. Archive metadata uses the
outer filename format: a TAR containing JARs also passes ZIP signature detection.
Owned archive/Git regressions check file counts and fail-closed freshness. Fresh 3.11 and 3.14 venvs
launch the extracted ZIP outside the checkout. Extracted TAR runs representative
raw and generated-async routes. Every mandatory installed test must run without
skips. Wheel/sdist and Java ZIP/TAR must repeat byte for byte. Both Java outer formats must contain exact root LICENSE
bytes and all separate dependency notices; the wheel must declare
`License-Expression: Apache-2.0`. A deterministic `SHA256SUMS` is written outside
the repository for the four candidate artifacts.

`python/tests/release_support.py` derives the operation inventory from OpenAPI.
Every actual response is schema validated; parsed models and handwritten wrappers
are compared with the complete wire body from that same invocation. UUID and
RFC3339 representation normalization preserves every semantic field. Live cursor,
provenance, status, coverage, diagnostic, error detail, cause and item-error fields
are never omitted. Raw HTTP builds JSON requests independently. Stock generated
SSE transport returns text; handwritten SSE parsing is compared with the retained
Java event history. Convenience gaps use the documented low-level escape.

Route/error validators derive inventories from the reviewed contract and reject
missing rows/surfaces. Final inventory tests reject missing positive/negative
operations and any stable code without real Java or explicit Java fault evidence.
METHOD_NOT_ALLOWED, planned OPERATION_NOT_IMPLEMENTED and test-only
CANCELLATION_PENDING use an owned HTTPX request hook with an existing generated
default-error decoder; their actual method/path and fault flags are recorded.
Error samples distinguish `decoder_operation` from `wire_operation`, resolved
from the actual HTTP request. A planned wire route has a null wire operation and
retains its real method/path rather than being mislabeled as the decoder's route.
They do not add planned routes to the first-release OpenAPI contract.

JSON evidence preserves request IDs and normalizes owned filesystem prefixes to
`<tmp>`, `<tmp-parent>` and `<repo>`. Artifact manifests distinguish byte hashes
from validation timing. The exact Git revision and source fingerprint identify
the gate; subsequent source changes invalidate it. Review evidence records
whether runs were author-run, CI or independent reviewer work.

CLI precedence is CLI > environment > YAML > defaults, including selection,
bind, port and allowed roots. The extracted-launcher tests exercise precedence
without accepting remote binds or resolving invalid lower-priority paths.

LibJadx candidate artifacts carry Apache-2.0; bundled and runtime dependencies
retain their own licenses/notices. Publication remains a separate explicit action.
This workflow creates no tag, release, registry upload, branch merge or deployment.
See the [alpha publication checklist](alpha-publication-checklist.md) for later
human-approved publication.

## Documentation cleanup and evidence freshness

PR #23 preserved the PR #22 evidence bytes and qualified source identity. Its
ordinary documentation changes invalidated the historical source fingerprint,
even though runtime/OpenAPI/generated transport were unchanged. The old rule
allowed only PR #22 report files and changelog descendants. The historical
reports remain immutable audit records for their original source/artifacts.

PR #24 replaces that PR-specific rule with `docs/release-evidence/**` and
`docs/changelog.md`. `source_hash()` includes all other tracked and unignored
inputs, including README, LICENSE, package metadata, ordinary Markdown and
validator scripts. Qualification runs on a clean candidate source commit; a
subsequent evidence-only descendant must preserve that digest and Git ancestry.
Any other post-qualification path invalidates the result, including historical
PR report edits. `tests/release/test_evidence.py` exercises exact heads, versioned
evidence/changelog descendants and rejected source/config/documentation changes.

New reports live under `docs/release-evidence/0.1.0-alpha.1/`. The qualified source
commit identifies tested artifact inputs; an evidence-only descendant contains
reports and may become the final PR head after freshness validation. No report
can include its own commit hash. All ordinary documentation, license, packaging
and release-note draft changes must be committed before running the full gate.

## Historical PR #22 qualification

**Historical PR #22 status: PASSED** for source head `55e46eb707d095abdb067424db77f51a31558e17`,
as recorded by PR #22 and merged to main. Candidate remains **NOT PUBLISHED**
and not tagged. See the [qualification report](pr-22-review.md),
[validation record](pr-22-validation.json) and [artifact manifest](pr-22-artifacts.json).
The main merge SHA is not the tested source SHA.
