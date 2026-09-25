package dev.libjadx.scheduler;

import java.time.Duration;
import java.util.Objects;

/** Trusted internal task. No HTTP route accepts a JobSpec or arbitrary executable code. */
public record JobSpec<S>(String type, OperationRequest operation,
		OperationCoordinator.Admission admission, String sourceSnapshotId,
		Duration deadline, SnapshotCapture<S> capture, JobTask<S> task) {
	public JobSpec {
		if (type == null || type.isBlank() || sourceSnapshotId == null || sourceSnapshotId.isBlank()) {
			throw new IllegalArgumentException("Job type and source snapshot are required");
		}
		Objects.requireNonNull(operation, "operation");
		Objects.requireNonNull(admission, "admission");
		Objects.requireNonNull(task, "task");
		if (deadline != null && (deadline.isNegative() || deadline.isZero())) {
			throw new IllegalArgumentException("Deadline must be positive");
		}
		if (operation.category() == OperationRequest.Category.TEMPORARY_ANALYSIS && capture == null) {
			throw new IllegalArgumentException("Temporary analysis requires immutable snapshot capture");
		}
	}

	@FunctionalInterface public interface SnapshotCapture<S> { S capture() throws Exception; }
	@FunctionalInterface public interface JobTask<S> { JobResult run(JobContext context, S snapshot) throws Exception; }
	public interface JobContext {
		CancellationToken cancellation();
		void progress(long completed, Long total, String stage);
		void diagnostic(String message);
	}
	public record JobResult(String json, Completeness completeness) {
		public JobResult {
			Objects.requireNonNull(json, "json");
			Objects.requireNonNull(completeness, "completeness");
		}
	}
	public enum Completeness { COMPLETE, PARTIAL }
}
