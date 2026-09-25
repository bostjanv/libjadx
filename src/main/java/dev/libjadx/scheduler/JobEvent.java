package dev.libjadx.scheduler;

import java.time.Instant;
import java.util.UUID;

/** A retained, serialized SSE event. */
public record JobEvent(UUID jobId, long sequence, String type, Instant at,
		JobSnapshot.State state, String dataJson) {
	public boolean terminal() {
		return state == JobSnapshot.State.SUCCEEDED || state == JobSnapshot.State.FAILED
				|| state == JobSnapshot.State.CANCELLED;
	}
}
