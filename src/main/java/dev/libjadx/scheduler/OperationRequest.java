package dev.libjadx.scheduler;

import java.util.Objects;

/** Resource classification for one admitted project operation. */
public record OperationRequest(Category category, String key) {
	public enum Category { PROJECT_EXCLUSIVE, CLASS_READ, INDEX_READ, QUERY_READ, TEMPORARY_ANALYSIS }

	public OperationRequest {
		Objects.requireNonNull(category, "category");
		if (key == null || key.isBlank()) throw new IllegalArgumentException("Operation key is required");
	}

	public static OperationRequest projectExclusive(String reason) {
		return new OperationRequest(Category.PROJECT_EXCLUSIVE, reason);
	}

	public static OperationRequest classRead(String classKey) {
		return new OperationRequest(Category.CLASS_READ, classKey);
	}

	public static OperationRequest indexRead(String snapshot) {
		return new OperationRequest(Category.INDEX_READ, snapshot);
	}

	public static OperationRequest queryRead(String snapshot) {
		return new OperationRequest(Category.QUERY_READ, snapshot);
	}

	public static OperationRequest temporaryAnalysis(String snapshot) {
		return new OperationRequest(Category.TEMPORARY_ANALYSIS, snapshot);
	}
}
