package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.libjadx.http.HttpApiServer;
import jadx.api.JadxDecompiler;
import jadx.api.data.impl.JadxCodeData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScopedParameterEndpointsTest {
	static final ObjectMapper JSON = new ObjectMapper();
	static final HttpClient HTTP = HttpClient.newHttpClient();
	@TempDir Path dir;
	static ObjectNode batch(JsonNode source, int index, String name) {
		ObjectNode result = JSON.createObjectNode().put("expectedSessionId", source.path("sessionId").asText())
				.put("expectedLogicalRevision", source.path("logicalRevision").asLong());
		var item = result.putArray("items").addObject().put("kind", "RENAME_PARAMETER")
				.put("parameterIndex", index).put("sourceSnapshotId", source.path("sourceSnapshotId").asText()).put("newName", name);
		item.set("method", JSON.valueToTree(ScopedParameterServiceTest.INSTANCE));
		return result;
	}
	static final String DECOMPILE = "{\"ref\":{\"kind\":\"CLASS\",\"originalClassDescriptor\":\"Lprobe/Variables;\"}}";

	@Test void capturesLiveScopedSuccessErrorsAndSourceSearchInvalidation() throws Exception {
		Path jar = SymbolFixtureSupport.compileVariableFixture(dir);
		Path smali = SymbolFixtureSupport.unusedCatchFixture(dir);
		var runtime = new ProjectRuntime(null, List.of(jar, smali), List.of(dir));
		Path captures = Files.createDirectories(Path.of("build/scoped-edit-contract-responses"));
		try (var server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start();
			capture(captures, "503-not-ready", post(server, "/edits/batch", "{}"), 503, "ErrorEnvelope");
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			var sourceResponse = post(server, "/decompile", DECOMPILE);
			capture(captures, "200-source", sourceResponse, 200, "DecompileResult");
			JsonNode source = body(sourceResponse);
			assertTrue(source.path("variables").size() > 4);
			var catchSourceResponse = post(server, "/decompile", "{\"ref\":{\"kind\":\"CLASS\",\"originalClassDescriptor\":\"Lprobe/UnusedCatch;\"}}");
			capture(captures, "200-catch-source", catchSourceResponse, 200, "DecompileResult");
			var catchSource = body(catchSourceResponse);
			assertTrue(catchSource.path("source").asText().contains("catch (NumberFormatException unused)"));
			assertEquals("UNSUPPORTED", catchSource.path("variables").get(0).path("persistability").asText());
			assertTrue(catchSource.path("variables").get(0).path("parameterIndex").isNull());
			var catchBatch = batch(catchSource, 0, "unused");
			((ObjectNode) catchBatch.path("items").get(0)).set("method", JSON.valueToTree(new dev.libjadx.core.symbols.SymbolRef(
					dev.libjadx.core.symbols.SymbolRef.Kind.METHOD, "Lprobe/UnusedCatch;", null, "unusedCatch", "(I)I")));
			var catchRejection = post(server, "/edits/batch", catchBatch.toString());
			capture(captures, "422-catch", catchRejection, 422, "ErrorEnvelope");
			assertEquals("UNSUPPORTED_CAPABILITY", body(catchRejection).path("error").path("code").asText());
			assertEquals(0, runtime.projectSnapshot().revisions().logicalRevision());
			assertFalse(runtime.projectSnapshot().dirty());
			var valid = batch(source, 1, "wideCount");
			var invalidName = batch(source, 1, "class");
			capture(captures, "400-name", post(server, "/edits/batch", invalidName.toString()), 400, "ErrorEnvelope");
			var missingRevision = valid.deepCopy(); missingRevision.remove("expectedSessionId"); missingRevision.remove("expectedLogicalRevision");
			capture(captures, "400-preconditions", post(server, "/edits/batch", missingRevision.toString()), 400, "ErrorEnvelope");
			var missing = valid.deepCopy(); ((ObjectNode) missing.path("items").get(0).path("method")).put("originalName", "missing");
			capture(captures, "404-method", post(server, "/edits/batch", missing.toString()), 404, "ErrorEnvelope");
			var unsupported = valid.deepCopy(); ((ObjectNode) unsupported.path("items").get(0)).put("parameterIndex", 4);
			capture(captures, "422-parameter", post(server, "/edits/batch", unsupported.toString()), 422, "ErrorEnvelope");
			var local = valid.deepCopy(); ((ObjectNode) local.path("items").get(0)).put("kind", "RENAME_LOCAL");
			capture(captures, "422-local", post(server, "/edits/batch", local.toString()), 422, "ErrorEnvelope");
			capture(captures, "415-type", send(server, "/edits/batch", valid.toString(), "text/plain"), 415, "ErrorEnvelope");
			capture(captures, "429-budget", post(server, "/edits/batch", " ".repeat(65537)), 429, "ErrorEnvelope");
			String query = "{\"query\":\"i\",\"domains\":[\"SOURCE_TEXT\"],\"pageSize\":1}";
			var search = body(post(server, "/search", query)); assertFalse(search.path("nextCursor").asText().isEmpty());
			capture(captures, "200-applied", post(server, "/edits/batch", valid.toString()), 200, "EditBatchResult");
			assertEquals(1, runtime.projectSnapshot().revisions().logicalRevision());
			var after = body(post(server, "/decompile", DECOMPILE));
			assertTrue(after.path("source").asText().contains("long wideCount"));
			capture(captures, "200-no-change", post(server, "/edits/batch", batch(after, 1, "wideCount").toString()), 200, "EditBatchResult");
			capture(captures, "409-revision", post(server, "/edits/batch", valid.toString()), 409, "ErrorEnvelope");
			var staleSnapshot = batch(after, 2, "fraction"); ((ObjectNode) staleSnapshot.path("items").get(0)).put("sourceSnapshotId", source.path("sourceSnapshotId").asText());
			capture(captures, "409-snapshot", post(server, "/edits/batch", staleSnapshot.toString()), 409, "ErrorEnvelope");
			var staleSearch = (ObjectNode) JSON.readTree(query); staleSearch.put("cursor", search.path("nextCursor").asText());
			assertEquals("STALE_REVISION", body(post(server, "/search", staleSearch.toString())).path("error").path("code").asText());
			var export = JSON.createObjectNode().put("targetPath", dir.resolve("out.tiny").toString()).put("format", "TINY_V2")
					.put("expectedSessionId", after.path("sessionId").asText()).put("expectedLogicalRevision", after.path("logicalRevision").asLong());
			assertEquals(422, post(server, "/project/mappings/export", export.toString()).statusCode());
			var ambiguousCode = new JadxCodeData();
			var nativeMethod = new jadx.api.data.impl.JadxNodeRef(jadx.api.data.IJavaNodeRef.RefType.METHOD,
					"probe.Variables", "instance(IJDLjava/lang/String;)I");
			ambiguousCode.setRenames(List.of(
					new jadx.api.data.impl.JadxCodeRename(nativeMethod, jadx.api.data.impl.JadxCodeRef.forMthArg(1), "firstWide"),
					new jadx.api.data.impl.JadxCodeRename(nativeMethod, jadx.api.data.impl.JadxCodeRef.forMthArg(1), "secondWide")));
			runtime.replaceCodeData(ambiguousCode, runtime.projectSnapshot().revisions().logicalRevision());
			var ambiguousSource = body(post(server, "/decompile", DECOMPILE));
			var ambiguousResponse = post(server, "/edits/batch", batch(ambiguousSource, 1, "wideCount").toString());
			capture(captures, "422-ambiguous", ambiguousResponse, 422, "ErrorEnvelope");
			assertEquals("INVALID_ENTITY_ID", body(ambiguousResponse).path("error").path("code").asText());
			runtime.close();
			capture(captures, "503-stopping", post(server, "/edits/batch", valid.toString()), 503, "ErrorEnvelope");
		} finally { runtime.close(); }
	}

	@Test void postReplacementReplayFailureIsBoundedHttp500AndFailedLifecycle() throws Exception {
		Path jar = SymbolFixtureSupport.compileVariableFixture(dir);
		var runtime = new ProjectRuntime(null, List.of(jar), args -> new ProjectRuntime.ProjectEngine() {
			private final JadxDecompiler engine = new JadxDecompiler(args);
			public void load() { engine.load(); }
			public JadxDecompiler decompiler() { return engine; }
			public void reloadCodeData(JadxCodeData data) { throw new IllegalStateException("private internal secret"); }
			public void close() { engine.close(); }
		});
		try (var server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			var source = body(post(server, "/decompile", DECOMPILE));
			var response = post(server, "/edits/batch", batch(source, 1, "wideCount").toString());
			capture(Files.createDirectories(Path.of("build/scoped-edit-contract-responses")), "500-replay", response, 500, "ErrorEnvelope");
			assertFalse(response.body().contains("secret")); assertFalse(response.body().contains("Exception"));
			assertEquals("FAILED", runtime.status().state());
		} finally { runtime.close(); }
	}

	static void capture(Path root, String name, HttpResponse<String> response, int status, String schema) throws Exception {
		assertEquals(status, response.statusCode(), response.body());
		var spec = new ObjectMapper(new YAMLFactory()).readTree(Path.of("openapi/openapi.yaml").toFile());
		OpenApiExampleValidator.assertValid(spec, schema, body(response));
		Files.writeString(root.resolve(name + ".json"), JSON.writeValueAsString(Map.of("status", status, "schema", schema, "body", body(response))));
	}
	static HttpResponse<String> post(HttpApiServer server, String path, String data) throws Exception { return send(server, path, data, "application/json"); }
	static HttpResponse<String> send(HttpApiServer server, String path, String data, String type) throws Exception {
		return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.localPort() + "/api/v1" + path))
				.header("Content-Type", type).POST(HttpRequest.BodyPublishers.ofString(data)).build(), HttpResponse.BodyHandlers.ofString());
	}
	static JsonNode body(HttpResponse<String> response) throws Exception { return JSON.readTree(response.body()); }
}
