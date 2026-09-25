package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import dev.libjadx.http.HttpApiServer;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;

class StatusServletTest {
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final HttpClient CLIENT = HttpClient.newHttpClient();

	@Test
	void exposesDiagnosticsAndUsesStateSpecificErrorsIncludingUnknownPaths() throws Exception {
		RuntimeEngine engine = new RuntimeEngine(new JadxArgs(), false);
		ProjectRuntime runtime = runtime(args -> engine);
		HttpApiServer server = start(runtime);
		try {
			HttpResponse<String> live = request(server, "GET", "/api/v1/health/live");
			assertEquals(200, live.statusCode());
			assertEquals("ALIVE", body(live).path("status").asText());

			HttpResponse<String> status = request(server, "GET", "/api/v1/status");
			assertEquals(200, status.statusCode());
			assertEquals("LOADING", body(status).path("state").asText());

			HttpResponse<String> capabilities = request(server, "GET", "/api/v1/capabilities");
			assertEquals("1.5.6", body(capabilities).path("jadxVersion").asText());
			assertTrue(body(capabilities).path("capabilities").size() > 0);

			assertError(request(server, "POST", "/api/v1/decompile"), 503, "PROJECT_NOT_READY", true, true);
			assertError(request(server, "GET", "/api/v1/does-not-exist"), 404, "NOT_FOUND", false, false);
			assertError(request(server, "PUT", "/api/v1/status"), 405, "METHOD_NOT_ALLOWED", false, false);

			engine.fail = true;
			runtime.initializeAsync(null).get(5, TimeUnit.SECONDS);
			HttpResponse<String> failedStatus = request(server, "GET", "/api/v1/status");
			assertEquals("FAILED", body(failedStatus).path("state").asText());
			assertEquals("PROJECT_LOAD_FAILED", body(failedStatus).path("error").path("code").asText());
			assertError(request(server, "POST", "/api/v1/decompile"), 503, "PROJECT_LOAD_FAILED", false, false);
			assertError(request(server, "POST", "/api/v1/unknown"), 404, "NOT_FOUND", false, false);

			runtime.close();
			assertError(request(server, "POST", "/api/v1/decompile"), 503, "SERVICE_SHUTTING_DOWN", false, false);
		} finally {
			server.close();
			runtime.close();
		}
	}

	@Test
	void plannedOperationAfterSuccessfulLoadIsNotImplementedRatherThanNotReady() throws Exception {
		RuntimeEngine engine = new RuntimeEngine(new JadxArgs(), false);
		ProjectRuntime runtime = runtime(args -> engine);
		HttpApiServer server = start(runtime);
		try {
			runtime.initializeAsync(null).get(5, TimeUnit.SECONDS);
			assertError(request(server, "POST", "/api/v1/references/query"), 501, "OPERATION_NOT_IMPLEMENTED", false, false);
		} finally {
			server.close();
			runtime.close();
		}
	}

	@Test
	void reportsNonRetryableShutdownWhileAnUninterruptibleLoadIsFinishing() throws Exception {
		CountDownLatch enteredLoad = new CountDownLatch(1);
		CountDownLatch releaseLoad = new CountDownLatch(1);
		RuntimeEngine engine = new RuntimeEngine(new JadxArgs(), false, enteredLoad, releaseLoad);
		ProjectRuntime runtime = runtime(args -> engine);
		HttpApiServer server = start(runtime);
		CompletableFuture<Void> initialization = null;
		try {
			initialization = runtime.initializeAsync(null);
			assertTrue(enteredLoad.await(5, TimeUnit.SECONDS));
			runtime.close();
			assertEquals("SHUTTING_DOWN", runtime.status().state());
			assertError(request(server, "POST", "/api/v1/decompile"), 503, "SERVICE_SHUTTING_DOWN", false, false);
		} finally {
			releaseLoad.countDown();
			if (initialization != null) initialization.get(5, TimeUnit.SECONDS);
			server.close();
			runtime.close();
		}
		assertEquals("STOPPED", runtime.status().state());
	}

	private static ProjectRuntime runtime(ProjectRuntime.EngineFactory factory) {
		return new ProjectRuntime(Path.of("fixture.jadx"), List.of(), factory);
	}

	private static HttpApiServer start(ProjectRuntime runtime) throws Exception {
		HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime);
		server.start();
		return server;
	}

	private static HttpResponse<String> request(HttpApiServer server, String method, String path) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.localPort() + path))
				.method(method, HttpRequest.BodyPublishers.noBody())
				.build();
		return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
	}

	private static JsonNode body(HttpResponse<String> response) throws Exception {
		return JSON.readTree(response.body());
	}

	private static void assertError(HttpResponse<String> response, int status, String code,
			boolean retryable, boolean retryAfter) throws Exception {
		assertEquals(status, response.statusCode());
		assertTrue(response.headers().firstValue("content-type").orElse("").startsWith("application/json"));
		assertEquals("no-store", response.headers().firstValue("cache-control").orElse(null));
		String headerRequestId = response.headers().firstValue("x-request-id").orElse(null);
		assertNotNull(headerRequestId);
		JsonNode error = body(response).path("error");
		assertEquals(code, error.path("code").asText());
		assertEquals(retryable, error.path("retryable").asBoolean());
		assertEquals(headerRequestId, error.path("requestId").asText());
		assertEquals(retryAfter, response.headers().firstValue("retry-after").isPresent());
	}

	private static final class RuntimeEngine implements ProjectRuntime.ProjectEngine {
		private final JadxDecompiler decompiler;
		private volatile boolean fail;
		private final CountDownLatch enteredLoad;
		private final CountDownLatch releaseLoad;

		private RuntimeEngine(JadxArgs args, boolean fail) {
			this(args, fail, null, null);
		}

		private RuntimeEngine(JadxArgs args, boolean fail, CountDownLatch enteredLoad, CountDownLatch releaseLoad) {
			this.decompiler = new JadxDecompiler(args);
			this.fail = fail;
			this.enteredLoad = enteredLoad;
			this.releaseLoad = releaseLoad;
		}

		@Override
		public void load() {
			if (enteredLoad != null) {
				enteredLoad.countDown();
				boolean interrupted = false;
				while (true) {
					try {
						releaseLoad.await();
						break;
					} catch (InterruptedException e) {
						interrupted = true;
					}
				}
				if (interrupted) Thread.currentThread().interrupt();
			}
			if (fail) throw new IllegalStateException("controlled test failure");
		}

		@Override
		public JadxDecompiler decompiler() {
			return decompiler;
		}

		@Override
		public void close() {
			decompiler.close();
		}
	}
}
