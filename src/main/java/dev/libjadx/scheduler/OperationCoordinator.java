package dev.libjadx.scheduler;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Short, fail-fast admission for one runtime. No Jadx or I/O work runs under its monitor. */
public final class OperationCoordinator {
	private final Object monitor = new Object();
	private final Map<UUID, Lease> active = new HashMap<>();
	private boolean stopped;

	public record Admission(String sessionId, long logicalRevision) { }

	public Lease tryAdmit(OperationRequest request, Admission admission, Runnable afterRelease) {
		Objects.requireNonNull(request, "request");
		Objects.requireNonNull(admission, "admission");
		Objects.requireNonNull(afterRelease, "afterRelease");
		synchronized (monitor) {
			if (stopped) throw new ServiceShuttingDownException();
			if (!compatible(request, admission)) throw new ProjectBusyException();
			Lease lease = new Lease(UUID.randomUUID(), request, admission, afterRelease);
			active.put(lease.id(), lease);
			return lease;
		}
	}

	private boolean compatible(OperationRequest candidate, Admission admission) {
		if (candidate.category() == OperationRequest.Category.TEMPORARY_ANALYSIS
				&& active.values().stream().anyMatch(l -> l.request.category() == OperationRequest.Category.TEMPORARY_ANALYSIS)) {
			return false;
		}
		for (Lease lease : active.values()) {
			OperationRequest.Category existing = lease.request.category();
			if (candidate.category() == OperationRequest.Category.TEMPORARY_ANALYSIS) {
				if (existing == OperationRequest.Category.CLASS_READ || existing == OperationRequest.Category.PROJECT_EXCLUSIVE) return false;
				continue;
			}
			if (existing == OperationRequest.Category.TEMPORARY_ANALYSIS) {
				if (lease.capturing && (candidate.category() == OperationRequest.Category.CLASS_READ
						|| candidate.category() == OperationRequest.Category.PROJECT_EXCLUSIVE)) return false;
				continue;
			}
			if (candidate.category() == OperationRequest.Category.PROJECT_EXCLUSIVE
					|| existing == OperationRequest.Category.PROJECT_EXCLUSIVE) return false;
			if (candidate.category() == OperationRequest.Category.CLASS_READ
					|| existing == OperationRequest.Category.CLASS_READ) return false;
			// A caller-selected key alone cannot prove snapshot identity.
			if (!candidate.key().equals(lease.request.key()) || !admission.equals(lease.admission)) return false;
		}
		return true;
	}

	/** Called while the runtime lifecycle monitor is held, before engine detachment. */
	public void stopAdmissions() {
		synchronized (monitor) {
			stopped = true;
		}
	}

	public int inFlightCount() {
		synchronized (monitor) {
			return active.size();
		}
	}

	/** Bounded, immutable metadata for a shutdown conflict response. */
	public List<ActiveOperation> activeOperations(int limit) {
		synchronized (monitor) {
			return active.values().stream().limit(limit)
					.map(lease -> new ActiveOperation(lease.id, lease.request.category(), bounded(lease.request.key())))
					.toList();
		}
	}

	private static String bounded(String value) { return value.length() <= 128 ? value : value.substring(0, 128); }

	public record ActiveOperation(UUID id, OperationRequest.Category category, String key) { }

	/** Temporary work does not use the primary engine after its snapshot gate is released. */
	public boolean primaryEngineInUse() {
		synchronized (monitor) {
			return active.values().stream().anyMatch(l -> l.request.category() == OperationRequest.Category.CLASS_READ
					|| l.request.category() == OperationRequest.Category.PROJECT_EXCLUSIVE);
		}
	}

	public final class Lease implements AutoCloseable {
		private final UUID id;
		private final OperationRequest request;
		private final Admission admission;
		private final Runnable afterRelease;
		private final AtomicBoolean closed = new AtomicBoolean();
		private boolean capturing;

		private Lease(UUID id, OperationRequest request, Admission admission, Runnable afterRelease) {
			this.id = id;
			this.request = request;
			this.admission = admission;
			this.afterRelease = afterRelease;
			this.capturing = request.category() == OperationRequest.Category.TEMPORARY_ANALYSIS;
		}

		public UUID id() { return id; }
		public OperationRequest request() { return request; }
		public Admission admission() { return admission; }

		/** End the short immutable-snapshot capture without ending isolated work ownership. */
		public void finishSnapshotCapture() {
			synchronized (monitor) {
				if (request.category() != OperationRequest.Category.TEMPORARY_ANALYSIS || closed.get()) {
					throw new IllegalStateException("No active temporary snapshot capture");
				}
				capturing = false;
			}
		}

		@Override public void close() {
			if (!closed.compareAndSet(false, true)) return;
			synchronized (monitor) {
				active.remove(id);
			}
			afterRelease.run();
		}
	}
}
