# Phase 7 — Isolated normalized CFG capture integration gate

**Status:** investigation completed locally on 2026-10-19; **BLOCKED/DEGRADED**
for public integration, not an implemented feature or public-contract change.
Immutable post-BlockFinisher copies and unsaved edit replay work, but filesystem
binding and method-only cost assumptions fail. The counterexamples, remaining
limits and smallest follow-on proposal are recorded under
[findings and decision](#findings-and-decision). The accepted scope below is
retained for review. The [CFG feasibility investigation](phase-7-cfg-feasibility.md)
and [bounded loaded-DEX facts](phase-7-control-flow-facts.md) remain the baseline.
`analysis.cfg` remains UNKNOWN and `/analysis/cfg` unimplemented.

## Goal and fixed boundary

Prove or disprove that one bounded original METHOD can return an immutable copy
of a named **normalized graph immediately after BlockFinisher**, using the
existing single-operation temporary-analysis lifecycle. Start with loaded DEX in
AUTO/RESTRUCTURE only. This is not an original/raw graph, exhaustive exception
dispatch, Java correspondence or a promise of method-only processing cost.

Keep Jadx **1.5.6**, source `28ff15e4ae69950aebea110a13e5ab895d234dfc`, JDK 21
and Gradle 8.14.3. Inspect the pinned source before using graph/pass APIs. The
earlier disposable-engine capture proves topology at a boundary, not production
admission, unsaved-state replay, input stability or bounded owner/dependency work.

Use the existing runtime's immutable native edit/settings snapshot and disposable
engine ownership. Never process/reprocess/reset passes on the published primary,
keep primary graphs loaded, install global visitors or retain mutable Jadx
nodes/readers/attributes. Do not reconstruct a graph from the reader facts,
Smali, Java or DOT output. No persistence, pin, public SDK or API changes.

## Existing ownership path to investigate

- `ProjectRuntime.withTemporarySourceRead` / `withTemporaryAnalysis` captures
  repository revisions, publication, inputs/mappings, deep-copied code data and
  effective settings before `finishSnapshotCapture`, then creates/loads/closes
  the temporary engine. Its callback is a borrowed reference, not an owner.
- `JadxEngineFactory.arguments` deep-copies native code data; it does not freeze
  filesystem input or mapping bytes. `TemporaryAnalysisTest` already proves
  captured unsaved aliases/comments survive a later primary edit. Extend that
  proof for actual graph capture, not a mock graph or only generated text.
- `OperationCoordinator` permits primary work after the brief temporary snapshot
  gate, rejects a second temporary operation and retains shutdown/lifecycle
  policy. Prove the real graph path obeys these rules; do not add parallel primary
  reads or hold the coordinator monitor during Jadx work.
- `JadxCfgProbeTest`, `CfgCases.smali`, `ControlFlowEndpointsTest` and
  `DecompileEndpointsTest` provide independent topology/reader/failure oracles.
  Reuse them rather than creating a second analysis framework.

Prefer test-only capture visitors and probes through the existing package-local
temporary callback. Do not build a production graph adapter, public DTOs or a
one-use runtime abstraction merely to make this gate pass. If the current
boundary cannot support the proof, preserve the failing fixture and document the
smallest ownership/admission change for a later reviewed implementation slice.

## Proof obligations

1. **Real capture and immutable ownership.** Select the exact original method
   without alias lookup and gate both class and method reader types. Capture at
   the specified post-BlockFinisher point, not an ambiguously named raw DOT
   boundary or a later region pass. Copy only bounded scalar/immutable values;
   no Jadx identity escapes. Show the copy remains usable after engine close and
   a new operation. Distinguish no-code/non-DEX/mode unavailability, processing
   failure and successful return-only graphs. A visited pass alone is not success:
   inspect retained method/owner errors, including failures later in generation.
2. **Independent semantics.** Verify both conditional arms with operator, backward
   edges, asymmetric packed/sparse cases/default, joins, entry/exit and ordered
   synthetic/handler-structural relationships against manually reasoned owned
   fixtures. Full successors and clean successors differ; do not lose back edges.
   Payload data is not executable graph content. Block IDs are snapshot-local;
   synthetic/entry/exit nodes have no original offset and transformed offsets are
   hints, not exhaustive original instruction ranges. No EXCEPTION_TRANSFER or
   feasible-edge guarantee is established by a protected range or splitter link.
3. **Unsaved snapshot and state isolation.** Replay supported unsaved declaration
   renames/comments, effective settings and mappings into the temporary engine.
   Demonstrate their presence from real engine state and prove the primary's
   code data is not shared. Hold temporary work with deterministic gates, mutate
   the primary after snapshot capture, then prove the result still binds its
   captured revision while a new request sees the newer state. Cover prior Java
   success/unload and actual Java failure with a healthy neighboring method.
   Primary class state, reader facts, source behavior, revisions/dirty state and
   native/input bytes must remain unchanged by the capture itself; no autosave.
4. **Admission, lifecycle and cleanup.** Exercise snapshot-gate conflicts, allowed
   primary reads/writes after capture, second-temporary PROJECT_BUSY, failure
   during load/capture/later generation and resource refusal. Use actual Jadx for
   graph/failure semantics; deterministic factory/latch instrumentation may
   observe resource ownership. Prove every owned engine/lease is released and a
   subsequent healthy operation works. Cover shutdown and conflicting reload/save
   policies without weakening existing guarantees or claiming hard interruption.
5. **Input validity and binding.** Determine what happens when input or mapping
   bytes change between snapshot capture and temporary load/processing. Paths and
   deep code-data copies alone are not immutable input snapshots. Use controlled
   filesystem changes to distinguish current behavior from the desired binding;
   never label changed-file observations with an old content identity. Probe
   explicit save without logical edit, accepted settings/input/mapping reload and
   restart. Recommend a truthful binding/refusal strategy; if it needs a new input
   snapshot mechanism or lifecycle change, report it as a blocker/design proposal,
   not silently add persistence, file copies or automatic retries.
6. **Bounded work versus bounded output.** Reuse the reader slice's preflight
   ceilings where useful: 4096 units, 1024 records/cases/handlers, 256 tries and
   bounded strings. Prove copied graph ceilings independently (initial candidates:
   1024 top-level instructions, 512 blocks, 2048 successor relationships, 2 MiB
   actual encoded observation bytes). Check before collection/allocation where
   counts are available; exact versus +1 refuses atomically, never truncates.
   Separately measure temporary loading and owner/dependency processing: put a
   tiny selected method beside large siblings/dependencies and inject capture
   refusal to identify work already done or still continuing. Payload/handler
   safety must not assume the unchecked upstream decoder is an untrusted-input
   boundary. Output limits do not bound JVM heap, all Jadx passes or deadlines.
   Recommend an enforceable scope/admission model, or say what remains unbounded.

## Deliverables and decision

Add focused real-runtime integration probes, extending existing tests/fixtures
where appropriate, and update this work item with exact pinned source paths,
observations, commands, results, skips and limits. Keep logs/XML/copied bounded
observations under excluded `.amp/in/artifacts/`; update current roadmap/index
status without rewriting historical qualification evidence.

Run the new gate plus existing CFG, temporary-analysis, coordinator, source,
loaded-DEX facts, instruction and Smali regressions; run Gradle check/installDist,
documentation validation/self-test and whitespace checks. Run broader tests if
shared production code changes become genuinely necessary. No GUI qualification
is needed for a test-only reader/capture investigation; no release qualification
or publication is implied. Do not change public OpenAPI/capability/SDK behavior.

End with one evidence-backed outcome:

- **Proceed:** identify the proved representation, admission/input-validity
  predicate, limits and owned snapshot fields; prepare the smallest separately
  reviewed public-contract implementation proposal. Do not implement that API in
  this gate.
- **Blocked/degraded:** retain the counterexample and propose the smallest
  compatible adjustment, with a precise account of unproved costs or races.
  Keep the loaded-DEX facts operation usable and public CFG unchanged.

Original/raw reconstruction, JVM graphs, exhaustive exception flow, arbitrary
obfuscation, interprocedural graphs, Java mapping, persistent graph caches,
process isolation, remote binding and dependency upgrades are excluded.

## Findings and decision

**Do not proceed to a public graph contract on the current temporary boundary.**
It provides isolated edit/settings ownership, not immutable filesystem inputs.
The gate preserves executable counterexamples rather than changing production
loading, identities, admission, capabilities or persistence to hide them.

The new test-only [NormalizedCfgIsolationTest](../src/test/java/dev/libjadx/app/NormalizedCfgIsolationTest.java)
uses the package-local temporary callbacks and real pinned Jadx. The original
[CFG probe](../src/test/java/dev/libjadx/probes/JadxCfgProbeTest.java) now exposes
only its existing test copier/value types for reuse; its tests and semantics are
unchanged. No production graph stack, new runtime abstraction, DOT parsing,
primary graph processing, pass reset or kept-live primary graph was introduced.

### Proved subset and ownership

- Exact original `SymbolRef` selection, not alias lookup, with both
  `DexClassData` and `DexCodeReader` gating. AUTO and RESTRUCTURE capture directly
  after the actual `BlockFinisher` visitor. SIMPLE/FALLBACK, JVM and abstract
  no-code controls are UNAVAILABLE, not empty successful graphs. A generated
  one-instruction `ReturnOnly.done()V` is AVAILABLE with ENTRY→0→EXIT.
- Seven positive `CfgCases` methods in both admitted modes use the previously
  reasoned instruction-width oracle. Both IF operators/alternatives, two
  asymmetric arithmetic arms, join splitters, the backward 10→1 edge, packed
  4/5 and sparse -5/1000 keys, distinct default/case targets, array/switch payload
  exclusion and ordered typed/catchall structural links survive real capture.
  Full successors include the loop back edge; clean successors omit it.
- The loop's true exit is a **synthetic splitter before return 13**, not a
  direct original edge to 13. Two more synthetic nodes split its join arms.
  Handler splitters' ordered links are structural, never EXCEPTION_TRANSFER or
  proof of feasible exception dispatch. Existing full-topology assertions in
  `JadxCfgProbeTest` remain the companion oracle.
- Only immutable lists/records and strings, integers and booleans escape. A
  recursive allowlist rejects Jadx types; outer and nested mutation attempts
  fail. Copies remain equal after another operation, primary Java generation/
  unload, and engine/runtime close. The test copier's `start` and instruction
  offsets are **internal normalized hints**, including synthetic collisions;
  they are not a public original-offset field. A future DTO must give synthetic,
  entry and exit nodes no original position. cIds are snapshot-local only.
- Deterministic latches hold a loaded temporary engine while a real replacement
  edit changes the primary. Unsaved method rename and comment appear in temporary
  aliases **and generated Java**, with independently allocated code-data records.
  The old result retains its captured revision, RESTRUCTURE settings and old
  edits; a new AUTO operation sees the newer edits/revision. The graph topology
  itself is unchanged by the rename/comment. Existing `TemporaryAnalysisTest`
  additionally covers unsaved class rename/comment replay.
- Capture leaves primary class state, reader facts, native/input bytes, dirty
  state, revisions and search identity unchanged. Explicit source generation and
  explicit edit/save/reload actions in the tests are separate deliberate controls;
  they are not capture side effects. No autosave or persistence change occurs.

### Input/path and identity counterexamples

`pathSnapshotDoesNotFreezeInputOrMappingAndSaveAloneDoesNotRebindTemporaryId`
assembles two valid owned DEX files differing only in the loop's IF_LEZ versus
IF_GTZ. A factory hook runs **after** `finishSnapshotCapture` and **before**
temporary `load()`. It replaces the input and changes Tiny's class alias from
MappedBefore to MappedAfter. The callback observes MappedAfter, restores both
files, then captures the **GT** graph. The primary and next operation still
observe the accepted **LE** graph. Nevertheless the changed observation carries
the same captured revisions and temporary `sourceSnapshotId` as the old graph.
The test intentionally asserts this collision as negative evidence, not as a
valid graph identity contract. Restoration before processing also proves that
the plain-DEX reader/mappings were consumed at loading, not reread for this graph.

This is an ABA counterexample to simply hashing the filesystem before and after
the entire operation: both checks can see the accepted bytes while loading used
the intervening replacement. Deep-copying code data cannot address it.

`temporaryLoadDoesNotRevalidateAnInputReplacedByAnOutsideRootSymlink` replaces
the admitted input path with a symlink to an independently assembled GT fixture
outside the configured root just before load. Temporary capture succeeds with
GT and the old revision. The repository's **existing** `checkAnalysisBaselines`
guard rejects exactly the same path state with `SecurityException`. Restoring
the input allows a healthy LE operation; all instrumented engines close. Thus
the missing validity guard is in this temporary path, not an absence of a
repository path policy. This is local evidence, not a production security fix.

Explicit save without a logical edit changes persisted identity and publication
epoch but leaves the helper's temporary `sourceSnapshotId` unchanged: it hashes
only session, logical revision and effective configuration. A graph must not
reuse that helper ID as its content/publication identity. Explicit input/mapping
reload rebinds the revision and sees the new graph; accepted mapping-path
settings rebuilds and restart/session invalidation work. Effective mode is a
temporary override, not a newly added persistable setting.

### Admission, failures and cleanup

The real graph path rejects a second temporary operation with PROJECT_BUSY.
Existing primary-read/exclusive leases reject temporary admission while held;
the existing coordinator regression checks the brief snapshot-capture gate
itself. There is no new runtime hook inside that gate. After capture, primary
Java reads and replacement edits proceed. Native save, accepted mappings
settings rebuild and explicit reload also proceed while the temporary engine
retains its earlier loaded state. These operations are **not** globally blocked
by an isolated lease; the gate does not strengthen that existing policy.

All requested shutdown policies reject active temporary work. Direct runtime
close returns promptly during a latch-held real temporary load, stays
SHUTTING_DOWN until the lease really ends, and rejects new work. The admitted
operation can finish and both primary/temporary engines close. No hard
interruption or deadline enforcement is claimed.

Instrumented ownership counts wrap the actual production `JadxProjectEngine`,
including its input census, real `JadxDecompiler` load and verifier binding; they
do not replace those steps with a mock engine. Load-hook failure, graph-copy
resource refusal, later injected method and owner visitor failures, actual
invalid-register Java failure and success all release engines/leases. Subsequent
healthy operations succeed and created/closed counts balance after runtime
close. Actual `Disassembly.broken` fails CheckCode; its neighboring `answer`
still returns an AVAILABLE copied graph. A copy taken before an injected later
generation-pipeline error is withheld as FAILED. The probe checks retained
method/owner errors and the error counter: method attributes alone can disappear
on Java unload. Sibling errors do not automatically make a healthy selected
method's observation FAILED or turn it into complete owner analysis.

### Output limits are not processing limits

The selected method reuses the production reader's preflight: 4096 units, 1024
reader records/cases/handlers, 256 tries and bounded handler strings, including
its checked payload/handler decoding. Existing malformed and independent exact/
plus-one reader controls run unchanged. It is **not** a safety check for all
other methods subsequently processed by Jadx.

Before collecting selected graph values, the reused copier checks instruction,
block and full-successor counts and a conservative allocation estimate. The
runtime probe independently exercises inclusive **7 instructions / 12 blocks /
13 successors**, and refusal at 6/11/12 respectively. These are reduced-budget
boundary tests over the real loop graph; the candidate production ceilings
remain 1024/512/2048, not newly published guarantees. A separate streaming JSON
count measures actual encoded graph bytes without a response-sized allocation:
the loop is **1138 UTF-8 bytes**, succeeds at 1138 and refuses at 1137, atomically.
The old conservative estimate is 10112, not the encoded byte count. The byte
limit is separate from that allocation estimate; no truncation is returned.
The 2 MiB candidate remains a ceiling on the test graph encoding, not a proved
future response envelope, alias/comment budget or heap ceiling. No graph at
exactly 2 MiB was generated. Public-contract byte/envelope boundaries remain
future implementation work, not a reason to build an endpoint in this gate.

The generated cost fixture selects `Cost.tiny`: **2 code units / 2 instructions**.
Its owner sibling `zzLarge` and superclass dependency `Dependency.heavy` each
have **3602 units / 1802 instructions**. During a zero-block capture refusal the
observed post-Finisher order is:

```text
Dependency.heavy:1802   (before selected capture)
Cost.tiny:2            (capture refuses)
Cost.zzLarge:1802      (processing continues after refusal)
```

Both large methods exceed the selected graph instruction budget. Jadx has
already loaded the project, scanned dependencies and built their IR; a selected
method's preflight cannot prevent that work. `DepthTraversal` records a visitor
RuntimeException on the method and proceeds, so throwing RESOURCE_LIMIT there
is not a global abort. The cost fixture also produced a real **sibling codegen
StackOverflow** on this runner after capture; the selected graph remains usable,
which is not a promise that the rest of the owner generated successfully.
The final focused run separately measured **98 ms** for temporary engine loading
(including census/binding), **60 ms** for the selected preflight and owner/
dependency generation callback, and **160 ms** for admission through refusal/
cleanup. These single-run times are diagnostic, not deadlines or benchmarks.
There is no hard bound on temporary loading, owner/dependency processing, peak
JVM heap, decoder work on unselected methods or arbitrary hostile inputs.

### Smallest compatible follow-on proposal (not implemented)

1. **Keep the graph API deferred.** First review a temporary-input validity slice,
   preferably initially limited to small plain DEX inputs and no external mapping
   attachment. Revalidate allowed roots/canonical references and accepted content
   baselines, then prove an owned bounded in-memory input capture that is
   actually fed to the pinned loader. Compare the bytes consumed to accepted
   baselines; do not rely on two path hashes or silently retry changed files.
   Mapped projects must either gain equally owned mapping bytes/parsed state or
   be explicitly refused in that first subset. This needs a separately approved
   ownership/admission adjustment; no persistent copies, moved inputs, new
   project format or automatic saving are proposed. Pre/post baseline checks
   alone are useful best-effort refusal, **not** a solution to the demonstrated
   ABA race or a proof of frozen bytes.
2. **Treat scope as project/owner/dependency work, not method-only work.** Before
   graph passes, conservatively admit the entire small loaded-DEX project with
   explicit aggregate input-byte, class/method and reader-unit/record/handler
   ceilings, including dependencies and inner owners. Put byte admission before
   loading where possible. Prove enumeration and refusal against the cost
   fixture; do not claim these structural limits bound upstream heap or runtime.
   Hard untrusted-input memory/deadline isolation would need a separate design;
   process isolation is not approved or implemented here.
3. **Give future copied graphs their own binding.** Original method, named
   post-Finisher representation/pin, session, logical revision, captured
   publication epoch, persisted provenance, effective settings, consumed input/
   mapping fingerprints and immutable graph content must be explicit. Save-only
   publication and accepted reload/restart cannot be hidden behind the existing
   temporary source ID. Retain final method/owner error checks and separate
   UNAVAILABLE, FAILED and atomic resource refusal. Only after these proofs
   should a separate OpenAPI-first implementation be proposed.

This is a proposal for review, not permission to weaken immutable snapshot claims
to mutable path provenance. Loaded-DEX facts remain usable; public CFG stays
UNKNOWN/unimplemented. There is no pending change to native persistence.

## Executed validation and delivery

Baseline was fetched and fast-forwarded with `git merge --ff-only` to the exact
[pushed handoff](https://github.com/bostjanv/libjadx/commit/c085593ef1d3ab58e150db4f1559fc99344aeb2b),
verified with `git merge-base --is-ancestor`. Work stayed in the isolated
`normalized-cfg-isolation` worktree on `tartuf-vm`; the source checkout and older
investigation worktrees were not edited.

Pinned source came from the exact-revision codeload archive, SHA-256
`d856c3bfaa2d3739a853bc488bcf0d9fec5c1254dda60cc22f60138bfdd67f64`.
The repository reader tool could not authenticate; the verified archive was
read directly instead. Paths inspected under Jadx
`28ff15e4ae69950aebea110a13e5ab895d234dfc`:

- `jadx-core/src/main/java/jadx/api/JadxDecompiler.java`: `load()` consumes
  inputs, runs preparation and initializes passes; `close()` releases loaders.
- `jadx-core/src/main/java/jadx/core/ProcessClass.java`: dependency-first
  processing, owner-wide passes/codegen/unload; no method-only cost guarantee.
- `jadx-core/src/main/java/jadx/core/dex/visitors/DepthTraversal.java`: visitor
  errors become recorded attributes and do not abort the owner's pass loop.
- `jadx-core/src/main/java/jadx/core/Jadx.java`,
  `dex/visitors/blocks/BlockFinisher.java`, `BlockProcessor.java`, and
  `dex/nodes/MethodNode.java`, `BlockNode.java`: pass order, splitters, finishing,
  mutable graph lifetime and unload.
- `jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/`:
  `DexFileLoader.java` reads plain DEX into bytes, `DexReader.java` owns the buffer.
- `jadx-plugins/jadx-rename-mappings/src/main/java/jadx/plugins/mappings/`:
  `RenameMappingsPlugin.java`, `load/LoadMappingsPass.java` load mapping paths
  during preparation; they are not frozen by LibJadx's argument construction.

`isolation-pinned-source.sha256` records audited file digests. Logs, full copied
graph JSON in JUnit stdout, first failed attempts and final XML are retained
locally under excluded `.amp/in/artifacts/`; `isolation-observations.json` extracts
the fourteen AUTO/RESTRUCTURE graphs for review. Fixtures are reproduced by the
retained test using `HierarchyFixture.dex`, existing owned `CfgCases` and
`Disassembly`, plus generated return-only, IF-opposite and cost controls.

Commands executed on Linux x86_64, OpenJDK 21.0.12.1 / Gradle 8.14.3, locked
Jadx 1.5.6 (from this worktree root):

```bash
./gradlew test --tests dev.libjadx.app.NormalizedCfgIsolationTest
# Final focused run: 9/9, zero failures/errors/skips; isolation-focused-final.log/xml.

./gradlew test check installDist
# Broader run: 521 tests, zero failures/errors; 16 opt-in GUI skips (505 executed).
# isolation-full.log, isolation-full-xml/.
# This preceded the final test-only production-engine wrapping/timing and owner-error
# control. The final targeted and focused runs below/above include those additions.

./gradlew test --tests '*NormalizedCfgIsolationTest' --tests '*JadxCfgProbeTest' \
  --tests '*TemporaryAnalysisTest' --tests '*SettingsRebuildFailureTest' \
  --tests '*OperationCoordinatorTest' --tests '*DecompileEndpointsTest' \
  --tests '*ReferenceEndpointsTest' --tests '*ReferenceSnapshotTest' \
  --tests '*Instruction*' --tests '*Smali*' --tests '*ControlFlowEndpointsTest' \
  check installDist
# Final: 64/64, zero failures/errors/skips; isolation-regression-final.log,
# isolation-regression-final-xml/ (also an earlier 64/64 run).

python3 tests/validate-documentation.py
python3 tests/validate-documentation.py --self-test
git diff --check
# Documentation: 54 Markdown files, 505 relative links/anchors; self-test and
# whitespace checks passed; isolation-docs.log and isolation-docs-self-test.log.
```

Initial executing attempts had incorrect **test expectations**, not passed proof:
splitter edges were mistaken for the earlier splitter-stage direct edges, a
method error attribute was checked after unload instead of the retained error
counter, and an unsaved mapping-path setting was incorrectly expected to survive
explicit discard/reload. These were corrected against pinned source and the
independent fixture; failed logs/XML remain available. No failed attempt is
counted as successful validation.

No production Java, OpenAPI, SDK, pins, persistence or historical release-evidence
files change. Matching-GUI qualification, Python unit/live/installed-route tests
and complete release-candidate qualification were **not rerun** for this test-only
gate; previous 204 Python and 21 installed-route results are historical, not fresh
results of this work. No GUI or publication claim is made. Reviewable changes are
local; no push, merge, tag or deployment is implied.
