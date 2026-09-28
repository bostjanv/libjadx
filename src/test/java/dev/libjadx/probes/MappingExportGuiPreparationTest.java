package dev.libjadx.probes;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import dev.libjadx.app.EditBatchService;
import dev.libjadx.app.MappingExportService;
import dev.libjadx.app.ProjectRuntime;
import dev.libjadx.app.SymbolFixtureSupport;
import dev.libjadx.core.edits.EditDtos;
import dev.libjadx.core.mappings.MappingExportDtos;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.project.NativeProjectDocument;
import org.junit.jupiter.api.Test;

class MappingExportGuiPreparationTest {
	@Test
	void prepareActualGuiMappingOnlyCopyFromUnsavedNativeEditsAndAcceptedAttachedMapping() throws Exception {
		Path root = Path.of("build/mapping-export-gui-fixture").toAbsolutePath();
		if (Files.exists(root)) {
			try (var paths = Files.walk(root)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
			}
		}
		Files.createDirectories(root);
		Path jar = SymbolFixtureSupport.compileFixture(root);
		Path second = Files.copy(Path.of("tests/fixtures/native-project/second.jar"), root.resolve("second.jar"));
		Path originalMapping = root.resolve("attached.tiny");
		Files.writeString(originalMapping, "tiny\t2\t0\toriginal\tmapped\n"
				+ "c\tprobe/Second\tprobe/MappedSecond\n"
				+ "c\tprobe/SymbolFixture\tprobe/AttachedFixture\n\tc\tattached class comment\n"
				+ "\tm\t(I)I\tmix\tattachedMix\n\t\tc\tattached method comment\n"
				+ "\tf\tI\tcount\tattachedCount\n\t\tc\tattached field comment\n");
		Path original = root.resolve("original.jadx");
		var document = NativeProjectDocument.newFromInputs(original, List.of(jar, second)).withMappingsPath(originalMapping);
		document.saveNew();
		try (var runtime = new ProjectRuntime(original, List.of(jar, second), List.of(root))) {
			runtime.initializeAsync(document).get(20, TimeUnit.SECONDS);
			var cls = SymbolRef.classRef("Lprobe/SymbolFixture;");
			var method = new SymbolRef(SymbolRef.Kind.METHOD, "Lprobe/SymbolFixture;", null, "mix", "(I)I");
			var field = new SymbolRef(SymbolRef.Kind.FIELD, "Lprobe/SymbolFixture;", null, "count", "I");
			new EditBatchService(runtime).apply(new EditDtos.Request(null, null, List.of(
					new EditDtos.Operation(EditDtos.Kind.RENAME, cls, "ExportedFixture", null, null),
					new EditDtos.Operation(EditDtos.Kind.RENAME, method, "exportedMix", null, null),
					new EditDtos.Operation(EditDtos.Kind.RENAME, field, "exportedCount", null, null),
					new EditDtos.Operation(EditDtos.Kind.SET_COMMENT, cls, null, "native class comment", "LINE"),
					new EditDtos.Operation(EditDtos.Kind.SET_COMMENT, method, null, "native method comment", "LINE"),
					new EditDtos.Operation(EditDtos.Kind.SET_COMMENT, field, null, "native field comment", "LINE"))));
			var before = runtime.projectSnapshot();
			var receipt = new MappingExportService(runtime).export(new MappingExportDtos.Request(root.resolve("exported.tiny"),
					"TINY_V2", before.revisions().sessionId(), before.revisions().logicalRevision()));
			assertEquals(before, runtime.projectSnapshot()); assertTrue(before.dirty());
			Files.writeString(root.resolve("receipt.json"), new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(receipt));
			runtime.saveProject(null, null);
			assertEquals(originalMapping, NativeProjectDocument.open(original).getMappingsPath());
			// A native copy with no code data ensures the exported file itself supplies every alias/comment.
			var copy = NativeProjectDocument.newFromInputs(root.resolve("mapping-copy.jadx"), List.of(jar, second))
					.withMappingsPath(root.resolve("exported.tiny"));
			copy.saveNew();
		}
	}
}
