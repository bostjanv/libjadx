# Phase 7 — CFG feasibility work item

**Status:** feasibility gate completed locally on 2026-10-17; no public feature
implemented. Real pinned-Jadx probes distinguish loaded-DEX flow facts from
normalized and later transformed block graphs. **An exposed original/raw CFG
was not found; do not relabel Jadx's normalized graph as one.** This follows the
[loaded-DEX instruction-reference slice](phase-7-instruction-references.md) and
does not advertise a new capability or renew historical release qualification.

## Goal and starting state

Determine which per-method control-flow graphs pinned Jadx can expose safely,
and recommend the smallest independently justified public slice. Investigate
loaded DEX first; distinguish input-level control flow from Jadx's transformed
graph rather than assuming one proves the other.

- Jadx remains **1.5.6**, source
  `28ff15e4ae69950aebea110a13e5ab895d234dfc`; JDK 21 and Gradle 8.14.3 are unchanged.
- `analysis.cfg` is currently UNKNOWN with NOT_PROBED evidence. The HTTP router
  reserves `/api/v1/analysis/cfg`, but the operation has no implemented handler
  or published OpenAPI contract. Leave both facts unchanged during this gate.
- The instruction-reference adapter proves method-relative loaded-DEX
  `DEX_CODE_UNIT_16` coordinates from `LOADED_METHOD_INSNS_START`, not original
  input provenance, a block graph, Java correspondence or exception-flow coverage.
- [Java completeness maintenance](java-completeness.md) shows that code generation
  unloads method state. Graph lifetime and recovery must be demonstrated, not
  assumed from a successful cold read.

## Approved investigation scope

### 1. Establish the pinned graph lifecycle

Inspect the pinned `MethodNode`, `BlockNode`, method-loading and block-building
passes, `ProcessClass`, and input-reader branch/payload/try metadata. Record
exact source paths, pass ordering and the point at which each observation is
captured. Determine:

- Whether an input-level graph is exposed at all, or only an early normalized
  Jadx graph. Do not label an early transformed graph raw/original.
- Which processing builds the transformed graph; which instructions, blocks or
  edges can be merged, removed, synthesized or reordered.
- How normal successors, conditional alternatives, switch keys/defaults,
  exceptional transfers and handlers are represented. Distinguish reported
  relationships from exhaustive exception semantics.
- What remains after Java generation, graph unload and real decompiler failure,
  and whether safe repeated reads require rebuilding analysis.

A DOT export or parsed Java/Smali is not graph evidence. If the required raw
graph is unavailable, record that result; do not quietly build an independent
CFG engine or substitute the transformed graph.

### 2. Add a small real-Jadx probe with independent expectations

Use an owned, directly assembled DEX with manually derived instruction starts,
branch destinations and expected edge sets. Keep asymmetric branch arms and
distinct switch targets so reversed or merged edges cannot pass accidentally.
Cover:

| Case | Required observation or control |
| --- | --- |
| Straight-line return and an unconditional jump | Entry/exit behavior and actual successor edges, not just block counts. |
| Conditional branch and loop | Both alternatives, a join and a back edge; no reliance on incidental Jadx block numbering. |
| Packed/sparse switches and array payload | Keys/default distinguished; payload data is not executable flow. |
| Typed catch, catchall and explicit throw | Handler entry and normal versus exceptional relationships, with limitations stated. |
| Unreachable code or a simplifiable branch | Demonstrate any raw/transformed difference instead of expecting identical block layouts. |
| No-code method, ordinary JVM input and malformed/truncated DEX | Unsupported/unavailable and failed observations are not successful empty graphs. |
| Real Java-generation failure and an unrelated healthy method | Determine which graph, if any, remains recoverable without poisoning the healthy control. |

Reuse [HierarchyFixture](../src/test/java/dev/libjadx/probes/HierarchyFixture.java)
for owned assembly and [LocalDex](../tests/fixtures/variables/LocalDex.smali)
for branch/loop inspiration. [LocalShapes](../tests/fixtures/variables/LocalShapes.java)
and [UnusedCatch](../tests/fixtures/variables/UnusedCatch.smali) provide further
controls. [InstructionVariants](../tests/fixtures/references/InstructionVariants.smali)
is reader evidence, not a ready-made semantic CFG oracle: its payload method
uses an uninitialized array register. Use a valid minimal payload fixture for
positive graph expectations, and keep intentional invalid cases separate.

Compare cold, repeated and post-Java observations for the same original method.
Check alias independence. Copy immutable graph observations at the proven
lifecycle boundary; never retain mutable Jadx nodes or the reused input visitor.
Extend existing fixtures where their contract fits; add only the probe/fixture
needed to isolate this responsibility. Report actual results, not just a dump.

### 3. Establish a safe integration boundary and recommendation

Use [ProjectRuntime.withPrimarySymbolRead](../src/main/java/dev/libjadx/app/ProjectRuntime.java)
and the existing fail-fast serialized primary-read model as the integration
constraint, not a promise that arbitrary graph passes are safe under that lease.
Prove the proposed processing scope and side effects. If it needs global passes,
changed primary settings or another ownership model, document the blocker and
smallest design proposal before any production implementation.

Recommend, without publishing a schema yet:

- Separate availability and precision for each representation, including
  successful empty/no-code/failure distinctions and strict/partial behavior.
- Snapshot-scoped block/edge identities, explicit edge kinds, and any proved
  loaded-DEX offsets. Synthetic or transformed blocks must not receive invented
  original offsets; graph IDs must not imply stability across representations.
- Method/original-descriptor, session, revision, settings and publication
  binding; alias/edit/save/reload/restart invalidation requirements. Never equate
  a method graph with exhaustive program coverage or verified input provenance.
- Before-collection limits for instructions, blocks, edges and copied bytes,
  with oversized/malformed controls and no truncated-success claim. State what
  bounds do not constrain in Jadx's own processing.

## Deliverables and acceptance

1. A focused executable feasibility probe, using the real pinned engine and
   independently expected flow relationships. Positive, negative and lifecycle
   controls must distinguish plausible wrong interpretations.
2. Update this document with pinned-source findings, actual commands/results,
   reproducible fixture expectations, graph-lifetime/exception-flow limits and
   an explicit supported/partial/unavailable decision for each representation.
   Store logs and copied observations under `.amp/in/artifacts/`.
3. Recommend a bounded follow-on implementation, or record the evidence-backed
   reason to defer it. A failed feasibility assumption must remain visible;
   it is not permission to advertise an approximate graph as exact.
4. Run the focused probe and relevant existing instruction/source regressions;
   validate documentation and run `git diff --check`. If shared production code
   becomes necessary, stop treating the work as a probe-only task and identify
   the separate implementation scope.

The executable [JadxCfgProbeTest](../src/test/java/dev/libjadx/probes/JadxCfgProbeTest.java)
now covers this gate. Results and exact commands follow below. This gate changes
no persistence and requires no GUI feature. No matching GUI installation was
assumed or used on `tartuf-vm`; startup smoke testing would not qualify native
persistence in any case.

## Explicit exclusions and follow-on boundary

No public CFG endpoint/schema or generated SDK changes in this work item; no
capability upgrade, rendering/UI, incoming project crawl, interprocedural graph,
resources/classpath expansion, new engine/cache/persistence mechanism or parallel
primary reads. Do not change the established Smali/instruction-reference views
or dependency pins, and preserve historical release evidence.

After a usable subset is proved, a separate implementation work item must review
OpenAPI before its controller, regenerate the Python transport, and validate raw
HTTP plus generated and handwritten sync/async clients, bounds, busy admission,
strict/partial responses and stale-state behavior. Full candidate qualification
and publication remain separate from this feasibility gate.

## Findings and decisions

These are **internal feasibility results**, not new public capability values.
`analysis.cfg = UNKNOWN / NOT_PROBED` and its reserved, unimplemented HTTP route
are deliberately unchanged; the public evidence string is not this probe's result.

| Representation / fact | Internal feasibility decision at pinned 1.5.6 |
| --- | --- |
| Original-input/raw block CFG | UNAVAILABLE through the inspected input/core boundaries. Readers expose instructions, branch operands, payloads and try tables, not a block graph. Constructing one would be a separately designed CFG engine, expressly excluded here. Original-input provenance remains unverified. |
| Loaded-DEX instruction/branch/switch/try facts | SUPPORTED for the owned bounded subset, independent of Java generation. Exact coordinates are method-relative 16-bit code units of the **loaded** DEX only. Handler tables are not exhaustive feasible exception transfers. |
| Immediately after `BlockSplitter` | PARTIAL early **normalized** graph. Exact copied Jadx topology at that boundary, including entry/exit and temporary handler links; not an original CFG or an exhaustive exceptional-flow model. |
| After `BlockFinisher` | PARTIAL normalized graph, AUTO/RESTRUCTURE. Unreachable removal, merging, loop splitters and exception splitters already applied. Full successors retain back edges; clean successors do not. |
| Immediately before `RegionMakerVisitor` | PARTIAL later transformed graph, AUTO/RESTRUCTURE. SSA, inlining, type inference and shrinking have changed instructions. Top-level instruction offsets are not complete input coverage or source correspondence. |
| Post-Java live graph | UNAVAILABLE after ordinary unload. Copied values survive; cached Java reads do not reconstruct blocks. Diagnostic reprocessing recovers healthy graphs but is not an observational read. |
| Invalid-register method | FAILED graph analysis, not successful empty. Original reader facts survive the actual Java pipeline failure. Reaching a pass boundary does not establish a valid graph. |
| Abstract/no-code method | UNAVAILABLE (`getCodeReader() == null`), not successful empty. A real return-only method still has entry/exit and return flow; this probe found no reason to invent an empty successful graph. |
| Ordinary JVM input | Loaded-DEX facts UNAVAILABLE. A normalized internal graph is obtainable on the owned JVM control, but its offsets cannot inherit DEX/original-input precision. No JVM public slice is recommended here. |
| Truncated/reserved-opcode reader | FAILED/rejected; never an empty successful scan. Oversized input/copy budgets reject rather than truncate. |

### Pinned source and capture boundaries

Inspected source is exactly Jadx **1.5.6** commit
`28ff15e4ae69950aebea110a13e5ab895d234dfc`, downloaded from the commit-pinned
codeload archive (SHA-256
`d856c3bfaa2d3739a853bc488bcf0d9fec5c1254dda60cc22f60138bfdd67f64`).
The local source audit records inspected paths and their SHA-256 digests.

- [ICodeReader](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-plugins/jadx-input-api/src/main/java/jadx/api/plugins/input/data/ICodeReader.java),
  [InsnData](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-plugins/jadx-input-api/src/main/java/jadx/api/plugins/input/insns/InsnData.java)
  and [DexCodeReader](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/sections/DexCodeReader.java):
  `copy`, `visitInstructions`, `getUnitsCount`, `getTries`. The visitor reuses
  one `DexInsnData`. Branch `getTarget` is absolute within the loaded method;
  switch payload targets are deltas relative to the switch instruction, not the
  payload. Try ends are inclusive (`start + insnsCount - 1`), not half-open.
- [DexInsnData](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/insns/DexInsnData.java)
  / [DexInsnFormat](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/insns/DexInsnFormat.java):
  payload decoding is distinct from executable flow. `getOpcodeMnemonic()`
  reports `nop` for the array payload; use the decoded API opcode/payload kind.
  `getTarget()` is not reset for non-target instructions, so the probe copies it
  only for target-bearing opcodes. Arrays and visitor objects are never retained.
- [MethodNode](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/nodes/MethodNode.java):
  `load` decodes instructions, not basic blocks; `unload` clears instructions,
  blocks, entry/exit, region and handler state but keeps the copied code reader.
- [Jadx.getRegionsModePasses](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/Jadx.java):
  `CheckCode`, debug information, `AttachTryCatchVisitor`, comments/details and
  `ProcessInstructionsVisitor` precede `BlockSplitter`; `BlockProcessor` and
  `BlockFinisher` follow it. The AUTO/RESTRUCTURE `rawCFGOutput` DOT point is
  **after all three**, while SIMPLE's is after splitting. The same word “raw”
  therefore does not identify one fixed boundary across modes. SIMPLE has no
  `BlockFinisher`/region stage; FALLBACK has no block builder.
- [ProcessInstructionsVisitor](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/visitors/ProcessInstructionsVisitor.java):
  attaches switch/default jumps, fixes payload target deltas, removes payloads
  and folds move-result into invoke before any block snapshot.
- [BlockSplitter](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/visitors/blocks/BlockSplitter.java):
  adds entry/exit, splits/joins instructions, removes gotos/NOPs and empty
  blocks, creates temporary handler edges, then unloads the instruction array.
- [BlockProcessor](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/visitors/blocks/BlockProcessor.java)
  / [BlockExceptionHandler](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/visitors/blocks/BlockExceptionHandler.java):
  remove unreachable blocks, merge/reorder blocks, compute dominators, modify
  loops and wrap protected regions with synthetic splitters. Handler processing
  filters catch attributes with `canThrowException`, removes temporary links,
  and links top/bottom splitters to handlers; these are structural relationships,
  not a proved instruction-to-handler exception dispatch relation.
- [BlockNode](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/nodes/BlockNode.java),
  [IfNode](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/instructions/IfNode.java)
  and [SwitchInsn](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/instructions/SwitchInsn.java):
  block position is mutable; cId is only constant for that node, not a persistent
  identity. Synthetic blocks can share another block's start offset. IfNode has
  an explicit operator/then/else; SwitchInsn has keys/target blocks/default.
  `getCleanSuccessors` excludes loop back edges as well as exception paths.
- [ProcessClass](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/ProcessClass.java)
  / [DepthTraversal](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/visitors/DepthTraversal.java):
  class generation can process dependencies and unloads the class on success.
  `processMethodUntilVisitor` unconditionally unloads/reloads one method without
  advancing class state; a true return does not detect visitors skipped after
  a recorded method error. Retrying it is not a safe graph getter.

The probe inserts read-only copying visitors only in its own disposable engine:
after `BlockSplitter`, after `BlockFinisher`, and immediately before
`RegionMakerVisitor` (after SSA/inference/inlining/shrinking). Each result owns
immutable lists, scalar offsets, operator/key values and snapshot-local IDs;
no Jadx node, reused reader visitor, payload array or live attribute escapes.
No DOT output or parsed Java/Smali is used as graph evidence.

### Independent fixture oracle and actual observations

[CfgCases.smali](../tests/fixtures/cfg/CfgCases.smali) is owned and assembled with
the existing `HierarchyFixture.dex` and pinned Smali runtime. Comments count DEX
instruction widths manually. Early expected edge sets are derived from those
instructions and the inspected splitter algorithm, not graph output or incidental
block numbers. `ENTRY`/`EXIT` below are explicitly synthetic.

| Method | Independent input oracle and verified result |
| --- | --- |
| `straight` | Starts 0,2; constant then return. Split edges ENTRY→0→2→EXIT. Finishing merges into block 0; later constant inlining leaves only top-level RETURN at offset 2. |
| `jump` | Starts 0,1,2,4,5,7; goto at 1 targets 5, not unreachable 2/4. Split edges ENTRY→0, 0→5, 2→4, 4→EXIT, 5→7, 7→EXIT. GOTO is already absent. Finishing removes 2/4 and merges the reachable path; pre-regions has ENTRY→0→EXIT with only top-level RETURN at 7. |
| `branchLoop` | Starts 0,1,3,5,7,8,10,12,13. IF_LEZ at 1: true→13, false→3; IF_EQZ at 3: true→8, false→5. Arms add **7 versus 3**, join at 10; goto at 12 returns to 1. Split edges assert both alternatives, 5→10, 8→10 and 10→1. Finishing inserts three empty synthetic nodes; its 10→1 back edge exists in full successors and disappears in clean successors. |
| `packed` / `sparse` | Switch at **1**, default at 4, distinct targets 7/10; payload at 14. Packed keys 4/5, sparse keys -5/1000. Reader deltas 6/9 become graph targets 7/10, preventing a false absolute-offset interpretation. Split graphs contain all three alternatives and returns; payload/alignment NOP is not executable flow. Finishing merges constant/return arms; keys/default remain distinct at pre-regions. |
| `array` | Starts 0,1,3,6,7,9,10. A valid array is initialized before fill at 3 targeting payload 10; two int elements 13/29. Split edges ENTRY→0→9→EXIT; payload 10 is not a block/instruction. Unlike the references fixture, this is a positive Java control. |
| `catches` | Starts 0,1,2,4,5,6,7,9,10,11,13; protected inclusive units 1–4, typed RuntimeException handler at 6, catchall at 10. Conditional at 2 goes to return 5 or explicit throw 4. Splitter creates **0→6 and 0→10 temporary** edges, not 4→handler edges. Finishing removes those, adds a top splitter before 1 and bottom splitter after 4, each linked to handlers. Full expected edges are asserted using splitter roles, not fixed cIds; pre-regions retains this topology. |

All seven positive code-bearing methods produce Java without a recorded error.
Abstract `absent` has no reader/graph. The existing `Disassembly.broken` fixture
really fails `CheckCode` (`return v5` with one register): graph analysis fails,
its reader still reports offset 0, and an unrelated JVM method still generates
`return 37;` without an attributed failure.

Cold/repeated/post-Java `BlockFinisher` copies of `branchLoop` match, including
after class/method aliases. Java generation unloads live blocks and cached Java
does not rerun graph passes. Diagnostic recovery leaves the owner saying
`GENERATED_AND_UNLOADED` while its method holds blocks: a concrete reason not to
publish that operation as a primary read. No `DONT_UNLOAD_CLASS`, changed primary
settings, global pass rerun or persistent graph cache was introduced.

### Safe integration and smallest follow-on recommendation

The runtime probe uses the real `ProjectRuntime.withPrimarySymbolRead` lease.
Copying input facts leaves class state, revisions, dirty state and input bytes
unchanged; a conflicting primary read gets `PROJECT_BUSY`. Existing HTTP source,
instruction and snapshot regressions verify the surrounding serialized behavior.
This proves the **reader-fact scope only**, not arbitrary graph processing.

**Recommend first: a bounded per-method loaded-DEX control-flow-facts view, not
an “exact raw CFG.”** Expose only branch destinations, switch keys/loaded-method
targets, executable-vs-payload distinction and typed/catchall protected-range
metadata. This needs no processing passes and has the demonstrated serialized
primary boundary. Do not synthesize blocks or feasible exception edges. It needs
a separately reviewed contract/SDK work item; no schema is published here.

For an actual block-graph follow-on, the smallest plausible design is one named
normalized representation after `BlockFinisher`, initially loaded DEX in
AUTO/RESTRUCTURE, using read-only capture during an **existing single-operation
temporary analysis** initialized with current settings and the immutable unsaved
edit snapshot. Do not call diagnostic method reprocessing on the published
primary, keep all graphs loaded, or reset global passes to recover a graph.
Production admission, unsaved-edit replay, owner/dependency costs, capture-failure
isolation and limits still need dedicated integration proof for that design;
they are not established by the disposable-engine probe. Raw CFG remains
unavailable unless a separately approved independent reconstruction effort is
justified. A dual raw/transformed CFG endpoint is not yet justified.

Requirements for either follow-on:

- Separate representation availability from precision: AVAILABLE facts may be
  exact loaded operands while overall coverage is PARTIAL; missing/no-code or
  non-DEX is UNAVAILABLE, parse/pass failure is FAILED, and resource refusal is
  not truncated success. Strict original/exhaustive-flow requests must reject
  incomplete analysis, not relabel it exact.
- Bind original method descriptor, representation/version, session, logical
  revision, publication epoch and effective-settings fingerprint. Alias edits
  still invalidate the binding even if operands do not change; comments,
  settings/input/mapping reload and replacement invalidate it too. Restart
  invalidates session-local IDs. Save must not turn a loaded observation into
  original-input provenance: invalidate/rebind if persisted provenance or
  publication changes, even when logical edits are unchanged.
- Future block/edge IDs belong only to one copied graph snapshot. Preserve
  IF_TRUE/IF_FALSE with operator, switch case key/default, ordinary successor
  and synthetic/handler-structural kinds; never label temporary or splitter
  relationships exhaustive EXCEPTION_TRANSFER. Entry/exit and synthetic blocks
  have no original bytecode offset. Even non-synthetic transformed start offsets
  are hints, not original input ranges. No Java-source mapping, cross-graph
  stable block IDs or whole-program completeness.
- Before collecting, bound code units/instructions, switch cases/try handlers,
  blocks, edges and copied payload bytes; reject overrun atomically. The probe
  applies 4096 code units/1024 reader items and graph-copy limits of 1024 top-level
  instructions, 512 blocks, 2048 successors and a conservative 2 MiB ASCII
  observation bound. It verifies inclusive limits and each limit-minus-one
  rejection on a real 7-instruction/12-block/13-edge graph (10112-byte bound),
  plus refusal of a 4097-unit method before processing. The bound is for copied
  observation content, **not total JVM heap**; production encoding needs its own
  independently checked byte bound. These limits do not bound Jadx loading,
  payload-decoder allocation, class/dependency processing, transient IR memory
  or in-process cancellation. They are not a hostile-input safety proof.

### Validation and delivery state

Run on `tartuf-vm`, Ubuntu Linux x86_64, OpenJDK 21.0.12.1, Gradle 8.14.3,
unchanged locked Jadx 1.5.6. JDK 21 was installed locally because this runner had
no Java; no GUI executable was required. Logs, full copied observations, XML
results and source digests are retained under `.amp/in/artifacts/`.

```bash
./gradlew test --tests dev.libjadx.probes.JadxCfgProbeTest

./gradlew test \
  --tests dev.libjadx.probes.JadxCfgProbeTest \
  --tests dev.libjadx.probes.JadxInstructionReferenceProbeTest \
  --tests dev.libjadx.probes.JadxSmaliProbeTest \
  --tests dev.libjadx.app.DecompileEndpointsTest \
  --tests dev.libjadx.app.InstructionReferenceEndpointsTest \
  --tests dev.libjadx.app.ReferenceEndpointsTest \
  --tests dev.libjadx.app.ReferenceSnapshotTest \
  --tests dev.libjadx.scheduler.OperationCoordinatorTest check installDist

python3 tests/validate-documentation.py
python3 tests/validate-documentation.py --self-test
git diff --check
```

The focused probe passed **5/5**; the combined gate passed **46/46**, zero
failures/errors/skips. Documentation validation, its self-test and whitespace
validation results are recorded with the final validation artifacts.
An initial compilation treated an Iterable as a stream; that test-only mistake
was corrected. The first executing attempt expected a payload-specific mnemonic
and failed (`nop` versus array-payload); source inspection established the decoded
opcode distinction above. No failed attempt is counted as validation.

No production Java, OpenAPI, SDK, dependency, native persistence or historical
release-evidence files changed. Full candidate qualification, matching-GUI
round-trip and cross-client release gates were **not rerun**: this is a local
probe-only slice, not a release qualification. APK/multidex provenance, arbitrary
obfuscation, JVM graph semantics, exhaustive exception dispatch and hard
memory/deadline bounds remain unproved. No commit, push, merge, tag or publication
is implied by these local results.

### Integration verification

On 2026-10-18, all seven completed files were recovered on `foobar-vm` and
verified against the saved SHA-256 manifest before integration. The completed
documents extend the original prepared handoff; its overlapping edits were
reconciled rather than applying the baseline patch over them blindly.
The same eight-class combined Gradle command above was rerun here: **46/46**
tests passed, zero failures/errors/skips, including all five CFG probes;
`check` and `installDist` succeeded. Documentation validation checked 52 files
and 481 links/anchors; its self-test and staged whitespace checks also passed.
Fresh logs and test XML are retained under `.amp/in/artifacts/cfg-review/`.
This integration verification does not extend the release or GUI qualification.
