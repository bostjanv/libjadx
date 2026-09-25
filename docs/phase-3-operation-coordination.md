# Phase 3.1 operation coordination

Baseline: `c20cfeda77500553e84450a6cde4000b0aca6c94` (merged PR #2),
Jadx `1.5.6` at source commit `4c0ac37699aa8c9803f1c73cfaacd9205acb044b`.
The coordinator is process local and has no journal or persistence.

## Inventory and admission matrix

| Operation | Category / resources | Initial concurrency | Shutdown and cancellation |
|---|---|---|---|
| Native save, including first raw-input save | `PROJECT_EXCLUSIVE`, native repository | No concurrent project operation; metadata reads can wait on the repository | Admitted save finishes native I/O; shutdown waits at most five seconds in the supervisor and cleanup may finish later. No forceful cancellation. |
| Native reload or mapping-path update | `PROJECT_EXCLUSIVE`, staged document and replacement Jadx engine | One at a time | Stop publication after shutdown; close the owned replacement and old engine exactly once. |
| Code-data replacement and `reloadCodeData` | `PROJECT_EXCLUSIVE`, primary Jadx engine and native repository | One at a time | Defer primary-engine close until reload finishes; no implicit save. |
| Temporary snapshot capture | `TEMPORARY_ANALYSIS`, short snapshot gate and one isolated-engine slot | Capture conflicts with live Jadx and exclusive work; one temporary operation total | Capture gate ends before load/callback. Active isolated work can coexist with a later edit. Its slot remains held through engine cleanup. |
| Future class retrieval | `CLASS_READ(classKey)`, primary Jadx engine | All live class reads serialize, including different keys, pending a pinned-Jadx safety probe | Scoped lease prevents primary-engine close during a callback. No class flight table yet. |
| Future immutable index/query reads | `INDEX_READ(snapshot)` / `QUERY_READ(snapshot)` | Parallel only for the same exact immutable snapshot; no concurrent exclusive/live Jadx operation | Caller owns and releases a scoped lease. No HTTP index endpoints in this PR. |
| `/project`, `/project/settings`, pending edits | Short repository read and publication-epoch recheck | No coordinator lease; may wait behind repository save | Recheck lifecycle after repository wait; a queued read fails if shutdown began. |
| `/status`, liveness | Short lifecycle read or no project read | Remain responsive while work is busy/stopping | Available during loading and shutdown. |
| Borrowed `decompiler()` | Legacy Phase 0 probe access; unsafe for new handlers | No safety guarantee | New handlers use scoped `withPrimaryClassRead`; legacy access remains for existing probes. |
| Startup initialization | Loader-owned engine, no operation lease before READY | One startup task | Loader cancellation is best effort; late owned cleanup is tracked. |
| Normal/fatal close and supervisor wait | Admission stop, engine ownership/cleanup | No new admissions after stop | Ordinary supervisor wait is five seconds; fatal watchdog retains nonzero exit behavior. |

## Lock topology

Before P3.1, save/rebuild/edit and temporary snapshot capture used
`operationLock`, then `lifecycleLock`, then occasionally the native repository
monitor. A temporary permit was acquired under those locks. Native save and
Jadx load/reload ran outside `lifecycleLock`, but inside `operationLock`; a
second synchronous request could therefore wait without a bounded duration.

After P3.1, `ProjectRuntime` acquires `lifecycleLock` only for readiness and
short admission, then the coordinator monitor. This order is one-way:
`lifecycleLock -> coordinator monitor`. The lease release removes in-flight
state under the coordinator monitor and calls back into the runtime only after
that monitor is released. Repository and Jadx work run after admission and
outside both monitors. Stable metadata reads take the repository monitor
without `lifecycleLock`, then recheck the publication epoch under
`lifecycleLock`. Shutdown takes `lifecycleLock`, stops admissions under the
coordinator monitor and decides engine ownership without waiting for native
I/O, Jadx work or a user callback.

All primary live Jadx operations are conservative and fail fast on conflict.
The coordinator's class key supports future same-class deduplication but no
deduplication or parallel class decompilation is claimed here. Temporary
analysis uses a deep native-code-data snapshot from the repository before
releasing its snapshot gate; the isolated engine remains in flight through
cleanup. A `PROJECT_BUSY` admission rejection is retryable HTTP 409. Lifecycle
unavailability remains HTTP 503, distinct from stale revisions and external
file conflicts. Reload refusal for unsaved edits retains `PROJECT_BUSY` with
`retryable: false` until the caller explicitly permits discard.

## Evidence and limits

The native save, reload, rename/comment and GUI round-trip evidence remains in
`docs/phase-2.md` and `docs/feasibility-matrix.md`. P3.1 adds deterministic
synthetic admission tests and reruns the real Jadx 1.5.6 lifecycle suite.
Jadx 1.5.6 parallel class-read safety remains **unknown**; the coordinator
does not enable it. P3.2 can add a bounded job registry and cooperative
cancellation using these leases without changing native persistence.

The ordinary `test` task skips the two GUI-only reverse probes; both dedicated
GUI tasks must be run with the matching Jadx 1.5.6 distribution. The coordinator
tests use synthetic operations to establish admission behavior; the existing
native fixture tests exercise save, rebuild, edit, temporary analysis and
shutdown against the pinned Jadx engine.
