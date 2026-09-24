package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

class OpenApiDocumentTest {
	@Test
	void phaseTwoRoutesAndReviewedExamplesRemainInContract() throws Exception {
		var yaml = new ObjectMapper(new YAMLFactory()).readTree(Path.of("openapi/openapi.yaml").toFile());
		assertEquals("3.1.0", yaml.path("openapi").asText());
		for (String path : List.of("/project", "/project/save", "/project/reload",
				"/project/settings", "/project/pending-edits/export")) {
			assertTrue(yaml.path("paths").has(path), "Missing native lifecycle route " + path);
		}
		assertTrue(yaml.path("components").path("schemas").path("RevisionSet")
				.path("required").toString().contains("persistedIdentityState"));
		ObjectMapper json = new ObjectMapper();
		for (String file : List.of("project.json", "save-request.json", "reload-request.json", "settings-update-request.json",
				"external-conflict.json")) {
			assertTrue(json.readTree(Files.readString(Path.of("openapi/examples", file))).isObject());
		}
	}
}
