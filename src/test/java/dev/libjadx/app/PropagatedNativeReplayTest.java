package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import dev.libjadx.core.edits.EditDtos;
import dev.libjadx.core.hierarchy.RelatedHierarchyVerifier;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.jadxadapter.JadxSymbolAdapter;
import dev.libjadx.probes.HierarchyFixture;
import dev.libjadx.probes.RelatedFixture;
import dev.libjadx.project.NativeProjectDocument;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** PR #16 outcome B: explicit legacy records are diagnostic, never an admitted propagation API. */
class PropagatedNativeReplayTest {
	@TempDir Path dir;
	static final String ALIAS = "verifiedDiagnostic";
	static final List<SymbolRef> BRIDGE_NONMEMBERS = List.of(
			RelatedFixture.ref("CovariantLeaf", "value", "()Ljava/lang/Object;"),
			RelatedFixture.ref("CovariantLeaf", "value", "()Ljava/lang/String;"));

	@ParameterizedTest @ValueSource(strings = {"work", "call", "run", "joined", "inherited", "simple", "dex"})
	void completeFamilyRecordsExposeNonmemberReplayFailureDespiteCorrectMemberPersistence(String name) throws Exception {
		List<Path> inputs; SymbolRef seed;
		if (name.equals("dex")) {
			inputs = List.of(HierarchyFixture.dex(dir, "iface.dex", ".class public interface abstract Ldex/I;\n.super Ljava/lang/Object;\n.method public abstract f(I)I\n.end method\n"),
					HierarchyFixture.dex(dir, "impl.dex", ".class public Ldex/C;\n.super Ljava/lang/Object;\n.implements Ldex/I;\n.method public f(I)I\n.registers 2\nreturn p1\n.end method\n"));
			seed = method("dex/I", "f");
		} else if (List.of("inherited", "simple").contains(name)) {
			inputs = HierarchyFixture.visibility(dir);
			seed = method("hierarchy/p/Visibility$" + (name.equals("inherited") ? "Contract" : "Simple"), name);
		} else {
			inputs = RelatedFixture.compile(dir);
			seed = RelatedFixture.ref(switch (name) { case "work" -> "Base"; case "call" -> "Root"; case "run" -> "DefaultRoot"; default -> "Joined"; }, name, "(I)I");
		}
		boolean blocked = List.of("work", "call", "run", "joined").contains(name);
		for (boolean hot : List.of(false, true)) {
			Path path = dir.resolve(name + "-" + hot + ".jadx"); NativeProjectDocument.newFromInputs(path, inputs).save();
			Map<SymbolRef, String> before; List<SymbolRef> family;
			try (var runtime = open(path)) {
				var verified = runtime.verifyRelatedHierarchy(seed, RelatedHierarchyVerifier.VerificationBudget.defaults());
				assertEquals(RelatedHierarchyVerifier.Status.COMPLETE, verified.status()); family = verified.members();
				// A read-only control separates ordinary codegen alias changes from mutation replay.
				try (var control = open(path)) {
					generate(control); before = aliases(control, family);
					if (blocked) for (var ref : BRIDGE_NONMEMBERS) assertEquals("value", before.get(ref));
				}
				if (hot) generate(runtime);
				String disk = Files.readString(path);
				long initialRevision = runtime.projectSnapshot().revisions().logicalRevision();
				for (var member : family) {
					assertEquals(family, runtime.verifyRelatedHierarchy(member, RelatedHierarchyVerifier.VerificationBudget.defaults()).members());
					// Move each original seed to the first native record; authority remains independent.
					var result = explicitRecords(runtime, family, member, ALIAS);
					assertEquals(member.equals(family.getFirst()) ? "APPLIED" : "NO_CHANGE", result.outcome());
					assertTrue(result.items().stream().allMatch(item -> item.affectedRefs().isEmpty()), "Legacy receipts must not claim propagation");
					verify(runtime, family, ALIAS); generate(runtime);
					assertNonmembers(before, aliases(runtime, family), hot && blocked);
				}
				assertEquals(initialRevision + 1, runtime.projectSnapshot().revisions().logicalRevision());
				assertEquals(disk, Files.readString(path), "No autosave");
				var hotNonmembers = aliases(runtime, family);
				runtime.saveProject(null, null);
				assertRecords(path, family, ALIAS);
				try (var fresh = open(path)) {
					verify(fresh, family, ALIAS); generate(fresh);
					assertEquals(before, aliases(fresh, family), "Fresh native reopen restores original nonmember aliases");
					if (hot && blocked) assertNotEquals(hotNonmembers, aliases(fresh, family), "Required hot/fresh gate fails outside the family");
					Path reports = Files.createDirectories(Path.of("build/propagated-replay-probe"));
					Files.writeString(reports.resolve(name + "-" + (hot ? "hot" : "cold") + "-primary.java"), sources(runtime));
					Files.writeString(reports.resolve(name + "-" + (hot ? "hot" : "cold") + "-fresh.java"), sources(fresh));
					Files.writeString(reports.resolve(name + "-" + (hot ? "hot" : "cold") + ".json"), new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of(
							"family", family, "sourceGeneratedBeforeEdit", hot, "nativeRecordCount", family.size(),
							"readOnlyNonmembers", printable(before), "primaryNonmembers", printable(hotNonmembers),
							"freshNonmembers", printable(aliases(fresh, family)), "safetyGate", hot && blocked ? "FAILED_NONMEMBER_ALIAS" : "CONTROL_AGREES")));
				}
			}
		}
	}

	@Test void prepareServiceReplayFailureForActualMatchingGuiSaveAs() throws Exception {
		Path root = Path.of("build/propagated-replay-gui-fixture").toAbsolutePath();
		Files.createDirectories(root); List<Path> inputs = RelatedFixture.compile(root);
		for (boolean hot : List.of(false, true)) for (int position = 0; position < 4; position++) {
			Path path = Files.createDirectories(root.resolve((hot ? "hot" : "cold") + position)).resolve("diagnostic.jadx");
			NativeProjectDocument.newFromInputs(path, inputs).save();
			try (var runtime = open(path)) {
				var family = runtime.verifyRelatedHierarchy(RelatedFixture.ref("Joined", "joined", "(I)I"), RelatedHierarchyVerifier.VerificationBudget.defaults()).members();
				assertEquals(4, family.size());
				if (hot) generate(runtime);
				assertEquals("APPLIED", explicitRecords(runtime, family, family.get(position), ALIAS).outcome());
				verify(runtime, family, ALIAS); generate(runtime);
				for (var ref : BRIDGE_NONMEMBERS) assertEquals(hot ? "m0value" : "value", aliases(runtime, family).get(ref));
				runtime.saveProject(null, null); assertRecords(path, family, ALIAS);
			}
		}
	}

	@Test void actualMatchingGuiConfirmsMemberPersistenceAndHotNonmemberDisagreement() throws Exception {
		String root = System.getenv("LIBJADX_PROPAGATED_REPLAY_GUI_ROOT");
		Assumptions.assumeTrue(root != null, "Run propagatedEditReplayGuiDiagnosticTest with matching Jadx 1.5.6 GUI");
		for (boolean hot : List.of(false, true)) for (int position = 0; position < 4; position++) {
			Path path = Path.of(root, (hot ? "hot" : "cold") + position, "gui-resaved.jadx");
			try (var fresh = open(path)) {
				var verification = fresh.verifyRelatedHierarchy(RelatedFixture.ref("Joined", "joined", "(I)I"), RelatedHierarchyVerifier.VerificationBudget.defaults());
				assertEquals(RelatedHierarchyVerifier.Status.COMPLETE, verification.status()); assertEquals(4, verification.members().size());
				assertRecords(path, verification.members(), ALIAS); verify(fresh, verification.members(), ALIAS); generate(fresh);
				for (var ref : BRIDGE_NONMEMBERS) assertEquals("value", aliases(fresh, verification.members()).get(ref), "GUI/fresh disagrees with primary m0value");
			}
		}
	}

	private static void assertNonmembers(Map<SymbolRef, String> before, Map<SymbolRef, String> after, boolean blocked) {
		var changed = new java.util.HashSet<SymbolRef>();
		before.forEach((ref, alias) -> { if (!alias.equals(after.get(ref))) changed.add(ref); });
		assertEquals(blocked ? Set.copyOf(BRIDGE_NONMEMBERS) : Set.of(), changed);
		if (blocked) for (var ref : BRIDGE_NONMEMBERS) assertEquals("m0value", after.get(ref));
	}
	private static void assertRecords(Path path, List<SymbolRef> family, String alias) throws Exception {
		var records = NativeProjectDocument.open(path).getCodeData().getRenames();
		assertEquals(family.size(), records.size());
		for (var ref : family) assertEquals(1, records.stream().filter(record -> record.getNodeRef().getDeclaringClass().equals(ref.originalClassDescriptor().substring(1, ref.originalClassDescriptor().length() - 1).replace('/', '.'))
				&& record.getNodeRef().getShortId().equals(ref.originalName() + ref.originalDescriptor()) && record.getCodeRef() == null && record.getNewName().equals(alias)).count());
	}
	static Map<SymbolRef, String> aliases(ProjectRuntime runtime, List<SymbolRef> excluded) {
		var result = new HashMap<SymbolRef, String>();
		for (var cls : runtime.decompiler().getClassesWithInners()) for (var node : cls.getClassNode().getMethods()) {
			var ref = JadxSymbolAdapter.originalRef(node.getMethodInfo()); if (!excluded.contains(ref)) result.put(ref, node.getAlias());
		}
		return Map.copyOf(result);
	}
	private static Map<String, String> printable(Map<SymbolRef, String> aliases) {
		var result = new java.util.TreeMap<String, String>(); aliases.forEach((ref, alias) -> result.put(ref.toString(), alias)); return result;
	}
	private static EditDtos.Result explicitRecords(ProjectRuntime runtime, List<SymbolRef> family, SymbolRef first, String name) {
		var ordered = new java.util.ArrayList<>(family); ordered.remove(first); ordered.addFirst(first);
		var revision = runtime.projectSnapshot().revisions();
		return new EditBatchService(runtime).apply(new EditDtos.Request(revision.sessionId(), revision.logicalRevision(),
				ordered.stream().map(ref -> new EditDtos.Operation(EditDtos.Kind.RENAME, ref, name, null, null)).toList()));
	}
	private static ProjectRuntime open(Path path) throws Exception {
		var project = NativeProjectDocument.open(path);
		var runtime = new ProjectRuntime(path, project.getInputFiles());
		try { runtime.initializeAsync(project).get(20, TimeUnit.SECONDS); assertEquals("READY", runtime.status().state(), runtime.status().toString()); return runtime; }
		catch (Exception failure) { runtime.close(); throw failure; }
	}
	private static SymbolRef method(String owner, String name) { return new SymbolRef(SymbolRef.Kind.METHOD, "L" + owner + ";", null, name, "(I)I"); }
	private static void generate(ProjectRuntime runtime) { for (var cls : runtime.decompiler().getClasses()) cls.getCode(); }
	private static String sources(ProjectRuntime runtime) { return runtime.decompiler().getClasses().stream().map(cls -> cls.getCode()).collect(java.util.stream.Collectors.joining("\n")); }
	static void verify(ProjectRuntime runtime, List<SymbolRef> family, String alias) {
		for (var ref : family) {
			var cls = runtime.decompiler().getClassesWithInners().stream().filter(owner -> JadxSymbolAdapter.descriptor(owner.getRawName()).equals(ref.originalClassDescriptor())).findFirst().orElseThrow();
			String source = cls.getTopParentClass().getCode();
			var matches = cls.getClassNode().getMethods().stream().filter(node -> JadxSymbolAdapter.originalRef(node.getMethodInfo()).equals(ref)).toList();
			assertEquals(1, matches.size(), ref.toString()); var method = matches.getFirst(); assertEquals(alias, method.getAlias(), ref.toString());
			assertTrue(method.getDefPosition() > 0, ref.toString());
			assertEquals(alias, source.substring(method.getDefPosition(), method.getDefPosition() + alias.length()), ref.toString());
		}
	}
}
