package dev.libjadx.probes;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import dev.libjadx.project.NativeProjectDocument;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.data.ICodeComment;
import jadx.api.data.ICodeRename;

class NativeGuiReverseRoundTripProbeTest {

	@Test
	void reopensAProjectResavedByTheMatchingGui() throws Exception {
		String configuredPath = System.getenv("LIBJADX_GUI_SAVED_PROJECT");
		Assumptions.assumeTrue(configuredPath != null && !configuredPath.isBlank(),
				"Set LIBJADX_GUI_SAVED_PROJECT to exercise the GUI-to-headless reverse round-trip");
		Path projectPath = Path.of(configuredPath).toAbsolutePath();
		NativeProjectDocument project = NativeProjectDocument.open(projectPath);
		assertTrue(project.getCodeData().getRenames().stream().map(ICodeRename::getNewName).anyMatch("RenamedSample"::equals));
		assertTrue(project.getCodeData().getComments().stream().map(ICodeComment::getComment).anyMatch("saved by headless probe"::equals));

		Path input = projectPath.getParent().resolve(project.toJsonTree().getAsJsonArray("files").get(0).getAsString()).normalize();
		JadxArgs args = new JadxArgs();
		args.getInputFiles().add(input.toFile());
		args.setCodeData(project.getCodeData());
		try (JadxDecompiler jadx = new JadxDecompiler(args)) {
			jadx.load();
			assertTrue(jadx.getClasses().stream().anyMatch(cls -> cls.getCode().contains("// saved by headless probe")));
		}
	}
}

