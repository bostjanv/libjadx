package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
import jadx.api.data.impl.JadxCodeComment;
import jadx.api.data.impl.JadxCodeRename;
import jadx.api.data.impl.JadxNodeRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectEndpointsTest {
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final HttpClient CLIENT = HttpClient.newHttpClient();
	@TempDir Path dir;

	@Test
	void realJadxProjectSaveConflictPendingExportAndExplicitReload() throws Exception {
		Path fixture = Path.of("tests/fixtures/native-project").toAbsolutePath();
		for (String name : List.of("sample.jar.jadx", "sample.jar", "second.jar", "sample.tiny")) {
			Files.copy(fixture.resolve(name), dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
		}
		Path path = dir.resolve("sample.jar.jadx");
		NativeProjectDocument nativeProject = NativeProjectDocument.open(path);
		ProjectRuntime runtime = new ProjectRuntime(path, nativeProject.getInputFiles(), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start();
			runtime.initializeAsync(nativeProject).get(20, TimeUnit.SECONDS);
			JsonNode initial = body(get(server, "/api/v1/project"));
			assertFalse(initial.path("dirty").asBoolean());
			assertEquals(0, initial.path("revisions").path("logicalRevision").asLong());
			String session = initial.path("revisions").path("sessionId").asText();
			String persisted = initial.path("revisions").path("persistedIdentity").asText();
			assertEquals(400, post(server, "/api/v1/project/save", "{malformed").statusCode());
			assertEquals(415, CLIENT.send(HttpRequest.newBuilder(uri(server, "/api/v1/project/save"))
					.header("Content-Type", "text/plain")
					.POST(HttpRequest.BodyPublishers.ofString("{}"))
					.build(), HttpResponse.BodyHandlers.ofString()).statusCode());

			var code = NativeProjectDocument.open(path).getCodeData();
			code.setRenames(List.of(new JadxCodeRename(JadxNodeRef.forCls("probe.Sample"), "EndpointAlias")));
			code.setComments(List.of(new JadxCodeComment(JadxNodeRef.forCls("probe.Sample"), "pending endpoint edit")));
			runtime.replaceCodeData(code, 0);
			assertTrue(body(get(server, "/api/v1/project")).path("dirty").asBoolean());
			assertFalse(Files.readString(path).contains("EndpointAlias"));
			assertEquals(409, post(server, "/api/v1/project/save", "{\"expectedLogicalRevision\":0,\"expectedSessionId\":\"" + session + "\"}").statusCode());
			assertEquals(409, post(server, "/api/v1/project/save", "{\"expectedLogicalRevision\":1,\"expectedSessionId\":\"00000000-0000-0000-0000-000000000000\"}").statusCode());
			assertEquals(200, post(server, "/api/v1/project/save", "{\"expectedLogicalRevision\":1,\"expectedSessionId\":\"" + session + "\"}").statusCode());
			assertTrue(Files.readString(path).contains("EndpointAlias"));
			assertFalse(body(get(server, "/api/v1/project")).path("dirty").asBoolean());
			assertNotEquals(persisted, body(get(server, "/api/v1/project")).path("revisions").path("persistedIdentity").asText());
			assertEquals(session, body(get(server, "/api/v1/project")).path("revisions").path("sessionId").asText());

			var changed = NativeProjectDocument.open(path).getCodeData();
			changed.setComments(List.of(new JadxCodeComment(JadxNodeRef.forCls("probe.Sample"), "second pending edit")));
			runtime.replaceCodeData(changed, 1);
			Files.writeString(dir.resolve("sample.tiny"), Files.readString(dir.resolve("sample.tiny")) + "\n");
			JsonNode conflict = body(post(server, "/api/v1/project/save", "{}"));
			assertEquals("EXTERNAL_MODIFICATION_CONFLICT", conflict.path("error").path("code").asText());
			assertTrue(body(post(server, "/api/v1/project/pending-edits/export", "{}"))
					.path("codeData").toString().contains("second pending edit"));
			assertEquals(400, post(server, "/api/v1/project/reload", "{\"discardUnsaved\":true}").statusCode());
			JsonNode staleReload = body(post(server, "/api/v1/project/reload", "{\"discardUnsaved\":true,"
					+ "\"expectedSessionId\":\"" + session + "\",\"expectedLogicalRevision\":1}"));
			assertEquals("STALE_REVISION", staleReload.path("error").path("code").asText());
			assertTrue(body(get(server, "/api/v1/project")).path("dirty").asBoolean());
			assertTrue(body(post(server, "/api/v1/project/pending-edits/export", "{}"))
					.path("codeData").toString().contains("second pending edit"));
			HttpResponse<String> refusedDiscard = post(server, "/api/v1/project/reload", "{\"discardUnsaved\":false,"
					+ "\"expectedSessionId\":\"" + session + "\",\"expectedLogicalRevision\":2}");
			assertEquals(409, refusedDiscard.statusCode());
			assertFalse(body(refusedDiscard).path("error").path("retryable").asBoolean());
			assertEquals(200, post(server, "/api/v1/project/reload", "{\"discardUnsaved\":true,"
					+ "\"expectedSessionId\":\"" + session + "\",\"expectedLogicalRevision\":2}").statusCode());
			assertFalse(body(get(server, "/api/v1/project")).path("dirty").asBoolean());
			assertEquals("READY", runtime.status().state());
			assertEquals(session, body(get(server, "/api/v1/project")).path("revisions").path("sessionId").asText());
		} finally {
			runtime.close();
		}
	}

	@Test
	void mappingSettingRebuildRetainsUnsavedEditAndSavesNatively() throws Exception {
		Path fixture = Path.of("tests/fixtures/native-project").toAbsolutePath();
		for (String name : List.of("sample.jar.jadx", "sample.jar", "second.jar", "sample.tiny")) {
			Files.copy(fixture.resolve(name), dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
		}
		Path path = dir.resolve("sample.jar.jadx");
		Path newMapping = dir.resolve("other.tiny");
		Files.writeString(newMapping, "tiny\t2\t0\toriginal\tmapped\nc\tprobe/Second\tprobe/OtherSecond\n");
		NativeProjectDocument nativeProject = NativeProjectDocument.open(path);
		ProjectRuntime runtime = new ProjectRuntime(path, nativeProject.getInputFiles(), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start();
			runtime.initializeAsync(nativeProject).get(20, TimeUnit.SECONDS);
			String session = body(get(server, "/api/v1/project")).path("revisions").path("sessionId").asText();
			var code = NativeProjectDocument.open(path).getCodeData();
			code.setRenames(List.of(new JadxCodeRename(JadxNodeRef.forCls("probe.Sample"), "UnsavedMapped")));
			runtime.replaceCodeData(code, 0);
			HttpResponse<String> unsupported = CLIENT.send(HttpRequest.newBuilder(uri(server, "/api/v1/project/settings"))
					.header("Content-Type", "application/json")
					.method("PATCH", HttpRequest.BodyPublishers.ofString("{\"mappingsPath\":null,\"decompilationMode\":\"SIMPLE\","
							+ "\"expectedLogicalRevision\":1,\"expectedSessionId\":\"" + session + "\"}"))
					.build(), HttpResponse.BodyHandlers.ofString());
			assertEquals(422, unsupported.statusCode());
			assertEquals("UNSUPPORTED_CAPABILITY", body(unsupported).path("error").path("code").asText());
			assertEquals(1, runtime.projectSnapshot().revisions().logicalRevision());
			HttpRequest update = HttpRequest.newBuilder(uri(server, "/api/v1/project/settings"))
					.header("Content-Type", "application/json")
					.method("PATCH", HttpRequest.BodyPublishers.ofString("{\"mappingsPath\":\"" + newMapping +
							"\",\"expectedLogicalRevision\":1,\"expectedSessionId\":\"" + session + "\"}"))
					.build();
			HttpResponse<String> response = CLIENT.send(update, HttpResponse.BodyHandlers.ofString());
			assertEquals(200, response.statusCode(), response.body());
			assertEquals(newMapping.toString(), body(get(server, "/api/v1/project/settings")).path("mappingsPath").asText());
			assertTrue(body(get(server, "/api/v1/project")).path("dirty").asBoolean());
			assertEquals(2, body(get(server, "/api/v1/project")).path("revisions").path("logicalRevision").asLong());
			assertTrue(runtime.decompiler().getClasses().stream().anyMatch(cls -> cls.getFullName().equals("probe.OtherSecond")));
			assertTrue(runtime.decompiler().getClasses().stream().anyMatch(cls -> cls.getFullName().equals("probe.UnsavedMapped")));
			assertTrue(body(post(server, "/api/v1/project/pending-edits/export", "{}"))
					.path("codeData").toString().contains("UnsavedMapped"));
			assertEquals(200, post(server, "/api/v1/project/save", "{\"expectedLogicalRevision\":2,\"expectedSessionId\":\"" + session + "\"}").statusCode());
			NativeProjectDocument saved = NativeProjectDocument.open(path);
			assertEquals(newMapping, saved.getMappingsPath());
			assertTrue(Files.readString(path).contains("UnsavedMapped"));
		} finally {
			runtime.close();
		}
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

	private static JsonNode body(HttpResponse<String> response) throws Exception {
		return JSON.readTree(response.body());
	}
}
