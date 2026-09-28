# Phase 5.2: strict Tiny v2 mapping import and merge

This is the bounded import sub-slice of Phase 5.2. Parameter/local editing,
related-method propagation, arbitrary comment reconstruction and Python SDK
generation remain deferred. Mapping attachment through settings is separate:
import converts supported declarations into pending native edits and never
changes `mappingsPath`, copies inputs, creates sidecars or calls native save.

The checkout base was confirmed as merged PR #11,
`6da173126a3b5a67fcc50b32400577230e19aab5`. Jadx remains 1.5.6, source commit
`28ff15e4ae69950aebea110a13e5ab895d234dfc`, mapping-io 0.8.0 and JDK 21.
No dependency or lock changed. The existing user change in
`docs/phase-5-editing.md` was retained.

## Contract and supported merge

`POST /api/v1/project/mappings/import` requires five fields, no extras:

```json
{
  "sourcePath": "/absolute/allowed/incoming.tiny",
  "format": "TINY_V2",
  "mode": "MERGE_FAIL_ON_CONFLICT",
  "expectedSessionId": "34b91bba-873b-4c32-9b88-b71af1b3d8d1",
  "expectedLogicalRevision": 3
}
```

The immutable receipt reports the hash of validated captured source bytes,
canonical source path, source byte count, parsed class/method/field/comment
records, applied and unchanged alias/comment operations, before/after logical
and index revisions, session, dirty state and outcome. An empty destination
has no alias operation; a class container with its original alias can contribute
an unchanged alias while its members change. A composite is one parsed comment
and stages at most one native LINE suffix. `saved` and `mappingAttached` are
always false; omissions are always empty. Prevalidation errors never produce
successful partial receipts. Four independently schema-validated examples are
under `openapi/examples/mapping-import-*`; OpenAPI was updated before the route.
The generated Python transport remains the explicit Phase 6 deliverable.

Only UTF-8 Tiny 2.0 `original` → `mapped` declaration records are supported.
The standard `escaped-names` encoding marker is recognized. Header-only input
is valid; explicitly empty input is malformed. The shared `JadxTinyMappings`
parser checks raw record shape before mapping-io, then rejects duplicate
original declaration/comment keys, extra namespaces, metadata, arguments,
locals, unknown records and unrepresentable strings. Accepted attached source
bytes are independently parsed and compared with the pinned loaded tree,
including the PR #11 attached-zero-byte regression. Incoming bytes never
replace that tree.

Resolution uses existing Jadx-visible class metadata and original full JVM
member descriptors, including method return and field type. It never calls
public `JavaClass.getMethods/getFields` or generates Java for validation.
Retained duplicate original keys are ambiguous and rejected. Jadx can collapse
input duplicates: the owned two-JAR probe sees one visible definition, and
import makes no per-input identity claim. This is the existing provenance
limitation, not a fabricated identity census.

Identical effective aliases are no-ops, including genuine original names.
Differing attached, native or automatically generated engine aliases conflict.
An unaliased target can receive the same nonreserved ASCII name accepted by
`/edits/batch`. Collisions consider proposed aliases and current visible class
qualified aliases or same-owner fields/methods, with method argument signature
distinctions. Return-only overloads resolve separately; a Jadx-generated alias
on one overload cannot be silently replaced. New top-level class aliases retain
the current package. Package moves, changed inner aliases and inner alias
records with a concurrently changed enclosing class fail unsupported. Native
synthetic/bridge/special targets fail closed.

Identical effective comment text is unchanged. A new comment must be one
representable native LINE (4096 code points/16 KiB UTF-8, no controls, format or
line separators, block delimiters or empty text). A composite can be staged
only as the exact attached prefix plus `"\n"` and one new native LINE suffix,
with no conflicting native declaration comment. Every other change conflicts
or is unsupported; no implicit replace or multiline splitting occurs.
Non-LINE comments and unknown native JSON fields remain intact on alias-only
or unrelated edits. A target with ambiguous declaration comment styles is
rejected when importing a comment.

## Admission, publication and filesystem checks

The service gets one exclusive edit admission and checks mandatory session and
logical revision before source capture. `SafeMappingInput` validates canonical
allowed roots, every parent/leaf symlink, regular-file status and protected
input/native paths (including hard-link aliases). An existing attached mapping
can itself be read; it remains protected from modification by baseline checks.
No reader method writes anything. Source bytes are owned, bounded and hashed.
File key, size, modification/creation times and parent directory identities are
captured. A final bounded streaming digest and identity check detects rewrites,
replacement, deletion and changed directories. Missing initial files return
404, path violations 403, changed captures 409 and budget failures 429.

The entire plan and copied native code data are private. Lists are staged once;
a staging failure commits no prefix. Immediately before publication the service
rechecks source bytes/identity and native/attached baselines. Equivalent input
and valid header-only files call neither `replaceCodeData` nor replay, and retain
logical/index/cache/search identity and dirty state. Effective input calls the
existing `EditContext.commit` once: ordinary logical/index revisions advance
once, source/reference/search state invalidates, and subsequent Java/search
reflect the edits. Replay failure after replacement follows the existing FAILED
lifecycle; rollback is not advertised. Save/reload/discard behavior is unchanged.

Budgets are 4 MiB source, 10000 parsed declaration/comment records, 16384
characters per parsed string, 16 MiB conservative aggregate intermediate
allocation, 64 KiB HTTP and 4096 path characters. Native baselines retain their
existing 16 MiB project limit. Visible class/member enumeration is also bounded
and charged to the shared budget, with at most 10000 visible classes and 10000
members per implicated owner; the aggregate memory estimate can reject below
any independent ceiling. Diagnostics contain one category and at most 256
characters of an original key, never incoming alias/comment text or stack traces.

Portable Java file identity/content checks do not lock external writers.
A writer or directory replacement after the final check remains a race. No
cross-process collaboration, transactional backup or atomic native-save guarantee
is claimed. Windows/macOS and filesystems without stable file identity have not
been verified; identity absence fails unsupported.

## Pinned source and executable evidence

Sources inspected at the commit above (local copies under
`/tmp/libjadx-pr11-source`, with newly fetched files at the exact commit):

- [`LoadMappingsPass`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-plugins/jadx-rename-mappings/src/main/java/jadx/plugins/mappings/load/LoadMappingsPass.java)
  and `RenameMappingsData`: source parsing can fail with no loaded tree; absence
  does not validate an explicitly attached empty file.
- [`ApplyMappingsPass`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-plugins/jadx-rename-mappings/src/main/java/jadx/plugins/mappings/load/ApplyMappingsPass.java),
  `UserRenames` and `AttachCommentsVisitor`: native aliases take precedence;
  attached LINE comments precede native ones. `JadxMappingImportProbeTest`
  verifies all three declaration kinds against fresh composite-mapping Java.
- `JadxNodeRef`, `JadxCodeRename`, `JadxCodeComment`, `ClassInfo` and
  `TypeGen`: original native keys and actual effective alias paths.
  `fullOriginalKeysResolveWithoutGeneratingOwnerSource` proves separate
  return-only keys and metadata state retention.
- [`ClassNode`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-core/src/main/java/jadx/core/dex/nodes/ClassNode.java),
  `JavaClass`, `AttrNode` and `CodeComment`: a cold `NOT_LOADED` class skips
  `unloadCode`, leaving attached attributes across listeners. The initial
  import/save/reopen test failed with three copies of attached comments.
  `JadxNativeEditAdapter.prepareCodeDataReplay` now clears declaration
  CODE_COMMENTS after cache unload and before listeners for cold and warm nodes.
  This fixes repeated edit replay without forcing source or changing persistence.

`MappingImportServiceTest` uses owned `EditOwner.java`, return-clash, synthetic,
bridge and duplicate-JAR fixtures. It covers live Java, dirty/no-op identity,
explicit save/reopen/discard, effective alias/comment conflicts, entire-plan
rejection, malformed/hostile/budget input, preserved native fields, source races
from a separate Python process, native/mapping changes, stale/busy requests,
private staging injection and post-replacement FAILED lifecycle. Filesystem
unit checks use actual symlinks, hard links, directory moves and sparse files.
HTTP tests capture and schema-check every returned receipt/error; installed
subprocess tests check discard on shutdown, explicit save/restart and absence
of the GUI runtime JAR. The GUI fixture combines matching attached aliases,
three imported native aliases and three additive comment suffixes, explicitly
saves, opens/resaves in actual Jadx 1.5.6 GUI, and reopens headlessly by original
keys.

Final commands, counts, hashes and GUI artifact paths are recorded in
[PR #12 validation and handoff](pr-12-review.md). Reports are in
`build/test-results/` and `build/reports/tests/`, fresh HTTP captures in
`build/mapping-import-contract-responses/` and saved GUI artifacts in
`build/mapping-import-gui-fixture/`. Dedicated GUI tasks are opt-in; a normal
suite skip is never counted as a GUI pass.
