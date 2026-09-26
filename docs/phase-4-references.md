# Phase 4.3: Jadx-reported references

Pinned source: Jadx 1.5.6, commit `4c0ac37699aa8c9803f1c73cfaacd9205acb044b`.
All upstream paths below are relative to that commit's `jadx-core/src/main/java/`.

## Executable feasibility evidence

`JadxReferenceProbeTest.coldAndOwnerProcessedGraph` compiles the owned
`tests/fixtures/references/ReferenceFixture.java`, then omits the owned
`MissingDependency.class` from its analysis JAR. No GUI runtime is used.

- At `NOT_LOADED`, before owner processing, entry already has four distinct
  resolved used methods and one unresolved method. Processing only the owner
  preserves those entry relationships. Two helper invocations produce one pair.
- Incoming helper users contain entry. Both ordinary and static fields report
  entry among their users, including compound increment. Access direction is
  unavailable: `JavaField.getUseIn()` contains methods, not access instructions.
- `ReferenceOther` is an outgoing dependency and reports the fixture as an
  incoming user. Dependencies also include the base class and interface.
- Missing `external(Ljava/lang/String;)Ljava/lang/String;` retains its original
  owner `probe.MissingDependency`; it is not a loaded method.
- Interface/base dispatch targets are the interface/base methods, not the
  overriding fixture implementation. Override-related results include all
  three declarations; this optional relation is not exposed in this slice.
- Recursive `helper(I)I` reports `callsSelf=true` but an empty processed `getUsed()`.
  No self-edge is synthesized. Lambda/synthetic and anonymous identities may
  remain graph nodes even when no standalone Java declaration is emitted.
- Reflection does not make `ReflectiveOnly` a static class dependency.
- Two direct helper tokens have P4.2 original targets, verified method ranges,
  and `ICodeMetadata.getNodeAt` equal to entry. Field tokens also have verified
  caller metadata. Sites require all these checks; nested/inlined ambiguity
  must fall back to no site.
- `javap -c` verifies actual JVM getfield/putfield instructions. Jadx OFFSET
  annotations exist, but no original instruction-to-site provenance is proved.
  Offsets and READ/WRITE remain unavailable. DEX offsets are not validated.

## Pinned source audit

- `jadx/api/JavaMethod.java`: `getUsed`, `getUseIn`, `getUnresolvedUsed`,
  `getOverrideRelatedMethods`; `jadx/api/JavaField.java`: method-level users.
- `jadx/api/JavaClass.java`: class users/dependencies and source ownership.
- `jadx/core/dex/nodes/MethodNode.java`: `getUsed` prunes methods when their
  incoming list no longer contains the caller (`removeInvalidMethodsUsed`).
- `jadx/core/dex/nodes/ClassNode.java`, `FieldNode.java`: underlying graph lists.
- `jadx/core/dex/info/MethodInfo.java`: raw declaring class, name, argument and
  return types; `TypeGen.signature` preserves the full original descriptor.
- `jadx/core/dex/visitors/usage/UsageInfoVisitor.java`: initialization scans input
  instructions/types before class source generation. It may omit unsupported
  instructions and records scan failures. This is not exhaustive input evidence.
- `jadx/core/dex/visitors/usage/UsageInfo.java`: distinct relationships,
  unresolved uses, and explicit exclusion of default Object constructors.
- `jadx/api/metadata/ICodeMetadata.java`, `ICodeAnnotation.java`,
  `impl/CodeMetadataStorage.java`: position annotations and enclosing-node walk.
- `jadx/api/metadata/annotations/InsnCodeOffset.java`: line annotations carry
  an instruction offset without the original method/input/unit proof we need.

Source archive inspected at `/tmp/libjadx-upstream`, downloaded by exact commit;
no build-time upstream checkout is needed. Reproduce with:

```sh
./gradlew test --offline --tests dev.libjadx.app.JadxReferenceProbeTest
```

## Delivered contract and bounds

`POST /api/v1/references/query` requires an original `ref` and explicit
`direction` for every symbol kind. Fields accept only INCOMING. Compatible
relations are CALL (methods), UNRESOLVED_CALL (outgoing methods), FIELD_USE
(incoming fields), and CLASS_DEPENDENCY (classes). Unknown READ/WRITE filters
are rejected with 400; they never silently erase FIELD_USE results.

Primary effective settings are used. One `CLASS_READ` lease captures engine,
revision, publication epoch and settings before resolution or processing.
The requested owner is processed; optional sites process only reported caller
owners. Jadx itself may process dependencies. There is no LibJadx global crawl,
background job, persistent graph or retained query cache. There is no mode
parameter. Hidden graph nodes are reported as OBSERVED where suppression is
known, with original refs retained. Provenance is unavailable independently
of whether the graph endpoint is resolved.

Source sites require P4.2 exact target metadata, its source snapshot algorithm,
a verified method range and matching `getNodeAt` caller metadata. Repeated
calls can have two sites on one edge. Overloaded descriptors stay separate.
Missing external methods, synthetic lambdas without a method range, and
unverified inlined boundaries have no speculative sites. Every offset is null.
Even with sites, source-site coverage remains PARTIAL. All resolved strict
queries return 409 INCOMPLETE_ANALYSIS because global coverage is unproved;
NOT_FOUND, AMBIGUOUS and PROVENANCE_UNAVAILABLE stay typed 200 outcomes.

The collector rejects above 10,000 observed edges, 20,000 sites or a conservative
4 MiB copied-string budget (six bytes per UTF-16 code unit, including identity
encoding overhead). Earlier string rejection is possible. Source extraction
retains P4.2's separate 4 MiB/source and 20,000 annotation limits. These ceilings
do not constrain Jadx's internal allocations or guarantee a wall-time deadline.
No truncated success is returned. Diagnostics are fixed/bounded to 16 × 512.

Pages contain 1–100 requested edges (default 50). The full observed filtered
result is copied, deduplicated and sorted by original relation/source/target,
resolution and verified site identity. A versioned length-prefixed UTF-16BE
encoding hashes state, normalized options and full edge evidence/sites.
The snapshot and HMAC cursor bind session, logical revision, engine publication,
effective settings, filters and last raw ordering key. The existing private
operational key is reused, with `libjadx-references-v1` endpoint separation.
Signature verification precedes decoding any payload field. Follow-up requests
recompute content; changes return STALE_REVISION, including changes without a
logical edit. A changed page size/filter returns 400. Cursors are capped at
4096 characters; an unusually long ordering key that cannot fit returns
RESOURCE_LIMIT. `pageComplete` only exhausts this observed result.

`ReferenceEndpointsTest.graphObservationChangesAtSameLogicalRevision` processes
`ReferenceLate` through `/decompile` between pages. Jadx changes the reported
helper users while logical revision remains equal; the old cursor is rejected.
This is a real pinned graph change, not injected mock data.

Native mappings and unsaved rename/comment tests keep original graph refs
unchanged, update snapshot identity and leave project/mapping bytes untouched.
Queries do not alter dirty state or logical revision. Native save publication,
reload, settings rebuild, edits and restart invalidate cursors. The native-save
monitor race returns PROJECT_BUSY within two seconds, before any monitor-taking
repository access. Installed tests launch the headless distribution and verify
nonempty references and authenticated stale/tampered cursors across restart.

## Remaining limits and next milestone

Evidence is from owned JVM JAR inputs, not a complete DEX precision audit.
Recursive self edges can be missing, native Jadx optimization can prune or
redirect graph relationships, and no complete call graph or dispatch closure is
claimed. READ/WRITE, override navigation, original offsets, exact per-input
provenance and reflection inference are not exposed. Python transport remains
Phase 6; this PR introduces no SDK. After review and merge, Phase 5 incremental
search and supported edits is the next milestone.
