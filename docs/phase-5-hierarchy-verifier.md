# PR #15: bounded original-input census and independent hierarchy verifier

Jadx **1.5.6**, source commit **28ff15e4ae69950aebea110a13e5ab895d234dfc**.
This is an internal Phase 5.2 prerequisite. It exposes no HTTP endpoint or
accepted mutation, and `edit.related_propagation` remains UNSUPPORTED.
Every `propagateRelated` field still returns 422 before staging. Ordinary
method RENAME retains its previous candidate alias behavior and empty affectedRefs.

## Raw input authority

`JadxInputCensusAdapter` copies original metadata before loading the primary
engine, using the same validated, ordered input paths. It uses public input-API
`IClassData`, `ISeqConsumer<IMethodData>` and loaded `IMethodRef` values, behind
two pinned internal reader entry points:

- [`JavaClassReader.loadClassData()`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-plugins/jadx-java-input/src/main/java/jadx/plugins/input/java/JavaClassReader.java),
  followed by [`JavaClassData.visitFieldsAndMethods()`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-plugins/jadx-java-input/src/main/java/jadx/plugins/input/java/data/JavaClassData.java).
- [`DexFileLoader.loadDexReaders()`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-plugins/jadx-dex-input/src/main/java/jadx/plugins/input/dex/DexFileLoader.java),
  `DexReader.getHeader().getClassDefsSize()` and `DexReader.visitClasses()`, followed
  by `DexClassData.visitFieldsAndMethods()`. `DexInputOptions.setOptions(emptyMap)`
  initializes the upstream default **enabled checksum validation**.

Bulk [`JavaLoadResult.visitClasses()`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-plugins/jadx-java-input/src/main/java/jadx/plugins/input/java/JavaLoadResult.java)
and `JavaInputLoader.collectFiles()` catch/log exceptions and skip broken
definitions. They cannot certify completeness. The adapter instead reads each
bounded class payload directly, allowing errors and budget exhaustion to escape
to an incomplete capture. It does not implement classfile or DEX parsing.
JAR enumeration uses Jadx `ZipReader` with its default security. Magic checks
recognize payload kinds, including DEX entries; nested ZIP/JAR envelopes refuse
the entire capture. The Java plugin's `META-INF/versions/` exclusion is preserved.

Actually exercised: individual JVM `.class`, two configured JVM JARs, two direct
DEX files, interface/implementation declarations split across DEX files, and
DEX payloads in two JAR envelopes. Owned smali is assembled by the existing
test-runtime dependency to generate real DEX; this is not census support for
Smali input. APK/AAB/XAPK/APKM/APKS, directories, nested archives and Java-to-DEX
conversion remain outside this census subset. Ordinary Jadx input support
continues to work, with internal UNSUPPORTED_INPUT verification for these cases.
No artifact version or runtime dependency changes; existing JVM/DEX input
artifacts gain compile visibility and corresponding lock membership.

## Duplicate proof and immutable graph

[`RootNode.loadClasses` / `finishClassLoad` / `fixDuplicatedClasses`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/nodes/RootNode.java)
receives all raw definitions, selects duplicates, and replaces the active list
with map values. `RawInputCensusProbeTest` demonstrates both raw Leaf definitions
while the visible model contains one and loses `onlyInDiscardedInput()I`.
`InputCensusTest` extends this to divergent class method sets, identical method
keys with different access/superclass metadata, duplicate interfaces, and edges
present only in discarded definitions.

[`SelectFromDuplicates`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/nodes/utils/SelectFromDuplicates.java)
selects the first ordinary JAR definition. Direct DEX source labels are absolute
paths, so the owned direct-DEX duplicate also selects the first. Embedded DEX
labels are `classes2.dex` / `classes.dex`; the later `classes.dex` wins. Both
definitions remain in the census in every case. Configured origin is the
canonical path plus its position in the startup list. Multiple definitions in
one configured input are still retained, but origin does not identify archive
entries. No origin is assigned to public `SymbolRef.inputIdentity`.

Core `InputCensus` records retain only descriptors, original method names/full
descriptors, access bits and immutable origins. Argument/return keys are derived
from each full method descriptor. `HierarchyGraph` retains all forward and
reverse edges, including every duplicate's divergent edges. Targets are
IN_CENSUS_UNIQUE, IN_CENSUS_DUPLICATE, EXTERNAL_CLASSPATH or MISSING. Global census
failure/unsupported input/resource exhaustion prevents building a usable graph.
No IClassData, callbacks, readers, ClassNodes, MethodNodes, code bodies, source,
aliases, mappings, instructions, ASTs or native project copies are retained.

## Supported completeness rule

`RelatedHierarchyVerifier.verify(SymbolRef, VerificationBudget)` returns an
immutable status, seed, sorted complete original refs and at most 16 bounded
diagnostics. Only COMPLETE has members. All other statuses have an empty list,
including RESOURCE_LIMIT; there is no advertised truncated family.

The supported subset uses original JVM/Dalvik instance override signatures:
original name plus exact argument descriptors, with full return descriptors
retained in identities. Private/static methods, constructors, initializers and
synthetic/bridge seeds are unsupported. Abstract declarations and default
interface methods are included. Public/protected inheritance and original
package-private visibility are checked. Original package boundaries determine
visibility even after aliases. Reduced visibility, conflicting static/private
declarations, cycles, illegal edge kinds, inaccessible parents and final
class/method violations refuse completeness.

The verifier walks the entire structural component through forward and reverse
edges, excluding only explicitly modeled Object terminals. This is deliberately
conservative: a missing/external/duplicate branch anywhere in that component can
block a seed even if narrower signature-specific reasoning might prove it
irrelevant. Every member seed sees the same structural component.

At every inheritance context, it computes accessible declarations in ancestors
and the current owner. It connects actual overriding declarations and public
inherited class implementations of interface declarations, including contexts
that declare no method. This joins independent interface branches without using
Jadx candidate sets. Siblings with no shared declaration stay separate, as do
unrelated same-name methods and different-argument overloads. A final overriding
leaf declaration may join its ancestors; overriding an already final declaration
is invalid.

The initial subset requires identical return descriptors along each connected
method family. Multiple return descriptors in one owner, covariant/incompatible
returns and any same-name bridge/synthetic declaration in the structural
component return UNSUPPORTED_METHOD. Checking same name across argument keys
also catches generic bridges that connect erased Object arguments to String
arguments. We reject entire families rather than guess editable bridge refs.
PR #14's native/GUI probes do not establish bridge editing; no new such guarantee
is made here.

`java.lang.Object` is explicitly classified EXTERNAL_CLASSPATH. The adapter
copies its override signatures from the pinned bundled `ClspClass.methodsMap`.
Only a signature proved absent from that nonempty table permits Object to be a
terminal. `toString`, `equals`, `hashCode`, etc. remain external and unverifiable.
Other external types refuse completeness even when they appear not to declare
the signature. No reflection, generated-source or call-graph inference is used.

The inspected [`ClsSet.processMethodDetails`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/clsp/ClsSet.java)
excludes private/synthetic/bridge declarations but retains public/protected and
final methods. This is why the Object table can serve as a signature boundary
for this nonsynthetic instance subset; it is not a generic classpath census.

## Fixtures and Jadx comparison

`IndependentHierarchyVerifierTest` checks exact sets and every member seed.
Existing `RelatedFixture`/`Hierarchy.java` is expanded by owned
`tests/fixtures/hierarchy/p/Visibility.java`, `q/Other.java`, small generated Java
fixtures and owned assembled DEX. Full descriptors are asserted throughout.

| Family / boundary | Independent result |
|---|---|
| Abstract Base, Middle, Leaf, Sibling, inner subclass `work(I)I` | COMPLETE, five declarations; unrelated work and overload excluded |
| Root/Left/Right/Diamond/Implementation `call(I)I` | COMPLETE, five declarations |
| DefaultRoot/DefaultImplementation `run(I)I` | COMPLETE, two declarations |
| SeparateLeft/ExtendedLeft/SeparateRight/Joined `joined(I)I` | COMPLETE, all four from every seed |
| Simple interface/implementation across two JARs | COMPLETE, two declarations |
| Public inherited class implementation plus interface, no subclass declaration | COMPLETE, two original declarations |
| Package-private Base/Same in one package | COMPLETE, two declarations |
| Same-name method in different-package Other | COMPLETE singleton, separate from package-private family |
| Protected Base, Other and final leaf across packages | COMPLETE, three declarations |
| Final method and overloads | Separate COMPLETE singletons |
| Two sibling declarations without a shared ancestor method | Separate COMPLETE singletons |
| Covariant Object/String and compiler bridge; generic Object/String bridge | UNSUPPORTED_METHOD, no affected refs |
| Private/static/special/synthetic method seeds | UNSUPPORTED_METHOD |
| Omitted superclass/interface, including a descendant's omitted branch | MISSING_SUPERTYPE |
| Runnable or a method supplied by Object | EXTERNAL_SUPERTYPE |
| Duplicate class/interface, including a nonseed or discarded branch | AMBIGUOUS_INPUT |
| Real DEX interface/implementation split across two inputs | COMPLETE, two declarations |
| Family of 64 / 65 declarations | COMPLETE 64 / RESOURCE_LIMIT with no members |
| Cyclic/final/visibility/edge violations | INVALID_HIERARCHY or conservative unsupported refusal |

`HierarchyComparison` compares afterward and distinguishes exact agreement,
missing members, extra members, seed dependence, bridge/synthetic differences
and independent incompleteness. It is diagnostic, never correctness authority.
Chain/default/shared-root-diamond agree with pinned Jadx. Joined's three seeds
have {SeparateLeft, ExtendedLeft, Joined}; SeparateRight has an empty set.
Comparison is JADX_CANDIDATE_MISMATCH with MISSING_MEMBERS and SEED_DEPENDENT.
Singletons are compared literally with Jadx's empty no-attribute candidate set;
this is a diagnostic difference, not independent incompleteness. All supported
visibility/inherited-interface/DEX comparisons are recorded in
`build/hierarchy-probe/comparisons.txt`. No live override attribute is repaired.

The inherited ImplementationBase/Contract family is another candidate omission:
the independent two-declaration family is complete, while both Jadx seeds return
empty candidate sets. Protected/package-private groups and simple JVM/DEX
interfaces agree; literal singleton-versus-empty differences are recorded too.

## Admission and memory

Internal defaults are supplied by `CensusLimits`; tests can supply smaller hard
limits. Public configuration/schema is unchanged.

| Limit | Default |
|---|---:|
| Configured input files | 16 |
| Raw class definitions, including duplicates | 20,000 |
| Methods per raw class | 2,000 |
| Total declared methods | 100,000 |
| Raw hierarchy edges | 80,000 |
| Definitions for one descriptor | 8 |
| Enumerated archive entries | 100,000 |
| Input file or expanded code payload | 32 MiB |
| Total admitted input bytes plus expanded JAR code bytes | 128 MiB |
| Copied declaration/origin characters | 8,000,000 |
| Complete family | 64 maximum, caller may lower |
| Structural visited nodes | 10,000 |
| Traversal work units | 200,000 |

Class/method header counts are checked before declaration visitation, and every
accepted callback is checked again. Duplicates consume full class/method/edge
budget. A parse failure refuses the entire capture. Boundary tests cover every
census limit at the exact inclusive limit and one less, plus family/node/work
limits. Traversal counts queue, edge, declaration, ancestor and comparison work;
it never truncates. Symbol descriptors must also fit existing SymbolRef limits.

A conservative planning estimate for retained census/graph is below 256 MiB:
16 bytes per admitted character, 1 KiB/class, 256 bytes/method and 128 bytes/edge
plus reverse maps and collection overhead. This is an estimate, not a measured
JVM heap guarantee. Only one bounded code payload is parsed at a time; expanded
entry assembly and upstream transient attributes may require roughly another
128 MiB. Per-verification closure/pair work is bounded by the work budget; it
uses no shared mutable cache. Parser allocations and archive directory parsing
remain subject to upstream behavior within the admitted file-byte cap.

## Lifecycle and persistence

The actual production engine constructs the census during the existing async
initialization, after path validation and before primary Jadx load. HTTP already
listens and reports LOADING during this work. An incomplete census does not stop
ordinary service loading; it leaves this internal capability incomplete.
Only checksum/metadata loading occurs; unrelated primary owners stay NOT_LOADED.

Capture fingerprints use the existing `FileFingerprint` content hashes before
and after census reading, after primary load, and before/after every verification.
The capture also rechecks canonical path identity before and after reading; an
input replaced by a symlink after startup validation is rejected without adopting
its new target. All caller-supplied input paths must already be canonicalized by
the project's path policy.
Repeated occurrences of a canonical input must have the same content baseline;
a later read cannot overwrite evidence that an earlier occurrence changed.
The binding also checks ordered primary input paths and refuses conversion mode.
Detected change yields INPUT_CHANGED until explicit reload constructs a fresh
capture. These optimistic checks do not eliminate a change-and-restore race with
uncooperative external writers. Verification may synchronously hash up to the
admitted file budget; there is no invisible background index job or autosave.

`ProjectRuntime.withHierarchyVerifier` uses QUERY_READ admission. Concurrent
reads of the same immutable state are allowed; exclusive native edits, save,
reload and requested shutdown conflict while a read lease is active. Rebuilds
construct a new census even if only mappings changed. Native declaration edits
and explicit save preserve the current capture; aliases/comments/mappings and
decompilation modes never become raw identity. Process restart constructs fresh
state under a new session. Census interruption is cooperative at input/entry/
class/method boundaries; construction is not a separately cancellable job and
no hard JVM interruption guarantee is added.

The callback receives a handle confined to its admitting thread and callback
lifetime. Use from another thread or after either normal return or callback
failure throws `IllegalStateException`, before reading inputs or the captured
verifier. Expiration drops the handle's reference to that verifier before the
query lease is released. Concurrent reads use separate admitted callbacks.
Retaining a handle across an unchanged-input reload therefore cannot reuse a
superseded engine's verifier. Returned immutable COMPLETE results are historical
evidence, not authority to stage edits after admission ends.

`HierarchyVerifierLifecycleTest` checks concurrent readers, busy reload/shutdown,
explicit reload, changed input content, fresh runtime/session reconstruction,
pending declaration edits, explicit save, mapping rebuild and all four analysis
modes. Disk hashes, pending native data, revisions, search identity and cold
class states are checked. No native/input file is written by verification and
no derived state is serialized. Existing actual GUI gates remain required and
are recorded separately in [PR #15 review](pr-15-review.md).
Regression tests also cover escaped/cross-thread handles, unchanged-input reload
and callback-failure expiration/admission release.

## Next boundary

PR #16 investigated this boundary and delivered required outcome B: explicit
per-member records correctly persist the independent families but hot native
replay also changes unrelated bridge aliases. The unchanged verifier remains
valid evidence of original family membership; COMPLETE is insufficient to
guarantee safe engine replay. See [executable nonmember failure](phase-5-propagated-edits.md).
The remaining requirements below apply to a follow-up after that blocker is
resolved; no accepted propagation contract or exclusive verification API shipped.

PR #16 can consume this internal verifier only on COMPLETE. It must still prove
verification and native staging under one current project-exclusive lease with
the current session/engine and expected revision checked. A cached COMPLETE
result or a separately admitted query is insufficient for mutation admission.
It must also prove all-owner alias collisions, group-private native records,
exact affectedRefs,
group-size admission, no-op/prefix/partial failure semantics, explicit-save,
headless restart/discard and actual matching-GUI propagated service transactions.
The existing native hot/fresh discrepancy also needs a reliable replay strategy.
Only those gates can justify an accepted propagateRelated field or capability
change. Local edits and parameter propagation remain separate unsupported work.
No architectural change or human decision is needed for this prerequisite.
