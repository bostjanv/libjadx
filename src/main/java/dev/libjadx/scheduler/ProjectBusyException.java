package dev.libjadx.scheduler;

/** Fail-fast rejection of incompatible in-flight work. */
public final class ProjectBusyException extends IllegalStateException {
	public ProjectBusyException() {
		super("The fixed project is busy with an incompatible operation; retry after it completes");
	}
}
