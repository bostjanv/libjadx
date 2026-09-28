package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.libjadx.http.HttpApiServer;
import dev.libjadx.project.FileFingerprint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MappingExportEndpointsTest {
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final HttpClient HTTP = HttpClient.newHttpClient();
	@TempDir Path dir;

	@Test
	void successfulExportRetainsDirtyStateSourceSnapshotAndClassAndSearchCursors() throws Exception {
		Path jar = SymbolFixtureSupport.compileEditFixture(dir);
		try (var runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
				var server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			String cls = "{\"kind\":\"CLASS\",\"originalClassDescriptor\":\"Lprobe/EditOwner;\"}";
			var edited = send(server, "/edits/batch", "{\"items\":[{\"kind\":\"RENAME\",\"target\":" + cls
					+ ",\"newName\":\"ExportOwner\"},{\"kind\":\"SET_COMMENT\",\"target\":" + cls
					+ ",\"comment\":\"unsaved declaration\",\"style\":\"LINE\"}]}", null, null);
			assertEquals(200, edited.statusCode(), edited.body());
			JsonNode before = get(server, "/project");
			String pending = runtime.pendingEdits().toString();
			String cursor = get(server, "/classes?pageSize=1").path("nextCursor").asText();
			JsonNode source = JSON.readTree(send(server, "/decompile", "{\"ref\":" + cls + "}", null, null).body());
			String search = "{\"query\":\"probe\",\"domains\":[\"CLASS_NAME\"],\"pageSize\":1}";
			String searchCursor = JSON.readTree(send(server, "/search", search, null, null).body()).path("nextCursor").asText();
			assertFalse(cursor.isEmpty()); assertFalse(searchCursor.isEmpty());
			Path target = dir.resolve("new.tiny");
			String request = request(runtime, target);
			JsonNode receipt = capture("dirty-success", send(server, "/project/mappings/export", request, null, null), 200);
			assertFalse(receipt.path("projectMutated").asBoolean()); assertTrue(receipt.path("omissions").isEmpty());
			assertEquals(Files.size(target), receipt.path("bytes").asLong());
			assertEquals(FileFingerprint.of(target).sha256(), receipt.path("sha256").asText());
			assertEquals(1, receipt.path("exported").path("classes").asInt());
			assertEquals(1, receipt.path("exported").path("comments").asInt());
			assertEquals(before, get(server, "/project")); assertTrue(before.path("dirty").asBoolean());
			assertEquals(pending, runtime.pendingEdits().toString()); assertFalse(Files.exists(dir.resolve("edits.jar.jadx")));
			assertEquals(source.path("sourceSnapshotId"), JSON.readTree(send(server, "/decompile", "{\"ref\":" + cls + "}", null, null).body()).path("sourceSnapshotId"));
			assertTrue(get(server, "/classes?pageSize=1&cursor=" + cursor).has("items"));
			assertEquals(200, send(server, "/search", search.substring(0, search.length() - 1) + ",\"cursor\":\"" + searchCursor + "\"}", null, null).statusCode());
			capture("existing", send(server, "/project/mappings/export", request, null, null), 409);
		}
	}

	@Test
	void strictValidationSecurityStaleBusyUnsupportedLimitsAndIoHaveContractErrors() throws Exception {
		Path jar = SymbolFixtureSupport.compileEditFixture(dir);
		try (var runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
				var server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			String route = "/project/mappings/export";
			Path target = dir.resolve("validated.tiny");
			String valid = request(runtime, target);
			List<String> invalid = List.of("{}", "null", valid + "{}", "{broken}",
					valid.replace("\"format\":\"TINY_V2\"", "\"format\":\"TINY_V2\",\"format\":\"TINY_V2\""),
					valid.replace("\"TINY_V2\"", "\"TINY_V1\""),
					valid.replace(target.toString(), "relative.tiny"),
					valid.replace(target.toString(), "/" + "x".repeat(4096) + ".tiny"),
					valid.substring(0, valid.length() - 1) + ",\"force\":true}",
					valid.replace("\"expectedLogicalRevision\":0", "\"expectedLogicalRevision\":-1"),
					valid.replace("\"expectedLogicalRevision\":0", "\"expectedLogicalRevision\":0.0"),
					valid.replace("\"expectedLogicalRevision\":0", "\"expectedLogicalRevision\":null"),
					valid.replace(runtime.projectSnapshot().revisions().sessionId(), "1-1-1-1-1"),
					valid.replace(runtime.projectSnapshot().revisions().sessionId(), runtime.projectSnapshot().revisions().sessionId().toUpperCase()),
					valid.replace(target.toString(), dir.resolve("../traversal.tiny").toString()));
			int index = 0;
			for (String body : invalid) assertEquals("INVALID_REQUEST", capture("invalid-" + index++, send(server, route, body, null, null), 400).path("error").path("code").asText());
			capture("media-type", send(server, route, valid, "text/plain", null), 415);
			capture("origin", send(server, route, valid, null, "https://elsewhere.invalid"), 403);
			capture("disallowed", send(server, route, request(runtime, dir.getParent().resolve("disallowed.tiny")), null, null), 403);
			capture("stale-session", send(server, route, valid.replace(runtime.projectSnapshot().revisions().sessionId(), UUID.randomUUID().toString()), null, null), 409);
			capture("stale-revision", send(server, route, valid.replace("\"expectedLogicalRevision\":0", "\"expectedLogicalRevision\":9"), null, null), 409);
			runtime.withExclusiveEdit(context -> {
				try { capture("busy", send(server, route, valid, null, null), 409); }
				catch (Exception failure) { throw new RuntimeException(failure); } return null;
			});
			capture("body-limit", send(server, route, " ".repeat(65_537), null, null), 429);
			Path readOnly = Files.createDirectory(dir.resolve("read-only"));
			var perms = Files.getPosixFilePermissions(readOnly);
			try {
				Files.setPosixFilePermissions(readOnly, PosixFilePermissions.fromString("r-x------"));
				capture("io-failure", send(server, route, request(runtime, readOnly.resolve("failed.tiny")), null, null), 500);
			} finally { Files.setPosixFilePermissions(readOnly, perms); }
			assertFalse(Files.exists(target));
			capture("clean-success", send(server, route, valid, null, null), 200);
			assertFalse(get(server, "/project").path("dirty").asBoolean());
			var code = new jadx.api.data.impl.JadxCodeData();
			code.setComments(List.of(new jadx.api.data.impl.JadxCodeComment(jadx.api.data.impl.JadxNodeRef.forCls("probe.EditOwner"), "block", jadx.api.data.CommentStyle.BLOCK)));
			runtime.replaceCodeData(code, 0);
			capture("unsupported-style", send(server, route, request(runtime, dir.resolve("block.tiny")), null, null), 422);
			assertFalse(Files.exists(dir.resolve("block.tiny")));
		}
	}

	@Test
	void nativeAndAttachedMappingExternalChangesAreRejectedAgainstTheirAcceptedBaselines() throws Exception {
		Path jar = SymbolFixtureSupport.compileEditFixture(dir);
		try (var runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
				var server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			Path nativePath = dir.resolve("native.jadx"); runtime.saveProject(nativePath, null);
			Path mapping = dir.resolve("attached.tiny");
			Files.writeString(mapping, "tiny\t2\t0\toriginal\tmapped\nc\tprobe/EditOwner\tprobe/MappedOwner\n");
			var revision = runtime.projectSnapshot().revisions();
			runtime.updateMappingsPath(mapping, revision.sessionId(), revision.logicalRevision());
			runtime.saveProject(null, null);
			byte[] accepted = Files.readAllBytes(mapping);
			JsonNode before = get(server, "/project");
			Path target = dir.resolve("conflict.tiny");
			String request = request(runtime, target);
			Files.writeString(mapping, "external editor bytes");
			capture("external-mapping", send(server, "/project/mappings/export", request, null, null), 409);
			assertFalse(Files.exists(target)); assertEquals(before, get(server, "/project"));
			Files.write(mapping, accepted);
			Files.writeString(nativePath, Files.readString(nativePath) + "\n ");
			capture("external-native", send(server, "/project/mappings/export", request, null, null), 409);
			assertFalse(Files.exists(target)); assertEquals(before, get(server, "/project"));
		}
	}

	@Test
	void loadingReturnsNotReadyAndCreatesNothing() throws Exception {
		Path jar = SymbolFixtureSupport.compileEditFixture(dir);
		try (var runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
				var server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start();
			capture("not-ready", send(server, "/project/mappings/export", "{}", null, null), 503);
		}
	}

	private static String request(ProjectRuntime runtime, Path target) throws Exception {
		var revisions = runtime.projectSnapshot().revisions();
		return JSON.writeValueAsString(Map.of("targetPath", target.toString(), "format", "TINY_V2", "expectedSessionId", revisions.sessionId(), "expectedLogicalRevision", revisions.logicalRevision()));
	}
	private static HttpResponse<String> send(HttpApiServer server, String path, String body, String type, String origin) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.localPort() + "/api/v1" + path))
				.timeout(java.time.Duration.ofSeconds(10)).header("Content-Type", type == null ? "application/json" : type);
		if (origin != null) request.header("Origin", origin);
		return HTTP.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
	}
	private static JsonNode get(HttpApiServer server, String path) throws Exception {
		var response = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.localPort() + "/api/v1" + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
		assertEquals(200, response.statusCode(), response.body()); return JSON.readTree(response.body());
	}
	private static JsonNode capture(String label, HttpResponse<String> response, int expected) throws Exception {
		assertEquals(expected, response.statusCode(), response.body());
		JsonNode body = JSON.readTree(response.body());
		String schema = expected == 200 ? "MappingExportReceipt" : "ErrorEnvelope";
		var spec = new ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory()).readTree(Path.of("openapi/openapi.yaml").toFile());
		OpenApiExampleValidator.assertValid(spec, schema, body);
		Path directory = Files.createDirectories(Path.of("build/mapping-export-contract-responses"));
		Files.writeString(directory.resolve(label + ".json"), JSON.writeValueAsString(Map.of("status", expected, "schema", schema, "body", body)));
		return body;
	}
}
