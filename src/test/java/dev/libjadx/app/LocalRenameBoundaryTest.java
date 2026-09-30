package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.net.URI;
import java.net.http.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.libjadx.http.HttpApiServer;
import dev.libjadx.core.edits.EditDtos;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.project.NativeProjectDocument;
import jadx.api.*;
import jadx.api.data.ICodeRename;
import jadx.api.data.impl.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LocalRenameBoundaryTest {
    @TempDir Path dir;
    static final SymbolRef OWNER = SymbolRef.classRef("Lprobe/LocalShapes;");
    static final SymbolRef METHOD = new SymbolRef(SymbolRef.Kind.METHOD, OWNER.originalClassDescriptor(), null, "straight", "(I)I");

    @ParameterizedTest @ValueSource(strings = {"LOCAL_VAR", "PARAMETER_VAR", "MIXED", "DUPLICATE_VAR", "MALFORMED_VAR"})
    void scopedVarStatesRemainUntranslatedAndCannotAuthorizeParameterMutation(String scenario) throws Exception {
        Path input = LocalIdentityDiagnostics.jvm(dir);
        var node = new JadxNodeRef(jadx.api.data.IJavaNodeRef.RefType.METHOD, "probe.LocalShapes", "straight(I)I");
        var records = new ArrayList<ICodeRename>();
        var varKey = JadxCodeRef.forVar(scenario.equals("PARAMETER_VAR") ? 4 : 0, scenario.equals("PARAMETER_VAR") ? 0 : 1);
        if (scenario.equals("MALFORMED_VAR")) varKey = JadxCodeRef.forVar(32768, 0);
        records.add(new JadxCodeRename(node, varKey, "nativeScoped"));
        if (scenario.equals("MIXED")) records.add(new JadxCodeRename(node, JadxCodeRef.forMthArg(0), "positional"));
        if (scenario.equals("DUPLICATE_VAR")) records.add(new JadxCodeRename(node, varKey, "secondScoped"));
        var data = new JadxCodeData(); data.setRenames(records);
        try (var runtime = new ProjectRuntime(null, List.of(input), List.of(dir))) {
            runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
            runtime.withExclusiveEdit(context -> context.commit(data));
            var source = ScopedParameterServiceTest.source(runtime, null, OWNER);
            var before = runtime.projectSnapshot(); var old = runtime.decompiler(); var identity = runtime.searchIdentity();
            assertEquals("INVALID_ENTITY_ID", assertThrows(EditBatchService.Rejected.class,
                    () -> new EditBatchService(runtime).apply(ScopedParameterServiceTest.request(source,
                            ScopedParameterServiceTest.rename(source, METHOD, 0, "attempt")))).code());
            assertEquals(before, runtime.projectSnapshot()); assertSame(old, runtime.decompiler());
            assertEquals(identity, runtime.searchIdentity());
            boolean equivalent = runtime.withExclusiveEdit(context -> NativeProjectDocument.codeDataEquivalent(data, context.codeDataCopy()));
            assertTrue(equivalent);
            assertEquals(source, ScopedParameterServiceTest.source(runtime, null, OWNER));
            assertTrue(source.variables().stream().filter(v -> v.kind().equals("LOCAL")).allMatch(v -> v.persistability().equals("UNSUPPORTED")));
        }
    }

    @Test void compilerBridgeAndBodylessFormsHaveNoEditableLocalMetadata() throws Exception {
        Path input = LocalIdentityDiagnostics.jvm(dir);
        try (var engine = LocalIdentityDiagnostics.open(input, new JadxCodeData(), DecompilationMode.AUTO)) {
            var bridge = engine.searchJavaClassByOrigFullName("probe.LocalBridge"); bridge.getCodeInfo();
            assertTrue(bridge.getClassNode().getMethods().stream().anyMatch(m -> m.getAccessFlags().isBridge() && m.getAccessFlags().isSynthetic()));
            var contract = engine.searchJavaClassByOrigFullName("probe.LocalContract"); contract.getCodeInfo();
            assertTrue(contract.getClassNode().getMethods().stream().anyMatch(m -> m.isNoCode()));
            for (var cls : List.of(bridge, contract)) {
                var source = dev.libjadx.jadxadapter.JadxSourceAdapter.extract(engine, cls,
                        SymbolRef.classRef("L" + cls.getRawName().replace('.', '/') + ";"), true,
                        "local-boundary", 0, 0, "probe");
                assertTrue(source.variables().stream().filter(v -> v.kind().equals("LOCAL")).allMatch(v -> v.persistability().equals("UNSUPPORTED")));
            }
        }
    }

    @Test void capabilityAndPublicLocalRejectionPreserveTheEntireUnstagedBatch() throws Exception {
        Path input = LocalIdentityDiagnostics.jvm(dir);
        try (var runtime = new ProjectRuntime(null, List.of(input), List.of(dir));
                var server = new HttpApiServer("127.0.0.1", 0, runtime)) {
            server.start(); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
            var json = new ObjectMapper(); var client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + server.localPort() + "/api/v1";
            var response = client.send(HttpRequest.newBuilder(URI.create(base + "/capabilities")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            var entries = json.readTree(response.body()).path("capabilities");
            var capability = java.util.stream.StreamSupport.stream(entries.spliterator(), false)
                    .filter(c -> c.path("name").asText().equals("edit.local_rename")).findFirst().orElseThrow();
            assertEquals("UNSUPPORTED", capability.path("status").asText());
            assertEquals("NATIVE_VAR_RETARGETS_WITH_UNBOUND_GUI_SETTINGS", capability.path("evidence").asText());
            assertEquals("UNAVAILABLE", capability.path("persistence").asText());
            var source = ScopedParameterServiceTest.source(runtime, null, OWNER);
            assertFalse(source.variables().stream().filter(v -> v.kind().equals("LOCAL")).toList().isEmpty());
            var before = runtime.projectSnapshot(); var old = runtime.decompiler(); var identity = runtime.searchIdentity();
            String body = json.writeValueAsString(Map.of("expectedSessionId", source.sessionId(), "expectedLogicalRevision", source.logicalRevision(),
                    "items", List.of(Map.of("kind", "RENAME", "target", OWNER, "newName", "MustNotStage"),
                            Map.of("kind", "RENAME_LOCAL", "method", METHOD, "register", 0, "ssaVersion", 1,
                                    "sourceSnapshotId", source.sourceSnapshotId(), "newName", "attempt"))));
            var rejected = client.send(HttpRequest.newBuilder(URI.create(base + "/edits/batch"))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(422, rejected.statusCode(), rejected.body());
            assertEquals("UNSUPPORTED_CAPABILITY", json.readTree(rejected.body()).path("error").path("code").asText());
            assertEquals(before, runtime.projectSnapshot()); assertSame(old, runtime.decompiler()); assertEquals(identity, runtime.searchIdentity());
            assertEquals(source, ScopedParameterServiceTest.source(runtime, null, OWNER));
        }
    }
}
