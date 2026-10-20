# Phase 7 — Internal accepted-input and owned-DEX integration

**Status:** implemented internally after explicit human approval of the
[initialization design](phase-7-owned-input-initialization.md) and its pin-specific
private-method dependency in the
[implementation thread](https://ampcode.com/threads/T-01a15645-d946-74f4-8fe1-51ce4c0d53dc).
This is not an HTTP CFG implementation. `analysis.cfg` remains UNKNOWN and
`/analysis/cfg` unimplemented. Primary loading, existing temporary Java overrides,
native persistence, OpenAPI, Python SDK and dependency pins are unchanged.

## Implemented boundaries

- [NativeProjectRepository](../src/main/java/dev/libjadx/project/NativeProjectRepository.java)
  exposes immutable internal `AcceptedInputs`: ordered canonical path/fingerprint
  pairs, immutable allowed roots and current mapping presence. Repeated paths
  retain their separate slots. This accessor performs no filesystem reads or
  hashing, including when persisted identity is PENDING. It never accepts new
  input content. Only explicit validated reload publishes new accepted baselines.
- [ProjectRuntime.withOwnedDexRead](../src/main/java/dev/libjadx/app/ProjectRuntime.java)
  captures that metadata alongside deep-copied native edits, effective settings,
  complete revisions and publication epoch under the existing short temporary
  capture lease. Acquisition/load occur after `finishSnapshotCapture`, outside
  repository/lifecycle monitors. Primary work may then proceed; one temporary
  operation and existing shutdown semantics remain enforced. This package-local
  entry point has no public-route caller.
- [OwnedDexAnalysis](../src/main/java/dev/libjadx/jadxadapter/OwnedDexAnalysis.java)
  acquires bounded bytes, compares their content against the accepted fingerprints,
  inventories all raw code/declarations and feeds private readers to both census
  and real isolated loading. Filesystem input arguments stay empty, so there is
  no secondary path-based load. Changed/deleted bytes produce the existing
  external-modification exception; path-policy refusal remains a security error.
  The path-based `checkAnalysisBaselines` guard is not called on this new branch,
  and remains unchanged on existing Java override requests.
- [JadxInputCensusAdapter.captureOwned](../src/main/java/dev/libjadx/jadxadapter/JadxInputCensusAdapter.java)
  reuses its existing declaration collector, never post-collapse ClassNodes or
  independently reopened paths. It preserves original superclass/interfaces,
  access/method records, hidden declarations and raw duplicates. Reader/origin
  ordering, labels, sizes, traversal completeness and all census ceilings must
  pass before engine creation. Failure returns no partial census. The owned
  capture has no path fingerprint map and cannot certify mutating hierarchy work.
  The original path-based `Capture.bind` contract is unchanged.

Loader labels use accepted canonical path spellings to preserve pinned direct-DEX
duplicate selection. They remain descriptive, not public original-input identity.
After loading, the adapter verifies complete ordered reader consumption, raw class
counts, unchanged private content hashes, no retained load errors, all unique raw
class descriptors and the selected definition's original method set/input label.
Only then can callers borrow the engine. Initialization/load failure never makes
an engine available through the accessor.

The native-initialization pass invokes the whole pinned private
`RenameVisitor.process(RootNode)` pipeline immediately after RenameVisitor and
before SaveDeobfMapping, retaining its equivalent code-data update listener.
It checks the exact runtime version, method signature/access, one completion and
actual pass placement outside Jadx's exception-swallowing boundary. No individual
alias patches, copied rename algorithm, dummy paths or temporary persisted inputs
are used. Upstream API changes must fail closed, not silently omit edits.

## Scope, limits and lifetime

The first subset is plain DEX 035/037/038/039/040 in AUTO or RESTRUCTURE. Attached
mappings, including unsaved attachments, conversion, containers, archives, JVM
inputs, resources and custom classpaths are not admitted. No filesystem fallback
or implicit reload/save/retry is implemented.

The [preceding admission gate](phase-7-owned-dex-admission.md#experimental-ceilings-and-refusal-stages)
supplies the structural ceilings: four ordered input slots, 2 MiB per file,
4 MiB aggregate bytes, 128 raw classes, 2048 fields/methods each, 65536 code units,
32768 instruction records, 4096 units per method, 1024 tries, 4096 encoded and
referenced handlers, 4096 switch cases and 262144 declaration-text UTF-16 units.
All definitions count, even repeats/duplicates later discarded by Jadx. Shared
census admission additionally limits edges to 512, definitions per descriptor to
four, methods per class to 2048 and copied metadata including path labels to
262144 characters. Refusal is atomic, never truncation; all admission precedes
engine preparation and graph processing. Selected-method/output graph ceilings
remain the caller's separate responsibility.

The owned object closes its decompiler before releasing private readers/arrays.
Close is idempotent. Jadx's duplicate loader-close callbacks have no resource
release action; the enclosing owner alone releases buffers. Runtime try-with-
resources releases that owner and the temporary lease on acquisition, census,
initialization, loading, callback/copy/generation failures and success.

The callback must return only immutable copied values and must not save, reload,
reconfigure or retain the borrowed decompiler. Its metadata contains full revision
and publication identity, settings and ordered consumed fingerprints. Original
method, representation/pin and a digest of copied graph values are bound separately
by the test observer; a Java temporary source ID is not reused as a graph ID.
This is an internal borrowing contract, not a security sandbox for Java callers.

## Pinned source evidence and executable controls

Jadx 1.5.6, source `28ff15e4ae69950aebea110a13e5ab895d234dfc`, JDK 21,
Gradle 8.14.3. Reused the previous gate's exact upstream source archive and
rechecked SHA-256
`d856c3bfaa2d3739a853bc488bcf0d9fec5c1254dda60cc22f60138bfdd67f64`.
The source audit covers `JadxDecompiler` registration/load/close,
`JadxArgsValidator`, `RootNode` class-load error swallowing, `ClassNode` and
`MethodNode` construction, `Jadx` prepare ordering, `PreparePassWrapper`,
`RenameVisitor`/`UserRenames`/`CodeRenameVisitor`, `SelectFromDuplicates`,
`DexFileLoader`/`DexReader`/`DexLoadResult`, `DexClassData`/`DexCodeReader`,
instruction skip and checksum code. See the preceding design for pinned source
links. Audited source, upstream LICENSE, hashes, logs and XML are retained under
excluded `.amp/in/artifacts/owned-input-integration/`.

- `OwnedDexIntegrationTest` compares actual repository-backed AUTO/RESTRUCTURE
  initialization against fresh path engines, including unedited automatic aliases
  and unsaved class/method/field/positional narrow/wide parameter renames and
  declaration comments. Only the exact loaded-from header is normalized by the
  reused comparator. Search identity/revisions remain unchanged by these reads.
- Its latch-held old snapshot survives primary edit, explicit save, changed input
  and explicit reload. Independent LE versus GT loop oracles distinguish the old
  accepted graph from new bytes. A later operation sees new edits/settings/hashes;
  save-only publication preserves graph content and changes publication identity;
  restart preserves saved edits but changes session. Changed/deleted secondary
  inputs and outside-root symlinks refuse, then recover with restored bytes.
- `OwnedDexAnalysisTest` retains divergent and repeated definitions, compares
  direct-DEX selection with a fresh path engine and loads/generates code after
  all input paths are deleted. Invalid native data triggers an actual swallowed
  upstream initialization failure; a subsequent healthy engine and update listener
  work. A checksum-valid missing-superclass fixture reaches raw census but fails
  actual class loading and cannot expose an engine.
- Structural controls admit 16 methods of 4001 units/2001 records each and refuse
  a seventeenth before engine creation. Census controls accept 512 edges and
  refuse 513 before loading. Checksum corruption also refuses.
- `InputCensusTest` covers same-reader versus path-census equivalence after path
  deletion, immutable complete raw metadata, missing/extra/reordered reader/origin
  sets, invalid byte sizes and inclusive census limits. `NativeProjectRepositoryTest`
  checks raw/native ordered repeats, PENDING identity, deleted/changed paths,
  immutable snapshot retention and explicit reload as the only acceptance step.
- Real malformed-register, graph-copy ceiling, later-generation and callback
  failure controls release temporary ownership and permit healthy subsequent work.
  A second temporary/path-based override and all requested shutdown policies
  refuse while the held operation is active. The previous test-only acquisition
  race controls and original inputless-rename counterexample remain unchanged.

## Residual limits and next work

This is not complete hostile-filesystem containment. Linux handle-relative
NOFOLLOW_LINKS traversal preserves the prior experiment's tested symlink behavior;
ancestor relocation and regular-to-special-file replacement remain unproved,
and a special-file open may block. Structural budgets do not bound all parser
allocations, annotations/debug strings, SSA/type expansion, JVM heap/stack or
execution time. Initial repository hashing and primary loading remain outside
these bounds. Cancellation stays cooperative. No worker or persistence layer,
mapping ownership, cross-platform qualification or new hierarchy-edit authority
is introduced.

**Next work item:** focused pinned-JDK/filesystem containment review with
deterministic ancestor-relocation and regular-to-special-file probes, before any
public graph exposure. Preserve the failed fixtures and propose the smallest
compatible acquisition adjustment if these races cannot be bounded under the
current in-process design; do not quietly add persisted copies, workers or weaken
allowed-root semantics. Only after that review/gate should a separately reviewed
OpenAPI-first normalized CFG slice begin. Original/raw CFGs and exhaustive
exception dispatch remain unproved.

## Executed validation and delivery

Linux x86_64, OpenJDK 21.0.12.1 and Gradle 8.14.3; locked Jadx 1.5.6.
Logs/XML and the pinned source audit are in the excluded evidence directory above.

```bash
./gradlew test --tests '*OwnedDex*Test' --tests '*InputCensusTest' \
  --tests '*NativeProjectRepositoryTest' --tests '*NormalizedCfgIsolationTest' \
  --tests '*TemporaryAnalysisTest' --tests '*OperationCoordinatorTest' \
  --tests '*SettingsRebuildFailureTest'
# regressions-2.log / regressions-2-xml: 78 passed, zero failures/errors/skips.

./gradlew clean test check installDist --no-build-cache
# java-full.log / java-full-xml: 558 total; 542 passed, 16 existing opt-in
# matching-GUI skips, zero failures/errors. Build/check/installDist succeeded.

python/.venv/bin/python -m pytest -q python/tests/unit python/tests/integration
# python-tests.log: 204 passed (175 unit + 29 live integration).

python/.venv/bin/python tests/validate-openapi.py
# openapi.log: valid 3.1; 23 operations, 42 JSON examples, 3 SSE frames.
python/.venv/bin/python tests/validate-documentation.py
# documentation.log: 58 Markdown files, 549 valid relative links/anchors.
python/.venv/bin/python tests/validate-documentation.py --self-test
# documentation-self-test.log: positive/negative controls passed.
PATH="$PWD/python/.venv/bin:$PATH" python/.venv/bin/python python/scripts/check_generated.py
# generated-2.log: all 183 generated files match.
python/.venv/bin/ruff format --check python
python/.venv/bin/ruff check python
python/.venv/bin/mypy --config-file python/pyproject.toml python/src/libjadx
# Formatting/lint passed; typing clean in 12 handwritten source files.
git diff --check
# Passed; new untracked files additionally checked separately.
```

The initial compile attempt used the wrong search-identity accessor name. The
first focused runtime run expected the generic busy exception instead of the
existing shutdown-specific rejection. The first expanded regression run tried
to assemble a missing-superclass source that Smali correctly rejected; the final
fixture instead alters the owned DEX superclass index and repairs its checksum,
so it actually reaches the intended Jadx class-load failure. These failed
attempts remain in `first.log`, `focused-1.log`/XML and `regressions-1.log`/XML;
they are not counted as passes. The first generation check omitted the venv tools
from PATH, so formatter hooks could not find ruff; correcting PATH produced the
matching result above without modifying generated sources.

No native serialization implementation changes, so no fresh matching-GUI or
release qualification is claimed. Historical evidence is unchanged and does not
qualify this new source; ordinary documentation also changes its fingerprint.
The implementation handoff initially left changes local and uncommitted.
Subsequent Git history records delivery; no release tag, deployment or
publication is implied.
