package dev.libjadx.probes;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import dev.libjadx.project.NativeProjectDocument;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class MappingImportGuiReverseRoundTripTest {
	@Test void matchingGuiResavesNativeImportedEditsAndFreshEngineReopensByOriginalKeys() throws Exception {
		String path = System.getenv("LIBJADX_IMPORT_GUI_SAVED_PROJECT");
		Assumptions.assumeTrue(path != null, "Run mappingImportGuiRoundTripTest with the matching 1.5.6 GUI");
		var project = NativeProjectDocument.open(Path.of(path));
		assertEquals(2, project.getInputFiles().size()); assertEquals("attached.tiny", project.getMappingsPath().getFileName().toString());
		assertEquals(3, project.getCodeData().getRenames().size()); assertEquals(3, project.getCodeData().getComments().size());
		assertEquals(java.util.Arrays.asList(null, "mix(I)I", "count:I"), project.getCodeData().getRenames().stream().map(rename -> rename.getNodeRef().getShortId()).toList());
		JadxArgs args = new JadxArgs(); project.getInputFiles().forEach(input -> args.getInputFiles().add(input.toFile()));
		args.setCodeData(project.getCodeData()); args.setUserRenamesMappingsPath(project.getMappingsPath());
		try (var engine = new JadxDecompiler(args)) {
			engine.load(); var cls = engine.searchJavaClassByOrigFullName("probe.SymbolFixture");
			assertEquals("probe.ImportedFixture", cls.getFullName());
			assertEquals("probe.ImportedFixture$Inner",
					engine.getRoot().resolveRawClass("probe.SymbolFixture$Inner").getClassInfo().makeAliasRawFullName());
			assertEquals("importedMix", cls.getClassNode().searchMethodByShortId("mix(I)I").getMethodInfo().getAlias());
			assertEquals("importedCount", cls.getClassNode().searchFieldByShortId("count:I").getFieldInfo().getAlias());
			assertEquals("probe.MappedSecond", engine.searchJavaClassByOrigFullName("probe.Second").getFullName());
			String source = cls.getCode();
			for (String kind : List.of("class", "method", "field")) {
				assertTrue(source.contains("// attached " + kind), source); assertTrue(source.contains("// native " + kind), source);
				assertEquals(1, source.split("// attached " + kind, -1).length - 1);
			}
		}
	}
}
