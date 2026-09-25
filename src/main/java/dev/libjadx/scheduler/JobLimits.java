package dev.libjadx.scheduler;

import java.time.Duration;

/** Finite, process-local scheduler and SSE budgets. */
public record JobLimits(int maxQueued, int maxRunning, int maxRetained,
		int maxResultBytes, int maxTotalResultBytes, int maxDiagnostics, int maxDiagnosticBytes,
		int maxEventsPerJob, int maxEventBytes, int maxSubscribersPerJob,
		int maxGlobalSubscribers, int maxFramesPerSubscriber,
		Duration terminalTtl, Duration heartbeatInterval) {
	public JobLimits {
		if (maxQueued < 1 || maxRunning < 1 || maxRetained < 1 || maxResultBytes < 1
				|| maxTotalResultBytes < maxResultBytes
				|| maxDiagnostics < 1 || maxDiagnosticBytes < 1 || maxEventsPerJob < 1
				|| maxEventBytes < 1024 || maxSubscribersPerJob < 1 || maxGlobalSubscribers < 1
				|| maxFramesPerSubscriber < 1 || terminalTtl == null || terminalTtl.isNegative()
				|| terminalTtl.isZero() || heartbeatInterval == null || heartbeatInterval.isNegative()
				|| heartbeatInterval.isZero()) {
			throw new IllegalArgumentException("Job limits must be finite and positive");
		}
	}

	public static JobLimits defaults() {
		return new JobLimits(32, 2, 256, 256 * 1024, 16 * 1024 * 1024, 16, 4096,
				128, 8192, 8, 32, 32, Duration.ofMinutes(10), Duration.ofSeconds(15));
	}
}
