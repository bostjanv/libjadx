package dev.libjadx.http;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.libjadx.app.ProjectRuntime;
import dev.libjadx.app.RuntimeStatus;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Initial read-only API surface; the OpenAPI document is the public contract. */
public final class StatusServlet extends HttpServlet {
	private final ProjectRuntime runtime;
	private final ObjectMapper json;

	public StatusServlet(ProjectRuntime runtime, ObjectMapper json) {
		this.runtime = runtime;
		this.json = json;
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String path = request.getRequestURI();
		if ("/api/v1/health/live".equals(path)) {
			write(response, HttpServletResponse.SC_OK, new HealthResponse("ALIVE"));
			return;
		}
		if ("/api/v1/status".equals(path)) {
			RuntimeStatus status = runtime.status();
			write(response, HttpServletResponse.SC_OK, new StatusResponse(
					status.state(), status.stage(), status.progress(), string(status.projectPath()),
					status.inputs().stream().map(StatusServlet::string).toList(), status.error()));
			return;
		}
		if ("/api/v1/capabilities".equals(path)) {
			write(response, HttpServletResponse.SC_OK, new CapabilitiesResponse("0.1.0-SNAPSHOT", "1.5.6", List.of(
					new Capability("class.list", "SUPPORTED", "SMALL_JAR_PROBED", "READ_ONLY"),
					new Capability("code.java", "SUPPORTED", "SMALL_JAR_PROBED", "READ_ONLY"),
					new Capability("code.smali", "SUPPORTED", "SMALL_JAR_PROBED", "READ_ONLY"),
					new Capability("references.method_uses", "PARTIAL", "LOCAL_CALLERS_PROBED", "READ_ONLY"),
					new Capability("project.native_load", "SUPPORTED", "JADX_1_5_6_FIXTURE", "READ_ONLY"),
					new Capability("project.native_save", "PARTIAL", "CLASS_RENAME_AND_COMMENT_GUI_ROUND_TRIP", "EXPLICIT_SAVE_ONLY"),
					new Capability("analysis.cfg", "UNKNOWN", "NOT_PROBED", "UNAVAILABLE"),
					new Capability("analysis.concurrent_reads", "UNKNOWN", "NOT_PROBED", "UNAVAILABLE"),
					new Capability("analysis.cancellation", "UNKNOWN", "NOT_PROBED", "UNAVAILABLE"))));
			return;
		}
		writeUnavailableOrUnimplemented(request, response);
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
		writeUnavailableOrUnimplemented(request, response);
	}

	@Override
	protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setHeader("X-Request-Id", UUID.randomUUID().toString());
		response.setHeader("Cache-Control", "no-store");
		Set<String> allowedMethods = allowedMethods(request.getRequestURI());
		if (allowedMethods.isEmpty()) {
			writeError(response, HttpServletResponse.SC_NOT_FOUND, "NOT_FOUND", "No endpoint is defined for this path");
			return;
		}
		if (!allowedMethods.contains(request.getMethod())) {
			response.setHeader("Allow", String.join(", ", allowedMethods));
			writeError(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "The HTTP method is not allowed for this path");
			return;
		}
		if (!"GET".equals(request.getMethod()) && !"POST".equals(request.getMethod())) {
			writeUnavailableOrUnimplemented(request, response);
			return;
		}
		super.service(request, response);
	}

	private void write(HttpServletResponse response, int status, Object body) throws IOException {
		response.setStatus(status);
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		json.writeValue(response.getOutputStream(), body);
	}

	private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
		boolean retryable = "PROJECT_NOT_READY".equals(code);
		write(response, status, new ErrorEnvelope(new ErrorBody(code, message, retryable, response.getHeader("X-Request-Id"), null)));
	}

	private void writeUnavailableOrUnimplemented(HttpServletRequest request, HttpServletResponse response) throws IOException {
		if (!isPlannedOperation(request.getRequestURI())) {
			writeError(response, HttpServletResponse.SC_NOT_FOUND, "NOT_FOUND", "No endpoint is defined for this path");
			return;
		}
		RuntimeStatus current = runtime.status();
		if ("LOADING".equals(current.state()) || "RELOADING".equals(current.state())) {
			response.setHeader("Retry-After", "2");
			writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "PROJECT_NOT_READY",
					"The fixed project is still loading; inspect GET /api/v1/status for lifecycle details");
			return;
		}
		if ("FAILED".equals(current.state())) {
			RuntimeStatus.ApiError failure = current.error();
			String code = failure == null ? "PROJECT_LOAD_FAILED" : failure.code();
			String message = failure == null ? "The fixed project could not be loaded" : failure.message();
			writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, code, message);
			return;
		}
		if ("SHUTTING_DOWN".equals(current.state()) || "STOPPED".equals(current.state())) {
			writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "SERVICE_SHUTTING_DOWN", "The service is shutting down");
			return;
		}
		if (!"READY".equals(current.state())) {
			writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "PROJECT_NOT_READY", "The fixed project is not ready");
			return;
		}
		writeError(response, HttpServletResponse.SC_NOT_IMPLEMENTED, "OPERATION_NOT_IMPLEMENTED", "This API operation is not implemented yet");
	}

	private static Set<String> allowedMethods(String path) {
		if ("/api/v1/health/live".equals(path) || "/api/v1/status".equals(path) || "/api/v1/capabilities".equals(path)
				|| "/api/v1/project".equals(path) || "/api/v1/classes".equals(path)
				|| path.matches("/api/v1/jobs/[^/]+") || path.matches("/api/v1/jobs/[^/]+/events")) {
			return Set.of("GET");
		}
		if ("/api/v1/project/settings".equals(path)) return Set.of("GET", "PATCH");
		if ("/api/v1/project/save".equals(path) || "/api/v1/project/reload".equals(path)
				|| "/api/v1/project/export".equals(path) || "/api/v1/project/pending-edits/export".equals(path)
				|| "/api/v1/symbols/resolve".equals(path) || "/api/v1/decompile".equals(path)
				|| "/api/v1/references/query".equals(path) || "/api/v1/analysis/cfg".equals(path)
				|| "/api/v1/search".equals(path) || "/api/v1/search/build-index".equals(path)
				|| "/api/v1/edits/batch".equals(path) || "/api/v1/resources/query".equals(path)
				|| "/api/v1/shutdown".equals(path) || path.matches("/api/v1/jobs/[^/]+/cancel")) {
			return Set.of("POST");
		}
		return Set.of();
	}

	private static boolean isPlannedOperation(String path) {
		return !"/api/v1/health/live".equals(path) && !"/api/v1/status".equals(path) && !"/api/v1/capabilities".equals(path)
				&& !allowedMethods(path).isEmpty();
	}

	private static String string(Object value) {
		return value == null ? null : value.toString();
	}

	public record HealthResponse(String status) { }
	public record StatusResponse(String state, String stage, RuntimeStatus.Progress progress, String projectPath,
			List<String> inputs, RuntimeStatus.ApiError error) { }
	public record Capability(String name, String status, String evidence, String persistence) { }
	public record CapabilitiesResponse(String serverVersion, String jadxVersion, List<Capability> capabilities) { }
	public record ErrorBody(String code, String message, boolean retryable, String requestId, Object details) { }
	public record ErrorEnvelope(ErrorBody error) { }
}
