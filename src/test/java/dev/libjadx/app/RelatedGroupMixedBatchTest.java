package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;
import static dev.libjadx.app.RelatedGroupAdmissionTest.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import dev.libjadx.core.edits.EditDtos;
import dev.libjadx.core.hierarchy.RelatedHierarchyVerifier.Status;
import dev.libjadx.core.hierarchy.RelatedHierarchyVerifier.VerificationBudget;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.probes.HierarchyFixture;
import dev.libjadx.probes.RelatedFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RelatedGroupMixedBatchTest {

	@TempDir Path dir;
	static final SymbolRef P_FOO = method("mixed/P", "foo", "(I)I");
	static final SymbolRef C_FOO = method("mixed/C", "foo", "(I)I");
	static final SymbolRef B_BAR = method("mixed/B", "bar", "(I)I");
	static final SymbolRef C_BAR = method("mixed/C", "bar", "(I)I");
	static final List<SymbolRef> BAR_FAMILY = List.of(B_BAR, C_BAR);

	static Path fixture(Path root) throws Exception {
		return HierarchyFixture.javaJar(root, "mixed.jar", Map.of(
				"mixed/P.java", "package mixed; public class P { public int data; public int foo(int v) { return v; } }",
				"mixed/B.java", "package mixed; public interface B { int bar(int v); }",
				"mixed/C.java", "package mixed; public class C extends P implements B { "
						+ "public int foo(int v) { return v + 1; } public int bar(int v) { return v + 2; } }"));
	}

	@ParameterizedTest
	@CsvSource({"false,false", "false,true", "true,false", "true,true"})
	void sharedOwnerImplicitRenameCannotCollideWithPropagatedGroup(boolean hot, boolean groupFirst) throws Exception {
		try (var runtime = new ProjectRuntime(null, List.of(fixture(dir)), List.of(dir))) {
			runtime.initializeAsync(null).get(30, TimeUnit.SECONDS);
			if (hot) runtime.decompiler().getClasses().forEach(cls -> cls.getCode());
			var proof = runtime.verifyRelatedHierarchy(B_BAR, VerificationBudget.defaults());
			assertEquals(Status.COMPLETE, proof.status()); assertEquals(BAR_FAMILY, proof.members());
			var ordinary = ReplacementStateTest.rename(P_FOO, "target");
			var group = propagated(B_BAR, "target");
			var failure = groupFirst ? rejects(runtime, 400, "INVALID_REQUEST", group, ordinary)
					: rejects(runtime, 400, "INVALID_REQUEST", ordinary, group);
			assertEquals(groupFirst ? 1 : 0, failure.itemErrors().getFirst().index());
			assertEquals("foo", RelatedFixture.method(runtime.decompiler(), C_FOO).getName());
			assertEquals("bar", RelatedFixture.method(runtime.decompiler(), C_BAR).getName());
		}
	}

	@Test void ordinaryRenameAloneStillImplicitlyAliasesTheOtherOwner() throws Exception {
		try (var runtime = new ProjectRuntime(null, List.of(fixture(dir)), List.of(dir))) {
			runtime.initializeAsync(null).get(30, TimeUnit.SECONDS);
			var result = new EditBatchService(runtime).apply(request(runtime, ReplacementStateTest.rename(P_FOO, "target")));
			assertEquals("APPLIED", result.outcome()); assertTrue(result.items().getFirst().affectedRefs().isEmpty());
			runtime.decompiler().getClasses().forEach(cls -> cls.getCode());
			assertEquals("target", RelatedFixture.method(runtime.decompiler(), P_FOO).getName());
			assertEquals("target", RelatedFixture.method(runtime.decompiler(), C_FOO).getName());
			assertEquals(1, runtime.<Integer>withExclusiveEdit(c -> c.codeDataCopy().getRenames().size()).intValue());
			rejects(runtime, 400, "INVALID_REQUEST", propagated(B_BAR, "target"));
		}
	}

	@Test void disjointAndNoopOrdinaryMethodsAlsoRejectButFieldsAndCommentsCanShareAGroupBatch() throws Exception {
		try (var runtime = new ProjectRuntime(null, List.of(fixture(dir)), List.of(dir))) {
			runtime.initializeAsync(null).get(30, TimeUnit.SECONDS);
			for (String alias : List.of("separateAlias", "foo")) {
				var ordinary = ReplacementStateTest.rename(P_FOO, alias); var group = propagated(B_BAR, "target");
				rejects(runtime, 400, "INVALID_REQUEST", ordinary, group);
				rejects(runtime, 400, "INVALID_REQUEST", group, ordinary);
			}
			var field = new SymbolRef(SymbolRef.Kind.FIELD, "Lmixed/P;", null, "data", "I");
			var comment = new EditDtos.Operation(EditDtos.Kind.SET_COMMENT, P_FOO, null, "method comment", "LINE");
			var result = new EditBatchService(runtime).apply(request(runtime,
					ReplacementStateTest.rename(field, "renamedData"), propagated(B_BAR, "target"), comment));
			assertEquals("APPLIED", result.outcome()); assertEquals(1, result.logicalRevisionAfter());
			assertEquals(BAR_FAMILY, result.items().get(1).affectedRefs()); records(runtime, BAR_FAMILY, "target");
			assertEquals("foo", RelatedFixture.method(runtime.decompiler(), C_FOO).getName());
			var code = runtime.withExclusiveEdit(c -> c.codeDataCopy());
			assertEquals(3, code.getRenames().size()); assertEquals(1, code.getComments().size());
		}
	}
}
