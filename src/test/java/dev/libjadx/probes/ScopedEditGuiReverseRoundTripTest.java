package dev.libjadx.probes;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import dev.libjadx.project.NativeProjectDocument;
import dev.libjadx.jadxadapter.JadxEngineFactory;
import dev.libjadx.jadxadapter.JadxSourceAdapter;
import dev.libjadx.core.EffectiveAnalysisConfig;
import dev.libjadx.core.symbols.SymbolRef;
import jadx.api.JadxDecompiler;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class ScopedEditGuiReverseRoundTripTest {
	@Test void matchingGuiResaveAndHeadlessReopenRetainExactParametersAndOtherEdits() throws Exception {
		String saved = System.getenv("LIBJADX_SCOPED_GUI_SAVED_PROJECT");
		Assumptions.assumeTrue(saved != null, "Run scopedEditGuiRoundTripTest with matching Jadx 1.5.6 GUI");
		var project = NativeProjectDocument.open(Path.of(saved));
		assertEquals(1, project.getInputFiles().size()); assertEquals("mapping.tiny", project.getMappingsPath().getFileName().toString());
		assertTrue(Files.readString(project.getProjectPath()).contains("variables.jar"));
		// Upstream GUI model drops unknown fields; headless save preservation is verified separately.
		try (var engine = new JadxDecompiler(JadxEngineFactory.arguments(project.getInputFiles(), project.getMappingsPath(), project.getCodeData(), EffectiveAnalysisConfig.defaults()))) {
			engine.load(); var cls = engine.searchJavaClassByOrigFullName("probe.Variables");
			assertEquals("EditedVariables", cls.getName());
			assertEquals("probe.MappedUnrelated", engine.searchJavaClassByOrigFullName("probe.VariableUnrelated").getFullName());
			var source = JadxSourceAdapter.extract(engine, cls, SymbolRef.classRef("Lprobe/Variables;"), true, "gui-probe", 0, 0, EffectiveAnalysisConfig.defaults().fingerprint());
			assertTrue(source.source().contains("scoped GUI comment"));
			for (var variable : source.variables()) {
				if (!variable.kind().equals("PARAMETER") || !variable.persistability().equals("SUPPORTED")) continue;
				assertEquals("parameter" + variable.parameterIndex(), variable.displayName());
				assertEquals(variable.displayName(), source.source().substring(variable.range().startOffsetUtf16(), variable.range().endOffsetUtf16()));
			}
			for (var rename : project.getCodeData().getRenames()) {
				if (rename.getCodeRef() == null) continue;
				var matching = source.variables().stream().filter(v -> v.kind().equals("PARAMETER")
						&& v.parameterIndex() != null && v.parameterIndex() == rename.getCodeRef().getIndex()
						&& (v.method().originalName() + v.method().originalDescriptor()).equals(rename.getNodeRef().getShortId())).toList();
				assertEquals(1, matching.size());
				assertEquals(rename.getNewName(), matching.getFirst().displayName());
			}

			for (String expected : List.of("instance(int parameter0, long parameter1, double parameter2, String parameter3)",
					"statik(long parameter0, int parameter1, double parameter2)", "single(int parameter0)", "single(String parameter0)"))
				assertTrue(source.source().contains(expected), source.source());
			assertTrue(project.getCodeData().getRenames().stream().filter(r -> r.getCodeRef() != null)
					.allMatch(r -> r.getNodeRef().getDeclaringClass().equals("probe.Variables") && r.getCodeRef().getAttachType() == jadx.api.data.CodeRefType.MTH_ARG));
		}
	}
}
