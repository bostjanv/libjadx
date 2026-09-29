package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import dev.libjadx.core.edits.EditDtos;
import dev.libjadx.core.hierarchy.RelatedHierarchyVerifier;
import dev.libjadx.core.hierarchy.RelatedHierarchyVerifier.Status;
import dev.libjadx.core.hierarchy.RelatedHierarchyVerifier.VerificationBudget;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.jadxadapter.JadxNativeEditAdapter;
import dev.libjadx.jadxadapter.RawMethodCollisionInventory;
import dev.libjadx.probes.RelatedFixture;
import dev.libjadx.probes.HierarchyFixture;
import dev.libjadx.project.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RelatedGroupAdmissionTest {
	@TempDir Path dir;
	static final String ALIAS = "renamedJoined";
	static final SymbolRef SEED = RelatedFixture.ref("Joined", "joined", "(I)I");
	static final List<SymbolRef> FAMILY = List.of("ExtendedLeft", "Joined", "SeparateLeft", "SeparateRight").stream()
			.map(o -> RelatedFixture.ref(o, "joined", "(I)I")).toList();
	static EditDtos.Operation propagated(SymbolRef ref, String alias) {
		return new EditDtos.Operation(EditDtos.Kind.RENAME, ref, alias, null, null, null, null, true);
	}
	static EditDtos.Request request(ProjectRuntime runtime, EditDtos.Operation... items) {
		var revisions = runtime.projectSnapshot().revisions();
		return new EditDtos.Request(revisions.sessionId(), revisions.logicalRevision(), List.of(items));
	}
	static ProjectRuntime open(Path path) throws Exception {
		var doc = NativeProjectDocument.open(path); var runtime = new ProjectRuntime(path, doc.getInputFiles(), List.of(path.getParent()));
		try { runtime.initializeAsync(doc).get(30, TimeUnit.SECONDS); return runtime; }
		catch (Exception e) { runtime.close(); throw e; }
	}
	static void records(ProjectRuntime runtime, List<SymbolRef> family, String alias) {
		runtime.withExclusiveEdit(context -> {
			var code = context.codeDataCopy();
			for (var ref : family) {
				var method = RelatedFixture.method(context.decompiler(), ref);
				var key = jadx.api.data.impl.JadxNodeRef.forMth(method);
				assertEquals(1, JadxNativeEditAdapter.renameCount(code, key));
				assertEquals(alias, JadxNativeEditAdapter.existingRename(code, key)); assertEquals(alias, method.getName());
			}
			return null;
		});
	}
	static EditBatchService.Rejected rejects(ProjectRuntime runtime, int status, String code, EditDtos.Operation... items) {
		var before = runtime.projectSnapshot(); var identity = runtime.searchIdentity(); var pending = runtime.pendingEdits();
		var engine = runtime.decompiler();
		var e = assertThrows(EditBatchService.Rejected.class, () -> new EditBatchService(runtime).apply(request(runtime, items)));
		assertEquals(status, e.status()); assertEquals(code, e.code()); assertFalse(e.itemErrors().isEmpty());
		assertEquals(before, runtime.projectSnapshot()); assertEquals(identity, runtime.searchIdentity());
		assertEquals(pending, runtime.pendingEdits()); assertSame(engine, runtime.decompiler()); return e;
	}

	@ParameterizedTest @ValueSource(strings={"ExtendedLeft", "Joined", "SeparateLeft", "SeparateRight"})
	void everySeedPublishesExactFamilyFreshEquivalentAndStrongNoop(String owner) throws Exception {
		var inputs = RelatedFixture.compile(dir); Path path = dir.resolve("project.jadx"); NativeProjectDocument.newFromInputs(path, inputs).save();
		try (var runtime = open(path)) {
			SafeReplayStrategyTest.semantic(runtime.decompiler());
			var before = runtime.projectSnapshot(); var identity = runtime.searchIdentity(); var old = runtime.decompiler();
			String disk = Files.readString(path);
			var result = new EditBatchService(runtime).apply(request(runtime, propagated(RelatedFixture.ref(owner,"joined","(I)I"), ALIAS)));
			assertEquals("APPLIED", result.outcome()); assertEquals(FAMILY, result.items().getFirst().affectedRefs());
			assertEquals(before.revisions().logicalRevision()+1, result.logicalRevisionAfter()); assertEquals(before.revisions().indexRevision()+1,result.indexRevisionAfter());
			assertEquals(identity.publicationEpoch()+1,runtime.searchIdentity().publicationEpoch()); assertNotSame(old,runtime.decompiler());
			records(runtime,FAMILY,ALIAS); assertEquals(disk,Files.readString(path)); assertTrue(result.dirty()); assertFalse(result.saved());
			var current = runtime.decompiler(); var after = runtime.projectSnapshot(); var afterIdentity = runtime.searchIdentity();
			runtime.replacementHook(stage -> { throw new AssertionError("No-op must not construct replacement"); });
			var noop = new EditBatchService(runtime).apply(request(runtime, propagated(SEED,ALIAS)));
			assertEquals("NO_CHANGE",noop.outcome()); assertEquals("SKIPPED",noop.items().getFirst().status()); assertEquals(FAMILY,noop.items().getFirst().affectedRefs());
			assertEquals(after,runtime.projectSnapshot()); assertEquals(afterIdentity,runtime.searchIdentity()); assertSame(current,runtime.decompiler());
			runtime.replacementHook(stage -> { });
			assertEquals(FAMILY,runtime.verifyRelatedHierarchy(SEED,VerificationBudget.defaults()).members());
			var code = runtime.withExclusiveEdit(c -> c.codeDataCopy());
			try(var fresh = SafeReplayStrategyTest.open(inputs,null,code)) { assertEquals(SafeReplayStrategyTest.semantic(fresh),SafeReplayStrategyTest.semantic(runtime.decompiler())); }
			runtime.saveProject(null,null);
		}
		try(var fresh = open(path)) { records(fresh,FAMILY,ALIAS); assertEquals(FAMILY,fresh.verifyRelatedHierarchy(SEED,VerificationBudget.defaults()).members()); }
	}

	@Test void derivedAliasesDoNotReplaceExplicitIntentAndMixedPriorRecordsNormalize() throws Exception {
		var inputs = RelatedFixture.compile(dir);
		try(var runtime = new ProjectRuntime(null,inputs,List.of(dir))) {
			runtime.initializeAsync(null).get(30,TimeUnit.SECONDS);
			new EditBatchService(runtime).apply(request(runtime,ReplacementStateTest.rename(SEED,ALIAS)));
			assertEquals(1,runtime.<Integer>withExclusiveEdit(c -> c.codeDataCopy().getRenames().size()).intValue());
			var grouped = new EditBatchService(runtime).apply(request(runtime,propagated(SEED,ALIAS)));
			assertEquals("APPLIED",grouped.outcome()); records(runtime,FAMILY,ALIAS);
			new EditBatchService(runtime).apply(request(runtime,ReplacementStateTest.rename(FAMILY.getLast(),"differentAlias")));
			assertEquals("APPLIED",new EditBatchService(runtime).apply(request(runtime,propagated(SEED,ALIAS))).outcome()); records(runtime,FAMILY,ALIAS);
		}
	}

	@ParameterizedTest @ValueSource(strings={"work","call","run","inherited","simple","packageMethod","protectedMethod","dex"})
	void otherCompleteFamiliesUseTheSameAdmission(String name) throws Exception {
		List<Path> inputs; SymbolRef seed;
		if(name.equals("dex")) {
			inputs=List.of(HierarchyFixture.dex(dir,"iface.dex",".class public interface abstract Ldex/I;\n.super Ljava/lang/Object;\n.method public abstract f(I)I\n.end method\n"),
				HierarchyFixture.dex(dir,"impl.dex",".class public Ldex/C;\n.super Ljava/lang/Object;\n.implements Ldex/I;\n.method public f(I)I\n.registers 2\nreturn p1\n.end method\n")); seed=method("dex/I","f","(I)I");
		} else if(List.of("inherited","simple","packageMethod","protectedMethod").contains(name)) {
			inputs=HierarchyFixture.visibility(dir); seed=method("hierarchy/p/Visibility$"+(name.equals("inherited")?"Contract":name.equals("simple")?"Simple":"Base"),name,"(I)I");
		} else { inputs=RelatedFixture.compile(dir); seed=RelatedFixture.ref(switch(name){case "work"->"Base";case "call"->"Root";default->"DefaultRoot";},name,"(I)I"); }
		Path path = dir.resolve("family.jadx"); NativeProjectDocument.newFromInputs(path, inputs).save();
		List<SymbolRef> family;
		try(var runtime=open(path)) {
			family=runtime.verifyRelatedHierarchy(seed,VerificationBudget.defaults()).members(); assertFalse(family.isEmpty());
			for(var member:family) {
				assertEquals(family,runtime.verifyRelatedHierarchy(member,VerificationBudget.defaults()).members());
				var result=new EditBatchService(runtime).apply(request(runtime,propagated(member,"verifiedAlias")));
				assertEquals(family,result.items().getFirst().affectedRefs()); records(runtime,family,"verifiedAlias");
			}
			var code=runtime.withExclusiveEdit(c->c.codeDataCopy());
			try(var fresh=SafeReplayStrategyTest.open(inputs,null,code)) {
				// Mirror current-revision admission reads in the oracle. Pinned JavaClass caches its
				// visible lists; otherwise only an omitted default constructor can differ by read order.
				for(var member:family) JadxNativeEditAdapter.memberTargets(
						dev.libjadx.jadxadapter.JadxSymbolAdapter.visibleClass(fresh,member.originalClassDescriptor(),0));
				var expected=SafeReplayStrategyTest.semantic(fresh); var actual=SafeReplayStrategyTest.semantic(runtime.decompiler());
				var differences=expected.keySet().stream().filter(k->!Objects.equals(expected.get(k),actual.get(k))).toList();
				if (!differences.isEmpty()) {
					Path diagnostic=Path.of("build/related-group-probe/"+name+"-oracle.json"); Files.createDirectories(diagnostic.getParent());
					Files.writeString(diagnostic,new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(Map.of("differentKeys",differences,"fresh",expected,"active",actual)));
				}
				assertEquals(List.of(), differences);
			}
			runtime.saveProject(null,null);
		}
		try(var restarted=open(path)) { records(restarted,family,"verifiedAlias"); }

	}
	static SymbolRef method(String owner,String name,String descriptor) { return new SymbolRef(SymbolRef.Kind.METHOD,"L"+owner+";",null,name,descriptor); }

	@ParameterizedTest @ValueSource(ints={0,2,3})
	void groupPrivateFaultNeverPublishesPartialMembers(int fault) throws Exception {
		var inputs=RelatedFixture.compile(dir);
		try(var runtime=new ProjectRuntime(null,inputs,List.of(dir))) {
			runtime.initializeAsync(null).get(30,TimeUnit.SECONDS);
			var ordinary=ReplacementStateTest.rename(RelatedFixture.ref("Unrelated","work","(I)I"),"prefixWork");
			var comment=new EditDtos.Operation(EditDtos.Kind.SET_COMMENT,SymbolRef.classRef("Lrelated/Hierarchy;"),null,"suffix","LINE");
			var service=new EditBatchService(runtime,i->{},(item,member)->{if(member==fault)throw new IllegalStateException("group fault");});
			var result=service.apply(request(runtime,ordinary,propagated(SEED,ALIAS),comment));
			assertEquals("PARTIAL",result.outcome()); assertEquals(List.of("APPLIED","FAILED","SKIPPED"),result.items().stream().map(EditDtos.ItemResult::status).toList());
			assertTrue(result.items().stream().allMatch(i->i.affectedRefs().isEmpty()));
			assertEquals(1,runtime.<Integer>withExclusiveEdit(c->c.codeDataCopy().getRenames().size()).intValue());
			assertEquals("joined",RelatedFixture.method(runtime.decompiler(),SEED).getName());
			var next=new EditBatchService(runtime,i->{if(i==1)throw new IllegalStateException("ordinary fault");});
			var prefix=next.apply(request(runtime,propagated(SEED,ALIAS),comment));
			assertEquals("PARTIAL",prefix.outcome()); assertEquals(FAMILY,prefix.items().getFirst().affectedRefs()); records(runtime,FAMILY,ALIAS);
		}
	}

	@Test void replacementFailurePublishesNoGroupOrPrefix() throws Exception {
		var inputs=RelatedFixture.compile(dir);
		try(var runtime=new ProjectRuntime(null,inputs,List.of(dir))) {
			runtime.initializeAsync(null).get(30,TimeUnit.SECONDS); var before=runtime.projectSnapshot(); var old=runtime.decompiler();
			runtime.replacementHook(s->{if(s==ProjectRuntime.ReplacementStage.LOADED)throw new IllegalStateException("candidate fault");});
			assertThrows(IllegalStateException.class,()->new EditBatchService(runtime).apply(request(runtime,propagated(SEED,ALIAS))));
			assertSame(old,runtime.decompiler());assertEquals(before,runtime.projectSnapshot());assertEquals(0,runtime.<Integer>withExclusiveEdit(c->c.codeDataCopy().getRenames().size()).intValue());
		}
	}

	@Test void overlapsAndAllOwnerCollisionsRejectRegardlessOfOrder() throws Exception {
		var inputs=RelatedFixture.compile(dir);
		try(var runtime=new ProjectRuntime(null,inputs,List.of(dir))) {
			runtime.initializeAsync(null).get(30,TimeUnit.SECONDS);
			var group=propagated(SEED,ALIAS);
			for(var other:List.of(propagated(FAMILY.getFirst(),ALIAS),ReplacementStateTest.rename(FAMILY.getLast(),"ordinary"),
					ReplacementStateTest.rename(SymbolRef.classRef("Lrelated/UnrelatedCold;"),"ClassAlias"))) {
				rejects(runtime,400,"INVALID_REQUEST",group,other); rejects(runtime,400,"INVALID_REQUEST",other,group);
			}
			assertEquals(0,rejects(runtime,400,"INVALID_REQUEST",propagated(RelatedFixture.ref("Base","work","(I)I"),"collision")).itemErrors().getFirst().index());
		}
	}

	@Test void rawHiddenBridgeAndReturnOnlyCollisionsAreBlockersIncludingPostBatchNames() throws Exception {
		Path input=HierarchyFixture.javaJar(dir,"raw.jar",Map.of("raw/C.java",
				"package raw; interface R<T> { T hidden(T v); } public class C implements R<String> { "
				+"public String hidden(String v){return v;} public int f(Object v){return 1;} public int ordinary(Object v){return 2;} }"));
		try(var runtime=new ProjectRuntime(null,List.of(input),List.of(dir))) {
			runtime.initializeAsync(null).get(30,TimeUnit.SECONDS); var cls=runtime.decompiler().searchJavaClassByOrigFullName("raw.C");
			// Retain real codegen flags so the pinned public list actually filters the compiler bridge.
			cls.getClassNode().add(jadx.core.dex.attributes.AFlag.DONT_UNLOAD_CLASS); cls.getCode();
			var raw=RawMethodCollisionInventory.methods(cls,200000); assertEquals(5,raw.size());
			assertFalse(cls.getCode().contains("hidden(Object"), "Real compiler bridge is omitted from emitted Java");
			assertFalse(cls.getMethods().stream().anyMatch(m -> m.getAccessFlags().isBridge()), "Pinned public list must omit the retained compiler bridge");
			var seed=method("raw/C","f","(Ljava/lang/Object;)I");
			rejects(runtime,400,"INVALID_REQUEST",propagated(seed,"hidden"));
			var group=propagated(seed,"newAlias");var ordinary=ReplacementStateTest.rename(method("raw/C","ordinary","(Ljava/lang/Object;)I"),"newAlias");
			rejects(runtime,400,"INVALID_REQUEST",ordinary,group);rejects(runtime,400,"INVALID_REQUEST",group,ordinary);
			rejects(runtime,400,"INVALID_REQUEST",ReplacementStateTest.rename(method("raw/C","ordinary","(Ljava/lang/Object;)I"),"away"),propagated(seed,"ordinary"));
		}
	}

	@Test void expiredAndCrossThreadEditContextCannotVerifyAndCachedEvidenceCannotAuthorize() throws Exception {
		var inputs=RelatedFixture.compile(dir);Path path=dir.resolve("project.jadx");NativeProjectDocument.newFromInputs(path,inputs).save();
		try(var runtime=open(path);var executor=Executors.newSingleThreadExecutor()) {
			var cached=runtime.verifyRelatedHierarchy(SEED,VerificationBudget.defaults());assertEquals(Status.COMPLETE,cached.status());
			var escaped=runtime.withExclusiveEdit(c->{
				assertEquals(cached,c.verifyRelated(SEED,VerificationBudget.defaults()));
				try{executor.submit(()->assertThrows(IllegalStateException.class,()->c.verifyRelated(SEED,VerificationBudget.defaults()))).get(5,TimeUnit.SECONDS);}catch(Exception e){throw new AssertionError(e);}return c;
			});
			assertThrows(IllegalStateException.class,()->escaped.verifyRelated(SEED,VerificationBudget.defaults()));
			var stale=request(runtime,propagated(SEED,ALIAS));runtime.reloadProject(true,stale.expectedSessionId(),stale.expectedLogicalRevision());
			assertEquals("STALE_REVISION",assertThrows(EditBatchService.Rejected.class,()->new EditBatchService(runtime).apply(stale)).code());
			Files.write(inputs.getFirst(),new byte[]{1},StandardOpenOption.APPEND);
			rejects(runtime,409,"EXTERNAL_MODIFICATION_CONFLICT",propagated(SEED,ALIAS));
		}
	}

	@Test void everyIncompleteStatusHasBoundedItemIndexedError() {
		for(var status:Status.values())if(status!=Status.COMPLETE) {
			var error=RelatedMethodPlanner.incomplete(status,3);assertEquals(3,error.itemErrors().getFirst().index());
			assertEquals(switch(status){case NOT_FOUND->404;case RESOURCE_LIMIT->429;case INPUT_CHANGED->409;case FAILED->500;default->422;},error.status());
		}
	}
}
