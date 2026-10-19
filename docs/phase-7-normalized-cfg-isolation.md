# Phase 7 — Isolated normalized CFG capture integration gate

**Status:** prepared feasibility work item, not an implemented feature or a
public-contract change. The [CFG feasibility investigation](phase-7-cfg-feasibility.md)
and [bounded loaded-DEX facts](phase-7-control-flow-facts.md) are complete locally.
The next gate is to prove a safe runtime ownership boundary before proposing a
graph endpoint. `analysis.cfg` remains UNKNOWN and `/analysis/cfg` unimplemented.

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
