package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.libjadx.core.ProjectSnapshot;
import dev.libjadx.http.HttpApiServer;
import dev.libjadx.project.NativeProjectDocument;
import dev.libjadx.project.NativeProjectRepository;
import dev.libjadx.scheduler.ProjectBusyException;
import dev.libjadx.scheduler.ServiceShuttingDownException;
import jadx.api.JadxDecompiler;
import jadx.api.data.impl.JadxCodeData;
import jadx.api.data.impl.JadxCodeRename;
import jadx.api.data.impl.JadxNodeRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RebuildLifecycleTest {
	@TempDir Path dir;
	private static final HttpClient HTTP = HttpClient.newHttpClient();
	private static final ObjectMapper JSON = new ObjectMapper();

	@Test
	void twoHttpClientsReceivePromptBusyConflictDuringNativeSave() throws Exception {
		NativeProjectDocument project = fixture();
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ProjectRuntime runtime = runtime(project, new AtomicInteger(), () -> { }, (repository, target, session, revision) -> {
			entered.countDown();
			awaitIgnoringInterrupt(release);
			return repository.save(target, session, revision);
		});
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start();
			runtime.initializeAsync(project).get(20, TimeUnit.SECONDS);
			String session = runtime.projectSnapshot().revisions().sessionId();
			HttpRequest saveRequest = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.localPort()
					+ "/api/v1/project/save"))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString("{}"))
					.build();
			CompletableFuture<HttpResponse<String>> save = HTTP.sendAsync(saveRequest, HttpResponse.BodyHandlers.ofString());
			try {
				assertTrue(entered.await(10, TimeUnit.SECONDS));
				String reloadBody = "{\"discardUnsaved\":false,\"expectedSessionId\":\"" + session
						+ "\",\"expectedLogicalRevision\":0}";
				HttpRequest reload = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
						+ server.localPort() + "/api/v1/project/reload"))
						.header("Content-Type", "application/json")
						.POST(HttpRequest.BodyPublishers.ofString(reloadBody)).build();
				HttpResponse<String> busy = HTTP.sendAsync(reload, HttpResponse.BodyHandlers.ofString())
						.get(3, TimeUnit.SECONDS);
				assertEquals(409, busy.statusCode());
				assertEquals("PROJECT_BUSY", JSON.readTree(busy.body()).path("error").path("code").asText());
				assertTrue(JSON.readTree(busy.body()).path("error").path("retryable").asBoolean());
				assertEquals(200, get(server, "/api/v1/status").statusCode());
				assertThrows(ProjectBusyException.class, () -> runtime.withPrimaryClassRead("probe.Sample", jadx -> 1));
				assertThrows(ProjectBusyException.class, () -> runtime.saveProject(null, session, 0L));
				assertThrows(ProjectBusyException.class, () -> runtime.updateMappingsPath(project.getMappingsPath(), session, 0));
				assertThrows(ProjectBusyException.class, () -> runtime.replaceCodeData(project.getCodeData(), 0));
			} finally {
				release.countDown();
			}
			assertEquals(200, save.get(10, TimeUnit.SECONDS).statusCode());
			HttpRequest retry = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
					+ server.localPort() + "/api/v1/project/reload"))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString("{\"discardUnsaved\":false,\"expectedSessionId\":\""
							+ session + "\",\"expectedLogicalRevision\":0}"))
					.build();
			assertEquals(200, HTTP.send(retry, HttpResponse.BodyHandlers.ofString()).statusCode());
		} finally {
			release.countDown();
			runtime.close();
		}
	}

	@Test
	void scopedClassReadDefersPrimaryEngineCloseAndSerializesDifferentClasses() throws Exception {
		NativeProjectDocument project = fixture();
		AtomicInteger closes = new AtomicInteger();
		ProjectRuntime runtime = runtime(project, closes, () -> { });
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		try {
			runtime.initializeAsync(project).get(20, TimeUnit.SECONDS);
			CompletableFuture<Integer> read = CompletableFuture.supplyAsync(() -> runtime.withPrimaryClassRead(
					"probe.Sample", jadx -> {
						entered.countDown();
						awaitIgnoringInterrupt(release);
						return jadx.getClasses().size();
					}));
			assertTrue(entered.await(10, TimeUnit.SECONDS));
			assertThrows(ProjectBusyException.class, () -> runtime.withPrimaryClassRead("probe.Second", jadx -> 0));
			CompletableFuture.runAsync(runtime::close).get(2, TimeUnit.SECONDS);
			assertEquals("SHUTTING_DOWN", runtime.status().state());
			assertEquals(0, closes.get());
			release.countDown();
			assertTrue(read.get(10, TimeUnit.SECONDS) > 0);
			assertTrue(runtime.awaitStopped(2, TimeUnit.SECONDS));
			assertEquals(1, closes.get());
		} finally {
			release.countDown();
			runtime.close();
		}
	}

	@Test
	void shutdownIsBoundedWhileAnAdmittedNativeSaveIsBlocked() throws Exception {
		NativeProjectDocument project = fixture();
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		AtomicInteger closes = new AtomicInteger();
		ProjectRuntime runtime = runtime(project, closes, () -> { }, (repository, target, session, revision) -> {
			synchronized (repository) {
				entered.countDown();
				awaitIgnoringInterrupt(release);
				return repository.save(target, session, revision);
			}
		});
		AtomicInteger listenerCloses = new AtomicInteger();
		StartupSupervisor supervisor = new StartupSupervisor(listenerCloses::incrementAndGet, runtime, () -> { }, code -> { });
		try {
			runtime.initializeAsync(project).get(20, TimeUnit.SECONDS);
			var edited = NativeProjectDocument.copyCodeData(project.getCodeData());
			edited.setRenames(List.of(new JadxCodeRename(JadxNodeRef.forCls("probe.Sample"), "SavedAfterShutdown")));
			runtime.replaceCodeData(edited, 0);
			String session = runtime.projectSnapshot().revisions().sessionId();
			CompletableFuture<ProjectSnapshot> save = CompletableFuture.supplyAsync(() -> {
				try { return runtime.saveProject(null, session, 1L); }
				catch (Exception failure) { throw new CompletionException(failure); }
			});
			assertTrue(entered.await(10, TimeUnit.SECONDS));
			AtomicReference<Thread> readerThread = new AtomicReference<>();
			CompletableFuture<ProjectSnapshot> concurrentRead = CompletableFuture.supplyAsync(() -> {
				readerThread.set(Thread.currentThread());
				return runtime.projectSnapshot();
			});
			awaitBlocked(readerThread);
			CompletableFuture.runAsync(supervisor::close).get(7, TimeUnit.SECONDS);
			assertEquals(1, listenerCloses.get());
			assertEquals("SHUTTING_DOWN", runtime.status().state());
			assertFalse(save.isDone());
			assertFalse(runtime.awaitStopped(50, TimeUnit.MILLISECONDS));
			release.countDown();
			assertFalse(save.get(10, TimeUnit.SECONDS).dirty());
			ExecutionException readFailure = assertThrows(ExecutionException.class,
					() -> concurrentRead.get(10, TimeUnit.SECONDS));
			assertTrue(readFailure.getCause() instanceof ServiceShuttingDownException);
			assertTrue(runtime.awaitStopped(2, TimeUnit.SECONDS));
			assertEquals(1, closes.get());
			assertTrue(Files.readString(project.getProjectPath()).contains("SavedAfterShutdown"));
		} finally {
			release.countDown();
			supervisor.close();
		}
	}

	@Test
	void shutdownDefersPrimaryEngineCleanupUntilCodeDataReloadFinishes() throws Exception {
		NativeProjectDocument project = fixture();
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		AtomicInteger closes = new AtomicInteger();
		ProjectRuntime runtime = new ProjectRuntime(project.getProjectPath(), project.getInputFiles(), args ->
				new ProjectRuntime.ProjectEngine() {
					private final JadxDecompiler jadx = new JadxDecompiler(args);
					@Override public void load() { jadx.load(); }
					@Override public JadxDecompiler decompiler() { return jadx; }
					@Override public void reloadCodeData(JadxCodeData codeData) {
						entered.countDown();
						awaitIgnoringInterrupt(release);
						ProjectRuntime.ProjectEngine.super.reloadCodeData(codeData);
					}
					@Override public void close() {
						closes.incrementAndGet();
						jadx.close();
					}
				});
		StartupSupervisor supervisor = new StartupSupervisor(() -> { }, runtime, () -> { }, code -> { });
		try {
			runtime.initializeAsync(project).get(20, TimeUnit.SECONDS);
			var edited = NativeProjectDocument.copyCodeData(project.getCodeData());
			edited.setRenames(List.of(new JadxCodeRename(JadxNodeRef.forCls("probe.Sample"), "DiscardOnClose")));
			CompletableFuture<Void> edit = CompletableFuture.runAsync(() -> runtime.replaceCodeData(edited, 0));
			assertTrue(entered.await(10, TimeUnit.SECONDS));
			CompletableFuture.runAsync(supervisor::close).get(7, TimeUnit.SECONDS);
			assertEquals("SHUTTING_DOWN", runtime.status().state());
			assertEquals(0, closes.get());
			release.countDown();
			edit.get(10, TimeUnit.SECONDS);
			assertTrue(runtime.awaitStopped(2, TimeUnit.SECONDS));
			assertEquals(1, closes.get());
			assertFalse(Files.readString(project.getProjectPath()).contains("DiscardOnClose"));
		} finally {
			release.countDown();
			supervisor.close();
		}
	}

	@Test
	void shutdownReturnsWhileMappingRebuildIsBlockedAndStopsAfterCleanup() throws Exception {
		NativeProjectDocument project = fixture();
		Path other = dir.resolve("other.tiny");
		Files.writeString(other, "tiny\t2\t0\toriginal\tmapped\nc\tprobe/Second\tprobe/OtherSecond\n");
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		AtomicInteger closes = new AtomicInteger();
		ProjectRuntime runtime = runtime(project, closes, () -> {
			entered.countDown();
			release.await();
		});
		AtomicInteger listenerCloses = new AtomicInteger();
		StartupSupervisor supervisor = new StartupSupervisor(listenerCloses::incrementAndGet, runtime, () -> { }, code -> { });
		try {
			runtime.initializeAsync(project).get(20, TimeUnit.SECONDS);
			String session = runtime.projectSnapshot().revisions().sessionId();
			CompletableFuture<?> rebuild = CompletableFuture.supplyAsync(() -> {
				try { return runtime.updateMappingsPath(other, session, 0); }
				catch (Exception failure) { throw new CompletionException(failure); }
			});
			assertTrue(entered.await(10, TimeUnit.SECONDS));
			CompletableFuture.runAsync(supervisor::close).get(7, TimeUnit.SECONDS);
			assertEquals(1, listenerCloses.get());
			assertEquals("SHUTTING_DOWN", runtime.status().state());
			assertFalse(runtime.awaitStopped(50, TimeUnit.MILLISECONDS));
			release.countDown();
			assertThrows(java.util.concurrent.ExecutionException.class, () -> rebuild.get(10, TimeUnit.SECONDS));
			assertTrue(runtime.awaitStopped(2, TimeUnit.SECONDS));
			assertEquals(2, closes.get());
		} finally {
			release.countDown();
			supervisor.close();
		}
	}

	@Test
	void reloadKeepsOldStateHiddenUntilMatchingEngineIsPublished() throws Exception {
		NativeProjectDocument project = fixture();
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ProjectRuntime runtime = runtime(project, new AtomicInteger(), () -> {
			entered.countDown();
			release.await();
		});
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start();
			runtime.initializeAsync(project).get(20, TimeUnit.SECONDS);
			JadxDecompiler oldEngine = runtime.decompiler();
			String session = runtime.projectSnapshot().revisions().sessionId();
			NativeProjectDocument changed = NativeProjectDocument.open(project.getProjectPath());
			changed.getCodeData().setRenames(List.of(new JadxCodeRename(JadxNodeRef.forCls("probe.Sample"), "ReloadedAlias")));
			changed.save();
			CompletableFuture<ProjectSnapshot> rebuild = CompletableFuture.supplyAsync(() -> {
				try { return runtime.reloadProject(false, session, 0); }
				catch (Exception failure) { throw new CompletionException(failure); }
			});
			try {
				assertTrue(entered.await(10, TimeUnit.SECONDS));
				assertEquals("RELOADING", runtime.status().state());
				HttpResponse<String> unavailable = get(server, "/api/v1/project");
				assertEquals(503, unavailable.statusCode());
				assertEquals("PROJECT_NOT_READY", JSON.readTree(unavailable.body()).path("error").path("code").asText());
				assertEquals(503, get(server, "/api/v1/project/settings").statusCode());
				assertTrue(oldEngine.getClasses().stream().noneMatch(cls -> cls.getFullName().equals("probe.ReloadedAlias")));
			} finally {
				release.countDown();
			}
			assertEquals(1, rebuild.get(20, TimeUnit.SECONDS).revisions().logicalRevision());
			assertEquals(1, JSON.readTree(get(server, "/api/v1/project").body())
					.path("revisions").path("logicalRevision").asLong());
			assertTrue(runtime.decompiler().getClasses().stream()
					.anyMatch(cls -> cls.getFullName().equals("probe.ReloadedAlias")));
		} finally {
			release.countDown();
			runtime.close();
		}
	}

	@Test
	void unreadableReloadKeepsThePublishedEngineAndRevision() throws Exception {
		NativeProjectDocument project = fixture();
		ProjectRuntime runtime = runtime(project, new AtomicInteger(), () -> { });
		try {
			runtime.initializeAsync(project).get(20, TimeUnit.SECONDS);
			JadxDecompiler original = runtime.decompiler();
			String session = runtime.projectSnapshot().revisions().sessionId();
			Files.writeString(project.getProjectPath(), "not JSON");
			assertThrows(Exception.class, () -> runtime.reloadProject(false, session, 0));
			assertEquals("READY", runtime.status().state());
			assertSame(original, runtime.decompiler());
			assertEquals(0, runtime.projectSnapshot().revisions().logicalRevision());
		} finally {
			runtime.close();
		}
	}

	@Test
	void mappingChangeDuringLoadReturnsStructuredConflict() throws Exception {
		NativeProjectDocument project = fixture();
		Path replacement = dir.resolve("changed-during-load.tiny");
		Files.writeString(replacement, "tiny\t2\t0\toriginal\tmapped\n");
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ProjectRuntime runtime = runtime(project, new AtomicInteger(), () -> {
			entered.countDown();
			release.await();
		});
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start();
			runtime.initializeAsync(project).get(20, TimeUnit.SECONDS);
			String session = runtime.projectSnapshot().revisions().sessionId();
			String body = JSON.createObjectNode()
					.put("mappingsPath", replacement.toString())
					.put("expectedSessionId", session)
					.put("expectedLogicalRevision", 0).toString();
			HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.localPort()
					+ "/api/v1/project/settings"))
					.header("Content-Type", "application/json")
					.method("PATCH", HttpRequest.BodyPublishers.ofString(body)).build();
			CompletableFuture<HttpResponse<String>> response = HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString());
			try {
				assertTrue(entered.await(10, TimeUnit.SECONDS));
				Files.writeString(replacement, "tiny\t2\t0\toriginal\tchanged\n");
			} finally {
				release.countDown();
			}
			HttpResponse<String> conflict = response.get(20, TimeUnit.SECONDS);
			assertEquals(409, conflict.statusCode());
			assertEquals("EXTERNAL_MODIFICATION_CONFLICT",
					JSON.readTree(conflict.body()).path("error").path("code").asText());
			assertEquals("READY", runtime.status().state());
			assertEquals(project.getMappingsPath(), runtime.settingsSnapshot().mappingsPath());
			assertEquals(0, runtime.projectSnapshot().revisions().logicalRevision());
		} finally {
			release.countDown();
			runtime.close();
		}
	}

	@Test
	void fatalMappingRebuildRequestsNonzeroSupervisedShutdown() throws Exception {
		assertFatalRebuild(true);
	}

	@Test
	void fatalProjectReloadRequestsNonzeroSupervisedShutdown() throws Exception {
		assertFatalRebuild(false);
	}

	@Test
	void fatalCodeDataReloadRequestsNonzeroSupervisedShutdown() throws Exception {
		NativeProjectDocument project = fixture();
		AtomicInteger closes = new AtomicInteger();
		ProjectRuntime runtime = new ProjectRuntime(project.getProjectPath(), project.getInputFiles(), args ->
				new ProjectRuntime.ProjectEngine() {
					private final JadxDecompiler jadx = new JadxDecompiler(args);
					@Override public void load() { jadx.load(); }
					@Override public JadxDecompiler decompiler() { return jadx; }
					@Override public void reloadCodeData(JadxCodeData data) {
						throw new LinkageError("controlled fatal edit");
					}
					@Override public void close() { closes.incrementAndGet(); jadx.close(); }
				});
		CountDownLatch exitRequested = new CountDownLatch(1);
		AtomicInteger exitCode = new AtomicInteger();
		StartupSupervisor supervisor = new StartupSupervisor(() -> { }, runtime, () -> { }, code -> {
			exitCode.set(code);
			exitRequested.countDown();
		});
		try {
			runtime.initializeAsync(project).get(20, TimeUnit.SECONDS);
			assertThrows(LinkageError.class, () -> runtime.replaceCodeData(project.getCodeData(), 0));
			assertTrue(exitRequested.await(10, TimeUnit.SECONDS));
			assertEquals(1, exitCode.get());
			assertEquals(1, closes.get());
			assertEquals("STOPPED", runtime.status().state());
		} finally {
			supervisor.close();
		}
	}

	private void assertFatalRebuild(boolean mapping) throws Exception {
		NativeProjectDocument project = fixture();
		AtomicInteger closes = new AtomicInteger();
		ProjectRuntime runtime = runtime(project, closes, () -> { throw new LinkageError("controlled fatal rebuild"); });
		AtomicInteger listenerCloses = new AtomicInteger();
		AtomicInteger exitCode = new AtomicInteger();
		CountDownLatch exitRequested = new CountDownLatch(1);
		StartupSupervisor supervisor = new StartupSupervisor(listenerCloses::incrementAndGet, runtime, () -> { }, code -> {
			exitCode.set(code);
			exitRequested.countDown();
		});
		CompletableFuture<Void> initialization = runtime.initializeAsync(project);
		supervisor.watch(initialization);
		try {
			initialization.get(20, TimeUnit.SECONDS);
			assertTrue(runtime.isReady());
			String session = runtime.projectSnapshot().revisions().sessionId();
			if (mapping) {
				Path other = dir.resolve("fatal.tiny");
				Files.writeString(other, "tiny\t2\t0\toriginal\tmapped\n");
				assertThrows(LinkageError.class, () -> runtime.updateMappingsPath(other, session, 0));
			} else {
				assertThrows(LinkageError.class, () -> runtime.reloadProject(false, session, 0));
			}
			assertTrue(exitRequested.await(10, TimeUnit.SECONDS));
			assertEquals(1, exitCode.get());
			assertEquals(1, listenerCloses.get());
			assertEquals(2, closes.get());
			assertEquals("STOPPED", runtime.status().state());
			assertTrue(runtime.fatalRuntimeFailure().isCompletedExceptionally());
		} finally {
			supervisor.close();
		}
	}

	private NativeProjectDocument fixture() throws Exception {
		Path source = Path.of("tests/fixtures/native-project").toAbsolutePath();
		for (String name : List.of("sample.jar.jadx", "sample.jar", "second.jar", "sample.tiny")) {
			Files.copy(source.resolve(name), dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
		}
		return NativeProjectDocument.open(dir.resolve("sample.jar.jadx"));
	}

	private static ProjectRuntime runtime(NativeProjectDocument project, AtomicInteger closes,
			LoadAction beforeReplacementLoad) {
		return runtime(project, closes, beforeReplacementLoad, NativeProjectRepository::save);
	}

	private static ProjectRuntime runtime(NativeProjectDocument project, AtomicInteger closes,
			LoadAction beforeReplacementLoad, ProjectRuntime.SaveAction saveAction) {
		AtomicInteger creations = new AtomicInteger();
		return new ProjectRuntime(project.getProjectPath(), project.getInputFiles(), args -> {
			int sequence = creations.incrementAndGet();
			return new ProjectRuntime.ProjectEngine() {
				private final JadxDecompiler jadx = new JadxDecompiler(args);
				@Override public void load() throws Exception {
					if (sequence == 2) beforeReplacementLoad.run();
					jadx.load();
				}
				@Override public JadxDecompiler decompiler() { return jadx; }
				@Override public void close() {
					closes.incrementAndGet();
					jadx.close();
				}
			};
		}, saveAction);
	}

	private static void awaitIgnoringInterrupt(CountDownLatch latch) {
		boolean interrupted = false;
		while (true) {
			try {
				latch.await();
				break;
			} catch (InterruptedException failure) {
				interrupted = true;
			}
		}
		if (interrupted) Thread.currentThread().interrupt();
	}

	private static void awaitBlocked(AtomicReference<Thread> thread) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (System.nanoTime() < deadline) {
			Thread current = thread.get();
			if (current != null && current.getState() == Thread.State.BLOCKED) return;
			Thread.sleep(5);
		}
		throw new AssertionError("Concurrent read did not wait for native save");
	}

	@FunctionalInterface
	private interface LoadAction {
		void run() throws Exception;
	}

	private static HttpResponse<String> get(HttpApiServer server, String path) throws Exception {
		return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.localPort() + path))
				.GET().build(), HttpResponse.BodyHandlers.ofString());
	}
}
