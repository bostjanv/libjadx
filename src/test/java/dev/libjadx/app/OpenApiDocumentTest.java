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
		for (String path : List.of("/jobs/{jobId}", "/jobs/{jobId}/cancel", "/jobs/{jobId}/events")) {
			assertTrue(yaml.path("paths").has(path), "Missing job route " + path);
		}
		assertTrue(yaml.path("components").path("schemas").path("Job").path("properties")
				.path("progress").path("$ref").asText().endsWith("/JobProgress"));
		assertTrue(yaml.path("components").path("schemas").path("JobProgress").path("properties")
				.path("total").path("type").toString().contains("null"));
		assertTrue(yaml.path("components").path("responses").has("AcceptedJob"));
		assertTrue(yaml.path("components").path("responses").has("JobResourceLimit"));
		assertTrue(yaml.path("paths").path("/jobs/{jobId}/cancel").path("post")
				.path("responses").has("403"));
		assertTrue(yaml.path("paths").path("/jobs/{jobId}/events").path("get")
				.path("responses").has("409"));
		assertTrue(yaml.path("paths").path("/jobs/{jobId}/events").path("get")
				.path("responses").has("429"));
		assertTrue(yaml.path("components").path("schemas").path("JobEvent").path("properties")
				.path("type").path("enum").toString().contains("job.cancel_requested"));
		assertTrue(yaml.path("components").path("schemas").path("RevisionSet")
				.path("required").toString().contains("persistedIdentityState"));
		ObjectMapper json = new ObjectMapper();
		for (String file : List.of("project.json", "save-request.json", "reload-request.json", "settings-update-request.json",
				"external-conflict.json", "project-busy.json", "job-queued.json",
				"job-running-unknown-total.json", "job-cancelling.json", "job-succeeded.json",
				"job-failed.json", "job-resource-limit.json")) {
			assertTrue(json.readTree(Files.readString(Path.of("openapi/examples", file))).isObject());
		}
		assertTrue(Files.readString(Path.of("openapi/examples/job-events.sse")).contains("event: job.completed"));
		assertEquals("RUNNING", json.readTree(Files.readString(Path.of(
				"openapi/examples/job-running-unknown-total.json"))).path("state").asText());
		assertTrue(json.readTree(Files.readString(Path.of(
				"openapi/examples/job-running-unknown-total.json"))).path("progress").path("total").isNull());
		assertEquals("RESOURCE_LIMIT", json.readTree(Files.readString(Path.of(
				"openapi/examples/job-resource-limit.json"))).path("error").path("code").asText());
		assertTrue(yaml.path("paths").path("/project/save").path("post").path("description")
				.asText().contains("PROJECT_BUSY"));
	}
}
