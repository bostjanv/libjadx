package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.libjadx.http.HttpApiServer;
import dev.libjadx.http.JobHttpResponses;
import dev.libjadx.project.NativeProjectDocument;
import dev.libjadx.scheduler.JobLimits;
import dev.libjadx.scheduler.JobSnapshot;
import dev.libjadx.scheduler.JobSpec;
import dev.libjadx.scheduler.OperationCoordinator;
import dev.libjadx.scheduler.OperationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import jadx.api.JadxDecompiler;
import dev.libjadx.scheduler.ServiceShuttingDownException;

class JobEndpointsTest {
	@TempDir Path dir;
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
	private static final ObjectMapper JSON = new ObjectMapper();

	private static JobLimits limits(int subscribersPerJob) {
		return new JobLimits(2, 1, 4, 4096, 8192, 4, 256,
				3, 1024, subscribersPerJob, 4, 2, Duration.ofSeconds(10), Duration.ofMillis(30));
	}

	@Test void pollingCancelAndSseReplayUseOneAuthoritativeJob() throws Exception {
		NativeProjectDocument fixture = fixture();
		ProjectRuntime runtime = new ProjectRuntime(fixture.getProjectPath(), fixture.getInputFiles(), List.of(dir), limits(2));
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start();
			runtime.initializeAsync(fixture).get(20, TimeUnit.SECONDS);
			UUID id = runtime.submitJob(spec(runtime, "fixture-work", (ctx, ignored) -> {
				ctx.progress(1, null, "working");
				entered.countDown();
				assertTrue(release.await(10, TimeUnit.SECONDS));
				return new JobSpec.JobResult("{\"complete\":true}", JobSpec.Completeness.COMPLETE);
			})).jobId();
			assertTrue(entered.await(10, TimeUnit.SECONDS));
			assertEquals(200, get(server, "/api/v1/status").statusCode());
			HttpResponse<String> polled = get(server, "/api/v1/jobs/" + id);
			assertEquals(200, polled.statusCode());
			assertEquals("RUNNING", body(polled).path("state").asText());
			assertTrue(polled.headers().firstValue("X-Request-Id").isPresent());
			assertEquals("no-store", polled.headers().firstValue("Cache-Control").orElseThrow());
			assertTrue(body(polled).path("progress").path("total").isNull());
			assertEquals(202, JobHttpResponses.accepted(runtime.jobRegistry().snapshot(id), JSON).status());
			assertEquals("/api/v1/jobs/" + id,
					JobHttpResponses.accepted(runtime.jobRegistry().snapshot(id), JSON).location());

			HttpResponse<java.io.InputStream> stream = HTTP.send(HttpRequest.newBuilder(uri(server,
					"/api/v1/jobs/" + id + "/events")).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
			assertEquals(200, stream.statusCode());
			assertTrue(stream.headers().firstValue("Content-Type").orElseThrow().startsWith("text/event-stream"));
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream.body()))) {
				List<String> initial = linesUntil(reader, "event: job.started", 20);
				assertTrue(initial.contains("id: 1"));
				assertTrue(initial.contains("event: job.started"));
				assertTrue(linesUntil(reader, ": heartbeat", 10).contains(": heartbeat"));
				HttpResponse<java.io.InputStream> second = HTTP.send(HttpRequest.newBuilder(uri(server,
						"/api/v1/jobs/" + id + "/events")).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
				try (BufferedReader reader2 = new BufferedReader(new InputStreamReader(second.body()))) {
					assertEquals(200, second.statusCode());
					assertTrue(linesUntil(reader2, "event: job.started", 20).contains("event: job.started"));
					assertEquals("RUNNING", body(get(server, "/api/v1/jobs/" + id)).path("state").asText());
					HttpResponse<String> cancelled = post(server, "/api/v1/jobs/" + id + "/cancel", null);
					assertEquals(200, cancelled.statusCode());
					assertEquals("CANCELLING", body(cancelled).path("state").asText());
					assertEquals("CANCELLING", body(post(server, "/api/v1/jobs/" + id + "/cancel", null))
							.path("state").asText());
					assertEquals(200, get(server, "/api/v1/status").statusCode());
					release.countDown();
					awaitState(runtime, id, "CANCELLED");
					assertTrue(linesUntil(reader2, "event: job.cancelled", 20).contains("event: job.cancelled"));
				}
			}
			HttpResponse<String> badId = get(server, "/api/v1/jobs/not-a-uuid");
			assertEquals(400, badId.statusCode());
			assertEquals("INVALID_REQUEST", body(badId).path("error").path("code").asText());
			assertEquals(404, get(server, "/api/v1/jobs/" + UUID.randomUUID()).statusCode());
			assertEquals(400, events(server, id, "999").statusCode());
			assertEquals(400, events(server, id, "bad").statusCode());

			HttpResponse<String> forbidden = post(server, "/api/v1/jobs/" + id + "/cancel", "http://evil.example");
			assertEquals(403, forbidden.statusCode());
			assertEquals("INPUT_SECURITY_REJECTION", body(forbidden).path("error").path("code").asText());
			assertEquals("CANCELLED", body(get(server, "/api/v1/jobs/" + id)).path("state").asText());
			assertTrue(body(get(server, "/api/v1/jobs/" + id)).path("result").isNull());
			HttpResponse<String> replay = events(server, id, null);
			assertEquals(200, replay.statusCode());
			assertTrue(replay.body().contains("event: job.cancelled"));
			assertTrue(replay.body().contains("id: "));
			HttpResponse<String> resumed = events(server, id, "2");
			assertEquals(200, resumed.statusCode());
			assertTrue(resumed.body().contains("id: 3"));
			assertFalse(resumed.body().contains("id: 1"));
			System.out.println("LIBJADX_SSE_SAMPLE\n" + replay.body());
			assertEquals(409, events(server, id, "1").statusCode());
			assertEquals("EVENT_HISTORY_EXPIRED", body(events(server, id, "1"))
					.path("error").path("code").asText());
			assertEquals("CANCELLED", body(post(server, "/api/v1/jobs/" + id + "/cancel", null))
					.path("state").asText());
		} finally {
			release.countDown();
			runtime.close();
		}
	}

	@Test void subscriberBudgetReturns429AndRestartForgetsJobs() throws Exception {
		NativeProjectDocument fixture = fixture();
		ProjectRuntime runtime = new ProjectRuntime(fixture.getProjectPath(), fixture.getInputFiles(), List.of(dir), limits(1));
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		UUID id;
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start();
			runtime.initializeAsync(fixture).get(20, TimeUnit.SECONDS);
			id = runtime.submitJob(spec(runtime, "hold", (ctx, ignored) -> {
				entered.countDown();
				assertTrue(release.await(10, TimeUnit.SECONDS));
				return new JobSpec.JobResult("{}", JobSpec.Completeness.COMPLETE);
			})).jobId();
			assertTrue(entered.await(10, TimeUnit.SECONDS));
			HttpResponse<java.io.InputStream> first = HTTP.send(HttpRequest.newBuilder(uri(server,
					"/api/v1/jobs/" + id + "/events")).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
			try (var ignored = first.body()) {
				HttpResponse<String> limited = events(server, id, null);
				assertEquals(429, limited.statusCode());
				assertEquals("RESOURCE_LIMIT", body(limited).path("error").path("code").asText());
			} finally { release.countDown(); }
			awaitState(runtime, id, "SUCCEEDED");
		} finally {
			release.countDown();
			runtime.close();
		}
		ProjectRuntime restarted = new ProjectRuntime(fixture.getProjectPath(), fixture.getInputFiles(), List.of(dir), limits(1));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, restarted)) {
			server.start();
			restarted.initializeAsync(fixture).get(20, TimeUnit.SECONDS);
			assertEquals(404, get(server, "/api/v1/jobs/" + id).statusCode());
		} finally { restarted.close(); }
	}

	@Test void shutdownCancelsQueueAndDefersEngineCloseUntilLeasedJobReturns() throws Exception {
		NativeProjectDocument fixture = fixture();
		AtomicInteger engineCloses = new AtomicInteger();
		ProjectRuntime runtime = new ProjectRuntime(fixture.getProjectPath(), fixture.getInputFiles(), args ->
				new ProjectRuntime.ProjectEngine() {
					private final JadxDecompiler jadx = new JadxDecompiler(args);
					@Override public void load() { jadx.load(); }
					@Override public JadxDecompiler decompiler() { return jadx; }
					@Override public void close() { engineCloses.incrementAndGet(); jadx.close(); }
				}, limits(2));
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		AtomicInteger queuedRuns = new AtomicInteger();
		try {
			runtime.initializeAsync(fixture).get(20, TimeUnit.SECONDS);
			UUID running = runtime.submitJob(spec(runtime, "running", (ctx, ignored) -> {
				entered.countDown();
				assertTrue(release.await(10, TimeUnit.SECONDS));
				return new JobSpec.JobResult("{}", JobSpec.Completeness.COMPLETE);
			})).jobId();
			assertTrue(entered.await(10, TimeUnit.SECONDS));
			UUID queued = runtime.submitJob(spec(runtime, "queued", (ctx, ignored) -> {
				queuedRuns.incrementAndGet();
				return new JobSpec.JobResult("{}", JobSpec.Completeness.COMPLETE);
			})).jobId();
			runtime.close();
			assertEquals("SHUTTING_DOWN", runtime.status().state());
			assertEquals(JobSnapshot.State.CANCELLING, runtime.jobRegistry().snapshot(running).state());
			assertEquals(JobSnapshot.State.CANCELLED, runtime.jobRegistry().snapshot(queued).state());
			assertEquals(0, queuedRuns.get());
			assertEquals(0, engineCloses.get());
			assertThrows(ServiceShuttingDownException.class,
					() -> runtime.submitJob(spec(runtime, "too-late", (ctx, ignored) ->
							new JobSpec.JobResult("{}", JobSpec.Completeness.COMPLETE))));
			assertFalse(runtime.awaitStopped(100, TimeUnit.MILLISECONDS));
			release.countDown();
			assertTrue(runtime.awaitStopped(5, TimeUnit.SECONDS));
			assertEquals(JobSnapshot.State.CANCELLED,
					runtime.jobRegistry().awaitTerminal(running, Duration.ofSeconds(5)).state());
			assertEquals(1, engineCloses.get());
			runtime.close();
			assertEquals(1, engineCloses.get());
		} finally { release.countDown(); runtime.close(); }
	}

	@Test void fatalJobFailureUsesExistingSupervisor() throws Exception {
		NativeProjectDocument fixture = fixture();
		ProjectRuntime runtime = new ProjectRuntime(fixture.getProjectPath(), fixture.getInputFiles(), List.of(dir), limits(2));
		CountDownLatch exitRequested = new CountDownLatch(1);
		AtomicInteger exitCode = new AtomicInteger();
		StartupSupervisor supervisor = new StartupSupervisor(() -> { }, runtime, () -> { }, code -> {
			exitCode.set(code);
			exitRequested.countDown();
		});
		try {
			runtime.initializeAsync(fixture).get(20, TimeUnit.SECONDS);
			UUID id = runtime.submitJob(spec(runtime, "fatal", (ctx, ignored) -> {
				throw new LinkageError("controlled fatal job");
			})).jobId();
			assertEquals(JobSnapshot.State.FAILED,
					runtime.jobRegistry().awaitTerminal(id, Duration.ofSeconds(5)).state());
			assertTrue(exitRequested.await(10, TimeUnit.SECONDS));
			assertEquals(1, exitCode.get());
			assertTrue(runtime.fatalRuntimeFailure().isCompletedExceptionally());
			assertEquals("STOPPED", runtime.status().state());
		} finally { supervisor.close(); }
	}

	@Test void fatalJobKeepsPrimaryEngineOwnedByAnotherActiveLease() throws Exception {
		NativeProjectDocument fixture = fixture();
		AtomicInteger engineCloses = new AtomicInteger();
		JobLimits concurrent = new JobLimits(2, 2, 4, 4096, 8192, 4, 256,
				3, 1024, 2, 4, 2, Duration.ofSeconds(10), Duration.ofMillis(30));
		ProjectRuntime runtime = new ProjectRuntime(fixture.getProjectPath(), fixture.getInputFiles(), args ->
				new ProjectRuntime.ProjectEngine() {
					private final JadxDecompiler jadx = new JadxDecompiler(args);
					@Override public void load() { jadx.load(); }
					@Override public JadxDecompiler decompiler() { return jadx; }
					@Override public void close() { engineCloses.incrementAndGet(); jadx.close(); }
				}, concurrent);
		CountDownLatch fatalEntered = new CountDownLatch(1);
		CountDownLatch triggerFatal = new CountDownLatch(1);
		CountDownLatch primaryEntered = new CountDownLatch(1);
		CountDownLatch releasePrimary = new CountDownLatch(1);
		CountDownLatch exitRequested = new CountDownLatch(1);
		StartupSupervisor supervisor = new StartupSupervisor(() -> { }, runtime, () -> { }, code -> {
			assertEquals(1, code);
			exitRequested.countDown();
		});
		try {
			runtime.initializeAsync(fixture).get(20, TimeUnit.SECONDS);
			var revision = runtime.projectSnapshot().revisions();
			UUID fatal = runtime.submitJob(new JobSpec<>("fatal-isolated",
					OperationRequest.temporaryAnalysis("test-snapshot"),
					new OperationCoordinator.Admission(revision.sessionId(), revision.logicalRevision()),
					"test-snapshot", Duration.ofSeconds(30), () -> "immutable test snapshot", (ctx, ignored) -> {
						fatalEntered.countDown();
						assertTrue(triggerFatal.await(10, TimeUnit.SECONDS));
						throw new LinkageError("controlled fatal overlap");
					})).jobId();
			assertTrue(fatalEntered.await(10, TimeUnit.SECONDS));
			UUID primary = runtime.submitJob(spec(runtime, "primary", (ctx, ignored) -> {
				primaryEntered.countDown();
				assertTrue(releasePrimary.await(10, TimeUnit.SECONDS));
				return new JobSpec.JobResult("{}", JobSpec.Completeness.COMPLETE);
			})).jobId();
			assertTrue(primaryEntered.await(10, TimeUnit.SECONDS));
			triggerFatal.countDown();
			assertEquals(JobSnapshot.State.FAILED,
					runtime.jobRegistry().awaitTerminal(fatal, Duration.ofSeconds(5)).state());
			assertTrue(exitRequested.await(10, TimeUnit.SECONDS));
			assertEquals(JobSnapshot.State.CANCELLING, runtime.jobRegistry().snapshot(primary).state());
			assertEquals(0, engineCloses.get());
			releasePrimary.countDown();
			assertEquals(JobSnapshot.State.CANCELLED,
					runtime.jobRegistry().awaitTerminal(primary, Duration.ofSeconds(5)).state());
			assertTrue(runtime.awaitStopped(5, TimeUnit.SECONDS));
			assertEquals(1, engineCloses.get());
		} finally {
			triggerFatal.countDown();
			releasePrimary.countDown();
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

	private static JobSpec<Void> spec(ProjectRuntime runtime, String name, JobSpec.JobTask<Void> task) {
		var revisions = runtime.projectSnapshot().revisions();
		return new JobSpec<>(name, OperationRequest.classRead(name),
				new OperationCoordinator.Admission(revisions.sessionId(), revisions.logicalRevision()),
				"test-snapshot:" + revisions.logicalRevision(), Duration.ofSeconds(30), null, task);
	}

	private static URI uri(HttpApiServer server, String path) {
		return URI.create("http://127.0.0.1:" + server.localPort() + path);
	}

	private static HttpResponse<String> get(HttpApiServer server, String path) throws Exception {
		return HTTP.send(HttpRequest.newBuilder(uri(server, path)).GET().build(), HttpResponse.BodyHandlers.ofString());
	}

	private static HttpResponse<String> post(HttpApiServer server, String path, String origin) throws Exception {
		HttpRequest.Builder builder = HttpRequest.newBuilder(uri(server, path))
				.POST(HttpRequest.BodyPublishers.noBody());
		if (origin != null) builder.header("Origin", origin);
		return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
	}

	private static HttpResponse<String> events(HttpApiServer server, UUID id, String lastId) throws Exception {
		HttpRequest.Builder builder = HttpRequest.newBuilder(uri(server, "/api/v1/jobs/" + id + "/events")).GET();
		if (lastId != null) builder.header("Last-Event-ID", lastId);
		return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
	}

	private static JsonNode body(HttpResponse<String> response) throws Exception { return JSON.readTree(response.body()); }

	private static String line(BufferedReader reader) throws Exception {
		return CompletableFuture.supplyAsync(() -> {
			try { return reader.readLine(); }
			catch (Exception failure) { throw new RuntimeException(failure); }
		}).get(5, TimeUnit.SECONDS);
	}

	private static List<String> linesUntil(BufferedReader reader, String expected, int limit) throws Exception {
		List<String> lines = new ArrayList<>();
		for (int i = 0; i < limit; i++) {
			String next = line(reader);
			assertNotNull(next);
			lines.add(next);
			if (expected.equals(next)) return lines;
		}
		fail("SSE event not found: " + expected);
		return lines;
	}

	private static void awaitState(ProjectRuntime runtime, UUID id, String expected) throws Exception {
		assertEquals(expected, runtime.jobRegistry().awaitTerminal(id, Duration.ofSeconds(5)).state().name());
	}
}
