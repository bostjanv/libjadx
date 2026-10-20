# Phase 7: method Smali feasibility (test-only)

## Decision and scope

**PARTIAL feasibility; no public support upgrade.** An original owner, name and
full DEX descriptor can select a single method through the pinned disassembler's
input model without slicing class text or matching overload names. Instruction
address correspondence is **not universally exact**: Baksmali can append synthetic
copies of shared switch payloads. Public METHOD Smali remains rejected. No API,
SDK, production adapter, capability, persistence or release qualification changes
are made here.

The independently owned `tests/fixtures/smali/MethodDisassembly.smali` is assembled
by `HierarchyFixture.dex`; the non-DEX control is compiled by
`HierarchyFixture.javaJar`. All test-only integration is in
`JadxMethodSmaliProbeTest`, not shared helpers or a new production layer.

## Pinned source audit

Inspected Jadx **1.5.6**, commit
`28ff15e4ae69950aebea110a13e5ab895d234dfc`, from the exact-revision source archive:

- `jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/sections/DexClassData.java`:
  `getDisassembledCode()` passes the loaded backing byte array and class-def
  offset to `SmaliUtils`; bodyless methods receive a null code reader.
- `.../dex/utils/SmaliUtils.java`: creates `DexBackedDexFile`,
  `DexBackedClassDef` and `ClassDefinition`, then writes Baksmali with default
  options. This is a class-only string API, not a method or text-location API.
- `.../dex/sections/DexCodeReader.java`: instruction offsets start at zero and
  accumulate 16-bit code units. `SectionReader.java` exposes the loaded reader.
- `jadx-plugins/jadx-dex-input/build.gradle.kts`: pins
  `com.android.tools.smali:smali-baksmali:3.0.9`.
- `jadx-core/src/main/java/jadx/core/dex/info/MethodInfo.java`: `getShortId()`
  retains original name/signature independently of aliases.
- `jadx-gui/src/main/java/jadx/gui/ui/codearea/sync/InsnOffsetSmaliSyncer.java`:
  explicitly requires the separate debugger/Dalvik model for line-offset
  metadata, not ordinary class Baksmali text. No GUI classes are used by this probe.

Also inspected the resolved **Baksmali 3.0.9 source artifact**:
`com/android/tools/smali/baksmali/Adaptors/MethodDefinition.java`.
Its public constructor plus `writeTo` emits one implemented method;
`writeEmptyMethodTo` emits bodyless declarations. `codeOffsets=true` emits
upstream `#@hex` comments from `currentCodeAddress`, incremented by
`instruction.getCodeUnits()`. Crucially the constructor builds
`effectiveInstructions`: a second switch using the same payload gets a rewritten
target and an appended payload copy. Thus these comments address effective
disassembly and cannot always be advertised as original-reader locations.

The test uses reflection for the existing runtime-only disassembler dependency,
without lock changes. Access to `DexClassData.in` is **private, version-sensitive,
test-only** access to precisely the buffer used by Jadx's class disassembler.
No input path is reopened for disassembly. Production adoption needs a reviewed
loaded-buffer boundary; this probe does not approve reflective production access.
Source audit copies, artifact hashes and test logs are retained locally under
`.amp/in/artifacts/method-smali-feasibility/`.

## Executable cases and limits

| Case | Evidence / interpretation |
|---|---|
| Asymmetric overloads | `pick(I)I`, `pick(I)J`, `pick(Ljava/lang/String;J)Ljava/lang/String;` select distinct bodies. Return-only distinction is assembled DEX, not Java source. Owner and complete signature must match; wrong owner/return type is refused. |
| Bodyless | `absent(I)I` has no reader; upstream empty-method writer emits its declaration without fabricated registers/instructions. |
| Lifecycle and aliases | Cold disassembly leaves the class process state unchanged. Output is identical after Java generation reaches `GENERATED_AND_UNLOADED` and original class/method nodes receive aliases. |
| Ordinary offsets | `flow(I)I` includes variable-width instructions, debug line, try/catch, switch, alignment and payload. Upstream comments agree with every raw-reader start in this fixture; selected instruction text is checked directly. Directives/labels can intervene between comments and instructions. |
| Counterexample | `shared(I)V` has two switches sharing one encoded payload. Disassembly emits two active payload bodies and an address at original `unitsCount`. A one-to-one original offset/text inventory is false even with upstream offset comments. |
| Non-DEX | Real JVM JAR class is refused before DEX/Baksmali construction. Raung/JVM output is not relabeled Smali. |

The assembler itself refuses two switches targeting the same payload. The fixture
therefore starts with separate payloads; test-only code retargets the second
encoded `packed-switch` to the first payload and repairs the standard DEX SHA-1
and Adler32 before reloading. The unused second payload is commented out by
Baksmali, while the effective copy receives address **21**, outside original
instruction starts `[0, 3, 6, 7, 8, 14, 20]` and at original size **21 code units**.
The ordinary `flow` reader/comments both report
`[0, 2, 5, 8, 9, 10, 11, 12, 13, 14, 15, 16]`.
The original fixture also triggers a pinned `RegionMakerVisitor` Java error in
`shared`; class generation still reaches `GENERATED_AND_UNLOADED` and the
selected original method disassembly remains unchanged. This is not evidence of
successful Java recovery for every fixture method.

The only text inspection is test assertions and reading upstream-emitted address
comments, not a Smali parser or selector. Default class Smali has no `#@` offset
comments and no structured text map. No Java-source correspondence is proved.
Duplicate definitions/input provenance, malformed/odex disassembly, exhaustive
opcodes/payload forms, resource limits and concurrency are outside this bounded
gate. A future method-text contract could be independent of offset metadata;
exact mapping would need an explicit effective-versus-original model and further
proof, not naive line counting or descriptor substring slicing.

## Verification

Executed from repository root (all Gradle invocations serialized with the agreed
lock):

```sh
flock .amp/in/implementation-gradle.lock ./gradlew test --tests dev.libjadx.probes.JadxMethodSmaliProbeTest --no-build-cache
flock .amp/in/implementation-gradle.lock bash -c './gradlew test --tests dev.libjadx.probes.JadxMethodSmaliProbeTest --tests dev.libjadx.probes.JadxSmaliProbeTest --tests dev.libjadx.probes.JadxInstructionReferenceProbeTest --no-build-cache && mkdir -p .amp/in/artifacts/method-smali-feasibility/regressions-xml && cp build/test-results/test/TEST-dev.libjadx.probes.Jadx*SmaliProbeTest.xml build/test-results/test/TEST-dev.libjadx.probes.JadxInstructionReferenceProbeTest.xml .amp/in/artifacts/method-smali-feasibility/regressions-xml/'
```

Both builds succeeded. Final preserved JUnit XML: method probe **4/4**, existing
class Smali **2/2**, instruction references **4/4**; **10 tests, zero failures,
errors or skips**. `git diff --check` also passed.

Exploratory runs before the final Gradle verification used `javac` against cached
JARs and JUnit Console 1.12.2 while the shared Gradle queue was blocked. First:
1 passed / 3 failed because Smali rejects the shared-payload source. Second:
3 passed / 1 failed because an unused payload is commented out, not a third active
directive. Those failures drove the encoded-fixture construction and corrected
assertion; logs are retained, not erased. The final pinned Gradle run is the
authoritative verification, not that broad exploratory classpath.

The initial queue stall was an existing idle daemon holding Gradle's journal
cache and a new daemon stuck sending its lock-owner UDP ping. Both confirmed
processes were terminated after diagnostics; the queued builds then ran normally.
The other worker's interrupted initial build must be retried by its owner.
No dependency locks or caches were rewritten manually.

This is not a release or matching-GUI gate. Explicit-save behavior and native
persistence are untouched; no GUI round-trip or freshly qualified source claim
is made. Next work is a reviewed method-text/precision contract and loaded-buffer
boundary if method Smali is to be exposed, not an automatic capability flip.
