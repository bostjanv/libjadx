package dev.libjadx.scheduler;

/** Admission was closed by project shutdown. */
public final class ServiceShuttingDownException extends IllegalStateException {
	public ServiceShuttingDownException() {
		super("The service is shutting down");
	}
}
