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
