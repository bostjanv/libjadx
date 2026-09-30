package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ReleaseIdentityTest {
	@Test void cliGradleAndDocumentedCandidateShareBuildIdentity() throws Exception {
		assertTrue(Files.readString(Path.of("build.gradle.kts")).contains("version = \"" + BuildInfo.VERSION + "\""));
		assertTrue(Files.readString(Path.of("docs/phase-6-release-qualification.md"))
				.contains("Product candidate: `" + BuildInfo.VERSION + "`"));
		var process = new ProcessBuilder(System.getProperty("libjadx.distributionScript"), "--version")
				.redirectErrorStream(true).start();
		assertTrue(process.waitFor(10, TimeUnit.SECONDS));
		assertEquals(0, process.exitValue());
		assertEquals("LibJadx " + BuildInfo.VERSION, new String(process.getInputStream().readAllBytes()).trim());
	}
}
