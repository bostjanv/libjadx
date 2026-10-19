package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import dev.libjadx.core.EffectiveAnalysisConfig;
import dev.libjadx.project.NativeProjectDocument;
import dev.libjadx.project.NativeProjectRepository;
import dev.libjadx.scheduler.ProjectBusyException;
import jadx.api.data.impl.JadxCodeComment;
import jadx.api.data.impl.JadxCodeRename;
import jadx.api.data.impl.JadxNodeRef;
import jadx.api.JadxDecompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TemporaryAnalysisTest {
	@TempDir Path dir;

	@Test
	void rawInputAndUnsavedMappingAttachmentUseAcceptedBaselinesUntilExplicitReload() throws Exception {
		Path fixture = Path.of("tests/fixtures/native-project").toAbsolutePath();
		Path input = Files.copy(fixture.resolve("second.jar"), dir.resolve("second.jar"));
		Path mapping = Files.copy(fixture.resolve("sample.tiny"), dir.resolve("attached.tiny"));
		AtomicInteger creations = new AtomicInteger();
		try (var runtime = new ProjectRuntime(null, List.of(input), args -> {
			creations.incrementAndGet();
			return new ProjectRuntime.JadxProjectEngine(args);
		})) {
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			byte[] accepted = Files.readAllBytes(input);
			Files.writeString(input, "\n", java.nio.file.StandardOpenOption.APPEND);
			assertThrows(NativeProjectRepository.ExternalModificationException.class,
					() -> runtime.withTemporaryAnalysis(new EffectiveAnalysisConfig("SIMPLE"), jadx -> "unexpected"));
			assertEquals(1, creations.get());
			Files.write(input, accepted);
			runtime.saveProject(dir.resolve("saved.jadx"), null);
			var revision = runtime.projectSnapshot().revisions();
			runtime.updateMappingsPath(mapping, revision.sessionId(), revision.logicalRevision());
			String renamed = runtime.withTemporaryAnalysis(new EffectiveAnalysisConfig("RESTRUCTURE"), jadx ->
					jadx.searchJavaClassByOrigFullName("probe.Second").getFullName()).value();
			assertEquals("probe.RenamedSecond", renamed);
			var before = runtime.projectSnapshot();
			int engines = creations.get();
			Files.writeString(mapping, Files.readString(mapping).replace("RenamedSecond", "ChangedSecond"));
			assertThrows(NativeProjectRepository.ExternalModificationException.class,
					() -> runtime.withTemporaryAnalysis(new EffectiveAnalysisConfig("RESTRUCTURE"), jadx -> "unexpected"));
			assertEquals(engines, creations.get());
			assertEquals(before, runtime.projectSnapshot());
			Files.copy(fixture.resolve("sample.tiny"), mapping, StandardCopyOption.REPLACE_EXISTING);
			runtime.saveProject(dir.resolve("saved.jadx"), null);
			Files.writeString(mapping, Files.readString(mapping).replace("RenamedSecond", "ChangedSecond"));
			revision = runtime.projectSnapshot().revisions();
			runtime.reloadProject(false, revision.sessionId(), revision.logicalRevision());
			assertEquals("probe.ChangedSecond", runtime.withTemporaryAnalysis(new EffectiveAnalysisConfig("RESTRUCTURE"),
					jadx -> jadx.searchJavaClassByOrigFullName("probe.Second").getFullName()).value());
		}
	}

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
			assertThrows(ProjectBusyException.class, () -> runtime.withTemporaryAnalysis(
					new EffectiveAnalysisConfig("SIMPLE"), jadx -> "second"));
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

	@Test
	void shutdownDoesNotWaitForAnActiveTemporaryOperationWhileHoldingItsAdmissionLock() throws Exception {
		Path input = Files.createFile(dir.resolve("input.bin"));
		CountDownLatch temporaryLoadEntered = new CountDownLatch(1);
		CountDownLatch releaseTemporaryLoad = new CountDownLatch(1);
		AtomicInteger creations = new AtomicInteger();
		AtomicInteger closes = new AtomicInteger();
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(input), args -> {
			int sequence = creations.incrementAndGet();
			return new ProjectRuntime.ProjectEngine() {
				private final JadxDecompiler jadx = new JadxDecompiler(args);
				@Override public void load() throws Exception {
					if (sequence == 2) {
						temporaryLoadEntered.countDown();
						releaseTemporaryLoad.await();
					}
				}
				@Override public JadxDecompiler decompiler() { return jadx; }
				@Override public void close() {
					closes.incrementAndGet();
					jadx.close();
				}
			};
		});
		CompletableFuture<ProjectRuntime.TemporaryResult<String>> temporary = null;
		try {
			runtime.initializeAsync(null).get(5, TimeUnit.SECONDS);
			temporary = CompletableFuture.supplyAsync(() -> {
				try {
					return runtime.withTemporaryAnalysis(new EffectiveAnalysisConfig("SIMPLE"), jadx -> "complete");
				} catch (Exception failure) {
					throw new RuntimeException(failure);
				}
			});
			assertTrue(temporaryLoadEntered.await(5, TimeUnit.SECONDS));
			CompletableFuture.runAsync(runtime::close).get(2, TimeUnit.SECONDS);
			assertEquals("SHUTTING_DOWN", runtime.status().state());
			assertFalse(runtime.awaitStopped(50, TimeUnit.MILLISECONDS));
			assertThrows(IllegalStateException.class, () -> runtime.withTemporaryAnalysis(
					new EffectiveAnalysisConfig("SIMPLE"), jadx -> "late"));
			releaseTemporaryLoad.countDown();
			assertEquals("complete", temporary.get(5, TimeUnit.SECONDS).value());
			assertTrue(runtime.awaitStopped(1, TimeUnit.SECONDS));
			assertEquals(2, closes.get());
		} finally {
			releaseTemporaryLoad.countDown();
			if (temporary != null) temporary.get(5, TimeUnit.SECONDS);
			runtime.close();
		}
	}
}
