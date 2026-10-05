# PR #24 — Apache-2.0 alpha qualification

**Outcome A: QUALIFIED locally; NOT PUBLISHED.** Java `0.1.0-alpha.1` / Python
`0.1.0a1` is freshly qualified under Apache-2.0 and prepared for publication.
No tag, GitHub Release, PyPI upload or Maven publication has occurred.

This is author-run local qualification on Linux x86_64. No remote CI or
independent reviewer rerun is claimed.

## Source and evidence identity

- Qualified source HEAD: `186a43d561367384f720465df0dd9ffd48829d96`.
- Source SHA-256: `c43f7b6a0e1bf6350c0994e348ba21bf83426d2bee9ed95842f182c65ad0466e`.
- The enclosing report commit is an evidence-only descendant. Its own hash cannot
  be embedded in this report; Git identifies that commit. Final freshness
  validation checks its ancestry, unchanged source digest and every intervening path.
- [Validation](validation.json), [artifacts](artifacts.json),
  [routes](route-matrix.json), [errors](error-matrix.json),
  [capabilities](capabilities.json) were imported from the fresh run.
- Both interpreter matrices are retained; CPython 3.11 is the primary matrix and
  CPython 3.14 is preserved under `additional_interpreter_evidence`.
- PR #22 report bytes remain unchanged and historical.

The first attempt was intentionally interrupted before completion to replace
Jadx's appendix-brace variant with the exact canonical Apache text. Only the
complete run on the corrected source above is qualification evidence.

## Implemented

Root LICENSE and python/LICENSE are byte-identical canonical Apache-2.0 text.
Python SPDX metadata agrees. Java ZIP/TAR carry a distribution-root LICENSE and
all five separate upstream notices; wheel/sdist preserve the LibJadx license,
openapi-python-client MIT notice and runtime attribution. No root NOTICE was
manufactured, no libghidra component was bundled and the independent-development
guard remains mandatory.

The harness now fingerprints all source/license/config/ordinary documentation,
excluding only `docs/release-evidence/**` and `docs/changelog.md`. Evidence-only
Git descendants are allowed; source changes (including later-reverted changes)
are rejected. Seven archive/Git regression tests cover the rule, including
uncommitted changes, unrelated evidence paths and unrelated ancestry. Current
matrix-validator defaults use this versioned evidence directory.

License/documentation checks are mandatory in the release gate. Java ZIP and
TAR audits compare license/notice bytes; Python artifact audits check
`License-Expression: Apache-2.0` and license/notice bytes. The harness emits
stable-order SHA256SUMS outside the repository. Draft
[release notes](../../releases/0.1.0-alpha.1.md) and the
[publication checklist](../../alpha-publication-checklist.md) prepare a separate
human-approved publication action.

No endpoint, public model, generated transport, runtime behavior, dependency
lock or capability status changed.

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

Before committing source, `git diff --check`, documentation validation and its
self-tests, license validation, OpenAPI and all four contract validators passed.
Frozen/offline generated drift showed 162 matching files; Ruff format/lint and
mypy passed. The complete gate repeated these checks on the clean source commit:

```bash
JADX_GUI=<jadx-1.5.6>/bin/jadx-gui \
JADX_GUI_ARCHIVE=<jadx-1.5.6.zip> \
UV_BIN=<uv-0.8.22> \
  tests/release/validate-release-candidate.sh all \
  --output /tmp/libjadx-alpha1-evidence
```

The result was `QUALIFIED locally; NOT PUBLISHED`. Exact normalized commands,
elapsed times and machine-produced PASS results are in [validation](validation.json).
Bootstrap downloaded the exact uv/GUI tools before qualification; the gate
used cached/offline Gradle and frozen/offline uv with loopback-only services.

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
| `libjadx-0.1.0a1-py3-none-any.whl` | 174583 | 181 | `4d62f0388ed65d99aaf661b140c2df505ea2b70d1b715fd82a8327da479a4840` |
| `libjadx-0.1.0a1.tar.gz` | 169498 | 203 | `e1955612f8b9698bd80d73e784b7de9c6b0e0af2346fdb84dd725d83a6db8437` |

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
