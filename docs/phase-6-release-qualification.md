# Cross-language release qualification

**Status: PASSED** for source head `55e46eb707d095abdb067424db77f51a31558e17`,
as recorded by PR #22 and merged to main. Candidate remains **NOT PUBLISHED**
and not tagged. See the [qualification report](pr-22-review.md),
[validation record](pr-22-validation.json) and [artifact manifest](pr-22-artifacts.json).
The main merge SHA is not the tested source SHA.

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
Only a descendant containing evidence files under docs/pr-22-* or changelog may
reuse the result; its Git ancestry and unchanged full source digest are verified.
This permits committing reports without claiming their self-referential commit
hash was tested. Any source or artifact-input change fails the freshness gate. Core alone prints
NOT QUALIFIED. A failed gate returns nonzero and retains its log and blocker.
Evidence lives outside build/ so `clean` cannot erase it. Core clean check prepares all owned GUI inputs at the exact source revision.
GUI requires that passing core evidence and runs all dedicated Exec/Test tasks
with `--rerun-tasks -x test`; the shared Java test task is excluded to preserve
those fresh inputs. Stale Save As outputs are removed.

Core runs clean Java check, distribution construction, four existing contract
validators, OpenAPI and external examples validation, frozen generator drift,
Ruff formatting/lint and mypy, artifact content/license audits, repeat builds,
and installed-wheel unit/integration/release suites. Archive metadata uses the
outer filename format: a TAR containing JARs also passes ZIP signature detection.
Owned archive/Git regressions check file counts and fail-closed freshness. Fresh 3.11 and 3.14 venvs
launch the extracted ZIP outside the checkout. Extracted TAR runs representative
raw and generated-async routes. Every mandatory installed test must run without
skips. Wheel/sdist and Java ZIP/TAR must repeat byte for byte.

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

Publication is explicitly outside this workflow: it creates no tag, release,
registry upload, branch merge, deployment or license grant. LibJadx has no root
open-source license grant; Python retains LicenseRef-Proprietary. Upstream notices
are distributed without suggesting endorsement. Future publication requires the
human to choose its channels and license policy separately.

## Documentation cleanup and evidence freshness

PR #23 preserves the PR #22 evidence bytes and qualified source identity. The
existing `tests/release/qualify.py` `source_hash()` hashes ordinary Markdown and
validator scripts as well as runtime inputs. `validate_evidence_identity()` only
permits descendants changing `docs/pr-22-*` or `docs/changelog.md`. Therefore the
broader documentation cleanup is outside its evidence-only descendant rule:
`validate-release-capabilities.py`, `validate-release-route-matrix.py` and
`validate-release-error-matrix.py` reject the cleaned checkout with
`Superseded-source evidence` even though runtime/OpenAPI/generated transport are
unchanged. All three passed on the unchanged PR #22 main merge before cleanup.

This is a separate documentation-work-order/freshness-policy mismatch. The work
order expected documentation-only descendants to be accepted, but the existing
executable rule does not accept them. This PR does not weaken that rule, regenerate
release evidence, replace the recorded source SHA with a documentation SHA or
claim a new runtime/GUI qualification. PR #22 evidence remains an audit record
for its qualified source/artifacts. Qualification of a later release revision
requires the full existing gate; changing the freshness policy is separate work.
