package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import dev.libjadx.http.HttpApiServer;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;

class StartupSupervisorTest {
	private static final ObjectMapper JSON = new ObjectMapper();

	@Test
	void ordinaryShutdownStopsWaitingAfterDefinedCleanupTimeout() throws Exception {
		CountDownLatch enteredLoad = new CountDownLatch(1);
		CountDownLatch releaseLoad = new CountDownLatch(1);
		AtomicInteger engineCloses = new AtomicInteger();
		ProjectRuntime runtime = runtime(() -> {
			enteredLoad.countDown();
			awaitIgnoringInterrupt(releaseLoad);
		}, engineCloses);
		StartupSupervisor supervisor = new StartupSupervisor(() -> { }, runtime, () -> { }, code -> { });
		CompletableFuture<Void> initialization = runtime.initializeAsync(null);
		Thread ordinaryShutdown = new Thread(supervisor::close);
		try {
			assertTrue(enteredLoad.await(5, TimeUnit.SECONDS));
			ordinaryShutdown.start();
			awaitState(runtime, "SHUTTING_DOWN");
			ordinaryShutdown.join(7_000);
			assertFalse(ordinaryShutdown.isAlive(), "Shutdown wait must be bounded");
			assertEquals("SHUTTING_DOWN", runtime.status().state());
			releaseLoad.countDown();
			initialization.get(5, TimeUnit.SECONDS);
			assertEquals(1, engineCloses.get());
			assertEquals("STOPPED", runtime.status().state());
		} finally {
			releaseLoad.countDown();
			ordinaryShutdown.join(7_000);
			supervisor.close();
		}
	}

	@Test
	void ordinaryShutdownWaitsForRunningLoaderAndEngineCleanup() throws Exception {
		CountDownLatch enteredLoad = new CountDownLatch(1);
		CountDownLatch releaseLoad = new CountDownLatch(1);
		CountDownLatch enteredEngineClose = new CountDownLatch(1);
		CountDownLatch releaseEngineClose = new CountDownLatch(1);
		AtomicInteger engineCloses = new AtomicInteger();
		ProjectRuntime runtime = new ProjectRuntime(Path.of("fixture.jadx"), List.of(), args -> new ProjectRuntime.ProjectEngine() {
			private final JadxDecompiler decompiler = new JadxDecompiler(args);

			@Override public void load() {
				enteredLoad.countDown();
				awaitIgnoringInterrupt(releaseLoad);
			}

			@Override public JadxDecompiler decompiler() { return decompiler; }

			@Override public void close() {
				enteredEngineClose.countDown();
				awaitIgnoringInterrupt(releaseEngineClose);
				engineCloses.incrementAndGet();
				decompiler.close();
			}
		});
		StartupSupervisor supervisor = new StartupSupervisor(() -> { }, runtime, () -> { }, code -> { });
		CompletableFuture<Void> initialization = runtime.initializeAsync(null);
		Thread ordinaryShutdown = new Thread(supervisor::close);
		try {
			assertTrue(enteredLoad.await(5, TimeUnit.SECONDS));
			ordinaryShutdown.start();
			awaitState(runtime, "SHUTTING_DOWN");
			assertTrue(ordinaryShutdown.isAlive(), "Shutdown must wait while the loader owns Jadx");
			releaseLoad.countDown();
			assertTrue(enteredEngineClose.await(5, TimeUnit.SECONDS));
			assertTrue(ordinaryShutdown.isAlive(), "Shutdown must wait for engine.close()");
			assertEquals("SHUTTING_DOWN", runtime.status().state());
			releaseEngineClose.countDown();
			ordinaryShutdown.join(5_000);
			assertFalse(ordinaryShutdown.isAlive());
			initialization.get(5, TimeUnit.SECONDS);
			assertEquals(1, engineCloses.get());
			assertEquals("STOPPED", runtime.status().state());
		} finally {
			releaseLoad.countDown();
			releaseEngineClose.countDown();
			ordinaryShutdown.join(5_000);
			supervisor.close();
		}
	}

	@Test
	void fatalLoaderErrorStopsListenerAndRequestsNonzeroExit() throws Exception {
		AtomicInteger engineCloses = new AtomicInteger();
		ProjectRuntime runtime = runtime(() -> { throw new LinkageError("controlled fatal error"); }, engineCloses);
		HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime);
		AtomicInteger listenerCloses = new AtomicInteger();
		CountDownLatch exitRequested = new CountDownLatch(1);
		AtomicInteger exitCode = new AtomicInteger();
		StartupSupervisor supervisor = new StartupSupervisor(() -> {
			listenerCloses.incrementAndGet();
			server.close();
		}, runtime, () -> { }, code -> {
			exitCode.set(code);
			exitRequested.countDown();
		});
		try {
			server.start();
			CompletableFuture<Void> initialization = runtime.initializeAsync(null);
			supervisor.watch(initialization);
			ExecutionException failure = assertThrows(ExecutionException.class,
					() -> initialization.get(5, TimeUnit.SECONDS));
			assertTrue(failure.getCause() instanceof LinkageError);
			assertTrue(exitRequested.await(5, TimeUnit.SECONDS));
			assertEquals(1, exitCode.get());
			assertThrows(IllegalStateException.class, supervisor::awaitFatalExitIfObserved);
			assertEquals(1, listenerCloses.get());
			assertEquals(1, engineCloses.get());
			assertEquals("STOPPED", runtime.status().state());
		} finally {
			supervisor.close();
		}
	}

	@Test
	void ordinaryLoadFailureKeepsDiagnosticHttpListenerRunning() throws Exception {
		AtomicInteger engineCloses = new AtomicInteger();
		ProjectRuntime runtime = runtime(() -> { throw new IllegalStateException("/private/input.jar"); }, engineCloses);
		HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime);
		AtomicInteger exitRequests = new AtomicInteger();
		StartupSupervisor supervisor = new StartupSupervisor(server, runtime, () -> { }, code -> exitRequests.incrementAndGet());
		try {
			server.start();
			CompletableFuture<Void> initialization = runtime.initializeAsync(null);
			supervisor.watch(initialization);
			initialization.get(5, TimeUnit.SECONDS);
			assertEquals("FAILED", runtime.status().state());
			assertEquals("PROJECT_LOAD_FAILED", runtime.status().error().code());
			HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.localPort()
					+ "/api/v1/status")).build();
			HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
			assertEquals(200, response.statusCode());
			assertEquals("PROJECT_LOAD_FAILED", JSON.readTree(response.body()).path("error").path("code").asText());
			assertEquals(0, exitRequests.get());
			supervisor.awaitFatalExitIfObserved();
			assertEquals(1, engineCloses.get());
		} finally {
			supervisor.close();
		}
	}

	@Test
	void intentionalShutdownCancellationDoesNotRequestFatalExit() {
		ProjectRuntime runtime = runtime(() -> { }, new AtomicInteger());
		AtomicInteger exitRequests = new AtomicInteger();
		StartupSupervisor supervisor = new StartupSupervisor(() -> { }, runtime, () -> { },
				code -> exitRequests.incrementAndGet());
		supervisor.close();
		supervisor.watch(CompletableFuture.failedFuture(new CancellationException("shutdown")));
		assertEquals(0, exitRequests.get());
		assertEquals("STOPPED", runtime.status().state());
	}

	@Test
	void shutdownHookAndFatalHandlerCloseResourcesOnlyOnce() throws Exception {
		CountDownLatch enteredLoad = new CountDownLatch(1);
		CountDownLatch releaseLoad = new CountDownLatch(1);
		AtomicInteger engineCloses = new AtomicInteger();
		ProjectRuntime runtime = runtime(() -> {
			enteredLoad.countDown();
			awaitIgnoringInterrupt(releaseLoad);
			throw new LinkageError("controlled fatal error");
		}, engineCloses);
		AtomicInteger listenerCloses = new AtomicInteger();
		CountDownLatch enteredListenerClose = new CountDownLatch(1);
		CountDownLatch releaseListenerClose = new CountDownLatch(1);
		CountDownLatch fatalHandlerEntered = new CountDownLatch(1);
		CountDownLatch exitRequested = new CountDownLatch(1);
		StartupSupervisor supervisor = new StartupSupervisor(() -> {
			listenerCloses.incrementAndGet();
			enteredListenerClose.countDown();
			awaitIgnoringInterrupt(releaseListenerClose);
		}, runtime, fatalHandlerEntered::countDown,
				code -> exitRequested.countDown());
		CompletableFuture<Void> initialization = runtime.initializeAsync(null);
		supervisor.watch(initialization);
		try {
			assertTrue(enteredLoad.await(5, TimeUnit.SECONDS));
			Thread hook = new Thread(supervisor::close);
			hook.start();
			assertTrue(enteredListenerClose.await(5, TimeUnit.SECONDS));
			releaseLoad.countDown();
			assertThrows(ExecutionException.class, () -> initialization.get(5, TimeUnit.SECONDS));
			assertTrue(fatalHandlerEntered.await(5, TimeUnit.SECONDS));
			assertEquals(1, exitRequested.getCount(), "Fatal exit waits for the first closer to finish");
			releaseListenerClose.countDown();
			hook.join(5_000);
			assertFalse(hook.isAlive());
			assertTrue(exitRequested.await(5, TimeUnit.SECONDS));
			assertEquals(1, listenerCloses.get());
			assertEquals(1, engineCloses.get());
			assertEquals("STOPPED", runtime.status().state());
		} finally {
			releaseLoad.countDown();
			releaseListenerClose.countDown();
			supervisor.close();
		}
	}

	private static ProjectRuntime runtime(LoadAction action, AtomicInteger closes) {
		return new ProjectRuntime(Path.of("fixture.jadx"), List.of(), args -> new ProjectRuntime.ProjectEngine() {
			private final JadxDecompiler decompiler = new JadxDecompiler(args);

			@Override public void load() throws Exception { action.run(); }
			@Override public JadxDecompiler decompiler() { return decompiler; }
			@Override public void close() { closes.incrementAndGet(); decompiler.close(); }
		});
	}

	private static void awaitIgnoringInterrupt(CountDownLatch latch) {
		boolean interrupted = false;
		while (true) {
			try {
				latch.await();
				break;
			} catch (InterruptedException ignored) {
				interrupted = true;
			}
		}
		if (interrupted) Thread.currentThread().interrupt();
	}

	private static void awaitState(ProjectRuntime runtime, String state) {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (!state.equals(runtime.status().state())) {
			if (System.nanoTime() >= deadline) {
				throw new AssertionError("Runtime did not enter " + state);
			}
			Thread.yield();
		}
	}

	@FunctionalInterface
	private interface LoadAction { void run() throws Exception; }
}
