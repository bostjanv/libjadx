# Phase 7 — Loaded-DEX instruction references

This slice follows the [Java completeness maintenance](java-completeness.md).
The initial feasibility gate below preceded the public implementation described
in [Public instruction view](#public-instruction-view). Pair-level references
and null original-input offsets retain their semantics. No persistence format
or endpoint is added. This is slice validation, not a renewal of PR #24's
historical release qualification.

## Pinned source

Jadx **1.5.6**, source `28ff15e4ae69950aebea110a13e5ab895d234dfc`:

- [MethodNode.getCodeReader](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/nodes/MethodNode.java)
  retains the input reader across transformed instruction unloading. Raw input
  instructions can be visited without using the transformed graph or Java.
- [DexCodeReader.visitInstructions](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/sections/DexCodeReader.java)
  starts method-relative offset at zero, advances in **two-byte code units**,
  and reuses one mutable `DexInsnData` object. Copy each observation immediately;
  never return the visitor object or a lazy method reference.
- [DexInsnData](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/insns/DexInsnData.java)
  exposes encoded method/field/type indexes, opcode, method-relative offset,
  loaded-buffer byte offset and instruction bytes. `IMethodRef.load()` is needed
  before reading its original descriptor.
- [DexFileLoader.collectDexFromZip](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/DexFileLoader.java)
  passes just an entry name into the loaded reader. A label such as `classes.dex`
  is not a unique archive/input identity. DEX-backed data can also come from a
  conversion plugin; the reader type alone does not establish original format.
- [UsageInfoVisitor](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/visitors/usage/UsageInfoVisitor.java)
  already scans raw readers, but aggregates relationships and does not retain
  each occurrence/offset. Its method-pair graph is not an instruction census.
- [DexInsnInfo](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/insns/DexInsnInfo.java)
  distinguishes ordinary method indexes from custom call sites. Polymorphic
  invokes also carry prototype information; a generic METHOD_REF branch is not
  sufficient evidence for full call semantics.

## Executable probe and independent expectations

`JadxInstructionReferenceProbeTest` directly assembles the owned
`tests/fixtures/references/InstructionRefs.smali` and also embeds the identical
DEX as `classes.dex` in a JAR. The expected entry layout is derived from the
fixture's instruction widths, not from the reader's output:

| Method offset (16-bit code units) | Encoded reference |
| --- | --- |
| 2 / 4 | static field read / write of `total:I` |
| 6 / 8 | instance field read / write of `value:I` |
| 10 / 13 | two distinct calls to `target()V` |
| 16 | unresolved `Lmissing/External;->lost()V` |
| 19 | unresolved interface `Lmissing/Api;->run()V` |
| 22 | explicit recursive call to `entry` |
| 25 | type reference `Lmissing/Type;` |
| 27 | overload `target(Ljava/lang/Class;)V` |

The full method has 13 instructions and 31 code units. The probe checks all
instruction starts, original target descriptors, resolution separately from
encoded targets, and instruction bytes at `code_item byte offset + 16 + 2 *
method offset`. It compares cold, post-Java-generation and aliased observations,
and confirms the cold scan does not trigger Java generation. An abstract method
has no code reader. The existing invalid-register fixture independently checks
reader retention after real Java failure; an ordinary JVM JAR is a non-DEX
control, not evidence for DEX units.

Reproduce:

```sh
./gradlew test --tests dev.libjadx.probes.JadxInstructionReferenceProbeTest
```

Both probes **passed on 2026-10-17** (Linux x86_64, JDK 21.0.12.1, Gradle
8.14.3/Jadx 1.5.6): two tests, zero skips/failures/errors. The 11 reference
occurrences and complete instruction/byte comparisons matched for direct DEX
and identical archive-contained DEX, before and after generation and aliases.
The archive input's loaded class label was just `classes.dex`, independently
demonstrating the provenance limit. Logs and copied JUnit observations are
retained in `.amp/in/artifacts/dex-instruction-reference-probe.log` and `.xml`.
These probes establish feasibility for the exercised subset, not public
availability or exhaustive DEX coverage.

## Boundary established by the initial gate

- Admit loaded DEX class **and** method-reader types. Keep unsupported/non-code
  input separate from a successful empty reference list or a failed scan.
- Name the coordinate `offsetCodeUnits` with an explicit `DEX_CODE_UNIT_16`
  unit and a base of the original loaded method's `insns[]` start. It is not an
  instruction ordinal, Java UTF-16 offset, `code_item` byte offset, ZIP byte
  offset or verified original-input address. Do not overload a bare offset.
- Retain one occurrence per instruction, including repeated, recursive and
  unresolved symbolic targets. Opcode-backed field direction is distinct from
  the existing aggregate FIELD_USE relation. Resolution to a loaded declaration
  does not prove a runtime dispatch target or complete call graph.
- Describe provenance as **loaded DEX input-reader evidence**, with original
  source/target descriptors. Exact configured-input/archive provenance and
  conversion lineage remain unavailable unless independently established.
- Keep source locations and source snapshots separate. Never backfill raw DEX
  offsets by parsing Java/Smali text or pairing aggregate graph edges by order.
- Before public implementation, review the OpenAPI extension to the existing
  references operation, including occurrence pagination/bounds, per-result
  availability, stale revisions and strict/partial semantics. Regenerate the
  transport and verify all five client surfaces together. Do not silently
  redefine v0.1 pair-level references.
- Further probes are required for invoke-range/super/direct/virtual forms,
  wide/array descriptors, switches/payloads/try-catch, malformed/truncated data,
  multidex duplicates and unsupported invoke-custom/polymorphic/handle cases.
  The expanded evidence below exercises these boundaries for this first slice.
  An allowlisted initial implementation must disclose excluded forms and scan
  failures, not drop them while claiming complete coverage. No CFG, resource,
  classpath, conversion or persistence feature is implied by this gate.

## Public instruction view

`POST /references/query` now accepts `view: INSTRUCTIONS` for one original
METHOD ref with `direction: OUTGOING`. Omitted `view` or `PAIRS` retains the
usage-graph behavior. No incoming crawl or new endpoint is introduced.
`references.dex_instructions = PARTIAL` is the new public capability.

- Each ordinary static/direct/super/virtual/interface invoke (including `/range`)
  and each encoded instance/static field read/write is one occurrence. Repeated
  and recursive calls remain distinct. `CALL` / `UNRESOLVED_CALL` distinguish
  loaded declaration resolution for calls; `FIELD_READ` / `FIELD_WRITE` use
  opcode direction independently of their `RESOLVED` / `UNRESOLVED` resolution.
- `instruction.offsetCodeUnits`, `unit: DEX_CODE_UNIT_16`, and
  `base: LOADED_METHOD_INSNS_START` describe an exact loaded-reader coordinate.
  Existing `originalOffset` stays null and its capability stays UNAVAILABLE.
  `capabilities.loadedDexOffset` describes this separate coordinate, not an
  original-input address. Encoded source/target descriptors never use aliases.
- `instructionScan.availability` is AVAILABLE for a finished traversal,
  including successful empty output; UNAVAILABLE for non-DEX/no-code/domain
  misses; FAILED for an unsuccessful traversal, potentially retaining earlier
  observations. An empty FAILED result is not a successful empty scan.
- Coverage remains PARTIAL in every resolved case. `strict: true` returns
  `409 INCOMPLETE_ANALYSIS`, including unavailable, excluded and failed scans.
  `pageComplete` only exhausts the filtered observation set. The service does
  not claim exhaustive graph coverage, dynamic-dispatch targets, unique input
  identity, conversion lineage, original bytes or Java correspondence.
- Custom/polymorphic invokes, method handles and method-type constants are
  excluded and each encountered opcode is listed in `excludedOpcodes`.
  Type/string references are outside this method/field view. Source sites are
  deliberately not joined to occurrences; `includeSourceSites: true` is invalid.
- Occurrences sort numerically by code-unit offset. Existing authenticated
  cursors bind the view, relations, page size, session/revision/publication,
  settings, observed content and scan status. Java generation alone does not
  invalidate unchanged raw observations; edits, save/reload and restart do.
- Limits apply before relation filtering: 10000 occurrences, 4 MiB copied
  identity strings, 1000000 method code units and pages of 1–100 occurrences.
  Crossing a budget returns `429 RESOURCE_LIMIT`, never truncated success.
  Payload headers are checked with wide arithmetic before allocation-free
  skipping; unknown opcodes and out-of-method/truncated reads produce FAILED.
  These bounds do not limit Jadx's startup allocation or wall time, and the
  scanner is not a full DEX verifier.

The adapter uses raw `ClassNode.getMethods()` and original `MethodInfo`, not
`JavaClass.getMethods()` (which invokes Java generation in pinned 1.5.6).
`DexCodeReader.copy()` owns traversal under the existing serialized primary
read lease; no retained Jadx visitor objects or extra engine/cache are added.
The pinned `DexInsnFormat` and `SectionReader` sources establish payload widths
and bounds behavior; `RootNode.resolveMethod/resolveField` establish loaded
declaration resolution only. Those APIs remain isolated in `jadxadapter`.

Reviewed request/response examples:
[request](../openapi/examples/references-instructions-query.json),
[response](../openapi/examples/references-instructions.json).
The Python transport is regenerated from OpenAPI using the pinned generator;
`Project.references`, `AsyncProject.references` and bound symbol convenience
methods accept `view="INSTRUCTIONS"`. Existing page iterators retain occurrence
coordinates and invalidation errors.

## Expanded evidence and validation

`InstructionVariants.smali` independently exercises all ten ordinary invoke
forms at offsets 0, 3, …, 27; `J[[I` arguments and `[J` return types; wide/array
fields at 0/2/4/6; packed/sparse/array payload traversal with invokes at 9 and
32; try/catch; and all six excluded opcodes. Its `broken()` contains an invoke
at offset 0 before an invalid-register return. Real Java generation fails and
the same symbolic occurrence is still recovered afterward.

Expanded `JadxInstructionReferenceProbeTest` covers truncated buffers and
reserved opcodes. `InstructionReferenceEndpointsTest` checks direct and
archive-contained DEX parity, unavailable versus empty versus failed scans,
occurrence pagination, recursion/repetition, unsaved alias independence,
strict/validation/busy/stale behavior and pre-filter collection limits. Malformed
on-disk fixtures recompute SHA-1/Adler32 integrity so scan failure, rather than
checksum rejection, is actually exercised. Duplicate definitions in two DEX
entries of one archive, and in two separate DEX inputs, expose only the loaded
definition selected by Jadx. The test distinguishes their encoded targets and
offsets, rejects fabricated entry-name attribution, and retains PARTIAL coverage;
it does not claim access to discarded definitions.

Python integration checks raw HTTP, generated sync/async and handwritten
sync/async against the real standalone distribution, including Java failure,
page iteration, native explicit save and process restart. The release route
matrix also includes the instruction view on all five installed-artifact
surfaces. Native persistence code is unchanged; no GUI compatibility expansion
is claimed.

Validation on 2026-10-17 in the `dex-instruction-references` worktree, using the
unchanged Jadx/JDK/Gradle pins above and Python 3.14.4:

| Command (repository root unless noted) | Actual result |
| --- | --- |
| `./gradlew test check installDist --offline` | Passed in 8m 50s: 501 tests, 485 passed, 16 skipped, zero failures/errors. This full run preceded the final test-only multidex addition. |
| `./gradlew test --tests dev.libjadx.app.InstructionReferenceEndpointsTest --tests dev.libjadx.probes.JadxInstructionReferenceProbeTest --offline` | Final targeted run passed: 9 tests, zero failures/errors/skips, including the multidex addition. |
| From `python/`: `uv run pytest -q tests/unit tests/integration` | 195 passed in 104.09s against the real installed Java application. |
| `LIBJADX_DISTRIBUTION="$PWD/.amp/in/extracted/libjadx-0.1.0-alpha.1/bin/libjadx" LIBJADX_EXPECT_INSTALLED=1 LIBJADX_RELEASE_EVIDENCE="$PWD/.amp/in/artifacts/installed-instructions" .amp/in/installed-314/bin/python -m pytest -q python/tests/release/test_routes.py -k loaded_dex_instruction` | Clean Python 3.14 wheel environment against the extracted standalone ZIP: 5 passed, 11 unrelated tests deselected, all five client surfaces and strict errors checked. |
| From `python/`: `uv run --frozen python scripts/check_generated.py` | Generated output matches all 168 files. |
| From `python/`: `uv run --frozen ruff format --check .`, `uv run --frozen ruff check .`, `uv run --frozen mypy src/libjadx` | Passed: 32 files formatted; lint clean; 12 typed source files checked. |
| `python/.venv/bin/python tests/validate-openapi.py` | Valid OpenAPI 3.1: 22 operations, 40 referenced JSON examples, 3 SSE frames. |
| `python/.venv/bin/python tests/validate-edit-contract.py` | Passed: 16 examples, 20 live responses, 4 injected service results, 18 scoped responses and 36 related-propagation responses. |
| `python/.venv/bin/python tests/validate-search-contract.py` | Passed: 4 examples and 33 live responses. |
| `python/.venv/bin/python tests/validate-mapping-export-contract.py` and `tests/validate-mapping-import-contract.py` with the same Python | Passed: 3 export examples/31 live responses; 4 import examples/41 live responses. |
| `python/.venv/bin/python tests/validate-documentation.py` and its `--self-test` | Passed, including stale-qualification negative controls. |
| `python/.venv/bin/python tests/validate-license.py` and `git diff --check` | Passed. |
| From `python/`: `uv build`; repository root: `./gradlew distZip --offline` | Built wheel/sdist and standalone ZIP successfully. |

The 16 skipped Java tests require matching-GUI saved fixtures. No matching GUI
executable was available here, so actual GUI gates were not rerun. The exercised
native save/restart path passed; that is not a replacement for GUI qualification.
This read-only slice does not modify native persistence. Python 3.11 installed
artifacts, all-operation/error release qualification, reproducibility and GUI
qualification were not repeated, and historical PR #24 evidence is unchanged.

Logs are retained under `.amp/in/artifacts/`: `java-check.log`,
`instruction-final.log`, `python-check.log`, `java-dist.log` and
`installed-instructions.log`. The `installed-instructions/` JSON observations
are slice evidence from an uncommitted worktree, not a qualified release record.
Temporary extracted distributions, wheel environment and downloaded source
copies are removed after validation; the built artifacts remain under
`build/distributions/` and `python/dist/`.
