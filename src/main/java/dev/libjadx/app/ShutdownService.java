package dev.libjadx.app;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runs the one accepted teardown only after the HTTP response is committed. */
public final class ShutdownService implements ShutdownRequester {
	private final ProjectRuntime runtime;
	private final Runnable closeApplication;
	private final AtomicBoolean triggered = new AtomicBoolean();

	public ShutdownService(ProjectRuntime runtime, Runnable closeApplication) {
		this.runtime = Objects.requireNonNull(runtime);
		this.closeApplication = Objects.requireNonNull(closeApplication);
	}

	@Override public void request(ShutdownPolicy policy) throws IOException {
		runtime.requestShutdown(policy);
	}

	@Override public void responseCommitted() {
		if (triggered.compareAndSet(false, true)) {
			Thread.ofPlatform().name("libjadx-requested-shutdown").start(closeApplication);
		}
	}
}
