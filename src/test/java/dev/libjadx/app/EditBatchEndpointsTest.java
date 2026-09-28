package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.libjadx.http.HttpApiServer;
import dev.libjadx.project.NativeProjectDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditBatchEndpointsTest {
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final HttpClient CLIENT = HttpClient.newHttpClient();
	@TempDir Path dir;

	@Test
	void realJadxBatchPrevalidatesStagesAndSavesNativeDeclarationEdits() throws Exception {
		Path jar = SymbolFixtureSupport.compileFixture(dir);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start();
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			JsonNode before = body(get(server, "/api/v1/project"));
			String session = before.path("revisions").path("sessionId").asText();
			String classCursor = body(get(server, "/api/v1/classes?pageSize=1")).path("nextCursor").asText();
			assertFalse(classCursor.isEmpty());
			String classRef = "{\"kind\":\"CLASS\",\"originalClassDescriptor\":\"Lprobe/SymbolFixture;\"}";
			String methodRef = "{\"kind\":\"METHOD\",\"originalClassDescriptor\":\"Lprobe/SymbolFixture;\",\"originalName\":\"mix\",\"originalDescriptor\":\"(I)I\"}";
			String fieldRef = "{\"kind\":\"FIELD\",\"originalClassDescriptor\":\"Lprobe/SymbolFixture;\",\"originalName\":\"count\",\"originalDescriptor\":\"I\"}";
			JsonNode sourceBefore = body(post(server, "/api/v1/decompile", "{\"ref\":" + classRef + "}"));
			String sourceSnapshotBefore = sourceBefore.path("sourceSnapshotId").asText();
			String renameClass = "{\"kind\":\"RENAME\",\"target\":" + classRef + ",\"newName\":\"EditedFixture\"}";
			JsonNode initiallyNoop = body(post(server, "/api/v1/edits/batch", "{\"items\":[{\"kind\":\"RENAME\","
					+ "\"target\":" + classRef + ",\"newName\":\"SymbolFixture\"}]}"));
			assertEquals("NO_CHANGE", initiallyNoop.path("outcome").asText());
			assertFalse(initiallyNoop.path("dirty").asBoolean());
			assertEquals(0, initiallyNoop.path("logicalRevisionAfter").asLong());
			String renameMethod = "{\"kind\":\"RENAME\",\"target\":" + methodRef + ",\"newName\":\"editedMix\"}";
			String renameField = "{\"kind\":\"RENAME\",\"target\":" + fieldRef + ",\"newName\":\"editedCount\"}";
			String comment = "{\"kind\":\"SET_COMMENT\",\"target\":" + methodRef + ",\"comment\":\"reviewed method\",\"style\":\"LINE\"}";
			String prefix = "{\"expectedSessionId\":\"" + session + "\",\"expectedLogicalRevision\":0,\"items\":[";
			JsonNode invalid = body(post(server, "/api/v1/edits/batch", prefix + renameClass + ","
					+ "{\"kind\":\"RENAME\",\"target\":" + fieldRef + ",\"newName\":\"bad-name\"}]}"));
			assertEquals("INVALID_REQUEST", invalid.path("error").path("code").asText());
			assertEquals(1, invalid.path("error").path("details").path("itemErrors").get(0).path("index").asInt());
			assertEquals(0, runtime.projectSnapshot().revisions().logicalRevision());
			assertEquals(200, get(server, "/api/v1/classes?pageSize=1&cursor=" + classCursor).statusCode());
			JsonNode applied = body(post(server, "/api/v1/edits/batch", prefix + renameClass + ","
					+ renameMethod + "," + renameField + "," + comment + "]}"));
			assertEquals("APPLIED", applied.path("outcome").asText(), applied.toString());
			assertEquals(1, applied.path("logicalRevisionAfter").asLong());
			assertTrue(applied.path("dirty").asBoolean());
			JsonNode sourceAfter = body(post(server, "/api/v1/decompile", "{\"ref\":" + classRef + "}"));
			for (String expected : List.of("class EditedFixture", "editedMix", "editedCount", "reviewed method")) {
				assertTrue(sourceAfter.path("source").asText().contains(expected), expected + " in " + sourceAfter.toPrettyString());
			}
			assertFalse(sourceSnapshotBefore.equals(sourceAfter.path("sourceSnapshotId").asText()));
			assertEquals("STALE_REVISION", body(post(server, "/api/v1/decompile", "{\"ref\":" + classRef
					+ ",\"expectedSourceSnapshotId\":\"" + sourceSnapshotBefore + "\"}"))
					.path("error").path("code").asText());
			assertEquals("STALE_REVISION", body(get(server, "/api/v1/classes?pageSize=1&cursor=" + classCursor))
					.path("error").path("code").asText());
			assertFalse(applied.path("saved").asBoolean());
			assertTrue(body(post(server, "/api/v1/project/pending-edits/export", "{}"))
					.path("codeData").toString().contains("editedMix"));
			JsonNode noop = body(post(server, "/api/v1/edits/batch", "{\"items\":[" + renameClass + "]}"));
			assertEquals("NO_CHANGE", noop.path("outcome").asText(), noop.toString());
			assertEquals(1, noop.path("logicalRevisionAfter").asLong());
			assertEquals(sourceAfter.path("sourceSnapshotId"), body(post(server, "/api/v1/decompile", "{\"ref\":" + classRef
					+ "}")).path("sourceSnapshotId"));
			assertEquals("STALE_REVISION", body(post(server, "/api/v1/edits/batch", prefix + renameClass + "]}"))
					.path("error").path("code").asText());
			Path nativePath = dir.resolve("edited.jadx");
			assertFalse(Files.exists(nativePath));
			assertEquals(200, post(server, "/api/v1/project/save", "{\"targetPath\":\"" + nativePath + "\"}").statusCode());
			NativeProjectDocument saved = NativeProjectDocument.open(nativePath);
			assertEquals(3, saved.getCodeData().getRenames().size());
			assertEquals(1, saved.getCodeData().getComments().size());
			assertTrue(Files.readString(nativePath).contains("editedCount"));
		} finally { runtime.close(); }
	}

	@Test
	void nativeUnknownFieldsAndRelativeInputsSurviveOneEditedEntry() throws Exception {
		Path fixture = Path.of("tests/fixtures/native-project").toAbsolutePath();
		for (String name : List.of("sample.jar.jadx", "sample.jar", "second.jar", "sample.tiny")) {
			Files.copy(fixture.resolve(name), dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
		}
		Path path = dir.resolve("sample.jar.jadx");
		var root = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(Files.readString(path));
		root.putObject("futureRoot").put("retain", true);
		var code = (com.fasterxml.jackson.databind.node.ObjectNode) root.path("codeData");
		code.put("futureCodeData", "retain");
		code.set("renames", JSON.readTree("""
				[{"nodeRef":{"refType":"CLASS","declClass":"probe.Sample"},"newName":"BeforeSample","futureRename":"keep"},
				 {"nodeRef":{"refType":"CLASS","declClass":"probe.Second"},"newName":"BeforeSecond","futureOtherRename":17}]
				"""));
		code.set("comments", JSON.readTree("""
				[{"nodeRef":{"refType":"CLASS","declClass":"probe.Sample"},"comment":"before","style":"LINE","futureLine":"keep"},
				 {"nodeRef":{"refType":"CLASS","declClass":"probe.Sample"},"comment":"other style","style":"BLOCK","futureBlock":17}]
				"""));
		Files.writeString(path, JSON.writeValueAsString(root));
		NativeProjectDocument nativeProject = NativeProjectDocument.open(path);
		ProjectRuntime runtime = new ProjectRuntime(path, nativeProject.getInputFiles(), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start();
			runtime.initializeAsync(nativeProject).get(20, TimeUnit.SECONDS);
			String beforeBytes = Files.readString(path);
			String item = "{\"kind\":\"RENAME\",\"target\":{\"kind\":\"CLASS\","
					+ "\"originalClassDescriptor\":\"Lprobe/Sample;\"},\"newName\":\"AfterSample\"}";
			String comment = "{\"kind\":\"SET_COMMENT\",\"target\":{\"kind\":\"CLASS\","
					+ "\"originalClassDescriptor\":\"Lprobe/Sample;\"},\"comment\":\"after\",\"style\":\"LINE\"}";
			assertEquals(200, post(server, "/api/v1/edits/batch", "{\"items\":[" + item + "," + comment + "]}").statusCode());
			assertEquals(beforeBytes, Files.readString(path));
			assertEquals(200, post(server, "/api/v1/project/save", "{}").statusCode());
			JsonNode saved = JSON.readTree(Files.readString(path));
			assertTrue(saved.path("futureRoot").path("retain").asBoolean());
			assertEquals("retain", saved.path("codeData").path("futureCodeData").asText());
			assertEquals("sample.jar", saved.path("files").get(0).asText());
			assertEquals("second.jar", saved.path("files").get(1).asText());
			assertEquals("sample.tiny", saved.path("mappingsPath").asText());
			assertEquals("AfterSample", saved.path("codeData").path("renames").get(0).path("newName").asText());
			assertEquals("keep", saved.path("codeData").path("renames").get(0).path("futureRename").asText());
			assertEquals("BeforeSecond", saved.path("codeData").path("renames").get(1).path("newName").asText());
			assertEquals(17, saved.path("codeData").path("renames").get(1).path("futureOtherRename").asInt());
			assertEquals("after", saved.path("codeData").path("comments").get(0).path("comment").asText());
			assertEquals("keep", saved.path("codeData").path("comments").get(0).path("futureLine").asText());
			assertEquals("other style", saved.path("codeData").path("comments").get(1).path("comment").asText());
			assertEquals(17, saved.path("codeData").path("comments").get(1).path("futureBlock").asInt());
		} finally { runtime.close(); }
	}

	@Test
	void liveEditContractErrorsAndResourceBudget() throws Exception {
		Path jar = SymbolFixtureSupport.compileFixture(dir);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
		Path captures = Path.of("build/edit-contract-responses");
		Files.createDirectories(captures);
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start();
			capture(captures, "503-loading", post(server, "/api/v1/edits/batch", "{}"), 503, "ErrorEnvelope");
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			String ref = "{\"kind\":\"CLASS\",\"originalClassDescriptor\":\"Lprobe/SymbolFixture;\"}";
			String valid = "{\"items\":[{\"kind\":\"RENAME\",\"target\":" + ref + ",\"newName\":\"ContractAlias\"}]}";
			capture(captures, "400-duplicate-json", post(server, "/api/v1/edits/batch", "{\"items\":[],\"items\":[]}"), 400, "ErrorEnvelope");
			capture(captures, "403-origin", send(server, valid, "application/json", "http://evil.invalid"), 403, "ErrorEnvelope");
			capture(captures, "404-missing", post(server, "/api/v1/edits/batch", valid.replace("SymbolFixture", "Missing")), 404, "ErrorEnvelope");
			capture(captures, "415-content-type", send(server, valid, "text/plain", null), 415, "ErrorEnvelope");
			capture(captures, "422-kind", post(server, "/api/v1/edits/batch", valid.replace("RENAME", "RENAME_LOCAL")), 422, "ErrorEnvelope");
			capture(captures, "429-body", post(server, "/api/v1/edits/batch", " ".repeat(65_537)), 429, "ErrorEnvelope");
			capture(captures, "200-applied", post(server, "/api/v1/edits/batch", valid), 200, "EditBatchResult");
			capture(captures, "200-no-change", post(server, "/api/v1/edits/batch", valid), 200, "EditBatchResult");
			String stale = valid.replace("{\"items\":", "{\"expectedSessionId\":\"" + runtime.projectSnapshot().revisions().sessionId()
					+ "\",\"expectedLogicalRevision\":0,\"items\":");
			capture(captures, "409-stale", post(server, "/api/v1/edits/batch", stale), 409, "ErrorEnvelope");
		} finally { runtime.close(); }
	}

	@Test
	void ambiguousExistingNativeCommentIsRejectedBeforeMutation() throws Exception {
		Path fixture = Path.of("tests/fixtures/native-project").toAbsolutePath();
		for (String name : List.of("sample.jar.jadx", "sample.jar", "second.jar", "sample.tiny")) {
			Files.copy(fixture.resolve(name), dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
		}
		Path path = dir.resolve("sample.jar.jadx");
		var root = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(Files.readString(path));
		((com.fasterxml.jackson.databind.node.ObjectNode) root.path("codeData")).set("comments", JSON.readTree("""
				[{"nodeRef":{"refType":"CLASS","declClass":"probe.Sample"},"comment":"one","style":"LINE"},
				 {"nodeRef":{"refType":"CLASS","declClass":"probe.Sample"},"comment":"two","style":"LINE"}]
				"""));
		Files.writeString(path, JSON.writeValueAsString(root));
		String original = Files.readString(path);
		NativeProjectDocument nativeProject = NativeProjectDocument.open(path);
		ProjectRuntime runtime = new ProjectRuntime(path, nativeProject.getInputFiles(), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); runtime.initializeAsync(nativeProject).get(20, TimeUnit.SECONDS);
			String request = "{\"items\":[{\"kind\":\"SET_COMMENT\",\"target\":{\"kind\":\"CLASS\","
					+ "\"originalClassDescriptor\":\"Lprobe/Sample;\"},\"comment\":\"replacement\",\"style\":\"LINE\"}]}";
			assertEquals("INVALID_ENTITY_ID", body(post(server, "/api/v1/edits/batch", request))
					.path("error").path("code").asText());
			assertEquals(0, runtime.projectSnapshot().revisions().logicalRevision());
			assertFalse(runtime.projectSnapshot().dirty());
			assertEquals(original, Files.readString(path));
		} finally { runtime.close(); }
	}

	private static void capture(Path directory, String name, HttpResponse<String> response, int status, String schema) throws Exception {
		assertEquals(status, response.statusCode(), response.body());
		Files.writeString(directory.resolve(name + ".json"), JSON.writeValueAsString(java.util.Map.of(
				"status", status, "schema", schema, "body", body(response))));
	}

	private static HttpResponse<String> send(HttpApiServer server, String value, String contentType, String origin) throws Exception {
		HttpRequest.Builder request = HttpRequest.newBuilder(uri(server, "/api/v1/edits/batch"))
				.header("Content-Type", contentType).POST(HttpRequest.BodyPublishers.ofString(value));
		if (origin != null) request.header("Origin", origin);
		return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
	}

	private static HttpResponse<String> get(HttpApiServer server, String path) throws Exception {
		return CLIENT.send(HttpRequest.newBuilder(uri(server, path)).GET().build(), HttpResponse.BodyHandlers.ofString());
	}
	private static HttpResponse<String> post(HttpApiServer server, String path, String value) throws Exception {
		return CLIENT.send(HttpRequest.newBuilder(uri(server, path)).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(value)).build(), HttpResponse.BodyHandlers.ofString());
	}
	private static URI uri(HttpApiServer server, String path) {
		return URI.create("http://127.0.0.1:" + server.localPort() + path);
	}
	private static JsonNode body(HttpResponse<String> response) throws Exception { return JSON.readTree(response.body()); }
}
