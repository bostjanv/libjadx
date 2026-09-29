package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import dev.libjadx.core.edits.EditDtos;
import dev.libjadx.core.hierarchy.RelatedHierarchyVerifier;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.probes.RelatedFixture;
import dev.libjadx.project.*;
import jadx.api.data.impl.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Production publication and an independent fresh oracle over complete native intent. */
class ReplacementStateTest {
	@TempDir Path dir;
	record Fixture(Path project, List<Path> inputs, Path mapping) { }
	static Fixture fixture(Path root, boolean mapping, boolean retainedVar) throws Exception {
		Files.createDirectories(root);
		var inputs = new ArrayList<>(RelatedFixture.compile(root));
		inputs.add(SymbolFixtureSupport.compileVariableFixture(Files.createDirectories(root.resolve("variables"))));
		inputs.add(SymbolFixtureSupport.returnTypeClashJar(Files.createDirectories(root.resolve("clash"))));
		Path attached = root.resolve("state.tiny");
		Files.writeString(attached, "tiny\t2\t0\toriginal\tmapped\nc\tprobe/VariableUnrelated\tprobe/MappedUnrelated\n\tc\tattached class comment\n"
				+ "\tm\t(I)I\tcold\tmappedCold\n\t\tc\tattached method comment\n"
				+ "c\tprobe/Variables\tprobe/Variables\n\tm\t()I\tzero\tmappedZero\n");
		Path project = root.resolve("replacement.jadx");
		var doc = NativeProjectDocument.newFromInputs(project, inputs);
		if (mapping) doc = doc.withMappingsPath(attached);
		var code = new JadxCodeData();
		var renames = new ArrayList<jadx.api.data.ICodeRename>();
		renames.add(new JadxCodeRename(new JadxNodeRef(jadx.api.data.IJavaNodeRef.RefType.METHOD, "probe.ReturnClash", "value()I"), "value"));
		if (retainedVar) try (var engine = SafeReplayStrategyTest.open(inputs, mapping ? attached : null, code)) {
			var variable = engine.searchJavaClassByOrigFullName("probe.Variables").getCodeInfo().getCodeMetadata().getAsMap().values().stream()
					.filter(a -> a instanceof jadx.api.metadata.annotations.NodeDeclareRef)
					.map(a -> ((jadx.api.metadata.annotations.NodeDeclareRef) a).getNode())
					.filter(n -> n instanceof jadx.api.metadata.annotations.VarNode)
					.map(n -> (jadx.api.metadata.annotations.VarNode) n)
					.filter(v -> v.getMth().getMethodInfo().getShortId().equals("single(I)I")).findFirst().orElseThrow();
			renames.add(new JadxCodeRename(SafeReplayStrategyTest.node("single(I)I"), JadxCodeRef.forVar(variable.getReg(), variable.getSsa()), "guiValue"));
		}
		code.setRenames(renames); doc.setCodeData(code);
		var tree = doc.toJsonTree(); tree.addProperty("futureRoot", "preserved");
		tree.getAsJsonObject("codeData").addProperty("futureCodeData", "preserved");
		tree.getAsJsonObject("codeData").getAsJsonArray("renames").get(0).getAsJsonObject().getAsJsonObject("nodeRef").addProperty("futureNode", "preserved");
		Files.writeString(project, tree.toString());
		return new Fixture(project, List.copyOf(inputs), mapping ? attached : null);
	}
	static EditDtos.Operation rename(SymbolRef ref, String name) { return new EditDtos.Operation(EditDtos.Kind.RENAME, ref, name, null, null); }
	static EditDtos.Request mixed(ProjectRuntime runtime) throws Exception {
		var source = ScopedParameterServiceTest.source(runtime);
		return ScopedParameterServiceTest.request(source,
				rename(ScopedParameterServiceTest.OWNER, "EditedVariables"),
				rename(ScopedParameterServiceTest.INSTANCE, "editedInstance"),
				rename(new SymbolRef(SymbolRef.Kind.FIELD, "Lprobe/Variables;", null, "field", "I"), "editedField"),
				rename(ScopedParameterServiceTest.method("zero", "()I"), "nativeZero"),
				new EditDtos.Operation(EditDtos.Kind.SET_COMMENT, ScopedParameterServiceTest.INSTANCE, null, "native method comment", "LINE"),
				new EditDtos.Operation(EditDtos.Kind.SET_COMMENT, SymbolRef.classRef("Lprobe/VariableUnrelated;"), null, "native class comment", "LINE"),
				ScopedParameterServiceTest.rename(source, ScopedParameterServiceTest.INSTANCE, 1, "wideCount"),
				ScopedParameterServiceTest.rename(source, ScopedParameterServiceTest.method("statik", "(JID)J"), 0, "staticWide"),
				ScopedParameterServiceTest.rename(source, ScopedParameterServiceTest.method("single", "(Ljava/lang/String;)Ljava/lang/String;"), 0, "textArg"),
				rename(new SymbolRef(SymbolRef.Kind.METHOD, "Lprobe/ReturnClash;", null, "value", "()I"), "intValueAlias"));
	}
	static Map<String,String> assertOracle(ProjectRuntime runtime, Fixture fixture) throws Exception {
		JadxCodeData code = runtime.withExclusiveEdit(context -> context.codeDataCopy());
		var actual = runtime.withPrimaryClassRead("oracle", SafeReplayStrategyTest::semantic);
		try (var fresh = SafeReplayStrategyTest.open(fixture.inputs(), runtime.settingsSnapshot().mappingsPath(), code)) {
			assertEquals(SafeReplayStrategyTest.semantic(fresh), actual);
			assertTrue(NativeProjectDocument.codeDataEquivalent(code, runtime.decompiler().getArgs().getCodeData()));
			var capture = dev.libjadx.jadxadapter.JadxInputCensusAdapter.capture(fixture.inputs(), dev.libjadx.core.hierarchy.CensusLimits.defaults());
			var seed = RelatedFixture.ref("Joined", "joined", "(I)I");
			assertEquals(capture.bind(fresh).verify(seed, RelatedHierarchyVerifier.VerificationBudget.defaults()),
					runtime.verifyRelatedHierarchy(seed, RelatedHierarchyVerifier.VerificationBudget.defaults()));
		}
		String java = actual.get("source:probe.Variables");
		for (String token : List.of("EditedVariables", "editedInstance", "editedField", "nativeZero", "wideCount", "staticWide", "textArg", "native method comment")) assertTrue(java.contains(token), token);
		assertTrue(actual.get("source:probe.ReturnClash").contains("String value()"));
		for (var ref : PropagatedNativeReplayTest.BRIDGE_NONMEMBERS) assertEquals("value", actual.get("method:" + ref));
		var records = code.getRenames().stream().filter(r -> r.getNodeRef().getDeclaringClass().equals("probe.ReturnClash")).toList();
		assertEquals(1, records.size()); assertEquals("value()I", records.getFirst().getNodeRef().getShortId());
		return actual;
	}
	@ParameterizedTest @ValueSource(strings={"cold", "owner", "unrelated", "all", "metadata"})
	void completeSupportedStateIsFreshEquivalentRegardlessOfOldAnalysis(String history) throws Exception {
		for (boolean raw : List.of(false, true)) for (boolean mapping : List.of(false, true)) {
			if (raw && mapping) continue; // Native mapping attachment requires an existing project.
			var fixture = fixture(dir.resolve(raw + "-" + mapping), mapping, !raw);
			try (var runtime = new ProjectRuntime(raw ? null : fixture.project(), fixture.inputs(), List.of(dir))) {
				runtime.initializeAsync(raw ? null : NativeProjectDocument.open(fixture.project())).get(20, TimeUnit.SECONDS);
				switch (history) {
					case "owner" -> runtime.decompiler().searchJavaClassByOrigFullName("probe.Variables").getCode();
					case "unrelated" -> runtime.decompiler().searchJavaClassByOrigFullName("probe.VariableUnrelated").getCode();
					case "all" -> runtime.decompiler().getClasses().forEach(c -> c.getCode());
					case "metadata" -> SafeReplayStrategyTest.semantic(runtime.decompiler());
					default -> { }
				}
				var disk = FileFingerprint.of(fixture.project());
				var result = new EditBatchService(runtime).apply(mixed(runtime));
				assertEquals("APPLIED", result.outcome()); assertEquals(1, result.logicalRevisionAfter()); assertEquals(1, result.indexRevisionAfter());
				var actual = assertOracle(runtime, fixture); assertEquals(disk, FileFingerprint.of(fixture.project()));
				runtime.saveProject(raw ? dir.resolve("raw-" + mapping + ".jadx") : null, null);
				var saved = NativeProjectDocument.open(runtime.projectSnapshot().projectPath());
				try (var fresh = SafeReplayStrategyTest.open(saved.getInputFiles(), saved.getMappingsPath(), saved.getCodeData())) { assertEquals(actual, SafeReplayStrategyTest.semantic(fresh)); }
				if (!raw) assertEquals("preserved", saved.toJsonTree().get("futureRoot").getAsString());
			}
		}
	}
	@Test void preparePositiveMatchingGuiGateThroughProductionService() throws Exception {
		Path root = Path.of("build/replacement-edit-gui-fixture").toAbsolutePath();
		var fixture = fixture(root, true, true);
		var hashes = new HashMap<Path, FileFingerprint>();
		for (Path path : fixture.inputs()) hashes.put(path, FileFingerprint.of(path));
		hashes.put(fixture.project(), FileFingerprint.of(fixture.project())); hashes.put(fixture.mapping(), FileFingerprint.of(fixture.mapping()));
		try (var runtime = new ProjectRuntime(fixture.project(), fixture.inputs(), List.of(root))) {
			runtime.initializeAsync(NativeProjectDocument.open(fixture.project())).get(20, TimeUnit.SECONDS);
			SafeReplayStrategyTest.semantic(runtime.decompiler());
			assertEquals("APPLIED", new EditBatchService(runtime).apply(mixed(runtime)).outcome());
			assertOracle(runtime, fixture);
			for (var entry : hashes.entrySet()) assertEquals(entry.getValue(), FileFingerprint.of(entry.getKey()), "No autosave " + entry.getKey());
			runtime.saveProject(null, null);
			var tree = NativeProjectDocument.open(fixture.project()).toJsonTree();
			assertEquals("preserved", tree.get("futureRoot").getAsString());
			assertEquals("preserved", tree.getAsJsonObject("codeData").get("futureCodeData").getAsString());
			assertEquals("preserved", tree.getAsJsonObject("codeData").getAsJsonArray("renames").get(0).getAsJsonObject().getAsJsonObject("nodeRef").get("futureNode").getAsString());
			assertFalse(Path.of(tree.getAsJsonArray("files").get(0).getAsString()).isAbsolute());
			assertEquals("state.tiny", tree.get("mappingsPath").getAsString());
		}
	}
	@Test void actualMatchingGuiSaveAsReopensExactIntentAndFreshAliases() throws Exception {
		String path = System.getenv("LIBJADX_REPLACEMENT_GUI_PROJECT");
		Assumptions.assumeTrue(path != null, "Run replacementEditGuiRoundTripTest with matching Jadx 1.5.6 GUI");
		var doc = NativeProjectDocument.open(Path.of(path));
		var original = NativeProjectDocument.open(Path.of(path).resolveSibling("replacement.jadx"));
		assertTrue(NativeProjectDocument.codeDataEquivalent(original.getCodeData(), doc.getCodeData()));
		assertFalse(doc.toJsonTree().has("futureRoot"), "Known matching-GUI unknown field loss");
		try (var runtime = new ProjectRuntime(doc.getProjectPath(), doc.getInputFiles())) {
			runtime.initializeAsync(doc).get(20, TimeUnit.SECONDS);
			var actual = assertOracle(runtime, new Fixture(doc.getProjectPath(), doc.getInputFiles(), doc.getMappingsPath()));
			try (var reference = SafeReplayStrategyTest.open(original.getInputFiles(), original.getMappingsPath(), original.getCodeData())) { assertEquals(SafeReplayStrategyTest.semantic(reference), actual); }
			assertTrue(actual.get("source:probe.VariableUnrelated").contains("attached method comment"));
			assertTrue(actual.get("source:probe.Variables").contains("guiValue"));
		}
	}
	@Test void discardReloadRestartAndMappingRebuildThenEdit() throws Exception {
		var fixture = fixture(dir, true, true);
		String session;
		try (var runtime = new ProjectRuntime(fixture.project(), fixture.inputs(), List.of(dir))) {
			runtime.initializeAsync(NativeProjectDocument.open(fixture.project())).get(20, TimeUnit.SECONDS);
			session = runtime.projectSnapshot().revisions().sessionId();
			runtime.updateMappingsPath(null, session, 0);
			runtime.updateMappingsPath(fixture.mapping(), session, 1);
			new EditBatchService(runtime).apply(mixed(runtime)); assertOracle(runtime, fixture);
			runtime.reloadProject(true, session, 3);
			assertFalse(runtime.projectSnapshot().dirty());
			assertTrue(runtime.decompiler().searchJavaClassByOrigFullName("probe.ReturnClash").getCode().contains("m0value"));
			new EditBatchService(runtime).apply(mixed(runtime)); runtime.saveProject(null, null);
		}
		try (var runtime = new ProjectRuntime(fixture.project(), fixture.inputs(), List.of(dir))) {
			runtime.initializeAsync(NativeProjectDocument.open(fixture.project())).get(20, TimeUnit.SECONDS);
			assertNotEquals(session, runtime.projectSnapshot().revisions().sessionId()); assertOracle(runtime, fixture);
		}
		// Separate unsaved discard, starting from saved edits.
		var baseline = FileFingerprint.of(fixture.project());
		try (var runtime = new ProjectRuntime(fixture.project(), fixture.inputs(), List.of(dir))) {
			runtime.initializeAsync(NativeProjectDocument.open(fixture.project())).get(20, TimeUnit.SECONDS);
			new EditBatchService(runtime).apply(new EditDtos.Request(null, null, List.of(rename(ScopedParameterServiceTest.OWNER, "DiscardedAlias"))));
			assertTrue(runtime.decompiler().searchJavaClassByOrigFullName("probe.Variables").getCode().contains("DiscardedAlias"));
		}
		assertEquals(baseline, FileFingerprint.of(fixture.project()));
		try (var fresh = SafeReplayStrategyTest.open(fixture.inputs(), fixture.mapping(), NativeProjectDocument.open(fixture.project()).getCodeData())) { assertFalse(fresh.searchJavaClassByOrigFullName("probe.Variables").getCode().contains("DiscardedAlias")); }
	}
}
