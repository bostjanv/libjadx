package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;
import static dev.libjadx.app.ReferenceEndpointsTest.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.http.HttpApiServer;
import dev.libjadx.jadxadapter.JadxSymbolAdapter;
import dev.libjadx.probes.HierarchyFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InstructionReferenceEndpointsTest {
    @TempDir Path dir;
    static SymbolRef ref(String owner, String name, String descriptor) {
        return new SymbolRef(SymbolRef.Kind.METHOD, "Lprobe/" + owner + ";", null, name, descriptor);
    }
    static ObjectNode instructions(SymbolRef ref) { return request(ref, "OUTGOING").put("view", "INSTRUCTIONS"); }
    static final SymbolRef ENTRY = ref("InstructionRefs", "entry", "(Lprobe/InstructionRefs;Lmissing/Api;)V");

    @Test void occurrencesAreStableAcrossJavaGenerationArchivesAndUnsavedEdits() throws Exception {
        Path dex = HierarchyFixture.dex(dir, "refs.dex", Files.readString(Path.of("tests/fixtures/references/InstructionRefs.smali")));
        byte[] original = Files.readAllBytes(dex);
        Path archive = dir.resolve("refs.jar");
        try (var out = new JarOutputStream(Files.newOutputStream(archive))) {
            out.putNextEntry(new JarEntry("classes.dex")); out.write(original); out.closeEntry();
        }
        for (Path input : List.of(dex, archive)) {
            var runtime = new ProjectRuntime(null, List.of(input), List.of(dir));
            try (var server = new HttpApiServer("127.0.0.1", 0, runtime)) {
                server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
                var state = runtime.withPrimarySymbolRead("state", c -> JadxSymbolAdapter.visibleClass(c.decompiler(),
                        ENTRY.originalClassDescriptor(), 0).getClassNode().getState());
                var query = instructions(ENTRY);
                JsonNode page = success(server, query);
                assertEquals("AVAILABLE", page.path("instructionScan").path("availability").asText());
                assertEquals("LOADED_DEX_METHOD", page.path("scope").asText());
                assertEquals("EXACT", page.path("capabilities").path("loadedDexOffset").asText());
                assertEquals("UNAVAILABLE", page.path("capabilities").path("originalOffset").asText());
                assertEquals(List.of(2, 4, 6, 8, 10, 13, 16, 19, 22, 27), offsets(page));
                assertEquals(2, count(page, "CALL", "target", "()V"));
                assertEquals(1, count(page, "CALL", "entry", ENTRY.originalDescriptor()));
                assertEquals(2, page.path("edges").findValuesAsText("resolution").stream().filter("UNRESOLVED"::equals).count());
                assertEquals(List.of("FIELD_READ", "FIELD_WRITE", "FIELD_READ", "FIELD_WRITE"),
                        page.path("edges").findValuesAsText("relation").subList(0, 4));
                for (var edge : page.path("edges")) {
                    assertEquals("DEX_CODE_UNIT_16", edge.path("instruction").path("unit").asText());
                    assertEquals("LOADED_METHOD_INSNS_START", edge.path("instruction").path("base").asText());
                    assertTrue(edge.path("originalOffset").isNull());
                    assertEquals(0, edge.path("sourceSites").size());
                    assertTrue(edge.path("targetRef").path("inputIdentity").isNull());
                }
                assertEquals(state, runtime.withPrimarySymbolRead("state", c -> JadxSymbolAdapter.visibleClass(c.decompiler(),
                        ENTRY.originalClassDescriptor(), 0).getClassNode().getState()), "Instruction query must not generate Java");
                error(server, query.deepCopy().put("strict", true), 409, "INCOMPLETE_ANALYSIS");
                query.put("pageSize", 3);
                var first = success(server, query);
                List<Integer> all = new ArrayList<>(offsets(first));
                var next = first;
                while (!next.path("nextCursor").isNull()) {
                    query.put("cursor", next.path("nextCursor").asText()); next = success(server, query);
                    assertEquals(first.path("snapshotId"), next.path("snapshotId")); all.addAll(offsets(next));
                }
                assertEquals(offsets(page), all);
                query.put("cursor", first.path("nextCursor").asText()).put("view", "PAIRS");
                error(server, query, 400, "INVALID_REQUEST");
                query.put("view", "INSTRUCTIONS");
                assertEquals(200, post(server, "/decompile", "{\"ref\":" + JSON.writeValueAsString(ENTRY) + "}").statusCode());
                assertEquals(page.path("edges"), success(server, instructions(ENTRY)).path("edges"));
                success(server, query); // Java generation does not stale a raw-reader page.
                new EditBatchService(runtime).apply(new dev.libjadx.core.edits.EditDtos.Request(null, null, List.of(
                        new dev.libjadx.core.edits.EditDtos.Operation(dev.libjadx.core.edits.EditDtos.Kind.RENAME,
                                SymbolRef.classRef(ENTRY.originalClassDescriptor()), "UnsavedAlias", null, null))));
                error(server, query, 409, "STALE_REVISION");
                assertEquals(page.path("edges"), success(server, instructions(ENTRY)).path("edges"));
                assertTrue(runtime.projectSnapshot().dirty());
                assertArrayEquals(original, Files.readAllBytes(dex));
                if ("true".equals(System.getenv("LIBJADX_RECORD_REFERENCE_EXAMPLES")))
                    Files.writeString(Path.of("openapi/examples/references-instructions.json"), page.toPrettyString());
            } finally { runtime.close(); }
        }
        Path output = Files.createDirectories(Path.of("build/instruction-reference-fixture"));
        Files.copy(dex, output.resolve("refs.dex"), StandardCopyOption.REPLACE_EXISTING);
    }

    @Test void availabilityExclusionsValidationAndBusyRemainDistinct() throws Exception {
        Path dex = HierarchyFixture.dex(dir, "variants.dex", Files.readString(Path.of("tests/fixtures/references/InstructionVariants.smali")), 28);
        Path base = HierarchyFixture.dex(dir, "base.dex", Files.readString(Path.of("tests/fixtures/references/InstructionRefs.smali")));
        Path jar = SymbolFixtureSupport.referenceFixture(dir);
        var runtime = new ProjectRuntime(null, List.of(dex, base, jar), List.of(dir));
        try (var server = new HttpApiServer("127.0.0.1", 0, runtime)) {
            server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
            var variants = success(server, instructions(ref("InstructionVariants", "variants", "(J[[ILmissing/Api;)V")));
            assertEquals(List.of(0, 3, 6, 9, 12, 15, 18, 21, 24, 27), offsets(variants));
            var fields = success(server, instructions(ref("InstructionVariants", "fields", "(J[[I)V")));
            assertEquals(List.of("FIELD_READ", "FIELD_WRITE", "FIELD_READ", "FIELD_WRITE"), fields.path("edges").findValuesAsText("relation"));
            assertTrue(fields.path("edges").findValuesAsText("resolution").stream().allMatch("UNRESOLVED"::equals));
            assertEquals(List.of("J", "J", "[[I", "[[I"), fields.path("edges").findValuesAsText("originalDescriptor").stream()
                    .filter(d -> !d.startsWith("(")).toList());
            var payloads = success(server, instructions(ref("InstructionVariants", "payloads", "(I)V")));
            assertEquals("AVAILABLE", payloads.path("instructionScan").path("availability").asText());
            assertEquals(List.of(9, 32), offsets(payloads));
            var broken = instructions(ref("InstructionVariants", "broken", "()V"));
            var before = success(server, broken);
            post(server, "/decompile", "{\"ref\":" + broken.path("ref") + "}");
            assertEquals(List.of(0), offsets(success(server, broken)));
            assertEquals(before.path("edges"), success(server, broken).path("edges"));
            var excludedQuery = instructions(ref("InstructionVariants", "excluded", "(Ljava/lang/invoke/MethodHandle;)V"));
            var excluded = success(server, excludedQuery);
            assertEquals(JSON.valueToTree(List.of("const-method-handle", "const-method-type", "invoke-custom", "invoke-custom/range",
                            "invoke-polymorphic", "invoke-polymorphic/range")),
                    excluded.path("instructionScan").path("excludedOpcodes"));
            assertEquals(0, excluded.path("edges").size());
            error(server, excludedQuery.put("strict", true), 409, "INCOMPLETE_ANALYSIS");
            for (var target : List.of(ref("InstructionRefs", "target", "()V"), ref("InstructionRefs", "absent", "()V"),
                    ref("ReferenceFixture", "entry", "()V"), ref("InstructionRefs", "missing", "()V"))) {
                var empty = success(server, instructions(target));
                assertEquals(0, empty.path("edges").size());
                assertEquals(target.originalName().equals("target") ? "AVAILABLE" : "UNAVAILABLE",
                        empty.path("instructionScan").path("availability").asText());
            }
            for (var invalid : List.of(instructions(ENTRY).put("direction", "INCOMING"),
                    instructions(SymbolRef.classRef(ENTRY.originalClassDescriptor())), instructions(ENTRY).put("includeSourceSites", true),
                    instructions(ENTRY).put("view", "other"), instructions(ENTRY).putNull("view"),
                    instructions(ENTRY).set("relations", JSON.valueToTree(List.of("FIELD_USE")))))
                error(server, invalid, 400, "INVALID_REQUEST");
            runtime.withPrimarySymbolRead("held", c -> {
                try { error(server, instructions(ENTRY), 409, "PROJECT_BUSY"); }
                catch (Exception e) { throw new RuntimeException(e); }
                return null;
            });
        } finally { runtime.close(); }
        Path output = Files.createDirectories(Path.of("build/instruction-reference-fixture"));
        Files.copy(dex, output.resolve("variants.dex"), StandardCopyOption.REPLACE_EXISTING);
    }

    @Test void multidexDuplicatesExposeOnlyTheSelectedLoadedDefinitionWithoutInventedOrigin() throws Exception {
        String source = ".class public Lprobe/Duplicate;\n.super Ljava/lang/Object;\n"
                + ".method public static entry()V\n.registers 0\n%s"
                + "invoke-static {}, Lmissing/External;->%s()V\nreturn-void\n.end method\n";
        Path one = HierarchyFixture.dex(dir, "one.dex", source.formatted("", "first"));
        Path two = HierarchyFixture.dex(dir, "two.dex", source.formatted("nop\n", "second"));
        Path archive = dir.resolve("multidex.jar");
        try (var out = new JarOutputStream(Files.newOutputStream(archive))) {
            out.putNextEntry(new JarEntry("classes.dex")); Files.copy(one, out); out.closeEntry();
            out.putNextEntry(new JarEntry("classes2.dex")); Files.copy(two, out); out.closeEntry();
        }
        for (var inputs : List.of(List.of(archive), List.of(one, two))) {
            var runtime = new ProjectRuntime(null, inputs, List.of(dir));
            try (var server = new HttpApiServer("127.0.0.1", 0, runtime)) {
                server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
                var query = instructions(ref("Duplicate", "entry", "()V"));
                var page = success(server, query);
                assertEquals("RESOLVED", page.path("outcome").asText());
                assertEquals("AVAILABLE", page.path("instructionScan").path("availability").asText());
                assertEquals("PARTIAL", page.path("coverage").path("status").asText());
                assertEquals(1, page.path("edges").size(), "Discarded definitions must not be merged into occurrences");
                var edge = page.path("edges").get(0);
                String occurrence = edge.path("targetRef").path("originalName").asText() + ":" + offsets(page).getFirst();
                assertTrue(Set.of("first:0", "second:1").contains(occurrence), occurrence);
                assertTrue(edge.path("sourceRef").path("inputIdentity").isNull());
                assertTrue(edge.path("targetRef").path("inputIdentity").isNull());
                assertTrue(page.path("diagnostics").toString().contains("input/archive identity"));
                error(server, query.deepCopy().put("strict", true), 409, "INCOMPLETE_ANALYSIS");
                ((ObjectNode) query.path("ref")).put("inputIdentity", "classes.dex");
                var attributed = success(server, query);
                assertEquals("PROVENANCE_UNAVAILABLE", attributed.path("outcome").asText());
                assertEquals("UNAVAILABLE", attributed.path("instructionScan").path("availability").asText());
                assertEquals(0, attributed.path("edges").size());
            } finally { runtime.close(); }
        }
    }

    @Test void malformedMethodsFailRatherThanReturningSuccessfulEmptyScans() throws Exception {
        Path dex = HierarchyFixture.dex(dir, "valid.dex", Files.readString(Path.of("tests/fixtures/references/InstructionRefs.smali")));
        int code;
        var initial = new ProjectRuntime(null, List.of(dex), List.of(dir));
        try {
            initial.initializeAsync(null).get(20, TimeUnit.SECONDS);
            code = initial.withPrimarySymbolRead("offset", c -> JadxSymbolAdapter.visibleClass(c.decompiler(), ENTRY.originalClassDescriptor(), 0)
                    .getClassNode().getMethods().stream().filter(m -> m.getName().equals("entry")).findFirst().orElseThrow().getCodeReader().getCodeOffset());
        } finally { initial.close(); }
        for (String kind : List.of("unknown", "boundary", "limit")) {
            byte[] bytes = Files.readAllBytes(dex);
            if (kind.equals("unknown")) bytes[code + 16] = 0x73;
            else ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(code + 12, kind.equals("limit") ? 1_000_001 : 1);
            // Keep file integrity valid so this exercises malformed code, not loader checksum rejection.
            var signature = java.security.MessageDigest.getInstance("SHA-1");
            signature.update(bytes, 32, bytes.length - 32); System.arraycopy(signature.digest(), 0, bytes, 12, 20);
            var checksum = new java.util.zip.Adler32(); checksum.update(bytes, 12, bytes.length - 12);
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(8, (int) checksum.getValue());
            Path corrupt = dir.resolve(kind + ".dex"); Files.write(corrupt, bytes);
            var runtime = new ProjectRuntime(null, List.of(corrupt), List.of(dir));
            try (var server = new HttpApiServer("127.0.0.1", 0, runtime)) {
                server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
                var query = instructions(ENTRY);
                if (kind.equals("limit")) error(server, query, 429, "RESOURCE_LIMIT");
                else {
                    var failed = success(server, query);
                    assertEquals("FAILED", failed.path("instructionScan").path("availability").asText());
                    assertEquals(0, failed.path("edges").size());
                    error(server, query.put("strict", true), 409, "INCOMPLETE_ANALYSIS");
                }
            } finally { runtime.close(); }
        }
    }

    @Test void occurrenceCollectionLimitsApplyBeforeRelationFiltering() throws Exception {
        String source = ".class public Lprobe/Many;\n.super Ljava/lang/Object;\n.method public static entry()V\n.registers 0\n"
                + "invoke-static {}, Lmissing/Api;->run()V\n".repeat(10001) + "return-void\n.end method\n";
        Path dex = HierarchyFixture.dex(dir, "many.dex", source);
        var runtime = new ProjectRuntime(null, List.of(dex), List.of(dir));
        try (var server = new HttpApiServer("127.0.0.1", 0, runtime)) {
            server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
            var query = instructions(ref("Many", "entry", "()V"));
            query.set("relations", JSON.valueToTree(List.of("FIELD_READ")));
            error(server, query, 429, "RESOURCE_LIMIT");
            assertFalse(runtime.projectSnapshot().dirty());
        } finally { runtime.close(); }
    }

    static List<Integer> offsets(JsonNode page) {
        List<Integer> values = new ArrayList<>();
        page.path("edges").forEach(e -> values.add(e.path("instruction").path("offsetCodeUnits").intValue()));
        return values;
    }
}
