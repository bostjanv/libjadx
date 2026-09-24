package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServiceConfigTest {
	@TempDir
	Path tempDir;

	@Test
	void cliOverridesEnvironmentAndYamlAndSelectionDoesNotMixSources() throws Exception {
		Path yamlInput = file("yaml.jar");
		Path envInput = file("env.jar");
		Path cliInput = file("cli.jar");
		Path yaml = yaml("input: [" + yamlInput + "]\nport: 10001\nbind: 127.0.0.1\n");

		ServiceConfig config = ServiceConfig.parse(new String[] {
				"--config", yaml.toString(), "--input", cliInput.toString(), "--port", "12345"
		}, Map.of("LIBJADX_INPUT", envInput.toString(), "LIBJADX_PORT", "23456"));

		assertEquals(12345, config.port());
		assertEquals(List.of(cliInput.toRealPath()), config.inputPaths());
	}

	@Test
	void environmentOverridesYamlAndSelectionIsTakenAsAWhole() throws Exception {
		Path yamlProject = project("yaml-project", file("yaml-project/input.jar"), null);
		Path envInput = file("env-input.jar");
		Path yaml = yaml("project: " + yamlProject + "\nport: 10001\nbind: 127.0.0.1\n");

		ServiceConfig config = ServiceConfig.parse(new String[] {"--config", yaml.toString()},
				Map.of("LIBJADX_INPUT", envInput.toString(), "LIBJADX_PORT", "23456"));

		assertEquals(23456, config.port());
		assertEquals(List.of(envInput.toRealPath()), config.inputPaths());
		assertNull(config.projectPath());
	}

	@Test
	void defaultsAreAppliedWhenOnlyAProjectSelectionIsProvided() throws Exception {
		Path input = file("default-input.jar");
		ServiceConfig config = ServiceConfig.parse(new String[] {"--input", input.toString()}, Map.of());

		assertEquals("127.0.0.1", config.bindAddress());
		assertEquals(18777, config.port());
		assertEquals(List.of(input.toRealPath()), config.inputPaths());
		assertEquals(List.of(tempDir.toRealPath()), config.allowedRoots());
	}

	@Test
	void nativeProjectRelativeInputsAndMappingsAreResolvedAndChecked() throws Exception {
		Path root = Files.createDirectories(tempDir.resolve("native"));
		Path input = file(root, "input.jar");
		Path mappings = file(root, "map.tiny");
		Path project = project(root, input, mappings);

		ServiceConfig config = ServiceConfig.parse(new String[] {"--project", project.toString()}, Map.of());

		assertEquals(project.toRealPath(), config.projectPath());
		assertEquals(List.of(input.toRealPath()), config.inputPaths());
		assertEquals(List.of(root.toRealPath()), config.allowedRoots());

		Path outside = file("outside/map.tiny");
		Path escapingProject = project(root, input, outside);
		assertThrows(IllegalArgumentException.class, () -> ServiceConfig.parse(new String[] {
				"--project", escapingProject.toString(), "--allowed-root", root.toString()
		}, Map.of()));
	}

	@Test
	void rejectsTraversalAndSymlinksThatResolveOutsideAllowedRoot() throws Exception {
		Path root = Files.createDirectories(tempDir.resolve("allowed"));
		Path input = file(root, "inside.jar");
		Path outside = file("outside.jar");
		assertThrows(IllegalArgumentException.class, () -> ServiceConfig.parse(new String[] {
				"--input", outside.toString(), "--allowed-root", root.toString()
		}, Map.of()));
		Path outsideProject = project("outside-project", file("outside-project/input.jar"), null);
		assertThrows(IllegalArgumentException.class, () -> ServiceConfig.parse(new String[] {
				"--project", outsideProject.toString(), "--allowed-root", root.toString()
		}, Map.of()));

		Path traversalProject = Files.writeString(root.resolve("traversal.jadx"), "{\"files\":[\"../outside.jar\"]}");
		assertThrows(IllegalArgumentException.class, () -> ServiceConfig.parse(new String[] {
				"--project", traversalProject.toString(), "--allowed-root", root.toString()
		}, Map.of()));

		Path link = root.resolve("linked.jar");
		try {
			Files.createSymbolicLink(link, outside);
		} catch (UnsupportedOperationException | IOException | SecurityException e) {
			return; // Symlink creation is unavailable on this platform/account.
		}
		Path symlinkProject = Files.writeString(root.resolve("symlink.jadx"), "{\"files\":[\"linked.jar\"]}");
		assertThrows(IllegalArgumentException.class, () -> ServiceConfig.parse(new String[] {
				"--project", symlinkProject.toString(), "--allowed-root", root.toString()
		}, Map.of()));
		assertTrue(Files.exists(input));
	}

	@Test
	void rejectsMissingOrConflictingSelectionInvalidBindPortAndMissingOptionValue() throws Exception {
		Path input = file("input.jar");
		Path project = project("project", file("project/input.jar"), null);
		assertThrows(IllegalArgumentException.class, () -> ServiceConfig.parse(new String[0], Map.of()));
		assertThrows(IllegalArgumentException.class, () -> ServiceConfig.parse(new String[] {
				"--project", project.toString(), "--input", input.toString()
		}, Map.of()));
		assertThrows(IllegalArgumentException.class, () -> ServiceConfig.parse(new String[] {
				"--input", input.toString(), "--bind", "0.0.0.0"
		}, Map.of()));
		assertThrows(IllegalArgumentException.class, () -> ServiceConfig.parse(new String[] {
				"--input", input.toString(), "--port", "65536"
		}, Map.of()));
		assertThrows(IllegalArgumentException.class, () -> ServiceConfig.parse(new String[] {"--input"}, Map.of()));
	}

	@Test
	void rejectsNonMappingYamlAndMissingPathsWithUsefulMessages() throws Exception {
		Path scalarYaml = yaml("not-a-mapping\n");
		IllegalArgumentException invalidRoot = assertThrows(IllegalArgumentException.class,
				() -> ServiceConfig.parse(new String[] {"--config", scalarYaml.toString()}, Map.of()));
		assertTrue(invalidRoot.getMessage().contains("mapping"));

		assertThrows(IllegalArgumentException.class, () -> ServiceConfig.parse(new String[] {
				"--input", tempDir.resolve("missing.jar").toString()
		}, Map.of()));
	}

	private Path yaml(String contents) throws IOException {
		return Files.writeString(tempDir.resolve("config.yaml"), contents);
	}

	private Path file(String name) throws IOException {
		return file(tempDir, name);
	}

	private static Path file(Path directory, String name) throws IOException {
		Path path = directory.resolve(name);
		Files.createDirectories(path.getParent());
		return Files.writeString(path, "fixture");
	}

	private Path project(String name, Path input, Path mappings) throws IOException {
		Path directory = Files.createDirectories(tempDir.resolve(name));
		return project(directory, input, mappings);
	}

	private static Path project(Path directory, Path input, Path mappings) throws IOException {
		String inputReference = directory.relativize(input).toString().replace('\\', '/');
		String mappingField = mappings == null ? "" : ",\"mappingsPath\":\"" + directory.relativize(mappings).toString().replace('\\', '/') + "\"";
		return Files.writeString(directory.resolve("test.jadx"), "{\"files\":[\"" + inputReference + "\"]" + mappingField + "}");
	}
}
