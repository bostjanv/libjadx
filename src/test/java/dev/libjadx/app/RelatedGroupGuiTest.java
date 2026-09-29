package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import dev.libjadx.core.hierarchy.RelatedHierarchyVerifier.VerificationBudget;
import dev.libjadx.probes.RelatedFixture;
import dev.libjadx.project.*;
import org.junit.jupiter.api.*;

/** Positive service-produced propagation, distinct from retained candidate/replay diagnostics. */
class RelatedGroupGuiTest {
	static final List<String> SEEDS = List.of("ExtendedLeft","Joined","SeparateLeft","SeparateRight");
	static void assertGroup(ProjectRuntime runtime) {
		RelatedGroupAdmissionTest.records(runtime,RelatedGroupAdmissionTest.FAMILY,RelatedGroupAdmissionTest.ALIAS);
		assertEquals(RelatedGroupAdmissionTest.FAMILY,runtime.verifyRelatedHierarchy(RelatedGroupAdmissionTest.SEED,VerificationBudget.defaults()).members());
		String source=runtime.decompiler().searchJavaClassByOrigFullName("related.Hierarchy").getCode();
		assertEquals(4,source.split("int renamedJoined\\(",-1).length-1,"Every original declaration token");
	}
	@Test void preparePositivePropagationFromEverySeedForMatchingGui() throws Exception {
		Path root=Path.of("build/propagated-edit-gui-fixture").toAbsolutePath();
		for(String seed:SEEDS) {
			Path caseRoot=root.resolve(seed); var fixture=ReplacementStateTest.fixture(caseRoot,true,true);
			Map<Path,FileFingerprint> hashes=new HashMap<>();
			for(Path p:fixture.inputs())hashes.put(p,FileFingerprint.of(p));hashes.put(fixture.project(),FileFingerprint.of(fixture.project()));hashes.put(fixture.mapping(),FileFingerprint.of(fixture.mapping()));
			try(var runtime=RelatedGroupAdmissionTest.open(fixture.project())) {
				SafeReplayStrategyTest.semantic(runtime.decompiler());
				new EditBatchService(runtime).apply(ReplacementStateTest.mixed(runtime));
				var result=new EditBatchService(runtime).apply(RelatedGroupAdmissionTest.request(runtime,
						RelatedGroupAdmissionTest.propagated(RelatedFixture.ref(seed,"joined","(I)I"),RelatedGroupAdmissionTest.ALIAS)));
				assertEquals(RelatedGroupAdmissionTest.FAMILY,result.items().getFirst().affectedRefs());
				assertGroup(runtime);ReplacementStateTest.assertOracle(runtime,fixture);
				for(var entry:hashes.entrySet())assertEquals(entry.getValue(),FileFingerprint.of(entry.getKey()),"No autosave");
				runtime.saveProject(null,null);
				var saved=NativeProjectDocument.open(fixture.project());
				assertEquals(4,saved.getCodeData().getRenames().stream().filter(r->r.getNodeRef().getShortId()!=null&&r.getNodeRef().getShortId().equals("joined(I)I")).count());
				assertEquals("preserved",saved.toJsonTree().get("futureRoot").getAsString());
				assertEquals("preserved",saved.toJsonTree().getAsJsonObject("codeData").get("futureCodeData").getAsString());
				assertEquals("state.tiny",saved.toJsonTree().get("mappingsPath").getAsString());
				assertFalse(Path.of(saved.toJsonTree().getAsJsonArray("files").get(0).getAsString()).isAbsolute());
			}
		}
	}
	@Test void actualMatchingGuiSaveAsPreservesEveryServiceProducedFamily() throws Exception {
		String root=System.getenv("LIBJADX_PROPAGATED_EDIT_GUI_ROOT");
		Assumptions.assumeTrue(root!=null,"Run propagatedEditGuiRoundTripTest with matching Jadx 1.5.6 GUI");
		for(String seed:SEEDS) {
			Path path=Path.of(root,seed,"gui-resaved.jadx");var saved=NativeProjectDocument.open(path);var original=NativeProjectDocument.open(path.resolveSibling("replacement.jadx"));
			assertTrue(NativeProjectDocument.codeDataEquivalent(original.getCodeData(),saved.getCodeData()));
			assertFalse(saved.toJsonTree().has("futureRoot"),"Matching GUI unknown-field loss, separately observed");
			assertFalse(saved.toJsonTree().getAsJsonObject("codeData").has("futureCodeData"));
			try(var runtime=RelatedGroupAdmissionTest.open(path)) {
				assertGroup(runtime);var actual=ReplacementStateTest.assertOracle(runtime,new ReplacementStateTest.Fixture(path,saved.getInputFiles(),saved.getMappingsPath()));
				try(var fresh=SafeReplayStrategyTest.open(original.getInputFiles(),original.getMappingsPath(),original.getCodeData())) { assertEquals(SafeReplayStrategyTest.semantic(fresh),actual); }
				assertTrue(actual.get("source:probe.VariableUnrelated").contains("attached method comment"));
				assertTrue(actual.get("source:probe.Variables").contains("guiValue"));
			}
		}
	}
}
