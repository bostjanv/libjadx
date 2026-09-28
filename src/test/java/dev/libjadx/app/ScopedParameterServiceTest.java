package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import dev.libjadx.core.edits.EditDtos;
import dev.libjadx.core.source.*;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.jadxadapter.JadxSymbolAdapter;
import dev.libjadx.project.NativeProjectDocument;
import dev.libjadx.project.NativeProjectRepository;
import jadx.api.data.impl.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ScopedParameterServiceTest {
	@TempDir Path dir;
	static final SymbolRef OWNER = SymbolRef.classRef("Lprobe/Variables;");
	static SymbolRef method(String name, String descriptor) {
		return new SymbolRef(SymbolRef.Kind.METHOD, OWNER.originalClassDescriptor(), null, name, descriptor);
	}
	static final SymbolRef INSTANCE = method("instance", "(IJDLjava/lang/String;)I");

	static DecompileResult source(ProjectRuntime runtime) throws Exception { return source(runtime, null); }
	static DecompileResult source(ProjectRuntime runtime, String mode) throws Exception {
		var catalogs = new SymbolCatalogProvider(new byte[32]);
		return new DecompiledSourceService(runtime, catalogs, new SearchService(runtime, catalogs, new byte[32]))
				.decompile(new DecompileRequest(OWNER, mode, true, false, false, null, null, null));
	}
	static EditDtos.Operation rename(DecompileResult source, SymbolRef method, int index, String name) {
		return new EditDtos.Operation(EditDtos.Kind.RENAME_PARAMETER, method, name, null, null, index, source.sourceSnapshotId());
	}
	static EditDtos.Request request(DecompileResult source, EditDtos.Operation... items) {
		return new EditDtos.Request(source.sessionId(), source.logicalRevision(), List.of(items));
	}
	ProjectRuntime raw() throws Exception {
		Path jar = SymbolFixtureSupport.compileVariableFixture(dir);
		var runtime = new ProjectRuntime(null, List.of(jar), List.of(dir)); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
		return runtime;
	}
	@Test void metadataPositionalIdentityAndMixedBatchThenNoop() throws Exception {
		try (var runtime = raw()) {
			var source = source(runtime);
			var parameters = source.variables().stream().filter(v -> v.kind().equals("PARAMETER") && v.method().equals(INSTANCE)).toList();
			assertEquals(List.of(0, 1, 2, 3), parameters.stream().map(DecompileResult.Variable::parameterIndex).toList());
			assertTrue(parameters.stream().allMatch(v -> v.persistability().equals("SUPPORTED")));
			for (var variable : source.variables()) {
				assertEquals(variable.displayName(), source.source().substring(variable.range().startOffsetUtf16(), variable.range().endOffsetUtf16()));
				if (variable.kind().equals("LOCAL")) assertEquals("UNSUPPORTED", variable.persistability());
			}
			assertEquals(source.variables(), source(runtime).variables());
			var service = new EditBatchService(runtime, i -> assertColdUnrelated(runtime));
			var result = service.apply(request(source, new EditDtos.Operation(EditDtos.Kind.RENAME, INSTANCE, "editedInstance", null, null),
					rename(source, INSTANCE, 1, "wideCount"), rename(source, INSTANCE, 3, "field")));
			assertEquals("APPLIED", result.outcome()); assertEquals(1, result.logicalRevisionAfter());
			assertEquals(1, result.indexRevisionAfter()); assertFalse(result.saved()); assertTrue(result.dirty());
			var after = source(runtime);
			assertTrue(after.source().contains("editedInstance(int i, long wideCount, double d, String field)"), after.source());
			assertTrue(after.source().contains("this.field"), after.source());
			assertNotEquals(source.sourceSnapshotId(), after.sourceSnapshotId()); assertColdUnrelated(runtime);
			assertEquals("NO_CHANGE", service.apply(request(after, rename(after, INSTANCE, 1, "wideCount"))).outcome());
			assertEquals(after.sourceSnapshotId(), source(runtime).sourceSnapshotId());
			// Current revision cannot bypass the old source binding.
			var stale = assertThrows(EditBatchService.Rejected.class, () -> service.apply(request(after, rename(source, INSTANCE, 2, "fraction"))));
			assertEquals("STALE_REVISION", stale.code());
			assertEquals("STALE_REVISION", assertThrows(EditBatchService.Rejected.class,
					() -> service.apply(request(source, rename(source, INSTANCE, 0, "first")))).code());
		}
	}
	static void assertColdUnrelated(ProjectRuntime runtime) {
		assertFalse(JadxSymbolAdapter.visibleClass(runtime.decompiler(), "Lprobe/VariableUnrelated;", 0).getClassNode().getState().isProcessComplete());
	}
	@ParameterizedTest @ValueSource(strings={"class", "bad-name", "i", "str"})
	void invalidNameOrParameterCollisionRejectsEntireMixedBatch(String name) throws Exception {
		try (var runtime = raw()) {
			var source = source(runtime);
			var rejected = assertThrows(EditBatchService.Rejected.class, () -> new EditBatchService(runtime).apply(request(source,
					new EditDtos.Operation(EditDtos.Kind.RENAME, OWNER, "NeverApplied", null, null), rename(source, INSTANCE, 1, name))));
			assertEquals("INVALID_REQUEST", rejected.code()); assertEquals(1, rejected.itemErrors().getFirst().index());
			assertEquals(0, runtime.projectSnapshot().revisions().logicalRevision()); assertFalse(runtime.projectSnapshot().dirty());
			assertEquals(source.sourceSnapshotId(), source(runtime).sourceSnapshotId()); assertColdUnrelated(runtime);
		}
	}
	@Test void localCollisionAndTwoProposedNamesAndDuplicatesReject() throws Exception {
		try (var runtime = raw()) {
			var source = source(runtime);
			var local = source.variables().stream().filter(v -> v.method().equals(INSTANCE) && v.kind().equals("LOCAL")).findFirst().orElseThrow();
			var service = new EditBatchService(runtime);
			assertEquals("INVALID_REQUEST", assertThrows(EditBatchService.Rejected.class, () -> service.apply(request(source,
					rename(source, INSTANCE, 0, local.displayName())))).code());
			assertEquals("INVALID_REQUEST", assertThrows(EditBatchService.Rejected.class, () -> service.apply(request(source,
					rename(source, INSTANCE, 0, "same"), rename(source, INSTANCE, 1, "same")))).code());
			assertEquals("INVALID_REQUEST", assertThrows(EditBatchService.Rejected.class, () -> service.apply(request(source,
					rename(source, INSTANCE, 0, "first"), rename(source, INSTANCE, 0, "other")))).code());
		}
	}
	@Test void missingMethodOutOfRangeIndexAndMissingRevisionReject() throws Exception {
		try (var runtime = raw()) {
			var source = source(runtime); var service = new EditBatchService(runtime);
			assertEquals("NOT_FOUND", assertThrows(EditBatchService.Rejected.class, () -> service.apply(request(source,
					rename(source, method("missing", "(I)I"), 0, "value")))).code());
			assertEquals("UNSUPPORTED_CAPABILITY", assertThrows(EditBatchService.Rejected.class, () -> service.apply(request(source,
					rename(source, INSTANCE, 4, "value")))).code());
			assertEquals("INVALID_REQUEST", assertThrows(EditBatchService.Rejected.class, () -> service.apply(new EditDtos.Request(null, null,
					List.of(rename(source, INSTANCE, 0, "value"))))).code());
		}
	}
	@Test void guiVarParameterOrDuplicateNativeKeyFailsAmbiguous() throws Exception {
		try (var runtime = raw()) {
			var ref = new JadxNodeRef(jadx.api.data.IJavaNodeRef.RefType.METHOD, "probe.Variables", "instance(IJDLjava/lang/String;)I");
			for (boolean duplicate : List.of(false, true)) {
				JadxCodeData code = new JadxCodeData();
				code.setRenames(duplicate ? List.of(new JadxCodeRename(ref, JadxCodeRef.forMthArg(1), "wide"),
						new JadxCodeRename(ref, JadxCodeRef.forMthArg(1), "other"))
						: List.of(new JadxCodeRename(ref, JadxCodeRef.forVar(6, 0), "wide")));
				runtime.replaceCodeData(code, runtime.projectSnapshot().revisions().logicalRevision());
				var source = source(runtime);
				assertEquals("INVALID_ENTITY_ID", assertThrows(EditBatchService.Rejected.class,
						() -> new EditBatchService(runtime).apply(request(source, rename(source, INSTANCE, 1, "newWide")))).code());
			}
		}
	}
	@Test void temporaryMetadataCannotAuthorizePrimaryEdit() throws Exception {
		try (var runtime = raw()) {
			var before = source(runtime); var temporary = source(runtime, "SIMPLE");
			assertTrue(temporary.variables().stream().allMatch(v -> v.persistability().equals("UNSUPPORTED")));
			assertEquals("STALE_REVISION", assertThrows(EditBatchService.Rejected.class,
					() -> new EditBatchService(runtime).apply(request(temporary, rename(temporary, INSTANCE, 1, "leaked")))).code());
			assertEquals(before.sourceSnapshotId(), source(runtime).sourceSnapshotId());
		}
	}
	@Test void ownerDeclarationMutationInvalidatesOldParameterSnapshot() throws Exception {
		try (var runtime = raw()) {
			var before = source(runtime);
			new EditBatchService(runtime).apply(request(before, new EditDtos.Operation(EditDtos.Kind.RENAME, OWNER, "EditedVariables", null, null)));
			var current = source(runtime);
			assertEquals("STALE_REVISION", assertThrows(EditBatchService.Rejected.class,
					() -> new EditBatchService(runtime).apply(request(current, rename(before, INSTANCE, 0, "old")))).code());
		}
	}
	@ParameterizedTest @ValueSource(ints={0,1})
	void stagingFailureReportsOnlyCommittedPrefix(int failedAt) throws Exception {
		try (var runtime = raw()) {
			var before = source(runtime);
			var service = new EditBatchService(runtime, i -> { if (i == failedAt) throw new IllegalStateException("injected"); });
			var result = service.apply(request(before, rename(before, INSTANCE, 0, "first"), rename(before, INSTANCE, 1, "wide"),
					rename(before, INSTANCE, 2, "fraction")));
			assertEquals("PARTIAL", result.outcome());
			assertEquals(failedAt, result.logicalRevisionAfter());
			assertEquals(failedAt == 0 ? List.of("FAILED", "SKIPPED", "SKIPPED") : List.of("APPLIED", "FAILED", "SKIPPED"),
					result.items().stream().map(EditDtos.ItemResult::status).toList());
			assertEquals(failedAt == 1, source(runtime).source().contains("int first"));
		}
	}

	@Test void generatedParameterNameIsANoopAndDirtyEditsSurviveScopedFailures() throws Exception {
		try (var runtime = raw()) {
			var source = source(runtime);
			var parameter = source.variables().stream().filter(v -> v.method().equals(INSTANCE) && Integer.valueOf(0).equals(v.parameterIndex())).findFirst().orElseThrow();
			var service = new EditBatchService(runtime);
			assertEquals("NO_CHANGE", service.apply(request(source, rename(source, INSTANCE, 0, parameter.displayName()))).outcome());
			assertFalse(runtime.projectSnapshot().dirty());
			service.apply(request(source, new EditDtos.Operation(EditDtos.Kind.RENAME, OWNER, "PriorAlias", null, null)));
			var dirty = source(runtime);
			String pending = runtime.pendingEdits().toString();
			assertEquals("INVALID_REQUEST", assertThrows(EditBatchService.Rejected.class, () -> service.apply(request(dirty,
					rename(dirty, INSTANCE, 1, "next"), rename(dirty, INSTANCE, 2, "class")))).code());
			assertEquals(pending, runtime.pendingEdits().toString());
			var failure = new EditBatchService(runtime, i -> { if (i == 1) throw new IllegalStateException("injected"); });
			var result = failure.apply(request(dirty, rename(dirty, INSTANCE, 1, "wide"), rename(dirty, INSTANCE, 2, "fraction")));
			assertEquals(List.of("APPLIED", "FAILED"), result.items().stream().map(EditDtos.ItemResult::status).toList());
			assertEquals(dirty.logicalRevision() + 1, result.logicalRevisionAfter());
			assertTrue(source(runtime).source().contains("class PriorAlias"));
			assertTrue(source(runtime).source().contains("long wide"));
		}
	}
	@Test void explicitSaveReopenDiscardSettingsAndExternalConflicts() throws Exception {
		Path jar = SymbolFixtureSupport.compileVariableFixture(dir);
		Path project = dir.resolve("variables.jadx");
		var doc = NativeProjectDocument.newFromInputs(project, List.of(jar)); doc.save();
		String original = Files.readString(project);
		try (var runtime = new ProjectRuntime(project, List.of(jar), List.of(dir))) {
			runtime.initializeAsync(NativeProjectDocument.open(project)).get(20, TimeUnit.SECONDS);
			var before = source(runtime); var service = new EditBatchService(runtime);
			service.apply(request(before, rename(before, INSTANCE, 1, "unsavedWide")));
			assertEquals(original, Files.readString(project));
			runtime.reloadProject(true, runtime.projectSnapshot().revisions().sessionId(), runtime.projectSnapshot().revisions().logicalRevision());
			var discarded = source(runtime); assertFalse(discarded.source().contains("unsavedWide"));
			assertEquals("STALE_REVISION", assertThrows(EditBatchService.Rejected.class,
					() -> service.apply(request(discarded, rename(before, INSTANCE, 0, "old")))).code());
			service.apply(request(discarded, rename(discarded, INSTANCE, 1, "savedWide")));
			var beforeSettings = source(runtime);
			Path mapping = dir.resolve("mapping.tiny"); Files.writeString(mapping, "tiny\t2\t0\toriginal\tmapped\n");
			var revision = runtime.projectSnapshot().revisions(); runtime.updateMappingsPath(mapping, revision.sessionId(), revision.logicalRevision());
			var settings = source(runtime); assertTrue(settings.source().contains("savedWide"));
			assertEquals("STALE_REVISION", assertThrows(EditBatchService.Rejected.class,
					() -> service.apply(request(settings, rename(beforeSettings, INSTANCE, 0, "old")))).code());
			Files.writeString(project, original + "\n");
			assertThrows(NativeProjectRepository.ExternalModificationException.class, () -> runtime.saveProject(null, null, null));
			Files.writeString(project, original);
			Files.writeString(mapping, "tiny\t2\t0\toriginal\tmapped\n#external\n");
			assertThrows(NativeProjectRepository.ExternalModificationException.class, () -> runtime.saveProject(null, null, null));
			Files.writeString(mapping, "tiny\t2\t0\toriginal\tmapped\n");
			runtime.saveProject(null, null, null);
			assertFalse(runtime.projectSnapshot().dirty());
			runtime.reloadProject(false, runtime.projectSnapshot().revisions().sessionId(), runtime.projectSnapshot().revisions().logicalRevision()); assertTrue(source(runtime).source().contains("savedWide"));
		}
		try (var runtime = new ProjectRuntime(project, List.of(jar), List.of(dir))) {
			runtime.initializeAsync(NativeProjectDocument.open(project)).get(20, TimeUnit.SECONDS);
			assertTrue(source(runtime).source().contains("savedWide"));
		}
	}


	@Test void scopedNativeEntryAndNestedUnknownFieldsSurviveExplicitSave() throws Exception {
		Path jar = SymbolFixtureSupport.compileVariableFixture(dir);
		Path project = dir.resolve("unknown.jadx");
		var document = NativeProjectDocument.newFromInputs(project, List.of(jar));
		var code = new JadxCodeData();
		var methodRef = new JadxNodeRef(jadx.api.data.IJavaNodeRef.RefType.METHOD, "probe.Variables", "instance(IJDLjava/lang/String;)I");
		code.setRenames(List.of(new JadxCodeRename(methodRef, JadxCodeRef.forMthArg(1), "beforeWide")));
		document.setCodeData(code); document.save();
		var json = new com.fasterxml.jackson.databind.ObjectMapper();
		var tree = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(Files.readString(project));
		var rename = (com.fasterxml.jackson.databind.node.ObjectNode) tree.path("codeData").path("renames").get(0);
		rename.put("futureRename", "keep-entry");
		((com.fasterxml.jackson.databind.node.ObjectNode) rename.path("codeRef")).put("futureScope", "keep-scope");
		((com.fasterxml.jackson.databind.node.ObjectNode) rename.path("nodeRef")).put("futureNode", "keep-node");
		Files.writeString(project, json.writeValueAsString(tree));
		String original = Files.readString(project);
		try (var runtime = new ProjectRuntime(project, List.of(jar), List.of(dir))) {
			runtime.initializeAsync(NativeProjectDocument.open(project)).get(20, TimeUnit.SECONDS);
			var source = source(runtime);
			new EditBatchService(runtime).apply(request(source, rename(source, INSTANCE, 1, "afterWide")));
			assertEquals(original, Files.readString(project)); runtime.saveProject(null, null, null);
		}
		var saved = json.readTree(Files.readString(project)).path("codeData").path("renames").get(0);
		assertEquals("keep-entry", saved.path("futureRename").asText());
		assertEquals("keep-scope", saved.path("codeRef").path("futureScope").asText());
		assertEquals("keep-node", saved.path("nodeRef").path("futureNode").asText());
		assertEquals("afterWide", saved.path("newName").asText());
	}
	@Test void annotatedGenericAndBodylessFormsFailClosed() throws Exception {
		try (var runtime = raw()) {
			var source = source(runtime);
			var service = new EditBatchService(runtime);
			for (SymbolRef ref : List.of(method("annotated", "(I)I"), method("generic", "(Ljava/lang/Object;)Ljava/lang/Object;"),
					new SymbolRef(SymbolRef.Kind.METHOD, "Lprobe/VariableContract;", null, "absentBody", "(I)I"))) {
				// The bodyless method belongs to another emitted owner and needs its current snapshot.
				var cls = JadxSymbolAdapter.visibleClass(runtime.decompiler(), ref.originalClassDescriptor(), 0);
				var ownSource = runtime.withPrimarySymbolRead(ref.originalClassDescriptor(), context ->
						dev.libjadx.jadxadapter.JadxSourceAdapter.extract(context.decompiler(), cls, SymbolRef.classRef(ref.originalClassDescriptor()),
								true, context.revisions().sessionId(), context.revisions().logicalRevision(), context.publicationEpoch(), context.settings().fingerprint()));
				var operation = new EditDtos.Operation(EditDtos.Kind.RENAME_PARAMETER, ref, "value", null, null, 0, ownSource.sourceSnapshotId());
				assertEquals("UNSUPPORTED_CAPABILITY", assertThrows(EditBatchService.Rejected.class,
						() -> service.apply(new EditDtos.Request(source.sessionId(), source.logicalRevision(), List.of(operation)))).code());
			}
		}
	}
	@Test void prepareMatchingGuiFixtureThroughPublicMetadataAndExplicitSave() throws Exception {
		Path root = Files.createDirectories(Path.of("build/scoped-edit-gui-fixture").toAbsolutePath());
		Path jar = SymbolFixtureSupport.compileVariableFixture(root);
		Path project = root.resolve("scoped.jadx");
		Files.deleteIfExists(project);
		Path mapping = root.resolve("mapping.tiny");
		Files.writeString(mapping, "tiny\t2\t0\toriginal\tmapped\nc\tprobe/VariableUnrelated\tprobe/MappedUnrelated\n");
		var doc = NativeProjectDocument.newFromInputs(project, List.of(jar)); doc.save();
		var json = new com.fasterxml.jackson.databind.ObjectMapper();
		var tree = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(Files.readString(project));
		tree.putObject("futureRoot").put("retain", true); tree.put("mappingsPath", "mapping.tiny");
		Files.writeString(project, json.writeValueAsString(tree));
		String baseline = Files.readString(project);
		try (var runtime = new ProjectRuntime(project, List.of(jar), List.of(root))) {
			runtime.initializeAsync(NativeProjectDocument.open(project)).get(20, TimeUnit.SECONDS);
			var source = source(runtime);
			List<EditDtos.Operation> operations = new ArrayList<>();
			for (var variable : source.variables()) {
				if (variable.kind().equals("PARAMETER") && variable.persistability().equals("SUPPORTED"))
					operations.add(rename(source, variable.method(), variable.parameterIndex(), "parameter" + variable.parameterIndex()));
			}
			assertFalse(operations.isEmpty());
			operations.add(new EditDtos.Operation(EditDtos.Kind.RENAME, OWNER, "EditedVariables", null, null));
			operations.add(new EditDtos.Operation(EditDtos.Kind.SET_COMMENT, INSTANCE, null, "scoped GUI comment", "LINE"));
			var applied = new EditBatchService(runtime).apply(new EditDtos.Request(source.sessionId(), source.logicalRevision(), operations));
			assertTrue(applied.dirty()); assertEquals(baseline, Files.readString(project));
			runtime.saveProject(null, null, null);
			assertTrue(Files.readString(project).contains("futureRoot"));
			assertTrue(source(runtime).source().contains("parameter1"));
		}
	}
}
