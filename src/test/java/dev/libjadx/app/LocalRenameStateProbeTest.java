package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import dev.libjadx.core.edits.EditDtos;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.project.*;
import jadx.api.*;
import jadx.api.data.impl.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Positive native replay evidence is explicitly narrower than public edit admission. */
class LocalRenameStateProbeTest {
    @TempDir Path dir;

    @ParameterizedTest @ValueSource(strings = {"JVM", "DEX"})
    void repeatedUnloadFreshReplacementExplicitSaveAndTemporaryModes(String format) throws Exception {
        Path input = format.equals("JVM") ? LocalIdentityDiagnostics.jvm(dir) : LocalIdentityDiagnostics.dex(dir);
        var inputs = new ArrayList<>(List.of(input));
        if (format.equals("DEX")) inputs.add(dev.libjadx.probes.HierarchyFixture.dex(dir, "unrelated.dex", """
                .class public Lprobe/LocalUnrelated;
                .super Ljava/lang/Object;
                .method public static cold(I)I
                    .registers 2
                    mul-int/lit8 v0, p0, 0x53
                    return v0
                .end method
                """));
        String owner = format.equals("JVM") ? "probe.LocalShapes" : "probe.LocalDex";
        String unrelated = "probe.LocalUnrelated";
        var data = new JadxCodeData();
        LocalIdentityDiagnostics.Snapshot intended;
        try (var engine = LocalIdentityDiagnostics.open(inputs, data, DecompilationMode.AUTO)) {
            var baseline = LocalIdentityDiagnostics.snapshot(engine, owner);
            var records = new ArrayList<jadx.api.data.ICodeRename>();
            for (int i = 0; i < baseline.locals().size(); i++) records.addAll(LocalIdentityDiagnostics.rename(baseline.locals().get(i), "localIntent" + i).getRenames());
            data.setRenames(records);
        }
        try (var engine = LocalIdentityDiagnostics.open(inputs, data, DecompilationMode.AUTO)) {
            intended = LocalIdentityDiagnostics.snapshot(engine, owner);
            assertEquals(data.getRenames().size(), intended.locals().stream().filter(d -> d.name().startsWith("localIntent")).count());
            assertEquals(intended, LocalIdentityDiagnostics.snapshot(engine, owner));
            engine.searchJavaClassByOrigFullName(unrelated).getCodeInfo();
            assertEquals(intended, LocalIdentityDiagnostics.snapshot(engine, owner));
            var cls = engine.searchJavaClassByOrigFullName(owner);
            cls.unload(); cls.getClassNode().add(jadx.core.dex.attributes.AFlag.DONT_UNLOAD_CLASS); engine.reloadCodeData();
            var regenerated = LocalIdentityDiagnostics.snapshot(engine, owner);
            assertEquals(localNames(intended), localNames(regenerated));
            if (format.equals("JVM")) {
                assertTrue(intended.source().contains("object(List<String> list)"));
                assertTrue(regenerated.source().contains("object(List list)"));
            } else assertEquals(intended.source(), regenerated.source());
        }
        Path project = dir.resolve("locals.jadx");
        NativeProjectDocument.newFromInputs(project, inputs).save();
        var disk = FileFingerprint.of(project);
        try (var runtime = new ProjectRuntime(project, inputs, List.of(dir))) {
            runtime.initializeAsync(NativeProjectDocument.open(project)).get(20, TimeUnit.SECONDS);
            // Internal diagnostic injection, NOT a public local edit operation. Real PR #18 staging.
            runtime.withExclusiveEdit(context -> context.commit(data));
            assertTrue(runtime.projectSnapshot().dirty()); assertEquals(disk, FileFingerprint.of(project));
            var result = new EditBatchService(runtime).apply(new EditDtos.Request(null, null, List.of(
                    new EditDtos.Operation(EditDtos.Kind.SET_COMMENT,
                            SymbolRef.classRef("L" + unrelated.replace('.', '/') + ";"), null, "unrelated diagnostic comment", "LINE"))));
            assertEquals("APPLIED", result.outcome());
            var published = runtime.withPrimaryClassRead("local-probe", engine -> {
                engine.getClasses().forEach(c -> c.getClassNode().add(jadx.core.dex.attributes.AFlag.DONT_UNLOAD_CLASS));
                return LocalIdentityDiagnostics.snapshot(engine, owner);
            });
            var complete = runtime.withExclusiveEdit(context -> context.codeDataCopy());
            var unrelatedPublished = runtime.withPrimaryClassRead("local-probe-unrelated",
                    engine -> LocalIdentityDiagnostics.snapshot(engine, unrelated));
            try (var fresh = LocalIdentityDiagnostics.open(inputs, complete, DecompilationMode.AUTO)) {
                assertEquals(published, LocalIdentityDiagnostics.snapshot(fresh, owner));
                assertEquals(unrelatedPublished, LocalIdentityDiagnostics.snapshot(fresh, unrelated));
                assertTrue(NativeProjectDocument.codeDataEquivalent(complete, fresh.getArgs().getCodeData()));
            }
            for (var mode : DecompilationMode.values()) {
                var temporary = runtime.withTemporaryAnalysis(new dev.libjadx.core.EffectiveAnalysisConfig(mode.name()), engine -> {
                    engine.getClasses().forEach(c -> c.getClassNode().add(jadx.core.dex.attributes.AFlag.DONT_UNLOAD_CLASS));
                    assertTrue(NativeProjectDocument.codeDataEquivalent(complete, engine.getArgs().getCodeData()));
                    return LocalIdentityDiagnostics.snapshot(engine, owner);
                });
                try (var fresh = LocalIdentityDiagnostics.open(inputs, complete, mode)) {
                    assertEquals(LocalIdentityDiagnostics.snapshot(fresh, owner), temporary.value());
                }
            }
            assertEquals(disk, FileFingerprint.of(project));
            runtime.saveProject(null, null);
            var saved = NativeProjectDocument.open(project);
            assertEquals(inputs, saved.getInputFiles());
            assertTrue(NativeProjectDocument.codeDataEquivalent(complete, saved.getCodeData()));
            try (var reopened = LocalIdentityDiagnostics.open(saved.getInputFiles(), saved.getCodeData(), DecompilationMode.AUTO)) {
                assertEquals(published, LocalIdentityDiagnostics.snapshot(reopened, owner));
                assertEquals(unrelatedPublished, LocalIdentityDiagnostics.snapshot(reopened, unrelated));
            }
        }
    }

    @Test void nativeEncodingIsUncheckedAndCanAliasOrDecodeNegativeRegisters() {
        assertEquals(JadxCodeRef.forVar(1, 0), JadxCodeRef.forVar(0, 65536));
        assertEquals(JadxCodeRef.forVar(1, 0), JadxCodeRef.forVar(65537, 0));
        assertEquals(-32768, JadxCodeRef.forVar(32768, 0).getIndex() >> 16);
        assertEquals(65535, JadxCodeRef.forVar(0, -1).getIndex() & 0xffff);
    }

    private static Map<String, String> localNames(LocalIdentityDiagnostics.Snapshot source) {
        var names = new TreeMap<String, String>();
        source.declarations().stream().filter(d -> d.name().startsWith("localIntent")).forEach(d -> names.put(d.key(), d.name()));
        return names;
    }
}
