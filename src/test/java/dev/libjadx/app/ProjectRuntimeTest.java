package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;

class ProjectRuntimeTest {
	@Test
	void publishesOnlyAfterSuccessfulInitializationAndCloseClaimsPublishedEngineOnce() throws Exception {
		FakeEngine engine = new FakeEngine(new JadxArgs(), () -> { });
		ProjectRuntime runtime = runtime(engine);
		assertThrows(IllegalStateException.class, runtime::decompiler);

		runtime.initializeAsync(null).get(5, TimeUnit.SECONDS);

		assertEquals("READY", runtime.status().state());
		assertEquals(1, engine.loadCount.get());
		assertTrue(runtime.isReady());
		assertSame(engine.decompiler, runtime.decompiler());
		runtime.close();
		runtime.close();
		assertEquals("STOPPED", runtime.status().state());
		assertFalse(runtime.isReady());
		assertEquals(1, engine.closeCount.get());
		assertThrows(IllegalStateException.class, runtime::decompiler);
	}

	@Test
	void expectedLoadFailureClosesPartialEngineAndPublishesSafeFailure() throws Exception {
		FakeEngine engine = new FakeEngine(new JadxArgs(), () -> {
			throw new IllegalStateException("failure with /private/path/input.apk");
		});
		ProjectRuntime runtime = runtime(engine);

		runtime.initializeAsync(null).get(5, TimeUnit.SECONDS);

		assertEquals("FAILED", runtime.status().state());
		assertEquals("PROJECT_LOAD_FAILED", runtime.status().error().code());
		assertFalse(runtime.status().error().message().contains("/private/path"));
		assertEquals(1, engine.closeCount.get());
		assertEquals(1, engine.loadCount.get());
		assertThrows(IllegalStateException.class, runtime::decompiler);
		runtime.close();
		assertEquals("STOPPED", runtime.status().state());
	}

	@Test
	void shutdownAtPublishBoundaryClosesLocalEngineExactlyOnceAndNeverPublishesReady() throws Exception {
		CountDownLatch reachedPublish = new CountDownLatch(1);
		CountDownLatch releasePublish = new CountDownLatch(1);
		FakeEngine engine = new FakeEngine(new JadxArgs(), () -> { });
		ProjectRuntime runtime = new ProjectRuntime(Path.of("fixture.jadx"), List.of(), args -> engine, () -> {
			reachedPublish.countDown();
			awaitIgnoringInterrupt(releasePublish);
		});
		CompletableFuture<Void> initialization = runtime.initializeAsync(null);
		assertTrue(reachedPublish.await(5, TimeUnit.SECONDS));

		runtime.close();
		assertEquals("SHUTTING_DOWN", runtime.status().state(), "The active loader still owns its local engine");
		assertThrows(IllegalStateException.class, runtime::decompiler);
		releasePublish.countDown();
		initialization.get(5, TimeUnit.SECONDS);

		assertEquals("STOPPED", runtime.status().state());
		assertFalse(runtime.isReady());
		assertEquals(1, engine.closeCount.get());
	}

	@Test
	void publicationWinningBeforeShutdownTransfersOwnershipForOneClose() throws Exception {
		FakeEngine engine = new FakeEngine(new JadxArgs(), () -> { });
		ProjectRuntime runtime = runtime(engine);
		runtime.initializeAsync(null).get(5, TimeUnit.SECONDS);
		assertTrue(runtime.isReady());

		runtime.close();

		assertEquals("STOPPED", runtime.status().state());
		assertEquals(1, engine.closeCount.get());
		runtime.close();
		assertEquals(1, engine.closeCount.get());
	}

	@Test
	void failureCompletingAfterShutdownDoesNotReplaceShutdownState() throws Exception {
		CountDownLatch enteredLoad = new CountDownLatch(1);
		CountDownLatch releaseLoad = new CountDownLatch(1);
		FakeEngine engine = new FakeEngine(new JadxArgs(), () -> {
			enteredLoad.countDown();
			awaitIgnoringInterrupt(releaseLoad);
			throw new IllegalArgumentException("late load failure");
		});
		ProjectRuntime runtime = runtime(engine);
		CompletableFuture<Void> initialization = runtime.initializeAsync(null);
		assertTrue(enteredLoad.await(5, TimeUnit.SECONDS));
		runtime.close();
		releaseLoad.countDown();
		initialization.get(5, TimeUnit.SECONDS);

		assertEquals("STOPPED", runtime.status().state());
		assertEquals(null, runtime.status().error());
		assertEquals(1, engine.closeCount.get());
	}

	@Test
	void fatalErrorIsObservableAsInternalFailureAndIsNotConvertedToProjectLoadFailure() throws Exception {
		FakeEngine engine = new FakeEngine(new JadxArgs(), () -> {
			throw new LinkageError("simulated fatal JVM error");
		});
		ProjectRuntime runtime = runtime(engine);

		CompletionException thrown = assertThrows(CompletionException.class, () -> runtime.initializeAsync(null).join());

		assertTrue(thrown.getCause() instanceof LinkageError);
		assertEquals("FAILED", runtime.status().state());
		assertEquals("INTERNAL_ERROR", runtime.status().error().code());
		assertEquals(1, engine.closeCount.get());
		runtime.close();
	}

	@Test
	void closeDuringNoninterruptibleFactoryCreationWaitsForWorkerOwnershipCleanup() throws Exception {
		CountDownLatch enteredFactory = new CountDownLatch(1);
		CountDownLatch releaseFactory = new CountDownLatch(1);
		AtomicInteger closeCount = new AtomicInteger();
		FakeEngine engine = new FakeEngine(new JadxArgs(), () -> { }, closeCount);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(), args -> {
			enteredFactory.countDown();
			awaitIgnoringInterrupt(releaseFactory);
			return engine;
		});
		CompletableFuture<Void> initialization = runtime.initializeAsync(null);
		assertTrue(enteredFactory.await(5, TimeUnit.SECONDS));
		runtime.close();
		assertEquals("SHUTTING_DOWN", runtime.status().state());
		releaseFactory.countDown();
		initialization.get(5, TimeUnit.SECONDS);
		assertEquals("STOPPED", runtime.status().state());
		assertEquals(0, engine.loadCount.get(), "A loader created after shutdown must not begin Jadx initialization");
		assertEquals(1, closeCount.get());
	}

	@Test
	void shutdownCancelsQueuedTaskEvenWhenExecutorDiscardsIt() throws Exception {
		HoldingExecutor executor = new HoldingExecutor();
		AtomicInteger creations = new AtomicInteger();
		ProjectRuntime runtime = new ProjectRuntime(Path.of("fixture.jadx"), List.of(), args -> {
			creations.incrementAndGet();
			return new FakeEngine(args, () -> { });
		}, () -> { }, executor);
		CompletableFuture<Void> initialization = runtime.initializeAsync(null);
		try {
			assertEquals(1, executor.queued.size());
			assertEquals(0, creations.get());
			runtime.close();
			assertEquals("STOPPED", runtime.status().state());
			assertThrows(CancellationException.class, () -> initialization.get(5, TimeUnit.SECONDS));
			assertEquals(0, creations.get());
			assertEquals(1, executor.discarded);
		} finally {
			runtime.close();
		}
	}

	@Test
	void closeAndWorkerStartInterleavingsAlwaysTerminateWithoutPublishingReady() throws Exception {
		for (int i = 0; i < 20; i++) {
			HoldingExecutor executor = new HoldingExecutor();
			CountDownLatch enteredFactory = new CountDownLatch(1);
			CountDownLatch releaseFactory = new CountDownLatch(1);
			AtomicInteger creations = new AtomicInteger();
			FakeEngine engine = new FakeEngine(new JadxArgs(), () -> { });
			ProjectRuntime runtime = new ProjectRuntime(Path.of("fixture.jadx"), List.of(), args -> {
				creations.incrementAndGet();
				enteredFactory.countDown();
				awaitIgnoringInterrupt(releaseFactory);
				return engine;
			}, () -> { }, executor);
			CompletableFuture<Void> initialization = runtime.initializeAsync(null);
			Runnable task = executor.queued.get(0);
			Thread worker = null;
			try {
				if (i % 2 == 0) {
					runtime.close();
					worker = new Thread(task);
					worker.start();
					assertThrows(CancellationException.class, () -> initialization.get(5, TimeUnit.SECONDS));
					assertEquals(0, creations.get());
				} else {
					worker = new Thread(task);
					worker.start();
					assertTrue(enteredFactory.await(5, TimeUnit.SECONDS));
					runtime.close();
					assertEquals("SHUTTING_DOWN", runtime.status().state());
					releaseFactory.countDown();
					initialization.get(5, TimeUnit.SECONDS);
					assertEquals(1, creations.get());
					assertEquals(0, engine.loadCount.get());
					assertEquals(1, engine.closeCount.get());
				}
				worker.join(5_000);
				assertFalse(worker.isAlive());
				assertEquals("STOPPED", runtime.status().state());
				assertFalse(runtime.isReady());
			} finally {
				releaseFactory.countDown();
				runtime.close();
				if (worker != null) worker.join(5_000);
			}
		}
	}

	@Test
	void repeatedCloseDuringQueuedCancellationCompletesOnlyOnce() throws Exception {
		HoldingExecutor executor = new HoldingExecutor();
		AtomicInteger creations = new AtomicInteger();
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(), args -> {
			creations.incrementAndGet();
			return new FakeEngine(args, () -> { });
		}, () -> { }, executor);
		CompletableFuture<Void> initialization = runtime.initializeAsync(null);
		AtomicInteger completions = new AtomicInteger();
		initialization.whenComplete((result, failure) -> completions.incrementAndGet());
		runtime.close();
		runtime.close();
		assertThrows(CancellationException.class, () -> initialization.get(5, TimeUnit.SECONDS));
		assertEquals(1, completions.get());
		assertEquals(0, creations.get());
		assertEquals("STOPPED", runtime.status().state());
	}

	@Test
	void submissionRejectionCompletesFutureAndLeavesNoPendingLoad() throws Exception {
		HoldingExecutor executor = new HoldingExecutor();
		executor.shutdown();
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(), args -> {
			throw new AssertionError("Factory must not run");
		}, () -> { }, executor);
		CompletableFuture<Void> initialization = runtime.initializeAsync(null);
		ExecutionException failure = assertThrows(ExecutionException.class,
				() -> initialization.get(5, TimeUnit.SECONDS));
		assertTrue(failure.getCause() instanceof RejectedExecutionException);
		assertEquals("FAILED", runtime.status().state());
		runtime.close();
		assertEquals("STOPPED", runtime.status().state());
	}

	private static ProjectRuntime runtime(FakeEngine engine) {
		return new ProjectRuntime(Path.of("fixture.jadx"), List.of(), args -> engine);
	}

	private static void awaitIgnoringInterrupt(CountDownLatch latch) {
		boolean interrupted = false;
		while (true) {
			try {
				latch.await();
				break;
			} catch (InterruptedException e) {
				interrupted = true;
			}
		}
		if (interrupted) Thread.currentThread().interrupt();
	}

	@FunctionalInterface
	private interface LoadAction {
		void run() throws Exception;
	}

	private static final class FakeEngine implements ProjectRuntime.ProjectEngine {
		private final JadxDecompiler decompiler;
		private final LoadAction loadAction;
		private final AtomicInteger closeCount;
		private final AtomicInteger loadCount = new AtomicInteger();

		private FakeEngine(JadxArgs args, LoadAction loadAction) {
			this(args, loadAction, new AtomicInteger());
		}

		private FakeEngine(JadxArgs args, LoadAction loadAction, AtomicInteger closeCount) {
			this.decompiler = new JadxDecompiler(args);
			this.loadAction = loadAction;
			this.closeCount = closeCount;
		}

		@Override
		public void load() throws Exception {
			loadCount.incrementAndGet();
			loadAction.run();
		}

		@Override
		public JadxDecompiler decompiler() {
			return decompiler;
		}

		@Override
		public void close() {
			closeCount.incrementAndGet();
			decompiler.close();
		}
	}

	private static final class HoldingExecutor extends AbstractExecutorService {
		private final List<Runnable> queued = new ArrayList<>();
		private boolean shutdown;
		private int discarded;

		@Override public void execute(Runnable command) {
			if (shutdown) throw new RejectedExecutionException("controlled rejection");
			queued.add(command);
		}

		@Override public void shutdown() { shutdown = true; }

		@Override public List<Runnable> shutdownNow() {
			shutdown = true;
			List<Runnable> removed = List.copyOf(queued);
			discarded += queued.size();
			queued.clear();
			return removed;
		}

		@Override public boolean isShutdown() { return shutdown; }

		@Override public boolean isTerminated() { return shutdown && queued.isEmpty(); }

		@Override public boolean awaitTermination(long timeout, TimeUnit unit) { return isTerminated(); }
	}
}
