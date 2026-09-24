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
	private static final Set<String> PLANNED_OPERATIONS = Set.of(
			"/api/v1/project", "/api/v1/project/settings", "/api/v1/project/save", "/api/v1/project/reload",
			"/api/v1/classes", "/api/v1/symbols/resolve", "/api/v1/decompile", "/api/v1/references/query",
			"/api/v1/analysis/cfg", "/api/v1/search", "/api/v1/search/build-index", "/api/v1/edits/batch",
			"/api/v1/resources/query", "/api/v1/jobs", "/api/v1/shutdown");
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
		if (!runtime.isReady()) {
			response.setHeader("Retry-After", "2");
			writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "PROJECT_NOT_READY",
					"The fixed project is not ready; inspect GET /api/v1/status for lifecycle details");
			return;
		}
		if (!isPlannedOperation(request.getRequestURI())) {
			writeError(response, HttpServletResponse.SC_NOT_FOUND, "NOT_FOUND", "No endpoint is defined for this path");
			return;
		}
		writeError(response, HttpServletResponse.SC_NOT_IMPLEMENTED, "OPERATION_NOT_IMPLEMENTED", "This API operation is not implemented yet");
	}

	private static boolean isPlannedOperation(String path) {
		return PLANNED_OPERATIONS.contains(path)
				|| path.startsWith("/api/v1/jobs/")
				|| "/api/v1/project/export".equals(path)
				|| "/api/v1/project/pending-edits/export".equals(path);
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
