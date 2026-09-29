package dev.libjadx.probes;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import dev.libjadx.core.symbols.SymbolRef;
import jadx.core.dex.attributes.AFlag;
import jadx.core.dex.attributes.AType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JadxRelatedMethodProbeTest {
	@TempDir Path dir;

	@Test void recordOriginalCensusAndCandidateGroupsWithoutGeneratingUnrelatedJava() throws Exception {
		var inputs = RelatedFixture.compile(dir);
		try (var engine = RelatedFixture.open(inputs)) {
			var cold = RelatedFixture.method(engine, RelatedFixture.ref("Unrelated", "work", "(I)I"))
					.getDeclaringClass().getClassNode();
			var state = cold.getState();
			var evidence = new ArrayList<String>();
			for (var cls : engine.getClassesWithInners()) {
				for (var node : cls.getClassNode().getMethods()) {
					var ref = dev.libjadx.jadxadapter.JadxSymbolAdapter.originalRef(node.getMethodInfo());
					var method = (jadx.api.JavaMethod) engine.getJavaNodeByRef(node);
					evidence.add(ref + " => " + RelatedFixture.group(method) + " flags=" + node.getAccessFlags()
							+ " dontRename=" + node.contains(AFlag.DONT_RENAME));
				}
			}
			Files.createDirectories(Path.of("build/related-probe"));
			Files.write(Path.of("build/related-probe/original-groups.txt"), evidence);
			assertEquals(state, cold.getState());
			assertEquals(jadx.core.dex.nodes.ProcessState.NOT_LOADED, cold.getState());
		}
	}

	@Test void simpleChainAndDefaultGroupsAreSeedIndependentButDoNotProveGeneralCompleteness() throws Exception {
		try (var engine = RelatedFixture.open(RelatedFixture.compile(dir))) {
			var chain = List.of("Base", "Middle", "Leaf", "Sibling", "Inner").stream()
					.map(owner -> RelatedFixture.ref(owner, "work", "(I)I"))
					.sorted(java.util.Comparator.comparing(Object::toString)).toList();
			for (var seed : chain) assertEquals(chain, RelatedFixture.group(RelatedFixture.method(engine, seed)));
			var defaults = List.of(RelatedFixture.ref("DefaultRoot", "run", "(I)I"),
					RelatedFixture.ref("DefaultImplementation", "run", "(I)I"))
					.stream().sorted(java.util.Comparator.comparing(Object::toString)).toList();
			for (var seed : defaults) assertEquals(defaults, RelatedFixture.group(RelatedFixture.method(engine, seed)));
			for (String owner : List.of("Base", "Middle")) {
				for (String name : List.of("hidden", "hiding"))
					assertTrue(RelatedFixture.group(RelatedFixture.method(engine, RelatedFixture.ref(owner, name, "(I)I"))).isEmpty());
			}
			assertTrue(RelatedFixture.group(RelatedFixture.method(engine, RelatedFixture.ref("Base", "work", "(Ljava/lang/String;)I"))).isEmpty());
			assertTrue(RelatedFixture.group(RelatedFixture.method(engine, RelatedFixture.ref("Unrelated", "work", "(I)I"))).isEmpty());
		}
	}

	@Test void missingParentHasNoCompletenessOrDontRenameMarker() throws Exception {
		try (var engine = RelatedFixture.open(RelatedFixture.compile(dir))) {
			var method = RelatedFixture.method(engine, RelatedFixture.ref("MissingParent", "lost", "(I)I"));
			assertTrue(RelatedFixture.group(method).isEmpty());
			assertNull(method.getMethodNode().get(AType.METHOD_OVERRIDE));
			assertFalse(method.getMethodNode().contains(AFlag.DONT_RENAME));
			assertNull(engine.getRoot().resolveClass(method.getDeclaringClass().getClassNode().getSuperClass()));
			var external = RelatedFixture.method(engine, RelatedFixture.ref("External", "run", "()V"));
			assertTrue(external.getMethodNode().contains(AFlag.DONT_RENAME));
			assertTrue(external.getMethodNode().get(AType.METHOD_OVERRIDE).getOverrideList().stream()
					.anyMatch(m -> !(m instanceof jadx.core.dex.nodes.MethodNode)));
		}
	}

	@Test void closedLocalInterfaceBranchesAreIncompleteAndRenameLeavesOneBranchUnchanged() throws Exception {
		try (var engine = RelatedFixture.open(RelatedFixture.compile(dir))) {
			var expectedCandidates = List.of("SeparateLeft", "ExtendedLeft", "Joined").stream()
					.map(owner -> RelatedFixture.ref(owner, "joined", "(I)I"))
					.sorted(java.util.Comparator.comparing(Object::toString)).toList();
			for (var seed : expectedCandidates) {
				var method = RelatedFixture.method(engine, seed);
				assertEquals(expectedCandidates, RelatedFixture.group(method));
				assertFalse(method.getMethodNode().contains(AFlag.DONT_RENAME));
				assertTrue(method.getMethodNode().get(AType.METHOD_OVERRIDE).getOverrideList().stream()
						.allMatch(m -> m instanceof jadx.core.dex.nodes.MethodNode));
			}
			var right = RelatedFixture.method(engine, RelatedFixture.ref("SeparateRight", "joined", "(I)I"));
			assertTrue(RelatedFixture.group(right).isEmpty());
			assertFalse(right.getMethodNode().contains(AFlag.DONT_RENAME));
			var joined = RelatedFixture.method(engine, RelatedFixture.ref("Joined", "joined", "(I)I"));
			for (var iface : joined.getDeclaringClass().getClassNode().getInterfaces())
				assertNotNull(engine.getRoot().resolveClass(iface), "Both original branches are loaded");
			joined.getMethodNode().rename("renamedJoined");
			for (var seed : expectedCandidates) assertEquals("renamedJoined", RelatedFixture.method(engine, seed).getName());
			assertEquals("joined", right.getName(), "A candidate-only rename silently leaves a relevant declaration behind");
		}
	}

	@Test void sharedRootDiamondWorksFromEverySeedAndCovariantGroupIncludesUneditableBridge() throws Exception {
		try (var engine = RelatedFixture.open(RelatedFixture.compile(dir))) {
			var diamond = List.of("Root", "Left", "Right", "Diamond", "Implementation").stream()
					.map(owner -> RelatedFixture.ref(owner, "call", "(I)I"))
					.sorted(java.util.Comparator.comparing(Object::toString)).toList();
			for (var seed : diamond) assertEquals(diamond, RelatedFixture.group(RelatedFixture.method(engine, seed)));
			var covariance = List.of(RelatedFixture.ref("CovariantBase", "value", "()Ljava/lang/Object;"),
					RelatedFixture.ref("CovariantLeaf", "value", "()Ljava/lang/Object;"),
					RelatedFixture.ref("CovariantLeaf", "value", "()Ljava/lang/String;"))
					.stream().sorted(java.util.Comparator.comparing(Object::toString)).toList();
			for (var seed : covariance) assertEquals(covariance, RelatedFixture.group(RelatedFixture.method(engine, seed)));
			var bridge = RelatedFixture.method(engine, RelatedFixture.ref("CovariantLeaf", "value", "()Ljava/lang/Object;"));
			assertTrue(bridge.getAccessFlags().isBridge()); assertTrue(bridge.getAccessFlags().isSynthetic());
			assertTrue(dev.libjadx.jadxadapter.JadxNativeEditAdapter.memberTargets(bridge.getDeclaringClass()).stream()
					.noneMatch(t -> t.ref().equals(RelatedFixture.ref("CovariantLeaf", "value", "()Ljava/lang/Object;")) && t.editable()));
		}
	}

	@Test void duplicateOriginalInputsHaveSameVisibleGroupAndHideADeclaration() throws Exception {
		var inputs = RelatedFixture.compile(dir);
		var seed = RelatedFixture.ref("Leaf", "work", "(I)I");
		List<SymbolRef> baseline;
		try (var engine = RelatedFixture.open(inputs)) { baseline = RelatedFixture.group(RelatedFixture.method(engine, seed)); }
		var duplicates = new ArrayList<>(inputs); duplicates.add(RelatedFixture.duplicateLeaf(dir));
		try (var engine = RelatedFixture.open(duplicates)) {
			assertEquals(baseline, RelatedFixture.group(RelatedFixture.method(engine, seed)),
					"Candidate group carries no warning about a discarded original definition");
			var visible = engine.getClassesWithInners().stream().filter(c -> c.getRawName().equals("related.Hierarchy$Leaf")).toList();
			assertEquals(1, visible.size());
			assertTrue(visible.getFirst().getClassNode().getMethods().stream()
					.noneMatch(m -> m.getMethodInfo().getName().equals("onlyInDiscardedInput")));
			assertNull(dev.libjadx.jadxadapter.JadxSymbolAdapter.originalRef(RelatedFixture.method(engine, seed)).inputIdentity());
		}
	}
}
