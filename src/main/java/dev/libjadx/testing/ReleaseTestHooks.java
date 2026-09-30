package dev.libjadx.testing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.locks.LockSupport;

/** Explicit process-only release instrumentation. No HTTP or native state controls it.
 * Unset in production. Files in the configured test directory control bounded gates.
 */
public final class ReleaseTestHooks {
	private static final Path ROOT = root();
	private ReleaseTestHooks() { }
	private static Path root() {
		String value = System.getProperty("libjadx.releaseTestDir");
		return value == null || value.isBlank() ? null : Path.of(value).toAbsolutePath();
	}
	public static boolean enabled(String point) {
		return ROOT != null && Files.exists(ROOT.resolve(point));
	}
	public static void fail(String point) {
		if (enabled(point + ".fail")) throw new IllegalStateException("Owned release-test fault: " + point);
	}
	/** Intentionally noninterruptible gate: cancellation must stay CANCELLING until released. */
	public static void gate(String point) {
		if (!enabled(point + ".hold")) return;
		try { Files.writeString(ROOT.resolve(point + ".entered"), point); }
		catch (IOException failure) { throw new IllegalStateException("Cannot signal release gate", failure); }
		long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
		boolean interrupted = false;
		try {
			while (enabled(point + ".hold")) {
				interrupted |= Thread.interrupted();
				if (System.nanoTime() >= deadline) throw new IllegalStateException("Release gate exceeded 90 seconds: " + point);
				LockSupport.parkNanos(Duration.ofMillis(10).toNanos());
			}
		} finally { if (interrupted) Thread.currentThread().interrupt(); }
	}
}
