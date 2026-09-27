# ADR 0001: Defer mapping export and scoped/propagated renames

Status: proposed follow-up, 2026-09-27.

Phase 5.2 requires native mapping export, parameter/local renames and
related-method propagation only where pinned Jadx and the matching GUI prove
the behavior. The current PR exposes only validated declaration editing.

The pinned
[`MappingExporter`](https://github.com/skylot/jadx/blob/28ff15e4ae69950aebea110a13e5ab895d234dfc/jadx-plugins/jadx-rename-mappings/src/main/java/jadx/plugins/mappings/save/MappingExporter.java)
does export a `JadxCodeData` with a selected mapping format. Its implementation
deletes an existing single-file output before writing and catches/logs its own
exceptions instead of reporting success or omissions to a caller. That is
insufficient for this service's explicit path safety, external conflict and
truthful success contract. Existing `/project/settings` only attaches an
already existing mapping; it is not an import or export transaction.

`JadxCodeRef.forMthArg` and `forVar` exist, but the service has no verified
original positional or register/SSA identity with a source-snapshot gate and
GUI persistence probe. `JavaMethod.getOverrideRelatedMethods()` returns
candidates; it does not prove every relevant declaration or GUI propagation
semantics. Requesting these operations returns `UNSUPPORTED_CAPABILITY`.

Follow-up work should use owned optimized-variable and inheritance fixtures,
verify native keys and matching-GUI resave, and design mapping output with
allowed-root, symlink, conflict, omission and failure behavior before adding
new routes. No mapping bytes are hand-formatted by this PR.
