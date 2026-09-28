package dev.libjadx.probes;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;

import dev.libjadx.project.NativeProjectDocument;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class MappingExportGuiReverseRoundTripTest {
	@Test
	void matchingGuiActuallyLoadsExportedMappingResavesAndHeadlesslyReopensByOriginalKeys() throws Exception {
		String path = System.getenv("LIBJADX_MAPPING_GUI_SAVED_PROJECT");
		Assumptions.assumeTrue(path != null, "Run mappingExportGuiRoundTripTest with matching 1.5.6 GUI");
		var project = NativeProjectDocument.open(Path.of(path));
		assertEquals(2, project.getInputFiles().size());
		assertEquals("exported.tiny", project.getMappingsPath().getFileName().toString());
		assertTrue(project.getCodeData().getRenames().isEmpty()); assertTrue(project.getCodeData().getComments().isEmpty());
		JadxArgs args = new JadxArgs();
		project.getInputFiles().forEach(input -> args.getInputFiles().add(input.toFile()));
		args.setUserRenamesMappingsPath(project.getMappingsPath()); args.setCodeData(project.getCodeData());
		try (var engine = new JadxDecompiler(args)) {
			engine.load();
			var cls = engine.searchJavaClassByOrigFullName("probe.SymbolFixture");
			assertEquals("probe.ExportedFixture", cls.getFullName());
			assertEquals("exportedMix", cls.getClassNode().searchMethodByShortId("mix(I)I").getMethodInfo().getAlias());
			assertEquals("exportedCount", cls.getClassNode().searchFieldByShortId("count:I").getFieldInfo().getAlias());
			assertEquals("probe.MappedSecond", engine.searchJavaClassByOrigFullName("probe.Second").getFullName());
			String code = cls.getCode();
			for (String comment : List.of("attached class comment", "attached method comment", "attached field comment",
					"native class comment", "native method comment", "native field comment")) assertTrue(code.contains("// " + comment), code);
		}
	}
}
