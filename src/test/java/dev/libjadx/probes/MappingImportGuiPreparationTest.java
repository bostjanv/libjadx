package dev.libjadx.probes;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import dev.libjadx.app.MappingImportService;
import dev.libjadx.app.ProjectRuntime;
import dev.libjadx.app.SymbolFixtureSupport;
import dev.libjadx.core.mappings.MappingImportDtos;
import dev.libjadx.project.NativeProjectDocument;
import dev.libjadx.project.FileFingerprint;
import org.junit.jupiter.api.Test;

class MappingImportGuiPreparationTest {
	@Test void importsAttachedCompositeAndNativeDeclarationAliasesThenExplicitlySavesGuiFixture() throws Exception {
		Path root = Path.of("build/mapping-import-gui-fixture").toAbsolutePath();
		if (Files.exists(root)) try (var files = Files.walk(root)) {
			for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
		}
		Files.createDirectories(root);
		Path jar = SymbolFixtureSupport.compileFixture(root);
		Path second = Files.copy(Path.of("tests/fixtures/native-project/second.jar"), root.resolve("second.jar"));
		Path mapping = root.resolve("attached.tiny");
		String attached = "tiny\t2\t0\toriginal\tmapped\n"
				+ "c\tprobe/Second\tprobe/MappedSecond\n"
				+ "c\tprobe/SymbolFixture\tprobe/SymbolFixture\n\tc\tattached class\n"
				+ "\tm\t(I)I\tmix\tmix\n\t\tc\tattached method\n"
				+ "\tf\tI\tcount\tcount\n\t\tc\tattached field\n";
		Files.writeString(mapping, attached);
		Path incoming = root.resolve("incoming.tiny");
		String imported = attached.replace("probe/SymbolFixture\n", "probe/ImportedFixture\n")
				.replace("\tmix\n", "\timportedMix\n").replace("\tcount\n", "\timportedCount\n");
		for (String kind : List.of("class", "method", "field")) imported = imported.replace("attached " + kind + "\n", "attached " + kind + "\\nnative " + kind + "\n");
		Files.writeString(incoming, imported);
		Path path = root.resolve("imported.jadx");
		var document = NativeProjectDocument.newFromInputs(path, List.of(jar, second)).withMappingsPath(mapping); document.saveNew();
		var before = FileFingerprint.of(path); var originalMapping = FileFingerprint.of(mapping); var originalInput = FileFingerprint.of(incoming);
		try (var runtime = new ProjectRuntime(path, document.getInputFiles(), List.of(root))) {
			runtime.initializeAsync(document).get(20, TimeUnit.SECONDS);
			var rev = runtime.projectSnapshot().revisions();
			var receipt = new MappingImportService(runtime).importMappings(new MappingImportDtos.Request(incoming, "TINY_V2", "MERGE_FAIL_ON_CONFLICT", rev.sessionId(), rev.logicalRevision()));
			assertEquals("APPLIED", receipt.outcome()); assertEquals(new MappingImportDtos.EditCounts(3, 3), receipt.applied());
			assertEquals(before, FileFingerprint.of(path)); assertEquals(originalMapping, FileFingerprint.of(mapping)); assertEquals(originalInput, FileFingerprint.of(incoming));
			assertEquals(mapping, runtime.decompiler().getArgs().getUserRenamesMappingsPath());
			Files.writeString(root.resolve("receipt.json"), new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(receipt));
			runtime.saveProject(null, null); assertFalse(runtime.projectSnapshot().dirty());
		}
	}
}
