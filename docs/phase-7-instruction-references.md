# Phase 7 — DEX instruction-reference feasibility gate

This starts the next substantive Phase 7 slice **after** the
[Java completeness maintenance](java-completeness.md). It is a capability probe
and implementation boundary, not a public instruction-reference API. Existing
reference endpoints, schemas, capabilities and null original offsets remain
unchanged. No persistence or endpoint is added by this gate.

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

## Boundary for the implementation slice

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
  An allowlisted initial implementation must disclose excluded forms and scan
  failures, not drop them while claiming complete coverage. No CFG, resource,
  classpath, conversion or persistence feature is implied by this gate.
