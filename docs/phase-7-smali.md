# Phase 7 — Class-level loaded-DEX Smali

## Feasibility gate and decision

Pinned Jadx **1.5.6**, source
`28ff15e4ae69950aebea110a13e5ab895d234dfc`, was inspected before implementation.
`./gradlew test --tests dev.libjadx.probes.JadxSmaliProbeTest` passed both probes
on 2026-10-17, using owned JAR and directly assembled DEX inputs. The DEX includes
an invalid register that causes real Java decompilation failure while its
disassembly remains available. Earlier probe attempts are not success evidence:
an argument-count mismatch did not cause a Jadx error; the final invalid-register
fixture does.

- [JavaClass.getSmali](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/JavaClass.java)
  delegates to `ClassNode.getDisassembledCode`.
- [ClassNode](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/nodes/ClassNode.java)
  caches text, adds alias-bearing headers, concatenates inner/inlined classes and
  can embed exception stack traces. That public facade is not a single-class,
  format-specific contract.
- [DexClassData](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/sections/DexClassData.java)
  delegates to [SmaliUtils](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/utils/SmaliUtils.java),
  which uses pinned Baksmali on loaded DEX bytes. Its failure text includes a
  stack trace and must never escape as successful source.
- [JavaClassData](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-plugins/jadx-java-input/src/main/java/jadx/plugins/input/java/data/JavaClassData.java)
  uses [DisasmUtils](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-plugins/jadx-java-input/src/main/java/jadx/plugins/input/java/utils/DisasmUtils.java):
  ordinary class/JAR disassembly is **Raung/JVM**, not Smali. A `.class` directive
  alone does not establish Smali support.

Decision: isolate the version-sensitive `DexClassData` admission and per-class
input-data disassembly in `jadxadapter`. Do not relabel Raung as Smali, silently
convert JVM inputs, concatenate nested classes, or force Java decompilation.

## Public contract

`POST /api/v1/decompile` accepts `representation: SMALI` for **CLASS** refs only.
The existing operation ID `decompileJava` is retained for transport compatibility.
Java remains the default. Unknown representations and Smali METHOD requests
return `422 UNSUPPORTED_CAPABILITY`.

For loaded DEX class data, `source` is the exact single-class Baksmali text,
capped at 4 MiB UTF-8. It describes the loaded DEX, not necessarily an original
input artifact: conversion and exact input provenance remain unverified.
Original descriptors in the text are not rewritten by unsaved renames/comments.
`sourceOwnerRef` is the requested original class, including for an inner class;
no enclosing Java-owner substitution occurs.

Non-DEX/generated class data or failed disassembly yields `status: UNAVAILABLE`
with null source/owner/snapshot and a bounded diagnostic, never raw upstream
exception text. Lookup outcomes remain distinct from representation availability.
Successful Smali reports `source: EXACT` for the returned text, not verified DEX
semantics or exhaustive original-input coverage. `code.smali` is `PARTIAL`.

Annotations/variables are empty; method ranges, original debug lines, offsets
and Java error counts are unavailable. Strict mode requires available source
and rejects requested unavailable annotations/debug lines. To request strict
text-only Smali, set `includeAnnotations: false`. The decompilation-mode option
retains existing isolated-engine routing and settings provenance; it does not
transform the underlying DEX disassembly.

Snapshots are representation-, text-, session-, settings-, revision- and
publication-scoped. Java and Smali preconditions are not interchangeable.
Reads retain the existing serialized primary admission and `PROJECT_BUSY`
behavior. Temporary overrides use existing immutable native-state snapshots.
Smali is never ingested into Java source search. No save, persistence format,
concurrency, cancellation or original-input identity guarantee changes.

## Qualification boundary

This Phase 7 change is not covered by the historical PR #24 release qualification.
Versioned release evidence remains unchanged. Full candidate requalification at
a clean source commit is required before publishing artifacts containing it.

## Slice validation — 2026-10-17

Platform: Linux x86_64, OpenJDK 21.0.12.1, pinned Gradle 8.14.3/Jadx 1.5.6,
CPython 3.14.4 and frozen Python dependencies. These results were collected
before committing this slice, not as qualification of a clean release source commit.

| Command / check | Observed result |
| --- | --- |
| `./gradlew test --tests dev.libjadx.probes.JadxSmaliProbeTest` | Both real-Jadx format/scope/failure probes passed. |
| `./gradlew clean test check installDist` | Passed in 7m 37s: 492 tests, zero failures/errors; 476 passed and 16 opt-in GUI tests skipped here, then exercised separately below. |
| `uv run --frozen pytest tests/integration/test_live.py -q` from `python` | 11 passed, including full response equality across raw HTTP, generated sync/async and handwritten sync/async clients. |
| `uv run --frozen python scripts/check_generated.py` from `python` | Generated output matches all 163 files. |
| `python/.venv/bin/ruff check python/src/libjadx python/tests` and `ruff format --check` on the same paths | Passed; 27 handwritten files formatted. |
| `uv run --frozen mypy src/libjadx --exclude _generated` from `python` | No issues in 12 handwritten source files. |
| `python/.venv/bin/python tests/validate-openapi.py` | OpenAPI 3.1 valid: 22 operations, 38 referenced JSON examples and three SSE frames. |
| `python/.venv/bin/python tests/validate-documentation.py` and `--self-test` | Passed; current links/anchors and stale-language/license/qualification rejection checks valid. |
| `python/.venv/bin/python tests/validate-edit-contract.py` | Passed: 16 examples, 20 live edit responses, four injected service results, 18 scoped responses and 36 related-propagation responses. |
| `python/.venv/bin/python tests/validate-mapping-export-contract.py` | Passed: three examples and 31 live responses. |
| `python/.venv/bin/python tests/validate-mapping-import-contract.py` | Passed: four examples and 41 live responses. |
| `python/.venv/bin/python tests/validate-search-contract.py` | Passed: four examples and 33 live responses. |
| `python/.venv/bin/python tests/validate-license.py` | Passed. |
| `./gradlew distZip distTar` and `uv build` from `python` | Standalone archives and wheel/sdist built locally; no publication. |
| Installed wheel against the extracted ZIP, command below | 315 passed in 744.65s, zero skips; aggregate gates cover all 22 operations, 19 errors and five client surfaces, including the updated complete capability snapshot. |
| `JADX_GUI="$PWD/.amp/in/jadx-dist/bin/jadx-gui" ./gradlew releaseGuiQualification -x test` | All 12 gates passed: 16 GUI verification tests, zero skips/failures/errors, 27 actual matching-GUI Save As operations; 9m 53s. |
| `git diff --check` | Passed. |

The installed-wheel command ran from `.amp/in`, outside the SDK source tree.
`ROOT` below denotes `/home/alice/dev/libjadx`; the scratch virtual environment
contained the built wheel and dependencies exported with
`uv export --frozen --all-groups --no-emit-project --no-hashes --project python`:

```bash
LIBJADX_EXPECT_INSTALLED=1 \
LIBJADX_DISTRIBUTION="$ROOT/.amp/in/extracted/libjadx-0.1.0-alpha.1/bin/libjadx" \
LIBJADX_RELEASE_EVIDENCE="$ROOT/.amp/in/artifacts/matrix-3.14" \
"$ROOT/.amp/in/wheel-env/bin/python" -m pytest \
  "$ROOT/python/tests/unit" "$ROOT/python/tests/integration" \
  "$ROOT/python/tests/release" -q
```

The first GUI attempt could not create a window because the runner had only
headless Java libraries. Installing the matching JRE's headful libraries fixed
that prerequisite; the successful run above actually opened the GUI. This is
test-environment setup, not a new production GUI dependency.

Owned fixtures include `tests/fixtures/smali/Disassembly.smali`, the separately
assembled inner-class DEX in `DecompileEndpointsTest`, generated JAR/Raung
classes, the release `LocalDex` fixture and existing native-project fixtures.
HTTP regressions cover strict text-only versus unavailable metadata, inner-class
ownership, representation/mode/revision snapshots, fail-fast busy admission,
unchanged DEX text after rename, no autosave and no Java search ingestion.
A post-load corruption of the real in-memory DEX class type index verifies that
upstream swallowed disassembly exceptions produce UNAVAILABLE without source or
exception traces. Release tests verify unchanged Smali text but a new snapshot
after explicit native save and restart. Existing GUI gates verify that native
serialization and supported edits still survive actual GUI save/headless reopen.

Local logs, baseline observations and installed-client matrices are retained in
`.amp/in/artifacts/`; these ignored review artifacts are not versioned release
evidence. Temporary source downloads and scratch environments are disposable.

## Pre-existing Java status limitation

The owned invalid-register fixture makes the Java endpoint emit a
`Method not decompiled` stub while reporting `status: COMPLETE`. This was
reproduced against unchanged HEAD, before this slice, by building a separate
`git archive HEAD` checkout and requesting Java for the same `.smali` input.
The baseline JSON/log are retained as `baseline-java-status.json` and
`baseline-java-status.log` in the local review artifacts. This observation is
not a Smali regression; its cause was not established and Java status behavior
was deliberately left unchanged. A separate maintenance change should investigate
and correct that contract limitation.

The subsequent [Java completeness maintenance](java-completeness.md) establishes
the unload/retained-error-node cause and repairs this result. The baseline
observations above remain historical evidence from the Smali slice.

## Delivery and remaining work

This slice adds no persistence format or save policy and enables no parallel
primary reads. Method-level Smali, JVM/Raung representation, original offsets,
CFG/resources APIs and broader APK/multidex qualification remain outside scope.
No architectural design change is required. Committing and pushing this slice
does not tag or publish a release; publication remains a separate explicit action.
Full clean-source release qualification, including Python 3.11 and reproducible
artifact checks, remains required before distributing a release with this feature.
