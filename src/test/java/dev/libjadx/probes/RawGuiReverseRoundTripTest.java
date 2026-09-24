package dev.libjadx.probes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import dev.libjadx.project.NativeProjectDocument;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

class RawGuiReverseRoundTripTest {
	@Test
	void matchingGuiOpensAndResavesRawInputNativeProject() throws Exception {
		String saved = System.getenv("LIBJADX_RAW_GUI_SAVED_PROJECT");
		Assumptions.assumeTrue(saved != null, "Run rawGuiRoundTripTest with the matching GUI");
		Path path = Path.of(saved);
		NativeProjectDocument project = NativeProjectDocument.open(path);
		assertEquals(1, project.getInputFiles().size());
		JadxArgs args = new JadxArgs();
		project.getInputFiles().forEach(input -> args.getInputFiles().add(input.toFile()));
		try (JadxDecompiler jadx = new JadxDecompiler(args)) {
			jadx.load();
			assertTrue(jadx.getClasses().stream().anyMatch(cls -> cls.getCode().contains("class Sample")));
		}
	}
}
