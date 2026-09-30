package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import dev.libjadx.core.edits.EditDtos;
import dev.libjadx.core.hierarchy.RelatedHierarchyVerifier.VerificationBudget;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.jadxadapter.*;
import dev.libjadx.probes.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RelatedGroupBoundsTest {
	@TempDir Path dir;
	static Path families(Path root,int size,int groups) throws Exception {
		var source=new StringBuilder("package bounded; public class Families {\n");
		for(int g=0;g<groups;g++) {
			source.append("public interface I").append(g).append(" { int f").append(g).append("(int n); }\n");
			for(int i=1;i<size;i++)source.append("public static class C").append(g).append('_').append(i).append(" implements I").append(g)
					.append(" { public int f").append(g).append("(int n){return n;} }\n");
		}
		for(int i=0;i<5;i++)source.append("public int single").append(i).append("(int n){return n;}\n");
		return HierarchyFixture.javaJar(root,"families.jar",Map.of("bounded/Families.java",source.append("}").toString()));
	}
	static SymbolRef seed(int group) { return RelatedGroupAdmissionTest.method("bounded/Families$I"+group,"f"+group,"(I)I"); }
	@Test void family64AndAggregate128AreInclusiveWhile65And129PlusReject() throws Exception {
		Path input=families(dir.resolve("boundary"),64,3);
		try(var runtime=new ProjectRuntime(null,List.of(input),List.of(dir))) {
			runtime.initializeAsync(null).get(30,TimeUnit.SECONDS);
			var operations=new ArrayList<EditDtos.Operation>();
			for(int i=0;i<3;i++)operations.add(RelatedGroupAdmissionTest.propagated(seed(i),"alias"+i));
			RelatedGroupAdmissionTest.rejects(runtime,429,"RESOURCE_LIMIT",operations.toArray(EditDtos.Operation[]::new));
			var result=new EditBatchService(runtime).apply(RelatedGroupAdmissionTest.request(runtime,operations.get(0),operations.get(1)));
			assertEquals(1,result.logicalRevisionAfter());assertEquals(64,result.items().get(0).affectedRefs().size());assertEquals(64,result.items().get(1).affectedRefs().size());
			RelatedGroupAdmissionTest.records(runtime,result.items().get(0).affectedRefs(),"alias0");RelatedGroupAdmissionTest.records(runtime,result.items().get(1).affectedRefs(),"alias1");
			for(var member:result.items().getFirst().affectedRefs()) {
				var noop=new EditBatchService(runtime).apply(RelatedGroupAdmissionTest.request(runtime,RelatedGroupAdmissionTest.propagated(member,"alias0")));
				assertEquals("NO_CHANGE",noop.outcome());assertEquals(result.items().getFirst().affectedRefs(),noop.items().getFirst().affectedRefs());
			}
		}
		input=families(dir.resolve("over"),65,1);
		try(var runtime=new ProjectRuntime(null,List.of(input),List.of(dir))) {
			runtime.initializeAsync(null).get(30,TimeUnit.SECONDS);RelatedGroupAdmissionTest.rejects(runtime,429,"RESOURCE_LIMIT",RelatedGroupAdmissionTest.propagated(seed(0),"Alias"));
		}
	}
	@Test void fourRequestsReserveAtMost800000WorkAndFifthIsRejected() throws Exception {
		Path input=families(dir,1,1);
		try(var runtime=new ProjectRuntime(null,List.of(input),List.of(dir))) {
			runtime.initializeAsync(null).get(30,TimeUnit.SECONDS);var operations=new ArrayList<EditDtos.Operation>();
			for(int i=0;i<5;i++)operations.add(RelatedGroupAdmissionTest.propagated(RelatedGroupAdmissionTest.method("bounded/Families","single"+i,"(I)I"),"alias"+i));
			RelatedGroupAdmissionTest.rejects(runtime,429,"RESOURCE_LIMIT",operations.toArray(EditDtos.Operation[]::new));
			assertEquals("APPLIED",new EditBatchService(runtime).apply(RelatedGroupAdmissionTest.request(runtime,operations.subList(0,4).toArray(EditDtos.Operation[]::new))).outcome());
		}
	}
	@Test void measureBoundedVerificationInventoryReplacementAndService() throws Exception {
		var rows=new ArrayList<Map<String,Object>>();
		for(String name:List.of("single","joined","chain","64","two-groups")) {
			Path root=Files.createDirectories(dir.resolve(name));List<Path> inputs;List<SymbolRef> seeds;
			if(List.of("64","two-groups").contains(name)) {
				inputs=List.of(families(root,name.equals("64")?64:4,name.equals("64")?1:2));seeds=name.equals("64")?List.of(seed(0)):List.of(seed(0),seed(1));
			}else { inputs=RelatedFixture.compile(root);seeds=List.of(name.equals("joined")?RelatedGroupAdmissionTest.SEED:RelatedFixture.ref(name.equals("chain")?"Base":"Unrelated","work","(I)I")); }
			try(var runtime=new ProjectRuntime(null,inputs,List.of(dir))) {
				runtime.initializeAsync(null).get(30,TimeUnit.SECONDS);
				long[] verification={0},work={0},inventory={0};int[] methods={0},members={0};
				runtime.withExclusiveEdit(c->{
					for(var seed:seeds) {
						long start=System.nanoTime();var result=c.verifyRelated(seed,VerificationBudget.defaults());verification[0]+=System.nanoTime()-start;work[0]+=result.work();members[0]+=result.members().size();
						assertEquals(dev.libjadx.core.hierarchy.RelatedHierarchyVerifier.Status.COMPLETE,result.status());
						start=System.nanoTime();
						for(var ref:result.members()) {
							var cls=JadxSymbolAdapter.visibleClass(c.decompiler(),ref.originalClassDescriptor(),0);assertEquals(1,JadxNativeEditAdapter.memberTargets(cls).stream().filter(t->t.ref().equals(ref)).count());
							methods[0]+=RawMethodCollisionInventory.methods(cls,200000-methods[0]).size();
						}inventory[0]+=System.nanoTime()-start;
					}return null;
				});
				long[] replacement={0,0};runtime.replacementHook(s->{if(s==ProjectRuntime.ReplacementStage.BEFORE_CONSTRUCTION)replacement[0]=System.nanoTime();if(s==ProjectRuntime.ReplacementStage.LOADED)replacement[1]=System.nanoTime();});
				var operations=new ArrayList<EditDtos.Operation>();for(int i=0;i<seeds.size();i++)operations.add(RelatedGroupAdmissionTest.propagated(seeds.get(i),"measuredAlias"+i));
				long start=System.nanoTime();var result=new EditBatchService(runtime).apply(RelatedGroupAdmissionTest.request(runtime,operations.toArray(EditDtos.Operation[]::new)));long service=System.nanoTime()-start;
				assertEquals("APPLIED",result.outcome());assertTrue(work[0]>0);assertTrue(replacement[1]>replacement[0]);
				rows.add(Map.of("case",name,"members",members[0],"work",work[0],"verificationMs",verification[0]/1e6,"resolutionInventoryMs",inventory[0]/1e6,"rawMethods",methods[0],"replacementLoadMs",(replacement[1]-replacement[0])/1e6,"serviceMs",service/1e6));
			}
		}
		Path output=Path.of("build/related-group-probe/performance.json");Files.createDirectories(output.getParent());Files.writeString(output,new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(rows));
	}
}
