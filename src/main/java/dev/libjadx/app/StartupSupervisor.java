package dev.libjadx.app;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

/** Application-boundary supervision and idempotent shutdown for startup. */
final class StartupSupervisor implements AutoCloseable {
	private static final long ORDINARY_SHUTDOWN_WAIT_SECONDS = 5;
	private final AutoCloseable listener;
	private final ProjectRuntime runtime;
	private final Runnable startFatalWatchdog;
	private final IntConsumer terminalExit;
	private final AtomicBoolean closed = new AtomicBoolean();
	private final CountDownLatch closeFinished = new CountDownLatch(1);
	private final CountDownLatch fatalExitFinished = new CountDownLatch(1);
	private volatile boolean fatalObserved;

	StartupSupervisor(AutoCloseable listener, ProjectRuntime runtime,
			Runnable startFatalWatchdog, IntConsumer terminalExit) {
		this.listener = listener;
		this.runtime = runtime;
		this.startFatalWatchdog = startFatalWatchdog;
		this.terminalExit = terminalExit;
	}

	void watch(CompletableFuture<Void> initialization) {
		initialization.whenComplete((ignored, failure) -> {
			if (failure == null) return;
			Throwable cause = unwrap(failure);
			if (cause instanceof CancellationException && closed.get()) return;
			if (cause instanceof Error) {
				fatalObserved = true;
				try {
					startFatalWatchdog.run();
					System.err.println("libjadx: fatal project initialization error: " + cause.getClass().getSimpleName());
					closeFromFatalLoader();
				} catch (Throwable shutdownFailure) {
					System.err.println("libjadx: fatal shutdown failed");
				} finally {
					try {
						terminalExit.accept(1);
					} finally {
						fatalExitFinished.countDown();
					}
				}
			} else {
				System.err.println("libjadx: project initialization ended: " + cause.getClass().getSimpleName());
			}
		});
	}

	/** Keeps main alive until the fatal exit action has run after listener shutdown. */
	void awaitFatalExitIfObserved() throws InterruptedException {
		if (fatalObserved) {
			fatalExitFinished.await();
			throw new IllegalStateException("Fatal project initialization error");
		}
	}

	private static Throwable unwrap(Throwable failure) {
		while ((failure instanceof CompletionException || failure instanceof ExecutionException)
				&& failure.getCause() != null) {
			failure = failure.getCause();
		}
		return failure;
	}

	@Override
	public void close() {
		close(true);
	}

	private void closeFromFatalLoader() {
		close(false);
	}

	private void close(boolean waitForLoader) {
		if (!closed.compareAndSet(false, true)) {
			if (waitForLoader) awaitCloseFinished();
			return;
		}
		try {
			try {
				listener.close();
			} catch (Exception failure) {
				System.err.println("libjadx: listener shutdown failed");
			} finally {
				runtime.close();
			}
			if (waitForLoader && !awaitRuntimeCleanup()) {
				System.err.println("libjadx: project cleanup did not finish within 5 seconds");
			}
		} finally {
			closeFinished.countDown();
		}
	}

	private boolean awaitRuntimeCleanup() {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(ORDINARY_SHUTDOWN_WAIT_SECONDS);
		boolean interrupted = false;
		try {
			while (true) {
				long remaining = deadline - System.nanoTime();
				if (remaining <= 0) return false;
				try {
					return runtime.awaitStopped(remaining, TimeUnit.NANOSECONDS);
				} catch (InterruptedException ignored) {
					interrupted = true;
				}
			}
		} finally {
			if (interrupted) Thread.currentThread().interrupt();
		}
	}

	private void awaitCloseFinished() {
		boolean interrupted = false;
		while (true) {
			try {
				closeFinished.await();
				break;
			} catch (InterruptedException ignored) {
				interrupted = true;
			}
		}
		if (interrupted) Thread.currentThread().interrupt();
	}
}
