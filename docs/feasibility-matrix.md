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
| Multidex provenance and duplicate definitions | UNKNOWN | Bounded follow-up: build a redistributable two-DEX fixture with the same descriptor and inspect whether `JadxDecompiler` preserves original input identity and duplicate definitions. Do not promise per-input identity before this probe. |
| Exact Java declaration/use ranges and bytecode offsets | PARTIAL | `getCodeInfo()` metadata exists on the small JAR fixture; exact method slicing, source mapping precision, inlining and original bytecode offsets are not tested. Bounded follow-up: add methods with debug info and compare annotations with the pinned source. |
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
