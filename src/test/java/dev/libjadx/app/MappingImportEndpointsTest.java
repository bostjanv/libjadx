package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
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

class MappingImportEndpointsTest {
	static final ObjectMapper JSON = new ObjectMapper();
	static final HttpClient HTTP = HttpClient.newHttpClient();
	@TempDir Path dir;
	static final String ROUTE = "/project/mappings/import";
	static final String CLASS = "{\"kind\":\"CLASS\",\"originalClassDescriptor\":\"Lprobe/EditOwner;\"}";

	@Test void liveImportUpdatesSourceReferencesSearchAndInvalidatesOldCursorsOnce() throws Exception {
		Path jar = SymbolFixtureSupport.compileEditFixture(dir); Path input = dir.resolve("incoming.tiny"); Files.writeString(input, MappingImportServiceTest.HAPPY);
		try (var runtime = new ProjectRuntime(null, List.of(jar), List.of(dir)); var server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			JsonNode before = get(server, "/project"); String oldCursor = get(server, "/classes?pageSize=1").path("nextCursor").asText();
			JsonNode oldSource = JSON.readTree(send(server, "/decompile", "{\"ref\":" + CLASS + "}", null, null).body());
			String search = "{\"query\":\"probe\",\"domains\":[\"CLASS_NAME\"],\"pageSize\":1}";
			String oldSearchCursor = JSON.readTree(send(server, "/search", search, null, null).body()).path("nextCursor").asText();
			var oldReferencesResponse = send(server, "/references/query", "{\"ref\":" + CLASS + ",\"direction\":\"OUTGOING\"}", null, null);
			assertEquals(200, oldReferencesResponse.statusCode(), oldReferencesResponse.body());
			JsonNode oldReferences = JSON.readTree(oldReferencesResponse.body());
			var hash = FileFingerprint.of(input);
			JsonNode applied = capture("applied", send(server, ROUTE, request(runtime, input), null, null), 200);
			assertEquals("APPLIED", applied.path("outcome").asText()); assertTrue(applied.path("dirty").asBoolean());
			assertEquals(3, applied.path("applied").path("aliases").asInt()); assertEquals(3, applied.path("applied").path("comments").asInt());
			assertEquals(hash.sha256(), applied.path("sha256").asText()); assertEquals(hash, FileFingerprint.of(input));
			assertNull(runtime.decompiler().getArgs().getUserRenamesMappingsPath()); assertFalse(Files.exists(dir.resolve("edits.jar.jadx")));
			JsonNode code = JSON.readTree(send(server, "/decompile", "{\"ref\":" + CLASS + "}", null, null).body());
			assertNotEquals(oldSource.path("sourceSnapshotId"), code.path("sourceSnapshotId")); assertTrue(code.path("source").asText().contains("importedWork"));
			JsonNode sourceHit = JSON.readTree(send(server, "/search", "{\"query\":\"importedWork\",\"domains\":[\"SOURCE_TEXT\"]}", null, null).body());
			assertFalse(sourceHit.path("hits").isEmpty(), sourceHit.toString());
			assertEquals(409, rawGet(server, "/classes?pageSize=1&cursor=" + oldCursor).statusCode());
			assertEquals(409, send(server, "/search", search.substring(0, search.length()-1) + ",\"cursor\":\"" + oldSearchCursor + "\"}", null, null).statusCode());
			var newReferencesResponse = send(server, "/references/query", "{\"ref\":" + CLASS + ",\"direction\":\"OUTGOING\"}", null, null);
			assertEquals(200, newReferencesResponse.statusCode(), newReferencesResponse.body());
			JsonNode newReferences = JSON.readTree(newReferencesResponse.body());
			assertNotEquals(oldReferences.path("logicalRevision"), newReferences.path("logicalRevision"));
			JsonNode after = get(server, "/project"); String pending = runtime.pendingEdits().toString(); var identity = runtime.searchIdentity();
			JsonNode noop = capture("idempotent", send(server, ROUTE, request(runtime, input), null, null), 200);
			assertEquals("NO_CHANGE", noop.path("outcome").asText()); assertEquals(after, get(server, "/project")); assertSame(identity, runtime.searchIdentity());
			assertEquals(pending, runtime.pendingEdits().toString());
			Files.writeString(input, MappingImportServiceTest.HEADER); capture("header-only", send(server, ROUTE, request(runtime, input), null, null), 200);
			assertEquals(after, get(server, "/project"));
			Files.writeString(input, MappingImportServiceTest.HAPPY.replace("ImportedOwner", "ConflictingOwner"));
			assertEquals("MAPPING_MERGE_CONFLICT", capture("merge-conflict", send(server, ROUTE, request(runtime, input), null, null), 409).path("error").path("code").asText());
			assertEquals(after, get(server, "/project")); assertNotEquals(before, after);
		}
	}

	@Test void requestAndFileFailuresUseExactContractAndLeaveStateUntouched() throws Exception {
		Path jar = SymbolFixtureSupport.compileEditFixture(dir); Path input = dir.resolve("incoming.tiny"); Files.writeString(input, MappingImportServiceTest.HAPPY);
		try (var runtime = new ProjectRuntime(null, List.of(jar), List.of(dir)); var server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			var before = runtime.projectSnapshot(); var identity = runtime.searchIdentity(); String valid = request(runtime, input);
			int index = 0;
			for (String body : List.of("{}", "null", "{broken}", valid + "{}", valid.replace("TINY_V2", "TINY_V1"),
					valid.replace("MERGE_FAIL_ON_CONFLICT", "OVERWRITE"), valid.replace(input.toString(), "relative.tiny"),
					valid.replace(input.toString(), "/" + "x".repeat(4096) + ".tiny"),
					valid.replace(input.toString(), dir.resolve("../escape.tiny").toString()),
					valid.substring(0, valid.length()-1) + ",\"force\":true}",
					valid.replace("\"format\":\"TINY_V2\"", "\"format\":\"TINY_V2\",\"format\":\"TINY_V2\""),
					valid.replace("\"expectedLogicalRevision\":0", "\"expectedLogicalRevision\":0.0"),
					valid.replace("\"expectedLogicalRevision\":0", "\"expectedLogicalRevision\":-1"),
					valid.replace("\"expectedLogicalRevision\":0", "\"expectedLogicalRevision\":null"),
					valid.replace(runtime.projectSnapshot().revisions().sessionId(), "1-1-1-1-1"))) {
				capture("invalid-" + index++, send(server, ROUTE, body, null, null), 400);
			}
			capture("wrong-media", send(server, ROUTE, valid, "text/plain", null), 415);
			capture("cross-origin", send(server, ROUTE, valid, null, "https://elsewhere.invalid"), 403);
			capture("escape-root", send(server, ROUTE, request(runtime, dir.getParent().resolve("escape.tiny")), null, null), 403);
			Path leaf = dir.resolve("link.tiny"); Files.createSymbolicLink(leaf, input);
			capture("symlink-leaf", send(server, ROUTE, request(runtime, leaf), null, null), 403);
			Path parent = dir.resolve("parent"); Files.createSymbolicLink(parent, dir);
			capture("symlink-parent", send(server, ROUTE, request(runtime, parent.resolve("incoming.tiny")), null, null), 403);
			Path nonregular = Files.createDirectory(dir.resolve("directory.tiny")); capture("nonregular", send(server, ROUTE, request(runtime, nonregular), null, null), 403);
			capture("missing-file", send(server, ROUTE, request(runtime, dir.resolve("missing.tiny")), null, null), 404);
			capture("stale-session", send(server, ROUTE, valid.replace(before.revisions().sessionId(), UUID.randomUUID().toString()), null, null), 409);
			capture("stale-revision", send(server, ROUTE, valid.replace("\"expectedLogicalRevision\":0", "\"expectedLogicalRevision\":2"), null, null), 409);
			runtime.withExclusiveEdit(context -> { try { capture("busy", send(server, ROUTE, valid, null, null), 409); } catch (Exception e) { throw new RuntimeException(e); } return null; });
			capture("body-limit", send(server, ROUTE, " ".repeat(65_537), null, null), 429);
			Files.write(input, new byte[0]); capture("empty-source", send(server, ROUTE, valid, null, null), 422);
			Files.write(input, new byte[]{(byte)0xc3, (byte)0x28}); capture("malformed-utf8", send(server, ROUTE, valid, null, null), 422);
			Files.writeString(input, MappingImportServiceTest.HEADER + "c\tprobe/Absent\tprobe/Absent\n"); capture("unresolved-original", send(server, ROUTE, valid, null, null), 404);
			Files.writeString(input, MappingImportServiceTest.HEADER + "c\tprobe/EditOwner\tprobe/EditOwner\n\tunknown\tdata\n"); capture("unsupported-record", send(server, ROUTE, valid, null, null), 422);
			Files.writeString(input, MappingImportServiceTest.HAPPY + " ".repeat(4 * 1024 * 1024)); capture("source-limit", send(server, ROUTE, valid, null, null), 429);
			Files.writeString(input, MappingImportServiceTest.HAPPY);
			var permissions = Files.getPosixFilePermissions(input);
			try { Files.setPosixFilePermissions(input, java.util.Set.of()); capture("io-failure", send(server, ROUTE, valid, null, null), 500); }
			finally { Files.setPosixFilePermissions(input, permissions); }
			assertEquals(before, runtime.projectSnapshot()); assertSame(identity, runtime.searchIdentity());
		}
	}

	@Test void parentRenameWithImplicitDescendantCollisionReturns409WithoutMutation() throws Exception {
		Path jar = SymbolFixtureSupport.compileMappingHierarchyFixture(dir);
		Path mapping = dir.resolve("attached.tiny");
		Files.writeString(mapping, MappingImportServiceTest.HEADER + "c\tpkg/Other\tpkg/NewOuter$Inner\n");
		Path nativePath = dir.resolve("hierarchy.jadx");
		var document = dev.libjadx.project.NativeProjectDocument.newFromInputs(nativePath, List.of(jar)).withMappingsPath(mapping);
		document.save();
		Path input = dir.resolve("incoming.tiny");
		Files.writeString(input, MappingImportServiceTest.HEADER + "c\tpkg/Outer\tpkg/NewOuter\n\tc\tincoming outer comment\n");
		try (var runtime = new ProjectRuntime(nativePath, List.of(jar), List.of(dir));
				var server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); runtime.initializeAsync(document).get(20, TimeUnit.SECONDS);
			var before = runtime.projectSnapshot(); var identity = runtime.searchIdentity(); var pending = runtime.pendingEdits();
			var nativeHash = FileFingerprint.of(nativePath); var mappingHash = FileFingerprint.of(mapping); var inputHash = FileFingerprint.of(input);
			JsonNode error = capture("implicit-descendant-collision", send(server, ROUTE, request(runtime, input), null, null), 409).path("error");
			assertEquals("MAPPING_MERGE_CONFLICT", error.path("code").asText());
			assertEquals("ALIAS_COLLISION", error.path("details").path("category").asText());
			assertEquals(before, runtime.projectSnapshot()); assertEquals(pending, runtime.pendingEdits()); assertSame(identity, runtime.searchIdentity());
			assertEquals("pkg.Outer$Inner", runtime.decompiler().getRoot().resolveRawClass("pkg.Outer$Inner").getClassInfo().makeAliasRawFullName());
			assertEquals(nativeHash, FileFingerprint.of(nativePath)); assertEquals(mappingHash, FileFingerprint.of(mapping)); assertEquals(inputHash, FileFingerprint.of(input));
		}
	}

	@Test void externalNativeAndMappingConflictsAndZeroByteAttachedRegressionAreCaptured() throws Exception {
		Path jar = SymbolFixtureSupport.compileEditFixture(dir); Path mapping = dir.resolve("attached.tiny"); Files.writeString(mapping, MappingImportServiceTest.HEADER);
		Path nativePath = dir.resolve("project.jadx"); var document = dev.libjadx.project.NativeProjectDocument.newFromInputs(nativePath, List.of(jar)).withMappingsPath(mapping); document.save();
		Path input = dir.resolve("incoming.tiny"); Files.writeString(input, MappingImportServiceTest.HAPPY);
		try (var runtime = new ProjectRuntime(nativePath, List.of(jar), List.of(dir)); var server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); runtime.initializeAsync(document).get(20, TimeUnit.SECONDS);
			for (Path path : List.of(nativePath, mapping)) {
				byte[] accepted = Files.readAllBytes(path); Files.writeString(path, Files.readString(path) + "\n ");
				capture(path.equals(nativePath) ? "external-native" : "external-mapping", send(server, ROUTE, request(runtime, input), null, null), 409);
				Files.write(path, accepted);
			}
			Files.write(mapping, new byte[0]); var rev = runtime.projectSnapshot().revisions(); runtime.reloadProject(true, rev.sessionId(), rev.logicalRevision());
			assertNull(jadx.plugins.mappings.RenameMappingsData.getTree(runtime.decompiler().getRoot()));
			capture("zero-byte-attached", send(server, ROUTE, request(runtime, input), null, null), 422);
			assertFalse(runtime.projectSnapshot().dirty());
		}
	}
	@Test void loadingReturns503BeforeBodyValidation() throws Exception {
		Path jar = SymbolFixtureSupport.compileEditFixture(dir);
		try (var runtime = new ProjectRuntime(null, List.of(jar), List.of(dir)); var server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); capture("not-ready", send(server, ROUTE, "{}", null, null), 503);
		}
	}
	static String request(ProjectRuntime runtime, Path source) throws Exception {
		var rev = runtime.projectSnapshot().revisions(); return JSON.writeValueAsString(Map.of("sourcePath", source.toString(), "format", "TINY_V2", "mode", "MERGE_FAIL_ON_CONFLICT", "expectedSessionId", rev.sessionId(), "expectedLogicalRevision", rev.logicalRevision()));
	}
	static HttpResponse<String> send(HttpApiServer server, String path, String body, String type, String origin) throws Exception {
		var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.localPort() + "/api/v1" + path)).timeout(java.time.Duration.ofSeconds(15)).header("Content-Type", type == null ? "application/json" : type);
		if (origin != null) request.header("Origin", origin);
		return HTTP.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
	}
	private static HttpResponse<String> rawGet(HttpApiServer server, String path) throws Exception { return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.localPort()+"/api/v1"+path)).GET().build(), HttpResponse.BodyHandlers.ofString()); }
	private static JsonNode get(HttpApiServer server, String path) throws Exception { var response = rawGet(server, path); assertEquals(200, response.statusCode(), response.body()); return JSON.readTree(response.body()); }
	static JsonNode capture(String label, HttpResponse<String> response, int status) throws Exception {
		assertEquals(status, response.statusCode(), response.body()); JsonNode body = JSON.readTree(response.body());
		String schema = status == 200 ? "MappingImportReceipt" : "ErrorEnvelope";
		var spec = new ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory()).readTree(Path.of("openapi/openapi.yaml").toFile()); OpenApiExampleValidator.assertValid(spec, schema, body);
		Path directory = Files.createDirectories(Path.of("build/mapping-import-contract-responses")); Files.writeString(directory.resolve(label+".json"), JSON.writeValueAsString(Map.of("status", status, "schema", schema, "body", body))); return body;
	}
}
