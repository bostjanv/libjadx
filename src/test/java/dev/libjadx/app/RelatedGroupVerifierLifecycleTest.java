package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import dev.libjadx.core.hierarchy.RelatedHierarchyVerifier;
import dev.libjadx.core.hierarchy.RelatedHierarchyVerifier.VerificationBudget;
import dev.libjadx.probes.RelatedFixture;
import dev.libjadx.project.NativeProjectDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RelatedGroupVerifierLifecycleTest {
	@TempDir Path dir;
	@Test void everyAdmissionUsesTheCurrentCapturedEngineAndNeverCachedComplete() throws Exception {
		var inputs=RelatedFixture.compile(dir);Path path=dir.resolve("project.jadx");NativeProjectDocument.newFromInputs(path,inputs).save();
		AtomicInteger creates=new AtomicInteger();var calls=new ArrayList<Integer>();AtomicBoolean fail=new AtomicBoolean();
		try(var runtime=new ProjectRuntime(path,inputs,args->{
			int sequence=creates.incrementAndGet();var engine=new ProjectRuntime.JadxProjectEngine(args);
			return new ProjectRuntime.ProjectEngine() {
				public void load(){engine.load();}public jadx.api.JadxDecompiler decompiler(){return engine.decompiler();}public void close(){engine.close();}
				public RelatedHierarchyVerifier hierarchyVerifier(){return (seed,budget)->{
					calls.add(sequence);if(fail.get())throw new IllegalStateException("injected verifier failure");return engine.hierarchyVerifier().verify(seed,budget);
				};}
			};
		})) {
			runtime.initializeAsync(NativeProjectDocument.open(path)).get(30,TimeUnit.SECONDS);
			var cached=runtime.verifyRelatedHierarchy(RelatedGroupAdmissionTest.SEED,VerificationBudget.defaults());assertEquals(RelatedHierarchyVerifier.Status.COMPLETE,cached.status());
			fail.set(true);RelatedGroupAdmissionTest.rejects(runtime,500,"INTERNAL_ERROR",RelatedGroupAdmissionTest.propagated(RelatedGroupAdmissionTest.SEED,RelatedGroupAdmissionTest.ALIAS));
			assertEquals(List.of(1,1),calls);assertEquals(1,creates.get());fail.set(false);
			new EditBatchService(runtime).apply(RelatedGroupAdmissionTest.request(runtime,RelatedGroupAdmissionTest.propagated(RelatedGroupAdmissionTest.SEED,RelatedGroupAdmissionTest.ALIAS)));
			assertEquals(List.of(1,1,1),calls);assertEquals(2,creates.get());
			assertEquals("NO_CHANGE",new EditBatchService(runtime).apply(RelatedGroupAdmissionTest.request(runtime,RelatedGroupAdmissionTest.propagated(RelatedGroupAdmissionTest.SEED,RelatedGroupAdmissionTest.ALIAS))).outcome());
			assertEquals(List.of(1,1,1,2),calls);
			var r=runtime.projectSnapshot().revisions();runtime.reloadProject(true,r.sessionId(),r.logicalRevision());assertEquals(3,creates.get());
			new EditBatchService(runtime).apply(RelatedGroupAdmissionTest.request(runtime,RelatedGroupAdmissionTest.propagated(RelatedGroupAdmissionTest.SEED,RelatedGroupAdmissionTest.ALIAS)));
			assertEquals(List.of(1,1,1,2,3),calls);assertEquals(4,creates.get());
		}
	}
	@Test void callbackFailureAndCommitExpireVerificationAuthority() throws Exception {
		var inputs=RelatedFixture.compile(dir);
		try(var runtime=new ProjectRuntime(null,inputs,List.of(dir))) {
			runtime.initializeAsync(null).get(30,TimeUnit.SECONDS);var escaped=new AtomicReference<ProjectRuntime.EditContext>();
			assertThrows(IllegalStateException.class,()->runtime.withExclusiveEdit(c->{escaped.set(c);c.verifyRelated(RelatedGroupAdmissionTest.SEED,VerificationBudget.defaults());throw new IllegalStateException("callback failed");}));
			assertThrows(IllegalStateException.class,()->escaped.get().verifyRelated(RelatedGroupAdmissionTest.SEED,VerificationBudget.defaults()));
			runtime.withExclusiveEdit(c->{var candidate=c.codeDataCopy();dev.libjadx.jadxadapter.JadxNativeEditAdapter.rename(candidate,
					jadx.api.data.impl.JadxNodeRef.forCls("related.Hierarchy"),"NewHierarchy");c.commit(candidate);
				assertThrows(IllegalStateException.class,()->c.verifyRelated(RelatedGroupAdmissionTest.SEED,VerificationBudget.defaults()));return null;});
		}
	}
}
