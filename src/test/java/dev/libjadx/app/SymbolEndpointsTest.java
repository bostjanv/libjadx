package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.libjadx.http.HttpApiServer;
import dev.libjadx.project.NativeProjectDocument;
import jadx.api.data.impl.JadxCodeData;
import jadx.api.data.impl.JadxCodeRename;
import jadx.api.data.impl.JadxNodeRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SymbolEndpointsTest {
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final HttpClient CLIENT = HttpClient.newHttpClient();
	@TempDir Path dir;

	@Test
	void rawFixtureListsNestedAndAnonymousClassesAndResolvesExactMembers() throws Exception {
		Path jar = SymbolFixtureSupport.compileFixture(dir);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start();
			assertError(get(server, "/api/v1/classes"), 503, "PROJECT_NOT_READY");
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			List<String> descriptors = new ArrayList<>();
			String cursor = null;
			do {
				String path = "/api/v1/classes?pageSize=1" + (cursor == null ? "" : "&cursor=" + encode(cursor));
				HttpResponse<String> response = get(server, path);
				assertEquals(200, response.statusCode(), response.body());
				assertHeaders(response);
				JsonNode page = body(response);
				assertEquals("JADX_VISIBLE", page.path("scope").asText());
				assertEquals("UNVERIFIED", page.path("sourceCoverage").asText());
				assertEquals(1, page.path("items").size());
				descriptors.add(page.path("items").get(0).path("ref").path("originalClassDescriptor").asText());
				cursor = page.path("nextCursor").isNull() ? null : page.path("nextCursor").asText();
				assertEquals(cursor == null, page.path("complete").asBoolean());
			} while (cursor != null);
			assertEquals(List.of("Lprobe/SymbolFixture$1;", "Lprobe/SymbolFixture$Inner;", "Lprobe/SymbolFixture;"), descriptors);
			JsonNode top = body(get(server, "/api/v1/classes?includeInner=false"));
			assertEquals(1, top.path("items").size());
			assertEquals("RESOLVED", resolve(server, JSON.writeValueAsString(top.path("items").get(0).path("ref")))
					.path("outcome").asText());
			assertEquals(3, body(get(server, "/api/v1/classes?packagePrefix=probe")).path("items").size());
			assertEquals(0, body(get(server, "/api/v1/classes?packagePrefix=pro")).path("items").size());
			JsonNode anonymous = body(get(server, "/api/v1/classes?nameContains=%241"));
			assertFalse(anonymous.path("items").get(0).path("codeAvailable").asBoolean());
			assertEquals("RESOLVED", resolve(server, "{\"kind\":\"CLASS\",\"originalClassDescriptor\":\"Lprobe/SymbolFixture$Inner;\"}")
					.path("outcome").asText());
			JsonNode method = resolve(server, "{\"kind\":\"METHOD\",\"originalClassDescriptor\":\"Lprobe/SymbolFixture;\","
					+ "\"originalName\":\"mix\",\"originalDescriptor\":\"([Ljava/lang/String;I)Ljava/lang/String;\"}");
			assertEquals("RESOLVED", method.path("outcome").asText());
			assertEquals("mix", method.path("symbol").path("displayName").asText());
			assertEquals("UNAVAILABLE", method.path("symbol").path("provenance").asText());
			assertEquals("RESOLVED", resolve(server, "{\"kind\":\"METHOD\",\"originalClassDescriptor\":\"Lprobe/SymbolFixture;\","
					+ "\"originalName\":\"<init>\",\"originalDescriptor\":\"()V\"}").path("outcome").asText());
			assertEquals("RESOLVED", resolve(server, "{\"kind\":\"METHOD\",\"originalClassDescriptor\":\"Lprobe/SymbolFixture;\","
					+ "\"originalName\":\"<clinit>\",\"originalDescriptor\":\"()V\"}").path("outcome").asText());
			assertEquals("RESOLVED", resolve(server, "{\"kind\":\"FIELD\",\"originalClassDescriptor\":\"Lprobe/SymbolFixture;\","
					+ "\"originalName\":\"names\",\"originalDescriptor\":\"[Ljava/lang/String;\"}").path("outcome").asText());
			assertEquals("NOT_FOUND", resolve(server, "{\"kind\":\"METHOD\",\"originalClassDescriptor\":\"Lprobe/SymbolFixture;\","
					+ "\"originalName\":\"mix\",\"originalDescriptor\":\"(I)V\"}").path("outcome").asText());
			assertEquals("NOT_FOUND", resolve(server, "{\"kind\":\"FIELD\",\"originalClassDescriptor\":\"Lprobe/SymbolFixture;\","
					+ "\"originalName\":\"names\",\"originalDescriptor\":\"I\"}").path("outcome").asText());
			assertEquals("PROVENANCE_UNAVAILABLE", resolve(server, "{\"kind\":\"CLASS\","
					+ "\"originalClassDescriptor\":\"Lprobe/SymbolFixture;\",\"inputIdentity\":\"sha256:claimed\"}")
					.path("outcome").asText());
			assertFalse(body(get(server, "/api/v1/project")).path("dirty").asBoolean());
			String beforeSave = body(get(server, "/api/v1/classes?pageSize=1")).path("nextCursor").asText();
			String session = runtime.projectSnapshot().revisions().sessionId();
			Path nativeTarget = dir.resolve("symbols.jar.jadx");
			runtime.saveProject(nativeTarget, session, 0L);
			assertTrue(Files.exists(nativeTarget));
			assertError(get(server, "/api/v1/classes?pageSize=1&cursor=" + encode(beforeSave)), 409, "STALE_REVISION");
			runtime.reloadProject(false, session, 0);
			assertEquals("RESOLVED", resolve(server, "{\"kind\":\"CLASS\","
					+ "\"originalClassDescriptor\":\"Lprobe/SymbolFixture;\"}").path("outcome").asText());
		} finally { runtime.close(); }
	}

	@Test
	void nativeMappingAndUnsavedAliasKeepRawRefAndInvalidateCursors() throws Exception {
		Path fixture = Path.of("tests/fixtures/native-project").toAbsolutePath();
		for (String file : List.of("sample.jar.jadx", "sample.jar", "second.jar", "sample.tiny"))
			Files.copy(fixture.resolve(file), dir.resolve(file), StandardCopyOption.REPLACE_EXISTING);
		Path project = dir.resolve("sample.jar.jadx");
		NativeProjectDocument document = NativeProjectDocument.open(project);
		ProjectRuntime runtime = new ProjectRuntime(project, document.getInputFiles(), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start();
			runtime.initializeAsync(document).get(20, TimeUnit.SECONDS);
			JsonNode mapped = resolve(server, "{\"kind\":\"CLASS\",\"originalClassDescriptor\":\"Lprobe/Second;\"}");
			assertEquals("RESOLVED", mapped.path("outcome").asText());
			assertEquals("probe.RenamedSecond", mapped.path("symbol").path("displayQualifiedName").asText());
			String raw = "{\"kind\":\"CLASS\",\"originalClassDescriptor\":\"Lprobe/Sample;\"}";
			String cursor = body(get(server, "/api/v1/classes?pageSize=1")).path("nextCursor").asText();
			assertNotNull(cursor);
			JadxCodeData code = document.getCodeData();
			code.setRenames(List.of(new JadxCodeRename(JadxNodeRef.forCls("probe.Sample"), "UnsavedAlias")));
			runtime.replaceCodeData(code, 0);
			assertError(get(server, "/api/v1/classes?pageSize=1&cursor=" + encode(cursor)), 409, "STALE_REVISION");
			assertEquals("probe.UnsavedAlias", resolve(server, raw).path("symbol").path("displayQualifiedName").asText());
			assertEquals(1, body(get(server, "/api/v1/classes?nameDomain=alias&nameContains=UnsavedAlias"))
					.path("items").size());
			assertEquals(0, body(get(server, "/api/v1/classes?nameDomain=original&nameContains=UnsavedAlias"))
					.path("items").size());
			assertFalse(Files.readString(project).contains("UnsavedAlias"));
			String session = runtime.projectSnapshot().revisions().sessionId();
			assertError(post(server, "/api/v1/symbols/resolve", "{\"ref\":" + raw + ",\"expectedSessionId\":\"" + session
					+ "\",\"expectedLogicalRevision\":0}"), 409, "STALE_REVISION");
			String currentCursor = body(get(server, "/api/v1/classes?pageSize=1")).path("nextCursor").asText();
			Path mapping = dir.resolve("replacement.tiny");
			Files.writeString(mapping, "tiny\t2\t0\toriginal\tmapped\nc\tprobe/Second\tprobe/AnotherSecond\n");
			runtime.updateMappingsPath(mapping, session, 1);
			assertError(get(server, "/api/v1/classes?pageSize=1&cursor=" + encode(currentCursor)), 409, "STALE_REVISION");
			assertEquals("probe.AnotherSecond", resolve(server, "{\"kind\":\"CLASS\",\"originalClassDescriptor\":\"Lprobe/Second;\"}")
					.path("symbol").path("displayQualifiedName").asText());
			assertEquals("probe.UnsavedAlias", resolve(server, raw).path("symbol").path("displayQualifiedName").asText());
			assertTrue(runtime.projectSnapshot().dirty());
			runtime.saveProject(null, session, 2L);
			String savedCursor = body(get(server, "/api/v1/classes?pageSize=1")).path("nextCursor").asText();
			runtime.reloadProject(false, session, 2);
			assertError(get(server, "/api/v1/classes?pageSize=1&cursor=" + encode(savedCursor)), 409, "STALE_REVISION");
			assertEquals("probe.UnsavedAlias", resolve(server, raw).path("symbol").path("displayQualifiedName").asText());
		} finally { runtime.close(); }
	}

	@Test
	void duplicateJarProbeAndRestartCannotClaimExactOriginOrReuseCursor() throws Exception {
		Path one = SymbolFixtureSupport.duplicateJar(dir, "one", 1);
		Path two = SymbolFixtureSupport.duplicateJar(dir, "two", 2);
		String cursor;
		ProjectRuntime first = new ProjectRuntime(null, List.of(one, two), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, first)) {
			server.start(); first.initializeAsync(null).get(20, TimeUnit.SECONDS);
			JsonNode result = resolve(server, "{\"kind\":\"CLASS\",\"originalClassDescriptor\":\"Lduplicate/Clash;\"}");
			assertEquals("RESOLVED", result.path("outcome").asText());
			assertEquals("UNAVAILABLE", result.path("symbol").path("provenance").asText());
			assertEquals(1, body(get(server, "/api/v1/classes")).path("items").size());
			// The duplicate loader exposed only one visible class; use a multi-class fixture for restart.
		} finally { first.close(); }
		Path fixture = SymbolFixtureSupport.compileFixture(dir.resolve("restart"));
		ProjectRuntime before = new ProjectRuntime(null, List.of(fixture), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, before)) {
			server.start(); before.initializeAsync(null).get(20, TimeUnit.SECONDS);
			cursor = body(get(server, "/api/v1/classes?pageSize=1")).path("nextCursor").asText();
		} finally { before.close(); }
		ProjectRuntime after = new ProjectRuntime(null, List.of(fixture), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, after)) {
			server.start(); after.initializeAsync(null).get(20, TimeUnit.SECONDS);
			assertError(get(server, "/api/v1/classes?pageSize=1&cursor=" + encode(cursor)), 409, "STALE_REVISION");
		} finally { after.close(); }
	}

	@Test
	void classfileReturnOnlyOverloadsResolveByFullDescriptor() throws Exception {
		Path jar = SymbolFixtureSupport.returnTypeClashJar(dir);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			String prefix = "{\"kind\":\"METHOD\",\"originalClassDescriptor\":\"Lprobe/ReturnClash;\","
					+ "\"originalName\":\"value\",\"originalDescriptor\":\"";
			assertEquals("RESOLVED", resolve(server, prefix + "()I\"}").path("outcome").asText());
			assertEquals("RESOLVED", resolve(server, prefix + "()Ljava/lang/String;\"}").path("outcome").asText());
			assertEquals("NOT_FOUND", resolve(server, prefix + "()J\"}").path("outcome").asText());
		} finally { runtime.close(); }
	}

	@Test
	void validationAndBusyAdmissionUseSharedErrorEnvelope() throws Exception {
		Path jar = SymbolFixtureSupport.compileFixture(dir);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			assertError(get(server, "/api/v1/classes?pageSize=101"), 400, "INVALID_REQUEST");
			assertError(get(server, "/api/v1/classes?pageSize=0"), 400, "INVALID_REQUEST");
			assertError(get(server, "/api/v1/classes?unknown=1"), 400, "INVALID_REQUEST");
			assertError(get(server, "/api/v1/classes?pageSize=1&pageSize=2"), 400, "INVALID_REQUEST");
			assertError(get(server, "/api/v1/classes?includeInner=maybe"), 400, "INVALID_REQUEST");
			assertError(get(server, "/api/v1/classes?cursor=garbage"), 400, "INVALID_REQUEST");
			assertError(post(server, "/api/v1/symbols/resolve", "{\"ref\":{\"kind\":\"METHOD\","
					+ "\"originalClassDescriptor\":\"Lprobe/SymbolFixture;\",\"originalName\":\"mix\","
					+ "\"originalDescriptor\":\"(V)V\"}}"), 400, "INVALID_REQUEST");
			assertError(post(server, "/api/v1/symbols/resolve", "{\"ref\":{\"kind\":\"CLASS\","
					+ "\"originalClassDescriptor\":\"Lprobe/SymbolFixture;\",\"extra\":true}}"), 400, "INVALID_REQUEST");
			assertError(post(server, "/api/v1/symbols/resolve", "{\"ref\":{\"kind\":\"CLASS\","
					+ "\"originalClassDescriptor\":\"Lprobe/SymbolFixture;\"},\"expectedSessionId\":\"x\"}"), 400, "INVALID_REQUEST");
			assertError(post(server, "/api/v1/symbols/resolve", "{\"ref\":{\"kind\":\"CLASS\","
					+ "\"originalClassDescriptor\":\"Lprobe/SymbolFixture;\"},\"padding\":\""
					+ "x".repeat(65_536) + "\"}"), 400, "INVALID_REQUEST");
			HttpResponse<String> wrongType = CLIENT.send(HttpRequest.newBuilder(uri(server, "/api/v1/symbols/resolve"))
					.header("Content-Type", "text/plain").POST(HttpRequest.BodyPublishers.ofString("{}"))
					.build(), HttpResponse.BodyHandlers.ofString());
			assertError(wrongType, 415, "INVALID_REQUEST");
			assertError(post(server, "/api/v1/classes", "{}"), 405, "METHOD_NOT_ALLOWED");
			CountDownLatch entered = new CountDownLatch(1);
			CountDownLatch release = new CountDownLatch(1);
			CompletableFuture<Void> held = CompletableFuture.runAsync(() -> runtime.withPrimarySymbolRead("held", context -> {
				entered.countDown();
				try { release.await(10, TimeUnit.SECONDS); }
				catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
				return null;
			}));
			try {
				assertTrue(entered.await(5, TimeUnit.SECONDS));
				assertError(get(server, "/api/v1/classes"), 409, "PROJECT_BUSY");
				assertError(post(server, "/api/v1/symbols/resolve", "{\"ref\":{\"kind\":\"CLASS\","
						+ "\"originalClassDescriptor\":\"Lprobe/SymbolFixture;\"}}"), 409, "PROJECT_BUSY");
				assertError(post(server, "/api/v1/shutdown", "{\"policy\":\"discard\"}"), 409, "PROJECT_BUSY");
			} finally { release.countDown(); held.get(10, TimeUnit.SECONDS); }
		} finally { runtime.close(); }
	}

	private static JsonNode resolve(HttpApiServer server, String ref) throws Exception {
		HttpResponse<String> response = post(server, "/api/v1/symbols/resolve", "{\"ref\":" + ref + "}");
		assertEquals(200, response.statusCode(), response.body());
		assertHeaders(response);
		return body(response);
	}

	private static void assertError(HttpResponse<String> response, int status, String code) throws Exception {
		assertEquals(status, response.statusCode(), response.body());
		assertEquals(code, body(response).path("error").path("code").asText());
		assertHeaders(response);
	}

	private static void assertHeaders(HttpResponse<String> response) {
		assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(null));
		assertTrue(response.headers().firstValue("X-Request-Id").isPresent());
	}

	private static JsonNode body(HttpResponse<String> response) throws Exception { return JSON.readTree(response.body()); }
	private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
	private static URI uri(HttpApiServer server, String path) { return URI.create("http://127.0.0.1:" + server.localPort() + path); }
	private static HttpResponse<String> get(HttpApiServer server, String path) throws Exception {
		return CLIENT.send(HttpRequest.newBuilder(uri(server, path)).GET().build(), HttpResponse.BodyHandlers.ofString());
	}
	private static HttpResponse<String> post(HttpApiServer server, String path, String value) throws Exception {
		return CLIENT.send(HttpRequest.newBuilder(uri(server, path)).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(value)).build(), HttpResponse.BodyHandlers.ofString());
	}
}
