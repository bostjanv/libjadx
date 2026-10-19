package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
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
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.libjadx.http.HttpApiServer;
import dev.libjadx.project.NativeProjectDocument;
import dev.libjadx.jadxadapter.JadxSymbolAdapter;
import dev.libjadx.jadxadapter.JadxSourceAdapter;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.core.edits.EditDtos;
import dev.libjadx.probes.HierarchyFixture;
import dev.libjadx.scheduler.ProjectBusyException;
import jadx.api.JadxDecompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DecompileEndpointsTest {
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final HttpClient HTTP = HttpClient.newHttpClient();
	@TempDir Path dir;
	@TempDir Path outsideRoot;

	@ParameterizedTest
	@ValueSource(strings = {"INPUT", "SECOND_INPUT", "MAPPING", "DELETED_INPUT", "DELETED_MAPPING", "INPUT_SYMLINK", "MAPPING_SYMLINK"})
	void temporaryModeRejectsChangedAnalysisFilesAndRetainsPendingEdits(String change) throws Exception {
		Path fixture = Path.of("tests/fixtures/native-project").toAbsolutePath();
		for (String file : List.of("sample.jar.jadx", "sample.jar", "second.jar", "sample.tiny"))
			Files.copy(fixture.resolve(file), dir.resolve(file));
		Path path = dir.resolve("sample.jar.jadx");
		byte[] nativeBefore = Files.readAllBytes(path);
		NativeProjectDocument document = NativeProjectDocument.open(path);
		try (var runtime = new ProjectRuntime(path, document.getInputFiles(), List.of(dir));
				var server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); runtime.initializeAsync(document).get(20, TimeUnit.SECONDS);
			SymbolRef sample = SymbolRef.classRef("Lprobe/Sample;");
			new EditBatchService(runtime).apply(new EditDtos.Request(null, null, List.of(
					new EditDtos.Operation(EditDtos.Kind.RENAME, sample, "PendingAlias", null, null))));
			String sourceRef = ref("CLASS", "Lprobe/Sample;", null, null);
			JsonNode primary = success(server, sourceRef);
			var before = runtime.projectSnapshot();
			var pending = runtime.pendingEdits();
			var identity = runtime.searchIdentity();
			Path changed = dir.resolve(change.contains("MAPPING") ? "sample.tiny"
					: change.equals("SECOND_INPUT") ? "second.jar" : "sample.jar");
			byte[] accepted = Files.readAllBytes(changed);
			boolean symlink = change.endsWith("SYMLINK");
			if (symlink) {
				Path outside = Files.copy(changed, outsideRoot.resolve(changed.getFileName()));
				Files.delete(changed); Files.createSymbolicLink(changed, outside);
			} else if (change.startsWith("DELETED")) Files.delete(changed);
			else Files.writeString(changed, "\n", java.nio.file.StandardOpenOption.APPEND);
			try {
				assertError(post(server, "{\"ref\":" + sourceRef + ",\"decompilationMode\":\"SIMPLE\"}"),
						symlink ? 403 : 409, symlink ? "INPUT_SECURITY_REJECTION" : "EXTERNAL_MODIFICATION_CONFLICT");
				assertEquals(before, runtime.projectSnapshot());
				assertEquals(pending, runtime.pendingEdits());
				assertEquals(identity, runtime.searchIdentity());
				assertEquals(primary, success(server, sourceRef));
				assertArrayEquals(nativeBefore, Files.readAllBytes(path));
			} finally { Files.deleteIfExists(changed); Files.write(changed, accepted); }
			JsonNode restored = body(post(server, "{\"ref\":" + sourceRef + ",\"decompilationMode\":\"SIMPLE\"}"));
			assertEquals("RESOLVED", restored.path("outcome").asText(), restored.toPrettyString());
			assertTrue(restored.path("source").asText().contains("PendingAlias"));
			assertEquals(before, runtime.projectSnapshot());
			// Temporary analysis uses in-memory native edits, not externally edited project JSON.
			Files.writeString(path, "\n", java.nio.file.StandardOpenOption.APPEND);
			assertEquals(restored, body(post(server, "{\"ref\":" + sourceRef + ",\"decompilationMode\":\"SIMPLE\"}")));
		}
	}

	@Test
	void realJavaFailuresRemainPartialAfterCodegenWithoutPoisoningHealthyOwners() throws Exception {
		String invalid = Files.readString(Path.of("tests/fixtures/smali/Disassembly.smali"));
		Path dex = HierarchyFixture.dex(dir, "invalid.dex", invalid);
		Path nested = HierarchyFixture.dex(dir, "nested.dex",
				invalid.replace("Lprobe/Disassembly;", "Lprobe/Holder$Broken;"));
		Path jar = HierarchyFixture.javaJar(dir, "healthy.jar", java.util.Map.of(
				"probe/Holder.java", "package probe; public class Holder {}",
				"probe/Healthy.java", """
						package probe;
						public class Healthy {
						    public static String message() { return "Method not decompiled"; }
						    public static int answer() { return 17; }
						}
						"""));
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(dex, nested, jar), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			for (String mode : List.of("AUTO", "RESTRUCTURE", "SIMPLE")) {
				for (String target : List.of(ref("CLASS", "Lprobe/Disassembly;", null, null),
						ref("METHOD", "Lprobe/Disassembly;", "answer", "()I"),
						ref("CLASS", "Lprobe/Holder;", null, null))) {
					String request = "{\"ref\":" + target + ",\"includeAnnotations\":false,\"decompilationMode\":\"" + mode + "\"";
					JsonNode result = body(post(server, request + "}"));
					assertEquals("PARTIAL", result.path("status").asText(), result.toPrettyString());
					assertTrue(result.path("source").asText().contains("answer"));
					assertFalse(result.path("sourceSnapshotId").isNull());
					assertTrue(result.path("classErrorCount").isNull());
					assertEquals("UNKNOWN", result.path("capabilities").path("classErrors").asText());
					assertTrue(result.path("diagnostics").toString().contains("Jadx recorded"), result.toPrettyString());
					assertError(post(server, request + ",\"strict\":true}"), 409, "INCOMPLETE_ANALYSIS");
					assertEquals("PARTIAL", body(post(server, request + "}")).path("status").asText());
				}
				String healthy = "{\"ref\":" + ref("CLASS", "Lprobe/Healthy;", null, null)
						+ ",\"includeAnnotations\":false,\"strict\":true,\"decompilationMode\":\"" + mode + "\"}";
				JsonNode result = body(post(server, healthy));
				assertEquals("COMPLETE", result.path("status").asText(), result.toPrettyString());
				assertTrue(result.path("source").asText().contains("Method not decompiled"));
				assertTrue(result.path("classErrorCount").isNull());
			}
			assertFalse(runtime.projectSnapshot().dirty());
			assertEquals(0, runtime.projectSnapshot().revisions().logicalRevision());
		} finally { runtime.close(); }
	}

	@Test
	void smaliIsSingleClassDexTextWithSeparateSnapshotsAndNoJavaSearchIngestion() throws Exception {
		Path dex = HierarchyFixture.dex(dir, "sample.dex",
				Files.readString(Path.of("tests/fixtures/smali/Disassembly.smali")));
		Path inner = HierarchyFixture.dex(dir, "inner.dex", """
				.class public Lprobe/Disassembly$Inner;
				.super Ljava/lang/Object;
				.annotation system Ldalvik/annotation/EnclosingClass;
				    value = Lprobe/Disassembly;
				.end annotation
				.annotation system Ldalvik/annotation/InnerClass;
				    accessFlags = 0x9
				    name = "Inner"
				.end annotation
				.method public static other()I
				.registers 1
				const/16 v0, 0x11
				return v0
				.end method
				""");
		byte[] inputBefore = Files.readAllBytes(dex);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(dex, inner), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			String cls = ref("CLASS", "Lprobe/Disassembly;", null, null);
			String request = "{\"ref\":" + cls + ",\"representation\":\"SMALI\"";
			JsonNode smali = body(post(server, request + "}"));
			OpenApiExampleValidator.assertValid(new ObjectMapper(new YAMLFactory()).readTree(
					Path.of("openapi/openapi.yaml").toFile()), "DecompileResult", smali);
			assertEquals("RESOLVED", smali.path("outcome").asText());
			assertEquals("COMPLETE", smali.path("status").asText());
			assertEquals("SMALI", smali.path("representation").asText());
			assertEquals("Lprobe/Disassembly;", smali.path("sourceOwnerRef").path("originalClassDescriptor").asText());
			String text = smali.path("source").asText();
			assertTrue(text.contains("const/16 v0, 0x2a"));
			assertFalse(text.contains("other()I"));
			assertEquals(0, smali.path("annotations").size());
			assertEquals(0, smali.path("variables").size());
			assertTrue(smali.path("methodRangeAvailable").isNull());
			assertEquals("UNAVAILABLE", smali.path("capabilities").path("originalBytecodeOffsets").asText());
			assertError(post(server, request + ",\"strict\":true}"), 409, "INCOMPLETE_ANALYSIS");
			assertEquals(text, body(post(server, request + ",\"strict\":true,\"includeAnnotations\":false}")).path("source").asText());
			assertError(post(server, request + ",\"strict\":true,\"includeAnnotations\":false,\"includeRawDebugLines\":true}"),
					409, "INCOMPLETE_ANALYSIS");
			JsonNode child = body(post(server, "{\"ref\":" + ref("CLASS", "Lprobe/Disassembly$Inner;", null, null)
					+ ",\"representation\":\"SMALI\"}"));
			assertEquals("Lprobe/Disassembly$Inner;", child.path("sourceOwnerRef").path("originalClassDescriptor").asText());
			assertTrue(child.path("source").asText().contains("const/16 v0, 0x11"));
			assertFalse(child.path("source").asText().contains("answer()I"));
			HttpResponse<String> search = HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"
					+ server.localPort() + "/api/v1/search")).header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"const/16\",\"domains\":[\"SOURCE_TEXT\"]}"))
					.build(), HttpResponse.BodyHandlers.ofString());
			assertEquals(200, search.statusCode(), search.body());
			assertEquals(0, body(search).path("hits").size());
			JsonNode java = success(server, cls);
			assertTrue(java.path("source").asText().contains("Method not decompiled"), java.toPrettyString());
			assertNotEquals(java.path("sourceSnapshotId"), smali.path("sourceSnapshotId"));
			assertEquals(smali.path("sourceSnapshotId"), body(post(server, request + "}")).path("sourceSnapshotId"));
			assertError(post(server, request + ",\"expectedSourceSnapshotId\":\"" + java.path("sourceSnapshotId").asText() + "\"}"),
					409, "STALE_REVISION");
			assertError(post(server, "{\"ref\":" + cls + ",\"expectedSourceSnapshotId\":\""
					+ smali.path("sourceSnapshotId").asText() + "\"}"), 409, "STALE_REVISION");
			JsonNode temporary = body(post(server, request + ",\"decompilationMode\":\"SIMPLE\"}"));
			assertEquals(text, temporary.path("source").asText());
			assertNotEquals(smali.path("sourceSnapshotId"), temporary.path("sourceSnapshotId"));
			assertEquals(0, runtime.projectSnapshot().revisions().logicalRevision());
			assertFalse(runtime.projectSnapshot().dirty());
			new EditBatchService(runtime).apply(new EditDtos.Request(null, null, List.of(
					new EditDtos.Operation(EditDtos.Kind.RENAME, SymbolRef.classRef("Lprobe/Disassembly;"), "Alias", null, null))));
			JsonNode renamed = body(post(server, request + "}"));
			assertEquals(text, renamed.path("source").asText());
			assertNotEquals(smali.path("sourceSnapshotId"), renamed.path("sourceSnapshotId"));
			assertError(post(server, request + ",\"expectedSourceSnapshotId\":\"" + smali.path("sourceSnapshotId").asText() + "\"}"),
					409, "STALE_REVISION");
			assertArrayEquals(inputBefore, Files.readAllBytes(dex));
			try (var files = Files.list(dir)) { assertFalse(files.anyMatch(file -> file.toString().endsWith(".jadx"))); }
		} finally { runtime.close(); }
	}

	@Test
	void smaliRejectsMethodRequestsAndNeverRelabelsJvmDisassembly() throws Exception {
		Path jar = SymbolFixtureSupport.compileFixture(dir);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			String cls = ref("CLASS", "Lprobe/SymbolFixture;", null, null);
			String request = "{\"ref\":" + cls + ",\"representation\":\"SMALI\"";
			JsonNode unavailable = body(post(server, request + "}"));
			assertEquals("RESOLVED", unavailable.path("outcome").asText());
			assertEquals("UNAVAILABLE", unavailable.path("status").asText());
			assertEquals("SMALI", unavailable.path("representation").asText());
			assertTrue(unavailable.path("source").isNull());
			assertTrue(unavailable.path("sourceSnapshotId").isNull());
			assertTrue(unavailable.path("diagnostics").toString().contains("JVM/Raung"));
			assertError(post(server, request + ",\"includeAnnotations\":false,\"strict\":true}"), 409, "INCOMPLETE_ANALYSIS");
			assertError(post(server, "{\"ref\":" + ref("METHOD", "Lprobe/SymbolFixture;", "mix", "(I)I")
					+ ",\"representation\":\"SMALI\"}"), 422, "UNSUPPORTED_CAPABILITY");
			JsonNode missing = body(post(server, "{\"ref\":" + ref("CLASS", "Lprobe/Absent;", null, null)
					+ ",\"representation\":\"SMALI\"}"));
			assertEquals("NOT_FOUND", missing.path("outcome").asText());
			assertEquals("SMALI", missing.path("representation").asText());
		} finally { runtime.close(); }
	}

	@Test
	void failedDexDisassemblyNeverReturnsTheUpstreamExceptionTrace() throws Exception {
		Path dex = HierarchyFixture.dex(dir, "sample.dex",
				Files.readString(Path.of("tests/fixtures/smali/Disassembly.smali")));
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(dex), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			// Fault injection after a real load: corrupt only the in-memory DEX class
			// type index so the pinned disassembler returns its swallowed exception.
			runtime.withPrimarySymbolRead("corrupt-disassembly", context -> {
				var cls = JadxSymbolAdapter.visibleClass(context.decompiler(), "Lprobe/Disassembly;", 0);
				var data = (jadx.plugins.input.dex.sections.DexClassData) cls.getClassNode().getClsData();
				try {
					var field = data.getClass().getDeclaredField("in");
					field.setAccessible(true);
					var reader = (jadx.plugins.input.dex.sections.SectionReader) field.get(data);
					reader.getDexReader().getBuf().putInt(data.getClassDefOffset(), Integer.MAX_VALUE);
				} catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
				assertTrue(data.getDisassembledCode().contains("Error generating smali code: "));
				return null;
			});
			HttpResponse<String> response = post(server, "{\"ref\":" + ref("CLASS", "Lprobe/Disassembly;", null, null)
					+ ",\"representation\":\"SMALI\"}");
			assertEquals(200, response.statusCode(), response.body());
			assertEquals("UNAVAILABLE", body(response).path("status").asText());
			assertTrue(body(response).path("source").isNull());
			assertTrue(body(response).path("sourceSnapshotId").isNull());
			assertFalse(response.body().contains("Exception"));
			assertFalse(response.body().contains("\tat "));
		} finally { runtime.close(); }
	}

	@Test
	void anonymousClassFirstUsesEmittedParentButEmptySyntheticHasNoSource() throws Exception {
		Path jar = SymbolFixtureSupport.compileFixture(dir);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			JsonNode anonymous = success(server, ref("CLASS", "Lprobe/SymbolFixture$1;", null, null));
			assertEquals("RESOLVED", anonymous.path("outcome").asText());
			assertEquals("COMPLETE", anonymous.path("status").asText());
			assertEquals("Lprobe/SymbolFixture;",
					anonymous.path("sourceOwnerRef").path("originalClassDescriptor").asText());
			assertTrue(anonymous.path("source").asText().contains("new Runnable()"));
			assertTrue(anonymous.path("methodRange").isNull());
			JsonNode parent = success(server, ref("CLASS", "Lprobe/SymbolFixture;", null, null));
			assertEquals(parent.path("source").asText(), anonymous.path("source").asText());
			assertEquals(parent.path("sourceSnapshotId").asText(), anonymous.path("sourceSnapshotId").asText());
		} finally { runtime.close(); }

		Path emptyJar = SymbolFixtureSupport.syntheticEmptyJar(dir);
		ProjectRuntime empty = new ProjectRuntime(null, List.of(emptyJar), List.of(dir));
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, empty)) {
			server.start(); empty.initializeAsync(null).get(20, TimeUnit.SECONDS);
			JsonNode unavailable = success(server, ref("CLASS", "Lprobe/EmptySynthetic;", null, null));
			assertEquals("RESOLVED", unavailable.path("outcome").asText());
			assertEquals("UNAVAILABLE", unavailable.path("status").asText());
			assertTrue(unavailable.path("source").isNull());
			assertTrue(unavailable.path("sourceOwnerRef").isNull());
			assertEquals("UNAVAILABLE", unavailable.path("capabilities").path("source").asText());
		} finally { empty.close(); }
	}

	@Test
	void nativeSaveHoldingRepositoryMonitorRejectsDecompileImmediately() throws Exception {
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
				try {
					if (!releaseSave.await(10, TimeUnit.SECONDS)) throw new IOException("Timed out holding native save");
				} catch (InterruptedException failure) {
					Thread.currentThread().interrupt();
					throw new IOException(failure);
				}
				return repository.save(target, session, revision);
			}
		});
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			String session = runtime.projectSnapshot().revisions().sessionId();
			CompletableFuture<Void> save = CompletableFuture.runAsync(() -> {
				try { runtime.saveProject(dir.resolve("busy.jadx"), session, 0L); }
				catch (IOException failure) { throw new CompletionException(failure); }
			});
			try {
				assertTrue(enteredSave.await(5, TimeUnit.SECONDS));
				String cls = ref("CLASS", "Lprobe/SymbolFixture;", null, null);
				for (String request : List.of("{\"ref\":" + cls + "}",
						"{\"ref\":" + cls + ",\"decompilationMode\":\"SIMPLE\"}")) {
					HttpResponse<String> busy = HTTP.sendAsync(HttpRequest.newBuilder(uri(server))
							.header("Content-Type", "application/json")
							.POST(HttpRequest.BodyPublishers.ofString(request)).build(), HttpResponse.BodyHandlers.ofString())
							.get(2, TimeUnit.SECONDS);
					assertError(busy, 409, "PROJECT_BUSY");
				}
			} finally { releaseSave.countDown(); save.get(10, TimeUnit.SECONDS); }
		} finally { releaseSave.countDown(); runtime.close(); }
	}

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
			SymbolRef sample = SymbolRef.classRef("Lprobe/Sample;");
			assertEquals("APPLIED", new EditBatchService(runtime).apply(new EditDtos.Request(null, null, List.of(
					new EditDtos.Operation(EditDtos.Kind.RENAME, sample, "UnsavedSourceAlias", null, null),
					new EditDtos.Operation(EditDtos.Kind.SET_COMMENT, sample, null, "unsaved source comment", "LINE")))).outcome());
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
			assertError(post(server, "{\"ref\":" + cls + ",\"representation\":\"UNKNOWN\"}"), 422, "UNSUPPORTED_CAPABILITY");
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
				assertError(post(server, "{\"ref\":" + cls + ",\"representation\":\"SMALI\"}"), 409, "PROJECT_BUSY");
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
