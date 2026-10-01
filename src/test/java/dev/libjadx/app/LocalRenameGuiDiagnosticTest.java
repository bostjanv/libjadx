package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.libjadx.project.NativeProjectDocument;
import jadx.api.*;
import jadx.api.data.ICodeRename;
import jadx.api.data.impl.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Actual-GUI negative diagnostic, never a positive local edit gate. */
class LocalRenameGuiDiagnosticTest {
    static final List<String> CASES = List.of("AUTO", "RESTRUCTURE", "SIMPLE", "FALLBACK", "AUTO_DX");

    @Test void prepareNativeLocalCounterexamplesAndControlledGlobalGuiConfigs() throws Exception {
        Path root = Files.createDirectories(Path.of("build/local-rename-gui-diagnostic").toAbsolutePath());
        Path input = LocalIdentityDiagnostics.jvm(root);
        var records = new ArrayList<ICodeRename>();
        try (var engine = LocalIdentityDiagnostics.open(input, new JadxCodeData(), DecompilationMode.AUTO)) {
            var source = LocalIdentityDiagnostics.snapshot(engine, "probe.LocalShapes");
            for (var target : source.locals()) {
                String name = switch (target.key()) {
                    case "pressure(I)I:0:2" -> "pressureLocal";
                    case "wide(JD)J:0:3" -> "wideLocal";
                    case "straight(I)I:0:1" -> "straightLocal";
                    case "branch(I)I:5:1" -> "branchLocal";
                    case "simpleRetarget(I)I:0:2" -> "simpleLocal";
                    default -> null;
                };
                if (name != null) records.addAll(LocalIdentityDiagnostics.rename(target, name).getRenames());
            }
            var parameter = source.declarations().stream().filter(d -> d.shortId().startsWith("object(") && d.argument()).findFirst().orElseThrow();
            records.addAll(LocalIdentityDiagnostics.rename(parameter, "guiVarParameter").getRenames());
        }
        assertEquals(6, records.size());
        records.add(new JadxCodeRename(new JadxNodeRef(jadx.api.data.IJavaNodeRef.RefType.METHOD,
                "probe.LocalShapes", "straight(I)I"), JadxCodeRef.forMthArg(0), "positionalParameter"));
        var data = new JadxCodeData(); data.setRenames(records);
        for (String scenario : CASES) {
            Path folder = Files.createDirectories(root.resolve(scenario));
            var doc = NativeProjectDocument.newFromInputs(folder.resolve("diagnostic.jadx"), List.of(input));
            doc.setCodeData(data); doc.save();
            boolean dx = scenario.equals("AUTO_DX");
            var mode = dx ? DecompilationMode.AUTO : DecompilationMode.valueOf(scenario);
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(folder.resolve("gui-config.json").toFile(),
                    Map.of("decompilationMode", mode.name(), "useDx", dx, "codeCacheMode", "MEMORY", "threadsCount", 2,
                            "checkForUpdates", false));
            try (var engine = LocalIdentityDiagnostics.open(input, data, mode, dx ? "DX" : "DEFAULT")) {
                var source = LocalIdentityDiagnostics.snapshot(engine, "probe.LocalShapes");
                assertSemanticCounterexample(source.source(), mode, dx);
                Files.writeString(folder.resolve("expected-source.java"), source.source());
            }
        }
    }

    @ParameterizedTest @ValueSource(strings = {"AUTO", "RESTRUCTURE", "SIMPLE", "FALLBACK", "AUTO_DX"})
    void matchingGuiEditorAndSaveAsPreserveRecordsButDoNotBindTheirMeaning(String scenario) throws Exception {
        String location = System.getenv("LIBJADX_LOCAL_GUI_ROOT");
        Assumptions.assumeTrue(location != null, "Run localRenameGuiDiagnosticTest with matching Jadx 1.5.6 GUI");
        Path folder = Path.of(location).resolve(scenario);
        var original = NativeProjectDocument.open(folder.resolve("diagnostic.jadx"));
        var saved = NativeProjectDocument.open(folder.resolve("gui-resaved.jadx"));
        assertTrue(NativeProjectDocument.codeDataEquivalent(original.getCodeData(), saved.getCodeData()));
        assertEquals(original.getInputFiles(), saved.getInputFiles());
        assertFalse(saved.toJsonTree().has("decompilationMode"));
        assertFalse(saved.toJsonTree().has("useDx"));
        boolean dx = scenario.equals("AUTO_DX");
        var mode = dx ? DecompilationMode.AUTO : DecompilationMode.valueOf(scenario);
        String actualGuiSource = Files.readString(folder.resolve("gui-source.java"));
        assertSemanticCounterexample(actualGuiSource, mode, dx);
        assertTrue(Files.size(folder.resolve("gui-source.png")) > 0);
        try (var reopened = LocalIdentityDiagnostics.open(saved.getInputFiles().getFirst(), saved.getCodeData(), mode, dx ? "DX" : "DEFAULT")) {
            String source = LocalIdentityDiagnostics.snapshot(reopened, "probe.LocalShapes").source();
            assertEquals(LocalIdentityDiagnostics.withoutConversionOrigin(Files.readString(folder.resolve("expected-source.java"))),
                    LocalIdentityDiagnostics.withoutConversionOrigin(source));
            assertSemanticCounterexample(source, mode, dx);
        }
        // Ignored VAR records are retained and switching back restores the default intent.
        try (var restored = LocalIdentityDiagnostics.open(saved.getInputFiles().getFirst(), saved.getCodeData(), DecompilationMode.AUTO)) {
            assertSemanticCounterexample(LocalIdentityDiagnostics.snapshot(restored, "probe.LocalShapes").source(), DecompilationMode.AUTO, false);
        }
    }

    static void assertSemanticCounterexample(String source, DecompilationMode mode, boolean dx) {
        if (mode == DecompilationMode.FALLBACK) {
            for (String name : List.of("pressureLocal", "wideLocal", "straightLocal", "branchLocal", "simpleLocal", "guiVarParameter", "positionalParameter"))
                assertFalse(source.contains(name), source);
        } else if (dx) {
            assertTrue(source.contains("pressureLocal = iAbs * iAbs2"), source);
            assertTrue(source.contains("wideLocal = j2 + d2"), source);
            assertTrue(source.contains("simpleLocal = iAbs * iAbs"), source);
            assertFalse(source.contains("straightLocal"), source);
            assertFalse(source.contains("branchLocal"), source);
        } else {
            // Parameter-independent expression roles identify the intended owned computations.
            assertTrue(source.contains("pressureLocal = Math.abs("), source);
            assertTrue(source.contains(" + 43)"), source);
            assertTrue(source.contains("wideLocal = ") && source.contains(" + 79.5"), source);
            assertTrue(source.contains("straightLocal = positionalParameter * 7"), source);
            assertTrue(source.contains("branchLocal"), source);
            assertTrue(source.contains("guiVarParameter"), source);
            assertTrue(source.contains("simpleLocal = Math.abs("), source);
        }
    }
}
