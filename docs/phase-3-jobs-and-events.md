# Phase 3.2 jobs and events

The fixed-project runtime owns one process-local `JobRegistry`. Trusted Java
callers submit typed `JobSpec` tasks with an operation request, the current
session and logical revision, a source snapshot identifier, and an optional
deadline. There is no HTTP task-submission endpoint in this phase. A future
job-producing endpoint must return `202 Accepted`, a `Job` body, and
`Location: /api/v1/jobs/{jobId}`. Saturated submission returns HTTP 429
`RESOURCE_LIMIT`; synchronous conflicting project operations continue to fail
with 409 `PROJECT_BUSY`.

Integration uses pinned Jadx 1.5.6 (source revision
`4c0ac37699aa8c9803f1c73cfaacd9205acb044b`) and the existing
`tests/fixtures/native-project/sample.jar.jadx` and raw-input fixtures.
The queue, replay, and cancellation tests use synthetic tasks. They establish
registry behavior without asserting that an arbitrary Jadx call is
interruptible or safe to run concurrently.

## Budgets and lifetime

Default limits are 32 queued, 2 executing, and 256 retained terminal jobs.
Inline results are limited to 256 KiB each and 16 MiB in total. Each job keeps
at most 16 diagnostics of 4096 bytes each and 128 replay events of 8192 bytes
each. Each job permits 8 SSE subscribers, with 32 globally; each subscriber
has a 32-frame live buffer. Terminal records expire after 10 minutes. These
values are injectable through immutable `JobLimits` for tests and future
configuration. Active and cancellation-pending jobs never expire or count as
retained terminal records. When queue or overall record capacity is full, a
new submission is refused without creating a job. The oldest terminal record
is evicted when the retained count is exceeded. All records and events vanish
on restart; no job data is written to `.jadx` or another file.

The FIFO dispatcher keeps a queue slot separate from the Phase 3.1 operation
lease. It acquires the scoped lease only at dispatch. If a conflicting lease
is held, it returns the task to the front of the queue and waits for the
coordinator's after-release signal without occupying a worker or spinning.
Primary live Jadx operations remain serialized. Index/query coexistence also
requires a matching session and logical revision in addition to the same
snapshot key. Those synthetic coordination checks do not prove Jadx parallel
read safety. Temporary analysis captures its immutable state under the short
capture gate and retains isolated-engine ownership through cleanup.

## State and cancellation

`QUEUED` advances to `RUNNING` and then `SUCCEEDED` or `FAILED`; queued work
may become `CANCELLED`. A cancellation request or deadline moves running work
to `CANCELLING`. The cancellation token asks task code to stop at safe points;
it does not interrupt a Jadx call. `CANCELLING` stays visible until the task
returns and its scoped lease and resources are released. A result returned
after cancellation intent is discarded. A fatal error remains `FAILED` and
reaches the existing supervisor. Normal exceptions yield `FAILED` with a
sanitized HTTP error and a local stack trace. Job state and analysis result
completeness are independent; a successful job can report a `PARTIAL` result.

Deadlines use monotonic time and include queue wait. A queued expiry removes
the task and becomes `CANCELLED`; an executing expiry requests cooperative
stop and remains `CANCELLING` until work exits. `cancellationReason` records
`USER`, `DEADLINE`, or `SHUTDOWN`. Progress accepts an unknown `total` as null,
and `completed` may only increase. Polling works without an SSE subscriber.

## Polling and SSE

`GET /api/v1/jobs/{id}` returns the authoritative snapshot while retained.
`POST /api/v1/jobs/{id}/cancel` is idempotent and returns the current snapshot;
an obvious cross-origin `Origin` is rejected. Unknown or expired IDs are 404,
malformed UUIDs are 400. Responses use the normal request ID and no-store
headers.

`GET /api/v1/jobs/{id}/events` replays retained events and then follows live
events. Sequence IDs start at 1 for each job and increase for `job.queued`,
`job.started`, `job.progress`, `job.cancel_requested`, `job.completed`,
`job.failed`, and `job.cancelled`. Heartbeat comment frames default to every
15 seconds and consume no sequence ID. `Last-Event-ID` replays greater IDs;
a future or malformed ID returns 400, and an ID older than the retained ring
returns 409 `EVENT_HISTORY_EXPIRED` before the stream starts. In that case,
poll and reconnect without the stale ID. A slow subscriber whose live buffer
fills is disconnected and can replay if history is still present. Subscribers
are isolated from producers. The terminal frame closes the stream. A client
disconnect never cancels work.

## Shutdown and lock order

Ordinary close first stops submissions and admission, cancels queued jobs,
requests cooperative stop of executing jobs, and closes SSE subscribers.
Scheduler executors are asked to shut down without interrupting worker code.
The coordinator lease protects the primary engine until an executing job
unwinds; the existing five-second supervisor wait remains bounded. A worker
that ignores cancellation may remain `CANCELLING` beyond that wait. Its owned
resources are cleaned up when it eventually exits. Force-kill cannot complete
a native save or guarantee cancellation of arbitrary JVM work.

The runtime lifecycle monitor may call the registry's short stop-submission
method. The registry never enters the lifecycle monitor while holding its
own monitor: admission, callbacks, native work, and SSE writes occur outside
the registry monitor. Coordinator release invokes its notification callback
after releasing the coordinator monitor. Large result JSON is validated
outside scheduler locks; bounded event serialization happens under the short
registry monitor to preserve ordering.
