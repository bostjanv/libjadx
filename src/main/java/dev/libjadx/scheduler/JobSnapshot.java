package dev.libjadx.scheduler;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Immutable process-local polling state. resultJson is validated bounded JSON. */
public record JobSnapshot(UUID jobId, String type, State state, Instant createdAt,
		Instant startedAt, Instant completedAt, Instant deadlineAt,
		String sessionId, long logicalRevision, String sourceSnapshotId,
		JobProgress progress, String resultJson, JobSpec.Completeness completeness,
		JobError error, List<String> diagnostics, CancellationToken.Reason cancellationReason) {
	public JobSnapshot {
		diagnostics = List.copyOf(diagnostics);
	}

	public enum State { QUEUED, RUNNING, CANCELLING, SUCCEEDED, FAILED, CANCELLED }
	public record JobError(String code, String message, boolean retryable) { }
	public boolean terminal() {
		return state == State.SUCCEEDED || state == State.FAILED || state == State.CANCELLED;
	}
}
