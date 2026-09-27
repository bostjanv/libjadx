package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.io.IOException;
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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.libjadx.http.HttpApiServer;
import dev.libjadx.core.symbols.SymbolCatalog;
import jadx.api.JadxDecompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SearchEndpointsTest {
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final HttpClient HTTP = HttpClient.newHttpClient();
	@TempDir Path dir;

	@Test
	void indexCatalogExhaustionDoesNotBreakDecompile() throws Exception {
		Path jar = SymbolFixtureSupport.compileFixture(dir);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
		SearchService search = new SearchService(runtime,
				new SymbolCatalogProvider(SymbolCatalog.newCursorKey()), SymbolCatalog.newCursorKey(),
				processed -> { }, SearchService::extractSource, System::nanoTime, 1024);
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime, null, search)) {
			server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			JsonNode source = ok(post(server, "/api/v1/decompile", JSON.createObjectNode().set("ref",
					JSON.createObjectNode().put("kind", "CLASS")
							.put("originalClassDescriptor", "Lprobe/SymbolFixture;"))));
			assertEquals("RESOLVED", source.path("outcome").asText());
			assertTrue(source.path("source").asText().contains("class SymbolFixture"));
			assertError(post(server, "/api/v1/search", query("SymbolFixture", "CLASS_NAME")),
					429, "RESOURCE_LIMIT");
		} finally { runtime.close(); }
	}

	@Test
	void excessiveLiteralAndRegexMatchesReturnResourceLimitWithoutTruncation() throws Exception {
		Path jar = SymbolFixtureSupport.compileManyMatchesFixture(dir);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			ok(post(server, "/api/v1/decompile", JSON.createObjectNode().set("ref",
					JSON.createObjectNode().put("kind", "CLASS")
							.put("originalClassDescriptor", "Lprobe/ManyMatches;"))));
			assertError(post(server, "/api/v1/search", query("a", "SOURCE_TEXT")), 429, "RESOURCE_LIMIT");
			assertError(post(server, "/api/v1/search", query("a", "SOURCE_TEXT").put("matchMode", "REGEX")),
					429, "RESOURCE_LIMIT");
			assertTrue(ok(post(server, "/api/v1/search", query("not present", "SOURCE_TEXT")))
					.path("hits").isEmpty());
		} finally { runtime.close(); }
	}

	@Test
	void nativeSaveHoldingRepositoryMonitorRejectsColdSearchAndBuildPromptly() throws Exception {
		Path jar = SymbolFixtureSupport.compileFixture(dir);
		CountDownLatch enteredSave = new CountDownLatch(1);
		CountDownLatch releaseSave = new CountDownLatch(1);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), args -> new ProjectRuntime.ProjectEngine() {
			private final JadxDecompiler jadx = new JadxDecompiler(args);
			@Override public void load() { jadx.load(); }
			@Override public JadxDecompiler decompiler() { return jadx; }
			@Override public void close() { jadx.close(); }
		}, (repository, target, session, revision) -> {
			synchronized (repository) {
				enteredSave.countDown();
				try { if (!releaseSave.await(10, TimeUnit.SECONDS)) throw new IOException("Timed out holding native save"); }
				catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IOException(failure); }
				return repository.save(target, session, revision);
			}
		});
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			String session = runtime.projectSnapshot().revisions().sessionId();
			CompletableFuture<Void> saving = CompletableFuture.runAsync(() -> {
				try { runtime.saveProject(dir.resolve("search-busy.jadx"), session, 0L); }
				catch (IOException failure) { throw new RuntimeException(failure); }
			});
			try {
				assertTrue(enteredSave.await(5, TimeUnit.SECONDS));
				for (String path : List.of("/api/v1/search", "/api/v1/search/build-index")) {
					HttpResponse<String> busy = CompletableFuture.supplyAsync(() -> {
						try { return post(server, path, path.endsWith("build-index")
									? JSON.createObjectNode() : query("x", "CLASS_NAME")); }
						catch (Exception failure) { throw new RuntimeException(failure); }
					}).get(2, TimeUnit.SECONDS);
					assertError(busy, 409, "PROJECT_BUSY");
				}
			} finally { releaseSave.countDown(); saving.get(10, TimeUnit.SECONDS); }
		} finally { releaseSave.countDown(); runtime.close(); }
	}

	@Test
	void searchRejectsMalformedBodiesAndRespectsReadiness() throws Exception {
		Path jar = SymbolFixtureSupport.compileFixture(dir);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start();
			assertError(postRaw(server, "/api/v1/search", "{\"query\":\"x\"}", "application/json", null),
					503, "PROJECT_NOT_READY");
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			assertError(postRaw(server, "/api/v1/search", "{\"query\":\"x\",\"query\":\"y\"}",
					"application/json", null), 400, "INVALID_REQUEST");
			assertError(post(server, "/api/v1/search", JSON.createObjectNode().put("query", "x").put("extra", 1)),
					400, "INVALID_REQUEST");
			assertError(post(server, "/api/v1/search", query("x", "CLASS_NAME").put("pageSize", 0)),
					400, "INVALID_REQUEST");
			assertError(post(server, "/api/v1/search", query("x", "CLASS_NAME").put("matchMode", "REGEX")),
					422, "UNSUPPORTED_CAPABILITY");
			assertError(postRaw(server, "/api/v1/search", "{}", "text/plain", null), 415, "INVALID_REQUEST");
			assertError(postRaw(server, "/api/v1/search/build-index", "{}", "application/json",
					"http://evil.invalid"), 403, "INPUT_SECURITY_REJECTION");
			assertError(postRaw(server, "/api/v1/search", "{\"query\":\"" + "a".repeat(65_537) + "\"}",
					"application/json", null), 400, "INVALID_REQUEST");
		} finally { runtime.close(); }
	}

	@Test
	void unicodeAndOwnedQuotedTextUseExactEmittedJavaCoordinates() throws Exception {
		Path jar = SymbolFixtureSupport.compileSearchFixture(dir);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			JsonNode source = ok(post(server, "/api/v1/decompile", JSON.createObjectNode().set("ref",
					JSON.createObjectNode().put("kind", "CLASS")
							.put("originalClassDescriptor", "Lprobe/SearchFixture;"))));
			assertTrue(source.path("source").asText().contains("😀"), source.path("source").asText());
			for (String expression : List.of("😀", "quoted", "alpha")) {
				JsonNode page = ok(post(server, "/api/v1/search", query(expression, "SOURCE_TEXT")));
				assertTrue(page.path("hits").size() > 0, expression + page.toPrettyString());
				JsonNode hit = page.path("hits").get(0);
				int start = hit.path("range").path("startOffsetUtf16").asInt();
				int end = hit.path("range").path("endOffsetUtf16").asInt();
				assertEquals(expression, source.path("source").asText().substring(start, end));
				assertEquals(source.path("sourceSnapshotId").asText(), hit.path("sourceSnapshotId").asText());
			}
			JsonNode regex = ok(post(server, "/api/v1/search",
					query("emoji|quoted", "SOURCE_TEXT").put("matchMode", "REGEX")));
			assertTrue(regex.path("hits").size() >= 2);
		} finally { runtime.close(); }
	}

	@Test
	void coldCatalogAndIncrementalSourceHaveHonestCoverageAndExactOffsets() throws Exception {
		Path jar = SymbolFixtureSupport.compileFixture(dir);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			ObjectNode classQuery = query("SymbolFixture", "CLASS_NAME");
			JsonNode classes = ok(post(server, "/api/v1/search", classQuery));
			assertEquals("COMPLETE", classes.path("coverage").get(0).path("state").asText());
			assertTrue(classes.path("hits").toString().contains("Lprobe/SymbolFixture;"));
			assertEquals("UNVERIFIED", classes.path("coverage").get(0).path("sourceInputCoverage").asText());
			assertError(post(server, "/api/v1/search", query("(a+)\\1", "SOURCE_TEXT")
					.put("matchMode", "REGEX").put("requireComplete", true)), 422, "UNSUPPORTED_CAPABILITY");
			ObjectNode sourceQuery = query("mix", "SOURCE_TEXT");
			JsonNode cold = ok(post(server, "/api/v1/search", sourceQuery));
			assertEquals("PARTIAL", cold.path("coverage").get(0).path("state").asText());
			assertEquals(0, cold.path("hits").size());
			assertError(post(server, "/api/v1/search", sourceQuery.deepCopy().put("strict", true)),
					409, "INCOMPLETE_ANALYSIS");
			JsonNode source = ok(post(server, "/api/v1/decompile", JSON.createObjectNode().set("ref",
					JSON.createObjectNode().put("kind", "CLASS")
							.put("originalClassDescriptor", "Lprobe/SymbolFixture;"))));
			JsonNode warm = ok(post(server, "/api/v1/search", sourceQuery));
			assertEquals("COMPLETE", warm.path("coverage").get(0).path("state").asText());
			assertTrue(warm.path("hits").size() > 0);
			JsonNode hit = warm.path("hits").get(0);
			assertEquals(source.path("sourceSnapshotId").asText(), hit.path("sourceSnapshotId").asText());
			int start = hit.path("range").path("startOffsetUtf16").asInt();
			int end = hit.path("range").path("endOffsetUtf16").asInt();
			assertEquals("mix", source.path("source").asText().substring(start, end));
			assertEquals("Lprobe/SymbolFixture;", hit.path("sourceOwnerRef").path("originalClassDescriptor").asText());
			assertEquals(0, runtime.projectSnapshot().revisions().logicalRevision());
			assertFalse(runtime.projectSnapshot().dirty());
			var yaml = new ObjectMapper(new YAMLFactory()).readTree(Path.of("openapi/openapi.yaml").toFile());
			OpenApiExampleValidator.assertValid(yaml, "SearchPage", warm);
		} finally { runtime.close(); }
	}

	@Test
	void completeJobRegexAndAuthenticatedPagingUseRealJadx() throws Exception {
		Path jar = SymbolFixtureSupport.compileFixture(dir);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			ObjectNode sourceQuery = query("mix", "SOURCE_TEXT");
			ObjectNode requireComplete = sourceQuery.deepCopy().put("requireComplete", true);
			HttpResponse<String> accepted = post(server, "/api/v1/search", requireComplete);
			assertEquals(202, accepted.statusCode(), accepted.body());
			assertTrue(accepted.headers().firstValue("Location").orElseThrow().startsWith("/api/v1/jobs/"));
			JsonNode firstJob = JSON.readTree(accepted.body());
			String jobId = firstJob.path("jobId").asText();
			HttpResponse<String> duplicate = post(server, "/api/v1/search/build-index",
					JSON.createObjectNode().set("domains", JSON.createArrayNode().add("SOURCE_TEXT")));
			assertTrue(duplicate.statusCode() == 202 || duplicate.statusCode() == 200, duplicate.body());
			JsonNode terminal = awaitJob(server, jobId);
			assertEquals("SUCCEEDED", terminal.path("state").asText(), terminal.toPrettyString());
			HttpResponse<String> events = HTTP.send(HttpRequest.newBuilder(URI.create(
					"http://127.0.0.1:" + server.localPort() + "/api/v1/jobs/" + jobId + "/events")).GET().build(),
					HttpResponse.BodyHandlers.ofString());
			assertEquals(200, events.statusCode());
			assertTrue(events.body().contains("event: job.progress"));
			assertTrue(events.body().contains("event: job.completed"));
			JsonNode complete = ok(post(server, "/api/v1/search", sourceQuery.deepCopy().put("strict", true)));
			assertEquals("COMPLETE", complete.path("coverage").get(0).path("state").asText(), complete.toPrettyString());
			assertTrue(complete.path("hits").size() > 0);
			ObjectNode regex = query("m(ix|issing)", "SOURCE_TEXT").put("matchMode", "REGEX");
			assertTrue(ok(post(server, "/api/v1/search", regex)).path("hits").size() > 0);
			assertError(post(server, "/api/v1/search", query("(a+)+\\1", "SOURCE_TEXT")
					.put("matchMode", "REGEX")), 422, "UNSUPPORTED_CAPABILITY");
			assertError(post(server, "/api/v1/search", query("x", "STRING_LITERAL")),
					422, "UNSUPPORTED_CAPABILITY");
			ObjectNode paged = query("m", "SOURCE_TEXT").put("pageSize", 1);
			JsonNode page1 = ok(post(server, "/api/v1/search", paged));
			String cursor = page1.path("nextCursor").asText();
			assertFalse(cursor.isBlank(), page1.toPrettyString());
			JsonNode page2 = ok(post(server, "/api/v1/search", paged.deepCopy().put("cursor", cursor)));
			assertEquals(page1.path("resultSnapshotId").asText(), page2.path("resultSnapshotId").asText());
			assertNotEquals(page1.path("hits").get(0).path("range").path("startOffsetUtf16").asInt(),
					page2.path("hits").get(0).path("range").path("startOffsetUtf16").asInt());
			assertError(post(server, "/api/v1/search", paged.deepCopy().put("cursor", "B" + cursor.substring(1))),
					400, "INVALID_REQUEST");
			assertError(post(server, "/api/v1/search", paged.deepCopy().put("query", "x").put("cursor", cursor)),
					400, "INVALID_REQUEST");
		} finally { runtime.close(); }
	}

	private static ObjectNode query(String text, String domain) {
		ObjectNode request = JSON.createObjectNode().put("query", text);
		request.set("domains", JSON.createArrayNode().add(domain));
		return request;
	}
	private static HttpResponse<String> post(HttpApiServer server, String path, JsonNode body) throws Exception {
		return postRaw(server, path, body.toString(), "application/json", null);
	}
	private static HttpResponse<String> postRaw(HttpApiServer server, String path, String body,
			String type, String origin) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.localPort() + path))
				.header("Content-Type", type);
		if (origin != null) request.header("Origin", origin);
		HttpResponse<String> response = HTTP.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
				HttpResponse.BodyHandlers.ofString());
		if (path.startsWith("/api/v1/search")) {
			String schema = response.statusCode() >= 400 ? "ErrorEnvelope" : response.statusCode() == 202 ? "Job"
					: path.endsWith("build-index") ? "SearchIndexStatus" : "SearchPage";
			ObjectNode record = JSON.createObjectNode().put("status", response.statusCode()).put("schema", schema);
			record.set("body", JSON.readTree(response.body()));
			Path output = Path.of("build/search-contract-responses");
			java.nio.file.Files.createDirectories(output);
			java.nio.file.Files.writeString(output.resolve(java.util.UUID.randomUUID() + ".json"), record.toString());
		}
		return response;
	}
	private static JsonNode ok(HttpResponse<String> response) throws Exception {
		assertEquals(200, response.statusCode(), response.body());
		return JSON.readTree(response.body());
	}
	private static void assertError(HttpResponse<String> response, int status, String code) throws Exception {
		assertEquals(status, response.statusCode(), response.body());
		assertEquals(code, JSON.readTree(response.body()).path("error").path("code").asText());
	}
	private static JsonNode awaitJob(HttpApiServer server, String id) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
		while (System.nanoTime() < deadline) {
			HttpResponse<String> response = HTTP.send(HttpRequest.newBuilder(URI.create(
					"http://127.0.0.1:" + server.localPort() + "/api/v1/jobs/" + id)).GET().build(),
					HttpResponse.BodyHandlers.ofString());
			JsonNode body = JSON.readTree(response.body());
			if (List.of("SUCCEEDED", "FAILED", "CANCELLED").contains(body.path("state").asText())) return body;
			Thread.sleep(20);
		}
		throw new AssertionError("Search build did not complete");
	}
}
