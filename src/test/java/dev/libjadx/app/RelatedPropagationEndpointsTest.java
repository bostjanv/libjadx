package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.libjadx.core.edits.EditDtos;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.http.HttpApiServer;
import dev.libjadx.probes.RelatedFixture;
import dev.libjadx.project.FileFingerprint;
import dev.libjadx.project.NativeProjectDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RelatedPropagationEndpointsTest {
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final HttpClient CLIENT = HttpClient.newHttpClient();
	@TempDir Path dir;

	@Test void propagationRemainsUnsupportedBeforeAnyMixedBatchStagingInCleanAndDirtyState() throws Exception {
		var inputs = RelatedFixture.compile(dir);
		Path path = dir.resolve("project.jadx");
		var project = NativeProjectDocument.newFromInputs(path, inputs);
		project.save();
		try (var runtime = new ProjectRuntime(path, inputs, List.of(dir));
				var server = new HttpApiServer("127.0.0.1", 0, runtime)) {
			server.start(); runtime.initializeAsync(project).get(20, TimeUnit.SECONDS);
			Path captures = Path.of("build/related-contract-responses"); Files.createDirectories(captures);
			String method = JSON.writeValueAsString(RelatedFixture.ref("Joined", "joined", "(I)I"));
			String regular = "{\"kind\":\"RENAME\",\"target\":{\"kind\":\"CLASS\",\"originalClassDescriptor\":\"Lrelated/UnrelatedCold;\"},\"newName\":\"ShouldNotApply\"}";
			var cold = runtime.decompiler().searchJavaClassByOrigFullName("related.UnrelatedCold").getClassNode();
			var coldState = cold.getState();
			for (boolean dirty : List.of(false, true)) {
				if (dirty) new EditBatchService(runtime).apply(new EditDtos.Request(null, null, List.of(
						new EditDtos.Operation(EditDtos.Kind.SET_COMMENT, SymbolRef.classRef("Lrelated/Hierarchy;"), null, "pending comment", "LINE"))));
				String sourceRequest = "{\"ref\":{\"kind\":\"CLASS\",\"originalClassDescriptor\":\"Lrelated/Hierarchy;\"}}";
				var source = JSON.readTree(post(server, "/api/v1/decompile", sourceRequest).body());
				String snapshot = source.path("sourceSnapshotId").asText(); assertFalse(snapshot.isEmpty());
				var before = runtime.projectSnapshot(); var identity = runtime.searchIdentity();
				String pending = runtime.pendingEdits().toString();
				var hashes = new java.util.ArrayList<FileFingerprint>();
				for (Path file : List.of(path, inputs.getFirst(), inputs.get(1))) hashes.add(FileFingerprint.of(file));
				String preconditions = "\"expectedSessionId\":\"" + before.revisions().sessionId() + "\",\"expectedLogicalRevision\":" + before.revisions().logicalRevision() + ",";
				for (String flag : List.of("true", "false", "null", "\"yes\"")) {
					var response = post(server, "/api/v1/edits/batch", "{" + preconditions + "\"items\":[" + regular
							+ ",{\"kind\":\"RENAME\",\"target\":" + method + ",\"newName\":\"renamedJoined\",\"propagateRelated\":" + flag + "}]}");
					assertEquals(422, response.statusCode(), response.body());
					var error = JSON.readTree(response.body()).path("error");
					assertEquals("UNSUPPORTED_CAPABILITY", error.path("code").asText());
					assertEquals(1, error.path("details").path("itemErrors").get(0).path("index").asInt());
					String name = (dirty ? "dirty-" : "clean-") + (flag.equals("\"yes\"") ? "string" : flag);
					Files.writeString(captures.resolve(name + ".json"), JSON.writeValueAsString(Map.of(
							"status", 422, "schema", "ErrorEnvelope", "body", JSON.readTree(response.body()))));
					assertEquals(before, runtime.projectSnapshot()); assertEquals(identity, runtime.searchIdentity());
					assertEquals(pending, runtime.pendingEdits().toString()); assertEquals(coldState, cold.getState());
					for (int i = 0; i < hashes.size(); i++) assertEquals(hashes.get(i), FileFingerprint.of(List.of(path, inputs.getFirst(), inputs.get(1)).get(i)));
				}
				assertEquals(snapshot, JSON.readTree(post(server, "/api/v1/decompile", sourceRequest).body()).path("sourceSnapshotId").asText());
				var capabilities = JSON.readTree(get(server, "/api/v1/capabilities").body());
				assertTrue(capabilities.toString().contains("edit.related_propagation"));
				var related = java.util.stream.StreamSupport.stream(capabilities.path("capabilities").spliterator(), false)
						.filter(c -> c.path("name").asText().equals("edit.related_propagation")).findFirst().orElseThrow();
				assertEquals("UNSUPPORTED", related.path("status").asText());
				assertEquals("INCOMPLETE_PINNED_OVERRIDE_GROUP", related.path("evidence").asText());
				assertEquals(coldState, cold.getState());
			}
		}
	}

	@Test void ordinaryRenameRetainsExistingImplicitCandidatePropagationAndEmptyAffectedRefs() throws Exception {
		var inputs = RelatedFixture.compile(dir);
		try (var runtime = new ProjectRuntime(null, inputs, List.of(dir))) {
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			var result = new EditBatchService(runtime).apply(new EditDtos.Request(null, null, List.of(
					new EditDtos.Operation(EditDtos.Kind.RENAME, RelatedFixture.ref("Joined", "joined", "(I)I"), "legacyJoined", null, null))));
			assertEquals("APPLIED", result.outcome()); assertTrue(result.items().getFirst().affectedRefs().isEmpty());
			runtime.decompiler().searchJavaClassByOrigFullName("related.Hierarchy").getCode();
			for (String owner : List.of("Joined", "ExtendedLeft", "SeparateLeft"))
				assertEquals("legacyJoined", RelatedFixture.method(runtime.decompiler(), RelatedFixture.ref(owner, "joined", "(I)I")).getName());
			assertEquals("joined", RelatedFixture.method(runtime.decompiler(), RelatedFixture.ref("SeparateRight", "joined", "(I)I")).getName());
			assertFalse(Files.exists(dir.resolve("hierarchy.jar.jadx")));
		}
	}

	private static URI uri(HttpApiServer server, String path) { return URI.create("http://127.0.0.1:" + server.localPort() + path); }
	private static HttpResponse<String> post(HttpApiServer server, String path, String body) throws Exception {
		return CLIENT.send(HttpRequest.newBuilder(uri(server, path)).header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
	}
	private static HttpResponse<String> get(HttpApiServer server, String path) throws Exception {
		return CLIENT.send(HttpRequest.newBuilder(uri(server, path)).GET().build(), HttpResponse.BodyHandlers.ofString());
	}
}
