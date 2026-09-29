# PR #16: verified family replay safety gate

**Outcome B: explicit propagation remains UNSUPPORTED.** PR #15 proves original
family completeness, but PR #16 finds a separate nonmember hot/fresh replay
failure in the pinned engine. No accepted `propagateRelated` field, group plan,
group transaction, new exclusive-verification interface or mapping support is
shipped. Phase 5.2 remains incomplete, including its separate local-variable
editing decision.

## Executable service counterexample

`PropagatedNativeReplayTest` uses the unchanged real `EditBatchService` and
`ProjectRuntime.ProjectEngine.reloadCodeData`, not a mock or direct diagnostic
rename. It obtains an independent COMPLETE family, then issues one ordinary
RENAME item for each original declaration. These legacy items are diagnostic:
they have empty affectedRefs and do not advertise atomic group semantics. The
separately admitted hierarchy query is historical evidence for this owned,
nonconcurrent test, not a production mutation-admission strategy.

For `Joined.joined(I)I`, all four original owners are included: ExtendedLeft,
Joined, SeparateLeft and SeparateRight. Every record is an ordinary original
METHOD `JadxNodeRef` with the complete `joined(I)I` shortId, including the return
type. No alias-derived identity, candidate-derived group or extra native field
is used. Every requested member's effective alias and actual emitted declaration
token agree after replay and explicit save/fresh reopen.

The safety failure concerns these original declarations **outside** that family:

- `Lrelated/Hierarchy$CovariantLeaf;.value()Ljava/lang/Object;` (compiler bridge)
- `Lrelated/Hierarchy$CovariantLeaf;.value()Ljava/lang/String;`

After generating the owned hierarchy source, service replay changes both
aliases from `value` to `m0value`. A fresh engine reopened from the explicitly
saved native project restores `value`. Its native rename list contains exactly
the requested family records, with no record for either nonmember. The test
compares **every raw nonmember**, including declarations suppressed from the
public Java method list. Only these two aliases differ; overloads, unrelated
same-signature methods, private declarations, static hiding and other excluded
owners retain their aliases.

A separate fresh, read-only engine generates source before the comparison.
Both nonmembers remain `value` there. This distinguishes mutation replay from
ordinary source generation. The visibility fixture's generic alias can change
from `generic2` to `generic` during ordinary codegen; the control avoids
misattributing that separate behavior to the edit.

## Replay matrix

All inputs are owned fixtures, using pinned Jadx 1.5.6. The positive controls
are observations, not advertised propagation subsets.

| Independently COMPLETE family | Requested members, emitted tokens, explicit save/fresh reopen | Nonmembers with cold legacy staging | Nonmembers after generated-owner replay |
|---|---|---|---|
| Base/Middle/Leaf/Sibling/Inner `work(I)I`, split JARs | Agree, five original records | Agree with read-only/fresh control | Two unrelated CovariantLeaf aliases differ |
| Root/Left/Right/Diamond/Implementation `call(I)I`, split JARs | Agree, five records | Agree | Same two aliases differ |
| DefaultRoot/DefaultImplementation `run(I)I` | Agree, two records | Agree | Same two aliases differ |
| SeparateLeft/ExtendedLeft/SeparateRight/Joined `joined(I)I` | Agree, all four records despite incomplete Jadx candidates | Agree | Same two aliases differ |
| Inherited ImplementationBase/Contract, no subclass declaration | Agree, two records | Agree | Agree after read-only control |
| Simple class/interface across JARs | Agree, two records | Agree | Agree after read-only control |
| Direct DEX interface/implementation across inputs | Agree, two records | Agree | Agree |

Every family seed verifies to the same independent set. Repeated explicit
member-record batches from different seeds are actual NO_CHANGE, with one
initial revision increment and no autosave. The retained counterexample does
not need attached mappings, comments, preexisting aliases or fault injection to
fail. It is smaller than those additional proposed admission requirements.
Input fingerprints remain the existing optimistic checks; no protection
against an external change-and-restore race is claimed.

## Actual GUI diagnostic

`propagatedEditReplayGuiDiagnosticTest` opens eight projects produced by actual
legacy service batches: four Joined seed positions, each cold and hot. The
matching Jadx 1.5.6 executable performs **File → Save Project As** through the
existing Xvfb/xdotool harness. Fresh headless engines reopen each GUI-resaved
project by original keys, verify all four exact records and emitted tokens, and
observe the unrelated aliases as `value`. The hot primary engine had `m0value`.

This is a passing test **of the failure**, not the required accepted
propagated-service gate. No `propagatedEditGuiRoundTripTest` success is claimed.
PR #14's `relatedPropagationGuiRoundTripTest` remains a separate candidate/record
diagnostic. Neither diagnostic invokes the GUI rename dialog. Existing native,
comment/scoped, mapping and unknown-field persistence gates are renewed
separately; no new guarantee of GUI unknown-field preservation is made.

Test artifacts:

- `build/propagated-replay-probe/{family}-{cold,hot}.json`: every original
  nonmember alias in the read-only control, primary and fresh engines.
- Matching `*-primary.java` and `*-fresh.java`: emitted source; exact declaration
  positions are asserted for every requested original member.
- `build/propagated-replay-gui-fixture/{cold0..cold3,hot0..hot3}/diagnostic.jadx`,
  `gui-resaved.jadx` and `actual-gui.log`.

## Pinned source reasoning

Pinned source revision: `28ff15e4ae69950aebea110a13e5ab895d234dfc`.
Eleven inspected files were byte-compared with fresh commit-pinned downloads;
the author-run audit is `/tmp/libjadx-pr16-source-audit.json`. The eight PR #16
mandated files include OverrideMethodVisitor, MethodOverrideAttr, MethodNode,
UserRenames, RenameVisitor, GUI JMethod, RenameDialog and JadxProject. Additional
inspection covers ClassNode, JadxDecompiler and AttributeStorage.

- [JadxDecompiler.reloadCodeData](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/api/JadxDecompiler.java)
  notifies registered code-data listeners. The existing service unloads all
  top-level owners before notifying them so source and comments are invalidated.
- [ClassNode.unloadCode/deepUnload](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/nodes/ClassNode.java)
  invalidates cached source and rebuilds class/member state. The path depends on
  whether source was already generated. `load(clsData, true)` constructs new
  MethodNodes; restoring usage data does not restore every override attribute.
- [RenameVisitor.process/checkMethods/canRename](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/visitors/rename/RenameVisitor.java)
  applies user renames and then checks method aliases across **all** classes.
  Return types are excluded from collision signatures. Its bridge guard depends
  on the current METHOD_OVERRIDE attribute and same-owner related nodes.
- [MethodOverrideAttr](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/attributes/nodes/MethodOverrideAttr.java)
  is a PinnedAttribute, so ordinary attribute unloading retains it. Rebuilding
  new MethodNodes is a different operation; the failure must not be explained
  as ordinary `unloadAttributes()` alone clearing this pinned attribute.
- [MethodNode.rename](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/nodes/MethodNode.java)
  can update every node in its current related attribute, including when called
  by automatic collision handling rather than a user record.
- [UserRenames.apply](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/visitors/rename/UserRenames.java)
  groups declaration records by original owner and calls MethodNode.rename;
  explicit record order is not an independent scope guarantee.

The executable observations establish the failure. The global collision pass
and mutable unload/override state explain why per-member records alone cannot
bound replay to the independently verified family. No upstream override
attribute is repaired, generated suffix persisted, unrelated rename invented,
or runtime dependency upgraded to hide the discrepancy.

## Public contract and follow-up

Every `propagateRelated` presence still returns `422 UNSUPPORTED_CAPABILITY`
before staging, including true, false, null, string, number, array and object.
Clean and dirty mixed-batch captures assert unchanged native data, revision,
search/source identity, disk/input hashes and cold unrelated owner state.
`edit.related_propagation` remains UNSUPPORTED with existing evidence
`INCOMPLETE_PINNED_OVERRIDE_GROUP`. Accepted OpenAPI/DTOs/examples are unchanged;
there are no successful propagated receipts. Ordinary method RENAME retains
its documented implicit candidate behavior and empty affectedRefs. Mapping
import/export support is unchanged.

The smallest follow-up is an isolated engine-replay feasibility slice that
preserves all nonmember aliases and requested member tokens across generated
owners, fresh state, native save and GUI resave. A rebuild from immutable native
state or a narrowly proved replay ordering may be investigated; neither is
implemented or claimed here. If it passes, group verification and staging must
still share one current exclusive lease, with revision/input/mapping checks,
all-owner collision preflight, private item staging, truthful prefix faults and
exact original affectedRefs. A global exclusion for unrelated bridge owners
would be a different admission rule, requiring its own explicit evidence.

PR #16 stops dependent accepted-controller/contract work at this mandatory
safety gate, as instructed. Local-variable editing and permission to proceed to
Phase 6 remain separate human decisions. Exact final commands/counts are in
[PR #16 review](pr-16-review.md).

## PR #17 safe replay feasibility

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
