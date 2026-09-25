package dev.libjadx.scheduler;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/** Bounded, volatile job owner. The registry lock never spans admission, task work or HTTP writes. */
public final class JobRegistry implements AutoCloseable {
	@FunctionalInterface public interface AdmissionGate {
		OperationCoordinator.Lease tryAdmit(OperationRequest request, OperationCoordinator.Admission admission);
	}

	public static final class ResourceLimitException extends IllegalStateException {
		public ResourceLimitException(String message) { super(message); }
	}
	public static final class EventHistoryExpiredException extends IllegalArgumentException {
		public EventHistoryExpiredException() { super("Requested job event history has expired; poll and reconnect without Last-Event-ID"); }
	}
	public static final class StaleJobSnapshotException extends IllegalStateException {
		public StaleJobSnapshotException() { super("Job source snapshot no longer matches the active project"); }
	}
	/** A lifecycle rebuild is in progress; queued work waits for the next release signal. */
	public static final class AdmissionDeferredException extends IllegalStateException { }

	private final Object lock = new Object();
	private final JobLimits limits;
	private final AdmissionGate gate;
	private final Consumer<Error> fatalHandler;
	private final LongSupplier ticker;
	private final Clock clock;
	private final ObjectMapper json = JsonMapper.builder().build();
	private final Deque<Record<?>> queue = new ArrayDeque<>();
	private final Map<UUID, Record<?>> records = new LinkedHashMap<>();
	private final ExecutorService dispatcher = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "libjadx-job-dispatch");
		thread.setDaemon(true);
		return thread;
	});
	private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
	private final ScheduledExecutorService deadlineTimer = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread thread = new Thread(r, "libjadx-job-deadlines");
		thread.setDaemon(true);
		return thread;
	});
	private final AtomicBoolean dispatchScheduled = new AtomicBoolean();
	private final AtomicBoolean dispatchPending = new AtomicBoolean();
	private int runningSlots;
	private int globalSubscribers;
	private int retainedResultBytes;
	private volatile boolean stopped;
	private boolean shutdownDone;

	public JobRegistry(JobLimits limits, AdmissionGate gate, Consumer<Error> fatalHandler) {
		this(limits, gate, fatalHandler, System::nanoTime, Clock.systemUTC(), true);
	}

	JobRegistry(JobLimits limits, AdmissionGate gate, Consumer<Error> fatalHandler,
			LongSupplier ticker, Clock clock, boolean automaticDeadlines) {
		this.limits = Objects.requireNonNull(limits, "limits");
		this.gate = Objects.requireNonNull(gate, "gate");
		this.fatalHandler = Objects.requireNonNull(fatalHandler, "fatalHandler");
		this.ticker = Objects.requireNonNull(ticker, "ticker");
		this.clock = Objects.requireNonNull(clock, "clock");
		if (automaticDeadlines) deadlineTimer.scheduleAtFixedRate(this::tick, 100, 100, TimeUnit.MILLISECONDS);
	}

	public JobLimits limits() { return limits; }
	/** Called under the runtime lifecycle monitor before engine ownership changes. */
	public void stopSubmissions() {
		synchronized (lock) { stopped = true; }
	}

	/** Trusted internal submission seam; no HTTP controller exposes it. */
	public <S> JobSnapshot submit(JobSpec<S> spec) {
		Objects.requireNonNull(spec, "spec");
		Record<S> record;
		synchronized (lock) {
			purgeExpiredLocked();
			if (stopped) throw new ServiceShuttingDownException();
			if (queue.size() >= limits.maxQueued()
					|| records.size() >= limits.maxQueued() + limits.maxRunning() + limits.maxRetained()) {
				throw new ResourceLimitException("The process-local job capacity is exhausted");
			}
			long now = ticker.getAsLong();
			record = new Record<>(spec, now, clock.instant());
			records.put(record.id, record);
			queue.addLast(record);
			emitLocked(record, "job.queued");
		}
		signal();
		return snapshot(record.id);
	}

	public JobSnapshot snapshot(UUID id) {
		synchronized (lock) {
			purgeExpiredLocked();
			return findLocked(id).snapshot();
		}
	}

	/** Bounded in-process wait used by trusted callers; HTTP polling remains independent. */
	public JobSnapshot awaitTerminal(UUID id, Duration timeout) throws InterruptedException {
		long deadline = System.nanoTime() + timeout.toNanos();
		synchronized (lock) {
			while (true) {
				purgeExpiredLocked();
				JobSnapshot current = findLocked(id).snapshot();
				if (current.terminal()) return current;
				long remaining = deadline - System.nanoTime();
				if (remaining <= 0) throw new IllegalStateException("Timed out waiting for job completion");
				TimeUnit.NANOSECONDS.timedWait(lock, remaining);
			}
		}
	}

	public JobSnapshot cancel(UUID id) {
		boolean changed = false;
		JobSnapshot snapshot;
		synchronized (lock) {
			purgeExpiredLocked();
			Record<?> record = findLocked(id);
			if (!record.snapshot().terminal() && record.token.request(CancellationToken.Reason.USER)) {
				changed = true;
				if (record.state == JobSnapshot.State.RUNNING) record.state = JobSnapshot.State.CANCELLING;
				emitLocked(record, "job.cancel_requested");
				if (record.state == JobSnapshot.State.QUEUED && !record.dispatching) {
					queue.remove(record);
					finishCancelledLocked(record);
				}
			}
			snapshot = record.snapshot();
		}
		if (changed) signal();
		return snapshot;
	}

	/** Invoked after an operation lease is released, or after new queued work arrives. */
	public void signal() {
		dispatchPending.set(true);
		if (!dispatchScheduled.compareAndSet(false, true)) return;
		try {
			dispatcher.execute(() -> {
				try {
					do {
						dispatchPending.set(false);
						pump();
					} while (dispatchPending.get());
				}
				finally {
					dispatchScheduled.set(false);
					if (dispatchPending.get()) signal();
				}
			});
		} catch (RejectedExecutionException ignored) {
			dispatchScheduled.set(false);
		}
	}

	private void pump() {
		while (true) {
			Record<?> record;
			synchronized (lock) {
				purgeExpiredLocked();
				if (stopped || runningSlots >= limits.maxRunning() || queue.isEmpty()) return;
				record = queue.removeFirst();
				record.dispatching = true;
				runningSlots++;
			}
			OperationCoordinator.Lease lease;
			try {
				lease = gate.tryAdmit(record.spec.operation(), record.spec.admission());
			} catch (ProjectBusyException | AdmissionDeferredException busy) {
				requeueBlocked(record);
				return; // Await an after-release signal; never spin on a held lease.
			} catch (ServiceShuttingDownException stopping) {
				finishDispatchFailure(record, null, true);
				return;
			} catch (Throwable failure) {
				finishDispatchFailure(record, failure, false);
				if (failure instanceof Error fatal) fatalHandler.accept(fatal);
				continue;
			}
			startAdmitted(record, lease);
		}
	}

	private void requeueBlocked(Record<?> record) {
		boolean removed;
		synchronized (lock) {
			runningSlots--;
			record.dispatching = false;
			if (expired(record)) record.token.request(CancellationToken.Reason.DEADLINE);
			if (stopped) record.token.request(CancellationToken.Reason.SHUTDOWN);
			removed = stopped || record.token.isCancellationRequested();
			if (removed) finishCancelledLocked(record);
			else queue.addFirst(record);
		}
		if (removed) signal();
	}

	private void finishDispatchFailure(Record<?> record, Throwable failure, boolean cancelled) {
		synchronized (lock) {
			runningSlots--;
			record.dispatching = false;
			if (failure != null) finishFailedLocked(record, failure, "Job admission failed");
			else if (cancelled || record.token.isCancellationRequested()) {
				record.token.request(CancellationToken.Reason.SHUTDOWN);
				finishCancelledLocked(record);
			} else finishFailedLocked(record, null, "Job admission failed");
		}
		signal();
	}

	private <S> void startAdmitted(Record<S> record, OperationCoordinator.Lease lease) {
		S captured = null;
		Throwable captureFailure = null;
		try {
			if (record.spec.capture() != null) captured = record.spec.capture().capture();
			if (record.spec.operation().category() == OperationRequest.Category.TEMPORARY_ANALYSIS) {
				lease.finishSnapshotCapture();
			}
		} catch (Throwable failure) {
			captureFailure = failure;
		}
		if (captureFailure != null) {
			try { lease.close(); } catch (Throwable closeFailure) { captureFailure.addSuppressed(closeFailure); }
			finishDispatchFailure(record, captureFailure, false);
			if (captureFailure instanceof Error fatal) fatalHandler.accept(fatal);
			return;
		}
		boolean cancelled;
		synchronized (lock) {
			if (expired(record)) record.token.request(CancellationToken.Reason.DEADLINE);
			if (stopped) record.token.request(CancellationToken.Reason.SHUTDOWN);
			cancelled = record.token.isCancellationRequested();
			if (!cancelled) {
				record.dispatching = false;
				record.state = JobSnapshot.State.RUNNING;
				record.startedAt = clock.instant();
				emitLocked(record, "job.started");
			}
		}
		if (cancelled) {
			Throwable closeFailure = closeLease(lease);
			finishDispatchFailure(record, closeFailure, closeFailure == null);
			if (closeFailure instanceof Error fatal) fatalHandler.accept(fatal);
			return;
		}
		S snapshot = captured;
		try {
			workers.execute(() -> execute(record, lease, snapshot));
		} catch (RejectedExecutionException failure) {
			Throwable closeFailure = closeLease(lease);
			if (stopped && closeFailure == null) {
				record.token.request(CancellationToken.Reason.SHUTDOWN);
				finishDispatchFailure(record, null, true);
			} else {
				if (closeFailure != null) failure.addSuppressed(closeFailure);
				finishDispatchFailure(record, failure, false);
			}
			if (closeFailure instanceof Error fatal) fatalHandler.accept(fatal);
		}
	}

	private static Throwable closeLease(OperationCoordinator.Lease lease) {
		try { lease.close(); return null; }
		catch (Throwable failure) { return failure; }
	}

	private <S> void execute(Record<S> record, OperationCoordinator.Lease lease, S captured) {
		JobSpec.JobResult result = null;
		Throwable failure = null;
		try (lease) {
			result = record.spec.task().run(new Context(record), captured);
			validateResult(result);
		} catch (Throwable caught) {
			failure = caught;
		}
		synchronized (lock) {
			runningSlots--;
			if (failure instanceof CancellationException && record.token.isCancellationRequested()) {
				finishCancelledLocked(record);
			} else if (failure != null) {
				finishFailedLocked(record, failure, failure instanceof ResourceLimitException
						? "Job result exceeded its memory limit" : "Job failed; inspect the local service log");
			} else if (record.token.isCancellationRequested() || stopped) {
				record.token.request(CancellationToken.Reason.SHUTDOWN);
				finishCancelledLocked(record);
			} else if ((long) retainedResultBytes + result.json().getBytes(StandardCharsets.UTF_8).length
					> limits.maxTotalResultBytes()) {
				finishFailedLocked(record, new ResourceLimitException("Result retention limit reached"),
						"Job result exceeded its memory limit");
			} else {
				record.resultJson = result.json();
				record.completeness = result.completeness();
				retainedResultBytes += result.json().getBytes(StandardCharsets.UTF_8).length;
				record.state = JobSnapshot.State.SUCCEEDED;
				record.completedAt = clock.instant();
				record.completedNanos = ticker.getAsLong();
				emitLocked(record, "job.completed");
			}
			enforceRetentionLocked();
		}
		if (failure != null) {
			System.err.println("libjadx: job " + record.id + " failed");
			failure.printStackTrace(System.err);
			if (failure instanceof Error fatal) fatalHandler.accept(fatal);
		}
		signal();
	}

	private void validateResult(JobSpec.JobResult result) {
		if (result == null || result.json().getBytes(StandardCharsets.UTF_8).length > limits.maxResultBytes()) {
			throw new ResourceLimitException("Job result exceeds inline result limit");
		}
		try {
			if (!json.readTree(result.json()).isObject()) throw new IllegalArgumentException("Job result must be a JSON object");
		} catch (Exception invalid) {
			throw new IllegalArgumentException("Job result is not valid JSON", invalid);
		}
	}

	private final class Context implements JobSpec.JobContext {
		private final Record<?> record;
		private Context(Record<?> record) { this.record = record; }
		@Override public CancellationToken cancellation() { return record.token; }
		@Override public void progress(long completed, Long total, String stage) {
			JobProgress next = new JobProgress(completed, total, bounded(stage, 128));
			synchronized (lock) {
				if (record.state != JobSnapshot.State.RUNNING && record.state != JobSnapshot.State.CANCELLING) return;
				if (completed < record.progress.completed()) throw new IllegalArgumentException("Progress cannot move backwards");
				record.progress = next;
				emitLocked(record, "job.progress");
			}
		}
		@Override public void diagnostic(String message) {
			synchronized (lock) {
				if (record.diagnostics.size() < limits.maxDiagnostics()) {
					record.diagnostics.add(bounded(message, limits.maxDiagnosticBytes()));
				}
			}
		}
	}

	private static String bounded(String text, int maxBytes) {
		if (text == null || text.isBlank()) return "unspecified";
		byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
		return bytes.length <= maxBytes ? text : new String(bytes, 0, maxBytes, StandardCharsets.UTF_8);
	}

	/** Runs real-time deadline/TTL maintenance; tests can drive an injected monotonic ticker. */
	public void tick() {
		boolean freed = false;
		synchronized (lock) {
			purgeExpiredLocked();
			for (Record<?> record : List.copyOf(records.values())) {
				if (record.snapshot().terminal() || !expired(record)) continue;
				if (record.token.request(CancellationToken.Reason.DEADLINE)) {
					if (record.diagnostics.size() < limits.maxDiagnostics()) {
						record.diagnostics.add("Job deadline expired");
					}
					if (record.state == JobSnapshot.State.RUNNING) record.state = JobSnapshot.State.CANCELLING;
					emitLocked(record, "job.cancel_requested");
					if (record.state == JobSnapshot.State.QUEUED && !record.dispatching) {
						queue.remove(record);
						finishCancelledLocked(record);
						freed = true;
					}
				}
			}
		}
		if (freed) signal();
	}

	private boolean expired(Record<?> record) {
		return record.deadlineNanos != null && ticker.getAsLong() - record.deadlineNanos >= 0;
	}

	private void finishCancelledLocked(Record<?> record) {
		if (record.snapshot().terminal()) return;
		record.state = JobSnapshot.State.CANCELLED;
		record.completedAt = clock.instant();
		record.completedNanos = ticker.getAsLong();
		emitLocked(record, "job.cancelled");
		enforceRetentionLocked();
	}

	private void finishFailedLocked(Record<?> record, Throwable failure, String message) {
		if (record.snapshot().terminal()) return;
		record.state = JobSnapshot.State.FAILED;
		record.completedAt = clock.instant();
		record.completedNanos = ticker.getAsLong();
		record.error = new JobSnapshot.JobError(failure instanceof ResourceLimitException ? "RESOURCE_LIMIT"
				: failure instanceof StaleJobSnapshotException ? "STALE_REVISION" : "INTERNAL_ERROR", message, false);
		if (record.diagnostics.size() < limits.maxDiagnostics()) record.diagnostics.add(message);
		emitLocked(record, "job.failed");
		enforceRetentionLocked();
	}

	private void emitLocked(Record<?> record, String type) {
		long sequence = ++record.nextSequence;
		Instant at = clock.instant();
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("jobId", record.id.toString());
		data.put("sequence", sequence);
		data.put("type", type);
		data.put("at", at.toString());
		data.put("state", record.state.name());
		data.put("progress", record.progress);
		data.put("resultUrl", "/api/v1/jobs/" + record.id);
		String serialized;
		try { serialized = json.writeValueAsString(data); }
		catch (Exception impossible) { throw new IllegalStateException("Job event serialization failed", impossible); }
		if (serialized.getBytes(StandardCharsets.UTF_8).length > limits.maxEventBytes()) {
			throw new IllegalStateException("Bounded job event exceeds configured event byte limit");
		}
		JobEvent event = new JobEvent(record.id, sequence, type, at, record.state, serialized);
		if (record.events.size() == limits.maxEventsPerJob()) record.events.removeFirst();
		record.events.addLast(event);
		for (Subscription subscriber : record.subscribers) subscriber.offer(event);
		lock.notifyAll();
	}

	public Subscription subscribe(UUID id, Long lastEventId) {
		synchronized (lock) {
			purgeExpiredLocked();
			Record<?> record = findLocked(id);
			if (lastEventId != null) {
				if (lastEventId < 0 || lastEventId > record.nextSequence) {
					throw new IllegalArgumentException("Last-Event-ID is outside this job's event sequence");
				}
				if (!record.events.isEmpty() && lastEventId < record.events.getFirst().sequence() - 1) {
					throw new EventHistoryExpiredException();
				}
			}
			if (globalSubscribers >= limits.maxGlobalSubscribers()
					|| record.subscribers.size() >= limits.maxSubscribersPerJob()) {
				throw new ResourceLimitException("Job SSE subscriber capacity is exhausted");
			}
			List<JobEvent> replay = record.events.stream()
					.filter(event -> lastEventId == null || event.sequence() > lastEventId).toList();
			Subscription subscriber = new Subscription(record, replay, record.snapshot().terminal());
			record.subscribers.add(subscriber);
			globalSubscribers++;
			return subscriber;
		}
	}

	public final class Subscription implements AutoCloseable {
		private final Record<?> record;
		private final Deque<JobEvent> replay;
		private final Deque<JobEvent> live = new ArrayDeque<>();
		private boolean closed;
		private boolean terminalAtOpen;
		private boolean terminalDelivered;
		private boolean overflowed;

		private Subscription(Record<?> record, List<JobEvent> replay, boolean terminalAtOpen) {
			this.record = record;
			this.replay = new ArrayDeque<>(replay);
			this.terminalAtOpen = terminalAtOpen;
		}

		private synchronized void offer(JobEvent event) {
			if (closed || overflowed) return;
			if (live.size() >= limits.maxFramesPerSubscriber()) {
				overflowed = true;
				closed = true;
				notifyAll();
				return;
			}
			live.addLast(event);
			notifyAll();
		}

		/** Null means heartbeat timeout or closed stream; inspect isClosed(). */
		public synchronized JobEvent next(long timeoutMillis) throws InterruptedException {
			long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
			while (replay.isEmpty() && live.isEmpty() && !closed && !terminalAtOpen && !terminalDelivered) {
				long remaining = deadline - System.nanoTime();
				if (remaining <= 0) return null;
				TimeUnit.NANOSECONDS.timedWait(this, remaining);
			}
			if (closed) return null;
			JobEvent event = !replay.isEmpty() ? replay.removeFirst() : live.pollFirst();
			if (event != null && event.terminal()) terminalDelivered = true;
			if (event == null && (terminalAtOpen || terminalDelivered)) closed = true;
			return event;
		}

		public synchronized boolean isClosed() { return closed || terminalDelivered; }
		public synchronized boolean overflowed() { return overflowed; }

		private synchronized void forceClose() { closed = true; notifyAll(); }

		@Override public void close() {
			synchronized (lock) {
				if (record.subscribers.remove(this)) globalSubscribers--;
				forceClose();
			}
		}
	}

	private Record<?> findLocked(UUID id) {
		Record<?> record = records.get(id);
		if (record == null) throw new NoSuchElementException("Job not found or expired");
		return record;
	}

	private void purgeExpiredLocked() {
		long now = ticker.getAsLong();
		List<Record<?>> expired = new ArrayList<>();
		for (Record<?> record : records.values()) {
			if (record.completedNanos != null && now - record.completedNanos >= limits.terminalTtl().toNanos()) {
				expired.add(record);
			}
		}
		for (Record<?> record : expired) removeRetainedLocked(record);
	}

	private void enforceRetentionLocked() {
		int terminal = 0;
		for (Record<?> record : records.values()) if (record.completedNanos != null) terminal++;
		if (terminal <= limits.maxRetained()) return;
		for (Record<?> record : List.copyOf(records.values())) {
			if (record.completedNanos != null) {
				removeRetainedLocked(record);
				return;
			}
		}
	}

	private void removeRetainedLocked(Record<?> record) {
		records.remove(record.id);
		if (record.resultJson != null) retainedResultBytes -= record.resultJson.getBytes(StandardCharsets.UTF_8).length;
		for (Subscription subscriber : List.copyOf(record.subscribers)) {
			record.subscribers.remove(subscriber);
			globalSubscribers--;
			subscriber.forceClose();
		}
		record.events.clear();
	}

	/** Nonblocking application shutdown: no worker is interrupted or claimed stopped early. */
	public void shutdown() {
		synchronized (lock) {
			if (shutdownDone) return;
			shutdownDone = true;
			stopped = true;
			for (Record<?> record : List.copyOf(queue)) {
				queue.remove(record);
				record.token.request(CancellationToken.Reason.SHUTDOWN);
				finishCancelledLocked(record);
			}
			for (Record<?> record : records.values()) {
				if (record.snapshot().terminal()) continue;
				record.token.request(CancellationToken.Reason.SHUTDOWN);
				if (record.state == JobSnapshot.State.RUNNING) record.state = JobSnapshot.State.CANCELLING;
				emitLocked(record, "job.cancel_requested");
			}
			for (Record<?> record : records.values()) {
				for (Subscription subscriber : List.copyOf(record.subscribers)) subscriber.forceClose();
			}
		}
		deadlineTimer.shutdown();
		dispatcher.shutdown();
		workers.shutdown();
	}

	@Override public void close() { shutdown(); }

	private final class Record<S> {
		private final UUID id = UUID.randomUUID();
		private final JobSpec<S> spec;
		private final CancellationToken token = new CancellationToken();
		private final Instant createdAt;
		private final Instant deadlineAt;
		private final Long deadlineNanos;
		private final Deque<JobEvent> events = new ArrayDeque<>();
		private final List<Subscription> subscribers = new ArrayList<>();
		private final List<String> diagnostics = new ArrayList<>();
		private JobSnapshot.State state = JobSnapshot.State.QUEUED;
		private JobProgress progress = new JobProgress(0, null, "queued");
		private Instant startedAt;
		private Instant completedAt;
		private Long completedNanos;
		private String resultJson;
		private JobSpec.Completeness completeness;
		private JobSnapshot.JobError error;
		private long nextSequence;
		private boolean dispatching;

		private Record(JobSpec<S> spec, long nowNanos, Instant now) {
			this.spec = spec;
			this.createdAt = now;
			this.deadlineAt = spec.deadline() == null ? null : now.plus(spec.deadline());
			this.deadlineNanos = spec.deadline() == null ? null : nowNanos + spec.deadline().toNanos();
		}

		private JobSnapshot snapshot() {
			return new JobSnapshot(id, spec.type(), state, createdAt, startedAt, completedAt,
					deadlineAt, spec.admission().sessionId(), spec.admission().logicalRevision(),
					spec.sourceSnapshotId(), progress, resultJson, completeness, error,
					diagnostics, token.reason());
		}
	}
}
