package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import dev.libjadx.core.EffectiveAnalysisConfig;
import dev.libjadx.core.hierarchy.CensusLimits;
import dev.libjadx.core.hierarchy.RelatedHierarchyVerifier;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.jadxadapter.JadxEngineFactory;
import dev.libjadx.jadxadapter.JadxInputCensusAdapter;
import dev.libjadx.jadxadapter.JadxNativeEditAdapter;
import dev.libjadx.jadxadapter.JadxSymbolAdapter;
import dev.libjadx.probes.RelatedFixture;
import dev.libjadx.project.NativeProjectDocument;
import jadx.api.JadxDecompiler;
import jadx.api.data.impl.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Phase A: isolated real engines, before changing the production publication path. */
class SafeReplayStrategyTest {

	@TempDir Path dir;

	enum Strategy { CURRENT, LISTENER_FIRST, OWNERS_ONLY, REPLACEMENT }

	static JadxDecompiler open(List<Path> inputs, Path mapping, JadxCodeData code) {
		var engine = new JadxDecompiler(JadxEngineFactory.arguments(inputs, mapping, code, EffectiveAnalysisConfig.defaults()));
		try { engine.load(); return engine; }
		catch (RuntimeException | Error failure) { engine.close(); throw failure; }
	}

	/** Original inventory plus ALL raw aliases; generated Java includes comments and scoped tokens.
	 * Generate in original-owner order in both engines. No process states become semantic identity. */
	static Map<String, String> semantic(JadxDecompiler engine) {
		var result = new TreeMap<String, String>();
		engine.getClasses().stream().sorted(java.util.Comparator.comparing(jadx.api.JavaClass::getRawName))
				.forEach(cls -> result.put("source:" + cls.getRawName(), cls.getCode()));
		for (var cls : engine.getClassesWithInners()) {
			var node = cls.getClassNode();
			result.put("class:" + node.getRawName(), node.getClassInfo().getAliasFullName());
			for (var method : node.getMethods()) result.put("method:" + JadxSymbolAdapter.originalRef(method.getMethodInfo()), method.getAlias());
			for (var field : node.getFields()) result.put("field:" + node.getRawName() + ":" + field.getFieldInfo().getShortId(), field.getAlias());
		}
		// The same immutable adapters used by source/navigation and member search.
		for (var cls : engine.getClasses()) {
			var ref = SymbolRef.classRef(JadxSymbolAdapter.descriptor(cls.getRawName()));
			var source = dev.libjadx.jadxadapter.JadxSourceAdapter.extract(engine, cls, ref, true,
					"00000000-0000-0000-0000-000000000001", 0, 0, EffectiveAnalysisConfig.defaults().fingerprint());
			result.put("annotations:" + cls.getRawName(), source.annotations().toString());
			result.put("variables:" + cls.getRawName(), source.variables().toString());
			result.put("searchMembers:" + cls.getRawName(), dev.libjadx.jadxadapter.JadxSearchAdapter.members(cls).stream()
					.map(Object::toString).sorted().toList().toString());
			var query = new dev.libjadx.core.references.ReferenceQuery(ref,
					dev.libjadx.core.references.ReferenceQuery.Direction.OUTGOING, null, 100, null, false, false, null, null);
			result.put("dependencies:" + cls.getRawName(), dev.libjadx.jadxadapter.JadxReferenceAdapter.extract(engine, cls, query,
					"00000000-0000-0000-0000-000000000001", 0, 0, EffectiveAnalysisConfig.defaults().fingerprint()).stream()
					.map(Object::toString).sorted().toList().toString());
		}
		return Map.copyOf(result);
	}

	static void replay(JadxDecompiler engine, JadxCodeData code, Strategy strategy, List<String> owners) {
		if (strategy == Strategy.CURRENT) {
			// Exact PR #17 production implementation, retained solely as a negative control.
			engine.getArgs().setCodeData(NativeProjectDocument.copyCodeData(code));
			for (var cls : engine.getClasses()) cls.unload();
			JadxNativeEditAdapter.prepareCodeDataReplay(engine);
			engine.reloadCodeData();
			return;
		}
		engine.getArgs().setCodeData(NativeProjectDocument.copyCodeData(code));
		if (strategy == Strategy.LISTENER_FIRST) {
			JadxNativeEditAdapter.prepareCodeDataReplay(engine);
			engine.reloadCodeData();
			engine.getClasses().forEach(jadx.api.JavaClass::unload);
		} else {
			engine.getClasses().stream().filter(cls -> strategy == Strategy.CURRENT || owners.contains(cls.getRawName()))
					.forEach(jadx.api.JavaClass::unload);
			JadxNativeEditAdapter.prepareCodeDataReplay(engine);
			engine.reloadCodeData();
		}
	}

	@Test void exactJoinedHotColdAndEveryFirstRecordWithIndependentFreshOracle() throws Exception {
		var inputs = RelatedFixture.compile(dir);
		var seed = RelatedFixture.ref("Joined", "joined", "(I)I");
		List<SymbolRef> family;
		var initialCapture = JadxInputCensusAdapter.capture(inputs, CensusLimits.defaults());
		try (var initial = open(inputs, null, new JadxCodeData())) {
			var verified = initialCapture.bind(initial)
					.verify(seed, RelatedHierarchyVerifier.VerificationBudget.defaults());
			assertEquals(RelatedHierarchyVerifier.Status.COMPLETE, verified.status());
			family = verified.members(); assertEquals(4, family.size());
		}
		for (boolean hot : List.of(false, true)) for (var first : family) {
			var ordered = new ArrayList<>(family); ordered.remove(first); ordered.addFirst(first);
			var code = new JadxCodeData();
			code.setRenames(ordered.stream().map(ref -> new JadxCodeRename(new JadxNodeRef(
					jadx.api.data.IJavaNodeRef.RefType.METHOD, ref.originalClassDescriptor().substring(1,
							ref.originalClassDescriptor().length() - 1).replace('/', '.'), ref.originalName() + ref.originalDescriptor()),
					PropagatedNativeReplayTest.ALIAS)).map(r -> (jadx.api.data.ICodeRename) r).toList());
			Map<String, String> expected;
			try (var fresh = open(inputs, null, code)) { expected = semantic(fresh); assertMemberTokens(fresh, family); }
			for (var strategy : Strategy.values()) try (var original = open(inputs, null, new JadxCodeData())) {
				if (hot) semantic(original);
				if (strategy == Strategy.REPLACEMENT) {
					var capture = JadxInputCensusAdapter.capture(inputs, CensusLimits.defaults());
					try (var candidate = open(inputs, null, code)) {
						assertEquals(expected, semantic(candidate));
						assertMemberTokens(candidate, family);
						for (var ref : PropagatedNativeReplayTest.BRIDGE_NONMEMBERS)
							assertEquals("value", expected.get("method:" + ref));
						var verified = capture.bind(candidate)
								.verify(seed, RelatedHierarchyVerifier.VerificationBudget.defaults());
						assertEquals(family, verified.members());
					}
				} else {
					replay(original, code, strategy, List.of("related.Hierarchy"));
					var actual = semantic(original);
					assertMemberTokens(original, family);
					if (hot && strategy != Strategy.LISTENER_FIRST) {
						for (var ref : PropagatedNativeReplayTest.BRIDGE_NONMEMBERS)
							assertEquals("m0value", actual.get("method:" + ref), "Old counterexample must still fail");
						assertNotEquals(expected, actual);
						assertEquals(PropagatedNativeReplayTest.BRIDGE_NONMEMBERS.stream().map(ref -> "method:" + ref).collect(java.util.stream.Collectors.toSet()),
								expected.keySet().stream().filter(key -> key.startsWith("method:") && !expected.get(key).equals(actual.get(key)))
										.collect(java.util.stream.Collectors.toSet()), "Exact unchanged PR #16 negative control");
					} else if (hot) {
						assertNotEquals(expected, actual, "Listener first is not a fresh semantic reset");
					} else assertEquals(expected, actual, strategy + " hot=" + hot);
					writeDiagnostic("joined-" + strategy + "-" + hot + "-" + family.indexOf(first),
							Map.of("strategy", strategy, "hot", hot, "first", first, "candidate", actual, "fresh", expected,
									"differentKeys", expected.keySet().stream().filter(key -> !expected.get(key).equals(actual.get(key))).sorted().toList()));
				}
			}
		}
	}

	@Test void listenerFirstLosesAttachedCommentsAndOwnerOnlyLeavesOtherSourceStale() throws Exception {
		var inputs = RelatedFixture.compile(dir);
		Path mapping = dir.resolve("mapping.tiny");
		Files.writeString(mapping, "tiny\t2\t0\toriginal\tmapped\nc\trelated/UnrelatedCold\trelated/MappedCold\n\tc\tattached class comment\n"
				+ "\tm\t(I)I\twork\tmappedWork\n\t\tc\tattached method comment\n");
		var code = new JadxCodeData();
		code.setRenames(List.of(new JadxCodeRename(JadxNodeRef.forCls("related.Hierarchy"), "EditedHierarchy")));
		try (var fresh = open(inputs, mapping, code)) {
			var expected = semantic(fresh);
			assertTrue(expected.get("source:related.UnrelatedCold").contains("attached class comment"));
			for (var strategy : List.of(Strategy.CURRENT, Strategy.LISTENER_FIRST, Strategy.OWNERS_ONLY)) {
				try (var candidate = open(inputs, mapping, new JadxCodeData())) {
					semantic(candidate);
					replay(candidate, code, strategy, List.of("related.Hierarchy"));
					var actual = semantic(candidate);
					assertNotEquals(expected, actual);
					if (strategy == Strategy.LISTENER_FIRST)
						assertFalse(actual.get("source:related.UnrelatedCold").contains("attached class comment"));
					// Bounded invalidation still invokes a global collision pass on unloaded bridge owners.
				}
			}
		}
	}
	static int count(String source, String token) { return source.split(java.util.regex.Pattern.quote(token), -1).length - 1; }

	@Test void boundedOwnerInvalidationLeavesGeneratedCallerReferencesStale() throws Exception {
		Path source = dir.resolve("Target.java");
		Files.writeString(source, "package owned; public class Target { public int field; public int call() { return field; } } "
				+ "class Caller { public int use(Target target) { return target.call() + target.field; } }");
		var inputs = List.of(SymbolFixtureSupport.compile(dir, source, "caller.jar"));
		var code = new JadxCodeData();
		code.setRenames(List.of(new JadxCodeRename(new JadxNodeRef(jadx.api.data.IJavaNodeRef.RefType.METHOD,
				"owned.Target", "call()I"), "editedCall"), new JadxCodeRename(new JadxNodeRef(
				jadx.api.data.IJavaNodeRef.RefType.FIELD, "owned.Target", "field:I"), "editedField")));
		try (var candidate = open(inputs, null, new JadxCodeData()); var fresh = open(inputs, null, code)) {
			semantic(candidate);
			replay(candidate, code, Strategy.OWNERS_ONLY, List.of("owned.Target"));
			var actual = semantic(candidate); var expected = semantic(fresh);
			assertTrue(actual.get("source:owned.Caller").contains("target.call()"));
			assertTrue(expected.get("source:owned.Caller").contains("target.editedCall()"));
			assertNotEquals(expected, actual);
		}
	}

	@Test void replacementReconstructsCompleteNativeMappingAndScopedStateAcrossSettings() throws Exception {
		var inputs = new ArrayList<>(RelatedFixture.compile(dir));
		inputs.add(SymbolFixtureSupport.compileVariableFixture(Files.createDirectories(dir.resolve("variables"))));
		Path mapping = dir.resolve("state.tiny");
		Files.writeString(mapping, "tiny\t2\t0\toriginal\tmapped\nc\tprobe/VariableUnrelated\tprobe/MappedUnrelated\n"
				+ "\tm\t(I)I\tcold\tmappedCold\n\t\tc\tmapping method comment\n"
				+ "c\tprobe/Variables\tprobe/Variables\n\tm\t()I\tzero\tmappedZero\n");
		JadxCodeRef guiScope;
		try (var before = open(inputs, mapping, new JadxCodeData())) {
			var variable = before.searchJavaClassByOrigFullName("probe.Variables").getCodeInfo().getCodeMetadata().getAsMap().values().stream()
					.filter(a -> a instanceof jadx.api.metadata.annotations.NodeDeclareRef)
					.map(a -> ((jadx.api.metadata.annotations.NodeDeclareRef) a).getNode())
					.filter(n -> n instanceof jadx.api.metadata.annotations.VarNode)
					.map(n -> (jadx.api.metadata.annotations.VarNode) n)
					.filter(v -> v.getMth().getMethodInfo().getShortId().equals("single(I)I")).findFirst().orElseThrow();
			guiScope = JadxCodeRef.forVar(variable.getReg(), variable.getSsa());
		}
		var code = new JadxCodeData();
		code.setRenames(List.of(new JadxCodeRename(JadxNodeRef.forCls("related.UnrelatedCold"), "NativeCold"),
				new JadxCodeRename(JadxNodeRef.forCls("probe.Variables"), "EditedVariables"),
				new JadxCodeRename(node("instance(IJDLjava/lang/String;)I"), "editedInstance"),
				new JadxCodeRename(new JadxNodeRef(jadx.api.data.IJavaNodeRef.RefType.FIELD, "probe.Variables", "field:I"), "editedField"),
				new JadxCodeRename(node("instance(IJDLjava/lang/String;)I"), JadxCodeRef.forMthArg(1), "wideCount"),
				new JadxCodeRename(node("statik(JID)J"), JadxCodeRef.forMthArg(0), "staticWide"),
				new JadxCodeRename(node("single(Ljava/lang/String;)Ljava/lang/String;"), JadxCodeRef.forMthArg(0), "textArg"),
				new JadxCodeRename(node("single(I)I"), guiScope, "guiValue")));
		code.setComments(List.of(new JadxCodeComment(JadxNodeRef.forCls("related.UnrelatedCold"), "unrelated native comment"),
				new JadxCodeComment(node("instance(IJDLjava/lang/String;)I"), "native method comment")));
		for (String mode : List.of("AUTO", "RESTRUCTURE", "SIMPLE", "FALLBACK")) {
			var config = new EffectiveAnalysisConfig(mode);
			try (var candidate = new JadxDecompiler(JadxEngineFactory.arguments(inputs, mapping, code, config));
					var fresh = new JadxDecompiler(JadxEngineFactory.arguments(inputs, mapping, code, config))) {
				candidate.load(); fresh.load();
				assertNotSame(candidate.getArgs().getCodeData(), fresh.getArgs().getCodeData());
				var expected = semantic(fresh); assertEquals(expected, semantic(candidate));
				writeDiagnostic("state-" + mode, Map.of("candidate", semantic(candidate), "fresh", expected));
				if (List.of("AUTO", "RESTRUCTURE").contains(mode)) {
					String java = expected.get("source:probe.Variables");
					for (String token : List.of("EditedVariables", "editedInstance", "editedField", "wideCount", "staticWide", "textArg", "guiValue", "native method comment", "mappedZero"))
						assertTrue(java.contains(token), token + " missing");
					assertTrue(expected.get("source:probe.VariableUnrelated").contains("mappedCold"));
					assertTrue(expected.get("source:probe.VariableUnrelated").contains("mapping method comment"));
				}
			}
		}
	}
	static JadxNodeRef node(String id) { return new JadxNodeRef(jadx.api.data.IJavaNodeRef.RefType.METHOD, "probe.Variables", id); }
	static void assertMemberTokens(JadxDecompiler engine, List<SymbolRef> family) {
		for (var ref : family) {
			var method = RelatedFixture.method(engine, ref);
			assertEquals(PropagatedNativeReplayTest.ALIAS, method.getName());
			int position = method.getMethodNode().getDefPosition(); assertTrue(position > 0);
			String java = method.getDeclaringClass().getTopParentClass().getCode();
			assertEquals(method.getName(), java.substring(position, position + method.getName().length()));
		}
	}
	static void writeDiagnostic(String name, Object value) throws Exception {
		Path reports = Path.of("build/safe-replay-probe"); Files.createDirectories(reports);
		Files.writeString(reports.resolve(name + ".json"), new com.fasterxml.jackson.databind.ObjectMapper()
				.writerWithDefaultPrettyPrinter().writeValueAsString(value));
	}

	@Test void replacementCannotPreserveAnUneditedAutomaticAliasWhenARenameRemovesACollision() throws Exception {
		var inputs = List.of(SymbolFixtureSupport.returnTypeClashJar(dir));
		var code = new JadxCodeData();
		code.setRenames(List.of(new JadxCodeRename(new JadxNodeRef(jadx.api.data.IJavaNodeRef.RefType.METHOD,
				"probe.ReturnClash", "value()I"), "intValueAlias")));
		String other = "method:" + new SymbolRef(SymbolRef.Kind.METHOD, "Lprobe/ReturnClash;", null, "value", "()Ljava/lang/String;");
		for (boolean hot : List.of(false, true)) {
			Map<String, String> before, reference;
			try (var old = open(inputs, null, new JadxCodeData()); var fresh = open(inputs, null, code)) {
				if (hot) semantic(old);
				before = semantic(old); reference = semantic(fresh);
				assertEquals("m0value", before.get(other), "Initial automatic collision alias");
				assertEquals("value", reference.get(other), "Fresh candidate recomputes automatic collision aliases");
			}
			for (var strategy : Strategy.values()) try (var engine = open(inputs, null, new JadxCodeData())) {
				if (hot) semantic(engine);
				Map<String, String> candidate;
				if (strategy == Strategy.REPLACEMENT) {
					try (var replacement = open(inputs, null, code)) { candidate = semantic(replacement); }
					assertEquals(reference, candidate);
					assertNotEquals(before.get(other), candidate.get(other), "Approved automatic alias recomputation");
				} else {
					replay(engine, code, strategy, List.of("probe.ReturnClash")); candidate = semantic(engine);
					assertEquals("m0value", candidate.get(other));
					assertNotEquals(reference, candidate, "Carrying old aliases is not fresh-engine equivalent");
				}
				Path reports = Path.of("build/safe-replay-probe"); Files.createDirectories(reports);
				Files.writeString(reports.resolve("collision-" + strategy + "-" + (hot ? "hot" : "cold") + ".json"),
						new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
								"strategy", strategy, "hot", hot, "untouchedOriginalKey", other, "before", before,
								"candidate", candidate, "fresh", reference, "gate", strategy == Strategy.REPLACEMENT
										? "FRESH_DERIVED_ALIAS_ACCEPTED" : "FAILED_FRESH_EQUIVALENCE")));
			}
		}
	}

	@Test void prepareCurrentServiceCollisionCounterexampleForNativeAndMatchingGuiReopen() throws Exception {
		Path root = Files.createDirectories(Path.of("build/safe-replay-gui-diagnostic").toAbsolutePath());
		Path input = SymbolFixtureSupport.returnTypeClashJar(root);
		Path path = root.resolve("diagnostic.jadx");
		var doc = NativeProjectDocument.newFromInputs(path, List.of(input));
		var nativeCode = new JadxCodeData();
		nativeCode.setRenames(List.of(new JadxCodeRename(new JadxNodeRef(jadx.api.data.IJavaNodeRef.RefType.METHOD,
				"probe.ReturnClash", "value()I"), "value")));
		nativeCode.setComments(List.of(new JadxCodeComment(JadxNodeRef.forCls("probe.ReturnClash"), "preserved native comment")));
		doc.setCodeData(nativeCode); var tree = doc.toJsonTree();
		tree.addProperty("futureRoot", "headless preserves");
		tree.getAsJsonObject("codeData").getAsJsonArray("renames").get(0).getAsJsonObject()
				.getAsJsonObject("nodeRef").addProperty("futureNode", "headless preserves nested");
		Files.writeString(path, tree.toString());
		var baseline = dev.libjadx.project.FileFingerprint.of(path);
		var inputHash = dev.libjadx.project.FileFingerprint.of(input);
		try (var runtime = new ProjectRuntime(path, List.of(input), List.of(root))) {
			runtime.initializeAsync(NativeProjectDocument.open(path)).get(20, java.util.concurrent.TimeUnit.SECONDS);
			var before = semantic(runtime.decompiler());
			var result = new EditBatchService(runtime).apply(new dev.libjadx.core.edits.EditDtos.Request(null, null, List.of(
					new dev.libjadx.core.edits.EditDtos.Operation(dev.libjadx.core.edits.EditDtos.Kind.RENAME,
							new SymbolRef(SymbolRef.Kind.METHOD, "Lprobe/ReturnClash;", null, "value", "()I"), "intValueAlias", null, null))));
			assertEquals("APPLIED", result.outcome()); assertEquals(1, result.logicalRevisionAfter()); assertEquals(1, result.indexRevisionAfter());
			assertEquals(baseline, dev.libjadx.project.FileFingerprint.of(path), "No autosave");
			String other = "method:" + new SymbolRef(SymbolRef.Kind.METHOD, "Lprobe/ReturnClash;", null, "value", "()Ljava/lang/String;");
			var hot = semantic(runtime.decompiler()); assertEquals("value", hot.get(other)); assertNotEquals(before.get(other), hot.get(other));
			runtime.saveProject(null, null);
			var saved = NativeProjectDocument.open(path);
			assertEquals("headless preserves", saved.toJsonTree().get("futureRoot").getAsString());
			assertEquals("headless preserves nested", saved.toJsonTree().getAsJsonObject("codeData").getAsJsonArray("renames").get(0)
					.getAsJsonObject().getAsJsonObject("nodeRef").get("futureNode").getAsString());
			try (var fresh = open(saved.getInputFiles(), saved.getMappingsPath(), saved.getCodeData())) {
				assertEquals("value", semantic(fresh).get(other));
				writeDiagnostic("collision-service-native", Map.of("before", before, "hotService", hot, "freshSaved", semantic(fresh)));
			}
		}
		assertEquals(inputHash, dev.libjadx.project.FileFingerprint.of(input));
	}

	@Test void actualMatchingGuiConfirmsAutomaticNonmemberAliasIsRecomputed() throws Exception {
		String path = System.getenv("LIBJADX_SAFE_REPLAY_GUI_DIAGNOSTIC");
		org.junit.jupiter.api.Assumptions.assumeTrue(path != null, "Run safeReplayGuiDiagnosticTest with matching Jadx 1.5.6 GUI");
		var doc = NativeProjectDocument.open(Path.of(path));
		assertEquals(1, doc.getCodeData().getRenames().size());
		assertEquals("value()I", doc.getCodeData().getRenames().getFirst().getNodeRef().getShortId());
		assertEquals("intValueAlias", doc.getCodeData().getRenames().getFirst().getNewName());
		assertFalse(doc.toJsonTree().has("futureRoot"), "Known matching GUI unknown-field loss");
		assertEquals("return-clash.jar", doc.toJsonTree().getAsJsonArray("files").get(0).getAsString());
		try (var fresh = open(doc.getInputFiles(), doc.getMappingsPath(), doc.getCodeData())) {
			var state = semantic(fresh);
			String java = state.get("source:probe.ReturnClash");
			assertTrue(java.contains("int intValueAlias()")); assertTrue(java.contains("String value()"));
			assertFalse(java.contains("m0value")); assertTrue(java.contains("preserved native comment"));
			writeDiagnostic("collision-gui-reopen", state);
		}
	}
}
