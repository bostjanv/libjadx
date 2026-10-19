package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;
import static dev.libjadx.app.ReferenceEndpointsTest.JSON;
import static dev.libjadx.app.ReferenceEndpointsTest.post;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.http.HttpApiServer;
import dev.libjadx.jadxadapter.JadxSymbolAdapter;
import dev.libjadx.probes.HierarchyFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ControlFlowEndpointsTest {
    @TempDir Path dir;
    static final String ROUTE = "/analysis/control-flow";
    static SymbolRef ref(String name, String descriptor) {
        return new SymbolRef(SymbolRef.Kind.METHOD, "Lprobe/CfgCases;", null, name, descriptor);
    }
    static ObjectNode query(SymbolRef ref) { return JSON.createObjectNode().set("ref", JSON.valueToTree(ref)); }
    static JsonNode facts(HttpApiServer server, ObjectNode query) throws Exception {
        var response = post(server, ROUTE, query.toString());
        assertEquals(200, response.statusCode(), response.body()); return JSON.readTree(response.body());
    }
    static void error(HttpApiServer server, ObjectNode query, int status, String code) throws Exception {
        checkError(post(server, ROUTE, query.toString()), status, code);
    }
    static void checkError(HttpResponse<String> response, int status, String code) throws Exception {
        assertEquals(status, response.statusCode(), response.body());
        assertEquals(code, JSON.readTree(response.body()).path("error").path("code").asText());
    }
    Path fixture() throws Exception {
        return HierarchyFixture.dex(dir, "cfg.dex", Files.readString(Path.of("tests/fixtures/cfg/CfgCases.smali")));
    }
    static List<Integer> ints(JsonNode array, String field) {
        List<Integer> result = new ArrayList<>(); array.forEach(v -> result.add(v.path(field).intValue())); return result;
    }
    static void sameFacts(JsonNode a, JsonNode b) {
        for (String field : List.of("instructions", "payloads", "branches", "switches", "arrayData", "tries"))
            assertEquals(a.path(field), b.path(field), field);
    }
    static void noFacts(JsonNode a) {
        for (String field : List.of("instructions", "payloads", "branches", "switches", "arrayData", "tries"))
            assertEquals(0, a.path(field).size(), field);
    }

    @Test void independentOracleReadOnlyLifecycleAndSnapshotBinding() throws Exception {
        Path dex = fixture(); byte[] original = Files.readAllBytes(dex);
        var runtime = new ProjectRuntime(null, List.of(dex), List.of(dir));
        String oldSnapshot, oldSession;
        Path project = dir.resolve("saved.jadx");
        try (var server = new HttpApiServer("127.0.0.1", 0, runtime)) {
            server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
            var revision = runtime.projectSnapshot().revisions();
            var state = runtime.withPrimarySymbolRead("state", c -> JadxSymbolAdapter.visibleClass(c.decompiler(),
                    "Lprobe/CfgCases;", 0).getClassNode().getState());
            var q = query(ref("branchLoop", "(I)I"));
            var flow = facts(server, q);
            oldSnapshot = flow.path("snapshotId").asText(); oldSession = flow.path("sessionId").asText();
            assertEquals("AVAILABLE", flow.path("availability").asText());
            assertEquals("EXACT", flow.path("precision").asText());
            assertEquals("PARTIAL", flow.path("coverage").asText());
            assertEquals("DEX_CODE_UNIT_16", flow.path("unit").asText());
            assertEquals("LOADED_METHOD_INSNS_START", flow.path("base").asText());
            assertEquals(List.of(0, 1, 3, 5, 7, 8, 10, 12, 13), ints(flow.path("instructions"), "offset"));
            assertEquals(List.of(13, 8, 10, 1), ints(flow.path("branches"), "target"));
            assertEquals(List.of(3, 5, 8, 13), ints(flow.path("branches"), "nextOffset"));
            assertEquals(flow, facts(server, q));
            assertEquals(List.of(5), ints(facts(server, query(ref("jump", "()I"))).path("branches"), "target"));
            for (String method : List.of("packed", "sparse")) {
                var page = facts(server, query(ref(method, "(I)I")));
                var sw = page.path("switches").get(0);
                assertEquals(1, sw.path("offset").intValue());
                assertEquals(14, sw.path("payloadOffset").intValue());
                assertEquals(4, sw.path("defaultOffset").intValue());
                assertEquals(method.equals("packed") ? List.of(4, 5) : List.of(-5, 1000), ints(sw.path("cases"), "key"));
                assertEquals(List.of(7, 10), ints(sw.path("cases"), "target"));
                assertEquals(List.of(14), ints(page.path("payloads"), "offset"));
                assertEquals(method.equals("packed") ? 8 : 10, page.path("payloads").get(0).path("length").intValue());
                assertTrue(ints(page.path("instructions"), "offset").contains(13), "Preserve NOP without inferring alignment intent");
                assertFalse(ints(page.path("instructions"), "offset").contains(14));
            }
            var array = facts(server, query(ref("array", "()I")));
            assertEquals(JSON.readTree("[{\"offset\":3,\"payloadOffset\":10}]"), array.path("arrayData"));
            assertEquals(JSON.readTree("[{\"offset\":10,\"length\":8,\"kind\":\"ARRAY_DATA\"}]"), array.path("payloads"));
            var catches = facts(server, query(ref("catches", "(Ljava/lang/RuntimeException;[I)I")));
            assertEquals(JSON.readTree("[{\"start\":1,\"endExclusive\":5,\"handlers\":[{\"type\":\"Ljava/lang/RuntimeException;\",\"target\":6},{\"type\":null,\"target\":10}]}]"), catches.path("tries"));
            var straight = facts(server, query(ref("straight", "()I")));
            assertEquals(List.of(0, 2), ints(straight.path("instructions"), "offset"));
            assertEquals(0, straight.path("branches").size());
            assertEquals(state, runtime.withPrimarySymbolRead("state", c -> JadxSymbolAdapter.visibleClass(c.decompiler(),
                    "Lprobe/CfgCases;", 0).getClassNode().getState()));
            assertEquals(revision, runtime.projectSnapshot().revisions());
            assertFalse(runtime.projectSnapshot().dirty()); assertArrayEquals(original, Files.readAllBytes(dex));
            assertFalse(Files.exists(project));
            error(server, q.deepCopy().put("strict", true), 409, "INCOMPLETE_ANALYSIS");
            assertEquals(200, post(server, "/decompile", query(ref("branchLoop", "(I)I")).toString()).statusCode());
            assertEquals(flow, facts(server, q), "Java generation does not change reader identity");
            q.put("expectedSessionId", revision.sessionId()).put("expectedLogicalRevision", revision.logicalRevision());
            new EditBatchService(runtime).apply(new dev.libjadx.core.edits.EditDtos.Request(null, null, List.of(
                    new dev.libjadx.core.edits.EditDtos.Operation(dev.libjadx.core.edits.EditDtos.Kind.RENAME,
                            ref("branchLoop", "(I)I"), "flowAlias", null, null),
                    new dev.libjadx.core.edits.EditDtos.Operation(dev.libjadx.core.edits.EditDtos.Kind.SET_COMMENT,
                            ref("branchLoop", "(I)I"), null, "unsaved flow comment", "LINE"))));
            error(server, q, 409, "STALE_REVISION");
            q.remove(List.of("expectedSessionId", "expectedLogicalRevision"));
            var edited = facts(server, q); sameFacts(flow, edited); assertNotEquals(flow.path("snapshotId"), edited.path("snapshotId"));
            assertTrue(runtime.projectSnapshot().dirty()); assertArrayEquals(original, Files.readAllBytes(dex));
            assertFalse(Files.exists(project));
            runtime.saveProject(project, null, null);
            var saved = facts(server, q); sameFacts(flow, saved);
            assertEquals(edited.path("logicalRevision"), saved.path("logicalRevision"));
            assertNotEquals(edited.path("snapshotId"), saved.path("snapshotId"));
            byte[] nativeBytes = Files.readAllBytes(project);
            runtime.reloadProject(false, oldSession, saved.path("logicalRevision").longValue());
            var reloaded = facts(server, q); sameFacts(flow, reloaded);
            assertNotEquals(saved.path("snapshotId"), reloaded.path("snapshotId"));
            assertArrayEquals(nativeBytes, Files.readAllBytes(project));
            Path mapping = Files.writeString(dir.resolve("flow.tiny"), "tiny\t2\t0\toriginal\tmapped\nc\tprobe/CfgCases\tprobe/MappedFlow\n");
            runtime.updateMappingsPath(mapping, oldSession, reloaded.path("logicalRevision").longValue());
            var configured = facts(server, q); sameFacts(flow, configured);
            assertNotEquals(reloaded.path("snapshotId"), configured.path("snapshotId"));
            runtime.saveProject(null, null, null);
            Files.writeString(mapping, "tiny\t2\t0\toriginal\tmapped\nc\tprobe/CfgCases\tprobe/RemappedFlow\n");
            runtime.reloadProject(false, oldSession, configured.path("logicalRevision").longValue());
            var remapped = facts(server, q); sameFacts(flow, remapped);
            assertNotEquals(configured.path("snapshotId"), remapped.path("snapshotId"));
            Path replacement = HierarchyFixture.dex(dir, "replacement.dex", Files.readString(Path.of("tests/fixtures/cfg/CfgCases.smali"))
                    .replace("const/16 v0, 17", "const/16 v0, 18"));
            Files.copy(replacement, dex, StandardCopyOption.REPLACE_EXISTING);
            runtime.reloadProject(false, oldSession, remapped.path("logicalRevision").longValue());
            var inputReload = facts(server, q); sameFacts(flow, inputReload);
            assertNotEquals(remapped.path("snapshotId"), inputReload.path("snapshotId"));
            runtime.withPrimarySymbolRead("held", c -> {
                try { error(server, q, 409, "PROJECT_BUSY"); } catch (Exception e) { throw new RuntimeException(e); }
                return null;
            });
            if ("true".equals(System.getenv("LIBJADX_RECORD_FLOW_EXAMPLES")))
                Files.writeString(Path.of("openapi/examples/control-flow.json"), flow.toPrettyString() + "\n");
        } finally { runtime.close(); }
        var document = dev.libjadx.project.NativeProjectDocument.open(project);
        var restarted = new ProjectRuntime(project, document.getInputFiles(), List.of(dir));
        try (var server = new HttpApiServer("127.0.0.1", 0, restarted)) {
            server.start(); restarted.initializeAsync(document).get(20, TimeUnit.SECONDS);
            var page = facts(server, query(ref("branchLoop", "(I)I")));
            assertNotEquals(oldSnapshot, page.path("snapshotId").asText()); assertNotEquals(oldSession, page.path("sessionId").asText());
            error(server, query(ref("branchLoop", "(I)I")).put("expectedSessionId", oldSession).put("expectedLogicalRevision", 0), 409, "STALE_REVISION");
        } finally { restarted.close(); }
        Files.copy(dex, Files.createDirectories(Path.of("build/control-flow-fixture")).resolve("cfg.dex"), StandardCopyOption.REPLACE_EXISTING);
    }

    @Test void unavailableFailureStrictValidationAndReadinessAreDistinct() throws Exception {
        Path dex = fixture(), broken = HierarchyFixture.dex(dir, "broken.dex", Files.readString(Path.of("tests/fixtures/smali/Disassembly.smali")));
        Path jar = SymbolFixtureSupport.referenceFixture(dir);
        var runtime = new ProjectRuntime(null, List.of(dex, broken, jar), List.of(dir));
        try (var server = new HttpApiServer("127.0.0.1", 0, runtime)) {
            server.start(); error(server, query(ref("straight", "()I")), 503, "PROJECT_NOT_READY");
            runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
            for (var target : List.of(ref("absent", "()V"), InstructionReferenceEndpointsTest.ref("ReferenceFixture", "entry", "()V"))) {
                var page = facts(server, query(target)); assertEquals("UNAVAILABLE", page.path("availability").asText()); noFacts(page);
                error(server, query(target).put("strict", true), 409, "INCOMPLETE_ANALYSIS");
            }
            for (var target : List.of(ref("missing", "()V"), new SymbolRef(SymbolRef.Kind.METHOD, "Lprobe/CfgCases;", "input.dex", "straight", "()I"))) {
                var page = facts(server, query(target).put("strict", true));
                assertEquals(target.inputIdentity() == null ? "NOT_FOUND" : "PROVENANCE_UNAVAILABLE", page.path("outcome").asText());
                assertEquals("UNAVAILABLE", page.path("availability").asText()); noFacts(page);
            }
            var badJava = query(InstructionReferenceEndpointsTest.ref("Disassembly", "broken", "()I"));
            var before = facts(server, badJava); assertEquals("AVAILABLE", before.path("availability").asText());
            var java = JSON.readTree(post(server, "/decompile", badJava.toString()).body());
            assertTrue(java.toString().contains("FAILED") || java.toString().contains("ERROR"), java.toString());
            assertEquals(before, facts(server, badJava));
            assertEquals("AVAILABLE", facts(server, query(ref("straight", "()I"))).path("availability").asText());
            for (var invalid : List.of(query(SymbolRef.classRef("Lprobe/CfgCases;")), query(ref("straight", "()I")).putNull("strict"),
                    query(ref("straight", "()I")).put("view", "INSTRUCTIONS"), query(ref("straight", "()I")).put("strict", "true"),
                    query(ref("straight", "()I")).put("expectedLogicalRevision", 0), query(ref("straight", "()I")).putNull("expectedSessionId"),
                    query(ref("straight", "()I")).put("expectedSessionId", "x").put("expectedLogicalRevision", 0),
                    query(ref("straight", "()I")).put("expectedSessionId", UUID.randomUUID().toString()).put("expectedLogicalRevision", 0.5)))
                error(server, invalid, 400, "INVALID_REQUEST");
            checkError(post(server, ROUTE, "{\"ref\":" + JSON.writeValueAsString(ref("straight", "()I")) + ",\"strict\":false,\"strict\":true}"), 400, "INVALID_REQUEST");
            checkError(post(server, "/analysis/cfg", "{}"), 501, "OPERATION_NOT_IMPLEMENTED");
        } finally { runtime.close(); }
    }

    @Test void malformedTargetsPayloadsTriesAndOversizedDeclarationsAreAtomic() throws Exception {
        Path dex = fixture();
        Map<String, Integer> code = codeOffsets(dex);
        int packed = code.get("packed"), catches = code.get("catches"), array = code.get("array");
        Map<String, Consumer<ByteBuffer>> mutations = new LinkedHashMap<>();
        mutations.put("unknown", b -> b.put(packed + 16, (byte) 0x73));
        mutations.put("truncated", b -> b.putInt(packed + 12, 2));
        mutations.put("middle", b -> b.putInt(packed + 16 + 14 * 2 + 8, 1)); // switch at 1 + 1 is inside itself
        mutations.put("payloadTarget", b -> b.putInt(packed + 16 + 14 * 2 + 8, 13)); // switch at 1 -> payload 14
        mutations.put("overflow", b -> b.putInt(packed + 16 + 4, Integer.MAX_VALUE));
        mutations.put("wrongKind", b -> b.put(packed + 16 + 2, (byte) 0x2c));
        mutations.put("wrongAddress", b -> b.putInt(packed + 16 + 4, 12));
        mutations.put("payloadExtent", b -> b.putInt(array + 16 + 10 * 2 + 4, -1));
        mutations.put("tryEnd", b -> b.putShort(catches + 16 + 14 * 2 + 4, (short) 2)); // [1,3) ends inside IF
        mutations.put("handlerOffset", b -> b.putShort(catches + 16 + 14 * 2 + 6, (short) 0));
        mutations.put("handlerTarget", b -> b.put(catches + 16 + 14 * 2 + 8 + 3, (byte) 3));
        mutations.put("unitsLimit", b -> b.putInt(packed + 12, 4097));
        mutations.put("unsignedUnitsLimit", b -> b.putInt(packed + 12, -1));
        mutations.put("casesLimit", b -> b.putShort(packed + 16 + 14 * 2 + 2, (short) 1025));
        mutations.put("triesLimit", b -> b.putShort(catches + 6, (short) 257));
        mutations.put("handlersLimit", b -> { int p = catches + 16 + 14 * 2 + 8 + 1; b.put(p, (byte) 0x81); b.put(p + 1, (byte) 0x08); }); // SLEB +1025
        mutations.put("handlersMinLimit", b -> {
            int p = catches + 16 + 14 * 2 + 8 + 1;
            b.putInt(p, 0x80808080); b.put(p + 4, (byte) 0x78); // SLEB Integer.MIN_VALUE, abs must not overflow
        });
        for (var entry : mutations.entrySet()) {
            byte[] bytes = Files.readAllBytes(dex); entry.getValue().accept(ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN));
            Path changed = writeDex(entry.getKey(), bytes);
            var runtime = new ProjectRuntime(null, List.of(changed), List.of(dir));
            try (var server = new HttpApiServer("127.0.0.1", 0, runtime)) {
                server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
                String method = entry.getKey().startsWith("handler") || entry.getKey().startsWith("try") || entry.getKey().startsWith("tries") ? "catches"
                        : entry.getKey().equals("payloadExtent") ? "array" : "packed";
                var q = query(ref(method, method.equals("catches") ? "(Ljava/lang/RuntimeException;[I)I" : method.equals("array") ? "()I" : "(I)I"));
                if (entry.getKey().endsWith("Limit")) {
                    error(server, q, 429, "RESOURCE_LIMIT"); error(server, q.put("strict", true), 429, "RESOURCE_LIMIT");
                } else {
                    var failed = facts(server, q); assertEquals("FAILED", failed.path("availability").asText(), entry.getKey());
                    assertEquals("UNAVAILABLE", failed.path("precision").asText()); noFacts(failed);
                    error(server, q.put("strict", true), 409, "INCOMPLETE_ANALYSIS");
                }
                assertEquals("AVAILABLE", facts(server, query(ref("straight", "()I"))).path("availability").asText());
                assertFalse(runtime.projectSnapshot().dirty());
            } finally { runtime.close(); }
            if (entry.getKey().equals("unknown") || entry.getKey().equals("unitsLimit"))
                Files.copy(changed, Files.createDirectories(Path.of("build/control-flow-fixture")).resolve(entry.getKey() + ".dex"), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Test void inclusiveIndependentLimitsAndActualUtf8ResponseBudget() throws Exception {
        Map<String, String> bodies = new LinkedHashMap<>();
        bodies.put("unitsExact", "const-wide v0, 0\n".repeat(819) + "return-void\n"); // 819*5 + 1 = 4096 units, 820 records
        bodies.put("unitsOver", bodies.get("unitsExact") + "nop\n");
        bodies.put("recordsExact", "nop\n".repeat(1023) + "return-void\n");
        bodies.put("recordsOver", "nop\n" + bodies.get("recordsExact"));
        for (int count : List.of(1024, 1025)) bodies.put(count == 1024 ? "casesExact" : "casesOver",
                "packed-switch v0, :data\n:ret\nreturn-void\n:data\n.packed-switch -512\n" + ":ret\n".repeat(count) + ".end packed-switch\n");
        bodies.put("triesExact", tryBody(256, 1, "Ljava/lang/Exception;"));
        bodies.put("triesOver", tryBody(257, 1, "Ljava/lang/Exception;"));
        bodies.put("handlersExact", tryBody(256, 4, "Lprobe/E;"));
        bodies.put("handlersOver", tryBody(205, 5, "Lprobe/E;")); // 205*5 = 1025 aggregate, but only 5 encoded
        for (int count : List.of(1024, 1025)) {
            StringBuilder body = new StringBuilder(":start\nnop\n:end\nreturn-void\n:handler\nmove-exception v0\nreturn-void\n");
            for (int h = 0; h < count; h++) body.append(".catch Lprobe/E").append(h).append("; {:start .. :end} :handler\n");
            bodies.put(count == 1024 ? "encodedHandlersExact" : "encodedHandlersOver", body.toString());
        }
        bodies.put("typeExact", tryBody(1, 1, "L" + "X".repeat(1022) + ";"));
        bodies.put("typeOver", tryBody(1, 1, "L" + "X".repeat(1023) + ";"));
        bodies.put("bytesOver", tryBody(256, 4, "L" + "界".repeat(1022) + ";")); // >3 MiB UTF-8 despite legal counts
        for (var entry : bodies.entrySet()) {
            String source = ".class public Lprobe/Bounds;\n.super Ljava/lang/Object;\n.method public static run()V\n.registers 2\n"
                    + entry.getValue() + ".end method\n";
            Path dex = HierarchyFixture.dex(dir, entry.getKey() + ".dex", source);
            var runtime = new ProjectRuntime(null, List.of(dex), List.of(dir));
            try (var server = new HttpApiServer("127.0.0.1", 0, runtime)) {
                server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
                var q = query(new SymbolRef(SymbolRef.Kind.METHOD, "Lprobe/Bounds;", null, "run", "()V"));
                if (entry.getKey().endsWith("Over")) {
                    error(server, q, 429, "RESOURCE_LIMIT"); error(server, q.put("strict", true), 429, "RESOURCE_LIMIT");
                } else {
                    var page = facts(server, q); assertEquals("AVAILABLE", page.path("availability").asText(), entry.getKey());
                    switch (entry.getKey()) {
                        case "unitsExact" -> assertEquals(4096, page.path("codeUnits").intValue());
                        case "recordsExact" -> assertEquals(1024, page.path("instructions").size());
                        case "casesExact" -> assertEquals(1024, page.path("switches").get(0).path("cases").size());
                        case "triesExact" -> assertEquals(256, page.path("tries").size());
                        case "handlersExact" -> assertEquals(1024, page.path("tries").findValues("target").size());
                        case "encodedHandlersExact" -> assertEquals(1024, page.path("tries").get(0).path("handlers").size());
                        case "typeExact" -> assertEquals(1024, page.path("tries").get(0).path("handlers").get(0).path("type").asText().length());
                    }
                }
            } finally { runtime.close(); }
        }
    }

    @Test void exactUtf8ByteCeilingAndOneByteOverUseTheRealResponse() throws Exception {
        var q = query(new SymbolRef(SymbolRef.Kind.METHOD, "Lprobe/Bytes;", null, "run", "()V"));
        int baseline = 0, commonUnicode = 0, remainder = 0;
        for (int attempt = 0; attempt < 3; attempt++) {
            // 255 ranges share four handler types; the last range has four unique types.
            // All descriptors have exactly 1024 UTF-16 units, so only UTF-8 widths change.
            String common = "界".repeat(commonUnicode) + "X".repeat(1021 - commonUnicode);
            String body = tryBody(256, 4, "L" + common + "A;");
            for (int h = 0; h < 4; h++) {
                int delta = h == 0 ? remainder + (attempt == 2 ? 1 : 0) : 0;
                String unique = "界".repeat(delta / 2) + (delta % 2 == 1 ? "é" : "")
                        + "Y".repeat(1021 - delta / 2 - delta % 2);
                char suffix = (char) ('A' + h);
                body = body.replace(".catch L" + common + suffix + "; {:s255", ".catch L" + unique + suffix + "; {:s255");
            }
            Path dex = HierarchyFixture.dex(dir, "bytes" + attempt + ".dex",
                    ".class public Lprobe/Bytes;\n.super Ljava/lang/Object;\n.method public static run()V\n.registers 2\n" + body + ".end method\n");
            var runtime = new ProjectRuntime(null, List.of(dex), List.of(dir));
            try (var server = new HttpApiServer("127.0.0.1", 0, runtime)) {
                server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
                var response = post(server, ROUTE, q.toString());
                if (attempt == 2) { checkError(response, 429, "RESOURCE_LIMIT"); continue; }
                assertEquals(200, response.statusCode(), response.body());
                int bytes = response.body().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                if (attempt == 0) {
                    baseline = bytes;
                    // Each ASCII -> three-byte character adds two bytes, in 255*4 occurrences.
                    commonUnicode = (2097152 - baseline) / (2 * 1020);
                    remainder = 2097152 - baseline - commonUnicode * 2 * 1020;
                    assertTrue(commonUnicode < 1021 && remainder < 2040);
                } else {
                    assertEquals(2097152, bytes, "Inclusive actual UTF-8 JSON ceiling, not a record-count estimate");
                }
            } finally { runtime.close(); }
        }
    }

    static String tryBody(int count, int handlers, String type) {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < count; i++) body.append(":s").append(i).append("\nnop\n:e").append(i).append("\nnop\n");
        body.append("return-void\n:handler\nmove-exception v0\nreturn-void\n");
        for (int i = 0; i < count; i++) for (int h = 0; h < handlers; h++)
            body.append(".catch ").append(type.substring(0, type.length() - 2)).append((char) ('A' + h)).append("; {:s")
                    .append(i).append(" .. :e").append(i).append("} :handler\n");
        return body.toString();
    }

    Map<String, Integer> codeOffsets(Path dex) throws Exception {
        var runtime = new ProjectRuntime(null, List.of(dex), List.of(dir));
        try {
            runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
            return runtime.withPrimarySymbolRead("offsets", c -> {
                Map<String, Integer> result = new HashMap<>();
                JadxSymbolAdapter.visibleClass(c.decompiler(), "Lprobe/CfgCases;", 0).getClassNode().getMethods()
                        .forEach(m -> { if (m.getCodeReader() != null) result.put(m.getName(), m.getCodeReader().getCodeOffset()); });
                return result;
            });
        } finally { runtime.close(); }
    }
    Path writeDex(String name, byte[] bytes) throws Exception {
        var signature = java.security.MessageDigest.getInstance("SHA-1"); signature.update(bytes, 32, bytes.length - 32);
        System.arraycopy(signature.digest(), 0, bytes, 12, 20);
        var checksum = new java.util.zip.Adler32(); checksum.update(bytes, 12, bytes.length - 12);
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(8, (int) checksum.getValue());
        return Files.write(dir.resolve(name + ".dex"), bytes);
    }
}
