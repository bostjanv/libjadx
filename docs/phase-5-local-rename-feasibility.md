# Native local-variable rename retargeting feasibility (PR #20)

> **Historical milestone record.** This document describes repository state at its named phase/PR. Its “next milestone,” “pending,” and support-status statements are historical. For current product state see the [documentation index](README.md), [compatibility](compatibility.md), [feasibility matrix](feasibility-matrix.md) and [PR #22 qualification](pr-22-review.md).

Outcome B on pinned Jadx **1.5.6**, source
**28ff15e4ae69950aebea110a13e5ab895d234dfc**: local rename remains deliberately
unsupported. No public local target, operation, verifier, persistence format or
SDK is added. Capability evidence is
`NATIVE_VAR_RETARGETS_WITH_UNBOUND_GUI_SETTINGS`, persistence `UNAVAILABLE`.
The existing `RENAME_LOCAL` rejection remains 422 `UNSUPPORTED_CAPABILITY`.

All Phase 5.2 edit capabilities that can be safely persisted through the proved
pinned native Jadx semantics are implemented. Local-variable rename remains
deliberately unsupported because native VAR identity is not proven
settings-independent. Phase 5.2 closes with this exclusion; Phase 6 Python SDK
work is next. This does not claim that every Jadx editing feature is supported
or that the first experimental release has passed its Python gates.

## Native identity and consumer

The persisted key is the original dotted owner and full method short ID in
`JadxNodeRef`, plus `JadxCodeRef(VAR, register << 16 | ssaVersion)`. The record
contains `newName`; it contains no mode, input-loader choice, source snapshot or
original-variable provenance. `CodeRenameVisitor` decodes the register using
signed `>> 16` and the SSA version with `& 0xffff`, searches `MethodNode.getSVars`,
and sets the first matching SSA variable's **CodeVar** name. It does not check
whether that SSA variable is an argument, emitted declaration or the same
logical local selected when the record was created.

The factory itself accepts unchecked integers. Executable tests demonstrate
`forVar(0,65536) == forVar(1,0)`, high-register truncation, signed high-register
decoding and negative-version overflow. These are diagnostics of upstream
encoding, never accepted public local targets.

`VarNode` caches one declaration node per CodeVar; `CodeVar.getAnySsaVar` returns
its first SSA member. `InitCodeVariables` links phi-connected SSA variables into
a CodeVar; later SSA/shrink/region passes can remove, merge or change the emitted
representative. Thus a native key, an emitted declaration and a logical local
are three different things. Display-name equality is not identity evidence.

## Owned fixtures and diagnostic method

`LocalShapes.java` is compiled with **`-g:none`**, without bytecode parameter or
debug local names. It covers straight-line primitive locals, sequential reuse,
branches, phi/loop accumulators, nested scopes, temporary pressure, a used catch,
switch/try/finally, long/double locals and generic/object locals. Its companion
contract and generic implementation supply bodyless and real synthetic bridge
methods. `LocalDex.smali` is assembled into a **direct DEX**, covering straight
primitive, explicit reuse, branch/merged loop, wide and object locals. This DEX
is loaded directly, separately from the JVM-to-DEX conversion diagnostic.
The original `Variables.java` and `UnusedCatch.smali` negative controls are
unchanged.

Test-only `LocalIdentityDiagnostics` records immutable original method refs,
native short IDs, register/SSA, emitted type/name/token and UTF-16 range,
argument classification, exactness, source snapshot, mode, SSA definitions and
uses, uniqueness, phi and register-reuse observations. A read-only observer
immediately after the **actual CodeRenameVisitor** captures what the native
record consumes, before shrinking and code generation. This catches consumed
variables that do not have emitted declaration metadata. No CodeVar/SSAVar is
mutated by the observer; only upstream's ordinary native rename visitor edits
names. DONT_UNLOAD_CLASS and visitor-list instrumentation remain test-only.
Pinned RootNode ignores custom plugin passes outside AUTO, so the diagnostic
inserts its observer directly after the consumer in the actual visitor list.

The matrix tests every emitted RESTRUCTURE local independently in AUTO,
RESTRUCTURE, SIMPLE and FALLBACK, with default settings, no finally extraction,
and no method inlining. JVM candidates additionally run under the GUI's normal
**Use dx/d8** input setting. The generated artifact is
`build/local-rename-probe/mode-matrix.json`.

Rows distinguish SAME_LOCAL, DIFFERENT_LOCAL, PARAMETER, ABSENT_INERT, AMBIGUOUS
and UNVERIFIABLE. SAME_LOCAL here means agreement of the available fixed-input
def/use diagnostics, not a general native identity guarantee. Changed transformed
roles are UNVERIFIABLE unless an owned semantic oracle proves actual retargeting.
Definition offsets from different loaders are never compared as if they used
one coordinate system. ABSENT_INERT requires no consumer match, no sentinel in
source, and unchanged source against that mode/settings' unedited control.
Native data must remain unchanged in every engine.

DX conversions include a fresh `/tmp/jadx-<number>/classes.dex` origin comment.
Only that generated comment is removed for cross-conversion source comparisons;
the diagnostic stores the original source, snapshots and declaration ranges.
No returned source or public offset is normalized.

## Counterexamples and candidate predicates

The retained `JadxVariableProbeTest.localNativeKeysAcrossReplayReopenAndModes`
still asserts RESTRUCTURE `merged(I)I:5:1`, SIMPLE `merged(I)I:5:0`, and no
FALLBACK VarNode declarations. The expanded branch and direct-DEX merged cases
also show that the native key can name a CodeVar whose emitted representative
has a different key. A missing emitted key is **not** automatically an inert
record: the native consumer must be measured separately.

The decisive new counterexample uses an ordinary GUI input setting on the same
unchanged JVM input:

| Original method/key | Intended default-loader computation | With Use dx/d8 |
|---|---|---|
| `simpleRetarget(I)I`, `(0,2)` | `Math.abs(input + 43)` | The square of that absolute value |
| `pressure(I)I`, `(0,2)` | `Math.abs(input + 43)` | The multiplication of the two absolute-value locals |
| `wide(JD)J`, `(0,3)` | `fraction + 79.5` | The sum of the wide intermediate results |

The smallest method has one emitted local, exact declaration metadata, a unique
SSA key and one CodeVar/SSA definition, without a phi or register reuse in the
final transformed tree. The computations and uses distinguish the
targets; this is not a conclusion based on equal integers or coincident names.
Native intent replays correctly in the original setting, but the **same record**
names a different computation after changing the GUI's input configuration.

Straight-line, one-definition, no-phi and exact-declaration predicates therefore
do not by themselves authorize persistence. No-register-reuse observations on
the final transformed tree fail on this counterexample and cannot prove original
register/provenance stability. A few primitive/object/direct-DEX candidates remain stable in the
tested modes; straight-line JVM keys may be safely ignored under conversion.
Those positive observations are retained. They do not establish a deterministic
admission rule over arbitrary inputs and normal GUI configuration, and no
fixture-name whitelist is an acceptable product predicate. We have not proved
that every conceivable DEX-only or special whole-method subset is impossible.
The decision is that **no native-safe product subset has been proved** for this
milestone's input/settings boundary.

A four-mode private verifier alone would miss the loader retargeting case.
Across JVM and converted DEX, the diagnostics do not supply a verified common
original-local provenance coordinate that could authorize a generic comparison.
No production dynamic verifier or cross-revision approval cache is introduced;
its performance/admission/fault gates are consequently inapplicable.

Candidate discovery retains the following distinctions (categories are diagnostic,
not public admission statuses):

| Category | Representative evidence |
|---|---|
| MODE_STABLE in tested non-FALLBACK modes | Direct-DEX straight/wide/object locals agree on available def/use roles; no general GUI-safe DEX predicate is claimed. |
| ABSENT_IN_MODE | JVM straight `(0,1)` is inert with DX; FALLBACK is inert for all matrix keys. Native records survive and the GUI default-mode reopen restores intent. |
| SSA_CHANGED / SAME_MODE_ONLY | Original merged control and expanded branch/DEX merged declarations change representatives under SIMPLE. Same-mode replay remains positive. |
| RETARGETED | JVM simple, pressure and wide keys select different computations under DX in AUTO, RESTRUCTURE and SIMPLE. |
| UNVERIFIABLE | Changed transformed roles or a consumed key without emitted declaration metadata do not prove semantic sameness. |
| PARAMETER_COLLISION / AMBIGUOUS scoped state | Native parameter VAR, mixed MTH_ARG/VAR and duplicate VAR records cannot authorize parameter edits; no observed local-to-parameter retarget is asserted. |

## Replay, replacement and scoped-state evidence

`LocalRenameStateProbeTest` retains positive native replay for every emitted
fixture local in the originating AUTO setting: cached generation, unrelated
owner generation in both JVM and DEX, explicit owner unload/reprocess, fresh engines, complete
native state through PR #18 replacement, explicit native save and reopen.
Local records are injected through an **internal test seam**, not exposed as a
service operation. A later ordinary supported comment edit uses the real
EditBatchService replacement path. Published source and full declaration/SSA
diagnostics agree with a separately built fresh engine with identical code data.
The DEX replay project has a second independently assembled DEX input supplying
the unrelated owner. Its input order and full unrelated-owner source/metadata
are compared through replacement and native reopen as well as the target owner.
All four isolated temporary modes receive the complete pending native snapshot
and agree with independent fresh engines for those modes. The native file stays
unchanged while dirty and before explicit save.

Manual owner unload clears the test retention flag; it must be reinstalled to
observe SSA state. JVM generic signature attributes also disappear on that
in-place reprocess. The test explicitly asserts this source difference while
checking that every explicit local native key/name survives; it does not call
this complete-source equivalence. Fresh PR #18 replacement and native reopen
have their own full source/metadata comparisons.

`LocalRenameBoundaryTest` retains local metadata as read-only UNSUPPORTED and
proves native local VAR, GUI parameter VAR, mixed MTH_ARG/VAR, duplicate VAR and
malformed VAR states cannot authorize a competing parameter edit. Records are
preserved rather than translated. Public local mutation rejects the entire
mixed batch before staging; engine, source, native intent and revision/search
identity stay authoritative. Real synthetic/bridge and bodyless fixtures remain
uneditable for locals. Stale snapshots, unannotated catch names and external
input/mapping changes continue to be exercised by the retained scoped and
replacement suites. Outcome B adds no local verifier/staging path to inject
failures into; existing replacement/prefix/fault semantics are unchanged.

## Actual matching GUI measurement

`localRenameGuiDiagnosticTest` is a **negative diagnostic**, not a positive local
editing gate. It runs the actual matching 1.5.6 executable under Xvfb with explicit
separate global JSON configurations: AUTO, RESTRUCTURE, SIMPLE, FALLBACK, and AUTO
with Use dx/d8. Each run selects `probe.LocalShapes`, copies the actual editor
text with xdotool/xclip, captures a screenshot, performs GUI Save As and reopens
the resaved project headlessly under the same configuration. Logs, config,
clipboard text, screenshots and resaved projects are under
`build/local-rename-gui-diagnostic/<scenario>/`.

All ordinary native local, parameter VAR and MTH_ARG records are compared in full
after resave. FALLBACK ignores scoped names while preserving their records;
reopening in default AUTO restores the original intent. Ignoring an unmatched
key is therefore different from dropping or retargeting it. The GUI's DX editor
and reverse reopen measure the retargeted simple, pressure and wide computations.
No parameter retarget is asserted where none was observed; GUI parameter VAR
records are explicitly tested without translating them into MTH_ARG.

`JadxSettingsData` inherits `decompilationMode` and `useDx` from CLI configuration.
`JadxSettings` loads/saves that global config and converts it to JadxArgs;
`JadxProject.fillJadxArgs` supplies files, mappings, code data and plugin options.
Neither mode nor input-loader choice is a native ProjectData field. GUI Save As
preserves the VAR record but does not bind its meaning to those global settings.
A current source snapshot binds request selection; it cannot bind the meaning
of a record later reopened by a differently configured GUI.

## Pinned source audit

The 28 inspected source files and SHA-256 values in
[the source audit](pr-20-source-audit.json) match the retained archive rooted at
`jadx-28ff15e4ae69950aebea110a13e5ab895d234dfc/`.
Source is locally available at `/tmp/libjadx-pr13-source`. Key pinned links:

- [JadxCodeRef](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/data/impl/JadxCodeRef.java),
  [CodeRenameVisitor](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/visitors/rename/CodeRenameVisitor.java),
  and [JVariable](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-gui/src/main/java/jadx/gui/treemodel/JVariable.java): encoding, actual consumer and GUI VAR generation, including arguments.
- [JavaVariable](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/JavaVariable.java),
  [VarNode](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/metadata/annotations/VarNode.java),
  [SSAVar](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/instructions/args/SSAVar.java),
  and [CodeVar](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/instructions/args/CodeVar.java): metadata identity, definitions/uses, CodeVar grouping and cached representative.
- [InitCodeVariables](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/visitors/InitCodeVariables.java),
  [SSATransform](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/visitors/ssa/SSATransform.java),
  [MoveInlineVisitor](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/visitors/MoveInlineVisitor.java),
  [CodeShrinkVisitor](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/visitors/shrink/CodeShrinkVisitor.java),
  and [ProcessVariables](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/visitors/regions/variables/ProcessVariables.java): phi linking, SSA repair, shrink and declaration grouping.
- [Jadx](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/Jadx.java),
  [MethodGen](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/codegen/MethodGen.java),
  [InsnGen](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/codegen/InsnGen.java),
  [MethodNode](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/nodes/MethodNode.java),
  and [RootNode](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/nodes/RootNode.java): mode pipelines, metadata and unload/custom-pass behavior.
- [ProjectData](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-gui/src/main/java/jadx/gui/settings/data/ProjectData.java),
  [JadxProject](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-gui/src/main/java/jadx/gui/settings/JadxProject.java),
  [JadxSettings](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-gui/src/main/java/jadx/gui/settings/JadxSettings.java),
  [JadxSettingsData](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-gui/src/main/java/jadx/gui/settings/JadxSettingsData.java),
  and [JadxWrapper](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-gui/src/main/java/jadx/gui/JadxWrapper.java): native project fields versus global GUI analysis configuration.

Final commands, counts and artifacts are recorded in [the PR #20 review](pr-20-review.md).
Tiny import/export retains its existing scoped-code-ref restrictions. No custom
local identity, mode field, mapping extension, sidecar, autosave, version upgrade
or GUI dependency is added to the standalone runtime.
