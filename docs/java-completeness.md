# Java decompilation completeness maintenance

## Contract and pinned-source finding

`POST /api/v1/decompile` retains useful Java and reports `PARTIAL` when pinned
Jadx records a failure in the returned source owner's class/method tree.
Strict requests reject that result with HTTP 409 `INCOMPLETE_ANALYSIS`.
This repairs the pre-existing invalid-register case recorded during the
[Smali validation](phase-7-smali.md#pre-existing-java-status-limitation), without
adding endpoints, options, persistence or a generated-Java text heuristic.

Jadx **1.5.6**, commit `28ff15e4ae69950aebea110a13e5ab895d234dfc`:

- [ProcessClass.process](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/ProcessClass.java)
  generates code, calls `ClassNode.unload`, then sets `GENERATED_AND_UNLOADED`.
- [ClassNode.unload](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/nodes/ClassNode.java)
  and [MethodNode.unload](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/nodes/MethodNode.java)
  unload attributes. `AttrList<JadxError>` does not override the default
  `IJadxAttribute.keepLoaded() == false`; `JADX_ERROR` disappears.
- [ErrorsCounter.addError/getErrorNodes](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/utils/ErrorsCounter.java)
  retains failed node identities independently of those attributes. Its global
  count includes other owners and repeated failures; it is not a class count.
- [JavaClass.loadLists](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/JavaClass.java)
  filters hidden methods. Checking only that facade also misses nested/inlined
  nodes, so the adapter walks raw methods, inner classes and inlined classes
  from the selected emitted owner, with a visited set.

The adapter checks surviving attributes **or membership in the retained failed
node set**, not the global count. It does not generate other owners or retain
new mutable state. Existing serialized engine leases and isolated override
instances are unchanged. A generic bounded diagnostic survives missing upstream
messages; surviving messages retain their existing sanitation and bounds.

`classErrorCount` remains **null** and Java `capabilities.classErrors` remains
**UNKNOWN**. No exhaustive error census, semantic correctness or coverage of
all input definitions is claimed. `capabilities.source: EXACT` still means
the exact returned Jadx string, independently of its completeness. A METHOD
request returns the full owner source, so even a healthy method reports PARTIAL
if that owner contains another verified failure.

FALLBACK skips `CheckCode` (see `Jadx.getFallbackPassesList`); the invalid-register
fixture produces a register dump without recording an error there. An initial
overbroad test expecting PARTIAL in FALLBACK exposed this distinction and was
corrected. This change reports **verified failures**, not new bytecode validation.
It does not invent errors for modes that did not record them.

## Regression and proof procedure

- `DecompileEndpointsTest.realJavaFailuresRemainPartialAfterCodegenWithoutPoisoningHealthyOwners`
  directly assembles the existing owned `Disassembly.smali`, including a nested
  variant. Through real HTTP it checks class/method/outer-owner PARTIAL, useful
  source/snapshot preservation, repeat reads and strict rejection in AUTO,
  RESTRUCTURE and SIMPLE. An unrelated healthy owner still passes strict mode
  after failures in the primary engine; a literal `Method not decompiled` does
  not cause a false positive. Reads leave revision/dirty state unchanged.
- The existing `JadxSmaliProbeTest` now independently demonstrates both the
  missing post-codegen attribute and the retained failed method identity.
- `test_java_completeness_and_strict_parity` launches the installed standalone
  service and compares complete result bodies across raw HTTP, generated
  sync/async and handwritten sync/async clients. It checks 409/error-code parity
  for verified failures and strict successful controls, including healthy
  FALLBACK. Counts remain null on all surfaces. An initial harness error passed
  a string rather than the generated mode enum; that run was discarded.

Before changing production code, the same Java HTTP regression and Python
AUTO invalid-register scenario failed with `expected PARTIAL, got COMPLETE`.
Ignored review artifacts under `.amp/in/artifacts/` retain
`java-completeness-before.log`, `java-completeness-before.xml` and
`java-clients-before.log`. The earlier baseline JSON is also preserved.

## Validation — 2026-10-17

Linux x86_64, OpenJDK 21.0.12.1, Gradle 8.14.3, pinned Jadx 1.5.6,
CPython 3.14.4 and frozen Python dependencies:

| Command / scenario | Outcome |
| --- | --- |
| Unchanged-code Java HTTP regression and Python AUTO failure scenario | Both reproduced false COMPLETE before the production edit. |
| `./gradlew test --tests dev.libjadx.app.DecompileEndpointsTest --tests dev.libjadx.probes.JadxSmaliProbeTest` | 15 passed. |
| `./gradlew test check installDist` | Passed in 7m 56s: 493 tests, zero failures/errors, 16 opt-in GUI skips (477 executed). Includes reference/search, concurrency/cancellation, malformed-input, stale-revision, conflict, restart and native-save tests. |
| `uv run --frozen pytest tests/integration/test_live.py -q` from `python` | 18 passed, including seven failure/healthy/mode client-parity scenarios. |
| `uv run --frozen pytest tests/unit -q` from `python` | 171 passed. |
| `uv build --project python`, install the wheel into a scratch venv, then `python -m pytest python/tests/unit python/tests/integration/test_live.py -q` using that interpreter outside the source tree | 189 passed against `build/install/libjadx/bin/libjadx`; import path verified in venv site-packages, not editable SDK source. |
| Independent installed-service HTTP capture | Invalid-register Java: 200 PARTIAL with useful `answer()` and snapshot; strict text-only: 409 INCOMPLETE_ANALYSIS; `classErrorCount: null`, `classErrors: UNKNOWN`. |
| `uv run --frozen python scripts/check_generated.py` from `python` | All 163 generated files match. |
| Frozen Ruff check/format check and mypy on handwritten Python | Passed; 27 files formatted, 12 source files type-checked. |
| `python/.venv/bin/python tests/validate-openapi.py` | 22 operations, 38 JSON examples and three SSE frames valid. |
| `tests/validate-edit-contract.py`, `validate-search-contract.py`, `validate-mapping-export-contract.py`, `validate-mapping-import-contract.py` using the same Python | All passed against full-regression captured HTTP responses, including 66 search responses. |
| `tests/validate-documentation.py`, its `--self-test`, `tests/validate-license.py`, and `git diff --check` | Passed. |

Review artifacts include `java-completeness-regression.log` and counts JSON,
`java-clients-after.log`, `java-completeness-installed-wheel.log`, and the full
raw response/strict error plus server JAR checksum in
`java-completeness-http-after.json`, all under `.amp/in/artifacts/`.
GUI gates were not rerun: persistence is untouched; ordinary native/save/restart
regressions passed. This is slice verification, not a new release qualification
or a claim about untested Python/platform combinations.

## Delivery boundary

No persistence code, native format, save behavior, concurrency or operation
inventory changes. Existing release evidence is historical qualification of
its recorded source, not of this maintenance patch. Full clean-source release
qualification remains required before publication. The next Phase 7 work starts
with the separate [DEX instruction-reference feasibility gate](phase-7-instruction-references.md),
not another public API folded into this maintenance patch. Public implementation
and its reviewed contract remain subsequent work.
