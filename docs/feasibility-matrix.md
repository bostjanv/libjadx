# Jadx feasibility matrix

Evidence is tied to Jadx `1.5.6` (`4c0ac37699aa8c9803f1c73cfaacd9205acb044b`). Capabilities are reported at the precision actually exercised; UNKNOWN entries include bounded follow-up probes.

| Capability | Status | Evidence / next probe |
|---|---|---|
| Public Java loading/decompilation and class/Smali metadata | SUPPORTED for a small JAR | `JadxSmokeProbeTest` loads a generated JAR, resolves an aliased class, reads Java, non-empty code metadata and `JavaClass.getSmali()`. This does not establish behavior for every input type. |
| Basic method use references | PARTIAL | `JadxSmokeProbeTest` verifies `JavaMethod.getUseIn()` reports a local caller. Field refs, unresolved refs, exact instruction offsets and multidex provenance remain unproven. |
| Headless native `.jadx` parsing and relative input paths | SUPPORTED for the pinned simple project | `NativeProjectModelProbeTest` calls `JadxProject.loadProjectData(Path)` under headless test execution and checks project-relative input resolution. The service codec does not invoke GUI windows. |
| Multiple input and native mapping references | SUPPORTED for two JAR inputs and a Tiny v2 class mapping | The native fixture references two relative JARs and `sample.tiny`. `NativeGuiFixtureRoundTripProbeTest` resolves both inputs, passes the mapping path through `JadxArgs.setUserRenamesMappingsPath`, and verifies the mapped class alias. The matching GUI log reported two classes loaded. |
| Native rename/comment JSON save and core reload | SUPPORTED for class rename/comment | `NativeGuiFixtureRoundTripProbeTest` updates `JadxCodeData`, explicitly writes via `NativeProjectDocument`, then loads it into `JadxDecompiler`; output contains the alias and comment. Method/field rename, mapping and local/parameter edits remain unproven. |
| Unknown native JSON field preservation | SUPPORTED at project-root, `codeData` and matched rename/comment entry level | `NativeProjectDocumentTest` proves unknown project, tab, top-level code-data and edited rename/comment entry fields survive explicit writes. Entries are matched by native node/code reference; unrecognized fields on unmatched/replaced entries cannot be retained. `ProjectData` model serialization alone drops unknown fields (`NativeProjectModelProbeTest`). |
| Actual matching jadx-gui round-trip | SUPPORTED for class rename/comment and relative input | `guiRoundTripTest` opens the headless-edited project in Jadx 1.5.6 `jadx-gui`, saves it through GUI Save As, then reopens the GUI-saved file headlessly and verifies code data. The fixture was created by that GUI. |
| Original class/member identities | SUPPORTED for owned JAR fixture; provenance UNAVAILABLE | `JadxSymbolProbeTest` checks raw class descriptors, inner/anonymous names, `<init>`/`<clinit>`, overloads and raw method/field type descriptors before and after class/method/field aliases. A classfile with return-type-only overloads preserves separate `()I` and `()Ljava/lang/String;` refs. Class listing did not change the class process state. No exact input identity is assigned. |
| Two-JAR duplicate definitions | PARTIAL; one definition visible | The owned two-input `duplicate.Clash` fixture yielded one Jadx-visible class in `JadxSymbolProbeTest`. The loader did not expose a second candidate, so the API does not invent one and reports per-input provenance `UNAVAILABLE`. This is not multidex evidence. |
| Multidex provenance and duplicate definitions | UNKNOWN | Bounded follow-up: build a redistributable two-DEX fixture with the same descriptor and inspect whether `JadxDecompiler` preserves original input identity and duplicate definitions. Do not promise per-input identity before this probe. |
| Java class source | SUPPORTED for owned JAR/native fixtures; PARTIAL across all Jadx inputs | `JadxSourceProbeTest` and `DecompileEndpointsTest` compare the returned string with pinned `ICodeInfo.getCodeStr()`. The endpoint test requests the owned inlined anonymous class first and verifies its `getTopParentClass()` source owner; a synthetic class with no methods or fields remains `UNAVAILABLE`. |
| Class errors and warnings | PARTIAL | Pinned `JadxError` and `JadxCommentsAttr` are class/method attributes. The source adapter emits bounded, sanitized messages when present and labels known error output `PARTIAL`. A complete per-source-owner error count, including every inlined child, has not been proved; `classErrorCount` is null. Follow-up: construct a reproducible failed-code fixture and audit nested-node coverage. |
| Declaration positions | PARTIAL; emitted positions EXACT | `JadxSourceProbeTest` records `NodeDeclareRef` offsets for class, field and method name tokens in `SymbolFixture.java`; HTTP tests validate offsets in the exact returned string. Unsupported targets and mismatched token positions are omitted. Declaration spans and local-variable positions are not claimed. |
| Reference targets | PARTIAL; emitted positions EXACT | Owned field-use and method-call annotations point to verified `count` and `mix` tokens and original refs. Incomplete/unresolved/structural Jadx annotations are omitted with a diagnostic. The metadata map does not prove all source references are present. |
| Method ranges | PARTIAL | `mix(int)` and `mix(String[],int)` use original-method `NodeDeclareRef`, a bounded lexer and a matching `NodeEnd`, then return a half-open substring of the same class source. `<clinit>` is a deterministic fallback with no invented range. Return-type-only overloads are checked independently; any range must round-trip to its own source. Other declarations may fall back explicitly. |
| Original debug lines | UNKNOWN; API UNAVAILABLE | `ICodeMetadata.getLineMapping()` exists, but its origin and behavior after optimization were not established on the pinned fixture. `includeRawDebugLines` reports unavailability, and strict requests fail. Follow-up: probe classfile debug tables and compare original versus generated line domains. |
| Original bytecode offsets | UNKNOWN; API UNAVAILABLE | Pinned `InsnCodeOffset` exists, but its origin/precision relative to generated Java and DEX/JVM inputs are unproved. No bytecode offsets are returned. Follow-up: compare pinned instruction disassembly and annotations on owned JAR/DEX fixtures. |
| CFG access and representation distinction | UNKNOWN | Bounded follow-up: inspect pinned graph APIs and test one loop/branch/try-catch method. No CFG endpoint or exactness guarantee is permitted yet. |
| Android resources and manifest decoding | UNKNOWN | Bounded follow-up: use an owned minimal APK containing manifest, XML and assets; compare `ResourceFile` output with decoded fixture entries. |
| Concurrent reads, same-class deduplication and mutation races | UNKNOWN | Bounded follow-up: stress two fixture classes and the same class concurrently, then add mutation/reload overlap tests. Keep project-wide serialization until this passes. |
| Cancellation and cache invalidation | UNKNOWN | Bounded follow-up: test cooperative cancellation points and `reloadCodeData()` after mutation; deadlines remain best-effort. |

## Phase 2 exercised behavior

`ProjectEndpointsTest` uses the pinned Jadx engine and native fixture to check
explicit save, mapping conflict, pending edit export, discard/reload, and a
mapping-path rebuild that retains an unsaved class rename. `TemporaryAnalysisTest`
uses a separate Jadx instance to verify that unsaved rename/comment state is
deep-copied at admission. `rawGuiRoundTripTest` opens a raw-input-created native
project in the matching GUI and reads the GUI-resaved file headlessly. These
tests do not establish concurrent class-read safety, source position precision,
or a persistent representation for decompilation mode.

## Phase 0 exit status

**Phase 0 exit gate passed for the first implementation slice.** A matching-GUI native fixture, two relative inputs, a native mapping reference, headless class rename/comment save, unknown-field retention, and actual GUI open/save/headless reopen are covered. Advanced behavior remains limited or unknown as listed above; keep those capability limits explicit and probe them before implementing corresponding API features.
