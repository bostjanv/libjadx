package dev.libjadx.probes;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;

import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;
import jadx.api.data.impl.JadxCodeComment;
import jadx.api.data.impl.JadxCodeRename;
import jadx.api.data.impl.JadxNodeRef;
import dev.libjadx.project.NativeProjectDocument;

class NativeGuiFixtureRoundTripProbeTest {

	@Test
	void editsAndSavesProjectProducedByMatchingGuiThenReloadsCodeDataInJadx() throws Exception {
		Path sourceDir = Path.of("tests/fixtures/native-project").toAbsolutePath();
		Path outputDir = Path.of("build/native-roundtrip-fixture").toAbsolutePath();
		resetDirectory(outputDir);
		Files.copy(sourceDir.resolve("sample.jar"), outputDir.resolve("sample.jar"), StandardCopyOption.REPLACE_EXISTING);
		Files.copy(sourceDir.resolve("second.jar"), outputDir.resolve("second.jar"), StandardCopyOption.REPLACE_EXISTING);
		Files.copy(sourceDir.resolve("sample.tiny"), outputDir.resolve("sample.tiny"), StandardCopyOption.REPLACE_EXISTING);
		Files.copy(sourceDir.resolve("sample.jar.jadx"), outputDir.resolve("sample.jar.jadx"), StandardCopyOption.REPLACE_EXISTING);

		Path projectPath = outputDir.resolve("sample.jar.jadx");
		NativeProjectDocument project = NativeProjectDocument.open(projectPath);
		assertTrue(project.getInputFiles().contains(outputDir.resolve("sample.jar")));
		assertTrue(project.getInputFiles().contains(outputDir.resolve("second.jar")));
		assertTrue(project.getMappingsPath().equals(outputDir.resolve("sample.tiny")));
		project.getCodeData().setRenames(List.of(new JadxCodeRename(JadxNodeRef.forCls("probe.Sample"), "RenamedSample")));
		project.getCodeData().setComments(List.of(new JadxCodeComment(JadxNodeRef.forCls("probe.Sample"), "saved by headless probe")));
		project.save();

		JadxArgs args = new JadxArgs();
		project.getInputFiles().forEach(input -> args.getInputFiles().add(input.toFile()));
		args.setUserRenamesMappingsPath(project.getMappingsPath());
		args.setCodeData(project.getCodeData());
		try (JadxDecompiler jadx = new JadxDecompiler(args)) {
			jadx.load();
			assertTrue(jadx.getClasses().stream().anyMatch(cls -> cls.getFullName().equals("probe.RenamedSecond")));
			JavaClass cls = jadx.getClasses().stream()
					.filter(candidate -> candidate.getFullName().equals("probe.RenamedSample"))
					.findFirst()
					.orElseThrow();
			assertTrue(cls.getCode().contains("class RenamedSample"));
			assertTrue(cls.getCode().contains("// saved by headless probe"));
		}
	}

	private static void resetDirectory(Path directory) throws IOException {
		if (Files.exists(directory)) {
			try (var paths = Files.walk(directory)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
					Files.delete(path);
				}
			}
		}
		Files.createDirectories(directory);
	}
}
