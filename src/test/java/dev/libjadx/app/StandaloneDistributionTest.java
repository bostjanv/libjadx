package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the production distribution without the test-only jadx-gui dependency. */
class StandaloneDistributionTest {
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
	@TempDir Path dir;

	@Test
	void installedHeadlessServiceEditsAndExplicitlySavesNativeProject() throws Exception {
		Path script = Path.of(System.getProperty("libjadx.distributionScript"));
		Path fixture = Path.of("tests/fixtures/native-project").toAbsolutePath();
		for (String name : java.util.List.of("sample.jar.jadx", "sample.jar", "second.jar", "sample.tiny")) {
			Files.copy(fixture.resolve(name), dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
		}
		Path project = dir.resolve("sample.jar.jadx");
		byte[] before = Files.readAllBytes(project);
		assertFalse(Files.exists(script.getParent().getParent().resolve("lib/jadx-gui-1.5.6.jar")));
		int port;
		try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
		Process first = start(script, project, port, "edit-first");
		try {
			awaitReady(first, port);
			String ref = "{\"kind\":\"CLASS\",\"originalClassDescriptor\":\"Lprobe/Sample;\"}";
			String batch = "{\"items\":[{\"kind\":\"RENAME\",\"target\":" + ref + ",\"newName\":\"DistAlias\"},"
					+ "{\"kind\":\"SET_COMMENT\",\"target\":" + ref + ",\"comment\":\"distribution edit\",\"style\":\"LINE\"}]}";
			HttpResponse<String> applied = post(port, "/api/v1/edits/batch", batch);
			assertEquals(200, applied.statusCode(), applied.body());
			assertEquals("APPLIED", body(applied).path("outcome").asText());
			assertTrue(body(post(port, "/api/v1/project/pending-edits/export", "{}"))
					.path("codeData").toString().contains("DistAlias"));
			assertTrue(body(get(port, "/api/v1/project")).path("dirty").asBoolean());
			org.junit.jupiter.api.Assertions.assertArrayEquals(before, Files.readAllBytes(project));
			JsonNode projectBeforeExport = body(get(port, "/api/v1/project"));
			Path exportedMapping = dir.resolve("distribution-export.tiny");
			String exportRequest = JSON.writeValueAsString(java.util.Map.of("targetPath", exportedMapping.toString(), "format", "TINY_V2",
					"expectedSessionId", projectBeforeExport.path("revisions").path("sessionId").asText(),
					"expectedLogicalRevision", projectBeforeExport.path("revisions").path("logicalRevision").asLong()));
			var exported = post(port, "/api/v1/project/mappings/export", exportRequest);
			assertEquals(200, exported.statusCode(), exported.body());
			assertEquals(dev.libjadx.project.FileFingerprint.of(exportedMapping).sha256(), body(exported).path("sha256").asText());
			assertEquals(projectBeforeExport, body(get(port, "/api/v1/project")));
			org.junit.jupiter.api.Assertions.assertArrayEquals(before, Files.readAllBytes(project));
			assertError(post(port, "/api/v1/project/mappings/export", exportRequest), 409, "EXTERNAL_MODIFICATION_CONFLICT");
			assertEquals(200, post(port, "/api/v1/project/save", "{}").statusCode());
			assertFalse(body(get(port, "/api/v1/project")).path("dirty").asBoolean());
		} finally { stop(first, port); }
		assertTrue(Files.readString(project).contains("DistAlias"));
		Process second = start(script, project, port, "edit-second");
		try {
			awaitReady(second, port);
			HttpResponse<String> source = post(port, "/api/v1/decompile", "{\"ref\":{\"kind\":\"CLASS\","
					+ "\"originalClassDescriptor\":\"Lprobe/Sample;\"}}");
			assertEquals(200, source.statusCode(), source.body());
			assertTrue(body(source).path("source").asText().contains("class DistAlias"));
			assertTrue(body(source).path("source").asText().contains("distribution edit"));
		} finally { stop(second, port); }
	}

	@Test
	void installedMappingImportRemainsUnsavedUntilExplicitSaveAndSurvivesRestart() throws Exception {
		Path script = Path.of(System.getProperty("libjadx.distributionScript"));
		Path jar = SymbolFixtureSupport.compileEditFixture(dir);
		Path project = dir.resolve("import.jadx");
		dev.libjadx.project.NativeProjectDocument.newFromInputs(project, java.util.List.of(jar)).saveNew();
		Path mapping = dir.resolve("incoming.tiny"); Files.writeString(mapping, MappingImportServiceTest.HAPPY);
		byte[] before = Files.readAllBytes(project); byte[] source = Files.readAllBytes(mapping);
		assertFalse(Files.exists(script.getParent().getParent().resolve("lib/jadx-gui-1.5.6.jar")));
		int port; try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
		for (int run = 0; run < 3; run++) {
			Process process = start(script, project, port, "mapping-import-" + run);
			try {
				awaitReady(process, port);
				JsonNode state = body(get(port, "/api/v1/project"));
				String request = JSON.writeValueAsString(java.util.Map.of("sourcePath", mapping.toString(), "format", "TINY_V2",
						"mode", "MERGE_FAIL_ON_CONFLICT", "expectedSessionId", state.path("revisions").path("sessionId").asText(),
						"expectedLogicalRevision", state.path("revisions").path("logicalRevision").asLong()));
				if (run < 2) {
					assertFalse(body(post(port, "/api/v1/decompile", "{\"ref\":" + MappingImportEndpointsTest.CLASS + "}")).path("source").asText().contains("ImportedOwner"));
					var imported = post(port, "/api/v1/project/mappings/import", request);
					assertEquals(200, imported.statusCode(), imported.body()); assertEquals("APPLIED", body(imported).path("outcome").asText());
					org.junit.jupiter.api.Assertions.assertArrayEquals(before, Files.readAllBytes(project));
					if (run == 1) assertEquals(200, post(port, "/api/v1/project/save", "{}").statusCode());
				} else {
					var noop = post(port, "/api/v1/project/mappings/import", request);
					assertEquals(200, noop.statusCode(), noop.body()); assertEquals("NO_CHANGE", body(noop).path("outcome").asText());
					assertFalse(body(noop).path("dirty").asBoolean());
				}
				String code = body(post(port, "/api/v1/decompile", "{\"ref\":" + MappingImportEndpointsTest.CLASS + "}")).path("source").asText();
				for (String text : java.util.List.of("ImportedOwner", "importedWork", "importedCount", "imported class")) assertTrue(code.contains(text), code);
				org.junit.jupiter.api.Assertions.assertArrayEquals(source, Files.readAllBytes(mapping));
				assertTrue(dev.libjadx.project.NativeProjectDocument.open(project).getMappingsPath() == null);
			} finally { stop(process, port); }
		}
	}

	@Test
	void installedSearchBuildsMemoryOnlyIndexAndRestartInvalidatesCursor() throws Exception {
		Path script = Path.of(System.getProperty("libjadx.distributionScript")
				+ (System.getProperty("os.name").startsWith("Windows") ? ".bat" : ""));
		Path project = Path.of("tests/fixtures/native-project/sample.jar.jadx").toAbsolutePath();
		byte[] projectBefore = Files.readAllBytes(project);
		byte[] inputBefore = Files.readAllBytes(project.getParent().resolve("sample.jar"));
		byte[] mappingBefore = Files.readAllBytes(project.getParent().resolve("sample.tiny"));
		int port;
		try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
		var schema = new ObjectMapper(new YAMLFactory()).readTree(Path.of("openapi/openapi.yaml").toFile());
		String cursor;
		Process first = start(script, project, port, "search-first");
		try {
			awaitReady(first, port);
			String classQuery = "{\"query\":\"probe\",\"domains\":[\"CLASS_NAME\"],\"pageSize\":1}";
			HttpResponse<String> classResponse = post(port, "/api/v1/search", classQuery);
			assertEquals(200, classResponse.statusCode(), classResponse.body());
			OpenApiExampleValidator.assertValid(schema, "SearchPage", body(classResponse));
			cursor = body(classResponse).path("nextCursor").asText();
			assertFalse(cursor.isBlank());
			HttpResponse<String> cold = post(port, "/api/v1/search",
					"{\"query\":\"class\",\"domains\":[\"SOURCE_TEXT\"]}");
			assertEquals(200, cold.statusCode(), cold.body());
			assertEquals("PARTIAL", body(cold).path("coverage").get(0).path("state").asText());
			OpenApiExampleValidator.assertValid(schema, "SearchPage", body(cold));
			HttpResponse<String> accepted = post(port, "/api/v1/search/build-index",
					"{\"domains\":[\"MEMBER_NAME\",\"SOURCE_TEXT\"]}");
			assertEquals(202, accepted.statusCode(), accepted.body());
			assertTrue(accepted.headers().firstValue("Location").orElseThrow().startsWith("/api/v1/jobs/"));
			OpenApiExampleValidator.assertValid(schema, "Job", body(accepted));
			String jobId = body(accepted).path("jobId").asText();
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
			JsonNode terminal = null;
			while (System.nanoTime() < deadline) {
				terminal = body(get(port, "/api/v1/jobs/" + jobId));
				if (terminal.path("state").asText().equals("SUCCEEDED")) break;
				if (terminal.path("state").asText().equals("FAILED")) throw new AssertionError(terminal.toPrettyString());
				Thread.sleep(20);
			}
			assertEquals("SUCCEEDED", terminal.path("state").asText());
			HttpResponse<String> complete = post(port, "/api/v1/search",
					"{\"query\":\"class\",\"domains\":[\"SOURCE_TEXT\"],\"strict\":true}");
			assertEquals(200, complete.statusCode(), complete.body());
			assertEquals("COMPLETE", body(complete).path("coverage").get(0).path("state").asText());
			OpenApiExampleValidator.assertValid(schema, "SearchPage", body(complete));
			HttpResponse<String> invalid = post(port, "/api/v1/search", "{\"query\":\"x\",\"domains\":[\"STRING_LITERAL\"]}");
			assertError(invalid, 422, "UNSUPPORTED_CAPABILITY");
			OpenApiExampleValidator.assertValid(schema, "ErrorEnvelope", body(invalid));
		} finally { stop(first, port); }
		org.junit.jupiter.api.Assertions.assertArrayEquals(projectBefore, Files.readAllBytes(project));
		org.junit.jupiter.api.Assertions.assertArrayEquals(inputBefore, Files.readAllBytes(project.getParent().resolve("sample.jar")));
		org.junit.jupiter.api.Assertions.assertArrayEquals(mappingBefore, Files.readAllBytes(project.getParent().resolve("sample.tiny")));
		Process second = start(script, project, port, "search-second");
		try {
			awaitReady(second, port);
			assertError(post(port, "/api/v1/search",
					"{\"query\":\"probe\",\"domains\":[\"CLASS_NAME\"],\"pageSize\":1,\"cursor\":\"" + cursor + "\"}"),
					409, "STALE_REVISION");
		} finally { stop(second, port); }
	}

	@Test
	void installedServiceLoadsClassesAndAuthenticatesCursorsAcrossRestart() throws Exception {
		Path script = Path.of(System.getProperty("libjadx.distributionScript") + (System.getProperty("os.name").startsWith("Windows") ? ".bat" : ""));
		Path project = Path.of("tests/fixtures/native-project/sample.jar.jadx").toAbsolutePath();
		int port;
		try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
		String cursor;
		Process first = start(script, project, port, "first");
		try {
			awaitReady(first, port);
			JsonNode page = body(get(port, "/api/v1/classes?pageSize=1"));
			assertEquals("Lprobe/Sample;", page.path("items").get(0).path("ref")
					.path("originalClassDescriptor").asText());
			cursor = page.path("nextCursor").asText();
			assertFalse(cursor.isBlank());
			HttpResponse<String> java = HTTP.send(HttpRequest.newBuilder(uri(port, "/api/v1/decompile"))
					.header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString("{\"ref\":{\"kind\":\"CLASS\","
							+ "\"originalClassDescriptor\":\"Lprobe/Sample;\"}}"))
					.build(), HttpResponse.BodyHandlers.ofString());
			assertEquals(200, java.statusCode(), java.body());
			assertEquals("RESOLVED", body(java).path("outcome").asText());
			assertTrue(body(java).path("source").asText().contains("class Sample"));
		} finally { stop(first, port); }

		Process second = start(script, project, port, "second");
		try {
			awaitReady(second, port);
			assertError(get(port, "/api/v1/classes?pageSize=1&cursor=" + encode(cursor)), 409, "STALE_REVISION");
			String[] parts = cursor.split("\\.", -1);
			String[] fields = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8).split("\0", -1);
			fields[1] = "tampered-session";
			String tampered = Base64.getUrlEncoder().withoutPadding().encodeToString(
					String.join("\0", fields).getBytes(StandardCharsets.UTF_8)) + "." + parts[1];
			assertError(get(port, "/api/v1/classes?pageSize=1&cursor=" + encode(tampered)), 400, "INVALID_REQUEST");
		} finally { stop(second, port); }
	}

	@Test
	void installedReferencesAreNonemptyAndOldCursorIsStaleAfterRestart() throws Exception {
		Path script = Path.of(System.getProperty("libjadx.distributionScript") + (System.getProperty("os.name").startsWith("Windows") ? ".bat" : ""));
		Path jar = SymbolFixtureSupport.referenceFixture(dir);
		Path project = dir.resolve("references.jadx");
		Files.writeString(project, "{\"projectVersion\":1,\"files\":[\"references.jar\"]}");
		int port;
		try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
		var query = ReferenceEndpointsTest.request(ReferenceEndpointsTest.method("entry", "()V"), "OUTGOING").put("pageSize", 1);
		String cursor;
		Process first = start(script, project, port, "references-first");
		try {
			awaitReady(first, port);
			var result = postReferences(port, query.toString());
			assertEquals(200, result.statusCode(), result.body());
			assertEquals(1, body(result).path("edges").size());
			cursor = body(result).path("nextCursor").asText();
			assertFalse(cursor.isBlank());
		} finally { stop(first, port); }
		Process second = start(script, project, port, "references-second");
		try {
			awaitReady(second, port);
			assertError(postReferences(port, query.put("cursor", cursor).toString()), 409, "STALE_REVISION");
			assertError(postReferences(port, query.put("cursor", "B" + cursor.substring(1)).toString()), 400, "INVALID_REQUEST");
		} finally { stop(second, port); }
	}
	@Test
	void installedScopedParameterSaveRestartDiscardAndOldSnapshotRejection() throws Exception {
		Path script = Path.of(System.getProperty("libjadx.distributionScript"));
		Path jar = SymbolFixtureSupport.compileVariableFixture(dir);
		Path project = dir.resolve("scoped.jadx");
		var document = dev.libjadx.project.NativeProjectDocument.newFromInputs(project, java.util.List.of(jar)); document.save();
		int port;
		try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
		JsonNode original = null;
		for (int run = 0; run < 3; run++) {
			Process process = start(script, project, port, "scoped-" + run);
			try {
				awaitReady(process, port);
				JsonNode source = body(post(port, "/api/v1/decompile", ScopedParameterEndpointsTest.DECOMPILE));
				if (run == 0) original = source;
				else {
					assertTrue(source.path("source").asText().contains("long savedWide"));
					assertFalse(source.path("source").asText().contains("unsavedFraction"));
					assertError(post(port, "/api/v1/edits/batch", ScopedParameterEndpointsTest.batch(original, 1, "old").toString()), 409, "STALE_REVISION");
					var staleSnapshot = ScopedParameterEndpointsTest.batch(source, 1, "old");
					((com.fasterxml.jackson.databind.node.ObjectNode) staleSnapshot.path("items").get(0)).put("sourceSnapshotId", original.path("sourceSnapshotId").asText());
					assertError(post(port, "/api/v1/edits/batch", staleSnapshot.toString()), 409, "STALE_REVISION");
				}
				if (run == 0) {
					assertEquals(200, post(port, "/api/v1/edits/batch", ScopedParameterEndpointsTest.batch(source, 1, "savedWide").toString()).statusCode());
					assertFalse(Files.readString(project).contains("savedWide"));
					assertEquals(200, post(port, "/api/v1/project/save", "{}").statusCode());
				} else if (run == 1) {
					assertEquals(200, post(port, "/api/v1/edits/batch", ScopedParameterEndpointsTest.batch(source, 2, "unsavedFraction").toString()).statusCode());
				}
			} finally { stop(process, port); }
		}
	}

	private static HttpResponse<String> postReferences(int port, String body) throws Exception {
		return HTTP.send(HttpRequest.newBuilder(uri(port, "/api/v1/references/query"))
				.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
				HttpResponse.BodyHandlers.ofString());
	}
	private static HttpResponse<String> post(int port, String path, String body) throws Exception {
		return HTTP.send(HttpRequest.newBuilder(uri(port, path)).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
	}

	private Process start(Path script, Path project, int port, String name) throws Exception {
		ProcessBuilder builder = new ProcessBuilder(script.toString(), "--project", project.toString(),
				"--port", Integer.toString(port));
		builder.environment().put("XDG_STATE_HOME", dir.resolve("state").toString());
		builder.redirectErrorStream(true);
		builder.redirectOutput(dir.resolve(name + ".log").toFile());
		return builder.start();
	}

	private static void awaitReady(Process process, int port) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
		while (System.nanoTime() < deadline) {
			if (!process.isAlive()) throw new AssertionError("Standalone service exited before readiness: " + process.exitValue());
			try {
				HttpResponse<String> response = get(port, "/api/v1/status");
				if (response.statusCode() == 200 && "READY".equals(body(response).path("state").asText())) return;
			} catch (java.io.IOException ignored) { /* Listener is still starting. */ }
			Thread.sleep(100);
		}
		throw new AssertionError("Standalone service did not become ready within 30 seconds");
	}

	private static void stop(Process process, int port) throws Exception {
		if (!process.isAlive()) return;
		try { HTTP.send(HttpRequest.newBuilder(uri(port, "/api/v1/shutdown"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString("{\"policy\":\"discard\"}"))
				.build(), HttpResponse.BodyHandlers.ofString());
		} catch (java.io.IOException ignored) { /* Process may already be exiting. */ }
		if (!process.waitFor(10, TimeUnit.SECONDS)) {
			process.destroyForcibly();
			assertTrue(process.waitFor(5, TimeUnit.SECONDS));
		}
	}

	private static void assertError(HttpResponse<String> response, int status, String code) throws Exception {
		assertEquals(status, response.statusCode(), response.body());
		assertEquals(code, body(response).path("error").path("code").asText());
	}

	private static HttpResponse<String> get(int port, String path) throws Exception {
		return HTTP.send(HttpRequest.newBuilder(uri(port, path)).GET().build(), HttpResponse.BodyHandlers.ofString());
	}

	private static URI uri(int port, String path) { return URI.create("http://127.0.0.1:" + port + path); }
	private static JsonNode body(HttpResponse<String> response) throws Exception { return JSON.readTree(response.body()); }
	private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
