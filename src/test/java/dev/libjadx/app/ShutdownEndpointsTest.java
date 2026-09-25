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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.libjadx.http.HttpApiServer;
import dev.libjadx.core.EffectiveAnalysisConfig;
import dev.libjadx.project.NativeProjectDocument;
import dev.libjadx.scheduler.JobSnapshot;
import dev.libjadx.scheduler.JobSpec;
import dev.libjadx.scheduler.OperationCoordinator;
import dev.libjadx.scheduler.OperationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import jadx.api.JadxDecompiler;
import jadx.api.data.impl.JadxCodeRename;
import jadx.api.data.impl.JadxNodeRef;

class ShutdownEndpointsTest {
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
	@TempDir Path dir;

	@Test void shutdownBeforeReadyReturnsLifecycleError() throws Exception {
		NativeProjectDocument nativeProject = fixture();
		ProjectRuntime runtime = new ProjectRuntime(nativeProject.getProjectPath(), nativeProject.getInputFiles(), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start();
			HttpResponse<String> response = post(server, "{}", null);
			assertEquals(503, response.statusCode());
			assertEquals("PROJECT_NOT_READY", body(response).path("error").path("code").asText());
		} finally { runtime.close(); }
	}

	@Test void discardDirtyRespondsThenStopsWithoutNativeWrite() throws Exception {
		NativeProjectDocument nativeProject = fixture();
		Path path = nativeProject.getProjectPath();
		String original = Files.readString(path);
		ProjectRuntime runtime = new ProjectRuntime(path, nativeProject.getInputFiles(), List.of(dir));
		try (HttpApiServer server = start(runtime, nativeProject)) {
			edit(runtime, nativeProject, "UnsavedShutdownAlias");
			HttpResponse<String> accepted = post(server, "{\"policy\":\"discard\"}", null);
			assertEquals(202, accepted.statusCode(), accepted.body());
			assertEquals("SHUTTING_DOWN", body(accepted).path("state").asText());
			assertEquals("discard", body(accepted).path("policy").asText());
			assertEquals("no-store", accepted.headers().firstValue("Cache-Control").orElseThrow());
			assertTrue(runtime.awaitStopped(10, TimeUnit.SECONDS));
			assertEquals(original, Files.readString(path));
		}
	}

	@Test void cleanPoliciesAndNativeSaveStopAfterCompleteAcknowledgment() throws Exception {
		for (String policy : List.of("discard", "refuse_if_dirty", "save")) {
			Path folder = Files.createDirectory(dir.resolve(policy));
			NativeProjectDocument nativeProject = fixture(folder);
			ProjectRuntime runtime = new ProjectRuntime(nativeProject.getProjectPath(), nativeProject.getInputFiles(), List.of(folder));
			try (HttpApiServer server = start(runtime, nativeProject)) {
				assertEquals(202, post(server, "{\"policy\":\"" + policy + "\"}", null).statusCode());
				assertTrue(runtime.awaitStopped(10, TimeUnit.SECONDS));
				assertNotNull(NativeProjectDocument.open(nativeProject.getProjectPath()));
			}
		}
	}

	@Test void dirtyRefusalLeavesEditsAndAdmissionIntact() throws Exception {
		NativeProjectDocument nativeProject = fixture();
		ProjectRuntime runtime = new ProjectRuntime(nativeProject.getProjectPath(), nativeProject.getInputFiles(), List.of(dir));
		try (HttpApiServer server = start(runtime, nativeProject)) {
			edit(runtime, nativeProject, "PendingRefusal");
			HttpResponse<String> refused = post(server, "{\"policy\":\"refuse_if_dirty\"}", null);
			assertEquals(409, refused.statusCode());
			assertEquals("PROJECT_BUSY", body(refused).path("error").path("code").asText());
			assertFalse(body(refused).path("error").path("retryable").asBoolean());
			assertEquals("READY", runtime.status().state());
			assertTrue(runtime.projectSnapshot().dirty());
			assertEquals(200, get(server, "/api/v1/project").statusCode());
			assertFalse(Files.readString(nativeProject.getProjectPath()).contains("PendingRefusal"));
		} finally { runtime.close(); }
	}

	@Test void saveDirtyWritesNativeAndExternalConflictKeepsServiceReady() throws Exception {
		NativeProjectDocument nativeProject = fixture();
		Path path = nativeProject.getProjectPath();
		ProjectRuntime runtime = new ProjectRuntime(path, nativeProject.getInputFiles(), List.of(dir));
		try (HttpApiServer server = start(runtime, nativeProject)) {
			edit(runtime, nativeProject, "SavedShutdownAlias");
			Files.writeString(dir.resolve("sample.tiny"), Files.readString(dir.resolve("sample.tiny")) + "\n");
			HttpResponse<String> conflict = post(server, "{\"policy\":\"save\"}", null);
			assertEquals(409, conflict.statusCode());
			assertEquals("EXTERNAL_MODIFICATION_CONFLICT", body(conflict).path("error").path("code").asText());
			assertEquals("READY", runtime.status().state());
			assertTrue(runtime.projectSnapshot().dirty());
			assertEquals(200, get(server, "/api/v1/project").statusCode());
		} finally { runtime.close(); }

		Path fresh = Files.createDirectory(dir.resolve("fresh"));
		NativeProjectDocument second = fixture(fresh);
		ProjectRuntime saveRuntime = new ProjectRuntime(second.getProjectPath(), second.getInputFiles(), List.of(fresh));
		try (HttpApiServer server = start(saveRuntime, second)) {
			edit(saveRuntime, second, "SavedShutdownAlias");
			assertEquals(202, post(server, "{\"policy\":\"save\"}", null).statusCode());
			assertTrue(saveRuntime.awaitStopped(10, TimeUnit.SECONDS));
			assertTrue(Files.readString(second.getProjectPath()).contains("SavedShutdownAlias"));
			assertFalse(NativeProjectDocument.open(second.getProjectPath()).getCodeData().getRenames().isEmpty());
		}
	}

	@Test void rawInputNeedsEstablishedNativeTargetForSave() throws Exception {
		Files.copy(Path.of("tests/fixtures/native-project/sample.jar"), dir.resolve("sample.jar"));
		Path input = dir.resolve("sample.jar");
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(input), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start();
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			HttpResponse<String> missing = post(server, "{\"policy\":\"save\"}", null);
			assertEquals(409, missing.statusCode());
			assertEquals("INVALID_REQUEST", body(missing).path("error").path("code").asText());
			assertEquals("READY", runtime.status().state());
			assertEquals(200, postPath(server, "/api/v1/project/save", "{\"targetPath\":\"" + dir.resolve("established.jadx") + "\"}", null).statusCode());
			assertEquals(202, post(server, "{\"policy\":\"save\"}", null).statusCode());
			assertTrue(runtime.awaitStopped(10, TimeUnit.SECONDS));
			assertTrue(Files.exists(dir.resolve("established.jadx")));
		}
	}

	@Test void malformedAndCrossOriginRequestsDoNotStopService() throws Exception {
		NativeProjectDocument nativeProject = fixture();
		ProjectRuntime runtime = new ProjectRuntime(nativeProject.getProjectPath(), nativeProject.getInputFiles(), List.of(dir));
		try (HttpApiServer server = start(runtime, nativeProject)) {
			for (String invalid : List.of("{bad", "{\"policy\":\"other\"}", "{\"extra\":1}", "{\"policy\":null}")) {
				assertEquals(400, post(server, invalid, null).statusCode());
			}
			assertEquals(403, post(server, "{}", "http://evil.example").statusCode());
			assertEquals("READY", runtime.status().state());
		} finally { runtime.close(); }
	}

	@Test void activeReadAndJobRemainInFlightUntilCallerReleasesThem() throws Exception {
		NativeProjectDocument nativeProject = fixture();
		ProjectRuntime runtime = new ProjectRuntime(nativeProject.getProjectPath(), nativeProject.getInputFiles(), List.of(dir));
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		try (HttpApiServer server = start(runtime, nativeProject)) {
			CompletableFuture<Void> read = CompletableFuture.runAsync(() -> runtime.withPrimaryClassRead("sample", engine -> {
				entered.countDown();
				try { release.await(); } catch (InterruptedException failure) { throw new RuntimeException(failure); }
				return null;
			}));
			assertTrue(entered.await(5, TimeUnit.SECONDS));
			HttpResponse<String> busy = post(server, "{}", null);
			assertEquals(409, busy.statusCode());
			assertEquals(1, body(busy).path("error").path("details").path("activeOperationCount").asInt());
			assertEquals("READY", runtime.status().state());
			release.countDown();
			read.get(5, TimeUnit.SECONDS);

			CountDownLatch jobEntered = new CountDownLatch(1);
			CountDownLatch jobRelease = new CountDownLatch(1);
			var revisions = runtime.projectSnapshot().revisions();
			UUID id = runtime.submitJob(new JobSpec<Void>("shutdown-busy", OperationRequest.classRead("job"),
					new OperationCoordinator.Admission(revisions.sessionId(), revisions.logicalRevision()),
					"snapshot", Duration.ofSeconds(30), null, (ctx, ignored) -> {
						jobEntered.countDown();
						jobRelease.await();
						return new JobSpec.JobResult("{}", JobSpec.Completeness.COMPLETE);
					})).jobId();
			assertTrue(jobEntered.await(5, TimeUnit.SECONDS));
			HttpResponse<String> jobBusy = post(server, "{}", null);
			assertEquals(409, jobBusy.statusCode());
			assertEquals(id.toString(), body(jobBusy).path("error").path("details").path("jobs").get(0).path("jobId").asText());
			assertEquals(JobSnapshot.State.RUNNING, runtime.jobRegistry().snapshot(id).state());
			postPath(server, "/api/v1/jobs/" + id + "/cancel", "", null);
			jobRelease.countDown();
			assertEquals(JobSnapshot.State.CANCELLED, runtime.jobRegistry().awaitTerminal(id, Duration.ofSeconds(5)).state());
			assertEquals(202, post(server, "{}", null).statusCode());
			assertTrue(runtime.awaitStopped(10, TimeUnit.SECONDS));
		} finally { release.countDown(); runtime.close(); }
	}

	@Test void temporaryAnalysisBlocksShutdownUntilItsOwnedCleanupFinishes() throws Exception {
		NativeProjectDocument nativeProject = fixture();
		ProjectRuntime runtime = new ProjectRuntime(nativeProject.getProjectPath(), nativeProject.getInputFiles(), List.of(dir));
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		try (HttpApiServer server = start(runtime, nativeProject)) {
			CompletableFuture<Void> analysis = CompletableFuture.runAsync(() -> {
				try {
					runtime.withTemporaryAnalysis(EffectiveAnalysisConfig.defaults(), jadx -> {
						entered.countDown();
						try { release.await(); } catch (InterruptedException failure) { throw new RuntimeException(failure); }
						return null;
					});
				} catch (Exception failure) { throw new RuntimeException(failure); }
			});
			assertTrue(entered.await(10, TimeUnit.SECONDS));
			assertEquals(409, post(server, "{}", null).statusCode());
			release.countDown();
			analysis.get(10, TimeUnit.SECONDS);
			assertEquals(202, post(server, "{}", null).statusCode());
			assertTrue(runtime.awaitStopped(10, TimeUnit.SECONDS));
		} finally { release.countDown(); runtime.close(); }
	}

	private NativeProjectDocument fixture() throws Exception { return fixture(dir); }
	private static NativeProjectDocument fixture(Path folder) throws Exception {
		Path source = Path.of("tests/fixtures/native-project").toAbsolutePath();
		for (String name : List.of("sample.jar.jadx", "sample.jar", "second.jar", "sample.tiny")) {
			Files.copy(source.resolve(name), folder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
		}
		return NativeProjectDocument.open(folder.resolve("sample.jar.jadx"));
	}
	private static HttpApiServer start(ProjectRuntime runtime, NativeProjectDocument project) throws Exception {
		HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime);
		server.start();
		runtime.initializeAsync(project).get(20, TimeUnit.SECONDS);
		return server;
	}
	private static void edit(ProjectRuntime runtime, NativeProjectDocument project, String alias) {
		var code = project.getCodeData();
		code.setRenames(List.of(new JadxCodeRename(JadxNodeRef.forCls("probe.Sample"), alias)));
		runtime.replaceCodeData(code, runtime.projectSnapshot().revisions().logicalRevision());
	}
	private static HttpResponse<String> get(HttpApiServer server, String path) throws Exception {
		return HTTP.send(HttpRequest.newBuilder(uri(server, path)).GET().build(), HttpResponse.BodyHandlers.ofString());
	}
	private static HttpResponse<String> post(HttpApiServer server, String body, String origin) throws Exception {
		return postPath(server, "/api/v1/shutdown", body, origin);
	}
	private static HttpResponse<String> postPath(HttpApiServer server, String path, String body, String origin) throws Exception {
		HttpRequest.Builder builder = HttpRequest.newBuilder(uri(server, path)).header("Content-Type", "application/json");
		if (origin != null) builder.header("Origin", origin);
		return HTTP.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
	}
	private static URI uri(HttpApiServer server, String path) { return URI.create("http://127.0.0.1:" + server.localPort() + path); }
	private static JsonNode body(HttpResponse<String> response) throws Exception { return JSON.readTree(response.body()); }
}
