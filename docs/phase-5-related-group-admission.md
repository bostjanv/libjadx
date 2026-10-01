# Verified related-method rename group admission (PR #19)

Pinned Jadx **1.5.6**, source **28ff15e4ae69950aebea110a13e5ab895d234dfc**.
PR #19 consumes PR #15's independent closed-input verifier and PR #18's fresh
replacement publication. Historical candidate incompleteness and in-place replay
counterexamples remain unchanged. This is a conservative verified subset;
PR #20 closes Phase 5.2 with local-variable editing deliberately unsupported;
the Phase 6 Python release remains open. See
[local feasibility](phase-5-local-rename-feasibility.md).

## Admission and identity

`propagateRelated` is optional on `RENAME` only. Omitted and `false` retain ordinary
behavior: one requested native record and empty `affectedRefs`. `true` requires
an original full METHOD descriptor, no input identity, and both
`expectedSessionId` and `expectedLogicalRevision`. Malformed flags, nonmethod
propagation, missing preconditions and unknown operation properties return
400 INVALID_REQUEST. Comments and parameter operations exclude the property.

Under one `ProjectRuntime.withExclusiveEdit` callback, the service captures the
snapshot, rechecks preconditions, resolves the original seed in the captured
engine, and synchronously calls `EditContext.verifyRelated`. The context is
confined to the admitting thread and callback; expiration occurs on return or
failure, before lease release. Verification after commit is also prohibited.
No raw verifier or query-derived COMPLETE result enters mutation admission.
There is no cross-admission/revision cache or nested query admission.

Only COMPLETE advances. The existing verifier subset is unchanged: original
instance declarations with identical full returns, including abstract/default
interfaces, chains, diamonds, independent interface branches, package/protected
visibility and inherited interface implementations in understood class/JAR/DEX
inputs. Every supported member is independently seed-equivalent. Static/private,
special, synthetic/bridge seeds, covariant/bridge families, duplicates, malformed
hierarchies, missing/external branches and unsupported input census fail closed.
Object is terminal only under PR #15's explicit pinned-classpath rule.

Families contain 1–64 distinct original METHOD refs, no input identities, and
exactly one seed. Order is original class descriptor, original name, full original
descriptor. Every member resolves to one current editable native key with exact
`originalName + originalDescriptor` short ID. Missing, hidden/uneditable or
ambiguous family members reject the entire item. The immutable `RelatedMethodPlan`
copies strings and refs, creating fresh JadxNodeRefs when staging; it retains no
live Jadx objects or mutable keys. Duplicate native declaration rename records
for any member return 422 INVALID_ENTITY_ID. Mixed prior aliases may normalize;
derived display aliases do not shrink the family.

## Bounds and error mapping

Per verification: 64 family members, 10000 visited nodes, 200000 work units.
Per batch: four propagated items, 128 total verified members, 800000 reserved
work units. Each attempt reserves its full 200000 allowance, including no-op
items; this upper bound requires no cross-request cache. Raw collision inventories
have a shared 200000-method cap. Exhaustion rejects with 429; nothing truncates.
The internal immutable verification now reports consumed work for measurements.
Capability `edit.related_propagation` is PARTIAL, meaning this conservative subset,
with `INDEPENDENT_CLOSED_INPUT_FAMILY_GUI_VERIFIED` evidence and
`MEMORY_ONLY_UNTIL_EXPLICIT_NATIVE_SAVE` persistence. Admitted families themselves
are always complete; no partial member set is published.

| Verifier status | HTTP / code |
|---|---|
| COMPLETE | Continue exact native admission |
| NOT_FOUND | 404 NOT_FOUND |
| AMBIGUOUS_INPUT | 422 INVALID_ENTITY_ID |
| MISSING_SUPERTYPE, EXTERNAL_SUPERTYPE | 422 UNSUPPORTED_CAPABILITY |
| UNSUPPORTED_METHOD, UNSUPPORTED_INPUT, INVALID_HIERARCHY | 422 UNSUPPORTED_CAPABILITY |
| RESOURCE_LIMIT | 429 RESOURCE_LIMIT |
| INPUT_CHANGED | 409 EXTERNAL_MODIFICATION_CONFLICT |
| FAILED or unexpected verifier exception | 500 INTERNAL_ERROR |

Errors identify the request item through `itemErrors[index]` and expose bounded
categories, without internal paths, census origins or stack traces. Global stale
revision errors retain the existing batch-level STALE_REVISION classification.

## Full-batch and raw collision planning

All groups expand before ordinary item validation or any staging. Overlapping
propagated groups, repeated family seeds, ordinary rename of a group member,
contradictory/duplicate native rename keys and parameter edits on group members
reject regardless of item ordering. Class renames and ordinary METHOD renames
cannot share a batch with a group, including disjoint and no-op method renames;
400 INVALID_REQUEST identifies the excluded operation. Separate batches retain
ordinary class/method editing. Field renames and declaration comments may coexist.

Pinned `UserRenames.apply` calls `MethodNode.rename`, which can alias every current
METHOD_OVERRIDE candidate in another owner. A proposed-name map of explicit
operation targets cannot bound those effects; candidates remain incomplete and
processing-sensitive. The review regression uses `P.foo` / `C.foo` and `B.bar` /
`C.bar`: an ordinary rename on P can alias C.foo, colliding with a propagated bar
family in C. Both batch orders reject before staging, without changing intent,
revisions or the engine. Ordinary rename alone retains its existing implicit
behavior. No candidate relation is promoted to group-admission authority.

Each family owner's ClassNode method inventory includes raw synthetic/bridge and
compiler-generated declarations, including those excluded by the public Java
list. Original full descriptors remain identity; Java collision signatures are
candidate name plus descriptor through `)`, excluding return type. Raw methods
that cannot be represented safely block admission. Duplicate explicit blocker
aliases also fail closed. Names use proposed batch aliases, otherwise explicit
native aliases, accepted attached mapping aliases, then current derived aliases
for comparison only. Current names also block obstacle-removal batches so every
possible committed prefix remains collision-free. Post-batch collisions are
checked even when another item creates the obstacle, in either order.

The real generic JVM fixture has a bridge `hidden(Object)Object` and a visible
`hidden(String)String`. Renaming `f(Object)I` to `hidden` rejects on the bridge's
argument signature, despite different returns. A test-only DONT_UNLOAD_CLASS flag
retains actual codegen DONT_GENERATE flags so the public list demonstrably omits
the compiler bridge. Production uses the raw list in both loaded/unloaded cases;
there is no production flag change or filter-dependent collision authority.

## Staging, publication and receipts

One group is one logical item. The item deep-copies working code data, writes one
standard native declaration rename for each member, and advances working state
only after every member succeeds. It never calls MethodNode.rename directly or
adds group metadata. An injected failure before first, middle or final member
cannot leave any group record in the published prefix. Earlier successful items
may publish once; the failed item has FAILED, later items SKIPPED. Replacement
failure publishes nothing and returns no APPLIED receipt.

The ordinary `EditContext.commit` loads a complete private fresh production
engine, checks accepted input/mapping bytes, commits once and atomically publishes
under the same exclusive lease. Logical/index/publication counters advance once
for an effective batch/prefix. Source snapshots and cursors become stale; source,
search and references use the replacement without mixed old/new state. Automatic
aliases remain derived under PR #18's approved semantics; explicit native and
mapping intent remain authoritative.

No-op requires every family member already explicitly renamed to newName. Missing
records still stage even if derived aliases match. Verified no-op returns overall
NO_CHANGE, item SKIPPED/NO_CHANGE and the exact family; it constructs no replacement
and changes no revisions, dirty state or native records. APPLIED and verified
NO_CHANGE have exact deterministic original `affectedRefs`. Failed/unexecuted
groups and every ordinary edit have empty lists.

Accepted inputs and attached mappings are checked before verification, after
planning and again around effective replacement publication. Changes before or
during load fail closed with 409, preserving the previous engine, pending data
and revisions; explicit reload accepts new baselines. Propagated no-op also
checks baselines because reporting COMPLETE requires current hierarchy evidence.
The existing optimistic change/restore and post-final-check race limits remain.

## Persistence and actual GUI gate

Unsaved intent is memory-only: original project/input/mapping bytes remain
unchanged; restart or discard/reload removes it. Explicit native save writes one
ordinary Jadx method record per member, with no sidecar/group field. Saved restart
restores aliases and original family membership. Strict Tiny v2 export contains
every family method; import stages ordinary records without inferring a group.
A later verified request may normalize those records or return verified NO_CHANGE.

`propagatedEditGuiRoundTripTest` is a positive service-produced gate, distinct from
historical diagnostics. For **all four Joined seeds**, preparation runs real
runtime/service edits after source generation on an owned relative-path project,
retains unrelated native/scoped/VAR/comments/mapping state, compares the fresh
oracle, verifies unchanged bytes before explicit save, then saves. The actual
matching 1.5.6 GUI opens and Save As resaves each project. Fresh reopen checks four
explicit records, all original aliases and declaration tokens, unrelated state
and matching fresh-derived bridge aliases. Headless unknown fields survive;
matching GUI unknown-field loss is observed separately.

Positive service fixtures also include five-member chains/diamonds, default,
package/protected, inherited interface, split JVM inputs and direct DEX families,
plus inclusive 64-member/128-total bounds. Each admitted family is verified from
every member seed. The public Java list's omitted default constructor may depend
on lazy read order; full metadata oracles mirror current-revision admission reads
in both fresh and active engines. Raw declarations, aliases and emitted source
are compared exactly; no constructor identity or explicit intent is normalized
away. Costs are recorded in `build/related-group-probe/performance.json`; these
small owned fixtures are observations, not large-project guarantees.

Final-head commands, actual GUI outcomes and remaining limitations are recorded
in [PR #19 review](pr-19-review.md). No dependency upgrade, upload, remote bind,
project switching, journal, autosave, persistent cache or Python release work.
