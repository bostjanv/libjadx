package dev.libjadx.scheduler;

/** Total is null when the amount of work cannot be known. */
public record JobProgress(long completed, Long total, String stage) {
	public JobProgress {
		if (completed < 0 || (total != null && (total < 0 || completed > total))) {
			throw new IllegalArgumentException("Invalid job progress");
		}
		if (stage == null || stage.isBlank()) throw new IllegalArgumentException("Progress stage is required");
	}
}
