# Phase 7 — Owned-input initialization and integration design

**Status:** original design proposal and executable initialization probes,
completed locally on 2026-10-19. The subsequent
[internal integration gate](phase-7-owned-input-integration.md) implements the
accepted-baseline, owned-census and initialization boundaries after explicit
[human approval](https://ampcode.com/threads/T-01a15645-d946-74f4-8fe1-51ce4c0d53dc).
The proposal, proof obligations and validation below retain their original scope
and delivery state; consult the integration report for current implementation.
`analysis.cfg` remains UNKNOWN and `/analysis/cfg` unimplemented.

## Decision proposed for review

Keep primary loading and the existing temporary Java override path unchanged.
Prepare a separate internal, read-only owned-DEX integration gate before any
public graph contract. Its first subset is small plain DEX 035–040, AUTO or
RESTRUCTURE, no attached mappings, conversion, archives, resources or custom
classpath. Preserve the previous gate's aggregate/graph ceilings and all its
residual filesystem, parser, heap and deadline limitations.

Three boundaries must be adopted together, not piecemeal:

1. Capture the repository's **already accepted**, ordered input fingerprints
   with the native edit/settings/revision/publication snapshot. Do not freshly
   hash changed files and call those hashes an accepted baseline.
2. Inventory and copy a census from the **same privately owned readers/bytes**
   supplied to the isolated engine. Do not independently reopen the paths.
3. Run the pinned native rename/name-validation pipeline at its normal prepare
   boundary, retain its update listener, and explicitly verify initialization
   before allowing an operation callback or result publication.

This proposal permits neither a new public endpoint nor a new claim of complete
hierarchy verification. There is no native persistence, dependency, scheduler
policy, public OpenAPI or Python SDK change in this work item.

## Pinned initialization evidence

Jadx **1.5.6**, source
`28ff15e4ae69950aebea110a13e5ab895d234dfc`; no upstream patch or version change.
The exact source archive from the previous gate was rechecked against SHA-256
`d856c3bfaa2d3739a853bc488bcf0d9fec5c1254dda60cc22f60138bfdd67f64`.
Audited source paths under `jadx-core/src/main/java/`:

- `jadx/api/JadxDecompiler.java`: `addCustomPass`, loading, prepare stage and
  after-load ordering. `JadxArgsValidator.java` accepts an inputless engine when
  a custom loader exists. It does not require a dummy file.
- [`jadx/core/dex/visitors/rename/RenameVisitor.java`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/visitors/rename/RenameVisitor.java):
  `init` returns for empty paths; private `process(RootNode)` calls the complete
  `UserRenames.apply` and `checkNames` pipeline. The normal listener invokes that
  same pipeline. File SHA-256 remains
  `e70c48badadf4e69febd49fe2b624b973ca2f5df330e4033c01200f823b1080f`.
- `jadx/core/Jadx.java`: prepare order is deobfuscation, source-file naming,
  RenameVisitor, SaveDeobfMapping, then usage and other preparation. Scoped
  parameter records are applied later by `CodeRenameVisitor`, not UserRenames.
- `jadx/api/plugins/pass/types/JadxPreparePass.java`,
  `jadx/api/plugins/pass/impl/OrderedJadxPassInfo.java` and
  `jadx/core/utils/PassMerge.java`: a public prepare-pass hook can run immediately
  after RenameVisitor and before SaveDeobfMapping without replacing other passes.
- `jadx/core/dex/nodes/RootNode.java` and
  `jadx/api/impl/passes/PreparePassWrapper.java`: custom prepare passes are skipped
  in SIMPLE/FALLBACK; prepare initialization exceptions can be logged/swallowed.
  A returning `load()` is therefore insufficient evidence of successful setup.
- `jadx/core/dex/visitors/rename/UserRenames.java` and `CodeRenameVisitor.java`:
  declaration and scoped listeners have separate roles; both must remain intact.
- `jadx/core/deobf/DeobfuscatorVisitor.java`, `DeobfPresets.java` and
  `SaveDeobfMapping.java`: generated deobfuscation mappings introduce additional
  path/read/write assumptions. Deobfuscation is off in the supported service
  argument builder; the proposal does not enable it or generated-mapping writes.
- `jadx/core/dex/nodes/utils/SelectFromDuplicates.java`: descriptive input labels
  influence duplicate selection. Preserving a declaration census is not proof
  that the selected engine definition is unchanged.

The loader ownership remains the previous gate's `DexLoadResult`/private-array
mechanism. Audited sources, upstream LICENSE and SHA-256 manifest are retained in
excluded `.amp/in/artifacts/owned-input-initialization/`.

### Executable candidate and comparison

[OwnedDexAdmissionTest](../src/test/java/dev/libjadx/app/OwnedDexAdmissionTest.java)
adds an opt-in **test-only** `NativeInitialization` prepare pass. It reflectively
invokes the entire pinned private `process(RootNode)` method and registers the
normal equivalent update listener. It never changes filesystem input arguments,
patches an alias, copies upstream rename logic or modifies a primary engine.
The original inputless-rename counterexample still runs without this opt-in.
The harness still uses test-supplied accepted hashes and bypasses production
census/bind; no production repository accessor is added by these probes.

Six additional test invocations establish:

- AUTO and RESTRUCTURE each match independent fresh path-based engines, both
  without edits and with unsaved class/method/field renames, narrow/wide parameter
  renames and class/method/field comments. The owned `Init.calculate(IJ)J` fixture
  uses its two arguments asymmetrically, so swapped parameter indexes differ.
  Both return-only `value` declarations retain their distinct original IDs.
  Their automatic collision alias is checked before editing and recomputed when
  the integer-return declaration receives an explicit rename.
- Java comparisons normalize **only** the exact Jadx `loaded from` header:
  the fresh engine reports the absolute input path and the experiment reports
  `owned-0.dex`. Alias records and the rest of the Java text must match. This is
  semantic parity, not original-input provenance or byte-identical source IDs.
- The real post-BlockFinisher CFG copier now sees the requested unsaved method
  alias and comment, with the same independently checked LE loop topology and
  graph-content digest as the unedited case.
- A latch-held owned snapshot retains old edits while a primary replacement
  publishes new edits; the next operation sees the new state. A diagnostic
  `reloadCodeData()` checks the declaration update listener without filesystem
  inputs. This diagnostic mutation is not part of a proposed read-only API.
- An injected prepare failure and a skipped prepare pass both refuse before the
  callback. Cleanup releases the temporary lease/engine and a subsequent healthy
  operation succeeds. The candidate checks completion and pass placement outside
  Jadx's swallowing boundary; a successful load cannot bypass those checks.
- The existing path-census `Capture.bind` returns UNSUPPORTED_INPUT for the
  inputless engine even when its raw declarations match. This is retained as a
  negative integration result, not bypassed by populating fake filesystem args.

These controls are evidence for this small subset, not comprehensive alias,
scoped-variable or arbitrary-input equivalence. Public local mutation remains
UNSUPPORTED. Parameter replay does not establish new native persistence support.

### Initialization tradeoff

Recommend one isolated, pin-specific reflective call to the existing complete
pipeline, guarded by supported mode, signature/access checks, pass placement,
single completion and post-load verification. Any missing/inaccessible method,
skipped pass, failed initialization or incompatible configuration must refuse;
never silently return an engine with partially initialized edits.

This adds a private-upstream-method dependency and requires explicit design
approval before production adoption. It is smaller and more faithful than
copying the rename/name-validation implementation. Calling only UserRenames
would omit collision/name validation and its listener. Running the pipeline
after all preparation would change ordering. Dummy filesystem paths or temporary
persisted DEX files would defeat input ownership. Subclassing RenameVisitor alone
cannot call its private process method. An upstream public inputless-init API is
the preferred future replacement, but changing the pin is a separate milestone.

## Accepted-baseline access contract — proposed, not implemented

[NativeProjectRepository](../src/main/java/dev/libjadx/project/NativeProjectRepository.java)
owns `inputBaselines`, already populated synchronously at open, independently of
asynchronous persisted identity. Ordinary edits/save do not accept new inputs;
`commitReload` publishes new accepted fingerprints only after validation.

Add one internal synchronized accessor returning an immutable ordered collection
of canonical path/fingerprint pairs plus immutable allowed roots. Retain repeated
input slots; do not reduce them to a map. Require present fingerprints. Capture
mapping presence from the same repository state and explicitly refuse attached
mappings, including an unsaved attachment, in the first owned subset. Keep this
internal to Java; do not extend ProjectSnapshot's public wire contract.

Call it under the existing temporary snapshot-capture lease, alongside the deep
native-code-data copy, effective settings, complete RevisionState and publication
epoch. No filesystem reading/hashing belongs in this accessor and no repository
or lifecycle monitor may remain held during acquisition or Jadx work. The existing
coordinator prevents conflicting state changes until `finishSnapshotCapture`.
After that point, primary work may proceed; the accepted baseline and edits
remain those captured for this operation, even across save/reload publication.

For this new owned path only, compare bounded captured bytes directly against
those accepted hashes instead of calling the unbounded synchronous
`checkAnalysisBaselines()` first. Keep the existing guard unchanged on current
path-based temporary Java requests. This avoids adding an unbounded preliminary
rehash to the new path; it does not bound startup hashing or primary loading.
Capture mismatch must produce the existing external-modification conflict;
path-policy refusal remains a security error. No implicit reload, retry or save.

Required implementation controls: raw/native projects, repeated and secondary
inputs, PENDING persisted identity, unsaved mapping attachment refusal, immutable
snapshot retention across edit/save/reload, changed/deleted inputs, and healthy
recovery. Verify that reading the accepted snapshot never accepts changed bytes.
These are future acceptance tests, not results of the initialization probes.

## Same-owned-data census contract — proposed, not implemented

[JadxInputCensusAdapter](../src/main/java/dev/libjadx/jadxadapter/JadxInputCensusAdapter.java)
currently wraps path acquisition, original-declaration copying and path-based
binding. Its private Builder's declaration-copying logic is the source of truth.
Reuse that logic for the owned reader path; do not create a second independent
census implementation or reuse post-collapse ClassNodes.

After bounded acquisition and aggregate raw inventory, but before `jadx.load()`:

1. Feed the same ordered private DEX readers into declaration copying, or collect
   declarations during the aggregate inventory traversal. Preserve original
   superclass/interfaces/access/method records and every raw duplicate/hidden
   definition. Enforce both structural budgets and census metadata/edge/duplicate
   ceilings before retaining a complete immutable InputCensus. Never truncate.
2. Bind each origin to its accepted canonical path and ordered slot. Keep the
   bytes/readers private until engine close. Use original canonical path spellings
   as descriptive loader labels, without reopening paths, to preserve direct-DEX
   duplicate-selection behavior. Labels still do not establish public provenance.
3. Supply those same readers through the custom loader with empty inputFiles.
   Verify consumed fingerprints and origins against the captured ordered set;
   missing/extra/reordered readers or partial class loading must refuse before
   allowing graph work. Exercise repeated paths and divergent duplicate classes,
   not only a unique healthy class.

The shared collector should stay inside the adapter boundary; path-based primary
capture/bind retains its existing behavior and regression tests. The owned path
must not call the old `Capture.bind`: it intentionally requires filesystem
arguments and repeated path revalidation. Do not weaken that primary invariant.
For the first read-only temporary engine, retain unsupported mutating hierarchy
verification rather than introducing an unnecessary new verifier-binding API.
A complete owned census supports admission, not permission for propagated edits.
Any later owned hierarchy binding requires separate same-engine classpath and
independent completeness proof before it can authorize mutations.

## Lifecycle, remaining blockers and next work

Keep one temporary lease, the brief metadata-capture gate, serialized primary
reads, permitted post-capture primary work and existing shutdown behavior.
Acquisition, census, initialization, graph/copy and later generation failures must
all close the temporary engine and release the owned arrays/readers exactly once
logically, despite Jadx's duplicate close callbacks. Results contain only immutable
copies and retain full revision, publication, settings, consumed-content and graph
bindings from the previous gate; the temporary source ID is not a graph ID.

The prior SecureDirectoryStream experiment still leaves ancestor relocation and
regular-to-special-file replacement unproved; special-file open can block. This
proposal does not solve those races. Aggregate structure is not a heap/time or
SSA-expansion bound. No arbitrary hostile-filesystem/DEX safety, worker isolation,
hard interruption, mapping ownership or cross-platform containment is claimed.

**Next work item, after review:** implement and validate the internal accepted-
baseline/owned-census integration above with the pin-specific initialization
adapter. Keep it off public routes until its real acquisition, declaration/load
binding, failure, concurrency and security obligations pass. Review unresolved
filesystem containment before exposing a production graph path. Only then prepare
a separately reviewed OpenAPI-first normalized CFG slice. No architectural
invariant is relaxed by the passing initialization comparison.

## Executed validation and delivery

Linux x86_64, OpenJDK 21.0.12.1, Gradle 8.14.3, locked Jadx 1.5.6. Logs/XML and
source audit are under excluded `.amp/in/artifacts/owned-input-initialization/`.

```bash
./gradlew test --tests '*OwnedDexAdmissionTest'
# focused-2.log / focused-2-xml: 15 passed, zero failures/errors/skips.
# Nine retained probes plus six additional invocations.

./gradlew test --tests '*OwnedDexAdmissionTest' \
  --tests '*NormalizedCfgIsolationTest' --tests '*JadxCfgProbeTest' \
  --tests '*InputCensusTest' --tests '*TemporaryAnalysisTest' \
  --tests '*OperationCoordinatorTest' --tests '*SettingsRebuildFailureTest' \
  --tests '*DecompileEndpointsTest' --tests '*ControlFlowEndpointsTest' \
  --tests '*Instruction*' --tests '*Smali*'
# regressions.log / regressions-xml: 96 passed, zero failures/errors/skips.

./gradlew clean test check installDist --no-build-cache
# java-full.log / java-full-xml: 544 total, 528 passed, 16 existing opt-in
# GUI skips, zero failures/errors. Build/check/installDist succeeded.

python/.venv/bin/python tests/validate-openapi.py
# openapi.log: valid 3.1 contract, 23 operations, 42 JSON examples, 3 SSE frames.
python/.venv/bin/python tests/validate-documentation.py
# documentation.log: 57 Markdown files, 539 valid relative links/anchors.
python/.venv/bin/python tests/validate-documentation.py --self-test
# documentation-self-test.log: passed positive and negative controls.
git diff --check
# Passed; the new untracked document was separately checked for whitespace/EOF.
```

Initial failed attempts are retained, not counted as successful validation:
`first.log` records a wrong custom-pass method name and a Java package-name
shadowing compile error. `focused-1.log`/XML compares unnormalized loaded-from
headers and correctly fails on their different labels. The final comparator
normalizes only that exact header, as described above; declarations, parameter
positions, comments and collision aliases are not normalized away.

No production Java, OpenAPI, generated SDK, native codec or lock files change.
The full Java suite exercises existing explicit-save/reload/no-autosave and
concurrency/conflict behavior. Native persistence implementation is untouched;
no matching-GUI, Python unit/live/installed-wheel or fresh release qualification
was run for this test/documentation-only work. The 16 GUI skips are not passes.
Historical release evidence is unchanged and does not qualify this source;
ordinary documentation also affects its strict fingerprint. Changes are local
and uncommitted; no push, merge, tag, deployment or publication is implied.
