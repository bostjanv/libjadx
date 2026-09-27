package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.io.IOException;
import jadx.api.JadxDecompiler;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.libjadx.http.HttpApiServer;
import dev.libjadx.jadxadapter.JadxSymbolAdapter;
import dev.libjadx.core.symbols.SymbolRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReferenceEndpointsTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final HttpClient HTTP = HttpClient.newHttpClient();
    @TempDir Path dir;
    static SymbolRef method(String name, String descriptor) { return new SymbolRef(SymbolRef.Kind.METHOD, "Lprobe/ReferenceFixture;", null, name, descriptor); }
    static ObjectNode request(SymbolRef ref, String direction) {
        ObjectNode body = JSON.createObjectNode(); body.set("ref", JSON.valueToTree(ref)); body.put("direction", direction); return body;
    }
    @Test void coldGraphSitesAndReadOnlyPagination() throws Exception {
        Path jar = SymbolFixtureSupport.referenceFixture(dir);
        byte[] before = Files.readAllBytes(jar);
        ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
        try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
            server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
            var entry = request(method("entry", "()V"), "OUTGOING");
            JsonNode graph = success(server, entry);
            assertEquals(5, graph.path("edges").size());
            assertEquals("PARTIAL", graph.path("coverage").path("status").asText());
            assertEquals("JADX_REPORTED", graph.path("scope").asText());
            assertTrue(graph.path("pageComplete").asBoolean());
            assertEquals(1, count(graph, "CALL", "helper", "()V"));
            assertEquals(1, count(graph, "UNRESOLVED_CALL", "external", "(Ljava/lang/String;)Ljava/lang/String;"));
            JsonNode incoming = success(server, request(method("helper", "()V"), "INCOMING"));
            assertTrue(hasSource(incoming, "entry"));
            var field = new SymbolRef(SymbolRef.Kind.FIELD, "Lprobe/ReferenceFixture;", null, "value", "I");
            JsonNode users = success(server, request(field, "INCOMING"));
            assertTrue(hasSource(users, "entry"));
            for (var edge : users.path("edges")) {
                assertEquals("FIELD_USE", edge.path("relation").asText()); assertTrue(edge.path("originalOffset").isNull());
            }
            JsonNode deps = success(server, request(SymbolRef.classRef("Lprobe/ReferenceFixture;"), "OUTGOING"));
            assertTrue(deps.path("edges").toString().contains("Lprobe/ReferenceOther;"));
            assertTrue(success(server, request(SymbolRef.classRef("Lprobe/ReferenceOther;"), "INCOMING"))
                    .path("edges").toString().contains("Lprobe/ReferenceFixture;"));
            entry.put("includeSourceSites", true);
            JsonNode withSites = success(server, entry);
            JsonNode helperEdge = null;
            for (var edge : withSites.path("edges")) if (edge.path("targetRef").path("originalName").asText().equals("helper")
                    && edge.path("targetRef").path("originalDescriptor").asText().equals("()V")) helperEdge = edge;
            assertNotNull(helperEdge); assertEquals(8, helperEdge.path("sourceSites").size());
            JsonNode source = JSON.readTree(post(server, "/decompile", "{\"ref\":" + JSON.writeValueAsString(method("entry", "()V")) + "}").body());
            for (var site : helperEdge.path("sourceSites")) {
                assertEquals(source.path("sourceSnapshotId"), site.path("sourceSnapshotId"));
                assertTrue(source.path("source").asText().startsWith("helper", site.path("position").path("offsetUtf16").asInt()));
            }
            var fieldRequest = request(field, "INCOMING").put("includeSourceSites", true);
            assertTrue(success(server, fieldRequest).path("edges").toString().contains("\"precision\":\"EXACT\""));
            entry.put("pageSize", 1);
            JsonNode sitePage = success(server, entry);
            assertEquals("helper", sitePage.path("edges").get(0).path("targetRef").path("originalName").asText());
            assertEquals(8, sitePage.path("edges").get(0).path("sourceSites").size());
            String compactCursor = sitePage.path("nextCursor").asText();
            assertTrue(compactCursor.length() < 1024, "Eight verified sites must use a compact cursor");
            entry.put("cursor", compactCursor);
            JsonNode nextSitePage = success(server, entry);
            assertNotEquals(sitePage.path("edges"), nextSitePage.path("edges"));
            assertEquals(sitePage.path("snapshotId"), nextSitePage.path("snapshotId"));
            entry.remove("cursor");
            entry.put("includeSourceSites", false).put("pageSize", 1);
            JsonNode first = success(server, entry);
            String token = first.path("nextCursor").asText(); assertFalse(token.isEmpty());
            List<JsonNode> all = new ArrayList<>(); all.add(first.path("edges").get(0));
            JsonNode page = first;
            while (!page.path("nextCursor").isNull()) {
                entry.put("cursor", page.path("nextCursor").asText()); page = success(server, entry);
                assertEquals(first.path("snapshotId"), page.path("snapshotId"));
                page.path("edges").forEach(all::add);
            }
            assertEquals(5, all.size()); assertEquals(5, new HashSet<>(all).size());
            entry.put("cursor", token).put("pageSize", 2); error(server, entry, 400, "INVALID_REQUEST");
            entry.put("pageSize", 1).put("cursor", "B" + token.substring(1)); error(server, entry, 400, "INVALID_REQUEST");
            entry.put("cursor", token);
            var rev = runtime.projectSnapshot().revisions();
            assertEquals(0, rev.logicalRevision());
            assertFalse(runtime.projectSnapshot().dirty());
            runtime.saveProject(dir.resolve("references.jadx"), rev.sessionId(), rev.logicalRevision());
            error(server, entry, 409, "STALE_REVISION");
            entry.remove("cursor"); var saved = success(server, entry);
            runtime.reloadProject(false, rev.sessionId(), rev.logicalRevision());
            entry.put("cursor", saved.path("nextCursor").asText()); error(server, entry, 409, "STALE_REVISION");
            assertFalse(runtime.projectSnapshot().dirty());
            assertEquals(rev.logicalRevision() + 1, runtime.projectSnapshot().revisions().logicalRevision());
            assertArrayEquals(before, Files.readAllBytes(jar));
            if ("true".equals(System.getenv("LIBJADX_RECORD_REFERENCE_EXAMPLES"))) {
                Files.writeString(Path.of("openapi/examples/references-method.json"), graph.toPrettyString());
                Files.writeString(Path.of("openapi/examples/references-sites.json"), withSites.toPrettyString());
                Files.writeString(Path.of("openapi/examples/references-field.json"), users.toPrettyString());
                Files.writeString(Path.of("openapi/examples/references-class.json"), deps.toPrettyString());
            }
        } finally { runtime.close(); }
    }
    @Test void validationTypedMissesStrictAndBusy() throws Exception {
        Path jar = SymbolFixtureSupport.referenceFixture(dir);
        ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
        try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
            server.start();
            assertEquals(503, post(server, "/references/query", "{}").statusCode());
            runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
            ObjectNode q = request(method("entry", "()V"), "OUTGOING");
            for (String extra : List.of("\"pageSize\":1.5", "\"pageSize\":101", "\"cursor\":\"" + "x".repeat(4097) + "\"",
                    "\"strict\":null", "\"expectedLogicalRevision\":0", "\"relations\":[\"CALL\",\"CALL\"]",
                    "\"relations\":[\"READ\"]", "\"unexpected\":true", "\"direction\":\"INCOMING\"")) {
                String raw = q.toString(); raw = raw.substring(0, raw.length()-1) + "," + extra + "}";
                assertEquals(400, post(server, "/references/query", raw).statusCode(), raw.substring(0, Math.min(300,raw.length())));
            }
            assertEquals(400, post(server, "/references/query", q + " {}").statusCode());
            assertEquals(400, post(server, "/references/query", "{\"padding\":\"" + "x".repeat(65536) + "\"}").statusCode());
            var wrong = HTTP.send(HttpRequest.newBuilder(uri(server,"/references/query")).header("Content-Type","text/plain")
                    .POST(HttpRequest.BodyPublishers.ofString(q.toString())).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(415, wrong.statusCode());
            error(server, q.deepCopy().put("strict",true),409,"INCOMPLETE_ANALYSIS");
            var missing = request(method("missing","()V"),"OUTGOING").put("strict",true);
            assertEquals("NOT_FOUND",success(server,missing).path("outcome").asText());
            var provenance = q.deepCopy(); ((ObjectNode)provenance.get("ref")).put("inputIdentity","claimed");
            assertEquals("PROVENANCE_UNAVAILABLE",success(server,provenance.put("strict",true)).path("outcome").asText());
            var field = request(new SymbolRef(SymbolRef.Kind.FIELD,"Lprobe/ReferenceFixture;",null,"value","I"),"OUTGOING");
            error(server,field,400,"INVALID_REQUEST");
            var session = runtime.projectSnapshot().revisions().sessionId();
            error(server,q.deepCopy().put("expectedSessionId",session).put("expectedLogicalRevision",9),409,"STALE_REVISION");
            runtime.withPrimarySymbolRead("held", context -> {
                try { error(server,q,409,"PROJECT_BUSY"); }
                catch (Exception e) { throw new RuntimeException(e); }
                assertThrows(dev.libjadx.scheduler.ProjectBusyException.class, () -> runtime.reloadProject(false,session,0));
                return null;
            });
        } finally { runtime.close(); }
    }
    @Test void returnTypeOverloadsAndRestartRemainDistinct() throws Exception {
        Path jar = SymbolFixtureSupport.returnTypeClashJar(dir);
        ProjectRuntime runtime = new ProjectRuntime(null,List.of(jar),List.of(dir));
        try(HttpApiServer server = new HttpApiServer("127.0.0.1",0,runtime)) {
            server.start(); runtime.initializeAsync(null).get(20,TimeUnit.SECONDS);
            for(String descriptor : List.of("()I","()Ljava/lang/String;")) {
                var ref = new SymbolRef(SymbolRef.Kind.METHOD,"Lprobe/ReturnClash;",null,"value",descriptor);
                var result = success(server,request(ref,"OUTGOING"));
                assertEquals("RESOLVED",result.path("outcome").asText());
                assertEquals(descriptor,result.path("queriedRef").path("originalDescriptor").asText());
            }
        } finally { runtime.close(); }
    }
	@Test
	void nativeSaveHoldingRepositoryMonitorRejectsReferencesImmediately() throws Exception {
		Path jar = SymbolFixtureSupport.referenceFixture(dir);
		CountDownLatch enteredSave = new CountDownLatch(1);
		CountDownLatch releaseSave = new CountDownLatch(1);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), args -> new ProjectRuntime.ProjectEngine() {
			private final JadxDecompiler jadx = new JadxDecompiler(args);
			@Override public void load() { jadx.load(); }
			@Override public JadxDecompiler decompiler() { return jadx; }
			@Override public void close() { jadx.close(); }
		}, (repository, target, session, revision) -> {
			synchronized (repository) {
				enteredSave.countDown();
				try {
					if (!releaseSave.await(10, TimeUnit.SECONDS)) throw new IOException("Timed out holding native save");
				} catch (InterruptedException failure) {
					Thread.currentThread().interrupt();
					throw new IOException(failure);
				}
				return repository.save(target, session, revision);
			}
		});
		try (HttpApiServer server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			String session = runtime.projectSnapshot().revisions().sessionId();
			CompletableFuture<Void> save = CompletableFuture.runAsync(() -> {
				try { runtime.saveProject(dir.resolve("busy.jadx"), session, 0L); }
				catch (IOException failure) { throw new CompletionException(failure); }
			});
			try {
				assertTrue(enteredSave.await(5, TimeUnit.SECONDS));
				var q = request(method("entry", "()V"), "OUTGOING");
                var busy = HTTP.sendAsync(HttpRequest.newBuilder(uri(server,"/references/query"))
                        .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(q.toString())).build(),
                        HttpResponse.BodyHandlers.ofString()).get(2,TimeUnit.SECONDS);
                assertEquals(409,busy.statusCode(),busy.body());
                assertEquals("PROJECT_BUSY",JSON.readTree(busy.body()).path("error").path("code").asText());
			} finally { releaseSave.countDown(); save.get(10, TimeUnit.SECONDS); }
		} finally { releaseSave.countDown(); runtime.close(); }
	}

    @Test void graphObservationChangesAtSameLogicalRevision() throws Exception {
        Path jar = SymbolFixtureSupport.referenceFixture(dir);
        ProjectRuntime runtime = new ProjectRuntime(null,List.of(jar),List.of(dir));
        try(HttpApiServer server = new HttpApiServer("127.0.0.1",0,runtime)) {
            server.start(); runtime.initializeAsync(null).get(20,TimeUnit.SECONDS);
            var q=request(method("helper","()V"),"INCOMING").put("pageSize",1);
            var before=success(server,q);

            var decompiled=post(server,"/decompile","{\"ref\":{\"kind\":\"CLASS\",\"originalClassDescriptor\":\"Lprobe/ReferenceLate;\"}}");
            assertEquals(200,decompiled.statusCode());
            var after=success(server,q);

            assertEquals(before.path("logicalRevision"),after.path("logicalRevision"));
            assertNotEquals(before.path("snapshotId"),after.path("snapshotId"));
            q.put("cursor",before.path("nextCursor").asText()); error(server,q,409,"STALE_REVISION");
        } finally {runtime.close();}
    }

    @Test void nativeMappingsAndUnsavedEditsPreserveOriginalEdgesAndNeverSave() throws Exception {
        Path fixture=Path.of("tests/fixtures/native-project").toAbsolutePath();
        for(String file:List.of("sample.jar.jadx","sample.jar","second.jar","sample.tiny"))
            Files.copy(fixture.resolve(file),dir.resolve(file));
        Path project=dir.resolve("sample.jar.jadx");
        var document=dev.libjadx.project.NativeProjectDocument.open(project);
        byte[] original=Files.readAllBytes(project), mapping=Files.readAllBytes(dir.resolve("sample.tiny"));
        var runtime=new ProjectRuntime(project,document.getInputFiles(),List.of(dir));
        try(var server=new HttpApiServer("127.0.0.1",0,runtime)) {
            server.start(); runtime.initializeAsync(document).get(20,TimeUnit.SECONDS);
            var ref=new SymbolRef(SymbolRef.Kind.METHOD,"Lprobe/Sample;",null,"caller","()I");
            var q=request(ref,"OUTGOING"); var before=success(server,q);
            assertEquals(1,count(before,"CALL","answer","()I"));
            assertFalse(runtime.projectSnapshot().dirty());
            var data=document.getCodeData();
            data.setRenames(List.of(new jadx.api.data.impl.JadxCodeRename(jadx.api.data.impl.JadxNodeRef.forCls("probe.Sample"),"UnsavedReferenceAlias")));
            data.setComments(List.of(new jadx.api.data.impl.JadxCodeComment(jadx.api.data.impl.JadxNodeRef.forCls("probe.Sample"),"unsaved reference comment")));
            runtime.replaceCodeData(data,0);
            var after=success(server,q);
            assertEquals(before.path("edges"),after.path("edges"));
            assertNotEquals(before.path("snapshotId"),after.path("snapshotId"));
            assertEquals(1,runtime.projectSnapshot().revisions().logicalRevision());
            assertTrue(runtime.projectSnapshot().dirty());
            assertArrayEquals(original,Files.readAllBytes(project));
            assertArrayEquals(mapping,Files.readAllBytes(dir.resolve("sample.tiny")));
        } finally {runtime.close();}
        assertArrayEquals(original,Files.readAllBytes(project));
    }

    @Test void optionalSitesDoNotConfuseOverloadsOrInlinedCallers() throws Exception {
        Path jar=SymbolFixtureSupport.referenceFixture(dir);
        var runtime=new ProjectRuntime(null,List.of(jar),List.of(dir));
        try(var server=new HttpApiServer("127.0.0.1",0,runtime)) {
            server.start();runtime.initializeAsync(null).get(20,TimeUnit.SECONDS);
            var q=request(method("entry","()V"),"OUTGOING").put("includeSourceSites",true);
            var page=success(server,q);
            for(var edge:page.path("edges")) {
                if(edge.path("targetRef").path("originalName").asText().equals("helper"))
                    assertEquals(edge.path("targetRef").path("originalDescriptor").asText().equals("()V")?8:1,edge.path("sourceSites").size());
                if(edge.path("relation").asText().equals("UNRESOLVED_CALL")) assertEquals(0,edge.path("sourceSites").size());
            }
            var incoming=success(server,request(method("helper","()V"),"INCOMING").put("includeSourceSites",true));
            for(var edge:incoming.path("edges")) {
                if(edge.path("sourceRef").path("originalName").asText().startsWith("lambda$"))
                    assertEquals(0,edge.path("sourceSites").size());
                for(var site:edge.path("sourceSites"))
                    assertEquals(edge.path("sourceRef").path("originalClassDescriptor").asText().replace("$1;", ";"),
                            site.path("sourceOwnerRef").path("originalClassDescriptor").asText());
            }
            var recursive=success(server,request(method("helper","(I)I"),"OUTGOING"));
            assertEquals(0,recursive.path("edges").size());
            assertEquals("PARTIAL",recursive.path("coverage").path("status").asText());
            var dispatch=success(server,request(method("dispatch","(Lprobe/ReferenceApi;Lprobe/ReferenceBase;)V"),"OUTGOING"));
            for(var edge:dispatch.path("edges")) assertNotEquals("Lprobe/ReferenceFixture;",edge.path("targetRef").path("originalClassDescriptor").asText());
        } finally {runtime.close();}
    }

    @Test void collectionLimitReturnsTypedFailureWithoutTruncatedSuccess() throws Exception {
        Path jar=SymbolFixtureSupport.referenceFixture(dir);
        var runtime=new ProjectRuntime(null,List.of(jar),List.of(dir));
        try(var server=new HttpApiServer("127.0.0.1",0,runtime)) {
            server.start();runtime.initializeAsync(null).get(20,TimeUnit.SECONDS);
            runtime.withPrimarySymbolRead("limit-fixture",context->{
                var cls=JadxSymbolAdapter.visibleClass(context.decompiler(),"Lprobe/ReferenceFixture;",0);
                cls.getCodeInfo();
                var helper=JadxReferenceProbeTest.method(cls,"helper","()V");
                var entry=JadxReferenceProbeTest.method(cls,"entry","()V");
                // Deliberately inflate the real node list to test the collection guard,
                // not reference extraction feasibility or a claimed real-world graph.
                helper.getMethodNode().setUseIn(Collections.nCopies(10001,entry.getMethodNode()));
                return null;
            });
            error(server,request(method("helper","()V"),"INCOMING"),429,"RESOURCE_LIMIT");
        } finally {runtime.close();}
    }

    static long count(JsonNode page,String relation,String name,String descriptor) {
        long count=0; for(var edge:page.path("edges")) if(edge.path("relation").asText().equals(relation)
                && edge.path("targetRef").path("originalName").asText().equals(name)
                && edge.path("targetRef").path("originalDescriptor").asText().equals(descriptor)) count++; return count;
    }
    static boolean hasSource(JsonNode page,String name) {
        for(var edge:page.path("edges")) if(edge.path("sourceRef").path("originalName").asText().equals(name)) return true; return false;
    }
    static JsonNode success(HttpApiServer server,ObjectNode query) throws Exception {
        var response=post(server,"/references/query",query.toString()); assertEquals(200,response.statusCode(),response.body());
        assertEquals("no-store",response.headers().firstValue("Cache-Control").orElseThrow());
        assertTrue(response.headers().firstValue("X-Request-Id").isPresent());
        var result=JSON.readTree(response.body());
        OpenApiExampleValidator.assertValid(new ObjectMapper(new YAMLFactory()).readTree(Path.of("openapi/openapi.yaml").toFile()),"ReferencePage",result);
        return result;
    }
    static void error(HttpApiServer server,ObjectNode query,int status,String code) throws Exception {
        var response=post(server,"/references/query",query.toString()); assertEquals(status,response.statusCode(),response.body());
        assertEquals(code,JSON.readTree(response.body()).path("error").path("code").asText());
        OpenApiExampleValidator.assertValid(new ObjectMapper(new YAMLFactory()).readTree(Path.of("openapi/openapi.yaml").toFile()),
                "ErrorEnvelope",JSON.readTree(response.body()));
    }
    static HttpResponse<String> post(HttpApiServer server,String path,String body) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(uri(server,path)).timeout(java.time.Duration.ofSeconds(5))
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    static URI uri(HttpApiServer server,String path) { return URI.create("http://127.0.0.1:"+server.localPort()+"/api/v1"+path); }
}
