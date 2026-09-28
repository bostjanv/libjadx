package dev.libjadx.core.mappings;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Import only stages native edits; receipts never imply an attachment or durable save. */
public final class MappingImportDtos {
	private MappingImportDtos() { }
	public record Request(Path sourcePath, String format, String mode, String expectedSessionId, long expectedLogicalRevision) {
		public Request {
			// Identical path, format and mandatory revision syntax to export.
			new MappingExportDtos.Request(sourcePath, format, expectedSessionId, expectedLogicalRevision);
			if (!"MERGE_FAIL_ON_CONFLICT".equals(mode)) throw new IllegalArgumentException("Unsupported merge mode");
		}
	}
	public record EditCounts(int aliases, int comments) {
		public int total() { return aliases + comments; }
	}
	public record Receipt(String format, String mode, String sourcePath, String sha256, long bytes,
			MappingExportDtos.Counts parsed, EditCounts applied, EditCounts unchanged, String sessionId,
			long beforeLogicalRevision, long afterLogicalRevision, long beforeIndexRevision, long afterIndexRevision,
			boolean dirty, boolean saved, boolean mappingAttached, String outcome, List<String> omissions) {
		public Receipt { omissions = List.copyOf(omissions); }
	}
	/** Only bounded categories and original keys; never incoming aliases, comments or exception text. */
	public static final class Problem extends RuntimeException {
		private final int status;
		private final String code;
		private final Map<String, Object> details;
		public Problem(int status, String code, String category, String originalKey) {
			super(code.equals("MAPPING_MERGE_CONFLICT") ? "Incoming mapping conflicts with current logical state"
					: "Mapping import rejected before native commit");
			this.status = status; this.code = code;
			this.details = originalKey == null ? Map.of("category", category)
					: Map.of("category", category, "originalKey", boundedKey(originalKey));
		}
		public int status() { return status; }
		public String code() { return code; }
		public Map<String, Object> details() { return details; }
	}
	private static String boundedKey(String key) {
		String text = key.replaceAll("[\\p{Cntrl}]", "?");
		if (text.length() <= 256) return text;
		int end = Character.isHighSurrogate(text.charAt(254)) ? 254 : 255;
		return text.substring(0, end) + "…";
	}
	public static Problem unsupported(String category, String key) { return new Problem(422, "UNSUPPORTED_CAPABILITY", category, key); }
	public static Problem conflict(String category, String key) { return new Problem(409, "MAPPING_MERGE_CONFLICT", category, key); }
	public static Problem limit() { return new Problem(429, "RESOURCE_LIMIT", "IMPORT_BUDGET", null); }
}
