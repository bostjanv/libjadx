package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import dev.libjadx.core.EffectiveAnalysisConfig;
import dev.libjadx.project.NativeProjectDocument;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.data.impl.JadxCodeRename;
import jadx.api.data.impl.JadxNodeRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsRebuildFailureTest {
	@TempDir Path dir;

	@Test
	void failedSettingsRebuildKeepsPrimaryAndUnsavedEdits() throws Exception {
		NativeProjectDocument nativeProject = fixture();
		AtomicInteger creations = new AtomicInteger();
		AtomicInteger closes = new AtomicInteger();
		ProjectRuntime runtime = new ProjectRuntime(nativeProject.getProjectPath(), nativeProject.getInputFiles(),
				args -> new CountingEngine(args, creations.incrementAndGet() == 2, closes));
		try {
			runtime.initializeAsync(nativeProject).get(20, TimeUnit.SECONDS);
			var edit = nativeProject.getCodeData();
			edit.setRenames(List.of(new JadxCodeRename(JadxNodeRef.forCls("probe.Sample"), "RetainedAlias")));
			runtime.replaceCodeData(edit, 0);
			JadxDecompiler primary = runtime.decompiler();
			Path other = dir.resolve("other.tiny");
			Files.writeString(other, "tiny\t2\t0\toriginal\tmapped\nc\tprobe/Second\tprobe/OtherSecond\n");
			String session = runtime.projectSnapshot().revisions().sessionId();
			assertThrows(IllegalStateException.class, () -> runtime.updateMappingsPath(other, session, 1));
			assertEquals("READY", runtime.status().state());
			assertSame(primary, runtime.decompiler());
			assertEquals(1, runtime.projectSnapshot().revisions().logicalRevision());
			assertTrue(runtime.projectSnapshot().dirty());
			assertEquals(dir.resolve("sample.tiny"), runtime.settingsSnapshot().mappingsPath());
			assertTrue(runtime.decompiler().getClasses().stream()
					.anyMatch(cls -> cls.getFullName().equals("probe.RetainedAlias")));
			assertEquals(1, closes.get(), "failed replacement is closed");
		} finally {
			runtime.close();
		}
		assertEquals(2, closes.get());
	}

	@Test
	void temporaryFailureClosesItsEngineWithoutChangingPrimary() throws Exception {
		NativeProjectDocument nativeProject = fixture();
		AtomicInteger closes = new AtomicInteger();
		ProjectRuntime runtime = new ProjectRuntime(nativeProject.getProjectPath(), nativeProject.getInputFiles(),
				args -> new CountingEngine(args, false, closes));
		try {
			runtime.initializeAsync(nativeProject).get(20, TimeUnit.SECONDS);
			JadxDecompiler primary = runtime.decompiler();
			assertThrows(IllegalStateException.class, () -> runtime.withTemporaryAnalysis(
					new EffectiveAnalysisConfig("SIMPLE"), jadx -> { throw new IllegalStateException("probe failure"); }));
			assertEquals(1, closes.get());
			assertSame(primary, runtime.decompiler());
			assertEquals(0, runtime.projectSnapshot().revisions().logicalRevision());
		} finally {
			runtime.close();
		}
		assertEquals(2, closes.get());
	}

	private NativeProjectDocument fixture() throws Exception {
		Path source = Path.of("tests/fixtures/native-project").toAbsolutePath();
		for (String name : List.of("sample.jar.jadx", "sample.jar", "second.jar", "sample.tiny")) {
			Files.copy(source.resolve(name), dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
		}
		return NativeProjectDocument.open(dir.resolve("sample.jar.jadx"));
	}

	private static final class CountingEngine implements ProjectRuntime.ProjectEngine {
		private final JadxDecompiler jadx;
		private final boolean failLoad;
		private final AtomicInteger closes;

		CountingEngine(JadxArgs args, boolean failLoad, AtomicInteger closes) {
			this.jadx = new JadxDecompiler(args);
			this.failLoad = failLoad;
			this.closes = closes;
		}

		@Override public void load() {
			if (failLoad) throw new IllegalStateException("controlled rebuild failure");
			jadx.load();
		}

		@Override public JadxDecompiler decompiler() { return jadx; }

		@Override public void close() {
			closes.incrementAndGet();
			jadx.close();
		}
	}
}
