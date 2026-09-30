package dev.libjadx.app;

import java.io.IOException;
import java.util.Properties;

/** Deterministic Gradle-generated product identity, independent of API version. */
public final class BuildInfo {
	private BuildInfo() { }
	public static final String VERSION = loadVersion();
	private static String loadVersion() {
		try (var input = BuildInfo.class.getResourceAsStream("/libjadx-build.properties")) {
			if (input == null) throw new IllegalStateException("Missing product build information");
			var properties = new Properties();
			properties.load(input);
			String version = properties.getProperty("version");
			if (version == null || version.isBlank()) throw new IllegalStateException("Missing product version");
			return version;
		} catch (IOException failure) { throw new ExceptionInInitializerError(failure); }
	}
}
