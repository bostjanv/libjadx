package dev.libjadx.jadxadapter;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.libjadx.core.hierarchy.*;
import dev.libjadx.core.hierarchy.RelatedHierarchyVerifier.Status;
import dev.libjadx.core.hierarchy.RelatedHierarchyVerifier.VerificationBudget;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.probes.HierarchyFixture;
import dev.libjadx.probes.RelatedFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IndependentHierarchyVerifierTest {
	@TempDir Path dir;
	static SymbolRef method(String owner, String name, String descriptor) {
		return new SymbolRef(SymbolRef.Kind.METHOD, "L" + owner + ";", null, name, descriptor);
	}
	static List<SymbolRef> sorted(List<SymbolRef> family) { return family.stream().sorted(Comparator.comparing(SymbolRef::toString)).toList(); }
	static void family(RelatedHierarchyVerifier verifier, List<SymbolRef> expected) {
		for (SymbolRef seed : expected) {
			var result = verifier.verify(seed, VerificationBudget.defaults());
			assertEquals(Status.COMPLETE, result.status(), seed + " " + result.diagnostics());
			assertEquals(sorted(expected), result.members(), seed.toString());
			assertEquals(result, verifier.verify(seed, VerificationBudget.defaults()), "Stable immutable result");
		}
	}
	static void compareAndRecord(RelatedHierarchyVerifier verifier, jadx.api.JadxDecompiler engine, List<SymbolRef> refs) throws Exception {
		Map<SymbolRef, List<SymbolRef>> candidates = new HashMap<>();
		for (var seed : refs) candidates.put(seed, RelatedFixture.group(RelatedFixture.method(engine, seed)));
		var independent = verifier.verify(refs.getFirst(), VerificationBudget.defaults());
		var comparison = HierarchyComparison.compare(independent, candidates, Set.of());
		assertNotEquals(HierarchyComparison.Status.INDEPENDENT_INCOMPLETE, comparison.status());
		Path evidence = Path.of("build/hierarchy-probe/comparisons.txt"); java.nio.file.Files.createDirectories(evidence.getParent());
		java.nio.file.Files.writeString(evidence, "independent=" + independent.members() + " comparison=" + comparison.status()
				+ " differences=" + comparison.differences() + " candidates=" + candidates + System.lineSeparator(),
				java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
	}

	@ParameterizedTest @ValueSource(strings = {"work", "call", "run", "joined"})
	void localFamiliesAreExactAndSeedIndependentAndComparedWithPinnedCandidates(String name) throws Exception {
		var inputs = RelatedFixture.compile(dir); var capture = JadxInputCensusAdapter.capture(inputs, CensusLimits.defaults());
		List<String> owners = switch (name) {
			case "work" -> List.of("Base", "Middle", "Leaf", "Sibling", "Inner");
			case "call" -> List.of("Root", "Left", "Right", "Diamond", "Implementation");
			case "run" -> List.of("DefaultRoot", "DefaultImplementation");
			default -> List.of("SeparateLeft", "ExtendedLeft", "SeparateRight", "Joined");
		};
		var expected = owners.stream().map(owner -> RelatedFixture.ref(owner, name, "(I)I")).toList();
		try (var engine = RelatedFixture.open(inputs)) {
			var verifier = capture.bind(engine); family(verifier, expected);
			compareAndRecord(verifier, engine, expected);
			Map<SymbolRef, List<SymbolRef>> candidates = new HashMap<>();
			for (SymbolRef seed : expected) candidates.put(seed, RelatedFixture.group(RelatedFixture.method(engine, seed)));
			var comparison = HierarchyComparison.compare(verifier.verify(expected.getFirst(), VerificationBudget.defaults()), candidates, Set.of());
			if (name.equals("joined")) {
				var incomplete = sorted(List.of(RelatedFixture.ref("SeparateLeft", name, "(I)I"),
						RelatedFixture.ref("ExtendedLeft", name, "(I)I"), RelatedFixture.ref("Joined", name, "(I)I")));
				for (var seed : incomplete) assertEquals(incomplete, candidates.get(seed));
				assertEquals(List.of(), candidates.get(RelatedFixture.ref("SeparateRight", name, "(I)I")));
				assertEquals(HierarchyComparison.Status.JADX_CANDIDATE_MISMATCH, comparison.status());
				assertEquals(Set.of(HierarchyComparison.Difference.MISSING_MEMBERS, HierarchyComparison.Difference.SEED_DEPENDENT), comparison.differences());
			} else assertEquals(HierarchyComparison.Status.EXACT_AGREEMENT, comparison.status());
			assertTrue(engine.getRoot().getClasses().stream().allMatch(c -> c.getState() == jadx.core.dex.nodes.ProcessState.NOT_LOADED));
		}
	}

	@Test void privateStaticSpecialOverloadsAndUnrelatedSameNameDoNotLeak() throws Exception {
		var inputs = RelatedFixture.compile(dir); var capture = JadxInputCensusAdapter.capture(inputs, CensusLimits.defaults());
		try (var engine = RelatedFixture.open(inputs)) {
			var verifier = capture.bind(engine);
			for (String owner : List.of("Base", "Middle")) for (String name : List.of("hidden", "hiding"))
				assertEquals(Status.UNSUPPORTED_METHOD, verifier.verify(RelatedFixture.ref(owner, name, "(I)I"), VerificationBudget.defaults()).status());
			assertEquals(Status.UNSUPPORTED_METHOD, verifier.verify(RelatedFixture.ref("Middle", "<init>", "()V"), VerificationBudget.defaults()).status());
			family(verifier, List.of(RelatedFixture.ref("Base", "work", "(Ljava/lang/String;)I")));
			family(verifier, List.of(RelatedFixture.ref("Unrelated", "work", "(I)I")));
			assertEquals(Status.NOT_FOUND, verifier.verify(RelatedFixture.ref("Leaf", "absent", "()V"), VerificationBudget.defaults()).status());
			assertEquals(Status.NOT_FOUND, verifier.verify(RelatedFixture.ref("Absent", "work", "(I)I"), VerificationBudget.defaults()).status());
			assertEquals(Status.UNSUPPORTED_METHOD, verifier.verify(SymbolRef.classRef("Lrelated/Hierarchy$Base;"), VerificationBudget.defaults()).status());
		}
	}

	@Test void siblingsWithoutSharedDeclarationStaySeparateAndObjectBoundaryIsExplicit() throws Exception {
		Path jar = HierarchyFixture.javaJar(dir, "separate.jar", Map.of("separate/C.java",
				"package separate; public class C { public static class Base { } public static class Left extends Base { public int f(int n){return n;} } "
				+ "public static class Right extends Base { public int f(int n){return n;} } public String toString(){return \"owned\";} }"));
		var raw = JadxInputCensusAdapter.capture(List.of(jar), CensusLimits.defaults());
		try (var engine = RelatedFixture.open(List.of(jar))) {
			var verifier = raw.bind(engine);
			for (String owner : List.of("separate/C$Left", "separate/C$Right")) family(verifier, List.of(method(owner, "f", "(I)I")));
			assertEquals(Status.EXTERNAL_SUPERTYPE, verifier.verify(method("separate/C", "toString", "()Ljava/lang/String;"), VerificationBudget.defaults()).status());
		}
	}

	@Test void duplicateInterfaceBranchAndMissingDescendantBranchCannotBeTreatedAsEnds() throws Exception {
		Path first = HierarchyFixture.javaJar(dir.resolve("first"), "one.jar", Map.of("branch/C.java",
				"package branch; interface I { int f(int n); } public class C implements I { public int f(int n){return n;} }"));
		Path second = HierarchyFixture.javaJar(dir.resolve("second"), "two.jar", Map.of("branch/I.java",
				"package branch; public interface I { default int f(int n){return n;} }"));
		var raw = JadxInputCensusAdapter.capture(List.of(first, second), CensusLimits.defaults());
		try (var engine = RelatedFixture.open(List.of(first, second))) {
			assertEquals(Status.AMBIGUOUS_INPUT, raw.bind(engine).verify(method("branch/C", "f", "(I)I"), VerificationBudget.defaults()).status());
		}
		Path child = HierarchyFixture.dex(dir, "missing.dex", ".class public Lbranch/Child;\n.super Lbranch/C;\n.implements Lbranch/Absent;\n.method public f(I)I\n.registers 2\nreturn p1\n.end method\n");
		raw = JadxInputCensusAdapter.capture(List.of(first, child), CensusLimits.defaults());
		try (var engine = RelatedFixture.open(List.of(first, child))) {
			assertEquals(Status.MISSING_SUPERTYPE, raw.bind(engine).verify(method("branch/C", "f", "(I)I"), VerificationBudget.defaults()).status());
		}
	}

	@Test void comparisonDistinguishesExtraAndBridgeDifferencesWithoutMakingThemAuthority() {
		var seed = method("compare/A", "f", "()I"); var extra = method("compare/B", "f", "()I");
		var complete = new RelatedHierarchyVerifier.Verification(Status.COMPLETE, seed, List.of(seed), List.of());
		var comparison = HierarchyComparison.compare(complete, Map.of(seed, List.of(seed, extra)), Set.of(extra));
		assertEquals(Set.of(HierarchyComparison.Difference.EXTRA_MEMBERS, HierarchyComparison.Difference.BRIDGE_SYNTHETIC), comparison.differences());
		assertEquals(List.of(extra), comparison.extra()); assertEquals(List.of(), comparison.missing());
	}

	@Test void packageVisibilityProtectedFinalSimpleAndInheritedInterfaceContextsAcrossInputs() throws Exception {
		var inputs = HierarchyFixture.visibility(dir); var capture = JadxInputCensusAdapter.capture(inputs, CensusLimits.defaults());
		try (var engine = RelatedFixture.open(inputs)) {
			var verifier = capture.bind(engine);
			family(verifier, List.of(method("hierarchy/p/Visibility$Base", "packageMethod", "(I)I"),
					method("hierarchy/p/Visibility$Same", "packageMethod", "(I)I")));
			family(verifier, List.of(method("hierarchy/q/Other", "packageMethod", "(I)I")));
			family(verifier, List.of(method("hierarchy/p/Visibility$Base", "protectedMethod", "(I)I"),
					method("hierarchy/p/Visibility$FinalLeaf", "protectedMethod", "(I)I"), method("hierarchy/q/Other", "protectedMethod", "(I)I")));
			family(verifier, List.of(method("hierarchy/p/Visibility$Base", "finalMethod", "(I)I")));
			family(verifier, List.of(method("hierarchy/p/Visibility$Base", "overload", "(I)I")));
			family(verifier, List.of(method("hierarchy/p/Visibility$Base", "overload", "(Ljava/lang/String;)I")));
			family(verifier, List.of(method("hierarchy/p/Visibility$Contract", "inherited", "(I)I"),
					method("hierarchy/p/Visibility$ImplementationBase", "inherited", "(I)I")));
			family(verifier, List.of(method("hierarchy/p/Visibility$Simple", "simple", "(I)I"),
					method("hierarchy/p/Visibility$SimpleImpl", "simple", "(I)I")));
			for (var seed : List.of(method("hierarchy/p/Visibility$Base", "packageMethod", "(I)I"),
					method("hierarchy/q/Other", "packageMethod", "(I)I"), method("hierarchy/p/Visibility$Base", "protectedMethod", "(I)I"),
					method("hierarchy/p/Visibility$Base", "finalMethod", "(I)I"), method("hierarchy/p/Visibility$Base", "overload", "(I)I"),
					method("hierarchy/p/Visibility$Base", "overload", "(Ljava/lang/String;)I"),
					method("hierarchy/p/Visibility$Contract", "inherited", "(I)I"), method("hierarchy/p/Visibility$Simple", "simple", "(I)I")))
				compareAndRecord(verifier, engine, verifier.verify(seed, VerificationBudget.defaults()).members());
		}
	}

	@Test void covarianceGenericBridgesAndSyntheticSeedsFailClosedWithFullOriginalIdentities() throws Exception {
		var inputs = RelatedFixture.compile(dir); var capture = JadxInputCensusAdapter.capture(inputs, CensusLimits.defaults());
		try (var engine = RelatedFixture.open(inputs)) {
			var verifier = capture.bind(engine);
			for (var seed : List.of(RelatedFixture.ref("CovariantBase", "value", "()Ljava/lang/Object;"),
					RelatedFixture.ref("CovariantLeaf", "value", "()Ljava/lang/String;"), RelatedFixture.ref("CovariantLeaf", "value", "()Ljava/lang/Object;"))) {
				assertEquals(Status.UNSUPPORTED_METHOD, verifier.verify(seed, VerificationBudget.defaults()).status());
				assertEquals(3, RelatedFixture.group(RelatedFixture.method(engine, seed)).size());
			}
		}
		var visibility = HierarchyFixture.visibility(dir); var raw = JadxInputCensusAdapter.capture(visibility, CensusLimits.defaults());
		try (var engine = RelatedFixture.open(visibility)) {
			var verifier = raw.bind(engine);
			for (String owner : List.of("hierarchy/p/Visibility$Generic", "hierarchy/p/Visibility$GenericImpl"))
				assertEquals(Status.UNSUPPORTED_METHOD, verifier.verify(method(owner, "generic", "(Ljava/lang/Object;)Ljava/lang/Object;"), VerificationBudget.defaults()).status());
			assertEquals(Status.UNSUPPORTED_METHOD, verifier.verify(method("hierarchy/p/Visibility$GenericImpl", "generic", "(Ljava/lang/String;)Ljava/lang/String;"), VerificationBudget.defaults()).status());
			var synthetic = raw.census().classesByDescriptor().get("Lhierarchy/p/Visibility$Synthetic;").getFirst().methods().stream()
					.filter(m -> m.has(InputCensus.Access.SYNTHETIC)).findFirst().orElseThrow();
			assertEquals(Status.UNSUPPORTED_METHOD, verifier.verify(synthetic.ref(), VerificationBudget.defaults()).status());
		}
	}

	@Test void missingExternalAndDuplicatesPreventCompletenessEvenOnNonSeedBranches() throws Exception {
		var inputs = new ArrayList<>(RelatedFixture.compile(dir)); var raw = JadxInputCensusAdapter.capture(inputs, CensusLimits.defaults());
		try (var engine = RelatedFixture.open(inputs)) {
			var verifier = raw.bind(engine);
			assertEquals(Status.MISSING_SUPERTYPE, verifier.verify(RelatedFixture.ref("MissingParent", "lost", "(I)I"), VerificationBudget.defaults()).status());
			assertEquals(Status.EXTERNAL_SUPERTYPE, verifier.verify(RelatedFixture.ref("External", "run", "()V"), VerificationBudget.defaults()).status());
		}
		inputs.add(RelatedFixture.duplicateLeaf(dir)); raw = JadxInputCensusAdapter.capture(inputs, CensusLimits.defaults());
		try (var engine = RelatedFixture.open(inputs)) {
			var verifier = raw.bind(engine);
			for (String owner : List.of("Base", "Middle", "Leaf", "Sibling", "Inner")) {
				var result = verifier.verify(RelatedFixture.ref(owner, "work", "(I)I"), VerificationBudget.defaults());
				assertEquals(Status.AMBIGUOUS_INPUT, result.status()); assertEquals(List.of(), result.members());
				assertEquals(HierarchyComparison.Status.INDEPENDENT_INCOMPLETE, HierarchyComparison.compare(result, Map.of(), Set.of()).status());
			}
		}
		var visibility = HierarchyFixture.visibility(dir); raw = JadxInputCensusAdapter.capture(visibility, CensusLimits.defaults());
		try (var engine = RelatedFixture.open(visibility)) {
			assertEquals(Status.MISSING_SUPERTYPE, raw.bind(engine).verify(method("hierarchy/p/Visibility$MissingInterface", "missingInterface", "(I)I"), VerificationBudget.defaults()).status());
		}
	}

	@Test void dexInterfaceFamilyIsVerifiedFromEverySeedWithoutSource() throws Exception {
		Path iface = HierarchyFixture.dex(dir, "iface.dex", ".class public interface abstract Ldex/I;\n.super Ljava/lang/Object;\n.method public abstract f(I)I\n.end method\n");
		Path impl = HierarchyFixture.dex(dir, "impl.dex", ".class public Ldex/C;\n.super Ljava/lang/Object;\n.implements Ldex/I;\n.method public f(I)I\n.registers 2\nreturn p1\n.end method\n");
		var inputs = List.of(iface, impl); var raw = JadxInputCensusAdapter.capture(inputs, CensusLimits.defaults());
		assertEquals(Status.COMPLETE, raw.status());
		try (var engine = RelatedFixture.open(inputs)) {
			family(raw.bind(engine), List.of(method("dex/I", "f", "(I)I"), method("dex/C", "f", "(I)I")));
			compareAndRecord(raw.bind(engine), engine, List.of(method("dex/I", "f", "(I)I"), method("dex/C", "f", "(I)I")));
			assertTrue(engine.getRoot().getClasses().stream().allMatch(c -> c.getState() == jadx.core.dex.nodes.ProcessState.NOT_LOADED));
		}
	}

	@ParameterizedTest @ValueSource(ints = {63, 64})
	void family64SucceedsAnd65IsNeverTruncated(int implementations) throws Exception {
		StringBuilder source = new StringBuilder("package many; public class Many { public interface Root {int f(int n);} ");
		for (int i = 0; i < implementations; i++) source.append("public static class C").append(i)
				.append(" implements Root { public int f(int n){return n;} }");
		source.append('}'); Path jar = HierarchyFixture.javaJar(dir, "many.jar", Map.of("many/Many.java", source.toString()));
		var inputs = List.of(jar); var raw = JadxInputCensusAdapter.capture(inputs, CensusLimits.defaults());
		try (var engine = RelatedFixture.open(inputs)) {
			var verifier = raw.bind(engine); var seed = method("many/Many$Root", "f", "(I)I");
			var result = verifier.verify(seed, VerificationBudget.defaults());
			assertEquals(implementations == 63 ? Status.COMPLETE : Status.RESOURCE_LIMIT, result.status());
			assertEquals(implementations == 63 ? 64 : 0, result.members().size());
			if (implementations == 63) family(verifier, result.members());
		}
	}

	@Test void familyNodeAndWorkBudgetsHaveExactDeterministicBoundaries() throws Exception {
		var inputs = RelatedFixture.compile(dir); var raw = JadxInputCensusAdapter.capture(inputs, CensusLimits.defaults());
		try (var engine = RelatedFixture.open(inputs)) {
			var verifier = raw.bind(engine); var seed = RelatedFixture.ref("Base", "work", "(I)I");
			assertEquals(Status.COMPLETE, verifier.verify(seed, new VerificationBudget(5, 5, 200_000)).status());
			for (var budget : List.of(new VerificationBudget(4, 5, 200_000), new VerificationBudget(5, 4, 200_000), new VerificationBudget(5, 5, 1))) {
				var result = verifier.verify(seed, budget); assertEquals(Status.RESOURCE_LIMIT, result.status()); assertTrue(result.members().isEmpty());
			}
			long low = 1, high = 200_000;
			while (low < high) {
				long mid = (low + high) / 2;
				if (verifier.verify(seed, new VerificationBudget(5, 5, mid)).status() == Status.COMPLETE) high = mid;
				else low = mid + 1;
			}
			assertEquals(Status.COMPLETE, verifier.verify(seed, new VerificationBudget(5, 5, low)).status());
			assertEquals(Status.RESOURCE_LIMIT, verifier.verify(seed, new VerificationBudget(5, 5, low - 1)).status());
		}
		assertThrows(IllegalArgumentException.class, () -> new VerificationBudget(65, 1, 1));
	}

	@Test void immutableGraphClassifiesEveryForwardAndReverseEdgeIncludingDivergentDuplicates() {
		var origin = new InputCensus.InputOrigin(0, "internal");
		var a = new InputCensus.ClassRecord("La;", origin, "Lb;", List.of("Lexternal;", "Lmissing;"), 1, List.of());
		var b1 = new InputCensus.ClassRecord("Lb;", origin, null, List.of(), 1, List.of());
		var b2 = new InputCensus.ClassRecord("Lb;", origin, null, List.of("Lc;"), 1, List.of());
		var c = new InputCensus.ClassRecord("Lc;", origin, null, List.of(), 1, List.of());
		var graph = new HierarchyGraph(new InputCensus(List.of(origin), Map.of("La;", List.of(a), "Lb;", List.of(b1, b2), "Lc;", List.of(c))), Set.of("Lexternal;"), Set.of());
		assertEquals(List.of(HierarchyGraph.Target.IN_CENSUS_DUPLICATE, HierarchyGraph.Target.EXTERNAL_CLASSPATH, HierarchyGraph.Target.MISSING), graph.parents("La;").stream().map(HierarchyGraph.Edge::classification).toList());
		assertEquals(HierarchyGraph.Target.IN_CENSUS_UNIQUE, graph.parents("Lb;").getFirst().classification());
		assertEquals(List.of("Lb;"), graph.children("Lc;"));
		assertThrows(UnsupportedOperationException.class, () -> graph.children("Lc;").clear());
	}

	@Test void cyclicFinalAndCovariantUnbridgedBytecodeNeverClaimsComplete() {
		var origin = new InputCensus.InputOrigin(0, "internal");
		var seed = method("a/A", "f", "()I"); var child = method("a/B", "f", "()I");
		var a = new InputCensus.ClassRecord(seed.originalClassDescriptor(), origin, child.originalClassDescriptor(), List.of(), 1, List.of(new InputCensus.MethodRecord(seed, 1)));
		var b = new InputCensus.ClassRecord(child.originalClassDescriptor(), origin, seed.originalClassDescriptor(), List.of(), 1, List.of(new InputCensus.MethodRecord(child, 1)));
		var graph = new HierarchyGraph(new InputCensus(List.of(origin), Map.of(a.descriptor(), List.of(a), b.descriptor(), List.of(b))), Set.of(), Set.of());
		assertEquals(Status.INVALID_HIERARCHY, new IndependentHierarchyVerifier(graph).verify(seed, VerificationBudget.defaults()).status());
		a = new InputCensus.ClassRecord(a.descriptor(), origin, null, List.of(), 1, List.of(new InputCensus.MethodRecord(seed, 17)));
		graph = new HierarchyGraph(new InputCensus(List.of(origin), Map.of(a.descriptor(), List.of(a), b.descriptor(), List.of(b))), Set.of(), Set.of());
		assertEquals(Status.INVALID_HIERARCHY, new IndependentHierarchyVerifier(graph).verify(child, VerificationBudget.defaults()).status());
		for (int flags : List.of(InputCensus.Access.PRIVATE, InputCensus.Access.PUBLIC | InputCensus.Access.STATIC)) {
			var parent = new InputCensus.ClassRecord(a.descriptor(), origin, null, List.of(), 1, List.of(new InputCensus.MethodRecord(seed, 1)));
			var hidden = new InputCensus.ClassRecord(b.descriptor(), origin, parent.descriptor(), List.of(), 1, List.of(new InputCensus.MethodRecord(child, flags)));
			var invalid = new HierarchyGraph(new InputCensus(List.of(origin), Map.of(parent.descriptor(), List.of(parent), hidden.descriptor(), List.of(hidden))), Set.of(), Set.of());
			assertEquals(Status.INVALID_HIERARCHY, new IndependentHierarchyVerifier(invalid).verify(seed, VerificationBudget.defaults()).status());
		}
		a = new InputCensus.ClassRecord(a.descriptor(), origin, null, List.of(), 17, List.of(new InputCensus.MethodRecord(seed, 1)));
		graph = new HierarchyGraph(new InputCensus(List.of(origin), Map.of(a.descriptor(), List.of(a), b.descriptor(), List.of(b))), Set.of(), Set.of());
		assertEquals(Status.INVALID_HIERARCHY, new IndependentHierarchyVerifier(graph).verify(child, VerificationBudget.defaults()).status());
		a = new InputCensus.ClassRecord(a.descriptor(), origin, null, List.of(), 1, List.of(new InputCensus.MethodRecord(method("a/A", "f", "()Ljava/lang/Object;"), 1)));
		b = new InputCensus.ClassRecord(b.descriptor(), origin, a.descriptor(), List.of(), 1, List.of(new InputCensus.MethodRecord(method("a/B", "f", "()Ljava/lang/String;"), 1)));
		graph = new HierarchyGraph(new InputCensus(List.of(origin), Map.of(a.descriptor(), List.of(a), b.descriptor(), List.of(b))), Set.of(), Set.of());
		assertEquals(Status.UNSUPPORTED_METHOD, new IndependentHierarchyVerifier(graph).verify(b.methods().getFirst().ref(), VerificationBudget.defaults()).status());
	}
}
