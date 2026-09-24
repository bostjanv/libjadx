package dev.libjadx.app;

import java.nio.file.Path;
import java.util.List;

public record RuntimeStatus(
		String state,
		String stage,
		Progress progress,
		Path projectPath,
		List<Path> inputs,
		ApiError error) {

	public record Progress(long completed, long total, String unit) {
	}

	public record ApiError(String code, String message, String requestId) {
	}
}
