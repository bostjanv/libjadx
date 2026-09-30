# PR #14: related-method propagation feasibility

Outcome: **B, evidence-only; explicit propagation remains UNSUPPORTED**.
Jadx 1.5.6, source commit `28ff15e4ae69950aebea110a13e5ab895d234dfc`,
cannot establish the required completeness through its related-method attributes.
This is an executable counterexample to the proposed candidate-based adapter,
not a claim that every future independent hierarchy verifier is impossible.
No production `RelatedMethodPlan`, propagation transaction or new mutation route
is shipped. Phase 5.2 remains incomplete; local editing is a separate open gate.

PR #15 subsequently proves the conservative closed-input family independently.
PR #16 extends actual service replay and finds a separate nonmember alias
failure despite writing every verified declaration record. Explicit propagation
still remains disabled. The PR #14 all-member GUI result below only establishes
requested-member persistence; it does not prove unchanged nonmembers. See
[PR #16 replay matrix](phase-5-propagated-edits.md).

## Original hierarchy evidence

The independently owned [Hierarchy.java](../tests/fixtures/related/Hierarchy.java)
compiles into two JARs. It covers an abstract base, intermediate, leaf, sibling,
inner override, overload, private same-name declarations, static hiding,
unrelated declarations, interface implementations, defaults, extended interfaces,
a shared-root diamond, independent interface branches, covariant return and
compiler bridge, missing parent and external Runnable. It uses no upstream test
or libghidra material. `RelatedFixture` deliberately excludes MissingRoot and
can add a distinct duplicate Leaf definition with a method absent from the
retained input. Tests use original full descriptors, never display-name matching.

All owners below are original `Lrelated/Hierarchy$<owner>;` descriptors.
All names are original names; return descriptors are part of each identity.

| Original method | Owned original declarations | Observed candidate group from each seed |
|---|---|---|
| `work(I)I` | Base, Middle, Leaf, Sibling, Inner | All five, including seed |
| `run(I)I` | DefaultRoot, DefaultImplementation | Both, including seed |
| `call(I)I` | Root, Left, Right, Diamond, Implementation | All five, including seed |
| `joined(I)I` | SeparateLeft, ExtendedLeft, SeparateRight, Joined | SeparateLeft/ExtendedLeft/Joined seeds return those three; SeparateRight returns empty |
| `value()Ljava/lang/Object;`, `value()Ljava/lang/String;` | CovariantBase Object; CovariantLeaf Object bridge and String declaration | All three original refs; one is synthetic/bridge and uneditable |
| `hidden(I)I`, `hiding(I)I`, `work(Ljava/lang/String;)I` | Private/static/overloaded declarations | Empty, excluded from override group |
| Unrelated `work(I)I` | Unrelated | Empty |
| MissingParent `lost(I)I` | MissingParent plus omitted MissingRoot | Empty, no override attribute and no DONT_RENAME |
| External `run()V` | External plus classpath Runnable | One local candidate; unresolved IMethodDetails and DONT_RENAME |

The independent-branch example is fully local and resolved. Joined implements
ExtendedLeft and SeparateRight, and both require exactly `joined(I)I`.
Its override list contains only MethodNodes, and neither the omitted declaration
nor the seed has DONT_RENAME. Starting from SeparateRight gives a different
group. Direct `MethodNode.rename` leaves SeparateRight unchanged. A nonempty
list, a resolved ancestor list and absence of DONT_RENAME therefore cannot certify
completeness, even for a small hierarchy without bridges or missing parents.

The duplicate-input probe preserves the same five visible `work(I)I` candidates
after adding a different original Leaf definition. Jadx exposes one Leaf;
`onlyInDiscardedInput()I` disappears. Upstream emits a duplicate warning, but
the candidate refs themselves carry no original-input census or ambiguity marker.
We do not invent a per-input identity. Simple chain and shared-root diamond
successes are fixture observations, not a production closed-world admission rule.

`JadxRelatedMethodProbeTest` writes the complete observed original-ref/flags/group
census to `build/related-probe/original-groups.txt`. Discovery uses raw declaration
metadata through `getJavaNodeByRef`, without `JavaClass.getMethods()` codegen.
The unrelated class remains NOT_LOADED. The measured owned census contains 23 owners and 44 declarations, with a
largest observed candidate group of five; this does not establish a general project scan budget or original-input
coverage. The existing 64-entry affectedRefs cap is unchanged; no results are
truncated or advertised as complete.

## Pinned source reasoning

The exact source archive inspected locally is `/tmp/libjadx-pr13-source` at
the pinned commit. All eight mandated files were byte-compared with fresh
commit-pinned upstream downloads; the audit is `/tmp/libjadx-pr14-source-audit.json`.
These are the relevant upstream paths and mechanisms:

- [JavaMethod.getOverrideRelatedMethods](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/JavaMethod.java)
  converts the attribute's nodes; no completeness validation occurs.
- [MethodOverrideAttr](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/attributes/nodes/MethodOverrideAttr.java)
  holds mutable related nodes and override/base lists.
- [OverrideMethodVisitor.processOverrideMethods](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/visitors/OverrideMethodVisitor.java)
  returns after finding an already processed ancestor attribute, before scanning
  later supertype branches. Shared sets merge visited branches, not necessarily
  every original declaration. Unknown supertypes are treated as hierarchy ends;
  DONT_RENAME is set for unresolved **found methods**, not every missing type.
- [MethodNode.rename](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/nodes/MethodNode.java)
  sets aliases on related nodes when an attribute exists; otherwise only itself.
- [UserRenames](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/visitors/rename/UserRenames.java)
  groups original-key records by owner and applies MethodNode.rename. It supplies
  no conflict preflight; differing member aliases compete and duplicate same-owner
  keys replay in list order. Probes avoid promising cross-owner iteration order.
- [RenameVisitor](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/visitors/rename/RenameVisitor.java)
  applies user aliases then checks generated aliases/collisions; automatic aliases
  are not evidence of a native conflict-free final group.
- [Jadx](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/Jadx.java)
  installs OverrideMethodVisitor in the pre-decompile stage before RenameVisitor.
- [ClassNode](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/nodes/ClassNode.java)
  deepUnload reconstructs methods. [ProcessClass](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/ProcessClass.java)
  reruns class prepasses on demand; global code-data replay alone is not a
  settings/cache-independent exhaustive hierarchy rebuild.
- [RootNode.finishClassLoad](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/nodes/RootNode.java)
  reduces duplicate classes to selected map values before override passes.
- [GUI JMethod.buildCodeRename](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-gui/src/main/java/jadx/gui/treemodel/JMethod.java)
  removes candidate declaration records, then creates one seed record.
  The headless builder probe shows the omitted branch record remains and MTH_ARG
  and VAR keys survive. This is a builder probe, not actual GUI interaction.
  [RenameDialog](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-gui/src/main/java/jadx/gui/ui/dialog/RenameDialog.java)
  invokes rename actions/events; Save As alone does not invoke the rename builder.

## Native and actual GUI diagnostics

`RelatedNativeReplayProbeTest` creates two native diagnostic projects using owned
fixture membership, **bypassing the disabled API**. Each has attached Tiny alias
`SeparateRight.joined(I)I -> mappedRight`, unrelated saved class rename/comment,
and unrelated original-method MTH_ARG rename. No group members are guessed by name.

| Native strategy | Hot unload/replay in this diagnostic | Explicit save / fresh headless engine |
|---|---|---|
| One Joined original-key declaration record | Joined renamed; SeparateLeft and ExtendedLeft retain joined; SeparateRight retains mappedRight | Three candidate members renamed; SeparateRight retains mappedRight |
| One record for each of the four owned original refs | All four renamed | All four renamed |

Initial optimistic hot-replay assertions failed on SeparateLeft. The retained
regression now asserts the actual differing behavior; it does not normalize the
result into a successful group operation. Each test checks original-key aliases
after Java generation, native record keys/counts, comments, parameter name, private
and static names, relative input/mapping attachment, both JARs and mapping bytes.
Native bytes remain unchanged until explicit save. Headless unknown JSON fields
survive. No persistence strategy is adopted in production.

`relatedPropagationGuiRoundTripTest` drives the existing Xvfb/xdotool automation
twice using the actual matching 1.5.6 GUI, opens each project, performs Save As,
and checks every owned original key on a fresh headless engine. This is a
**diagnostic counterexample round trip**, not the successful propagated-service
gate described for outcome A. Execution details and record canonicalization are
recorded in [PR #14 review](pr-14-review.md). Both actual GUI Save As runs passed:
one seed record and four member records were preserved without group
canonicalization; the omitted branch still has mappedRight in the seed strategy.
GUI serialization drops the unknown root/code-data members, which headless save
preserves. A normal opt-in skip is no GUI proof.

## Public behavior and zero staging

The existing request schema still excludes propagateRelated. Presence of that
field, including false, null or a malformed string, retains the legacy
`422 UNSUPPORTED_CAPABILITY` rejection. Omitting it preserves ordinary RENAME.
The new reviewed negative request is intentionally invalid against EditBatchRequest;
the unsupported response is valid against ErrorEnvelope. The capability remains
UNSUPPORTED with evidence `INCOMPLETE_PINNED_OVERRIDE_GROUP`.

`RelatedPropagationEndpointsTest` captures eight actual rejection responses,
checks the failing mixed-batch item's index, and compares clean and dirty project
snapshots, pending native data, logical/index revisions, search identity, source
snapshot, cold unrelated owner and native/input hashes before/after rejection.
The existing scoped snapshot/catch and Tiny import/export protections are retained.
Ordinary method RENAME already invokes Jadx's implicit candidate alias behavior;
the legacy regression records three changed candidate aliases and the unchanged
omitted branch. Its affectedRefs remains empty and makes no exhaustiveness claim.

## Smallest follow-up

PR #15 now implements and proves that bounded original-input census and
independent hierarchy verifier for a conservative class/JAR/DEX subset. It
detects this document's four-versus-three/empty candidate omission without
modifying live override attributes. See [hierarchy verifier evidence](phase-5-hierarchy-verifier.md)
and [PR #15 review](pr-15-review.md). Public propagation remains unsupported.
The following discovery work is historical PR #14 guidance; all-owner native
transactions and actual propagated service/GUI persistence are now PR #16.

Before enabling any subset, implement and prove a bounded original-input census
and independent hierarchy completeness check, accounting for discarded originals,
all resolved branches, visibility, bridges and cache reload. Compare it with Jadx's
candidate attributes and fail closed on disagreement. Then prove all-owner conflict
preflight, group-private staging/prefix failure semantics and an actual propagated
**service** Save As/restart gate. This preserves the approved architecture and
native formats; no upstream fork, dependency upgrade or sidecar is proposed.
This PR stops dependent implementation at the failed completeness gate. There are
no propagated success/no-op/partial responses to validate. Phase 6 still requires
human approval of the remaining Phase 5.2 exit interpretation.

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
failures, oracle, persistence and validation. Related propagation and local
editing remain unsupported. PR #19 still requires same-lease COMPLETE verification,
immutable group plans, all-owner collision admission, exact original native
records and affectedRefs, and propagated HTTP/native/restart/actual-GUI gates.
