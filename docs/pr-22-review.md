# PR #22 review — cross-language release qualification

Candidate identity: Java `0.1.0-alpha.1`, Python `0.1.0a1`; API remains
`0.1.0-experimental`. Base: `11b3a9d0f524d6be0053246603d581f8403571ea`.
Jadx remains `1.5.6` / `28ff15e4ae69950aebea110a13e5ab895d234dfc`.

Qualification status: NOT QUALIFIED until the complete core and matching-GUI
aggregate passes at the committed candidate source. This document will retain
the actual gate result and source revision. No tag, release, publication or
artifact upload is authorized or performed. No remote CI or independent reviewer
run is claimed.

## Implemented

Added installed-artifact qualification of all 22 reviewed operations across raw
HTTP, generated sync/async and handwritten sync/async clients. The comparator
schema validates actual Java responses and retains all wire fields, including
provenance, diagnostics, coverage, snapshots, revisions, cursors, request IDs
and typed error details. All 19 published errors require real Java or an explicit
owned Java fault path. Machine matrices are derived from executable observations.

Added deterministic filesystem-only test gates, disabled unless explicitly
configured at JVM startup, for loading, work admission, cancellation, streaming,
private edit staging and request faults. Ordinary cancellation remains idempotent;
CANCELLATION_PENDING is qualified only through the documented owned fault path.
Planned OPERATION_NOT_IMPLEMENTED remains outside the first-release routes.
Neither path introduces a new public API or native persisted field.

CLI and capabilities share Gradle-generated candidate identity. Archives use
fixed timestamps/order; required Jadx notices are included. The release aggregate
checks clean Java regression, contract/example validation, frozen generation and
Python quality, isolated installed wheels, repeat builds, archive audits, and all
12 actual matching-GUI tasks with 27 logged Save As operations and zero skips.

## Evidence and persistence

Final author-run commands, counts, source revision, artifact hashes and observed
resource use are recorded after the aggregate completes. Development runs are
not substituted for final qualification. The installed tests exercise local
class/JAR/DEX/native inputs, unsaved edits and isolated overrides, explicit save,
clean/dirty shutdown policies, restart, stale source/cursors, mapping import and
export, external native/input/mapping conflicts, queued/running cancellation,
subscriber limits/history/heartbeat/overflow, and bounded process cleanup.
The matching-GUI suite preserves all existing positive and negative diagnostics.

## State and next milestone

Local renames remain UNSUPPORTED, including the retained GUI retargeting diagnostic.
Related rename groups remain the independently COMPLETE closed-input subset;
references/search and unavailable input provenance retain their partial boundaries.
Primary Jadx reads remain serialized. No persistent jobs/indexes, autosave, uploads,
project switching, remote hosting/authentication or Python process management is
introduced. Native writes retain their existing interruption limits.

If any mandatory gate fails, the executable failure remains a qualification
blocker and the next work is its smallest correction. If all pass, Phase 6.2 is
complete and publication or Phase 7 requires a separate user instruction.
