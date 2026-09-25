package dev.libjadx.http;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.NoSuchElementException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import dev.libjadx.core.ProjectSnapshot;
import dev.libjadx.core.symbols.ClassQuery;
import dev.libjadx.core.symbols.SymbolCatalog;
import dev.libjadx.core.symbols.SymbolLookup;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.core.symbols.SymbolResolution;
import dev.libjadx.jadxadapter.JadxSymbolAdapter;
import dev.libjadx.project.NativeProjectRepository;
import dev.libjadx.scheduler.ProjectBusyException;
import dev.libjadx.scheduler.ServiceShuttingDownException;
import dev.libjadx.scheduler.JobRegistry;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.libjadx.app.ProjectRuntime;
import dev.libjadx.app.RuntimeStatus;
import dev.libjadx.app.ShutdownPolicy;
import dev.libjadx.app.ShutdownRequester;
import jakarta.servlet.ServletException;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Fixed-project API surface; the OpenAPI document is the public contract. */
public final class StatusServlet extends HttpServlet {
	private final ProjectRuntime runtime;
	private final ObjectMapper json;
	private final JobEventsHandler jobEvents;
	private final ShutdownRequester shutdown;
	private final AtomicBoolean shutdownRequestInProgress = new AtomicBoolean();
	private final byte[] cursorKey = CursorSigningKey.loadDefault();
	private volatile SymbolCatalog symbolCatalog;

	public StatusServlet(ProjectRuntime runtime, ObjectMapper json, ShutdownRequester shutdown) {
		this.runtime = runtime;
		this.json = json;
		this.shutdown = shutdown;
		this.jobEvents = new JobEventsHandler(runtime.jobRegistry());
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
					new Capability("class.list", "PARTIAL", "JADX_VISIBLE_INCLUDING_INNERS_AND_NO_CODE", "READ_ONLY"),
					new Capability("symbol.resolve", "PARTIAL", "PINNED_RAW_CLASS_METHOD_FIELD_METADATA", "READ_ONLY"),
					new Capability("symbol.input_provenance", "UNKNOWN", "TWO_JAR_DUPLICATE_COLLAPSED_ORIGIN_UNVERIFIED", "UNAVAILABLE"),
					new Capability("code.java", "SUPPORTED", "SMALL_JAR_PROBED", "READ_ONLY"),
					new Capability("code.smali", "SUPPORTED", "SMALL_JAR_PROBED", "READ_ONLY"),
					new Capability("references.method_uses", "PARTIAL", "LOCAL_CALLERS_PROBED", "READ_ONLY"),
					new Capability("project.native_load", "SUPPORTED", "JADX_1_5_6_FIXTURE", "READ_ONLY"),
					new Capability("project.native_save", "PARTIAL", "CLASS_RENAME_COMMENT_MAPPING_AND_GUI_ROUND_TRIP", "EXPLICIT_SAVE_ONLY"),
					new Capability("project.revisions", "PARTIAL", "CONTENT_HASH_AND_SESSION_TOKENS", "PROCESS_LOCAL_COUNTERS"),
					new Capability("analysis.temporary_override", "PARTIAL", "ISOLATED_DECOMPILATION_MODE_WITH_UNSAVED_EDITS", "READ_ONLY"),
					new Capability("analysis.cfg", "UNKNOWN", "NOT_PROBED", "UNAVAILABLE"),
					new Capability("analysis.concurrent_reads", "UNKNOWN", "NOT_PROBED", "UNAVAILABLE"),
					new Capability("analysis.cancellation", "UNKNOWN", "NOT_PROBED", "UNAVAILABLE"))));
			return;
		}
		if ("/api/v1/project".equals(path)) {
			write(response, HttpServletResponse.SC_OK, projectResponse(runtime.projectSnapshot()));
			return;
		}
		if ("/api/v1/project/settings".equals(path)) {
			write(response, 200, settingsResponse(runtime.settingsSnapshot()));
			return;
		}
		if ("/api/v1/classes".equals(path)) {
			handleClasses(request, response);
			return;
		}
		if (path.matches("/api/v1/jobs/[^/]+") || path.matches("/api/v1/jobs/[^/]+/events")) {
			try {
				String raw = path.substring("/api/v1/jobs/".length()).split("/", 2)[0];
				UUID id = jobId(raw);
				if (path.endsWith("/events")) jobEvents.start(request, response, id);
				else write(response, 200, JobHttpResponses.map(runtime.jobRegistry().snapshot(id), json));
			} catch (JobRegistry.EventHistoryExpiredException expired) {
				writeError(response, 409, "EVENT_HISTORY_EXPIRED", expired.getMessage());
			} catch (JobRegistry.ResourceLimitException limit) {
				writeError(response, 429, "RESOURCE_LIMIT", limit.getMessage(), true);
			} catch (NoSuchElementException missing) {
				writeError(response, 404, "NOT_FOUND", "Job not found or expired");
			} catch (IllegalArgumentException invalid) {
				writeError(response, 400, "INVALID_REQUEST", invalid.getMessage());
			}
			return;
		}
		writeUnavailableOrUnimplemented(request, response);
	}

	private void handlePatch(HttpServletRequest request, HttpServletResponse response) throws IOException {
		if (!"/api/v1/project/settings".equals(request.getRequestURI())) {
			writeUnavailableOrUnimplemented(request, response);
			return;
		}
		try {
			runtime.assertReady();
			if (!isJsonContentType(request.getContentType())) {
				writeError(response, 415, "INVALID_REQUEST", "Content-Type must be application/json");
				return;
			}
			JsonNode body = readBody(request);
			if (!body.has("mappingsPath") || !body.has("expectedLogicalRevision")
					|| !body.hasNonNull("expectedSessionId") || !body.get("expectedSessionId").isTextual()
					|| !body.get("expectedLogicalRevision").canConvertToLong()) {
				throw new IllegalArgumentException("mappingsPath, expectedSessionId and expectedLogicalRevision are required");
			}
			if (body.has("decompilationMode")) {
				writeError(response, 422, "UNSUPPORTED_CAPABILITY", "Decompiler mode is temporary-only and cannot be saved in the native project");
				return;
			}
			JsonNode mapping = body.get("mappingsPath");
			if (!mapping.isNull() && !mapping.isTextual()) throw new IllegalArgumentException("mappingsPath must be a path string or null");
			Path path = mapping.isNull() ? null : Path.of(mapping.asText());
			runtime.updateMappingsPath(path, body.get("expectedSessionId").asText(),
					body.get("expectedLogicalRevision").longValue());
			write(response, 200, settingsResponse(runtime.settingsSnapshot()));
		} catch (ProjectRuntime.ProjectNotReadyException notReady) {
			writeLifecycleError(response, notReady.status());
		} catch (ServiceShuttingDownException shuttingDown) {
			writeError(response, 503, "SERVICE_SHUTTING_DOWN", shuttingDown.getMessage());
		} catch (ProjectBusyException busy) {
			writeError(response, 409, "PROJECT_BUSY", busy.getMessage());
		} catch (NativeProjectRepository.StaleRevisionException stale) {
			writeError(response, 409, "STALE_REVISION", stale.getMessage());
		} catch (NativeProjectRepository.ExternalModificationException conflict) {
			writeError(response, 409, "EXTERNAL_MODIFICATION_CONFLICT", conflict.getMessage());
		} catch (SecurityException denied) {
			writeError(response, 403, "INVALID_REQUEST", denied.getMessage());
		} catch (IllegalArgumentException invalid) {
			writeError(response, 400, "INVALID_REQUEST", invalid.getMessage());
		} catch (IOException invalidPath) {
			writeError(response, 400, "INVALID_REQUEST", "Mapping path cannot be resolved or read");
		} catch (Exception failure) {
			writeError(response, 500, "INTERNAL_ERROR", "Settings rebuild failed; the previous analysis remains active");
		}
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String path = request.getRequestURI();
		if ("/api/v1/symbols/resolve".equals(path)) {
			handleSymbolResolve(request, response);
			return;
		}
		if ("/api/v1/shutdown".equals(path)) {
			if (!sameOrigin(request)) {
				writeError(response, 403, "INPUT_SECURITY_REJECTION", "Cross-origin shutdown is not allowed");
				return;
			}
			try {
				ShutdownPolicy policy = ShutdownPolicy.DISCARD;
				if (request.getContentLengthLong() != 0) {
					if (!isJsonContentType(request.getContentType())) throw new IllegalArgumentException("Content-Type must be application/json");
					JsonNode body = readBody(request);
					if (body.size() > (body.has("policy") ? 1 : 0)) throw new IllegalArgumentException("Unknown shutdown request field");
					if (body.has("policy")) {
						if (!body.get("policy").isTextual()) throw new IllegalArgumentException("policy must be a string");
						policy = ShutdownPolicy.fromWireName(body.get("policy").asText());
					}
				}
				if (!shutdownRequestInProgress.compareAndSet(false, true)) {
					writeError(response, 409, "PROJECT_BUSY", "A shutdown request is already in progress");
					return;
				}
				AsyncContext async;
				try {
					async = request.startAsync();
					async.setTimeout(0);
				} catch (RuntimeException failure) {
					shutdownRequestInProgress.set(false);
					throw failure;
				}
				ShutdownPolicy selected = policy;
				try {
					Thread.ofVirtual().name("libjadx-shutdown-policy").start(() -> handleShutdown(async, selected));
				} catch (RuntimeException | Error failure) {
					shutdownRequestInProgress.set(false);
					async.complete();
					throw failure;
				}
			} catch (IllegalArgumentException invalid) {
				writeError(response, 400, "INVALID_REQUEST", invalid.getMessage());
			}
			return;
		}
		if (path.matches("/api/v1/jobs/[^/]+/cancel")) {
			try {
				if (!sameOrigin(request)) {
					writeError(response, 403, "INPUT_SECURITY_REJECTION", "Cross-origin job cancellation is not allowed");
					return;
				}
				String raw = path.substring("/api/v1/jobs/".length(), path.length() - "/cancel".length());
				write(response, 200, JobHttpResponses.map(runtime.jobRegistry().cancel(jobId(raw)), json));
			} catch (NoSuchElementException missing) {
				writeError(response, 404, "NOT_FOUND", "Job not found or expired");
			} catch (IllegalArgumentException invalid) {
				writeError(response, 400, "INVALID_REQUEST", invalid.getMessage());
			}
			return;
		}
		if ("/api/v1/project/save".equals(path) || "/api/v1/project/reload".equals(path)
				|| "/api/v1/project/pending-edits/export".equals(path)) {
			try {
				runtime.assertReady();
				if ("/api/v1/project/pending-edits/export".equals(path)) {
					write(response, 200, json.readValue(runtime.pendingEdits().toString(), Object.class));
					return;
				}
				if (!isJsonContentType(request.getContentType())) {
					writeError(response, 415, "INVALID_REQUEST", "Content-Type must be application/json");
					return;
				}
				JsonNode body = readBody(request);
				if ("/api/v1/project/save".equals(path)) {
					if (body.has("targetPath") && !body.get("targetPath").isTextual()) throw new IllegalArgumentException("targetPath must be a string");
					if (body.has("expectedLogicalRevision") && !body.get("expectedLogicalRevision").canConvertToLong()) {
						throw new IllegalArgumentException("expectedLogicalRevision must be an integer");
					}
					if (body.has("expectedLogicalRevision") && (!body.hasNonNull("expectedSessionId")
							|| !body.get("expectedSessionId").isTextual())) {
						throw new IllegalArgumentException("expectedSessionId is required with expectedLogicalRevision");
					}
					Path target = body.hasNonNull("targetPath") ? Path.of(body.get("targetPath").asText()) : null;
					Long expected = body.hasNonNull("expectedLogicalRevision") ? body.get("expectedLogicalRevision").longValue() : null;
					String expectedSession = body.hasNonNull("expectedSessionId") ? body.get("expectedSessionId").asText() : null;
					write(response, 200, projectResponse(runtime.saveProject(target, expectedSession, expected)));
				} else if ("/api/v1/project/reload".equals(path)) {
					if (!body.has("discardUnsaved") || !body.get("discardUnsaved").isBoolean()
							|| !body.hasNonNull("expectedSessionId") || !body.get("expectedSessionId").isTextual()
							|| !body.hasNonNull("expectedLogicalRevision")
							|| !body.get("expectedLogicalRevision").canConvertToLong()
							|| body.get("expectedLogicalRevision").longValue() < 0) {
						throw new IllegalArgumentException("discardUnsaved, expectedSessionId and expectedLogicalRevision are required");
					}
					write(response, 200, projectResponse(runtime.reloadProject(
							body.get("discardUnsaved").booleanValue(), body.get("expectedSessionId").asText(),
							body.get("expectedLogicalRevision").longValue())));
				}
			} catch (ProjectRuntime.ProjectNotReadyException notReady) {
				writeLifecycleError(response, notReady.status());
			} catch (ServiceShuttingDownException shuttingDown) {
				writeError(response, 503, "SERVICE_SHUTTING_DOWN", shuttingDown.getMessage());
			} catch (ProjectBusyException busy) {
				writeError(response, 409, "PROJECT_BUSY", busy.getMessage());
			} catch (NativeProjectRepository.ExternalModificationException conflict) {
				writeError(response, 409, "EXTERNAL_MODIFICATION_CONFLICT", conflict.getMessage());
			} catch (NativeProjectRepository.StaleRevisionException stale) {
				writeError(response, 409, "STALE_REVISION", stale.getMessage());
			} catch (NativeProjectRepository.UnsavedChangesException unsaved) {
				writeError(response, 409, "PROJECT_BUSY", unsaved.getMessage(), false);
			} catch (SecurityException denied) {
				writeError(response, 403, "INVALID_REQUEST", denied.getMessage());
			} catch (IllegalArgumentException invalid) {
				writeError(response, 400, "INVALID_REQUEST", invalid.getMessage());
			} catch (Exception failure) {
				writeError(response, 500, "INTERNAL_ERROR", "Project operation failed; inspect the local service log");
			}
			return;
		}
		writeUnavailableOrUnimplemented(request, response);
	}

	private void handleClasses(HttpServletRequest request, HttpServletResponse response) throws IOException {
		try {
			runtime.assertReady();
			ClassQuery query = parseClassQuery(request);
			write(response, 200, runtime.withPrimarySymbolRead("symbol-catalog", context ->
					catalog(context).page(query)));
		} catch (SymbolCatalog.StaleCursorException stale) {
			writeError(response, 409, "STALE_REVISION", "Class cursor belongs to a previous project snapshot");
		} catch (ProjectRuntime.ProjectNotReadyException notReady) {
			writeLifecycleError(response, notReady.status());
		} catch (ServiceShuttingDownException stopping) {
			writeError(response, 503, "SERVICE_SHUTTING_DOWN", stopping.getMessage());
		} catch (ProjectBusyException busy) {
			writeError(response, 409, "PROJECT_BUSY", busy.getMessage());
		} catch (JadxSymbolAdapter.CatalogLimitException limit) {
			writeError(response, 429, "RESOURCE_LIMIT", limit.getMessage());
		} catch (IllegalArgumentException invalid) {
			writeError(response, 400, "INVALID_REQUEST", invalid.getMessage());
		} catch (RuntimeException failure) {
			writeError(response, 500, "INTERNAL_ERROR", "Class enumeration failed; inspect the local service log");
		}
	}

	private void handleSymbolResolve(HttpServletRequest request, HttpServletResponse response) throws IOException {
		try {
			runtime.assertReady();
			if (!isJsonContentType(request.getContentType())) {
				writeError(response, 415, "INVALID_REQUEST", "Content-Type must be application/json");
				return;
			}
			JsonNode body = readBody(request);
			requireFields(body, Set.of("ref", "expectedSessionId", "expectedLogicalRevision"));
			if (!body.has("ref") || !body.get("ref").isObject()) throw new IllegalArgumentException("ref object is required");
			boolean session = body.has("expectedSessionId");
			boolean revision = body.has("expectedLogicalRevision");
			if (session != revision) throw new IllegalArgumentException("Revision preconditions must be supplied together");
			if (session && (!body.get("expectedSessionId").isTextual()
					|| !body.get("expectedLogicalRevision").canConvertToLong()
					|| body.get("expectedLogicalRevision").longValue() < 0)) {
				throw new IllegalArgumentException("Invalid revision precondition");
			}
			if (session) {
				String candidate = body.get("expectedSessionId").asText();
				try {
					if (!UUID.fromString(candidate).toString().equalsIgnoreCase(candidate))
						throw new IllegalArgumentException("Invalid expectedSessionId");
				} catch (IllegalArgumentException invalid) {
					throw new IllegalArgumentException("Invalid expectedSessionId", invalid);
				}
			}
			SymbolRef ref = parseSymbolRef(body.get("ref"));
			String expectedSession = session ? body.get("expectedSessionId").asText() : null;
			Long expectedRevision = revision ? body.get("expectedLogicalRevision").longValue() : null;
			SymbolResolution result = runtime.withPrimarySymbolRead(ref.originalClassDescriptor(), context -> {
				if (expectedSession != null && (!context.revisions().sessionId().equals(expectedSession)
						|| context.revisions().logicalRevision() != expectedRevision)) {
					throw new SymbolCatalog.StaleCursorException();
				}
				return SymbolLookup.resolve(catalog(context), ref, entry -> {
					var cls = JadxSymbolAdapter.visibleClass(context.decompiler(),
							ref.originalClassDescriptor(), entry.occurrence());
					if (cls == null) throw new IllegalStateException("Visible class vanished during leased lookup");
					return JadxSymbolAdapter.matchingMembers(cls, ref);
				});
			});
			write(response, 200, result);
		} catch (SymbolCatalog.StaleCursorException stale) {
			writeError(response, 409, "STALE_REVISION", "Symbol precondition belongs to a previous project snapshot");
		} catch (ProjectRuntime.ProjectNotReadyException notReady) {
			writeLifecycleError(response, notReady.status());
		} catch (ServiceShuttingDownException stopping) {
			writeError(response, 503, "SERVICE_SHUTTING_DOWN", stopping.getMessage());
		} catch (ProjectBusyException busy) {
			writeError(response, 409, "PROJECT_BUSY", busy.getMessage());
		} catch (JadxSymbolAdapter.CatalogLimitException limit) {
			writeError(response, 429, "RESOURCE_LIMIT", limit.getMessage());
		} catch (IllegalArgumentException invalid) {
			writeError(response, 400, "INVALID_REQUEST", invalid.getMessage());
		} catch (RuntimeException failure) {
			writeError(response, 500, "INTERNAL_ERROR", "Symbol resolution failed; inspect the local service log");
		}
	}

	private SymbolCatalog catalog(ProjectRuntime.PrimarySymbolRead context) {
		SymbolCatalog current = symbolCatalog;
		if (current == null || !current.sessionId().equals(context.revisions().sessionId())
				|| current.logicalRevision() != context.revisions().logicalRevision()
				|| current.publicationEpoch() != context.publicationEpoch()) {
			current = new SymbolCatalog(JadxSymbolAdapter.classes(context.decompiler()),
					context.revisions().sessionId(), context.revisions().logicalRevision(),
					context.publicationEpoch(), cursorKey);
			symbolCatalog = current;
		}
		return current;
	}

	private static SymbolRef parseSymbolRef(JsonNode node) {
		requireFields(node, Set.of("kind", "originalClassDescriptor", "inputIdentity", "originalName", "originalDescriptor"));
		if (!node.hasNonNull("kind") || !node.get("kind").isTextual()
				|| !node.hasNonNull("originalClassDescriptor") || !node.get("originalClassDescriptor").isTextual()) {
			throw new IllegalArgumentException("kind and originalClassDescriptor are required strings");
		}
		for (String key : List.of("inputIdentity", "originalName", "originalDescriptor")) {
			if (node.has(key) && !node.get(key).isNull() && !node.get(key).isTextual()) {
				throw new IllegalArgumentException(key + " must be a string or null");
			}
		}
		SymbolRef.Kind kind;
		try { kind = SymbolRef.Kind.valueOf(node.get("kind").asText()); }
		catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Unknown symbol kind"); }
		return new SymbolRef(kind, node.get("originalClassDescriptor").asText(), optionalText(node, "inputIdentity"),
				optionalText(node, "originalName"), optionalText(node, "originalDescriptor"));
	}

	private static String optionalText(JsonNode node, String key) {
		return node.hasNonNull(key) ? node.get(key).asText() : null;
	}

	private static void requireFields(JsonNode node, Set<String> allowed) {
		node.fieldNames().forEachRemaining(name -> {
			if (!allowed.contains(name)) throw new IllegalArgumentException("Unknown field: " + name);
		});
	}

	private static ClassQuery parseClassQuery(HttpServletRequest request) {
		String raw = request.getQueryString();
		if (raw != null && raw.length() > 4096) throw new IllegalArgumentException("Query string exceeds 4096 characters");
		Map<String, String[]> parameters = request.getParameterMap();
		for (var entry : parameters.entrySet()) {
			if (!Set.of("pageSize", "cursor", "packagePrefix", "nameContains", "nameDomain", "includeInner").contains(entry.getKey())
					|| entry.getValue().length != 1) throw new IllegalArgumentException("Unknown or repeated class query parameter");
		}
		String size = request.getParameter("pageSize");
		int pageSize;
		try { pageSize = size == null ? 50 : Integer.parseInt(size); }
		catch (NumberFormatException invalid) { throw new IllegalArgumentException("Invalid pageSize"); }
		String domain = request.getParameter("nameDomain");
		ClassQuery.NameDomain nameDomain;
		try { nameDomain = domain == null ? ClassQuery.NameDomain.original : ClassQuery.NameDomain.valueOf(domain); }
		catch (IllegalArgumentException invalid) { throw new IllegalArgumentException("Invalid nameDomain"); }
		String inner = request.getParameter("includeInner");
		if (inner != null && !inner.equals("true") && !inner.equals("false")) throw new IllegalArgumentException("Invalid includeInner");
		return new ClassQuery(pageSize, request.getParameter("cursor"), request.getParameter("packagePrefix"),
				request.getParameter("nameContains"), nameDomain, inner == null || Boolean.parseBoolean(inner));
	}

	private void handleShutdown(AsyncContext async, ShutdownPolicy policy) {
		HttpServletResponse response = (HttpServletResponse) async.getResponse();
		boolean accepted = false;
		try {
			shutdown.request(policy);
			accepted = true;
			write(response, 202, new ShutdownAccepted("SHUTTING_DOWN", policy.wireName()));
			response.flushBuffer();
		} catch (ProjectRuntime.ShutdownRejectedException rejected) {
			writeShutdownError(response, rejected.statusCode(), rejected.code(), rejected.getMessage(),
					rejected.retryable(), rejected.details());
		} catch (ProjectRuntime.ProjectNotReadyException notReady) {
			try { writeLifecycleError(response, notReady.status()); }
			catch (IOException failure) { System.err.println("libjadx: shutdown response failed"); }
		} catch (ServiceShuttingDownException stopping) {
			writeShutdownError(response, 503, "SERVICE_SHUTTING_DOWN", stopping.getMessage(), false, null);
		} catch (NativeProjectRepository.ExternalModificationException conflict) {
			writeShutdownError(response, 409, "EXTERNAL_MODIFICATION_CONFLICT", conflict.getMessage(), false, null);
		} catch (IOException failure) {
			writeShutdownError(response, 500, "INTERNAL_ERROR", "Native save failed; the service remains available", false, null);
		} catch (RuntimeException failure) {
			writeShutdownError(response, 500, "INTERNAL_ERROR", "Shutdown request failed; inspect the local service log", false, null);
		} finally {
			shutdownRequestInProgress.set(false);
			try { async.complete(); }
			finally { if (accepted) shutdown.responseCommitted(); }
		}
	}

	private void writeShutdownError(HttpServletResponse response, int status, String code, String message,
			boolean retryable, Object details) {
		try { writeError(response, status, code, message, retryable, details); }
		catch (IOException failure) { System.err.println("libjadx: shutdown response failed"); }
	}

	private JsonNode readBody(HttpServletRequest request) throws IOException {
		byte[] bytes = request.getInputStream().readNBytes(65_537);
		if (bytes.length > 65_536) throw new IllegalArgumentException("Request body exceeds 64 KiB");
		JsonNode body;
		try {
			body = json.readTree(new String(bytes, StandardCharsets.UTF_8));
		} catch (JsonProcessingException malformed) {
			throw new IllegalArgumentException("Malformed JSON request body", malformed);
		}
		if (body == null || !body.isObject()) throw new IllegalArgumentException("JSON object body required");
		return body;
	}

	private static boolean isJsonContentType(String contentType) {
		return contentType != null && "application/json".equalsIgnoreCase(contentType.split(";", 2)[0].trim());
	}

	private static UUID jobId(String raw) {
		UUID parsed = UUID.fromString(raw);
		if (!parsed.toString().equalsIgnoreCase(raw)) throw new IllegalArgumentException("Job ID must be a UUID");
		return parsed;
	}

	private static boolean sameOrigin(HttpServletRequest request) {
		String origin = request.getHeader("Origin");
		if (origin == null) return true;
		try {
			URI parsed = URI.create(origin);
			return parsed.getScheme().equalsIgnoreCase(request.getScheme())
					&& parsed.getRawAuthority().equalsIgnoreCase(request.getHeader("Host"))
					&& (parsed.getRawPath() == null || parsed.getRawPath().isEmpty())
					&& parsed.getRawQuery() == null;
		} catch (RuntimeException invalid) {
			return false;
		}
	}

	private static FixedProjectResponse projectResponse(ProjectSnapshot snapshot) {
		return new FixedProjectResponse(string(snapshot.projectPath()), snapshot.inputs().stream().map(StatusServlet::string).toList(),
				snapshot.dirty(), snapshot.revisions());
	}

	private static ProjectSettingsResponse settingsResponse(ProjectRuntime.SettingsSnapshot snapshot) {
		return new ProjectSettingsResponse(string(snapshot.mappingsPath()), snapshot.effective().decompilationMode(),
				snapshot.revisions());
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
		if (!"GET".equals(request.getMethod()) && !"POST".equals(request.getMethod())
				&& !"PATCH".equals(request.getMethod())) {
			writeUnavailableOrUnimplemented(request, response);
			return;
		}
		if ("PATCH".equals(request.getMethod())) {
			handlePatch(request, response);
			return;
		}
		try {
			super.service(request, response);
		} catch (ProjectRuntime.ProjectNotReadyException notReady) {
			writeLifecycleError(response, notReady.status());
		} catch (ServiceShuttingDownException shuttingDown) {
			writeError(response, 503, "SERVICE_SHUTTING_DOWN", shuttingDown.getMessage());
		}
	}

	private void write(HttpServletResponse response, int status, Object body) throws IOException {
		response.setStatus(status);
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		json.writeValue(response.getOutputStream(), body);
	}

	private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
		boolean retryable = "PROJECT_NOT_READY".equals(code) || "PROJECT_BUSY".equals(code);
		writeError(response, status, code, message, retryable);
	}

	private void writeError(HttpServletResponse response, int status, String code, String message,
			boolean retryable) throws IOException {
		writeError(response, status, code, message, retryable, null);
	}

	private void writeError(HttpServletResponse response, int status, String code, String message,
			boolean retryable, Object details) throws IOException {
		write(response, status, new ErrorEnvelope(new ErrorBody(code, message, retryable, response.getHeader("X-Request-Id"), details)));
	}

	private void writeUnavailableOrUnimplemented(HttpServletRequest request, HttpServletResponse response) throws IOException {
		if (!isPlannedOperation(request.getRequestURI())) {
			writeError(response, HttpServletResponse.SC_NOT_FOUND, "NOT_FOUND", "No endpoint is defined for this path");
			return;
		}
		RuntimeStatus current = runtime.status();
		if (!"READY".equals(current.state())) {
			writeLifecycleError(response, current);
			return;
		}
		writeError(response, HttpServletResponse.SC_NOT_IMPLEMENTED, "OPERATION_NOT_IMPLEMENTED", "This API operation is not implemented yet");
	}

	private void writeLifecycleError(HttpServletResponse response, RuntimeStatus current) throws IOException {
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
		writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "PROJECT_NOT_READY", "The fixed project is not ready");
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
	public record FixedProjectResponse(String projectPath, List<String> inputs, boolean dirty,
			dev.libjadx.core.RevisionState revisions) { }
	public record ProjectSettingsResponse(String mappingsPath, String decompilationMode,
			dev.libjadx.core.RevisionState revisions) { }
	public record ShutdownAccepted(String state, String policy) { }
}
