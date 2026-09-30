package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jadx.api.*;
import jadx.api.data.impl.*;
import dev.libjadx.project.NativeProjectDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalRenameRetargetingProbeTest {
    @TempDir Path dir;

    @Test void nativeKeyModeAndGuiInputSettingsMatrix() throws Exception {
        var all = new TreeMap<String, Object>();
        for (String format : List.of("JVM", "DEX")) {
            Path root = Files.createDirectories(dir.resolve(format));
            Path input = format.equals("JVM") ? LocalIdentityDiagnostics.jvm(root) : LocalIdentityDiagnostics.dex(root);
            String owner = format.equals("JVM") ? "probe.LocalShapes" : "probe.LocalDex";
            LocalIdentityDiagnostics.Snapshot baseline;
            try (var engine = LocalIdentityDiagnostics.open(input, new JadxCodeData(), DecompilationMode.RESTRUCTURE)) {
                baseline = LocalIdentityDiagnostics.snapshot(engine, owner);
            }
            assertFalse(baseline.locals().isEmpty());
            var controls = new TreeMap<String, LocalIdentityDiagnostics.Snapshot>();
            var settingsList = format.equals("JVM") ? List.of("DEFAULT", "NO_FINALLY", "NO_INLINE", "DX")
                    : List.of("DEFAULT", "NO_FINALLY", "NO_INLINE");
            for (String settings : settingsList) for (var mode : DecompilationMode.values()) {
                try (var engine = LocalIdentityDiagnostics.open(input, new JadxCodeData(), mode, settings)) {
                    controls.put(settings + ":" + mode.name(), LocalIdentityDiagnostics.snapshot(engine, owner));
                }
            }
            for (var target : baseline.locals()) {
                assertTrue(target.exact(), target.toString());
                String sentinel = "selectedLocal";
                var rows = new TreeMap<String, Object>();
                var data = LocalIdentityDiagnostics.rename(target, sentinel);
                for (String settings : settingsList) {
                  for (var mode : DecompilationMode.values()) {
                    var consumption = new ArrayList<LocalIdentityDiagnostics.NativeConsumption>();
                    try (var engine = LocalIdentityDiagnostics.open(input, data, mode, settings, consumption)) {
                        var selected = LocalIdentityDiagnostics.snapshot(engine, owner);
                        var renamed = selected.named(sentinel);
                        String classification = renamed.isEmpty() ? (selected.source().contains(sentinel) || !consumption.isEmpty() ? "UNVERIFIABLE" : "ABSENT_INERT")
                                : renamed.size() > 1 ? "AMBIGUOUS" : renamed.getFirst().argument() ? "PARAMETER"
                                : !settings.equals("DX") && target.roles().equals(renamed.getFirst().roles()) ? "SAME_LOCAL" : "UNVERIFIABLE";
                        if (consumption.stream().anyMatch(c -> c.argument())) classification = "PARAMETER";
                        if (classification.equals("ABSENT_INERT")) assertEquals(
                                LocalIdentityDiagnostics.withoutConversionOrigin(controls.get(settings + ":" + mode).source()),
                                LocalIdentityDiagnostics.withoutConversionOrigin(selected.source()));
                        if (settings.equals("DEFAULT") && (mode == DecompilationMode.AUTO || mode == DecompilationMode.RESTRUCTURE)) {
                            assertEquals(1, renamed.size(), target.toString());
                            assertEquals("SAME_LOCAL", classification);
                        }
                        // Two owned semantic oracles, rather than offset comparison across loaders.
                        if (settings.equals("DX") && mode != DecompilationMode.FALLBACK
                                && target.shortId().equals("pressure(I)I") && target.register() == 0 && target.ssa() == 2) {
                            assertEquals(1, consumption.size());
                            assertTrue(consumption.getFirst().instruction().contains(" * "));
                            assertTrue(selected.source().contains("selectedLocal = " + (mode == DecompilationMode.SIMPLE ? "r0 * r1" : "iAbs * iAbs2")), selected.source());
                            classification = "DIFFERENT_LOCAL";
                        }
                        if (settings.equals("DX") && mode != DecompilationMode.FALLBACK
                                && target.shortId().equals("simpleRetarget(I)I") && target.register() == 0 && target.ssa() == 2) {
                            assertEquals(1, target.definitions().size());
                            assertTrue(target.uniqueSsaKey());
                            assertFalse(target.methodRegisterReuse());
                            assertFalse(target.methodPhi());
                            assertEquals(1, consumption.size());
                            assertTrue(consumption.getFirst().instruction().contains(" * "));
                            classification = "DIFFERENT_LOCAL";
                        }
                        if (settings.equals("DX") && mode != DecompilationMode.FALLBACK
                                && target.shortId().equals("wide(JD)J") && target.register() == 0 && target.ssa() == 3) {
                            assertEquals(1, consumption.size());
                            assertEquals(List.of("11:CAST"), consumption.getFirst().definitions().getFirst().uses());
                            assertFalse(selected.source().contains("selectedLocal = " + (mode == DecompilationMode.SIMPLE ? "r8 + 79.5" : "d + 79.5")));
                            classification = "DIFFERENT_LOCAL";
                        }
                        assertTrue(NativeProjectDocument.codeDataEquivalent(data, engine.getArgs().getCodeData()));
                        // A changed transformed def/use map does not prove a different logical local.
                        // Conversion offsets are a different coordinate system, never compared as identity.
                        rows.put(settings + ":" + mode.name(), Map.of("classification", classification, "snapshot", selected,
                                "nativeConsumption", List.copyOf(consumption)));
                    }
                  }
                }
                all.put(format + ":" + target.key(), Map.of("target", target, "modes", rows));
            }
            if (format.equals("JVM")) {
                assertTrue(baseline.locals().stream().anyMatch(d -> d.shortId().equals("reuse(I)I")));
                assertTrue(baseline.locals().stream().anyMatch(d -> d.shortId().equals("wide(JD)J")));
            }
        }
        Path output = Path.of("build/local-rename-probe/mode-matrix.json"); Files.createDirectories(output.getParent());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.toFile(), all);
    }
}
