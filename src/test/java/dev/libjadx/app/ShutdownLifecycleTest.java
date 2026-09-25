package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.libjadx.http.HttpApiServer;
import dev.libjadx.project.NativeProjectDocument;
import dev.libjadx.scheduler.JobSpec;
import dev.libjadx.scheduler.OperationCoordinator;
import dev.libjadx.scheduler.OperationRequest;
import dev.libjadx.scheduler.ServiceShuttingDownException;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShutdownLifecycleTest {
	private static final HttpClient HTTP = HttpClient.newHttpClient();
	private static final ObjectMapper JSON = new ObjectMapper();
	@TempDir Path dir;

	@Test void failedSaveReopensAdmissionsWithoutBlockingJettyThread() throws Exception {
		NativeProjectDocument project = fixture();
		CountDownLatch enteredSave = new CountDownLatch(1);
		CountDownLatch releaseSave = new CountDownLatch(1);
		ProjectRuntime runtime = runtime(project, new AtomicInteger(), (repository, target, session, revision) -> {
			enteredSave.countDown();
			try { assertTrue(releaseSave.await(10, TimeUnit.SECONDS)); }
			catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IOException(failure); }
			throw new IOException("controlled failure");
		});
		try (HttpApiServer server = start(runtime, project)) {
			JobSpec<Void> queued = spec(runtime);
			CompletableFuture<HttpResponse<String>> pending = HTTP.sendAsync(request(server, "POST", "/api/v1/shutdown", "{\"policy\":\"save\"}"), HttpResponse.BodyHandlers.ofString());
			assertTrue(enteredSave.await(5, TimeUnit.SECONDS));
			assertEquals(200, HTTP.send(request(server, "GET", "/api/v1/status", null), HttpResponse.BodyHandlers.ofString()).statusCode());
			assertEquals(409, shutdown(server).statusCode());
			assertThrows(ServiceShuttingDownException.class, () -> runtime.withPrimaryClassRead("blocked", jadx -> null));
			assertThrows(ServiceShuttingDownException.class, () -> runtime.submitJob(queued));
			assertThrows(ServiceShuttingDownException.class, () -> runtime.jobRegistry().submit(queued));
			releaseSave.countDown();
			HttpResponse<String> failed = pending.get(10, TimeUnit.SECONDS);
			assertEquals(500, failed.statusCode());
			assertEquals("INTERNAL_ERROR", JSON.readTree(failed.body()).path("error").path("code").asText());
			assertEquals("READY", runtime.status().state());
			assertDoesNotThrow(() -> runtime.withPrimaryClassRead("admitted", jadx -> null));
			var reopened = runtime.jobRegistry().submit(queued);
			assertTrue(runtime.jobRegistry().awaitTerminal(reopened.jobId(), Duration.ofSeconds(5)).terminal());
			assertEquals(200, HTTP.send(request(server, "GET", "/api/v1/project", null), HttpResponse.BodyHandlers.ofString()).statusCode());
		} finally { releaseSave.countDown(); runtime.close(); }
	}

	@Test void responseCompletesBeforeSupervisorClosesListenerAndDuplicateCannotDoubleClose() throws Exception {
		NativeProjectDocument project = fixture();
		AtomicInteger engineCloses = new AtomicInteger();
		ProjectRuntime runtime = runtime(project, engineCloses, (repository, target, session, revision) -> repository.save(target, session, revision));
		CountDownLatch triggered = new CountDownLatch(1);
		CountDownLatch allowStop = new CountDownLatch(1);
		AtomicInteger triggerCount = new AtomicInteger();
		final StartupSupervisor[] supervisorRef = new StartupSupervisor[1];
		ShutdownService requester = new ShutdownService(runtime, () -> {
			triggerCount.incrementAndGet();
			triggered.countDown();
			try { allowStop.await(); } catch (InterruptedException failure) { Thread.currentThread().interrupt(); }
			supervisorRef[0].close();
		});
		HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime, requester);
		StartupSupervisor supervisor = new StartupSupervisor(server, runtime, () -> { }, code -> { });
		supervisorRef[0] = supervisor;
		try {
			server.start();
			runtime.initializeAsync(project).get(5, TimeUnit.SECONDS);
			HttpResponse<String> accepted = HTTP.send(request(server, "POST", "/api/v1/shutdown", "{}"), HttpResponse.BodyHandlers.ofString());
			assertEquals(202, accepted.statusCode());
			assertEquals("SHUTTING_DOWN", JSON.readTree(accepted.body()).path("state").asText());
			assertTrue(triggered.await(5, TimeUnit.SECONDS));
			assertEquals(503, HTTP.send(request(server, "POST", "/api/v1/shutdown", "{}"), HttpResponse.BodyHandlers.ofString()).statusCode());
			assertEquals(200, HTTP.send(request(server, "GET", "/api/v1/status", null), HttpResponse.BodyHandlers.ofString()).statusCode());
			allowStop.countDown();
			CompletableFuture.runAsync(() -> {
				try { server.join(); } catch (InterruptedException failure) { throw new RuntimeException(failure); }
			}).get(10, TimeUnit.SECONDS);
			assertTrue(runtime.awaitStopped(10, TimeUnit.SECONDS));
			assertEquals(1, engineCloses.get());
			assertEquals(1, triggerCount.get());
		} finally { allowStop.countDown(); supervisor.close(); }
	}

	@Test void queuedJobAndExclusiveSaveBothRejectPublicShutdownWithoutCancellation() throws Exception {
		NativeProjectDocument project = fixture();
		CountDownLatch enteredSave = new CountDownLatch(1);
		CountDownLatch releaseSave = new CountDownLatch(1);
		ProjectRuntime runtime = runtime(project, new AtomicInteger(), (repository, target, session, revision) -> {
			enteredSave.countDown();
			try { assertTrue(releaseSave.await(10, TimeUnit.SECONDS)); }
			catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IOException(failure); }
			return repository.save(target, session, revision);
		});
		CountDownLatch enteredRead = new CountDownLatch(1);
		CountDownLatch releaseRead = new CountDownLatch(1);
		try (HttpApiServer server = start(runtime, project)) {
			CompletableFuture<Void> read = CompletableFuture.runAsync(() -> runtime.withPrimaryClassRead("held", jadx -> {
				enteredRead.countDown();
				try { releaseRead.await(); } catch (InterruptedException failure) { throw new RuntimeException(failure); }
				return null;
			}));
			assertTrue(enteredRead.await(5, TimeUnit.SECONDS));
			var job = runtime.submitJob(spec(runtime));
			assertEquals(409, shutdown(server).statusCode());
			assertEquals(dev.libjadx.scheduler.JobSnapshot.State.QUEUED, runtime.jobRegistry().snapshot(job.jobId()).state());
			runtime.jobRegistry().cancel(job.jobId());
			releaseRead.countDown();
			read.get(5, TimeUnit.SECONDS);
			assertTrue(runtime.jobRegistry().snapshot(job.jobId()).terminal());
			CompletableFuture<Void> save = CompletableFuture.runAsync(() -> {
				try { runtime.saveProject(null, null); } catch (IOException failure) { throw new RuntimeException(failure); }
			});
			assertTrue(enteredSave.await(5, TimeUnit.SECONDS));
			assertEquals(409, shutdown(server).statusCode());
			assertEquals("READY", runtime.status().state());
			releaseSave.countDown();
			save.get(10, TimeUnit.SECONDS);
			assertEquals(202, shutdown(server).statusCode());
			assertTrue(runtime.awaitStopped(10, TimeUnit.SECONDS));
		} finally { releaseRead.countDown(); releaseSave.countDown(); runtime.close(); }
	}

	private NativeProjectDocument fixture() throws Exception {
		Path source = Path.of("tests/fixtures/native-project").toAbsolutePath();
		for (String name : List.of("sample.jar.jadx", "sample.jar", "second.jar", "sample.tiny")) {
			Files.copy(source.resolve(name), dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
		}
		return NativeProjectDocument.open(dir.resolve("sample.jar.jadx"));
	}
	private static ProjectRuntime runtime(NativeProjectDocument project, AtomicInteger engineCloses, ProjectRuntime.SaveAction save) {
		return new ProjectRuntime(project.getProjectPath(), project.getInputFiles(), args -> new ProjectRuntime.ProjectEngine() {
			private final JadxDecompiler jadx = new JadxDecompiler(args);
			@Override public void load() { }
			@Override public JadxDecompiler decompiler() { return jadx; }
			@Override public void close() { engineCloses.incrementAndGet(); jadx.close(); }
		}, save);
	}
	private static HttpApiServer start(ProjectRuntime runtime, NativeProjectDocument project) throws Exception {
		HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime);
		server.start();
		runtime.initializeAsync(project).get(5, TimeUnit.SECONDS);
		return server;
	}
	private static JobSpec<Void> spec(ProjectRuntime runtime) {
		var revision = runtime.projectSnapshot().revisions();
		return new JobSpec<>("queued", OperationRequest.classRead("queued"),
				new OperationCoordinator.Admission(revision.sessionId(), revision.logicalRevision()),
				"snapshot", Duration.ofSeconds(30), null,
				(ctx, ignored) -> new JobSpec.JobResult("{}", JobSpec.Completeness.COMPLETE));
	}
	private static HttpResponse<String> shutdown(HttpApiServer server) throws Exception {
		return HTTP.send(request(server, "POST", "/api/v1/shutdown", "{}"), HttpResponse.BodyHandlers.ofString());
	}
	private static HttpRequest request(HttpApiServer server, String method, String path, String body) {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.localPort() + path));
		return "POST".equals(method) ? builder.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body)).build() : builder.GET().build();
	}
}
