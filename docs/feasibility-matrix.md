# Jadx feasibility matrix

Evidence is tied to Jadx `1.5.6` source commit `28ff15e4ae69950aebea110a13e5ab895d234dfc` (annotated tag object `4c0ac37699aa8c9803f1c73cfaacd9205acb044b`). Capabilities are reported at the precision actually exercised; UNKNOWN entries include bounded follow-up probes.

| Capability | Status | Evidence / next probe |
|---|---|---|
| Public Java loading/decompilation and class/Smali metadata | SUPPORTED for a small JAR | `JadxSmokeProbeTest` loads a generated JAR, resolves an aliased class, reads Java, non-empty code metadata and `JavaClass.getSmali()`. This does not establish behavior for every input type. |
| Basic method, field and class references | PARTIAL | `JadxReferenceProbeTest` verifies distinct incoming/outgoing method pairs, field user methods, class dependencies and missing external method descriptors. `ReferenceEndpointsTest` verifies source sites and real graph-content changes at unchanged revision. READ/WRITE, original offsets, exhaustive coverage and multidex provenance remain unverified. |
| Headless native `.jadx` parsing and relative input paths | SUPPORTED for the pinned simple project | `NativeProjectModelProbeTest` calls `JadxProject.loadProjectData(Path)` under headless test execution and checks project-relative input resolution. The service codec does not invoke GUI windows. |
| Multiple input and native mapping references | SUPPORTED for two JAR inputs and a Tiny v2 class mapping | The native fixture references two relative JARs and `sample.tiny`. `NativeGuiFixtureRoundTripProbeTest` resolves both inputs, passes the mapping path through `JadxArgs.setUserRenamesMappingsPath`, and verifies the mapped class alias. The matching GUI log reported two classes loaded. |
| Native rename/comment JSON save and core reload | SUPPORTED for class rename/comment | `NativeGuiFixtureRoundTripProbeTest` updates `JadxCodeData`, explicitly writes via `NativeProjectDocument`, then loads it into `JadxDecompiler`; output contains the alias and comment. Later rows cover declaration/mapping and scoped parameter extensions; local edits remain unsupported. |
| Unknown native JSON field preservation | SUPPORTED at project-root, `codeData`, matched rename/comment entry and scoped rename reference levels | `NativeProjectDocumentTest` proves unknown project, tab, top-level code-data and edited rename/comment entry fields survive explicit writes. Entries are matched by typed native node/code identity; PR #13 additionally verifies unknown nested node/code-ref members survive a scoped rename. Unrecognized fields on unmatched/replaced entries cannot be retained. `ProjectData` model serialization alone drops unknown fields (`NativeProjectModelProbeTest`). |
| Actual matching jadx-gui round-trip | SUPPORTED for class rename/comment and relative input | `guiRoundTripTest` opens the headless-edited project in Jadx 1.5.6 `jadx-gui`, saves it through GUI Save As, then reopens the GUI-saved file headlessly and verifies code data. The fixture was created by that GUI. |
| Original class/member identities | SUPPORTED for owned JAR fixture; provenance UNAVAILABLE | `JadxSymbolProbeTest` checks raw class descriptors, inner/anonymous names, `<init>`/`<clinit>`, overloads and raw method/field type descriptors before and after class/method/field aliases. A classfile with return-type-only overloads preserves separate `()I` and `()Ljava/lang/String;` refs. Class listing did not change the class process state. No exact input identity is assigned. |
| Two-JAR duplicate definitions | PARTIAL; one definition visible | The owned two-input `duplicate.Clash` fixture yielded one Jadx-visible class in `JadxSymbolProbeTest`. The loader did not expose a second candidate, so the API does not invent one and reports per-input provenance `UNAVAILABLE`. This is not multidex evidence. |
| Multidex provenance and duplicate definitions | PARTIAL, internal census only | PR #15 owned direct-DEX and two-JAR embedded-DEX probes retain both raw definitions and configured origins. Raw absolute DEX labels select the first; embedded classes.dex is preferred over earlier classes2.dex. Existing public symbol provenance remains UNAVAILABLE. APK/bundle coverage is unverified. |
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
| Cancellation and cache invalidation | PARTIAL for search; general graph behavior UNKNOWN | `SearchBuildJobTest` checks cooperative class-boundary cancellation and lease release; `SearchInvalidationTest` checks code-data, native save and reload cursor invalidation. JVM-level interruption, complete graph invalidation and broader concurrent class-read safety remain unproved. |
| Cold class-name search | SUPPORTED for owned JAR/native fixtures; input census UNVERIFIED | `JadxSearchProbeTest` observes unchanged Jadx class process state after catalog and original-owner enumeration. `SearchEndpointsTest` and installed-distribution test show cold class hits with original refs. Duplicate original input definitions can be collapsed by Jadx. |
| Incremental member-name search | SUPPORTED for Jadx-visible processed classes; initially PARTIAL | `JadxSearchProbeTest` preserves distinct return-only overload descriptors; decompile ingestion and the complete-index job copy original refs and aliases. Unprocessed or failed classes remain explicit. |
| Exact emitted-Java source search | SUPPORTED for successful owner source; initially PARTIAL | `SearchEndpointsTest` compares UTF-16 half-open hit ranges and snapshot IDs with the exact `/decompile` Java, including a supplementary Unicode code point. Anonymous/inner classes share one emitted owner. Failed or evicted owners keep coverage partial. |
| Safe source regex | SUPPORTED with bounded RE2/J 1.8 syntax | RE2/J uses linear-time matching; unsupported backreferences return 422. Expressions are limited to 256 characters; hits and copied DTOs are capped. This is emitted Java search only. |
| Emitted string literal lexer and original constants | UNKNOWN; API UNSUPPORTED | A lexical ownership probe across strings, text blocks and Java escaping remains. Search only sees these characters through `SOURCE_TEXT`; it does not label them as original bytecode constants. |
| Annotation and resource metadata search | UNKNOWN; API UNSUPPORTED | Bounded follow-up: audit pinned annotation and resource APIs using an owned JAR/APK fixture, including cheap pre-code availability and provenance. |
| Complete-index job and cancellation | SUPPORTED for Jadx-visible eligible owners; partial failures explicit | `SearchBuildJobTest` verifies class-boundary cancellation, engine lease release and a verified empty synthetic skip. `StandaloneDistributionTest` runs the installed headless job and strict source search. JVM-level interruption during Jadx work remains unproved. |
| Native class/method/field declaration rename | SUPPORTED for owned Java fixture; GUI save/reopen verified | `EditBatchEndpointsTest` stages all three aliases and explicitly saves native data; `nativeEditGuiRoundTripTest` has the matching GUI resave and headless emitted Java check. `EditBatchServiceTest` proves return-type-only overloads have distinct native IDs. Synthetic/bridge/special methods and exact input origin remain unsupported. |
| Native class/method/field LINE declaration comments | SUPPORTED for owned Java fixture; GUI save/reopen verified | The same GUI task retains three comments after resave. `EditBatchEndpointsTest` updates one LINE comment without erasing a BLOCK comment on the same node or its unknown fields. Multiline/control input and other styles are intentionally rejected. |
| Batch prevalidation and partial execution | SUPPORTED for validated declaration edits | `EditBatchEndpointsTest` proves invalid later item yields no native change, unchanged revision and usable class cursor; `EditBatchServiceTest` injects a staging failure after one staged item and observes only the real committed prefix. Normal batches commit once. Candidate load failure publishes no prefix and leaves READY. |
| Safe native Tiny v2 export | PARTIAL, strict verified declarations | `JadxMappingExportProbeTest`, `MappingExportServiceTest`, `MappingExportEndpointsTest` and the matching `mappingExportGuiRoundTripTest` verify attached aliases plus unsaved class/method/field aliases and LINE comments. Unknown/duplicate records, unverified code refs/styles, namespaces/options and unresolved keys fail closed. No-clobber, faults, external changes and retained revisions have regression tests. See `phase-5-mapping-export.md` for actual gate outcomes and residual filesystem limits. |
| Native mapping import/merge | PARTIAL, strict Tiny v2 declaration subset | `JadxMappingImportProbeTest`, `MappingImportServiceTest`, `MappingImportEndpointsTest` and matching `mappingImportGuiRoundTripTest` prove conservative original-key merge, additive LINE suffixes, one native commit, no-op/cache identity, source races and explicit save/reopen. Inner renames, arbitrary composites and conflicting existing aliases fail closed. See `phase-5-mapping-import.md`. |
| Parameter rename | SUPPORTED for verified plain AUTO/RESTRUCTURE concrete signatures | `JadxVariableProbeTest`, `ScopedParameterServiceTest`, `ScopedParameterEndpointsTest`, packaged process restart and `scopedEditGuiRoundTripTest` cover original positional indexes, wide arguments, source binding, prevalidation and explicit persistence. Unsupported forms fail closed. See `phase-5-scoped-editing.md`. |
| Local rename | UNSUPPORTED; deliberate pinned limitation | PR #20 retains merged SSA/mode and same-mode positive probes, and proves a one-local native VAR key retargets from an absolute value to its square under matching GUI Use dx/d8. Actual GUI Save As preserves records without binding global mode/loader settings. No safe product predicate is proved; [feasibility](phase-5-local-rename-feasibility.md). Phase 5.2 closes with this exclusion. |
| Generic fresh-equivalent native edit publication | SUPPORTED for the owned complete-state matrix | `ReplacementPublicationTest` and `ReplacementStateTest` prove staged load-before-commit publication, faults/leases, explicit intent and fresh-derived aliases. `SafeReplayStrategyTest` retains old replay negative controls. See [PR #18](phase-5-replacement-publication.md). |
| Verified related-method group rename | PARTIAL — conservative independently COMPLETE closed-input subset | PR #19 same-exclusive-lease verification, raw all-owner collision blockers, immutable group staging and exact affectedRefs; PR #18 fresh replacement. Ordinary native records, explicit save/restart and matching-GUI Save As. See [admission](phase-5-related-group-admission.md) and [final evidence](pr-19-review.md). |
| Original-input declaration census | SUPPORTED for bounded class/JAR/DEX subset, internal only | `RawInputCensusProbeTest` and `InputCensusTest` retain duplicates and divergent methods/access/supertypes before RootNode selection, with exact configured origins, hard-limit boundaries and strict parse/checksum failures. No source bodies or mutable Jadx nodes are retained. |
| Independent override-family completeness | SUPPORTED for conservative closed-input exact-return subset, internal only | `IndependentHierarchyVerifierTest` verifies chains/defaults/diamonds/independent branches, visibility, inherited interface implementations, two inputs and DEX. Every complete family is seed independent. Bridge/covariant/synthetic, missing/external/duplicate and resource-limited results expose no partial family. `HierarchyVerifierLifecycleTest` checks lifecycle and input invalidation. See [rules](phase-5-hierarchy-verifier.md). |

## PR #21 Python SDK foundation

Generated OpenAPI 3.1 transport, sync/async wrappers and local packaging are
SUPPORTED for the representative owned-server workflows on CPython 3.11.13 and
3.14.4 (Linux). All 22 operation IDs have generated functions; live exhaustive
route/error parity and larger cancellation/lifecycle workflows remain Phase 6.2.
Native/GUI behavior and all Jadx capability limitations above are unchanged.
See [SDK architecture](phase-6-python-sdk.md) and [validation](pr-21-review.md).

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

## Phase 4.3 reference probe

`JadxReferenceProbeTest` proves distinct incoming/outgoing method pairs, field
users, class dependencies and omitted-dependency original method descriptors
on an owned JAR. Graphs exist before owner decompilation, but may be pruned by
later processing. Recursive self edges can be absent. READ/WRITE, exhaustive
coverage and original offsets remain unavailable. See [reference evidence](phase-4-references.md).

## Historical PR #17 safe replay feasibility (superseded below)

Outcome B; production replay and every accepted edit contract remain unchanged.
Fresh replacement reconstructs complete native/mapping/scoped state and fixes
PR #16's unrelated hot bridge aliases on the owned Joined fixture. It also
recomputes an unedited automatic collision alias in the existing return-only
fixture (`m0value` → `value` after renaming the integer-return declaration).
Current replay preserves that alias but fails fresh equivalence. Listener-first
loses mapping comments; owner-only leaves another owner's generated references
stale. No tested generic strategy passes every gate. Propagation still returns
422 on every flag presence, with its existing UNSUPPORTED capability evidence.

[Detailed strategies, pinned source, oracle and cost](phase-5-safe-replay.md) and
[final validation](pr-17-review.md) distinguish positive reconstruction probes
from the failed adoption gate. No engine swap, alias patch, native extra record,
autosave, API/SDK or dependency change is shipped. The next decision concerns
automatic nonmember alias recomputation; group admission remains dependent on
safe publication and its separate service/native/GUI gates.

## PR #18 approved alias semantics and replacement publication

This section supersedes PR #17's requirement to preserve every incidental alias.
Automatic Jadx aliases are derived analysis state: collision aliases,
deobfuscation aliases without explicit persistence, and other generated aliases
may be recomputed when an edit changes analysis. A declaration with no explicit
rename may therefore change its display alias without a separate user edit.
Native declaration renames, mapping aliases/comments, supported scoped renames,
retained VAR records and declaration comments remain authoritative. Original
identities, settings, ordered input references and unknown native fields retain
their existing preservation rules. Never add native records to freeze generated
aliases.

Effective native batches now privately stage complete code data, load one fresh
production engine, commit native data once, then publish that engine under the
existing exclusive lease. No-op retains the engine and revisions; candidate
failure publishes nothing. See [replacement publication](phase-5-replacement-publication.md) for ordering,
failures, oracle, persistence and validation. PR #19's
[verified group admission](phase-5-related-group-admission.md) now supplies same-lease
COMPLETE verification, immutable plans, all-owner raw collision checks and exact
native records/affectedRefs. Local editing remains unsupported.

## PR #19 — verified related-method rename group admission

This section supersedes prior propagation deferrals. Explicit METHOD RENAME
`propagateRelated: true` admits only independently COMPLETE closed-input families
verified synchronously against the captured current engine under one exclusive
edit lease. Immutable sorted original/native plans, raw all-owner collision
inventory, full-batch overlap checks and group-private atomic staging precede
PR #18 fresh replacement publication. Ordinary omitted/false retains one-record
semantics and empty affectedRefs. Applied and verified no-op groups return exact
original family refs; no-op requires every explicit member record already present.

Limits: 64 per family; four propagated items, 128 total members and 800000 reserved
verification work per batch. No class or ordinary method rename shares a group
batch; parameter edits on members reject. Field renames and declaration comments
may coexist. Raw hidden bridge/synthetic methods block collisions,
without becoming admitted members. Standard Jadx records persist only on explicit
save. Restart/discard/reload, accepted input/mapping conflicts and actual matching
GUI Save As retain their native-only behavior. Covariant/bridge, missing/external,
duplicate, local and parameter propagation remain unsupported. PR #20 subsequently closes Phase 5.2 with local rename
deliberately unsupported; the Python release remains open. No dependencies or locks change.

See [admission, status mapping, ordering and persistence](phase-5-related-group-admission.md)
and [final-head validation](pr-19-review.md).
