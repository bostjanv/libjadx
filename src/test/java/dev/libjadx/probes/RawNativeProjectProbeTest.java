package dev.libjadx.probes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import dev.libjadx.project.NativeProjectDocument;
import dev.libjadx.project.NativeProjectRepository;
import org.junit.jupiter.api.Test;

class RawNativeProjectProbeTest {
	@Test
	void savesRawInputOnlyOnExplicitSaveAsForMatchingGui() throws Exception {
		Path dir = Path.of("build/raw-roundtrip-fixture").toAbsolutePath();
		Files.createDirectories(dir);
		Path input = dir.resolve("sample.jar");
		Files.copy(Path.of("tests/fixtures/native-project/sample.jar"), input, StandardCopyOption.REPLACE_EXISTING);
		Path target = dir.resolve("raw.jadx");
		Files.deleteIfExists(target);
		NativeProjectRepository repository = NativeProjectRepository.fromInputs(List.of(input), List.of(dir));
		assertTrue(!Files.exists(target));
		repository.save(target, 0L);
		assertTrue(Files.exists(target));
		assertEquals(List.of(input), NativeProjectDocument.open(target).getInputFiles());
	}
}
