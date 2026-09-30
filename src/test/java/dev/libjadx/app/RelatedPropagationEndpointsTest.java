package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.libjadx.core.edits.EditDtos;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.http.HttpApiServer;
import dev.libjadx.probes.RelatedFixture;
import dev.libjadx.project.FileFingerprint;
import dev.libjadx.project.NativeProjectDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RelatedPropagationEndpointsTest {
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final HttpClient CLIENT = HttpClient.newHttpClient();
	@TempDir Path dir;

	@Test void relatedContractAdmitsOnlyVerifiedRequestsAndInvalidatesSnapshots() throws Exception {
		var inputs = RelatedFixture.compile(dir); Path path = dir.resolve("project.jadx");
		var project = NativeProjectDocument.newFromInputs(path, inputs); project.save();
		try (var runtime = new ProjectRuntime(path, inputs, List.of(dir)); var server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); runtime.initializeAsync(project).get(30, TimeUnit.SECONDS);
			String target = JSON.writeValueAsString(RelatedGroupAdmissionTest.SEED);
			String base = "{\"kind\":\"RENAME\",\"target\":" + target + ",\"newName\":\"renamedJoined\"";
			for (boolean dirty : List.of(false, true)) {
				if (dirty) new EditBatchService(runtime).apply(new EditDtos.Request(null,null,List.of(
						new EditDtos.Operation(EditDtos.Kind.SET_COMMENT,SymbolRef.classRef("Lrelated/Hierarchy;"),null,"pending comment","LINE"))));
				var before = runtime.projectSnapshot(); var identity = runtime.searchIdentity(); var pending = runtime.pendingEdits();
				String prefix = "{\"expectedSessionId\":\""+before.revisions().sessionId()+"\",\"expectedLogicalRevision\":"+before.revisions().logicalRevision()+",\"items\":[";
				for(String flag:List.of("null","\"yes\"","7","[]","{}")) {
					String name=switch(flag){case "\"yes\""->"string";case "7"->"number";case "[]"->"array";case "{}"->"object";default->flag;};
					capture(server,(dirty?"dirty-":"clean-")+name, prefix+base+",\"propagateRelated\":"+flag+"}]}",400,"INVALID_REQUEST");
				}
				capture(server,(dirty?"dirty-":"clean-")+"missing-revisions","{\"items\":["+base+",\"propagateRelated\":true}]}",400,"INVALID_REQUEST");
				for(String kind:List.of("CLASS","FIELD")) {
					String ref=kind.equals("CLASS")?JSON.writeValueAsString(SymbolRef.classRef("Lrelated/Hierarchy;")):
						"{\"kind\":\"FIELD\",\"originalClassDescriptor\":\"Lrelated/Hierarchy;\",\"originalName\":\"field\",\"originalDescriptor\":\"I\"}";
					capture(server,(dirty?"dirty-":"clean-")+kind,prefix+"{\"kind\":\"RENAME\",\"target\":"+ref+",\"newName\":\"Alias\",\"propagateRelated\":true}]}",400,"INVALID_REQUEST");
				}
				for(String owner:List.of("MissingParent","External","CovariantLeaf","Base")) {
					String name=switch(owner){case "MissingParent"->"lost";case "External"->"run";case "CovariantLeaf"->"value";default->"hidden";};
					String desc=switch(owner){case "External"->"()V";case "CovariantLeaf"->"()Ljava/lang/String;";default->"(I)I";};
					capture(server,(dirty?"dirty-":"clean-")+owner,prefix+wire(RelatedGroupAdmissionTest.propagated(RelatedFixture.ref(owner,name,desc),"Alias"))+"]}",422,"UNSUPPORTED_CAPABILITY");
				}
				capture(server,(dirty?"dirty-":"clean-")+"collision",prefix+wire(RelatedGroupAdmissionTest.propagated(RelatedFixture.ref("Base","work","(I)I"),"collision"))+"]}",400,"INVALID_REQUEST");
				assertEquals(before,runtime.projectSnapshot()); assertEquals(identity,runtime.searchIdentity()); assertEquals(pending,runtime.pendingEdits());
			}
			// False is ordinary behavior; the later explicit request must still add missing family records.
			var legacy=capture(server,"false","{\"items\":["+base+",\"propagateRelated\":false}]}",200,null);
			assertTrue(legacy.path("items").get(0).path("affectedRefs").isEmpty());
			String sourceRequest="{\"ref\":{\"kind\":\"CLASS\",\"originalClassDescriptor\":\"Lrelated/Hierarchy;\"}}";
			String snapshot=JSON.readTree(post(server,"/api/v1/decompile",sourceRequest).body()).path("sourceSnapshotId").asText();
			String cursor=JSON.readTree(get(server,"/api/v1/classes?pageSize=1").body()).path("nextCursor").asText();
			String searchRequest="{\"query\":\"Hierarchy\",\"domains\":[\"CLASS_NAME\"],\"pageSize\":1}";
			String searchCursor=JSON.readTree(post(server,"/api/v1/search",searchRequest).body()).path("nextCursor").asText();
			var result=capture(server,"applied",body(runtime,base+",\"propagateRelated\":true}"),200,null);
			assertEquals(JSON.valueToTree(RelatedGroupAdmissionTest.FAMILY),result.path("items").get(0).path("affectedRefs"));
			capture(server,"no-change",body(runtime,base+",\"propagateRelated\":true}"),200,null);
			var budgetItems=List.of(RelatedGroupAdmissionTest.SEED,RelatedFixture.ref("Base","work","(I)I"),RelatedFixture.ref("Root","call","(I)I"),RelatedFixture.ref("DefaultRoot","run","(I)I"),RelatedFixture.ref("Unrelated","work","(I)I"));
			var budgetWire=new java.util.ArrayList<String>();for(var ref:budgetItems)budgetWire.add(wire(RelatedGroupAdmissionTest.propagated(ref,"budgetAlias")));
			capture(server,"resource-limit",body(runtime,String.join(",",budgetWire)),429,"RESOURCE_LIMIT");
			capture(server,"missing-method",body(runtime,wire(RelatedGroupAdmissionTest.propagated(RelatedFixture.ref("Joined","absent","(I)I"),"Alias"))),404,"NOT_FOUND");
			capture(server,"stale","{\"expectedSessionId\":\""+runtime.projectSnapshot().revisions().sessionId()+"\",\"expectedLogicalRevision\":0,\"items\":["+base+",\"propagateRelated\":true}]}",409,"STALE_REVISION");
			assertEquals("STALE_REVISION",JSON.readTree(get(server,"/api/v1/classes?pageSize=1&cursor="+cursor).body()).path("error").path("code").asText());
			assertEquals("STALE_REVISION",JSON.readTree(post(server,"/api/v1/decompile",sourceRequest.substring(0,sourceRequest.length()-1)+",\"expectedSourceSnapshotId\":\""+snapshot+"\"}").body()).path("error").path("code").asText());
			assertFalse(searchCursor.isEmpty());
			assertEquals(409,post(server,"/api/v1/search",searchRequest.substring(0,searchRequest.length()-1)+",\"cursor\":\""+searchCursor+"\"}").statusCode());
			var caps=JSON.readTree(get(server,"/api/v1/capabilities").body());
			var related=java.util.stream.StreamSupport.stream(caps.path("capabilities").spliterator(),false).filter(c->c.path("name").asText().equals("edit.related_propagation")).findFirst().orElseThrow();
			assertEquals("PARTIAL",related.path("status").asText());
			assertEquals("INDEPENDENT_CLOSED_INPUT_FAMILY_GUI_VERIFIED",related.path("evidence").asText());
			assertEquals("MEMORY_ONLY_UNTIL_EXPLICIT_NATIVE_SAVE",related.path("persistence").asText());
		}
	}
	@Test void mixedOrdinaryMethodAndGroupRejectInBothOrdersWithOmittedOrFalseFlag() throws Exception {
		var input=RelatedGroupMixedBatchTest.fixture(dir);
		try(var runtime=new ProjectRuntime(null,List.of(input),List.of(dir));var server=new HttpApiServer("127.0.0.1",0,runtime)) {
			server.start();runtime.initializeAsync(null).get(30,TimeUnit.SECONDS);
			var before=runtime.projectSnapshot();var identity=runtime.searchIdentity();var pending=runtime.pendingEdits();var engine=runtime.decompiler();
			runtime.replacementHook(stage->{throw new AssertionError("Rejected batch must not construct a replacement");});
			String group=wire(RelatedGroupAdmissionTest.propagated(RelatedGroupMixedBatchTest.B_BAR,"target"));
			for(boolean explicitFalse:List.of(false,true))for(boolean groupFirst:List.of(false,true)) {
				var ordinary=(com.fasterxml.jackson.databind.node.ObjectNode)JSON.readTree(wire(ReplacementStateTest.rename(RelatedGroupMixedBatchTest.P_FOO,"target")));
				if(!explicitFalse)ordinary.remove("propagateRelated");
				String items=groupFirst?group+","+ordinary:ordinary+","+group;
				var response=capture(server,"mixed-"+(groupFirst?"group-first-":"ordinary-first-")+(explicitFalse?"false":"omitted"),body(runtime,items),400,"INVALID_REQUEST");
				assertEquals(groupFirst?1:0,response.path("error").path("details").path("itemErrors").get(0).path("index").asInt());
				assertEquals(before,runtime.projectSnapshot());assertEquals(identity,runtime.searchIdentity());assertEquals(pending,runtime.pendingEdits());assertSame(engine,runtime.decompiler());
			}
		}
	}

	private static String wire(EditDtos.Operation item) throws Exception {
		return JSON.writeValueAsString(Map.of("kind",item.kind(),"target",item.target(),"newName",item.newName(),"propagateRelated",item.propagateRelated()));
	}
	private static String body(ProjectRuntime runtime,String item) {
		var r=runtime.projectSnapshot().revisions();return "{\"expectedSessionId\":\""+r.sessionId()+"\",\"expectedLogicalRevision\":"+r.logicalRevision()+",\"items\":["+item+"]}";
	}
	private static com.fasterxml.jackson.databind.JsonNode capture(HttpApiServer server,String name,String body,int status,String error) throws Exception {
		var response=post(server,"/api/v1/edits/batch",body);assertEquals(status,response.statusCode(),response.body());
		var parsed=JSON.readTree(response.body());if(error!=null)assertEquals(error,parsed.path("error").path("code").asText());
		Path captures=Path.of("build/related-contract-responses");Files.createDirectories(captures);
		Files.writeString(captures.resolve(name+".json"),JSON.writeValueAsString(Map.of("status",status,"schema",status==200?"EditBatchResult":"ErrorEnvelope","body",parsed,"request",JSON.readTree(body))));return parsed;
	}

	@Test void ordinaryRenameRetainsExistingImplicitCandidatePropagationAndEmptyAffectedRefs() throws Exception {
		var inputs = RelatedFixture.compile(dir);
		try (var runtime = new ProjectRuntime(null, inputs, List.of(dir))) {
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			var result = new EditBatchService(runtime).apply(new EditDtos.Request(null, null, List.of(
					new EditDtos.Operation(EditDtos.Kind.RENAME, RelatedFixture.ref("Joined", "joined", "(I)I"), "legacyJoined", null, null))));
			assertEquals("APPLIED", result.outcome()); assertTrue(result.items().getFirst().affectedRefs().isEmpty());
			runtime.decompiler().searchJavaClassByOrigFullName("related.Hierarchy").getCode();
			for (String owner : List.of("Joined", "ExtendedLeft", "SeparateLeft"))
				assertEquals("legacyJoined", RelatedFixture.method(runtime.decompiler(), RelatedFixture.ref(owner, "joined", "(I)I")).getName());
			assertEquals("joined", RelatedFixture.method(runtime.decompiler(), RelatedFixture.ref("SeparateRight", "joined", "(I)I")).getName());
			assertFalse(Files.exists(dir.resolve("hierarchy.jar.jadx")));
		}
	}

	private static URI uri(HttpApiServer server, String path) { return URI.create("http://127.0.0.1:" + server.localPort() + path); }
	private static HttpResponse<String> post(HttpApiServer server, String path, String body) throws Exception {
		return CLIENT.send(HttpRequest.newBuilder(uri(server, path)).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
	}
	private static HttpResponse<String> get(HttpApiServer server, String path) throws Exception {
		return CLIENT.send(HttpRequest.newBuilder(uri(server, path)).GET().build(), HttpResponse.BodyHandlers.ofString());
	}
}
