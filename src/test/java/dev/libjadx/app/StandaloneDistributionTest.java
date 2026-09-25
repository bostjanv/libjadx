package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the production distribution without the test-only jadx-gui dependency. */
class StandaloneDistributionTest {
	private static final ObjectMapper JSON = new ObjectMapper();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
	@TempDir Path dir;

	@Test
	void installedServiceLoadsClassesAndAuthenticatesCursorsAcrossRestart() throws Exception {
		Path script = Path.of(System.getProperty("libjadx.distributionScript"));
		Path project = Path.of("tests/fixtures/native-project/sample.jar.jadx").toAbsolutePath();
		int port;
		try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
		String cursor;
		Process first = start(script, project, port, "first");
		try {
			awaitReady(first, port);
			JsonNode page = body(get(port, "/api/v1/classes?pageSize=1"));
			assertEquals("Lprobe/Sample;", page.path("items").get(0).path("ref")
					.path("originalClassDescriptor").asText());
			cursor = page.path("nextCursor").asText();
			assertFalse(cursor.isBlank());
		} finally { stop(first, port); }

		Process second = start(script, project, port, "second");
		try {
			awaitReady(second, port);
			assertError(get(port, "/api/v1/classes?pageSize=1&cursor=" + encode(cursor)), 409, "STALE_REVISION");
			String[] parts = cursor.split("\\.", -1);
			String[] fields = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8).split("\0", -1);
			fields[1] = "tampered-session";
			String tampered = Base64.getUrlEncoder().withoutPadding().encodeToString(
					String.join("\0", fields).getBytes(StandardCharsets.UTF_8)) + "." + parts[1];
			assertError(get(port, "/api/v1/classes?pageSize=1&cursor=" + encode(tampered)), 400, "INVALID_REQUEST");
		} finally { stop(second, port); }
	}

	private Process start(Path script, Path project, int port, String name) throws Exception {
		ProcessBuilder builder = new ProcessBuilder(script.toString(), "--project", project.toString(),
				"--port", Integer.toString(port));
		builder.environment().put("XDG_STATE_HOME", dir.resolve("state").toString());
		builder.redirectErrorStream(true);
		builder.redirectOutput(dir.resolve(name + ".log").toFile());
		return builder.start();
	}

	private static void awaitReady(Process process, int port) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
		while (System.nanoTime() < deadline) {
			if (!process.isAlive()) throw new AssertionError("Standalone service exited before readiness: " + process.exitValue());
			try {
				HttpResponse<String> response = get(port, "/api/v1/status");
				if (response.statusCode() == 200 && "READY".equals(body(response).path("state").asText())) return;
			} catch (java.io.IOException ignored) { /* Listener is still starting. */ }
			Thread.sleep(100);
		}
		throw new AssertionError("Standalone service did not become ready within 30 seconds");
	}

	private static void stop(Process process, int port) throws Exception {
		if (!process.isAlive()) return;
		try { HTTP.send(HttpRequest.newBuilder(uri(port, "/api/v1/shutdown"))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString("{\"policy\":\"discard\"}"))
				.build(), HttpResponse.BodyHandlers.ofString());
		} catch (java.io.IOException ignored) { /* Process may already be exiting. */ }
		if (!process.waitFor(10, TimeUnit.SECONDS)) {
			process.destroyForcibly();
			assertTrue(process.waitFor(5, TimeUnit.SECONDS));
		}
	}

	private static void assertError(HttpResponse<String> response, int status, String code) throws Exception {
		assertEquals(status, response.statusCode(), response.body());
		assertEquals(code, body(response).path("error").path("code").asText());
	}

	private static HttpResponse<String> get(int port, String path) throws Exception {
		return HTTP.send(HttpRequest.newBuilder(uri(port, path)).GET().build(), HttpResponse.BodyHandlers.ofString());
	}

	private static URI uri(int port, String path) { return URI.create("http://127.0.0.1:" + port + path); }
	private static JsonNode body(HttpResponse<String> response) throws Exception { return JSON.readTree(response.body()); }
	private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
