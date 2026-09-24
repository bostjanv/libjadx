package dev.libjadx.probes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.Gson;

import jadx.gui.settings.JadxProject;
import jadx.gui.settings.data.ProjectData;

class NativeProjectModelProbeTest {

	@TempDir
	Path tempDir;

	@Test
	void nativeProjectDataLoadsWithoutCreatingGuiWindowsAndRelativePathsUseProjectDirectory() throws Exception {
		Path inputs = Files.createDirectories(tempDir.resolve("inputs"));
		Path apk = Files.createFile(inputs.resolve("sample.apk"));
		Path project = tempDir.resolve("sample.jadx");
		Files.writeString(project, """
				{"projectVersion":2,"files":["inputs/sample.apk"],"treeExpansionsV2":["probe"],
				 "codeData":{"comments":[],"renames":[]},"openTabs":[],"mappingsPath":null,
				 "cacheDir":null,"enableLiveReload":false,"searchHistory":[],
				 "searchResourcesFilter":"","searchResourcesSizeLimit":0,"pluginOptions":{},
				 "futureGuiField":{"retainedByNativeFormat":true}}
				""");

		ProjectData data = JadxProject.loadProjectData(project);
		assertEquals(apk.toAbsolutePath(), data.getFiles().get(0).toAbsolutePath());
		assertEquals("probe", data.getTreeExpansionsV2().get(0));
	}

	@Test
	void gsonModelRoundTripDropsUnknownFieldsAndMustNotBeUsedAsThePreservingWriter() throws Exception {
		Path project = tempDir.resolve("unknown.jadx");
		Files.writeString(project, """
				{"projectVersion":2,"files":[],"treeExpansionsV2":[],"codeData":{"comments":[],"renames":[]},
				 "openTabs":[],"mappingsPath":null,"cacheDir":null,"enableLiveReload":false,
				 "searchHistory":[],"searchResourcesFilter":"","searchResourcesSizeLimit":0,
				 "pluginOptions":{},"futureGuiField":{"mustSurvive":true}}
				""");

		ProjectData data = JadxProject.loadProjectData(project);
		Method builder = JadxProject.class.getDeclaredMethod("buildGson", Path.class);
		builder.setAccessible(true);
		Gson gson = (Gson) builder.invoke(null, tempDir);
		String serialized = gson.toJson(data);

		assertTrue(serialized.contains("projectVersion"));
		assertFalse(serialized.contains("futureGuiField"));
	}
}

