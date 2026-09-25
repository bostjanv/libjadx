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
		assertEquals("#/components/schemas/ClassPage", yaml.path("paths").path("/classes").path("get")
				.path("responses").path("200").path("content").path("application/json")
				.path("schema").path("$ref").asText());
		assertEquals("#/components/schemas/SymbolResolution", yaml.path("paths").path("/symbols/resolve")
				.path("post").path("responses").path("200").path("content").path("application/json")
				.path("schema").path("$ref").asText());
		assertEquals(100, yaml.path("paths").path("/classes").path("get").path("parameters").get(0)
				.path("schema").path("maximum").asInt());
		for (String schema : List.of("SymbolRef", "SymbolInfo", "ClassInfo", "ClassPage", "SymbolResolveRequest", "SymbolResolution"))
			assertTrue(yaml.path("components").path("schemas").has(schema));
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
		var shutdown = yaml.path("paths").path("/shutdown").path("post");
		assertTrue(shutdown.path("requestBody").path("content").path("application/json")
				.path("schema").path("$ref").asText().endsWith("/ShutdownRequest"));
		var policy = yaml.path("components").path("schemas").path("ShutdownRequest").path("properties").path("policy");
		assertEquals("discard", policy.path("default").asText());
		assertEquals("[\"discard\",\"save\",\"refuse_if_dirty\"]", policy.path("enum").toString());
		for (String code : List.of("202", "400", "403", "409", "500", "503")) assertTrue(shutdown.path("responses").has(code));
		String shutdownConflict = shutdown.path("responses").path("409").path("description").asText();
		assertTrue(shutdownConflict.contains("INVALID_REQUEST"));
		assertTrue(shutdownConflict.contains("targetPath"));
		assertTrue(shutdown.path("responses").path("500").path("description").asText().contains("INTERNAL_ERROR"));
		assertEquals("#/components/schemas/ErrorEnvelope", shutdown.path("responses").path("500")
				.path("content").path("application/json").path("schema").path("$ref").asText());
		assertTrue(yaml.path("components").path("schemas").path("JobEvent").path("properties")
				.path("type").path("enum").toString().contains("job.cancel_requested"));
		assertTrue(yaml.path("components").path("schemas").path("RevisionSet")
				.path("required").toString().contains("persistedIdentityState"));
		ObjectMapper json = new ObjectMapper();
		for (String file : List.of("project.json", "save-request.json", "reload-request.json", "settings-update-request.json",
				"external-conflict.json", "project-busy.json", "job-queued.json",
				"job-running-unknown-total.json", "job-cancelling.json", "job-succeeded.json",
				"job-failed.json", "job-resource-limit.json", "shutdown-discard-request.json",
				"shutdown-save-request.json", "shutdown-accepted.json", "shutdown-busy.json",
				"shutdown-dirty-refused.json", "shutdown-raw-save-refused.json", "shutdown-save-failed.json",
				"classes-first-page.json", "class-resolved.json", "method-resolved.json", "field-resolved.json",
				"symbol-ambiguous.json", "symbol-provenance-unavailable.json", "classes-stale-cursor.json")) {
			assertTrue(json.readTree(Files.readString(Path.of("openapi/examples", file))).isObject());
		}
		assertEquals("INVALID_REQUEST", json.readTree(Files.readString(Path.of(
				"openapi/examples/shutdown-raw-save-refused.json"))).path("error").path("code").asText());
		assertEquals("INTERNAL_ERROR", json.readTree(Files.readString(Path.of(
				"openapi/examples/shutdown-save-failed.json"))).path("error").path("code").asText());
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
