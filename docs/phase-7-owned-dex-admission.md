# Phase 7 — Owned DEX input and aggregate-admission feasibility gate

**Status:** test-only gate implemented and validated locally on 2026-10-19;
**BLOCKED/DEGRADED** for production adoption. Owned bytes and aggregate structural
admission work on the tested subset, but inputless Jadx loading skips declaration
rename initialization. The executable counterexample is retained below.
This follows
[normalized CFG isolation](phase-7-normalized-cfg-isolation.md) and the implemented
[temporary baseline guard](temporary-analysis-baselines.md). It does not approve
a production input-ownership change or a public graph contract. `analysis.cfg`
remains UNKNOWN and `/analysis/cfg` unimplemented.

## Goal and bounded scope

Determine whether a small plain-DEX project can supply **owned, bounded input
bytes** to an isolated pinned-Jadx engine, with aggregate admission before graph
passes and observations bound to the bytes actually consumed. A path snapshot,
two matching filesystem hashes or a selected-method budget is not that proof.

Start with one or more plain DEX files, no attached external mappings, no input
conversion, archives, directories or custom external classpaths. Refuse those
cases explicitly in the experimental path; do not drop them or silently use
filesystem loading. Retain current logical settings and deep-copied supported
unsaved native renames/comments. Graph controls remain one original METHOD,
loaded DEX, AUTO/RESTRUCTURE, immediately after BlockFinisher. Existing service
behavior for other inputs/mappings must remain unchanged.

Keep Jadx **1.5.6**, source `28ff15e4ae69950aebea110a13e5ab895d234dfc`, JDK 21
and Gradle 8.14.3. Read AGENTS.md, DESIGN.md and IMPLEMENTATION.md first. Inspect
the exact pinned loader/plugin and pass APIs before choosing a feeding mechanism;
do not assume byte-array reader access also initializes a complete decompiler.

## Evidence and seams to reuse

- [ProjectRuntime](../src/main/java/dev/libjadx/app/ProjectRuntime.java):
  `withTemporarySourceRead` validates accepted baselines, captures native state
  and publication epoch, then releases the snapshot gate before engine load.
  `EngineFactory`/`ProjectEngine` are package-local test seams. `JadxProjectEngine`
  runs path-based census, load and bind; an incomplete census degrades hierarchy
  verification, **not** engine admission. Do not mistake it for a hard load gate.
- [JadxEngineFactory](../src/main/java/dev/libjadx/jadxadapter/JadxEngineFactory.java)
  copies code data but passes input/mapping paths. The repository's
  [baseline guard](../src/main/java/dev/libjadx/project/NativeProjectRepository.java)
  owns accepted fingerprints; aggregate persisted identity can be pending and
  is not a substitute for those accepted input baselines.
- [JadxInputCensusAdapter](../src/main/java/dev/libjadx/jadxadapter/JadxInputCensusAdapter.java)
  already uses `DexFileLoader.loadDexReaders(label, content)` for raw declarations.
  Inspect its strict-reader and duplicate accounting, plus
  [InputCensusTest](../src/test/java/dev/libjadx/jadxadapter/InputCensusTest.java)
  and [FileFingerprint](../src/main/java/dev/libjadx/project/FileFingerprint.java).
  Their bounded parsing/hashing does not prove ownership through full Jadx load.
- [NormalizedCfgIsolationTest](../src/test/java/dev/libjadx/app/NormalizedCfgIsolationTest.java)
  retains the DEX/mapping ABA and outside-root symlink counterexamples, save-only
  identity collision, real engine/latch instrumentation and tiny-method cost
  control. The latter processes 1802-instruction dependency and sibling methods
  despite a two-instruction selection; visitor refusal does not stop the owner.
- Reuse [JadxCfgProbeTest](../src/test/java/dev/libjadx/probes/JadxCfgProbeTest.java),
  [CfgCases.smali](../tests/fixtures/cfg/CfgCases.smali),
  [Disassembly.smali](../tests/fixtures/smali/Disassembly.smali),
  [HierarchyFixture](../src/test/java/dev/libjadx/probes/HierarchyFixture.java)
  and existing temporary/coordinator tests. Do not build a second graph framework.

Audit pinned `JadxDecompiler` input registration/load/close, DEX plugin load-result
ownership, `DexFileLoader`/`DexReader`, `ProcessClass`, `DepthTraversal` and pass
ordering. Record exact source paths/digests and the mechanism's effects on normal
plugin initialization, resources, input census and hierarchy-verifier binding.

## Proof obligations

1. **Capture and feed the same bytes.** Revalidate allowed canonical references,
   acquire bytes with per-file and aggregate growth-safe ceilings, and compare
   the captured content to the repository's accepted input baselines. A prototype
   may use test-supplied accepted fingerprints; state that limitation instead of
   claiming production repository integration. Account for all ordered inputs,
   including repeated paths and duplicate definitions. Keep buffers private and
   alive for the loader's actual lifetime; copied observations must outlive close.
   Feed these exact bytes into a real isolated decompiler, with no later input
   path fallback or independently reread census. Prove that normal preparation
   and post-BlockFinisher graph capture still work. Labels remain descriptive
   paths, not proof of original-input provenance. If initialization or verifier
   compatibility needs a production change, document the smallest proposal.
2. **Races and path boundaries.** Use deterministic gates before acquisition,
   during bounded reading and between owned capture and load. Cover replacement,
   deletion, growth, outside-root symlinks and replace/restore ABA. Changed content
   before capture must refuse against the accepted baseline; after capture,
   loading must consume only the owned accepted bytes or refuse, never return
   replacement semantics under the old binding. Include the existing opposite-IF
   oracle and a changed secondary input. Check whether canonical validation and
   open are securely bound; consuming an outside-root target before rejecting
   its hash is not proof of path security. Document any unresolved race rather
   than claiming two path checks solve it. Keep current production counterexample
   tests unchanged as controls; a prototype is not a production race fix.
3. **Admit aggregate work, not just output.** Declare finite experimental ceilings
   for input count, per-file/total bytes, raw classes/methods and total reader code
   units/records/tries/handlers/payload cases, plus retained text. Count all raw
   definitions, hidden/synthetic methods, siblings, inner owners and configured
   dependencies before graph passes, including duplicates Jadx may discard.
   Reject unknown input/code-reader kinds. Keep the existing selected reader and
   copied graph ceilings separately. Inventory what happens before each ceiling
   can be checked: byte capture, parsing, preparation and dependency processing.
   Check counts before decoding/allocation where possible; unchecked upstream
   decoding is not a hostile-input safety boundary. Use overflow-safe totals,
   independent exact/plus-one fixture counts and atomic refusal, not truncation.
   Reuse the cost fixture to prove that aggregate refusal prevents its graph
   passes; a visitor exception after processing begins is too late. State all
   unbounded expansion, parser work, heap and runtime costs explicitly.
4. **Snapshot ownership and binding.** Prove unsaved renames/comments and effective
   settings reach the real owned-byte engine without sharing primary code data.
   Hold capture while the primary changes; the old observation must retain its
   captured state and a later operation see the new state. Record an internal
   observation binding with original method, named representation/pin, session,
   logical revision, publication epoch, persisted identity/state, settings and
   ordered consumed-content fingerprints. Distinguish content identity from
   publication identity: save-only publication may preserve graph content while
   changing provenance. Cover explicit reload and restart. Do not reuse the
   temporary `sourceSnapshotId` as a graph identity or invent public identity
   fields. Any content digest must derive from immutable copied values, not live
   nodes, paths or aliases.
5. **Admission, failure and cleanup.** Exercise the experimental path through real
   temporary ownership where feasible; explain any test-only bypass. Preserve
   the brief snapshot gate, one-temporary limit, primary serialization, allowed
   post-capture primary work and shutdown policies. Prove ownership release for
   capture mismatch, unsupported input/mappings, aggregate refusal, load failure,
   actual malformed-method processing failure and success. Check subsequent
   healthy work, balanced resources and unchanged primary revisions, dirty state,
   native/input bytes and search state. No autosave, automatic reload/retry or
   hard cancellation claim. Retain final method/owner error checks; a pass visit
   alone is not a successful graph.

## Deliverables and acceptance decision

Use test-only loader/capture instrumentation and focused real-Jadx probes; extend
existing fixtures/tests where that keeps their roles clear. No production loader,
repository, scheduler, public DTO/OpenAPI/SDK or persistence changes in this gate.
If the current seams are insufficient, preserve the failing probe and specify
the required change instead of introducing a production abstraction to pass it.

Update this work item with audited pinned-source paths, mechanism and ownership
lifetime, experimental ceilings and their refusal stages, independent fixture
expectations, exact commands/results, skips and remaining races/costs. Store
logs/XML/bounded observations in excluded `.amp/in/artifacts/`; keep scratch work
elsewhere under `.amp/in/`. Preserve historical qualification evidence.

End with one decision:

- **Proceed to design review:** the prototype consumes only admitted owned bytes,
  aggregate refusal precedes graph passes, binding and cleanup are proved, and
  remaining limits are explicit. Propose the smallest production ownership and
  admission adjustment for human review, including whether baseline access,
  plugin/census integration or capture-gate policy must change. This is not yet
  permission to implement that adjustment or an endpoint.
- **Blocked/degraded:** retain the executable counterexample and name the failed
  obligation and smallest compatible alternative. Keep loaded-DEX facts usable
  and public CFG UNKNOWN/unimplemented. Do not expand to persistent copies,
  workers, mapping ownership or a weakened snapshot guarantee without review.

Only after an approved ownership/admission design and its implementation are
validated should a separately reviewed OpenAPI-first normalized CFG slice begin.
Raw/original graphs, exhaustive exception dispatch, Java correspondence, JVM
graphs, arbitrary obfuscation, persistent graph caches, process isolation,
external publication and dependency upgrades are excluded here.

## Implemented mechanism and pinned-source audit

[OwnedDexAdmissionTest](../src/test/java/dev/libjadx/app/OwnedDexAdmissionTest.java)
contains the entire experimental acquisition, inventory and engine substitution.
No production Java, native codec, dependency/lock, OpenAPI or SDK changes.
The test-only factory distinguishes temporary calls from primary initialization
and replacement engines; the latter retain production loading. Temporary calls
use the real coordinator lease and native-state capture, then acquire bytes
after the existing short snapshot gate has been released.

The exact upstream archive was fetched from
<https://codeload.github.com/skylot/jadx/tar.gz/28ff15e4ae69950aebea110a13e5ab895d234dfc>.
Its SHA-256 is
`d856c3bfaa2d3739a853bc488bcf0d9fec5c1254dda60cc22f60138bfdd67f64`.
Local `pinned-source.sha256` and `pinned-audited-sources.tar.gz` retain these
audited paths and upstream LICENSE in the evidence directory:

- [`JadxDecompiler.java`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/JadxDecompiler.java),
  `load`, `loadInputFiles`, `addCustomCodeLoader`, `close`: registering a custom
  `ICodeLoader` adds to normal input-plugin loading. Clearing filesystem
  `inputFiles` is necessary to avoid independently rereading original paths.
  Normal plugin/root/resource/classpath/pass initialization still runs, but no
  input resources are supplied. Custom loaders can be closed from both
  `loadedInputs` and `customCodeLoaders`; the experimental release is idempotent.
- Under `jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/`,
  `DexInputOptions.java`, `DexFileLoader.java`, `DexReader.java` and
  `DexLoadResult.java`: `loadDexReaders(label, content)` plus `DexLoadResult`
  feeds owned buffers through real loading. `DexReader` wraps, rather than
  copies, the byte array. Arrays/readers stay private until engine close, after
  `JadxDecompiler.close()`. The test initializes options with `setOptions(Map.of())`
  and verifies checksum checking is enabled; a bare uninitialized options object
  would not apply the declared default.
- In that same DEX source tree, `sections/DexClassData.java`,
  `sections/DexCodeReader.java`, `insns/DexInsnFormat.java` and
  `utils/DexCheckSum.java`: raw declaration inventory precedes `jadx.load()`.
  Header counts and code extents are checked before traversal; payload lengths
  are checked before pinned `skip`, without upstream payload decoding. Bounded
  direct LEB parsing counts all encoded handlers and limits referenced-handler
  expansion before upstream try decoding. This is not complete DEX validation.
- `jadx-core/src/main/java/jadx/api/plugins/options/impl/BasePluginOptionsBuilder.java`
  and `jadx-plugins/jadx-input-api/src/main/java/jadx/api/plugins/input/ICodeLoader.java`
  establish option initialization and the actual loader interface in this pin.
- `jadx-core/src/main/java/jadx/core/ProcessClass.java`,
  `dex/nodes/RootNode.java` and `dex/visitors/DepthTraversal.java`: owner/dependency
  processing and visitor error recording explain why selected-method output
  limits are not aggregate admission. The existing graph copier and final
  method/owner error checks are reused unchanged.
- [`RenameVisitor.java`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/visitors/rename/RenameVisitor.java),
  `init`, lines 34–45: empty input paths return before `UserRenames.apply`, name
  checking and listener registration. File SHA-256:
  `e70c48badadf4e69febd49fe2b624b973ca2f5df330e4033c01200f823b1080f`.

The prototype deliberately bypasses the production path-based census/bind; it
does **not** establish hierarchy-verifier completeness or propagated-edit
admission. Production integration would have to derive census and engine input
from the same owned data. No dummy path, manual alias patch, temporary persisted
input or special rename replay is added to disguise the initialization gap.

## Experimental ceilings and refusal stages

| Scope | Inclusive ceiling | Checked before |
|---|---:|---|
| Ordered input references, including repeats | 4 | Opening inputs |
| Per-file captured bytes | 2 MiB | Initial size and every 256-byte chunk retained |
| Aggregate captured bytes | 4 MiB | Every chunk retained |
| Raw class definitions | 128 | Visiting class declarations |
| Raw fields / methods | 2048 each | Visiting their declarations |
| Aggregate code units / raw instruction records | 65536 / 32768 | Engine load/preparation |
| Per-method code units | 4096 | Instruction traversal |
| Try entries | 1024 | Handler parsing |
| Encoded handler entries / referenced-handler expansion | 4096 each | Parsing individual entries / engine load |
| Switch payload cases | 4096 | Payload skip/decode |
| Retained declaration text | 262144 Java UTF-16 units | Engine load, after each string is read |

The existing selected reader preflight and copied graph ceilings remain
independent: default graph limits are 1024 instructions, 512 blocks, 2048 edges
and 2 MiB encoded output. Admission refuses the entire operation; it never
silently truncates. Subtraction-based aggregate checks avoid addition overflow.
Archives, JVM headers, DEX 041 containers, non-regular inputs and mappings have
no filesystem fallback. The production argument builder exposes no external
classpath configuration; custom classpaths are not tested or admitted here.

Independent `CfgCases.smali` expectations are one class, eight methods, 103 code
units, 55 records (including payloads/alignment), one try, two handlers and four
switch cases. Declaration text is counted separately from the fixture's literal
names/types. Each nonzero inventory dimension accepts its exact fixture total
and refuses when its configured maximum is one smaller. The repeated duplicate
fixture additionally counts three raw classes, fields and synthetic methods,
including the repeated path, before Jadx can merge definitions.

The cost control has two classes and three methods: the tiny selection contributes
two units/two records, while its sibling and dependency each contribute 3602
units/1802 records. Total 7206 units/3606 records accepts; a 7205-unit maximum
refuses before `jadx.load()`, so no graph visitor can already have processed the
expensive methods. The previous isolation gate's unbounded control is unchanged.

## Observed results and adoption decision

Nine real-Jadx test cases cover these outcomes:

| Obligation | Observation |
|---|---|
| Consumed bytes | Replacing the captured first input with an outside-root opposite-IF symlink and deleting the second input still produces the accepted LE graph and both accepted hashes. Copies remain readable after engine close. |
| Acquisition races | Deterministic replacement, deletion, final/parent symlinks, growth and in-place mutation refuse. Symlink/deletion cases read zero input chunks. Changed first and secondary inputs refuse against test-supplied accepted baselines before load. |
| Aggregate refusal | Exact/over-budget fixture controls, duplicate/hidden declarations and the sibling/dependency control refuse atomically before engine preparation. |
| Malformed/unsupported controls | Archive/JVM/container headers, checksum corruption, an oversized array payload and a declared 63-handler table refuse during acquisition/inventory, without upstream payload allocation. |
| Snapshot semantics | Code-data and rename records are deep copies; old/new comments and AUTO/RESTRUCTURE settings retain their captured state during primary edits. **Declaration aliases do not replay.** |
| Binding | Internal immutable observations include original method, named representation/pin, complete revisions including persisted identity/state, session, publication epoch, settings, ordered consumed hashes and copied-graph digest. Save-only publication changes provenance without changing graph content; reload changes consumed bytes, restart changes session. |
| Admission/cleanup | Real temporary admission refuses a second temporary and all requested-shutdown policies while allowing post-capture primary work. Capture/aggregate/mapping refusals, injected pre-load and in-load failures, malformed-register method failure and success release the engine lease; healthy subsequent work succeeds. Ordinary runtime close waits for owned loading to finish. |

**Decision: BLOCKED/DEGRADED.**
`inputlessLoadingSkipsDeclarationRenamesDespiteCopiedUnsavedDataAndComments`
retains the executable negative result: `oldLoop` and then `newLoop` are present
in captured native rename records, but owned-engine generated Java and method
aliases remain `branchLoop`. The path-based primary emits `oldLoop`/`newLoop`
correctly. This is not merely a missing argument copy: the pinned source's
`RenameVisitor.init` early return is reached by the empty-path feeding mechanism.
The passing counterexample test asserts the failure; it does **not** mean the
unsaved-rename requirement passed. Public CFG stays UNKNOWN/unimplemented.

Smallest compatible follow-on for human design review: make inputless owned
loading run the pinned native rename/name-validation initialization with its
normal ordering/listeners, without reintroducing filesystem input. Compare it
against fresh path-based engines for declaration/scoped renames, automatic aliases,
comments and supported settings before considering adoption. Separately review
repository access to immutable accepted fingerprints and same-owned-data census
binding. Do not weaken snapshot semantics, manually patch individual aliases,
change the upstream version, or implement a public CFG while these are unproved.

## Residual limits, security and persistence

- This is a Linux `SecureDirectoryStream` experiment, not a portable filesystem
  security implementation. Every component is opened handle-relative with
  NOFOLLOW_LINKS, preventing the tested symlink races from reading an outside
  target. Concurrent ancestor relocation and regular-file-to-special-file
  replacement between the attribute check and open remain unproved; the latter
  can block on open. No claim of complete hostile-filesystem containment.
- Accepted fingerprints are supplied by the test, not exposed from the production
  repository. Its existing pre-capture hash guard and the initial primary load
  still run outside these experimental byte budgets. This gate bounds neither
  total request admission time nor primary initialization.
- Chunk reads can transiently obtain one fixed chunk beyond the accepted ceiling
  before refusal. ByteArrayOutputStream plus its final copy duplicates bounded
  capture storage; no hard heap bound is claimed. Hashing/comparison binds the
  completed array, not an atomic on-disk snapshot during in-place writes.
- Jadx parses header/declaration strings, interfaces, annotation metadata and
  other tables before some inventory checks can fire. The text ceiling covers
  copied declaration strings, not debug/source strings, comments, annotations,
  parser allocations or all later Java output. This is not a hostile-DEX verifier.
- Raw structural totals do not bound graph/SSA expansion, type inference, resource
  initialization, dependency processing time, stack/heap consumption or JVM-level
  interruption. Cancellation remains cooperative; there is no worker-process
  deadline, persistent input copy or arbitrary-input safety guarantee.
- The prototype supplies no archive resources, mapping ownership or external
  classpaths. Labels such as `owned-0.dex` are descriptive only, not source
  provenance or unique original definitions. Binding is internal evidence,
  not a proposed public graph identity.
- Explicit-save/publication/reload/restart controls were exercised. Temporary
  operations do not autosave and leave primary snapshots, dirty state, native
  and input bytes, search identity and primary owner state unchanged in the
  relevant controls. Native persistence implementation is untouched; no new
  matching-GUI or installed-SDK qualification is claimed.

## Executed validation

Linux x86_64, OpenJDK 21.0.12.1, Gradle 8.14.3 and locked Jadx 1.5.6. Logs,
JUnit XML, negative observations and audited upstream source are retained under
excluded `.amp/in/artifacts/owned-dex-admission/`.

```bash
./gradlew test --tests dev.libjadx.app.OwnedDexAdmissionTest
# focused-final.log / focused-final-xml: 9 passed, zero skips/failures/errors.

./gradlew test --tests '*OwnedDexAdmissionTest' \
  --tests '*NormalizedCfgIsolationTest' --tests '*JadxCfgProbeTest' \
  --tests '*InputCensusTest' --tests '*TemporaryAnalysisTest' \
  --tests '*OperationCoordinatorTest' --tests '*SettingsRebuildFailureTest' \
  --tests '*DecompileEndpointsTest' --tests '*ControlFlowEndpointsTest' \
  --tests '*Instruction*' --tests '*Smali*'
# regressions.log / regressions-xml: 90 passed, zero skips/failures/errors.

./gradlew test check installDist
# java-full.log / java-full-xml: 538 total, 522 passed, 16 opt-in GUI skips,
# zero failures/errors; check and installDist successful.

python/.venv/bin/python tests/validate-documentation.py
# 56 Markdown files, 529 valid relative links/anchors; current language clean.
python/.venv/bin/python tests/validate-documentation.py --self-test
# Passed, including negative-link/language/license/qualification controls.
git diff --check
# Passed; untracked test/document additionally checked for trailing whitespace
# and final newlines.
```

Initial attempts are retained, not counted as successful validation: one checked
exception compilation error; then an overly broad test factory mistakenly
substituted primary replacements. The corrected temporary-only factory exposed
the real rename failure. `replay-debug.log`/XML preserve generated Java from that
failure; the final counterexample checks it explicitly rather than hiding it.

The 16 skipped cases are the existing opt-in matching-GUI diagnostics and
round-trips; they are not counted as passes. No Python/installed-wheel or fresh
GUI suite was rerun for this test/documentation-only gate. Loaded-DEX controls,
temporary operation/concurrency checks, malformed inputs, stale revisions,
external-change/restart and headless persistence regressions ran in the Java
suite. Downloaded source scratch was removed after retaining the original
archive, audited files, hashes and upstream license in the evidence directory.

The gate does not renew historical release evidence; documentation also enters
its strict fingerprint. At completion of the gate validation above, changes
were local/uncommitted. Subsequent Git delivery is separate from release
qualification, tagging, deployment or external package publication.
