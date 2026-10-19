# Temporary analysis — accepted-file baseline admission

**Status:** implemented and validated locally on 2026-10-19. Maintenance following the
[normalized CFG isolation gate](phase-7-normalized-cfg-isolation.md). That gate
found that temporary loading accepts changed filesystem inputs under an older
logical snapshot. A public CFG remains blocked; this item does not implement it.

## Scope and acceptance

Before capturing a temporary analysis's native edits/settings and releasing the
coordinator snapshot gate, call the repository's existing
`checkAnalysisBaselines()`. Reject changed/deleted inputs and attached mappings
with `EXTERNAL_MODIFICATION_CONFLICT`; reject outside-root symlinks with
`INPUT_SECURITY_REJECTION`. Do not create a temporary engine on refusal. Preserve
the primary, unsaved edits, revisions and native files, and release admission so
a subsequent valid operation succeeds.

Validate every configured input, not only the requested class's owner, and use
the accepted baseline for an unsaved mapping attachment. Explicit native reload
is still the operation that accepts new input/mapping bytes. Native project JSON
is not reread by temporary loading, so unrelated external project-file edits
must not be treated as changes to the captured in-memory analysis.

Update the existing decompile operation's error documentation and generated
transport together. Retain the current serialized primary-read and brief
temporary snapshot-gate policies. No new endpoint, capability, persistent format,
file copies, automatic retry/save or dependency change.

## Deliberate limitation

This is **admission-time, best-effort validation**, not immutable input ownership
or a TOCTOU/ABA solution. Files may still change after the check and before/during
Jadx loading. Keep the gate's executable between-capture-and-load counterexamples;
do not claim to fix them with before/after path hashes. Hashing is synchronous
under the admission gate (not the lifecycle/coordinator monitor), so large
inputs can extend PROJECT_BUSY admission time. No runtime/heap bound is added.

## Remaining work

1. Review the completed test-only
   [owned DEX input and aggregate-admission gate](phase-7-owned-dex-admission.md).
   Its BLOCKED/DEGRADED result proves structural admission and consumed-byte
   binding on a small subset, but inputless loading skips native renames.
2. Resolve inputless initialization, accepted-baseline access and same-owned-data
   census integration in a separately reviewed design. Production ownership and
   independent public graph publication/content provenance remain unimplemented.
3. Only after those gates, prepare an OpenAPI-first normalized CFG slice.
4. Other Phase 7 work remains: deeper references/representations, annotations,
   resources/classpaths and portable native export. Local rename remains
   unsupported; publication needs fresh full qualification and explicit approval.

## Validation plan

Extend real-Jadx decompile HTTP regressions with changed first/second inputs,
mapping changes/deletions, outside-root symlinks, retained unsaved edits and
restore/reload controls. Cover raw input and an unsaved mapping attachment through
the runtime. Exercise typed errors on generated/handwritten sync/async Python
surfaces. Run the CFG isolation probes unchanged to retain the residual-race
evidence, full Java tests/check/installDist, OpenAPI/generated SDK checks, Python
tests, documentation checks and whitespace validation. Native persistence is
unchanged; matching-GUI and full release qualification are not implied.

## Implementation and pinned evidence

`ProjectRuntime.withTemporarySourceRead` now calls the existing repository guard
inside the temporary capture lease, before arguments, engine construction and
`finishSnapshotCapture`. The existing try-with-resources releases admission on
refusal. `StatusServlet.handleDecompile` maps native conflicts to HTTP 409 and
path-policy failures to HTTP 403. OpenAPI documents the distinction and the
pinned generator supplies the 403 error decoder; no response model changes.

Source inspected at Jadx **1.5.6**, revision
`28ff15e4ae69950aebea110a13e5ab895d234dfc`, fetched directly from commit-pinned
raw GitHub URLs, with local SHA-256 audit in the artifact directory:

- [JadxDecompiler.java](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/JadxDecompiler.java):
  `load()` loads filesystem inputs and runs preparation; argument construction
  alone does not capture their contents.
- [LoadMappingsPass.java](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-plugins/jadx-rename-mappings/src/main/java/jadx/plugins/mappings/load/LoadMappingsPass.java)
  and [RenameMappingsPlugin.java](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-plugins/jadx-rename-mappings/src/main/java/jadx/plugins/mappings/RenameMappingsPlugin.java):
  mapping preparation reads the configured path, not owned snapshot bytes.
- [RootNode.java](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/nodes/RootNode.java):
  `mergePasses` deliberately skips custom/plugin passes in SIMPLE/FALLBACK.
  Mapping-alias semantic controls therefore use RESTRUCTURE; baseline admission
  conservatively validates attached mappings in every temporary mode. This item
  does not change upstream mode/mapping semantics.

Owned fixtures are the native two-JAR/Tiny project, plus existing CFG-isolation
fixtures. Seven HTTP cases cover changed first/second inputs, changed mappings,
deleted input/mappings and input/mapping outside-root symlinks. They also verify
unchanged primary source/revisions/search identity/pending edits/native bytes,
recovery after restoration, and the non-conflicting native-JSON-only control.
The raw-input/runtime case checks refusal before engine creation, an unsaved
mapping attachment's baseline and acceptance after explicit save/reload.

The existing installed-release error scenario now also checks changed secondary
input/mapping bytes, outside-root symlinks and recovery on all five surfaces.
It uses the existing wire-schema/fidelity/typed-exception oracle and emits local
route/error observations. Historical versioned release matrices are untouched.
The nine unchanged CFG-isolation probes still pass, including their deliberate
between-capture-and-load counterexamples. Public CFG remains blocked/UNKNOWN.

## Executed validation and delivery state

Linux x86_64, OpenJDK 21.0.12.1, Gradle 8.14.3, locked Jadx 1.5.6; Python
CPython 3.11.17 with frozen `uv.lock` dependencies and uv 0.8.22. All artifacts
are under excluded `.amp/in/artifacts/temporary-baselines/`.

```bash
./gradlew test --tests \
  'dev.libjadx.app.DecompileEndpointsTest.temporaryModeRejectsChangedAnalysisFilesAndRetainsPendingEdits'
# Before production fix: 7/7 failed (before.log, before-xml/).

./gradlew test check installDist
# After: 529 tests, 513 passed, zero failures/errors, 16 opt-in GUI skips.
# Includes 20 decompile, 3 temporary-analysis and 9 unchanged isolation tests.
# java-full.log, java-full-xml/.

./gradlew distZip
# Success; extracted locally for installed-wheel checks.

python/.venv/bin/python tests/validate-openapi.py
# 23 unique operations, 42 JSON examples, 3 SSE frames; valid.
python/.venv/bin/python tests/validate-documentation.py
python/.venv/bin/python tests/validate-documentation.py --self-test
git diff --check
# Passed.

# From python/ (PATH includes its .venv/bin for generator post-hooks):
python scripts/check_generated.py
ruff format --check .
ruff check .
mypy src/libjadx
python -m pytest -q tests/unit tests/integration
# 183 generated files match; format/lint/strict typing pass; 204/204 tests pass.
uv build
# Wheel and sdist built successfully.

# From repository root, using a fresh environment with frozen dependencies and
# the installed wheel, plus the extracted Java archive (not build/install):
LIBJADX_DISTRIBUTION="$PWD/.amp/in/baseline-distribution/libjadx-0.1.0-alpha.1/bin/libjadx" \
LIBJADX_EXPECT_INSTALLED=1 \
LIBJADX_RELEASE_EVIDENCE="$PWD/.amp/in/artifacts/temporary-baselines/installed-observations" \
.amp/in/installed-baseline-sdk/bin/python -m pytest -q \
  python/tests/release/test_errors.py -k external_file_conflict
# 5/5 passed, zero skips; installed-errors.log/xml and installed-observations/.
```

Initial test-only failures were corrected: mappings require a saved native
project; SIMPLE intentionally omits plugin passes; handwritten result wrappers
expose generated data through `raw`, and generated modes are enums. An early
broad Python run preceded Java's instruction-fixture producer and failed four
fixture prerequisites; the final 204-test run follows fixture generation and
passes unchanged. The first generator invocation lacked Ruff on PATH; regeneration
with the pinned environment restored canonical output and passed drift checks.
No failed attempt counts as successful validation. Temporary parity checks were
replaced by extending the existing installed-release scenario rather than
keeping duplicate Python coverage.

Native persistence code is unchanged. Explicit-save/reload/no-autosave behavior
was exercised, but matching-GUI gates, Python 3.14 and full release qualification
were not rerun. The 16 GUI skips are reported, not counted as passes. The PR #24
evidence does not qualify this source. Changes remain local and uncommitted;
no push, merge, tag, deployment or publication is implied.
