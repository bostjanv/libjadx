package dev.libjadx.http;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.libjadx.scheduler.CancellationToken;
import dev.libjadx.scheduler.JobProgress;
import dev.libjadx.scheduler.JobSnapshot;
import dev.libjadx.scheduler.JobSpec;

/** HTTP mapping and future 202/Location helper for typed job-producing routes. */
public final class JobHttpResponses {
	private JobHttpResponses() { }

	public record JobResponse(UUID jobId, String type, JobSnapshot.State state, String createdAt,
			String startedAt, String completedAt, String deadlineAt,
			String sessionId, long logicalRevision, String sourceSnapshotId,
			JobProgress progress, JsonNode result, JobSpec.Completeness completeness,
			StatusServlet.ErrorBody error, List<String> diagnostics,
			CancellationToken.Reason cancellationReason) { }

	public record AcceptedJob(int status, String location, JobResponse body) { }

	public static JobResponse map(JobSnapshot snapshot, ObjectMapper json) {
		JsonNode result = null;
		if (snapshot.resultJson() != null) {
			try { result = json.readTree(snapshot.resultJson()); }
			catch (Exception impossible) { throw new IllegalStateException("Validated job result is unreadable", impossible); }
		}
		StatusServlet.ErrorBody error = snapshot.error() == null ? null
				: new StatusServlet.ErrorBody(snapshot.error().code(), snapshot.error().message(),
						snapshot.error().retryable(), null, null);
		return new JobResponse(snapshot.jobId(), snapshot.type(), snapshot.state(), dateTime(snapshot.createdAt()),
				dateTime(snapshot.startedAt()), dateTime(snapshot.completedAt()), dateTime(snapshot.deadlineAt()),
				snapshot.sessionId(), snapshot.logicalRevision(), snapshot.sourceSnapshotId(),
				snapshot.progress(), result, snapshot.completeness(), error,
				snapshot.diagnostics(), snapshot.cancellationReason());
	}

	private static String dateTime(java.time.Instant instant) {
		return instant == null ? null : instant.toString();
	}

	public static AcceptedJob accepted(JobSnapshot snapshot, ObjectMapper json) {
		return new AcceptedJob(202, "/api/v1/jobs/" + snapshot.jobId(), map(snapshot, json));
	}
}
