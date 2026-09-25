package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.libjadx.http.HttpApiServer;
import dev.libjadx.project.NativeProjectDocument;
import dev.libjadx.jadxadapter.JadxSymbolAdapter;
import dev.libjadx.jadxadapter.JadxSourceAdapter;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.scheduler.ProjectBusyException;
import jadx.api.data.impl.JadxCodeComment;
import jadx.api.data.impl.JadxCodeRename;
import jadx.api.data.impl.JadxNodeRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DecompileEndpointsTest {
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final HttpClient HTTP = HttpClient.newHttpClient();
	@TempDir Path dir;

	@Test
	void realJadxClassAndMethodShareSourceSnapshotAndVerifiedExcerpt() throws Exception {
		Path jar = SymbolFixtureSupport.compileFixture(dir);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			JsonNode cls = success(server, ref("CLASS", "Lprobe/SymbolFixture;", null, null));
			OpenApiExampleValidator.assertValid(new ObjectMapper(new YAMLFactory()).readTree(
					Path.of("openapi/openapi.yaml").toFile()), "DecompileResult", cls);
			assertEquals("RESOLVED", cls.path("outcome").asText());
			assertEquals("COMPLETE", cls.path("status").asText());
			assertTrue(cls.path("source").asText().contains("class SymbolFixture"));
			String pinnedJava = runtime.withPrimarySymbolRead("source-evidence", context ->
					JadxSymbolAdapter.visibleClass(context.decompiler(), "Lprobe/SymbolFixture;", 0).getCodeInfo().getCodeStr());
			assertEquals(pinnedJava, cls.path("source").asText());
			assertEquals("Lprobe/SymbolFixture;", cls.path("sourceOwnerRef").path("originalClassDescriptor").asText());
			assertTrue(cls.path("sourceSnapshotId").asText().startsWith("sha256:"));
			assertTrue(cls.path("methodRangeAvailable").isNull());
			assertTrue(cls.path("annotations").size() > 0);
			for (JsonNode annotation : cls.path("annotations")) {
				int offset = annotation.path("position").path("offsetUtf16").asInt();
				assertTrue(offset >= 0 && offset < cls.path("source").asText().length());
				assertEquals(cls.path("sourceSnapshotId").asText(), annotation.path("sourceSnapshotId").asText());
			}
			assertTrue(hasAnnotation(cls, "REFERENCE", "FIELD", "count", "count"));
			assertTrue(hasAnnotation(cls, "REFERENCE", "METHOD", "mix", "mix"));
			JsonNode method = success(server, ref("METHOD", "Lprobe/SymbolFixture;", "mix", "(I)I"));
			OpenApiExampleValidator.assertValid(new ObjectMapper(new YAMLFactory()).readTree(
					Path.of("openapi/openapi.yaml").toFile()), "DecompileResult", method);
			assertEquals(cls.path("sourceSnapshotId").asText(), method.path("sourceSnapshotId").asText());
			assertEquals(cls.path("source").asText(), method.path("source").asText());
			assertTrue(method.path("methodRangeAvailable").asBoolean(), method.toPrettyString());
			int start = method.path("methodRange").path("startOffsetUtf16").asInt();
			int end = method.path("methodRange").path("endOffsetUtf16").asInt();
			assertEquals(method.path("methodSource").asText(), method.path("source").asText().substring(start, end));
			assertTrue(method.path("methodSource").asText().contains("mix(int"));
			JsonNode other = success(server, ref("METHOD", "Lprobe/SymbolFixture;", "mix",
					"([Ljava/lang/String;I)Ljava/lang/String;"));
			assertTrue(other.path("methodSource").asText().contains("mix(String[]"));
			assertNotEquals(method.path("methodRange").path("startOffsetUtf16").asInt(),
					other.path("methodRange").path("startOffsetUtf16").asInt());
			JsonNode initializer = success(server, ref("METHOD", "Lprobe/SymbolFixture;", "<clinit>", "()V"));
			assertFalse(initializer.path("methodRangeAvailable").asBoolean());
			assertTrue(initializer.path("methodSource").isNull());
			assertEquals("UNAVAILABLE", initializer.path("capabilities").path("methodRange").asText());
			assertError(post(server, "{\"ref\":" + ref("METHOD", "Lprobe/SymbolFixture;", "<clinit>", "()V")
					+ ",\"strict\":true}"), 409, "INCOMPLETE_ANALYSIS");
			JsonNode withoutAnnotations = body(post(server, "{\"ref\":"
					+ ref("METHOD", "Lprobe/SymbolFixture;", "mix", "(I)I")
					+ ",\"includeAnnotations\":false}"));
			assertEquals(0, withoutAnnotations.path("annotations").size());
			assertTrue(withoutAnnotations.path("methodRangeAvailable").asBoolean());
			JsonNode noCode = success(server, ref("CLASS", "Lprobe/SymbolFixture$1;", null, null));
			assertEquals("Lprobe/SymbolFixture;", noCode.path("sourceOwnerRef").path("originalClassDescriptor").asText());
			assertTrue(noCode.path("source").asText().contains("new Runnable()"));
			assertEquals(cls.path("sourceSnapshotId").asText(), noCode.path("sourceSnapshotId").asText());
			assertEquals("PROVENANCE_UNAVAILABLE", success(server,
					"{\"kind\":\"CLASS\",\"originalClassDescriptor\":\"Lprobe/SymbolFixture;\",\"inputIdentity\":\"claimed\"}")
					.path("outcome").asText());
		} finally { runtime.close(); }
	}

	@Test
	void temporaryModeIncludesUnsavedNativeEditsWithoutChangingPrimary() throws Exception {
		Path fixture = Path.of("tests/fixtures/native-project").toAbsolutePath();
		for (String file : List.of("sample.jar.jadx", "sample.jar", "second.jar", "sample.tiny"))
			Files.copy(fixture.resolve(file), dir.resolve(file), StandardCopyOption.REPLACE_EXISTING);
		Path path = dir.resolve("sample.jar.jadx");
		byte[] nativeBefore = Files.readAllBytes(path);
		NativeProjectDocument document = NativeProjectDocument.open(path);
		ProjectRuntime runtime = new ProjectRuntime(path, document.getInputFiles(), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); runtime.initializeAsync(document).get(20, TimeUnit.SECONDS);
			var data = document.getCodeData();
			data.setRenames(List.of(new JadxCodeRename(JadxNodeRef.forCls("probe.Sample"), "UnsavedSourceAlias")));
			data.setComments(List.of(new JadxCodeComment(JadxNodeRef.forCls("probe.Sample"), "unsaved source comment")));
			runtime.replaceCodeData(data, 0);
			long revision = runtime.projectSnapshot().revisions().logicalRevision();
			String sourceRef = ref("CLASS", "Lprobe/Sample;", null, null);
			JsonNode primary = success(server, sourceRef);
			JsonNode temporary = body(post(server, "{\"ref\":" + sourceRef + ",\"decompilationMode\":\"SIMPLE\"}"));
			assertEquals("RESOLVED", temporary.path("outcome").asText(), temporary.toPrettyString());
			assertEquals("SIMPLE", temporary.path("effectiveSettings").path("decompilationMode").asText());
			assertNotEquals(primary.path("effectiveSettings").path("fingerprint").asText(),
					temporary.path("effectiveSettings").path("fingerprint").asText());
			assertTrue(temporary.path("source").asText().contains("UnsavedSourceAlias"));
			assertTrue(temporary.path("source").asText().contains("unsaved source comment"));
			assertEquals(revision, runtime.projectSnapshot().revisions().logicalRevision());
			assertTrue(runtime.projectSnapshot().dirty());
			assertFalse(Files.readString(path).contains("UnsavedSourceAlias"));
			assertArrayEquals(nativeBefore, Files.readAllBytes(path));
			assertEquals(primary.path("sourceSnapshotId").asText(), success(server, sourceRef).path("sourceSnapshotId").asText());
			assertError(post(server, "{\"ref\":" + sourceRef + ",\"expectedSourceSnapshotId\":\""
					+ temporary.path("sourceSnapshotId").asText() + "\"}"), 409, "STALE_REVISION");
		} finally { runtime.close(); }
	}

	@Test
	void returnTypeOnlyOverloadsNeverAcquireEachOthersExcerpt() throws Exception {
		Path jar = SymbolFixtureSupport.returnTypeClashJar(dir);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			JsonNode number = success(server, ref("METHOD", "Lprobe/ReturnClash;", "value", "()I"));
			JsonNode text = success(server, ref("METHOD", "Lprobe/ReturnClash;", "value", "()Ljava/lang/String;"));
			assertEquals("RESOLVED", number.path("outcome").asText());
			assertEquals("RESOLVED", text.path("outcome").asText());
			assertEquals(number.path("sourceSnapshotId").asText(), text.path("sourceSnapshotId").asText());
			for (JsonNode result : List.of(number, text)) {
				if (result.path("methodRangeAvailable").asBoolean()) {
					int start = result.path("methodRange").path("startOffsetUtf16").asInt();
					int end = result.path("methodRange").path("endOffsetUtf16").asInt();
					assertEquals(result.path("source").asText().substring(start, end), result.path("methodSource").asText());
				} else assertTrue(result.path("methodSource").isNull());
			}
			if (number.path("methodRangeAvailable").asBoolean() && text.path("methodRangeAvailable").asBoolean())
				assertNotEquals(number.path("methodRange").path("startOffsetUtf16").asInt(),
						text.path("methodRange").path("startOffsetUtf16").asInt());
		} finally { runtime.close(); }
	}

	@Test
	void simulatedPinnedNodeErrorRendersPartialAndSanitizedDiagnostics() throws Exception {
		Path jar = SymbolFixtureSupport.compileFixture(dir);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			// Inject an error after a real Jadx fixture load. This tests the renderer,
			// not a naturally occurring partial decompilation fixture.
			runtime.withPrimarySymbolRead("simulated-error", context -> {
				var cls = JadxSymbolAdapter.visibleClass(context.decompiler(), "Lprobe/SymbolFixture;", 0);
				cls.getCodeInfo();
				cls.getClassNode().addError("simulated\ncode generation failure", null);
				cls.getClassNode().addWarnComment("simulated\nwarning");
				return null;
			});
			JsonNode result = success(server, ref("CLASS", "Lprobe/SymbolFixture;", null, null));
			assertEquals("PARTIAL", result.path("status").asText());
			assertTrue(result.path("source").asText().contains("class SymbolFixture"));
			assertTrue(result.path("diagnostics").toString().contains("Jadx error: simulated code generation failure"));
			assertTrue(result.path("diagnostics").toString().contains("Jadx warning: simulated warning"));
			assertTrue(result.path("classErrorCount").isNull());
		} finally { runtime.close(); }
	}

	@Test
	void abstractInterfaceConstructorAndLambdaCasesKeepTruthfulMethodRanges() throws Exception {
		Path jar = SymbolFixtureSupport.compileSourceFixture(dir);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			String owner = "Lprobe/SourceFixture;";
			JsonNode abstractMethod = success(server, ref("METHOD", owner, "missing", "(I)I"));
			assertEquals("RESOLVED", abstractMethod.path("outcome").asText());
			assertFalse(abstractMethod.path("methodRangeAvailable").asBoolean());
			assertTrue(abstractMethod.path("methodSource").isNull());
			assertTrue(abstractMethod.path("source").asText().contains("abstract int missing"));
			JsonNode contract = success(server, ref("METHOD", "Lprobe/SourceFixture$Api;", "invoke", "()V"));
			assertEquals("RESOLVED", contract.path("outcome").asText());
			assertFalse(contract.path("methodRangeAvailable").asBoolean());
			assertEquals(owner, contract.path("sourceOwnerRef").path("originalClassDescriptor").asText());
			JsonNode constructor = success(server, ref("METHOD", owner, "<init>", "()V"));
			assertEquals("RESOLVED", constructor.path("outcome").asText());
			if (constructor.path("methodRangeAvailable").asBoolean())
				assertTrue(constructor.path("methodSource").asText().contains("SourceFixture("));
			JsonNode tricky = success(server, ref("METHOD", owner, "tricky", "(I)I"));
			assertEquals("RESOLVED", tricky.path("outcome").asText());
			if (tricky.path("methodRangeAvailable").asBoolean()) {
				int start = tricky.path("methodRange").path("startOffsetUtf16").asInt();
				int end = tricky.path("methodRange").path("endOffsetUtf16").asInt();
				assertEquals(tricky.path("source").asText().substring(start, end), tricky.path("methodSource").asText());
				assertTrue(tricky.path("methodSource").asText().contains("return"));
			} else assertTrue(tricky.path("methodSource").isNull());
			JsonNode lambda = success(server, ref("METHOD", owner, "lambda$tricky$0",
					"(Ljava/lang/String;Ljava/lang/String;)V"));
			assertTrue(List.of("NOT_FOUND", "RESOLVED").contains(lambda.path("outcome").asText()));
			if (lambda.path("outcome").asText().equals("RESOLVED"))
				assertFalse(lambda.path("methodRangeAvailable").asBoolean());
		} finally { runtime.close(); }
	}

	@Test
	void malformedRequestsStrictCoverageAndBusyAdmissionAreTyped() throws Exception {
		Path jar = SymbolFixtureSupport.compileFixture(dir);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			String cls = ref("CLASS", "Lprobe/SymbolFixture;", null, null);
			assertError(post(server, "{"), 400, "INVALID_REQUEST");
			assertError(post(server, "{\"ref\":" + cls + ",\"unknown\":true}"), 400, "INVALID_REQUEST");
			assertError(post(server, "{\"ref\":" + cls + ",\"representation\":\"SMALI\"}"), 422, "UNSUPPORTED_CAPABILITY");
			assertError(post(server, "{\"ref\":" + cls + ",\"expectedSessionId\":\"bad\"}"), 400, "INVALID_REQUEST");
			assertError(post(server, "{\"ref\":{\"kind\":\"METHOD\",\"originalClassDescriptor\":\"Lprobe/SymbolFixture;\","
					+ "\"originalName\":\"mix\",\"originalDescriptor\":\"(V)V\"}}"), 400, "INVALID_REQUEST");
			assertError(post(server, "{\"ref\":{\"kind\":\"CLASS\",\"originalClassDescriptor\":\"Lprobe/SymbolFixture;\","
					+ "\"extra\":true}}"), 400, "INVALID_REQUEST");
			String session = runtime.projectSnapshot().revisions().sessionId();
			assertError(post(server, "{\"ref\":" + cls + ",\"expectedSessionId\":\"" + session
					+ "\",\"expectedLogicalRevision\":5}"), 409, "STALE_REVISION");
			JsonNode noAnnotations = body(post(server, "{\"ref\":" + cls + ",\"includeAnnotations\":false}"));
			assertEquals(0, noAnnotations.path("annotations").size());
			assertEquals("UNKNOWN", noAnnotations.path("capabilities").path("declarationPositions").asText());
			assertEquals("NOT_FOUND", success(server, ref("CLASS", "Lprobe/Missing;", null, null)).path("outcome").asText());
			assertError(post(server, "{\"ref\":" + cls + ",\"includeRawDebugLines\":true,\"strict\":true}"),
					409, "INCOMPLETE_ANALYSIS");
			assertError(post(server, "{\"ref\":" + cls + ",\"strict\":true}"),
					409, "INCOMPLETE_ANALYSIS");
			assertEquals("RESOLVED", body(post(server, "{\"ref\":" + cls
					+ ",\"includeAnnotations\":false,\"strict\":true}")).path("outcome").asText());
			assertError(post(server, "{\"ref\":" + ref("FIELD", "Lprobe/SymbolFixture;", "count", "I") + "}"),
					400, "INVALID_REQUEST");
			assertError(post(server, "{\"ref\":" + cls + ",\"padding\":\"" + "x".repeat(65_536) + "\"}"),
					400, "INVALID_REQUEST");
			var wrongType = HTTP.send(HttpRequest.newBuilder(uri(server)).header("Content-Type", "text/plain")
					.POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
			assertError(wrongType, 415, "INVALID_REQUEST");
			CountDownLatch entered = new CountDownLatch(1);
			CountDownLatch release = new CountDownLatch(1);
			CompletableFuture<Void> held = CompletableFuture.runAsync(() -> runtime.withPrimarySymbolRead("source-hold", context -> {
				var sourceClass = JadxSymbolAdapter.visibleClass(context.decompiler(), "Lprobe/SymbolFixture;", 0);
				JadxSourceAdapter.extract(context.decompiler(), sourceClass,
						SymbolRef.classRef("Lprobe/SymbolFixture;"), true, context.revisions().sessionId(),
						context.revisions().logicalRevision(), context.publicationEpoch(), context.settings().fingerprint());
				entered.countDown();
				try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
				return null;
			}));
			try {
				assertTrue(entered.await(5, TimeUnit.SECONDS));
				assertError(post(server, "{\"ref\":" + cls + "}"), 409, "PROJECT_BUSY");
				assertError(post(server, "{\"ref\":" + cls + ",\"decompilationMode\":\"SIMPLE\"}"),
						409, "PROJECT_BUSY");
				assertThrows(ProjectBusyException.class, () -> runtime.saveProject(null, session, 0L));
				assertThrows(ProjectBusyException.class, () -> runtime.reloadProject(false, session, 0));
				HttpResponse<String> shutdown = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
						+ server.localPort() + "/api/v1/shutdown")).header("Content-Type", "application/json")
						.POST(HttpRequest.BodyPublishers.ofString("{\"policy\":\"discard\"}"))
						.build(), HttpResponse.BodyHandlers.ofString());
				assertError(shutdown, 409, "PROJECT_BUSY");
			} finally { release.countDown(); held.get(10, TimeUnit.SECONDS); }
		} finally { runtime.close(); }
	}

	@Test
	void saveReloadRestartAndCollapsedDuplicateNeverReuseSourceIdentityOrInventOrigin() throws Exception {
		Path jar = SymbolFixtureSupport.compileFixture(dir);
		String classRef = ref("CLASS", "Lprobe/SymbolFixture;", null, null);
		String beforeId;
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			beforeId = success(server, classRef).path("sourceSnapshotId").asText();
			String session = runtime.projectSnapshot().revisions().sessionId();
			Path saved = dir.resolve("source.jar.jadx");
			runtime.saveProject(saved, session, 0L);
			assertTrue(Files.exists(saved));
			String afterSave = success(server, classRef).path("sourceSnapshotId").asText();
			assertNotEquals(beforeId, afterSave);
			assertError(post(server, "{\"ref\":" + classRef + ",\"expectedSourceSnapshotId\":\""
					+ beforeId + "\"}"), 409, "STALE_REVISION");
			runtime.reloadProject(false, session, 0);
			assertNotEquals(afterSave, success(server, classRef).path("sourceSnapshotId").asText());
		} finally { runtime.close(); }
		ProjectRuntime restarted = new ProjectRuntime(null, List.of(jar), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, restarted)) {
			server.start(); restarted.initializeAsync(null).get(20, TimeUnit.SECONDS);
			assertNotEquals(beforeId, success(server, classRef).path("sourceSnapshotId").asText());
		} finally { restarted.close(); }
		Path one = SymbolFixtureSupport.duplicateJar(dir, "one", 1);
		Path two = SymbolFixtureSupport.duplicateJar(dir, "two", 2);
		ProjectRuntime duplicate = new ProjectRuntime(null, List.of(one, two), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, duplicate)) {
			server.start(); duplicate.initializeAsync(null).get(20, TimeUnit.SECONDS);
			JsonNode result = success(server, ref("CLASS", "Lduplicate/Clash;", null, null));
			assertEquals("RESOLVED", result.path("outcome").asText());
			assertTrue(result.path("queriedRef").path("inputIdentity").isNull());
			assertEquals("PROVENANCE_UNAVAILABLE", success(server,
					"{\"kind\":\"CLASS\",\"originalClassDescriptor\":\"Lduplicate/Clash;\",\"inputIdentity\":\"one\"}")
					.path("outcome").asText());
		} finally { duplicate.close(); }
	}

	private static boolean hasAnnotation(JsonNode response, String kind, String targetKind,
			String originalName, String displayedToken) {
		String source = response.path("source").asText();
		for (JsonNode annotation : response.path("annotations")) {
			if (!kind.equals(annotation.path("kind").asText())
					|| !targetKind.equals(annotation.path("targetRef").path("kind").asText())
					|| !originalName.equals(annotation.path("targetRef").path("originalName").asText())) continue;
			if (source.startsWith(displayedToken, annotation.path("position").path("offsetUtf16").asInt())) return true;
		}
		return false;
	}

	private static String ref(String kind, String descriptor, String name, String memberDescriptor) {
		return "{\"kind\":\"" + kind + "\",\"originalClassDescriptor\":\"" + descriptor + "\""
				+ (name == null ? "" : ",\"originalName\":\"" + name + "\",\"originalDescriptor\":\""
						+ memberDescriptor + "\"") + "}";
	}
	private static JsonNode success(HttpApiServer server, String ref) throws Exception {
		HttpResponse<String> result = post(server, "{\"ref\":" + ref + "}");
		assertEquals(200, result.statusCode(), result.body());
		return body(result);
	}
	private static void assertError(HttpResponse<String> response, int status, String code) throws Exception {
		assertEquals(status, response.statusCode(), response.body());
		assertEquals(code, body(response).path("error").path("code").asText());
	}
	private static HttpResponse<String> post(HttpApiServer server, String json) throws Exception {
		return HTTP.send(HttpRequest.newBuilder(uri(server)).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString());
	}
	private static URI uri(HttpApiServer server) { return URI.create("http://127.0.0.1:" + server.localPort() + "/api/v1/decompile"); }
	private static JsonNode body(HttpResponse<String> response) throws Exception { return JSON.readTree(response.body()); }
}
