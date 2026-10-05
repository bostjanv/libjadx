# PR #24 — Apache-2.0 alpha qualification

**Outcome A: QUALIFIED locally; NOT PUBLISHED.** Java `0.1.0-alpha.1` / Python
`0.1.0a1` is freshly qualified under Apache-2.0 and prepared for publication.
No tag, GitHub Release, PyPI upload or Maven publication has occurred.

This is author-run local qualification on Linux x86_64. No remote CI or
independent reviewer rerun is claimed.

## Source and evidence identity

- Qualified source HEAD: `ea2560abbbc237ffd48268899b68a5a6b0237e65`.
- Source SHA-256: `20b93c16b32e52b711ef648a9e1eb320f63de4ee1bac756288edbaf529851e18`.
- The enclosing report commit is an evidence-only descendant. Its own hash cannot
  be embedded in this report; Git identifies that commit. Final freshness
  validation checks its ancestry, unchanged source digest and every intervening path.
- [Validation](validation.json), [artifacts](artifacts.json),
  [routes](route-matrix.json), [errors](error-matrix.json),
  [capabilities](capabilities.json) were imported from the fresh run.
- Both interpreter matrices are retained; CPython 3.11 is the primary matrix and
  CPython 3.14 is preserved under `additional_interpreter_evidence`.
- PR #22 report bytes remain unchanged and historical.

[Review #5418946904](https://github.com/bostjanv/libjadx/pull/24#pullrequestreview-5418946904)
identified stale current-facing PR #22/pending PR #24 status wording. The fix
updates current status documents and direct links to the versioned report and
validation record; historical PR #22 files remain unchanged. The validation
record owns the exact source HEAD, avoiding a self-referential hash in ordinary
Markdown. The source commit above includes those documentation and validator
changes, and the entire release gate was rerun at that clean commit.

## Implemented

Current status text in README, AGENTS, DESIGN, IMPLEMENTATION, the documentation
index, compatibility, feasibility, Python SDK guidance, release notes and the
release runbook now identifies PR #24 as the fresh Apache-2.0 alpha qualification.
The runbook has a current PASS section and separates PR #22 historical results.
Current docs link to the versioned validation record for exact qualified source
identity and to current route/error/capability/artifact reports.

The documentation gate rejects obsolete PR #22 active-qualification claims and
pending PR #24 wording. Self-tests cover 84 negative qualification-language cases
across all 14 current-facing documents, case/whitespace normalization, valid
current PR #24 wording and accepted historical language. Existing license and
archive/Git freshness regressions remain mandatory. No evidence freshness
allowlist was broadened: ordinary Markdown still enters the source fingerprint.

The original PR #24 licensing, package metadata, byte-exact artifact license and
notice audits, independent-development guard and publication boundary remain
intact. Root and Python LICENSE bytes remain canonical Apache-2.0. Java ZIP/TAR
and Python wheel/sdist were rebuilt, audited and reproduced from this source.

No endpoint, public model, generated transport, runtime behavior, dependency
lock or capability status changed. Draft
[release notes](../../releases/0.1.0-alpha.1.md) and the
[publication checklist](../../alpha-publication-checklist.md) remain preparation
for a separate human-approved publication action.

## Toolchains and pinned engine

Java `0.1.0-alpha.1`, Python package
`0.1.0a1`, API `0.1.0-experimental`;
JDK 21, Gradle `8.14.3`,
`uv 0.8.22`;
CPython 3.11.13 and
3.14.4.

Jadx remains 1.5.6 at `28ff15e4ae69950aebea110a13e5ab895d234dfc`.
No new Jadx API was used. Native serialization evidence continues to exercise
pinned `jadx-gui/src/main/java/jadx/gui/settings/JadxProject.java` and
`jadx-gui/src/main/java/jadx/gui/settings/data/ProjectData.java`, through actual
matching-GUI processes. Root LICENSE is the unmodified
`https://www.apache.org/licenses/LICENSE-2.0.txt` text, committed for offline use.

## Commands and outcomes

Before committing this source, `git diff --check`, documentation validation and
its self-tests, license validation and the seven archive/Git evidence regressions
passed. The complete gate also ran OpenAPI and all four contract validators,
frozen/offline generated drift (162 matching files), Ruff format/lint and mypy
on the clean source commit:

```bash
JADX_GUI=<jadx-1.5.6>/bin/jadx-gui \
JADX_GUI_ARCHIVE=<jadx-1.5.6.zip> \
UV_BIN=<uv-0.8.22> \
  tests/release/validate-release-candidate.sh all \
  --output /tmp/libjadx-alpha1-review-fix-evidence
```

The original orchestration console detached after the completed Java check,
preliminary gates, repeat builds and both installed-wheel suites; its next
progress print raised `BrokenPipeError`. All completed exit-code records, XML
counts, cleanup observations, artifact hashes and the unchanged source identity
were reverified before resuming the remaining gates on the same clean HEAD.
Matrix validators, TAR smoke, all GUI tasks and the final result then ran to
completion. The resume driver source is retained in the validation record under
`core.orchestration_resume`; its console output was redirected to an external
file. No mandatory stage was omitted or counted as passing from historical PR #22
or the earlier PR #24 source. The continuation command was:

```bash
JADX_GUI=<jadx-1.5.6>/bin/jadx-gui \
JADX_GUI_ARCHIVE=<jadx-1.5.6.zip> \
UV_BIN=<uv-0.8.22> \
  python3 <resume-driver>/libjadx-pr24-resume-qualification.py \
  > /tmp/libjadx-alpha1-review-fix-evidence/resume-orchestration.log 2>&1
```

The result was `QUALIFIED locally; NOT PUBLISHED`. Exact normalized commands,
elapsed times and machine-produced PASS results are in [validation](validation.json).
The exact cached uv/GUI tools were reused. The gate used cached/offline Gradle
and frozen/offline uv with loopback-only services.

| Gate | Actual result |
| --- | --- |
| `./gradlew clean check --offline --rerun-tasks` | 487 tests / 90 suites; 471 passed; 16 opt-in GUI skips; 0 failures/errors |
| Installed wheel CPython 3.11 | 307 passed; 0 failures/errors/skips; 203 Java processes |
| Installed wheel CPython 3.14 | 307 passed; 0 failures/errors/skips; 203 Java processes |
| OpenAPI/route inventory | 22/22 operations on all five surfaces, both interpreters |
| Error inventory | 19/19 errors on all five surfaces, both interpreters |
| Public capabilities | 37 reviewed entries on all five ordinary-process surfaces, both interpreters |
| Extracted TAR smoke | 2 passed; 0 failures/errors/skips |
| Archive license/content audits | Java ZIP/TAR and Python wheel/sdist passed |
| Repeat builds | All four archives byte-identical |
| `releaseGuiQualification --offline --rerun-tasks --no-parallel -x test` | 12 tasks; 0 failures/errors/skips |
| Actual matching-GUI Save As | 27 fresh saved projects / 27 actual logged processes |

## Matching-GUI persistence check

Owned qualification.jar plus the existing native/edit/mapping/scoped/related/
replacement/replay/local-retargeting fixture families were recreated by clean
check. Every matching-GUI task ran; the ordinary check's opt-in skips were
covered here. Native API saves remain explicit, unsaved restart/discard,
external-change refusal and saved restart behavior were exercised. GUI Save As
outputs were reopened headlessly. Diagnostic gates preserve documented
unsupported/degraded cases rather than advertising new edits.

| GUI task | Tests | Skips | Failures / errors |
| --- | --- | --- | --- |
| `guiRoundTripTest` | 1 | 0 | 0 / 0 |
| `rawGuiRoundTripTest` | 1 | 0 | 0 / 0 |
| `nativeEditGuiRoundTripTest` | 1 | 0 | 0 / 0 |
| `mappingExportGuiRoundTripTest` | 1 | 0 | 0 / 0 |
| `mappingImportGuiRoundTripTest` | 1 | 0 | 0 / 0 |
| `scopedEditGuiRoundTripTest` | 1 | 0 | 0 / 0 |
| `relatedPropagationGuiRoundTripTest` | 1 | 0 | 0 / 0 |
| `propagatedEditReplayGuiDiagnosticTest` | 1 | 0 | 0 / 0 |
| `safeReplayGuiDiagnosticTest` | 1 | 0 | 0 / 0 |
| `replacementEditGuiRoundTripTest` | 1 | 0 | 0 / 0 |
| `propagatedEditGuiRoundTripTest` | 1 | 0 | 0 / 0 |
| `localRenameGuiDiagnosticTest` | 5 | 0 | 0 / 0 |

## Reproducible artifacts

All hashes below come from repeat-build equality, after the license changes.
Binary artifacts remain outside Git. Apache-2.0 license SHA-256:
`cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30`. OpenAPI, Gradle lock, uv lock and
generated-tree fingerprints are recorded in [artifacts](artifacts.json).

| Artifact | Bytes | Files | SHA-256 |
| --- | --- | --- | --- |
| `libjadx-0.1.0-alpha.1.tar` | 59798016 | 70 | `4d9f9324d814d68292df4c0b15653cbd989082af0cc02587a2b2297126132d75` |
| `libjadx-0.1.0-alpha.1.zip` | 51541985 | 70 | `88e7fc6750fcd49532dbb720148013dff407287bb96aef0a93e576d49a73aaeb` |
| `libjadx-0.1.0a1-py3-none-any.whl` | 174597 | 181 | `a057ad01b5909353dfc2e96c36ed202f360ca711256661dd4e20e2da14d64c89` |
| `libjadx-0.1.0a1.tar.gz` | 169519 | 203 | `ee6e2f28f670164e17dd7315a34ad8483a9949a938977f4026f5b182a9c51305` |

## State and next work

The API remains experimental. Primary Jadx reads are serialized; conflicting
admission returns PROJECT_BUSY. Cancellation is cooperative/PARTIAL. References,
native-save guarantees and related propagation retain their partial limits.
Local-variable rename and public Smali are unsupported; CFG/resources have no
public API. Provenance limitations, one fixed local project per process and the
trusted loopback security assumption are unchanged. Linux x86_64 is qualified;
Windows/macOS and the complete bundled-plugin input matrix remain unqualified.

Phase 7 remains the extended-capability roadmap. The only release decision
remaining is separate explicit human authorization to publish, choose the tag
target and confirm current PyPI project state. Nothing was externally published.
