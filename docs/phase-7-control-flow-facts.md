# Phase 7 — Bounded loaded-DEX control-flow facts

**Status:** implemented locally, with the contract, decoder and validation record
below. This follows the completed
[CFG feasibility gate](phase-7-cfg-feasibility.md#findings-and-decisions), not an
assumption that Jadx exposes an original/raw CFG. The accepted work-item scope
below remains the boundary; this is not release qualification or publication.

## Goal and fixed boundary

Expose control-flow facts for one uniquely selected original METHOD through a
bounded synchronous operation. Use only copied loaded-DEX input-reader values
under the existing serialized primary-read lease. Do not generate Java, load
transformed instructions, run graph passes, call diagnostic method reprocessing,
or retain mutable Jadx nodes/readers/payload arrays outside their lease.

The evidence baseline is the integrated CFG gate and outgoing
[instruction-reference slice](phase-7-instruction-references.md). Keep Jadx
**1.5.6**, source `28ff15e4ae69950aebea110a13e5ab895d234dfc`, JDK 21 and Gradle
8.14.3 unchanged. The owned [CfgCases.smali](../tests/fixtures/cfg/CfgCases.smali)
and [JadxCfgProbeTest](../src/test/java/dev/libjadx/probes/JadxCfgProbeTest.java)
provide independent starts, branch/switch destinations, protected ranges and
lifecycle controls. Inspect the pinned reader/payload/try implementations before
extending the proof; the graph-copy probe is not a production decoder.

## Contract before controller

Design and review the OpenAPI operation, schemas, examples, limits and errors
before implementation. The proposed route is
`POST /api/v1/analysis/control-flow`, explicitly returning loaded-DEX facts, not
implementing the reserved `/api/v1/analysis/cfg` route. Prefer one complete,
bounded per-method response: no cursor store, project crawl or new job system.
Use the existing original SymbolRef, resolution/provenance outcomes, paired
expected session/logical-revision preconditions and typed error conventions.

The response contract must distinguish:

- Original method identity and a named/versioned loaded-DEX-facts representation.
  Alias names are display metadata, never lookup identities.
- Executable instruction starts/lengths and payload starts/extents/kinds. Payload
  data must not masquerade as executable control-flow targets. Preserve NOP
  instruction records without claiming their alignment/original intent. Report
  only metadata needed for these facts, not a general disassembler or
  payload-content export.
- Conditional opcode and encoded taken destination, unconditional destinations,
  and the sequential instruction boundary needed to distinguish fall-through.
  Do not synthesize basic blocks or infer general feasible successor edges.
- Packed/sparse switch keys, absolute loaded-method destinations and default
  position, separately from the switch's payload address. Reader payload targets
  are relative to the switch instruction, not the payload; use asymmetric cases.
- Typed handlers, catchall and protected intervals, with an explicit endpoint
  convention. The pinned reader's try end is inclusive. Converting to half-open
  is acceptable only when named, independently tested and consistently applied.
  A protected interval/handler table is not exhaustive exception dispatch.
- Availability AVAILABLE / UNAVAILABLE / FAILED separately from operand precision
  and overall PARTIAL analysis coverage. Valid return-only/straight-line methods
  can have no branches/tries; missing/no-code/non-DEX and failed traversal must
  not look like a successful empty fact set. Preserve ordinary NOT_FOUND,
  AMBIGUOUS and PROVENANCE_UNAVAILABLE resolution behavior.

All coordinates are method-relative `DEX_CODE_UNIT_16`, based at
`LOADED_METHOD_INSNS_START`. They are not original-input addresses, Java source
positions, byte offsets, instruction ordinals or verified configured-input/ZIP
provenance. A DEX reader can originate from a conversion plugin. Gate both class
and method reader types, and do not invent archive/input identity.

Keep `analysis.cfg` UNKNOWN and its reserved route unimplemented. Add a narrowly
named PARTIAL capability for loaded-DEX flow facts and result-level precision,
with explicit reasons/limits; do not upgrade original-offset/global-flow claims.
Mirror existing strict reference semantics: a uniquely resolved strict request
rejects this PARTIAL analysis with INCOMPLETE_ANALYSIS, even if all loaded-reader
facts were traversed. Document resolution outcomes and strict/error precedence.
Unknown fields, incompatible refs/options and malformed preconditions reject
through existing INVALID_REQUEST conventions, not silent coercion.

## Ownership, validity and bounds

- Reuse ProjectRuntime.withPrimarySymbolRead, SymbolCatalogProvider and existing
  original-member lookup patterns. Admit before touching Jadx/catalog state;
  concurrent primary reads, writes and lifecycle conflicts retain PROJECT_BUSY.
  Readiness/shutdown behavior must match existing operations. No parallel reads.
- Copy immutable facts inside the lease. Bind their snapshot identity to the
  original method, representation/version, session, logical revision, publication
  epoch, effective settings and observed content. Repeated unchanged requests
  are deterministic. Edits/comments, settings changes, input/mapping reload,
  replacement publication and restart must invalidate/rebind identity. Explicit
  save can change persisted provenance/publication without a logical edit: test
  the existing behavior and do not retain a misleading snapshot identity.
- Reader fields are reused; getTarget is not reset on non-target instructions.
  Copy targets only for target-bearing opcodes. Verify traversal length, signed
  branch arithmetic, payload kind/address/extent and target instruction starts.
  Out-of-range, middle-instruction or payload-as-executable destinations must
  fail structural validation, not be silently dropped or presented as valid.
  This validation does not claim full DEX verifier or runtime exception semantics.
- Refuse oversized work before collection/allocation where counts are available.
  Proposed fixed initial ceilings are 4096 method code units, 1024 reader records
  (including payload records), 1024 total switch cases, 256 try ranges, 1024 total
  handler entries including catchalls, and 2 MiB UTF-8 response content. Final
  documented ceilings must match OpenAPI and implementation. Count aggregate
  entries, not merely each nested array. Bound diagnostics and strings too.
- Validate variable-length payload/handler counts and remaining bytes before
  decode/allocation; the instruction adapter's header/extent checks are useful
  evidence, not permission to decode an unchecked payload. Use overflow-safe
  arithmetic. Check the actual response-byte bound independently of a rough
  object-count estimate. These are bounded observations, not hard JVM heap,
  upstream loading, decoder deadline or hostile-input isolation guarantees.
- Resource refusal is RESOURCE_LIMIT, atomically, without a truncated success.
  For malformed/truncated/unknown-opcode scans, prefer a bounded FAILED result
  with no published fact arrays; identify failure without returning unchecked
  partial destinations or masking an internal programming error. Keep this
  distinction explicit in OpenAPI, HTTP and Python models.

Keep ownership in the existing core/application/Jadx-adapter/HTTP layers. Reuse
shared helpers where their contract fits, but do not repurpose reference-specific
cursor machinery or copy its whole service into a second abstraction stack.
If a reader helper genuinely serves both adapters, preserve instruction-view
behavior and verify both consumers. No new persistence/cache/configuration layer.

## Verification and deliverables

1. Implement reviewed OpenAPI, immutable domain results, pinned reader adapter,
   serialized application service and HTTP operation. Regenerate the low-level
   Python transport with the existing pinned generator, then expose handwritten
   synchronous/asynchronous and method-object convenience access consistently.
   Do not hand-edit generated files. Update current documentation, capabilities
   and contract fixtures together; preserve historical release evidence.
2. Use the owned CFG oracle through the public HTTP interface. Check both branch
   arms, join/backward jumps, distinct negative/large sparse keys, packed keys,
   default versus case/payload addresses, valid array payloads, typed/catchall
   ranges and handler order. Extend fixtures only for missing contract boundaries.
   Include no-branch, no-code, non-DEX, ambiguity/provenance and healthy controls.
3. Prove unchanged facts before/after Java generation, actual Java failure and
   alias/comment edits, while snapshot/precondition binding changes correctly.
   Exercise accepted settings/reload/replacement, save/reopen and restart using
   existing native workflows. Reader requests must leave dirty state, revisions,
   input/native bytes and class analysis state unchanged; never autosave.
4. Test malformed/truncated/unknown opcodes, wrong payload kinds/addresses,
   invalid executable targets, oversized payload/handler declarations and exact
   limit versus limit-plus-one boundaries. Expected values come from manual
   fixture widths/bytes, not the implementation or its budget helper. Prove
   RESOURCE_LIMIT is not converted into successful or merely FAILED truncation.
5. Exercise the actual service on all five surfaces: raw HTTP, generated sync,
   generated async, handwritten sync and handwritten async. Assert typed values,
   availability/precision, strict rejection, invalid requests, stale revisions,
   readiness/busy admission, failures and limits. Include method-object access.
   Extend existing tests rather than building a parallel validation framework.
6. Run relevant CFG/instruction/Smali/decompile/reference/coordinator regressions,
   Gradle check/installDist, OpenAPI and generated-code checks, SDK checks/live
   tests, documentation validation/self-test and whitespace checks. Run broader
   existing tests if shared code changes. Record exact executed commands, results,
   skips and limits, with logs/XML under `.amp/in/artifacts/`. Update this document
   from prepared scope to actual implemented behavior/evidence.

A new public operation changes the live operation inventory. Update source-owned
contract/route/error expectations where required, but do not rewrite PR #24 or
other historical qualification matrices to pretend they qualify the new route.
If a historical-evidence validator is stale by design, report that truthfully;
fresh candidate qualification/publication remains a separate work item.

## Explicit exclusions and escalation

No original/raw or normalized/transformed CFG endpoint, independent CFG engine,
block/edge synthesis, exception-transfer inference, interprocedural graph,
incoming crawl, Java mapping, arbitrary-obfuscation guarantee, JVM flow API,
resources/classpath expansion, parallel primary reads, UI or pin upgrades.
No changes to pair/instruction-reference or Smali semantics, native formats or
explicit-save behavior. No publication/tag/deployment is included. Matching-GUI
qualification is unnecessary for a reader-only slice; any persistence change
would require a separately identified scope and actual GUI proof.

If implementation needs primary graph passes, a broader persistence/ownership
model or unsupported precision, preserve the counterexample and report the
smallest blocker instead of silently implementing a different feature.

## Implemented contract and ownership

`POST /api/v1/analysis/control-flow` (`getControlFlowFacts`) is the 23rd live
operation. The [contract](../openapi/openapi.yaml), actual-service
[response example](../openapi/examples/control-flow.json), capability example
and regenerated Python transport describe `LOADED_DEX_CONTROL_FLOW_FACTS_V1`.
`ControlFlowService` selects one original METHOD through the existing catalog
and raw-member matcher inside `withPrimarySymbolRead`. `JadxControlFlowAdapter`
returns immutable copied values; neither a reader nor a Jadx node escapes.
The HTTP layer checks the actual UTF-8 response encoding before committing it.
No new cache, cursor, job, persistence format or processing pass is involved.

AVAILABLE/EXACT describes complete traversal of this bounded loaded-reader
representation, not exhaustive analysis. Semantic coverage is always PARTIAL.
UNAVAILABLE distinguishes unresolved, no-code and non-DEX input from successful
empty branch tables; malformed scans are FAILED with every fact array empty.
Resolution misses retain 200 even under strict; uniquely resolved strict calls
return 409 INCOMPLETE_ANALYSIS after materialization. RESOURCE_LIMIT has
precedence over strict rejection. Invalid/duplicate fields and incompatible
refs/options return INVALID_REQUEST. Admission, readiness and paired revision
preconditions retain existing busy/stale/lifecycle errors.

All counts are inclusive: **4096 code units, 1024 reader records including
payloads, 1024 aggregate switch cases, 256 protected ranges, 1024 handler entries
including catchalls, 1024 UTF-16 units per handler type, and 2097152 actual UTF-8
response bytes**. Handler counts bound both encoded lists and repeated range
uses; switch counts bound encoded payloads and repeated switch uses. Oversized
declarations are refused before variable collection/allocation; unsigned DEX
code-unit counts and signed handler-count magnitude use overflow-safe checks.

Snapshots bind original method, representation, session, logical revision,
persisted identity, publication epoch, effective settings and copied facts.
Unchanged reads and Java generation retain identity. Alias/comment edits,
accepted settings/mapping/input reloads, replacement publication, native save
and restart rebind it as appropriate, including save without a logical edit.
Index-only activity is not a new source identity. Python exposes generated
sync/async calls plus `Project.control_flow`, `AsyncProject.control_flow` and
sync/async `JavaMethod.control_flow`, using the existing revision cache without
hidden retry, save or Java generation.

## Pinned production decoder audit

Inspected source **Jadx 1.5.6, revision
`28ff15e4ae69950aebea110a13e5ab895d234dfc`**, extracted locally under
`.amp/in/jadx-28ff15e4ae69950aebea110a13e5ab895d234dfc/`. The source archive's
SHA-256 was `d856c3bfaa2d3739a853bc488bcf0d9fec5c1254dda60cc22f60138bfdd67f64`.
Relevant pinned paths beneath `jadx-plugins/jadx-dex-input/src/main/java/`:

- `jadx/plugins/input/dex/sections/DexCodeReader.java`: copied readers, visitor
  reuse, signed Java storage of unsigned `insns_size`, raw code offsets, padded
  try-table placement, inclusive native try ends, unchecked handler allocations.
- `jadx/plugins/input/dex/insns/DexInsnData.java` and `DexInsnFormat.java`:
  fixed widths, branch encodings, switch-relative targets, payload byte-copy and
  skip behavior; variable payload decoders allocate declared-size arrays.
- `jadx/plugins/input/dex/sections/SectionReader.java`, `DexClassData.java`,
  `jadx/plugins/input/dex/utils/Leb128.java` and `MUtf8.java`: absolute byte
  access, type/string tables, count decoding and MUTF-8 allocation behavior.

Production does **not** call unchecked payload decode or `getTries()`. It
checks payload headers, method extents and remaining bytes before bounded
copies/skips. Branch and switch destinations must be executable instruction
starts, never payloads/interiors. Switch keys are checked for ordered signed
32-bit values. Try ranges are nonoverlapping half-open boundaries with ordered
typed handlers and optional final catchall. Bounded manual LEB decoding checks
encoded and aggregate handler counts, minimum remaining bytes, type-table
indices, string length/termination and handler destinations before using the
pinned type/MUTF-8 decoder. A public byte-copy accessor temporarily retargets
only the borrowed visitor's byte window/index, restoring those fields
immediately; there is no reflection or reread of a possibly changed input file.

Malformed/buffer/index/DexException failures become atomic FAILED observations;
resource refusal is not caught by that path. Unexpected programming exceptions
are not blanket-converted into successful empty results. These checks are not a
DEX verifier, hard heap/deadline budget or sandbox for hostile upstream loading.

## Executed validation and limits

Implemented on `tartuf-vm`, Linux x86_64, OpenJDK **21.0.12.1**, Gradle
**8.14.3**, locked Jadx **1.5.6**, Python **3.14.4**. Baseline HEAD is
`76a838dcd2cbee095ed33fbff4acbad5aafbc973`, containing the five integrated CFG
probes and owned fixture. The source-thread handoff patch was transferred,
verified as **15348 bytes / SHA-256
361cdb5e672efc7f9bd7fe0cc57c0b8d761b113bd25e057f0d2649060264836d**, checked
with `git apply --check` and applied once before implementation.

`ControlFlowEndpointsTest` exercises the public HTTP contract with real Jadx:

- Independently enumerated `CfgCases.smali` starts, both branch arms/backward
  jumps, packed 4/5 and sparse -5/1000 keys, distinct switch/default/payload
  positions, valid array extent, half-open typed/catchall ranges and order.
- AVAILABLE straight-line, abstract/no-code, non-DEX, missing/provenance controls;
  actual Java generation/failure, healthy neighbors, unchanged reader/class
  analysis state, revisions/dirty state/input/native bytes, no autosave.
- Alias/comment replacement, accepted mappings settings, external mapping and
  input replacement reload, explicit save/reopen and process restart binding.
  Save changes snapshot identity without changing logical revision. Busy and
  readiness admission, strict/missing precedence and invalid/duplicate JSON.
- Checksum-repaired DEX mutations: unknown/truncated instruction scans,
  middle-instruction/payload/overflow destinations, wrong payload kind/address,
  overflowing payload extent, malformed protected ends/handler offsets/targets.
  All malformed observations publish no facts; healthy methods remain usable.
- Independent exact/+1 code-unit, record, case, try, encoded/aggregate handler
  and type-length boundaries; oversized unsigned code-unit and signed-minimum
  handler declarations; legal-count Unicode responses over 3 MiB; actual
  **2097152-byte success versus 2097153-byte RESOURCE_LIMIT**, using independent
  UTF-8 width arithmetic and the real serialized response, not a budget helper.

The existing shared `SymbolCatalogTest` proves bounded AMBIGUOUS selection with
a synthetic duplicate catalog. The real pinned loader merges duplicate inputs;
this slice does not claim a new real-service ambiguity/provenance proof or
fabricate distinct originals. Original-input/ZIP attribution stays unverified.

Commands executed from the repository root (artifacts are local, untracked):

```bash
./gradlew test check installDist distTar
# Final: 512 tests, zero failures/errors; 16 opt-in GUI skips (496 executed).
# Log: .amp/in/artifacts/gradle-final-full.log; java-final-full-xml/.

./gradlew test --tests 'dev.libjadx.probes.JadxCfgProbeTest' \
  --tests '*Instruction*' --tests '*Smali*' --tests '*DecompileEndpointsTest' \
  --tests '*ReferenceEndpointsTest' --tests '*ReferenceSnapshotTest' \
  --tests '*OperationCoordinatorTest' --tests '*ControlFlowEndpointsTest' \
  check installDist distTar
# 51/51, no skips; gradle-final-targeted.log and java-final-targeted-xml/.
# Final full gate above also includes the subsequent unsigned-size control.

PATH="$PWD/python/.venv/bin:$PATH" python/.venv/bin/python python/scripts/generate.py
PATH="$PWD/python/.venv/bin:$PATH" python/.venv/bin/python python/scripts/check_generated.py
# Pinned generator 0.29.1 / ruff 0.16.9; 183 generated files match.
python/.venv/bin/python tests/validate-openapi.py
# OpenAPI 3.1 valid, 23 operations, 42 JSON examples, 3 SSE frames.
python/.venv/bin/ruff check python
python/.venv/bin/ruff format --check python
python/.venv/bin/mypy --config-file python/pyproject.toml python/src/libjadx
# Passed: 32 formatted files; mypy 12 handwritten source files.
python/.venv/bin/python -m pytest python/tests/unit python/tests/integration \
  --junitxml=.amp/in/artifacts/python-final.xml
# 204/204, including 29 live tests; python-final.log/xml.

.amp/in/uv/uv build python --out-dir .amp/in/artifacts/python-dist
tar -xf build/distributions/libjadx-0.1.0-alpha.1.tar -C .amp/in/candidate
LIBJADX_DISTRIBUTION="$PWD/.amp/in/candidate/libjadx-0.1.0-alpha.1/bin/libjadx" \
LIBJADX_EXPECT_INSTALLED=1 \
LIBJADX_RELEASE_EVIDENCE="$PWD/.amp/in/artifacts/flow-client-evidence" \
  .amp/in/installed/bin/python -m pytest python/tests/release/test_routes.py \
  --junitxml=.amp/in/artifacts/installed-routes-final.xml
# 21/21 on the extracted distribution and clean installed wheel.
# Rebuilt wheel was reinstalled; -k control_flow also passed 5/5.

python/.venv/bin/python tests/validate-documentation.py
python/.venv/bin/python tests/validate-documentation.py --self-test
python/.venv/bin/python tests/release/test_evidence.py
git diff --check
# 53 Markdown files / 498 relative links and anchors valid; self-test passed;
# 7 evidence-tool regressions passed; whitespace clean.
```

The installed-route matrix exercises all **23 operations on all five surfaces**:
raw HTTP, generated sync/async and handwritten sync/async. The new operation's
AVAILABLE/UNAVAILABLE/FAILED, precision/coverage, strict/invalid/stale errors,
readiness/busy admission and atomic limits retain typed values and exact wire
fidelity. Seven live parameterized cases additionally exercise both method-object
forms. `flow-client-evidence/` contains local route/error/process observations;
these are slice evidence, not new versioned release qualification. Earlier
test-only compile/fixture mistakes and the stale 22-operation unit expectation
were corrected before the passing runs; failed attempts are not counted green.

No persistence implementation, dependency pin, reference/Smali behavior or
historical release evidence changed. Native explicit-save/reopen/lifecycle
regressions passed; **matching-GUI and full release-candidate qualification were
not rerun** for this reader-only slice. Python 3.11, Windows and hostile-input
isolation were not newly qualified. `analysis.cfg` remains UNKNOWN and its route
unimplemented. A graph API still needs separately proved ownership/capture;
there is no blocking design decision for this slice. Changes remain local and
uncommitted; no push, merge, tag or publication is implied.

Integration must reconcile the source thread's three prepared document edits
(`IMPLEMENTATION.md`, `docs/README.md`, this work item). A full baseline patch
must not be blindly applied over those retained uncommitted edits.
