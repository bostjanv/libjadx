# Phase 3.3 requested shutdown

Baseline: merged PR #4 at `91ae20c9a7b04fac287a5f19647126a2cc4c81d7`.
Jadx remains pinned to 1.5.6, source revision
`4c0ac37699aa8c9803f1c73cfaacd9205acb044b`.

`POST /api/v1/shutdown` accepts an optional JSON object. Omitted `policy` means
`discard`. `discard` ends the process without saving pending edits. `save`
calls the same `NativeProjectRepository.save` path as `/project/save`, including
native project and mappings baseline checks. `refuse_if_dirty` accepts only a
clean project. The accepted response is HTTP 202 with
`{"state":"SHUTTING_DOWN","policy":"..."}`. The response is completed before
the listener is stopped on an application-owned thread.

The public request first checks that the runtime is READY, there are no active
operation leases, and there are no queued, dispatching, running or cancelling
jobs. A terminal retained job does not block shutdown. A conflicting request
returns retryable 409 `PROJECT_BUSY` with bounded operation and job metadata;
it does not cancel work. A dirty `refuse_if_dirty` returns non-retryable 409
`PROJECT_BUSY` and leaves the project and admission state intact.

Under the short lifecycle monitor, the runtime sets a reversible reservation,
reserves registry submissions, then checks quiescence. Project operations and
runtime job submission take the lifecycle monitor, while the registry's own
submission gate covers direct trusted registry callers. Neither can slip in
after validation. The registry and coordinator are only read under their short
monitors. For
`save`, the runtime also holds a scoped exclusive operation lease while native
I/O runs. Native I/O runs on a dedicated virtual thread outside the Jetty
request thread and all admission monitors. If save fails, the reservation is
cleared and the runtime remains READY unless an independent ordinary/fatal
close has already started. The one-way coordinator and registry stop flags
are set only after policy success. Accepted requests hand off to the ordinary
supervisor teardown after the HTTP response completes.

Raw input must first establish a native `.jadx` target through
`/project/save`; shutdown has no target-path field and never invents one.
An external project or mappings change returns `EXTERNAL_MODIFICATION_CONFLICT`
and keeps pending edits in memory. The caller can use pending-edit export and
explicit reload. The native writer has no extra backup or transactional layer;
interruption during a write may require restoring a user backup. SIGKILL
cannot run any save or cleanup policy.

Ordinary JVM-hook, fatal and application-finally close still use the Phase 3.2
bounded cooperative cancellation path. They may request running jobs to stop
and wait at most five seconds for cleanup. The public endpoint does not use
that path until after quiescence and policy checks succeed. Pinned Jadx native
project serialization evidence is recorded in `docs/feasibility-matrix.md` and
`docs/phase-2.md`; this phase does not change its serialization or version.
