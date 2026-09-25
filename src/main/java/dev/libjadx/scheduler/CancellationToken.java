package dev.libjadx.scheduler;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

/** Cooperative intent only; it never interrupts arbitrary Jadx work. */
public final class CancellationToken {
	public enum Reason { USER, DEADLINE, SHUTDOWN }
	private final AtomicReference<Reason> reason = new AtomicReference<>();

	public boolean request(Reason requested) {
		return reason.compareAndSet(null, requested);
	}

	public boolean isCancellationRequested() { return reason.get() != null; }
	public Reason reason() { return reason.get(); }

	public void throwIfCancellationRequested() {
		if (isCancellationRequested()) throw new CancellationException("Job cancellation requested");
	}
}
