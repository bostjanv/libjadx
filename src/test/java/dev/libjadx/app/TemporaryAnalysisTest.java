package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import dev.libjadx.core.EffectiveAnalysisConfig;
import dev.libjadx.project.NativeProjectDocument;
import jadx.api.data.impl.JadxCodeComment;
import jadx.api.data.impl.JadxCodeRename;
import jadx.api.data.impl.JadxNodeRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemporaryAnalysisTest {
	@TempDir Path dir;

	@Test
	void overrideUsesUnsavedDeepSnapshotAndDoesNotChangePrimary() throws Exception {
		Path source = Path.of("tests/fixtures/native-project").toAbsolutePath();
		for (String name : List.of("sample.jar.jadx", "sample.jar", "second.jar", "sample.tiny")) {
			Files.copy(source.resolve(name), dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
		}
		Path path = dir.resolve("sample.jar.jadx");
		NativeProjectDocument project = NativeProjectDocument.open(path);
		ProjectRuntime runtime = new ProjectRuntime(path, project.getInputFiles(), List.of(dir));
		try {
			runtime.initializeAsync(project).get(20, TimeUnit.SECONDS);
			var data = project.getCodeData();
			data.setRenames(List.of(new JadxCodeRename(JadxNodeRef.forCls("probe.Sample"), "UnsavedAlias")));
			data.setComments(List.of(new JadxCodeComment(JadxNodeRef.forCls("probe.Sample"), "unsaved comment")));
			runtime.replaceCodeData(data, 0);
			String primary = runtime.decompiler().getClasses().stream()
					.filter(cls -> cls.getFullName().equals("probe.UnsavedAlias")).findFirst().orElseThrow().getCode();
			assertTrue(primary.contains("unsaved comment"));
			long before = runtime.projectSnapshot().revisions().logicalRevision();

			CountDownLatch admitted = new CountDownLatch(1);
			CountDownLatch proceed = new CountDownLatch(1);
			CompletableFuture<ProjectRuntime.TemporaryResult<String>> pending = CompletableFuture.supplyAsync(() -> {
				try {
					return runtime.withTemporaryAnalysis(new EffectiveAnalysisConfig("SIMPLE"), jadx -> {
						admitted.countDown();
						try { proceed.await(); } catch (InterruptedException e) { throw new RuntimeException(e); }
						return jadx.getClasses().stream().filter(cls -> cls.getFullName().equals("probe.UnsavedAlias"))
								.findFirst().orElseThrow().getCode();
					});
				} catch (Exception failure) { throw new RuntimeException(failure); }
			});
			assertTrue(admitted.await(20, TimeUnit.SECONDS));
			var changed = runtime.projectSnapshot();
			var latest = NativeProjectDocument.open(path).getCodeData();
			latest.setRenames(List.of(new JadxCodeRename(JadxNodeRef.forCls("probe.Sample"), "NewAlias")));
			runtime.replaceCodeData(latest, changed.revisions().logicalRevision());
			proceed.countDown();
			var result = pending.get(20, TimeUnit.SECONDS);
			assertEquals(before, result.revisions().logicalRevision());
			assertEquals("SIMPLE", result.settings().decompilationMode());
			assertTrue(result.settingsFingerprint().startsWith("sha256:"));
			assertTrue(result.sourceSnapshotId().startsWith("sha256:"));
			assertTrue(result.value().contains("UnsavedAlias"));
			assertTrue(result.value().contains("unsaved comment"));
			assertEquals(before + 1, runtime.projectSnapshot().revisions().logicalRevision());
			assertEquals("AUTO", runtime.decompiler().getArgs().getDecompilationMode().name());
			var newer = runtime.withTemporaryAnalysis(new EffectiveAnalysisConfig("SIMPLE"), jadx ->
					jadx.getClasses().stream().filter(cls -> cls.getFullName().equals("probe.NewAlias"))
							.findFirst().orElseThrow().getCode());
			assertTrue(!result.sourceSnapshotId().equals(newer.sourceSnapshotId()));
			assertTrue(newer.value().contains("NewAlias"));
			assertEquals(before + 1, runtime.projectSnapshot().revisions().logicalRevision());
		} finally {
			runtime.close();
		}
	}
}
