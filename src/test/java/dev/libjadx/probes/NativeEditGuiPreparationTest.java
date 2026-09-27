package dev.libjadx.probes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import dev.libjadx.app.EditBatchService;
import dev.libjadx.app.ProjectRuntime;
import dev.libjadx.app.SymbolFixtureSupport;
import dev.libjadx.core.edits.EditDtos;
import dev.libjadx.core.symbols.SymbolRef;
import org.junit.jupiter.api.Test;

class NativeEditGuiPreparationTest {
	@Test
	void serviceEditsAndExplicitlySavesMatchingGuiFixture() throws Exception {
		Path directory = Path.of("build/edit-gui-fixture").toAbsolutePath();
		if (Files.exists(directory)) {
			try (var paths = Files.walk(directory)) {
				for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
			}
		}
		Files.createDirectories(directory);
		Path jar = SymbolFixtureSupport.compileFixture(directory);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), List.of(directory));
		try {
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			SymbolRef cls = SymbolRef.classRef("Lprobe/SymbolFixture;");
			SymbolRef method = new SymbolRef(SymbolRef.Kind.METHOD, "Lprobe/SymbolFixture;", null, "mix", "(I)I");
			SymbolRef field = new SymbolRef(SymbolRef.Kind.FIELD, "Lprobe/SymbolFixture;", null, "count", "I");
			var result = new EditBatchService(runtime).apply(new EditDtos.Request(null, null, List.of(
					new EditDtos.Operation(EditDtos.Kind.RENAME, cls, "GuiEditedFixture", null, null),
					new EditDtos.Operation(EditDtos.Kind.RENAME, method, "guiEditedMix", null, null),
					new EditDtos.Operation(EditDtos.Kind.RENAME, field, "guiEditedCount", null, null),
					new EditDtos.Operation(EditDtos.Kind.SET_COMMENT, cls, null, "class declaration comment", "LINE"),
					new EditDtos.Operation(EditDtos.Kind.SET_COMMENT, method, null, "method declaration comment", "LINE"),
					new EditDtos.Operation(EditDtos.Kind.SET_COMMENT, field, null, "field declaration comment", "LINE"))));
			assertEquals("APPLIED", result.outcome());
			runtime.saveProject(directory.resolve("edited.jadx"), null);
		} finally { runtime.close(); }
	}
}
