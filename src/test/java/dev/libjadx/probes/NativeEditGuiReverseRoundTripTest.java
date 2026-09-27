package dev.libjadx.probes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import dev.libjadx.project.NativeProjectDocument;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class NativeEditGuiReverseRoundTripTest {
	@Test
	void matchingGuiResaveRetainsServiceEditsAndOriginalNativeKeys() throws Exception {
		String saved = System.getenv("LIBJADX_EDIT_GUI_SAVED_PROJECT");
		Assumptions.assumeTrue(saved != null, "Run nativeEditGuiRoundTripTest with matching 1.5.6 GUI");
		NativeProjectDocument project = NativeProjectDocument.open(Path.of(saved));
		assertEquals(1, project.getInputFiles().size());
		assertEquals(List.of("probe.SymbolFixture", "probe.SymbolFixture", "probe.SymbolFixture"),
				project.getCodeData().getRenames().stream().map(rename -> rename.getNodeRef().getDeclaringClass()).toList());
		assertTrue(project.getCodeData().getRenames().stream().anyMatch(rename -> rename.getNodeRef().getShortId() != null
				&& rename.getNodeRef().getShortId().equals("mix(I)I") && rename.getNewName().equals("guiEditedMix")));
		assertTrue(project.getCodeData().getRenames().stream().anyMatch(rename -> rename.getNodeRef().getShortId() != null
				&& rename.getNodeRef().getShortId().equals("count:I") && rename.getNewName().equals("guiEditedCount")));
		assertEquals(3, project.getCodeData().getComments().size());
		JadxArgs args = new JadxArgs();
		project.getInputFiles().forEach(path -> args.getInputFiles().add(path.toFile()));
		args.setCodeData(project.getCodeData());
		try (JadxDecompiler jadx = new JadxDecompiler(args)) {
			jadx.load();
			String code = jadx.getClassesWithInners().stream()
					.filter(cls -> cls.getRawName().equals("probe.SymbolFixture"))
					.findFirst().orElseThrow().getCode();
			for (String expected : List.of("class GuiEditedFixture", "guiEditedMix", "guiEditedCount",
					"class declaration comment", "method declaration comment", "field declaration comment")) {
				assertTrue(code.contains(expected), expected + " missing from GUI-resaved native project source");
			}
		}
	}
}
