package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import dev.libjadx.core.edits.EditDtos;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.jadxadapter.JadxNativeEditAdapter;
import dev.libjadx.scheduler.ProjectBusyException;
import jadx.api.JadxDecompiler;
import jadx.api.data.impl.JadxCodeData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditBatchServiceTest {
	@TempDir Path dir;

	@Test
	void injectedStagingFailureCommitsOnlySafePrefix() throws Exception {
		Path jar = SymbolFixtureSupport.compileFixture(dir);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
		try {
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			SymbolRef owner = SymbolRef.classRef("Lprobe/SymbolFixture;");
			SymbolRef method = new SymbolRef(SymbolRef.Kind.METHOD, "Lprobe/SymbolFixture;", null, "mix", "(I)I");
			SymbolRef field = new SymbolRef(SymbolRef.Kind.FIELD, "Lprobe/SymbolFixture;", null, "count", "I");
			EditBatchService service = new EditBatchService(runtime, index -> {
				if (index == 1) throw new IllegalStateException("injected after first staged edit");
			});
			var result = service.apply(new EditDtos.Request(null, null, List.of(
					new EditDtos.Operation(EditDtos.Kind.RENAME, owner, "PrefixOnly", null, null),
					new EditDtos.Operation(EditDtos.Kind.RENAME, method, "NeverApplied", null, null),
					new EditDtos.Operation(EditDtos.Kind.RENAME, field, "AlsoSkipped", null, null))));
			assertEquals("PARTIAL", result.outcome());
			assertEquals(List.of("APPLIED", "FAILED", "SKIPPED"), result.items().stream().map(EditDtos.ItemResult::status).toList());
			assertEquals(1, result.logicalRevisionAfter());
			assertTrue(result.dirty());
			String pending = runtime.pendingEdits().toString();
			assertTrue(pending.contains("PrefixOnly"));
			assertFalse(pending.contains("NeverApplied"));
			assertFalse(pending.contains("AlsoSkipped"));
			assertTrue(runtime.decompiler().getClassesWithInners().stream()
					.anyMatch(cls -> cls.getFullName().equals("probe.PrefixOnly")));
		} finally { runtime.close(); }
	}

	@Test
	void collisionsAndDuplicateNativeKeysRejectWholeBatch() throws Exception {
		Path jar = SymbolFixtureSupport.compileFixture(dir);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
		try {
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			EditBatchService service = new EditBatchService(runtime);
			SymbolRef cls = SymbolRef.classRef("Lprobe/SymbolFixture;");
			SymbolRef count = new SymbolRef(SymbolRef.Kind.FIELD, "Lprobe/SymbolFixture;", null, "count", "I");
			SymbolRef names = new SymbolRef(SymbolRef.Kind.FIELD, "Lprobe/SymbolFixture;", null, "names", "[Ljava/lang/String;");
			var collision = assertThrows(EditBatchService.Rejected.class, () -> service.apply(new EditDtos.Request(null, null, List.of(
					new EditDtos.Operation(EditDtos.Kind.RENAME, cls, "WouldNotApply", null, null),
					new EditDtos.Operation(EditDtos.Kind.RENAME, count, "names", null, null)))));
			assertEquals(400, collision.status());
			assertEquals(1, collision.itemErrors().getFirst().index());
			var duplicate = assertThrows(EditBatchService.Rejected.class, () -> service.apply(new EditDtos.Request(null, null, List.of(
					new EditDtos.Operation(EditDtos.Kind.RENAME, count, "firstName", null, null),
					new EditDtos.Operation(EditDtos.Kind.RENAME, count, "secondName", null, null)))));
			assertEquals(400, duplicate.status());
			for (String unsafe : List.of("first\nsecond", "*/", "\u2028")) {
				var rejected = assertThrows(EditBatchService.Rejected.class, () -> service.apply(new EditDtos.Request(null, null, List.of(
						new EditDtos.Operation(EditDtos.Kind.SET_COMMENT, cls, null, unsafe, "LINE")))));
				assertEquals(400, rejected.status());
			}
			assertEquals(0, runtime.projectSnapshot().revisions().logicalRevision());
			assertFalse(runtime.projectSnapshot().dirty());
			assertFalse(runtime.pendingEdits().toString().contains("WouldNotApply"));
			var swap = service.apply(new EditDtos.Request(null, null, List.of(
					new EditDtos.Operation(EditDtos.Kind.RENAME, count, "names", null, null),
					new EditDtos.Operation(EditDtos.Kind.RENAME, names, "count", null, null))));
			assertEquals("APPLIED", swap.outcome());
			assertEquals(1, swap.logicalRevisionAfter());
		} finally { runtime.close(); }
	}

	@Test
	void returnTypeOnlyOverloadsHaveDistinctNativeKeys() throws Exception {
		Path jar = SymbolFixtureSupport.returnTypeClashJar(dir);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
		try {
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			var targets = JadxNativeEditAdapter.visibleTargets(runtime.decompiler());
			var values = targets.stream().filter(target -> target.ref().originalName() != null
					&& target.ref().originalName().equals("value")
					&& target.ref().originalClassDescriptor().equals("Lprobe/ReturnClash;")).toList();
			assertEquals(2, values.size());
			assertNotEquals(values.get(0).nativeRef().getShortId(), values.get(1).nativeRef().getShortId());
			assertTrue(values.stream().anyMatch(target -> target.nativeRef().getShortId().equals("value()I")));
			assertTrue(values.stream().anyMatch(target -> target.nativeRef().getShortId().equals("value()Ljava/lang/String;")));
			String untouchedBefore = values.stream().filter(target -> target.nativeRef().getShortId().equals("value()Ljava/lang/String;"))
					.findFirst().orElseThrow().displayName();
			var result = new EditBatchService(runtime).apply(new EditDtos.Request(null, null, List.of(
					new EditDtos.Operation(EditDtos.Kind.RENAME,
							new SymbolRef(SymbolRef.Kind.METHOD, "Lprobe/ReturnClash;", null, "value", "()I"),
							"intValueAlias", null, null))));
			assertEquals("APPLIED", result.outcome());
			assertTrue(runtime.pendingEdits().toString().contains("value()I"));
			assertFalse(runtime.pendingEdits().toString().contains("value()Ljava/lang/String;"));
			String untouchedAfter = JadxNativeEditAdapter.visibleTargets(runtime.decompiler()).stream()
					.filter(target -> target.nativeRef().getShortId() != null
							&& target.nativeRef().getShortId().equals("value()Ljava/lang/String;"))
					.findFirst().orElseThrow().displayName();
			assertEquals(untouchedBefore, untouchedAfter);
		} finally { runtime.close(); }
	}

	@Test
	void editFailsFastWhileNativeSaveHoldsRepositoryMonitor() throws Exception {
		Path jar = SymbolFixtureSupport.compileFixture(dir);
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), args -> new ProjectRuntime.ProjectEngine() {
			private final JadxDecompiler jadx = new JadxDecompiler(args);
			@Override public void load() { jadx.load(); }
			@Override public JadxDecompiler decompiler() { return jadx; }
			@Override public void close() { jadx.close(); }
		}, (repository, target, session, revision) -> {
			synchronized (repository) {
				entered.countDown();
				try {
					if (!release.await(10, TimeUnit.SECONDS)) throw new IOException("Timed out holding native save");
				} catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					throw new IOException(interrupted);
				}
				return repository.save(target, session, revision);
			}
		});
		try {
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			CompletableFuture<Void> save = CompletableFuture.runAsync(() -> {
				try { runtime.saveProject(dir.resolve("busy.jadx"), null); }
				catch (IOException failure) { throw new RuntimeException(failure); }
			});
			try {
				assertTrue(entered.await(5, TimeUnit.SECONDS));
				CompletableFuture<Class<?>> edit = CompletableFuture.supplyAsync(() -> {
					try {
						new EditBatchService(runtime).apply(new EditDtos.Request(null, null, List.of(
								new EditDtos.Operation(EditDtos.Kind.RENAME,
										SymbolRef.classRef("Lprobe/SymbolFixture;"), "BusyAlias", null, null))));
						return null;
					} catch (RuntimeException rejected) { return rejected.getClass(); }
				});
				assertEquals(ProjectBusyException.class, edit.get(2, TimeUnit.SECONDS));
			} finally { release.countDown(); save.get(10, TimeUnit.SECONDS); }
			assertEquals(0, runtime.projectSnapshot().revisions().logicalRevision());
		} finally { release.countDown(); runtime.close(); }
	}

	@Test
	void reloadFailureAfterNativeReplacementFailsRuntimeWithoutSuccessResult() throws Exception {
		Path jar = SymbolFixtureSupport.compileFixture(dir);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), args -> new ProjectRuntime.ProjectEngine() {
			private final JadxDecompiler jadx = new JadxDecompiler(args);
			@Override public void load() { jadx.load(); }
			@Override public JadxDecompiler decompiler() { return jadx; }
			@Override public void reloadCodeData(JadxCodeData codeData) { throw new IllegalStateException("injected reload failure"); }
			@Override public void close() { jadx.close(); }
		});
		try {
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			assertThrows(IllegalStateException.class, () -> new EditBatchService(runtime).apply(new EditDtos.Request(null, null,
					List.of(new EditDtos.Operation(EditDtos.Kind.RENAME, SymbolRef.classRef("Lprobe/SymbolFixture;"),
							"PendingAfterFailure", null, null)))));
			assertEquals("FAILED", runtime.status().state());
			assertThrows(ProjectRuntime.ProjectNotReadyException.class, runtime::projectSnapshot);
		} finally { runtime.close(); }
	}
}
