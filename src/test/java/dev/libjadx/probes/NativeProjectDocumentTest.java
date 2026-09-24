package dev.libjadx.probes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jadx.api.data.ICodeComment;
import jadx.api.data.ICodeRename;
import jadx.api.data.impl.JadxCodeComment;
import jadx.api.data.impl.JadxCodeRename;
import jadx.api.data.impl.JadxNodeRef;
import jadx.api.data.ICodeData;
import dev.libjadx.project.NativeProjectDocument;

class NativeProjectDocumentTest {

	@TempDir
	Path tempDir;

	@Test
	void preservesUnknownNativeFieldsAndDoesNotWriteUntilExplicitSave() throws IOException {
		Path input = Files.createFile(tempDir.resolve("sample.apk"));
		Path project = tempDir.resolve("sample.jadx");
		String source = """
				{"projectVersion":2,"files":["sample.apk"],"treeExpansionsV2":["probe"],
				 "codeData":{"comments":[],"renames":[],"futureCodeDataField":"keep"},
				 "openTabs":[{"type":"futureTab","payload":19}],"mappingsPath":null,"cacheDir":null,
				 "enableLiveReload":false,"searchHistory":[],"searchResourcesFilter":"",
				 "searchResourcesSizeLimit":0,"pluginOptions":{},"futureGuiField":{"keep":true}}
				""";
		Files.writeString(project, source);

		NativeProjectDocument document = NativeProjectDocument.open(project);
		document.getCodeData().setRenames(List.of(new JadxCodeRename(JadxNodeRef.forCls("probe.Sample"), "Alias")));
		document.getCodeData().setComments(List.of(new JadxCodeComment(JadxNodeRef.forCls("probe.Sample"), "reviewed")));
		assertEquals(source, Files.readString(project), "opening and editing must not autosave");
		assertTrue(Files.exists(input));

		document.setCodeData(document.getCodeData());
		document.save();
		String saved = Files.readString(project);
		assertTrue(saved.contains("\"futureGuiField\""));
		assertTrue(saved.contains("\"futureCodeDataField\""));
		assertTrue(saved.contains("\"futureTab\""));
		assertTrue(saved.contains("\"Alias\""));
		assertTrue(saved.contains("\"reviewed\""));
		assertTrue(saved.contains("\"sample.apk\""));

		NativeProjectDocument reopened = NativeProjectDocument.open(project);
		assertEquals("Alias", ((ICodeRename) reopened.getCodeData().getRenames().get(0)).getNewName());
		assertEquals("reviewed", ((ICodeComment) reopened.getCodeData().getComments().get(0)).getComment());
		assertFalse(reopened.getCodeData().isEmpty());
	}

	@Test
	void preservesUnknownMembersInsideEditedNativeEntries() throws IOException {
		Path project = tempDir.resolve("nested.jadx");
		Files.writeString(project, """
				{"projectVersion":2,"files":[],"codeData":{
				 "renames":[{"nodeRef":{"refType":"CLASS","declClass":"probe.Sample"},
				             "newName":"Old","futureRename":{"keep":1}}],
				 "comments":[{"nodeRef":{"refType":"CLASS","declClass":"probe.Sample"},
				              "comment":"old","style":"LINE","futureComment":true}]}}
				""");
		NativeProjectDocument document = NativeProjectDocument.open(project);
		document.getCodeData().setRenames(List.of(new JadxCodeRename(JadxNodeRef.forCls("probe.Sample"), "New")));
		document.getCodeData().setComments(List.of(new JadxCodeComment(JadxNodeRef.forCls("probe.Sample"), "new comment")));
		document.save();
		String saved = Files.readString(project);
		assertTrue(saved.contains("futureRename"));
		assertTrue(saved.contains("futureComment"));
		assertTrue(saved.contains("New"));
		assertTrue(saved.contains("new comment"));
	}
}
