# PR #21 — Python SDK foundation review

Outcome A: Phase 6.1 foundation implemented for pull-request review.
Phase 6.2 exhaustive cross-language release qualification remains pending.
No package publication, tag, release candidate, remote CI result or independent
review is claimed.

## Validated identity and tooling

- Base commit: `02d896a3d6fe085338004e13c546ded42bba9e65` (merged PR #20).
  Validation ran against the implementation working tree on this base, which
  is committed on `pr-21-python-sdk-foundation` for GitHub review. The exact
  validated SDK source/test snapshot is identified by wheel/sdist hashes below;
  subsequent handoff documentation changes do not change those package bytes.
- OpenAPI: 3.1.0, API 0.1.0-experimental, SHA-256
  `b29e71c5ac6e24411c82f9afd3afe61d90475779e4b3ee53393b2714dde9f12b`.
  Schema/Java behavior unchanged. `job-events.sse` was corrected to include
  required JobEvent.progress and the final empty-line frame delimiter.
- Jadx: 1.5.6, source `28ff15e4ae69950aebea110a13e5ab895d234dfc`.
  The prior [native/GUI source audit](pr-20-source-audit.json) and
  [editing boundary](phase-5-local-rename-feasibility.md) remain authoritative.
  Inspected existing `src/main/java/dev/libjadx/{app,http,scheduler}` startup,
  configuration, servlet, project/revision, edits, search, jobs and shutdown
  implementations; no Java source/dependency/lock changed.
- Python: CPython **3.11.13**, **3.14.4**, Linux x86_64. Minimum metadata 3.11.
  JDK 21.0.12.1, Gradle 8.14.3.
- Generator: **openapi-python-client 0.29.1**, patched security release;
  stock templates with deterministic isolated ruff hooks, no manual edits.
- uv **0.8.22**, hatchling **1.27.0**, ruff **0.16.9**, mypy **1.19.1**,
  pytest **8.4.2**, pytest-asyncio **1.2.0**.
- Runtime: httpx **0.28.1**, attrs **26.1.0**, typing-extensions **4.16.0**;
  all runtime/dev transitives and platform markers in `python/uv.lock`.
- License: `LicenseRef-Proprietary` / all rights reserved, explicitly selected
  by the human for now. The upstream generator's MIT notice is included.
- Name availability check: PyPI `/pypi/libjadx/json` returned 404; nothing published.

## Reproducibility and artifacts

[Machine-readable artifact manifest](pr-21-artifacts.json) includes complete
wheel/sdist file lists, runtime dependency metadata and hashing algorithm.

| Input/artifact | SHA-256 |
|---|---|
| Generator config | `7d1237c63c54bb9176d91a8f1a57d68091444e9982afcf69a2d4e8fbf20bb83d` |
| uv.lock | `30d342706ee9c263e1a7c4392ca60c40e7ae454f600b4264919569ad4f9eef5e` |
| Generated tree (162 Python files) | `214c7197183c22702b2cf07ff0d37818ba0ce4d201490f0ab4590f4d9b17c20a` |
| libjadx-0.1.0a1-py3-none-any.whl | `dd1b807fcfc6385672ea8498bd381469bcf6f38b333751a9bcafec1ef16c8913` |
| libjadx-0.1.0a1.tar.gz | `96b93261573091dc914187c6a2fa63d443c257e54f8be22bd4fc3b68fb410155` |

Two final `uv build` runs produced byte-identical artifacts; both complete
manifests compared equal using `cmp`. This is local repeat-build evidence,
not an untested cross-platform reproducibility claim. Wheel: 181 files;
sdist: 193 files. The wheel contains SDK/generated source, py.typed, three
license/attribution documents and metadata. It contains no tests, JARs, class
files, bytecode caches or Gradle artifacts. Only HTTPX/attrs/typing-extensions
are direct runtime dependencies. Artifacts are under `python/dist/` (ignored).

## Commands actually run and outcomes

All results below are author-run local tests, not remote CI or independently
rerun review. The temporary uv executable was `/tmp/libjadx-pr21-tools/bin/uv`;
commands below use `uv` as shorthand for that exact executable.

| Command | Observed outcome |
|---|---|
| `./gradlew clean check --offline --rerun-tasks` | PASS, 12m59s; 88 XML suites, 485 discovered, 469 passed, 16 opt-in GUI/GUI diagnostics skipped, zero failures/errors |
| `./gradlew installDist --offline` | PASS, 7s, distribution up to date after clean check |
| `python/.venv/bin/python tests/validate-edit-contract.py` | PASS: 16 examples, 20 live responses, four injected service results, 18 scoped and 36 related responses; exact family/no-op checks |
| `python/.venv/bin/python tests/validate-mapping-export-contract.py` | PASS: three examples, 31 live responses |
| `python/.venv/bin/python tests/validate-mapping-import-contract.py` | PASS: four examples, 41 live responses |
| `python/.venv/bin/python tests/validate-search-contract.py` | PASS: four examples, 33 live responses |
| `cd python && uv sync --frozen --all-groups` | PASS, exact locked development environment |
| `uv run python scripts/check_generated.py` (Python cwd) | PASS, 162 generated files match; check does not mutate checkout |
| `uv run ruff format --check .` / `uv run ruff check .` | PASS, 22 handwritten/script/test files formatted, no lint errors |
| `uv run mypy src/libjadx` | PASS, strict checking of all 12 handwritten modules; generated annotations followed silently |
| `uv run pytest -q tests/unit` | PASS, 162 unit/model/transport cases on development 3.14.4 |
| `uv build` / `uv run python scripts/artifact_manifest.py` | PASS, wheel built from sdist; metadata/file inspection and repeated-build comparison passed |
| `git diff --check` and `git diff --check origin/main...HEAD` | PASS on the validated patch; staged patch whitespace also checked before committing for review |

Independent Draft202012Validator validation also passed for each of the three
corrected SSE example data frames against the unchanged JobEvent schema.
Existing Java captures came from this clean run, not retained previous captures.

Clean installed-wheel verification used both isolated environments, not the
editable development install. Exact orchestration (run from `/tmp`):

```bash
uv export --project /home/alice/projects/libjadx/python --frozen --all-groups \
  --no-emit-project --no-hashes --output-file /tmp/libjadx-pr21-requirements.txt
uv venv --python 3.11 /tmp/libjadx-pr21-py311
uv venv --python /usr/bin/python3 /tmp/libjadx-pr21-py314
# For each suffix 311 and 314 (each final SDK wheel was installed freshly):
uv pip install --python /tmp/libjadx-pr21-py311/bin/python \
  --reinstall-package libjadx -r /tmp/libjadx-pr21-requirements.txt \
  /home/alice/projects/libjadx/python/dist/libjadx-0.1.0a1-py3-none-any.whl
LIBJADX_EXPECT_INSTALLED=1 /tmp/libjadx-pr21-py311/bin/python -m pytest -q \
  /home/alice/projects/libjadx/python/tests \
  --junitxml=/tmp/libjadx-pr21-py311-results.xml
# The same pip-install/pytest commands were run with py314 paths/results.
```

Both final environments passed **172 tests = 162 unit + 10 live integration**,
zero failures/skips. Python 3.11.13: 88.93s; Python 3.14.4: 88.14s. Tests assert
that import resolves to actual `site-packages`, not `python/src`. Temporary
install/test logs and JUnit files are `/tmp/libjadx-pr21-py{311,314}-{install,tests}.log`
and `/tmp/libjadx-pr21-py{311,314}-results.xml`. Both clean installs use exact
locked versions, with no unrelated environment packages supplying SDK imports.

## Implemented and exercised

One distribution with separate machine-owned `_generated` and handwritten
Client/AsyncClient, Project/AsyncProject, original SymbolRef helpers and typed
sync/async symbols, lazy search/class/reference pages, jobs, typed errors,
bounded SSE and explicit polling fallback. The generator spike tests all 22
operation IDs, reviewed response/request examples, hard 3.1 types/default
responses and sync/async transport. Stable error-code coverage is checked
against the generated enum, preserving details/causes/item errors and both
request-ID sources. Constructors/imports have no network calls.

Owned `tests/fixtures/native-project/sample.jar` powers actual installDist
subprocesses, each on port 0 with isolated server files/operational state. The
live tests cover generated low-level liveness/capabilities/class/method/source
and unsupported response; sync save/restart and reload; async analysis/edit/save;
sync and async two-client stale conflicts without retry; external modification
with retained pending edits; honest PARTIAL/strict references; real search/index
202 jobs, polling, SSE replay/reconnect and terminal cancellation semantics;
strict mapping import/export and cached receipt revision followed by native save.

Unit tests cover out-of-order monotonic cache updates and retired boots, explicit
precondition overrides/unconditional pairs, scoped snapshot request shape,
related METHOD-only checks, all stable error mappings, readiness failure/deadline,
job terminal/timeout states, async local cancellation, repeated/stale cursors,
abandoned iteration/no prefetch, early SSE close, heartbeat/chunk boundaries,
malformed/wrong-job/type/sequence/oversized frames, preserved opaque JSON,
PARTIAL source provenance and bounded representations. See the
[public parity matrix](phase-6-python-sdk.md#high-level-parity-and-validation).

## Persistence, exclusions and handoff

Live native save verified dirty state without an auto-created project, explicit
creation and native rename records, service restart restoring aliases, and
reload after save. Mapping import stayed unsaved until explicit native save.
Context cleanup performed no save/shutdown/cancel. External-modification refusal
retained pending state. Native GUI gates are **not renewed** by this Python PR;
the 16 skips are the existing opt-in GUI round trips/diagnostics listed in
`build/test-results/test/`. This follows PR #21's exemption for unchanged
Java/native behavior; prior matching-GUI proof remains in PR #20/Phase 5 docs.

Known limits: experimental generated names; redundant nullable-enum unions and
strict primary-enum parsing; generated attrs are not full JSON Schema validators;
Capabilities' generated temporary shadowing needs selective mypy treatment;
no arbitrary wrapper thread-safety claim; SSE fallback/reconnect is explicit;
small live jobs complete before cancellation, so nonterminal cancellation and
history expiration have Python unit plus existing Java evidence; only Linux
3.11/3.14 exercised. Source-only PARTIAL renderer is simulated in existing examples;
real PARTIAL coverage tests use references. Local rename remains unsupported.

No API/SDK promise of complete input provenance, all references or general
related propagation; no autosave/retry framework, project switching, upload,
service startup/download, auth/remote hosting, publication, or new persistence.
The next milestone is **PR #22 — cross-language release qualification**: exhaustive
route/error parity, concurrent lifecycle/deadline/nonterminal cancellation from
Python, final Java/Python distribution/native/GUI release gates and release docs.
External publication/tagging still requires a separately explicit instruction.
