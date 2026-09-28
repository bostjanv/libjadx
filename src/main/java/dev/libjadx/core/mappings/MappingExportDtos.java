package dev.libjadx.core.mappings;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Export is an operational artifact, never a project mutation or native save. */
public final class MappingExportDtos {
	private MappingExportDtos() { }
	public static final int MAX_ENTRIES = 10_000;
	public static final int MAX_BYTES = 4 * 1024 * 1024;
	public static final int MAX_MEMORY = 16 * 1024 * 1024;
	public static final int MAX_STRING = 16_384;

	public record Request(Path targetPath, String format, String expectedSessionId, long expectedLogicalRevision) {
		public Request {
			if (targetPath == null || !targetPath.isAbsolute() || !targetPath.equals(targetPath.normalize())
					|| targetPath.toString().length() > 4096 || targetPath.getFileName() == null
					|| !targetPath.getFileName().toString().endsWith(".tiny")) {
				throw new IllegalArgumentException("targetPath must be an absolute normalized .tiny path (at most 4096 characters)");
			}
			if (!"TINY_V2".equals(format)) throw new IllegalArgumentException("format must be TINY_V2");
			if (expectedSessionId == null || !UUID.fromString(expectedSessionId).toString().equals(expectedSessionId)
					|| expectedLogicalRevision < 0) throw new IllegalArgumentException("Invalid revision precondition");
		}
	}
	public record Counts(int classes, int methods, int fields, int comments) {
		public int total() { return classes + methods + fields + comments; }
	}
	public record Receipt(String format, String targetPath, String sessionId, long logicalRevision,
			String source, String completeness, Counts exported, long bytes, String sha256,
			List<String> omissions, boolean projectMutated) { }

	/** Bounded categories only: no exception text or arbitrary source contents in HTTP. */
	public static final class Problem extends RuntimeException {
		private final int status;
		private final String code;
		private final Map<String, Object> details;
		public Problem(int status, String code, String message, Map<String, Object> details) {
			super(message); this.status = status; this.code = code; this.details = Map.copyOf(details);
		}
		public int status() { return status; }
		public String code() { return code; }
		public Map<String, Object> details() { return details; }
	}
	public static Problem unsupported(String category) {
		return new Problem(422, "UNSUPPORTED_CAPABILITY", "Current mapping data cannot be exported exactly",
				Map.of("omissions", List.of(Map.of("category", category, "count", 1))));
	}
	public static Problem limit() {
		return new Problem(429, "RESOURCE_LIMIT", "Mapping export exceeds bounded entry, string, byte or memory limits", Map.of());
	}
}
