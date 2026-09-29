package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.libjadx.core.edits.EditDtos;
import dev.libjadx.core.search.SearchDtos.Page;
import dev.libjadx.core.search.SearchQuery;
import dev.libjadx.core.source.DecompileRequest;
import dev.libjadx.core.symbols.SymbolCatalog;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.http.HttpApiServer;
import dev.libjadx.project.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Real-Jadx regressions for accepted bytes, rather than a new candidate's self-consistency. */
class ReplacementExternalChangeTest {
	@TempDir Path dir;
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final SymbolRef OWNER = SymbolRef.classRef("Lprobe/SymbolFixture;");

	@ParameterizedTest @ValueSource(strings={"INPUT", "SECOND_INPUT", "MAPPING", "DELETED_INPUT", "DELETED_MAPPING"})
	void externalChangeRejectsCleanAndDirtyEditsWithoutImplicitReload(String changed) throws Exception {
		Fixture fixture = fixture();
		AtomicInteger creates = new AtomicInteger(), closes = new AtomicInteger();
		try (var runtime = runtime(fixture, creates, closes);
				var server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start();
			runtime.initializeAsync(NativeProjectDocument.open(fixture.project())).get(20, TimeUnit.SECONDS);
			for (boolean dirty : List.of(false, true)) {
				fixture.restore();
				if (dirty) new EditBatchService(runtime).apply(rename("PriorAlias"));
				var before = runtime.projectSnapshot();
				var pending = runtime.pendingEdits();
				var identity = runtime.searchIdentity();
				var old = runtime.decompiler();
				var semantic = SafeReplayStrategyTest.semantic(old);
				var catalogs = new SymbolCatalogProvider(SymbolCatalog.newCursorKey());
				var search = new SearchService(runtime, catalogs, SymbolCatalog.newCursorKey());
				var sources = new DecompiledSourceService(runtime, catalogs, search);
				var sourceRequest = new DecompileRequest(OWNER, null, true, false, false, null, null, null);
				var source = sources.decompile(sourceRequest);
				Page page = (Page) search.query(query(null));
				assertNotNull(page.nextCursor());
				Page continuation = (Page) search.query(query(page.nextCursor()));
				var projectHash = FileFingerprint.of(fixture.project());
				int constructions = creates.get(), cleanups = closes.get();
				fixture.change(changed);
				String body = """
						{"items":[{"kind":"RENAME","target":{"kind":"CLASS",
						"originalClassDescriptor":"Lprobe/SymbolFixture;"},"newName":"AttemptAlias"}]}
						""";
				var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
						URI.create("http://127.0.0.1:" + server.localPort() + "/api/v1/edits/batch"))
						.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
						HttpResponse.BodyHandlers.ofString());
				assertEquals(409, response.statusCode(), response.body());
				assertEquals("EXTERNAL_MODIFICATION_CONFLICT", JSON.readTree(response.body()).path("error").path("code").asText());
				Path captures = Files.createDirectories(Path.of("build/edit-contract-responses"));
				Files.writeString(captures.resolve("409-external-" + changed.toLowerCase() + "-" + dirty + ".json"),
						JSON.writeValueAsString(java.util.Map.of("status", response.statusCode(), "schema", "ErrorEnvelope",
								"body", JSON.readTree(response.body()))));
				assertSame(old, runtime.decompiler());
				assertEquals(before, runtime.projectSnapshot());
				assertEquals(pending, runtime.pendingEdits());
				assertEquals(identity, runtime.searchIdentity());
				assertEquals(semantic, SafeReplayStrategyTest.semantic(old));
				assertEquals(source, sources.decompile(sourceRequest));
				assertEquals(continuation, search.query(query(page.nextCursor())));
				assertEquals(projectHash, FileFingerprint.of(fixture.project()));
				assertEquals(constructions, creates.get());
				assertEquals(cleanups, closes.get());
				assertEquals("READY", runtime.status().state());
				// A no-op neither reloads nor requires acceptance of external bytes.
				assertEquals("NO_CHANGE", new EditBatchService(runtime).apply(rename(dirty ? "PriorAlias" : "SymbolFixture")).outcome());
				assertEquals(constructions, creates.get());
			}
		}
	}

	@ParameterizedTest @CsvSource({"SECOND_INPUT,CENSUS", "SECOND_INPUT,LOAD", "SECOND_INPUT,BIND",
			"MAPPING,CENSUS", "MAPPING,LOAD", "MAPPING,BIND", "SECOND_INPUT,BEFORE_CONSTRUCTION",
			"MAPPING,BEFORE_CONSTRUCTION", "SECOND_INPUT,BEFORE_COMMIT", "MAPPING,REPOSITORY_COMMIT"})
	void changesDuringCandidateConstructionOrLoadCloseCandidateAndPublishNothing(String changed, String checkpoint) throws Exception {
		Fixture fixture = fixture();
		AtomicInteger creates = new AtomicInteger(), closes = new AtomicInteger();
		boolean[] armed = {false};
		try (var runtime = new ProjectRuntime(fixture.project(), fixture.inputs(), args -> {
			creates.incrementAndGet();
			var real = new ProjectRuntime.JadxProjectEngine(args, stage -> {
				if (armed[0] && stage.name().equals(checkpoint)) fixture.change(changed);
			});
			return counted(real, closes);
		})) {
			runtime.initializeAsync(NativeProjectDocument.open(fixture.project())).get(20, TimeUnit.SECONDS);
			new EditBatchService(runtime).apply(rename("PriorAlias"));
			var before = runtime.projectSnapshot(); var pending = runtime.pendingEdits(); var identity = runtime.searchIdentity();
			var old = runtime.decompiler(); var source = SafeReplayStrategyTest.semantic(old);
			int constructions = creates.get(), cleanups = closes.get();
			armed[0] = true;
			runtime.replacementHook(stage -> { if (stage.name().equals(checkpoint)) fixture.change(changed); });
			assertThrows(ProjectRuntime.NativeEditConflictException.class, () -> new EditBatchService(runtime).apply(rename("AttemptAlias")));
			assertSame(old, runtime.decompiler()); assertEquals(before, runtime.projectSnapshot()); assertEquals(pending, runtime.pendingEdits());
			assertEquals(identity, runtime.searchIdentity()); assertEquals(source, SafeReplayStrategyTest.semantic(old));
			assertEquals(constructions + 1, creates.get()); assertEquals(cleanups + 1, closes.get());
			assertEquals("READY", runtime.status().state());
		}
	}

	@ParameterizedTest @ValueSource(strings={"SECOND_INPUT", "MAPPING"})
	void explicitReloadAcceptsChangedBytesAndEstablishesTheNextEditBaseline(String changed) throws Exception {
		Fixture fixture = fixture();
		try (var runtime = runtime(fixture, new AtomicInteger(), new AtomicInteger())) {
			runtime.initializeAsync(NativeProjectDocument.open(fixture.project())).get(20, TimeUnit.SECONDS);
			new EditBatchService(runtime).apply(rename("UnsavedAlias"));
			var before = runtime.projectSnapshot(); fixture.change(changed);
			assertThrows(ProjectRuntime.NativeEditConflictException.class, () -> new EditBatchService(runtime).apply(rename("AttemptAlias")));
			assertThrows(NativeProjectRepository.UnsavedChangesException.class, () -> runtime.reloadProject(false, before.revisions().sessionId(), 1));
			runtime.reloadProject(true, before.revisions().sessionId(), 1);
			assertFalse(runtime.projectSnapshot().dirty());
			String adopted = runtime.withPrimaryClassRead("secondary", jadx -> jadx.searchJavaClassByOrigFullName("probe.ExternalBytes").getCode());
			assertTrue(adopted.contains(changed.equals("MAPPING") ? "NewMappingAlias" : "NEW_INPUT_BYTES"), adopted);
			assertEquals("APPLIED", new EditBatchService(runtime).apply(rename("AfterReloadAlias")).outcome());
			fixture.restore();
			assertThrows(ProjectRuntime.NativeEditConflictException.class, () -> new EditBatchService(runtime).apply(rename("AnotherAlias")));
		}
	}

	@Test void rawInputAndSaveCannotAdvanceTheAcceptedAnalysisBaseline() throws Exception {
		Fixture fixture = fixture();
		try (var runtime = new ProjectRuntime(null, fixture.inputs(), List.of(dir))) {
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			runtime.saveProject(dir.resolve("raw.jadx"), null);
			var before = runtime.projectSnapshot(); var old = runtime.decompiler(); fixture.change("SECOND_INPUT");
			assertThrows(ProjectRuntime.NativeEditConflictException.class, () -> new EditBatchService(runtime).apply(rename("AttemptAlias")));
			assertThrows(NativeProjectRepository.ExternalModificationException.class, () -> runtime.saveProject(null, null));
			assertEquals(before, runtime.projectSnapshot()); assertSame(old, runtime.decompiler());
		}
	}

	@Test void unsavedMappingAttachmentUsesItsOwnAcceptedBaseline() throws Exception {
		Fixture fixture = fixture();
		try (var runtime = runtime(fixture, new AtomicInteger(), new AtomicInteger())) {
			runtime.initializeAsync(NativeProjectDocument.open(fixture.project())).get(20, TimeUnit.SECONDS);
			Path attached = Files.writeString(dir.resolve("attached.tiny"), mapping("AttachedAlias"));
			var before = runtime.projectSnapshot();
			runtime.updateMappingsPath(attached, before.revisions().sessionId(), 0);
			assertEquals("APPLIED", new EditBatchService(runtime).apply(rename("PriorAlias")).outcome());
			var accepted = runtime.projectSnapshot(); var old = runtime.decompiler();
			Files.writeString(attached, mapping("ChangedAttachment"));
			assertThrows(ProjectRuntime.NativeEditConflictException.class, () -> new EditBatchService(runtime).apply(rename("AttemptAlias")));
			assertEquals(accepted, runtime.projectSnapshot()); assertSame(old, runtime.decompiler());
		}
	}

	@Test void startupDoesNotPublishAnEngineWhoseFilesChangedDuringLoading() throws Exception {
		Fixture fixture = fixture();
		AtomicInteger closes = new AtomicInteger();
		try (var runtime = new ProjectRuntime(fixture.project(), fixture.inputs(),
				args -> counted(new ProjectRuntime.JadxProjectEngine(args), closes), () -> fixture.change("SECOND_INPUT"))) {
			runtime.initializeAsync(NativeProjectDocument.open(fixture.project())).get(20, TimeUnit.SECONDS);
			assertEquals("FAILED", runtime.status().state());
			assertEquals("PROJECT_LOAD_FAILED", runtime.status().error().code());
			assertEquals(1, closes.get());
			assertThrows(ProjectRuntime.ProjectNotReadyException.class, runtime::projectSnapshot);
		}
	}

	@ParameterizedTest @ValueSource(strings={"SECOND_INPUT", "MAPPING"})
	void explicitReloadAlsoRejectsFilesChangedDuringItsCandidateLoad(String changed) throws Exception {
		Fixture fixture = fixture();
		AtomicInteger closes = new AtomicInteger();
		try (var runtime = runtime(fixture, new AtomicInteger(), closes)) {
			runtime.initializeAsync(NativeProjectDocument.open(fixture.project())).get(20, TimeUnit.SECONDS);
			var before = runtime.projectSnapshot(); var pending = runtime.pendingEdits(); var old = runtime.decompiler();
			runtime.replacementHook(stage -> { if (stage == ProjectRuntime.ReplacementStage.LOADED) fixture.change(changed); });
			assertThrows(NativeProjectRepository.ExternalModificationException.class,
					() -> runtime.reloadProject(false, before.revisions().sessionId(), 0));
			assertEquals(before, runtime.projectSnapshot()); assertEquals(pending, runtime.pendingEdits()); assertSame(old, runtime.decompiler());
			assertEquals(1, closes.get());
			runtime.replacementHook(ignored -> { });
			runtime.reloadProject(false, before.revisions().sessionId(), 0);
			assertEquals("APPLIED", new EditBatchService(runtime).apply(rename("AfterReloadAlias")).outcome());
		}
	}

	private ProjectRuntime runtime(Fixture fixture, AtomicInteger creates, AtomicInteger closes) {
		return new ProjectRuntime(fixture.project(), fixture.inputs(), args -> {
			creates.incrementAndGet(); return counted(new ProjectRuntime.JadxProjectEngine(args), closes);
		});
	}
	private static ProjectRuntime.ProjectEngine counted(ProjectRuntime.JadxProjectEngine real, AtomicInteger closes) {
		return new ProjectRuntime.ProjectEngine() {
			public void load() { real.load(); }
			public jadx.api.JadxDecompiler decompiler() { return real.decompiler(); }
			public dev.libjadx.core.hierarchy.RelatedHierarchyVerifier hierarchyVerifier() { return real.hierarchyVerifier(); }
			public void close() { closes.incrementAndGet(); real.close(); }
		};
	}
	private Fixture fixture() throws Exception {
		Path input = SymbolFixtureSupport.compileFixture(dir);
		Path oldRoot = Files.createDirectories(dir.resolve("old")), newRoot = Files.createDirectories(dir.resolve("new"));
		Path oldSource = Files.writeString(oldRoot.resolve("ExternalBytes.java"), javaSource("OLD_INPUT_BYTES"));
		Path newSource = Files.writeString(newRoot.resolve("ExternalBytes.java"), javaSource("NEW_INPUT_BYTES"));
		Path secondary = Files.copy(SymbolFixtureSupport.compile(oldRoot, oldSource, "bytes.jar"), dir.resolve("secondary.jar"));
		byte[] changedBytes = Files.readAllBytes(SymbolFixtureSupport.compile(newRoot, newSource, "bytes.jar"));
		Path mapping = Files.writeString(dir.resolve("attached-original.tiny"), mapping("OldMappingAlias"));
		Path project = dir.resolve("project.jadx");
		var document = NativeProjectDocument.newFromInputs(project, List.of(input, secondary)).withMappingsPath(mapping);
		document.save();
		return new Fixture(project, input, secondary, mapping, Files.readAllBytes(input), Files.readAllBytes(secondary), changedBytes);
	}
	private record Fixture(Path project, Path input, Path secondary, Path mapping, byte[] inputBytes, byte[] oldBytes, byte[] newBytes) {
		List<Path> inputs() { return List.of(input, secondary); }
		void restore() throws Exception {
			Files.write(input, inputBytes); Files.write(secondary, oldBytes); Files.writeString(mapping, ReplacementExternalChangeTest.mapping("OldMappingAlias"));
		}
		void change(String changed) {
			try {
				switch (changed) {
					case "INPUT" -> Files.write(input, newBytes);
					case "SECOND_INPUT" -> Files.write(secondary, newBytes);
					case "MAPPING" -> Files.writeString(mapping, ReplacementExternalChangeTest.mapping("NewMappingAlias"));
					case "DELETED_INPUT" -> Files.delete(secondary);
					case "DELETED_MAPPING" -> Files.delete(mapping);
					default -> throw new IllegalArgumentException(changed);
				}
			} catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
		}
	}
	private static String javaSource(String value) { return "package probe; public class ExternalBytes { public String value() { return \"" + value + "\"; } }"; }
	private static String mapping(String alias) { return "tiny\t2\t0\toriginal\tmapped\nc\tprobe/ExternalBytes\tprobe/" + alias + "\n"; }
	private static EditDtos.Request rename(String alias) { return new EditDtos.Request(null, null, List.of(
			new EditDtos.Operation(EditDtos.Kind.RENAME, OWNER, alias, null, null))); }
	private static SearchQuery query(String cursor) { return new SearchQuery("probe", List.of(SearchQuery.Domain.CLASS_NAME),
			SearchQuery.MatchMode.CONTAINS, true, 1, cursor, false, false, null, null); }
}
