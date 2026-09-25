package dev.libjadx.scheduler;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class JobRegistryTest {
	private static final OperationCoordinator.Admission ADMISSION = new OperationCoordinator.Admission("session-1", 4);
	private static final Clock WALL = Clock.fixed(Instant.parse("2026-09-25T09:00:00Z"), ZoneOffset.UTC);

	private static JobLimits limits(int queued, int running, int events, int frames, int retained) {
		return new JobLimits(queued, running, retained, 1024, 4096, 4, 128,
				events, 1024, 2, 4, frames, Duration.ofSeconds(10), Duration.ofMillis(20));
	}

	private static JobSpec<Void> spec(String name, Duration deadline, JobSpec.JobTask<Void> task) {
		return new JobSpec<>(name, OperationRequest.classRead(name), ADMISSION, "snapshot-4", deadline, null, task);
	}

	private static JobSnapshot terminal(JobRegistry jobs, UUID id) throws Exception {
		try (var events = jobs.subscribe(id, null)) {
			while (true) {
				JobEvent event = events.next(5_000);
				assertNotNull(event, "Timed out waiting for terminal event");
				if (event.terminal()) return jobs.snapshot(id);
			}
		}
	}

	@Test void successProgressAndPollingAgreeWithOrderedEvents() throws Exception {
		OperationCoordinator coordinator = new OperationCoordinator();
		AtomicReference<JobRegistry> owner = new AtomicReference<>();
		try (JobRegistry jobs = new JobRegistry(limits(2, 1, 8, 4, 4),
				(req, admission) -> coordinator.tryAdmit(req, admission, () -> owner.get().signal()),
				fatal -> fail("Unexpected fatal error"), System::nanoTime, WALL, false)) {
			owner.set(jobs);
			JobSnapshot submitted = jobs.submit(spec("A", null, (ctx, ignored) -> {
				ctx.progress(1, null, "classes");
				ctx.progress(2, 2L, "complete");
				for (int i = 0; i < 6; i++) ctx.diagnostic("diagnostic-" + i);
				return new JobSpec.JobResult("{\"count\":2}", JobSpec.Completeness.COMPLETE);
			}));
			JobSnapshot done = terminal(jobs, submitted.jobId());
			assertEquals(JobSnapshot.State.SUCCEEDED, done.state());
			assertEquals("{\"count\":2}", done.resultJson());
			assertEquals(2, done.progress().completed());
			assertEquals(4, done.diagnostics().size());
			assertNotNull(done.completedAt());
			assertEquals(0, coordinator.inFlightCount());
			try (var replay = jobs.subscribe(done.jobId(), null)) {
				long previous = 0;
				JobEvent event;
				while ((event = replay.next(100)) != null) {
					assertTrue(event.sequence() > previous);
					previous = event.sequence();
					if (event.terminal()) break;
				}
				assertTrue(previous >= 4);
			}
		}
	}

	@Test void boundedQueueCancelsQueuedWorkWithoutDispatch() throws Exception {
		OperationCoordinator coordinator = new OperationCoordinator();
		AtomicReference<JobRegistry> owner = new AtomicReference<>();
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		AtomicInteger secondRuns = new AtomicInteger();
		try (JobRegistry jobs = new JobRegistry(limits(1, 1, 8, 4, 4),
				(req, admission) -> coordinator.tryAdmit(req, admission, () -> owner.get().signal()),
				fatal -> fail("Unexpected fatal error"), System::nanoTime, WALL, false)) {
			owner.set(jobs);
			UUID first = jobs.submit(spec("A", null, (ctx, ignored) -> {
				entered.countDown();
				assertTrue(release.await(5, TimeUnit.SECONDS));
				return new JobSpec.JobResult("{}", JobSpec.Completeness.COMPLETE);
			})).jobId();
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			UUID second = jobs.submit(spec("B", null, (ctx, ignored) -> {
				secondRuns.incrementAndGet();
				return new JobSpec.JobResult("{}", JobSpec.Completeness.COMPLETE);
			})).jobId();
			assertThrows(JobRegistry.ResourceLimitException.class, () -> jobs.submit(spec("C", null,
					(ctx, ignored) -> new JobSpec.JobResult("{}", JobSpec.Completeness.COMPLETE))));
			assertEquals(JobSnapshot.State.CANCELLED, jobs.cancel(second).state());
			assertEquals(JobSnapshot.State.CANCELLED, jobs.cancel(second).state());
			assertEquals(0, secondRuns.get());
			release.countDown();
			assertEquals(JobSnapshot.State.SUCCEEDED, terminal(jobs, first).state());
		} finally { release.countDown(); }
	}

	@Test void runningCancellationAndDeadlineNeverClaimEarlyStop() throws Exception {
		AtomicLong nanos = new AtomicLong();
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		OperationCoordinator coordinator = new OperationCoordinator();
		AtomicReference<JobRegistry> owner = new AtomicReference<>();
		try (JobRegistry jobs = new JobRegistry(limits(2, 1, 8, 4, 4),
				(req, admission) -> coordinator.tryAdmit(req, admission, () -> owner.get().signal()),
				fatal -> fail("Unexpected fatal error"), nanos::get, WALL, false)) {
			owner.set(jobs);
			UUID id = jobs.submit(spec("A", Duration.ofNanos(5), (ctx, ignored) -> {
				entered.countDown();
				assertTrue(release.await(5, TimeUnit.SECONDS));
				return new JobSpec.JobResult("{\"mustNotPublish\":true}", JobSpec.Completeness.COMPLETE);
			})).jobId();
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			nanos.set(6);
			jobs.tick();
			assertEquals(JobSnapshot.State.CANCELLING, jobs.snapshot(id).state());
			assertEquals(CancellationToken.Reason.DEADLINE, jobs.snapshot(id).cancellationReason());
			assertEquals(JobSnapshot.State.CANCELLING, jobs.cancel(id).state());
			assertNull(jobs.snapshot(id).completedAt());
			release.countDown();
			JobSnapshot done = terminal(jobs, id);
			assertEquals(JobSnapshot.State.CANCELLED, done.state());
			assertNull(done.resultJson());
			assertEquals(0, coordinator.inFlightCount());
		} finally { release.countDown(); }
	}

	@Test void queuedDeadlineIncludesWaitTimeAndTtlExpiresTerminalState() throws Exception {
		AtomicLong nanos = new AtomicLong();
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		AtomicInteger queuedRuns = new AtomicInteger();
		OperationCoordinator coordinator = new OperationCoordinator();
		AtomicReference<JobRegistry> owner = new AtomicReference<>();
		try (JobRegistry jobs = new JobRegistry(limits(2, 1, 8, 4, 4),
				(req, admission) -> coordinator.tryAdmit(req, admission, () -> owner.get().signal()),
				fatal -> fail("Unexpected fatal error"), nanos::get, WALL, false)) {
			owner.set(jobs);
			UUID first = jobs.submit(spec("A", null, (ctx, ignored) -> {
				entered.countDown();
				assertTrue(release.await(5, TimeUnit.SECONDS));
				return new JobSpec.JobResult("{}", JobSpec.Completeness.COMPLETE);
			})).jobId();
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			UUID second = jobs.submit(spec("B", Duration.ofNanos(5), (ctx, ignored) -> {
				queuedRuns.incrementAndGet();
				return new JobSpec.JobResult("{}", JobSpec.Completeness.COMPLETE);
			})).jobId();
			nanos.set(6);
			jobs.tick();
			assertEquals(JobSnapshot.State.CANCELLED, jobs.snapshot(second).state());
			assertEquals(CancellationToken.Reason.DEADLINE, jobs.snapshot(second).cancellationReason());
			assertEquals(0, queuedRuns.get());
			nanos.set(Duration.ofSeconds(11).toNanos());
			jobs.tick();
			assertThrows(java.util.NoSuchElementException.class, () -> jobs.snapshot(second));
			release.countDown();
			assertEquals(JobSnapshot.State.SUCCEEDED, terminal(jobs, first).state());
		} finally { release.countDown(); }
	}

	@Test void busyLeaseSignalsQueuedJobWithoutSpin() throws Exception {
		OperationCoordinator coordinator = new OperationCoordinator();
		AtomicReference<JobRegistry> owner = new AtomicReference<>();
		CountDownLatch attempted = new CountDownLatch(1);
		AtomicInteger attempts = new AtomicInteger();
		try (JobRegistry jobs = new JobRegistry(limits(2, 1, 8, 4, 4), (req, admission) -> {
			attempts.incrementAndGet();
			try {
				return coordinator.tryAdmit(req, admission, () -> owner.get().signal());
			} catch (ProjectBusyException busy) {
				attempted.countDown();
				throw busy;
			}
		}, fatal -> fail("Unexpected fatal error"), System::nanoTime, WALL, false)) {
			owner.set(jobs);
			var exclusive = coordinator.tryAdmit(OperationRequest.projectExclusive("save"), ADMISSION, jobs::signal);
			UUID id = jobs.submit(spec("A", null,
					(ctx, ignored) -> new JobSpec.JobResult("{}", JobSpec.Completeness.COMPLETE))).jobId();
			assertTrue(attempted.await(5, TimeUnit.SECONDS));
			assertEquals(JobSnapshot.State.QUEUED, jobs.snapshot(id).state());
			assertEquals(1, attempts.get());
			exclusive.close();
			assertEquals(JobSnapshot.State.SUCCEEDED, terminal(jobs, id).state());
			assertEquals(2, attempts.get());
		}
	}

	@Test void dispatchingDeadlineWakesFollowingQueuedWork() throws Exception {
		AtomicLong nanos = new AtomicLong();
		CountDownLatch admittingFirst = new CountDownLatch(1);
		CountDownLatch releaseAdmission = new CountDownLatch(1);
		AtomicInteger attempts = new AtomicInteger();
		OperationCoordinator coordinator = new OperationCoordinator();
		AtomicReference<JobRegistry> owner = new AtomicReference<>();
		try (JobRegistry jobs = new JobRegistry(limits(2, 1, 8, 4, 4), (req, admission) -> {
			if (attempts.incrementAndGet() == 1) {
				admittingFirst.countDown();
				try { assertTrue(releaseAdmission.await(5, TimeUnit.SECONDS)); }
				catch (InterruptedException interrupted) { throw new RuntimeException(interrupted); }
				throw new ProjectBusyException();
			}
			return coordinator.tryAdmit(req, admission, () -> owner.get().signal());
		}, fatal -> fail("Unexpected fatal error"), nanos::get, WALL, false)) {
			owner.set(jobs);
			UUID first = jobs.submit(spec("A", Duration.ofNanos(5),
					(ctx, ignored) -> new JobSpec.JobResult("{}", JobSpec.Completeness.COMPLETE))).jobId();
			assertTrue(admittingFirst.await(5, TimeUnit.SECONDS));
			UUID second = jobs.submit(spec("B", null,
					(ctx, ignored) -> new JobSpec.JobResult("{}", JobSpec.Completeness.COMPLETE))).jobId();
			nanos.set(6);
			jobs.tick();
			releaseAdmission.countDown();
			assertEquals(JobSnapshot.State.CANCELLED, terminal(jobs, first).state());
			assertEquals(JobSnapshot.State.SUCCEEDED, terminal(jobs, second).state());
		} finally { releaseAdmission.countDown(); }
	}

	@Test void boundedReplayAndSlowSubscriberOverflow() throws Exception {
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch emit = new CountDownLatch(1);
		CountDownLatch emitted = new CountDownLatch(1);
		OperationCoordinator coordinator = new OperationCoordinator();
		AtomicReference<JobRegistry> owner = new AtomicReference<>();
		try (JobRegistry jobs = new JobRegistry(limits(2, 1, 3, 1, 4),
				(req, admission) -> coordinator.tryAdmit(req, admission, () -> owner.get().signal()),
				fatal -> fail("Unexpected fatal error"), System::nanoTime, WALL, false)) {
			owner.set(jobs);
			UUID id = jobs.submit(spec("A", null, (ctx, ignored) -> {
				entered.countDown();
				assertTrue(emit.await(5, TimeUnit.SECONDS));
				ctx.progress(1, null, "one");
				ctx.progress(2, null, "two");
				ctx.progress(3, null, "three");
				emitted.countDown();
				return new JobSpec.JobResult("{}", JobSpec.Completeness.COMPLETE);
			})).jobId();
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			try (var slow = jobs.subscribe(id, 2L)) {
				emit.countDown();
				assertTrue(emitted.await(5, TimeUnit.SECONDS));
				assertTrue(slow.overflowed());
			}
			assertEquals(JobSnapshot.State.SUCCEEDED, terminal(jobs, id).state());
			assertThrows(JobRegistry.EventHistoryExpiredException.class, () -> jobs.subscribe(id, 1L));
			assertThrows(IllegalArgumentException.class, () -> jobs.subscribe(id, 999L));
		} finally { emit.countDown(); }
	}

	@Test void cooperativeCancellationStopsBetweenItemsAndOrdinaryFailureIsTerminal() throws Exception {
		OperationCoordinator coordinator = new OperationCoordinator();
		AtomicReference<JobRegistry> owner = new AtomicReference<>();
		CountDownLatch firstItem = new CountDownLatch(1);
		CountDownLatch nextItem = new CountDownLatch(1);
		AtomicInteger worked = new AtomicInteger();
		try (JobRegistry jobs = new JobRegistry(limits(2, 1, 8, 4, 4),
				(req, admission) -> coordinator.tryAdmit(req, admission, () -> owner.get().signal()),
				fatal -> fail("Unexpected fatal error"), System::nanoTime, WALL, false)) {
			owner.set(jobs);
			UUID id = jobs.submit(spec("A", null, (ctx, ignored) -> {
				worked.incrementAndGet();
				firstItem.countDown();
				assertTrue(nextItem.await(5, TimeUnit.SECONDS));
				ctx.cancellation().throwIfCancellationRequested();
				worked.incrementAndGet();
				return new JobSpec.JobResult("{}", JobSpec.Completeness.COMPLETE);
			})).jobId();
			assertTrue(firstItem.await(5, TimeUnit.SECONDS));
			assertEquals(JobSnapshot.State.CANCELLING, jobs.cancel(id).state());
			nextItem.countDown();
			assertEquals(JobSnapshot.State.CANCELLED, terminal(jobs, id).state());
			assertEquals(1, worked.get());
			UUID failed = jobs.submit(spec("B", null, (ctx, ignored) -> {
				throw new IllegalStateException("local details");
			})).jobId();
			JobSnapshot failure = terminal(jobs, failed);
			assertEquals(JobSnapshot.State.FAILED, failure.state());
			assertEquals("INTERNAL_ERROR", failure.error().code());
			assertFalse(failure.error().message().contains("local details"));
			assertEquals(0, coordinator.inFlightCount());
		} finally { nextItem.countDown(); }
	}

	@Test void resultBytePressureEvictsOldestTerminalResultBeforePublishingSuccess() throws Exception {
		OperationCoordinator coordinator = new OperationCoordinator();
		AtomicReference<JobRegistry> owner = new AtomicReference<>();
		JobLimits small = new JobLimits(2, 1, 4, 16, 20, 2, 128,
				8, 1024, 2, 4, 4, Duration.ofSeconds(10), Duration.ofMillis(20));
		try (JobRegistry jobs = new JobRegistry(small,
				(req, admission) -> coordinator.tryAdmit(req, admission, () -> owner.get().signal()),
				fatal -> fail("Unexpected fatal error"), System::nanoTime, WALL, false)) {
			owner.set(jobs);
			UUID first = jobs.submit(spec("A", null,
					(ctx, ignored) -> new JobSpec.JobResult("{\"count\":123}", JobSpec.Completeness.COMPLETE))).jobId();
			assertEquals(JobSnapshot.State.SUCCEEDED, terminal(jobs, first).state());
			UUID second = jobs.submit(spec("B", null,
					(ctx, ignored) -> new JobSpec.JobResult("{\"count\":456}", JobSpec.Completeness.COMPLETE))).jobId();
			assertEquals(JobSnapshot.State.SUCCEEDED, terminal(jobs, second).state());
			assertThrows(java.util.NoSuchElementException.class, () -> jobs.snapshot(first));
			assertEquals("{\"count\":456}", jobs.snapshot(second).resultJson());
			UUID third = jobs.submit(spec("C", null,
					(ctx, ignored) -> new JobSpec.JobResult("{\"count\":789}", JobSpec.Completeness.COMPLETE))).jobId();
			assertEquals(JobSnapshot.State.SUCCEEDED, terminal(jobs, third).state());
			assertThrows(java.util.NoSuchElementException.class, () -> jobs.snapshot(second));
			assertEquals("{\"count\":789}", jobs.snapshot(third).resultJson());
		}
	}

	@Test void terminalCountRetentionEvictsOldestEvenWithoutBytePressure() throws Exception {
		OperationCoordinator coordinator = new OperationCoordinator();
		AtomicReference<JobRegistry> owner = new AtomicReference<>();
		JobLimits small = new JobLimits(2, 1, 1, 16, 100, 2, 128,
				8, 1024, 2, 4, 4, Duration.ofSeconds(10), Duration.ofMillis(20));
		try (JobRegistry jobs = new JobRegistry(small,
				(req, admission) -> coordinator.tryAdmit(req, admission, () -> owner.get().signal()),
				fatal -> fail("Unexpected fatal error"), System::nanoTime, WALL, false)) {
			owner.set(jobs);
			UUID first = jobs.submit(spec("A", null,
					(ctx, ignored) -> new JobSpec.JobResult("{}", JobSpec.Completeness.COMPLETE))).jobId();
			assertEquals(JobSnapshot.State.SUCCEEDED, terminal(jobs, first).state());
			UUID second = jobs.submit(spec("B", null,
					(ctx, ignored) -> new JobSpec.JobResult("{}", JobSpec.Completeness.COMPLETE))).jobId();
			assertEquals(JobSnapshot.State.SUCCEEDED, terminal(jobs, second).state());
			assertThrows(java.util.NoSuchElementException.class, () -> jobs.snapshot(first));
		}
	}

	@Test void oversizedInlineResultFailsWithoutPublishingItsPayload() throws Exception {
		OperationCoordinator coordinator = new OperationCoordinator();
		AtomicReference<JobRegistry> owner = new AtomicReference<>();
		try (JobRegistry jobs = new JobRegistry(limits(2, 1, 8, 4, 4),
				(req, admission) -> coordinator.tryAdmit(req, admission, () -> owner.get().signal()),
				fatal -> fail("Unexpected fatal error"), System::nanoTime, WALL, false)) {
			owner.set(jobs);
			UUID id = jobs.submit(spec("large", null,
					(ctx, ignored) -> new JobSpec.JobResult("{\"data\":\"" + "x".repeat(1024) + "\"}",
							JobSpec.Completeness.COMPLETE))).jobId();
			JobSnapshot failed = terminal(jobs, id);
			assertEquals(JobSnapshot.State.FAILED, failed.state());
			assertEquals("RESOURCE_LIMIT", failed.error().code());
			assertNull(failed.resultJson());
		}
	}
}
