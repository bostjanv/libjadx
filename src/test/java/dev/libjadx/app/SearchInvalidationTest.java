package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.TimeUnit;

import dev.libjadx.core.search.SearchDtos.Page;
import dev.libjadx.core.search.SearchQuery;
import dev.libjadx.core.symbols.SymbolCatalog;
import dev.libjadx.project.NativeProjectDocument;
import jadx.api.data.impl.JadxCodeComment;
import jadx.api.data.impl.JadxCodeRename;
import jadx.api.data.impl.JadxNodeRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SearchInvalidationTest {
	@TempDir Path dir;

	@Test
	void unsavedNativeEditSaveAndReloadInvalidateSearchCursorsWithoutSearchWrites() throws Exception {
		Path fixture = Path.of("tests/fixtures/native-project").toAbsolutePath();
		for (String file : List.of("sample.jar.jadx", "sample.jar", "second.jar", "sample.tiny"))
			Files.copy(fixture.resolve(file), dir.resolve(file), StandardCopyOption.REPLACE_EXISTING);
		Path project = dir.resolve("sample.jar.jadx");
		byte[] originalProject = Files.readAllBytes(project);
		byte[] originalInput = Files.readAllBytes(dir.resolve("sample.jar"));
		byte[] originalMapping = Files.readAllBytes(dir.resolve("sample.tiny"));
		NativeProjectDocument document = NativeProjectDocument.open(project);
		ProjectRuntime runtime = new ProjectRuntime(project, document.getInputFiles(), List.of(dir));
		SearchService service = new SearchService(runtime, new SymbolCatalogProvider(SymbolCatalog.newCursorKey()),
				SymbolCatalog.newCursorKey());
		try {
			runtime.initializeAsync(document).get(20, TimeUnit.SECONDS);
			SearchQuery originalQuery = query(null);
			Page before = (Page) service.query(originalQuery);
			assertFalse(before.nextCursor() == null, before.toString());
			assertArrayEquals(originalProject, Files.readAllBytes(project));
			var edited = document.getCodeData();
			edited.setRenames(List.of(new JadxCodeRename(JadxNodeRef.forCls("probe.Sample"), "UnsavedSearchAlias")));
			edited.setComments(List.of(new JadxCodeComment(JadxNodeRef.forCls("probe.Sample"), "unsaved search comment")));
			runtime.replaceCodeData(edited, runtime.projectSnapshot().revisions().logicalRevision());
			assertThrows(SearchService.StaleSearchException.class, () -> service.query(query(before.nextCursor())));
			Page alias = (Page) service.query(new SearchQuery("UnsavedSearchAlias",
					List.of(SearchQuery.Domain.CLASS_NAME), SearchQuery.MatchMode.CONTAINS,
					true, 1, null, false, false, null, null));
			assertEquals("Lprobe/Sample;", alias.hits().get(0).ref().originalClassDescriptor());
			assertArrayEquals(originalProject, Files.readAllBytes(project));
			Page beforeSave = (Page) service.query(originalQuery);
			runtime.saveProject(null, runtime.projectSnapshot().revisions().sessionId(),
					runtime.projectSnapshot().revisions().logicalRevision());
			assertThrows(SearchService.StaleSearchException.class, () -> service.query(query(beforeSave.nextCursor())));
			assertNotEquals(new String(originalProject), Files.readString(project));
			Page beforeReload = (Page) service.query(originalQuery);
			runtime.reloadProject(true, runtime.projectSnapshot().revisions().sessionId(),
					runtime.projectSnapshot().revisions().logicalRevision());
			assertThrows(SearchService.StaleSearchException.class, () -> service.query(query(beforeReload.nextCursor())));
			Page beforeMapping = (Page) service.query(originalQuery);
			runtime.updateMappingsPath(null, runtime.projectSnapshot().revisions().sessionId(),
					runtime.projectSnapshot().revisions().logicalRevision());
			assertThrows(SearchService.StaleSearchException.class, () -> service.query(query(beforeMapping.nextCursor())));
			assertArrayEquals(originalInput, Files.readAllBytes(dir.resolve("sample.jar")));
			assertArrayEquals(originalMapping, Files.readAllBytes(dir.resolve("sample.tiny")));
			assertTrue(runtime.projectSnapshot().dirty());
		} finally { runtime.close(); }
	}

	private static SearchQuery query(String cursor) {
		return new SearchQuery("probe", List.of(SearchQuery.Domain.CLASS_NAME),
				SearchQuery.MatchMode.CONTAINS, true, 1, cursor, false, false, null, null);
	}
}
