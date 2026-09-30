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
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.libjadx.core.edits.EditDtos;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.jadxadapter.JadxNativeEditAdapter;
import dev.libjadx.jadxadapter.JadxSymbolAdapter;
import dev.libjadx.scheduler.ProjectBusyException;
import jadx.api.JadxDecompiler;
import jadx.api.data.impl.JadxCodeData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class EditBatchServiceTest {
	@TempDir Path dir;

	@ParameterizedTest
	@EnumSource(SymbolRef.Kind.class)
	void effectiveEditLeavesUnrelatedClassesUnprocessed(SymbolRef.Kind kind) throws Exception {
		assertColdBatch(kind, "APPLIED");
	}

	@ParameterizedTest
	@EnumSource(SymbolRef.Kind.class)
	void rejectedBatchLeavesUnrelatedClassesUnprocessed(SymbolRef.Kind kind) throws Exception {
		assertColdBatch(kind, "REJECTED");
	}

	@ParameterizedTest
	@EnumSource(SymbolRef.Kind.class)
	void noChangeBatchLeavesUnrelatedClassesUnprocessed(SymbolRef.Kind kind) throws Exception {
		assertColdBatch(kind, "NO_CHANGE");
	}

	private void assertColdBatch(SymbolRef.Kind kind, String outcome) throws Exception {
		Path jar = SymbolFixtureSupport.compileEditFixture(dir);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
		try {
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			assertUnrelatedUnprocessed(runtime);
			SymbolRef ref = switch (kind) {
				case CLASS -> SymbolRef.classRef("Lprobe/EditOwner;");
				case METHOD -> new SymbolRef(kind, "Lprobe/EditOwner;", null, "work", "()I");
				case FIELD -> new SymbolRef(kind, "Lprobe/EditOwner;", null, "count", "I");
			};
			String current = kind == SymbolRef.Kind.CLASS ? "EditOwner" : ref.originalName();
			var first = new EditDtos.Operation(EditDtos.Kind.RENAME, ref,
					outcome.equals("NO_CHANGE") ? current : "EditedOwner", null, null);
			// Check before commit as well: publication unloads all code caches and could mask eager processing.
			EditBatchService service = new EditBatchService(runtime, index -> assertUnrelatedUnprocessed(runtime));
			if (outcome.equals("REJECTED")) {
				var second = new EditDtos.Operation(EditDtos.Kind.RENAME,
						new SymbolRef(SymbolRef.Kind.FIELD, "Lprobe/EditOwner;", null, "otherCount", "I"),
						"bad-name", null, null);
				var rejected = assertThrows(EditBatchService.Rejected.class, () -> service.apply(
						new EditDtos.Request(null, null, List.of(first, second))));
				assertEquals(1, rejected.itemErrors().getFirst().index());
				assertEquals("INVALID_REQUEST", rejected.code());
			} else {
				assertEquals(outcome, service.apply(new EditDtos.Request(null, null, List.of(first))).outcome());
			}
			assertUnrelatedUnprocessed(runtime);
			assertEquals(outcome.equals("APPLIED") ? 1 : 0, runtime.projectSnapshot().revisions().logicalRevision());
			assertEquals(outcome.equals("APPLIED"), runtime.projectSnapshot().dirty());
			if (kind == SymbolRef.Kind.CLASS && !outcome.equals("REJECTED")) {
				assertFalse(JadxSymbolAdapter.visibleClass(runtime.decompiler(), "Lprobe/EditOwner;", 0)
						.getClassNode().getState().isProcessComplete());
			}
		} finally { runtime.close(); }
	}

	private static void assertUnrelatedUnprocessed(ProjectRuntime runtime) {
		for (String descriptor : List.of("Lprobe/UnrelatedA;", "Lprobe/UnrelatedB;")) {
			var cls = JadxSymbolAdapter.visibleClass(runtime.decompiler(), descriptor, 0);
			assertTrue(cls.loadingWouldRequireDecompilation(), descriptor);
			assertFalse(cls.getClassNode().getState().isProcessComplete(), descriptor);
		}
	}

	@ParameterizedTest
	@EnumSource(SymbolRef.Kind.class)
	void declarationCommentLeavesUnrelatedClassesUnprocessed(SymbolRef.Kind kind) throws Exception {
		Path jar = SymbolFixtureSupport.compileEditFixture(dir);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
		try {
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			SymbolRef ref = switch (kind) {
				case CLASS -> SymbolRef.classRef("Lprobe/EditOwner;");
				case METHOD -> new SymbolRef(kind, "Lprobe/EditOwner;", null, "work", "()I");
				case FIELD -> new SymbolRef(kind, "Lprobe/EditOwner;", null, "count", "I");
			};
			EditBatchService service = new EditBatchService(runtime, index -> assertUnrelatedUnprocessed(runtime));
			var result = service.apply(new EditDtos.Request(null, null, List.of(
					new EditDtos.Operation(EditDtos.Kind.SET_COMMENT, ref, null, "reviewed declaration", "LINE"))));
			assertEquals("APPLIED", result.outcome());
			assertUnrelatedUnprocessed(runtime);
		} finally { runtime.close(); }
	}

	@Test
	void scopedCatalogStillRejectsClassAndSameOwnerMethodCollisions() throws Exception {
		Path jar = SymbolFixtureSupport.compileEditFixture(dir);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), List.of(dir));
		try {
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			EditBatchService service = new EditBatchService(runtime);
			SymbolRef owner = SymbolRef.classRef("Lprobe/EditOwner;");
			var classCollision = assertThrows(EditBatchService.Rejected.class, () -> service.apply(new EditDtos.Request(null, null,
					List.of(new EditDtos.Operation(EditDtos.Kind.RENAME, owner, "UnrelatedA", null, null)))));
			assertEquals("INVALID_REQUEST", classCollision.code());
			assertUnrelatedUnprocessed(runtime);
			var methodCollision = assertThrows(EditBatchService.Rejected.class, () -> service.apply(new EditDtos.Request(null, null,
					List.of(new EditDtos.Operation(EditDtos.Kind.RENAME,
							new SymbolRef(SymbolRef.Kind.METHOD, "Lprobe/EditOwner;", null, "work", "()I"), "otherWork", null, null)))));
			assertEquals("INVALID_REQUEST", methodCollision.code());
			assertUnrelatedUnprocessed(runtime);
			assertEquals(0, runtime.projectSnapshot().revisions().logicalRevision());
			assertFalse(runtime.projectSnapshot().dirty());
		} finally { runtime.close(); }
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1})
	void stagingFailureWithoutAppliedItemsPreservesCleanAndDirtyState(int failedAt) throws Exception {
		Path jar = SymbolFixtureSupport.compileFixture(dir);
		AtomicInteger reloads = new AtomicInteger();
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), args -> new ProjectRuntime.ProjectEngine() {
			private final JadxDecompiler jadx = new JadxDecompiler(args);
			@Override public void load() { reloads.incrementAndGet(); jadx.load(); }
			@Override public JadxDecompiler decompiler() { return jadx; }
			@Override public void close() { jadx.close(); }
		});
		try {
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			SymbolRef owner = SymbolRef.classRef("Lprobe/SymbolFixture;");
			SymbolRef method = new SymbolRef(SymbolRef.Kind.METHOD, "Lprobe/SymbolFixture;", null, "mix", "(I)I");
			SymbolRef field = new SymbolRef(SymbolRef.Kind.FIELD, "Lprobe/SymbolFixture;", null, "count", "I");
			for (boolean dirty : List.of(false, true)) {
				if (dirty) new EditBatchService(runtime).apply(new EditDtos.Request(null, null, List.of(
						new EditDtos.Operation(EditDtos.Kind.RENAME, owner, "PriorUnsavedAlias", null, null))));
				var before = runtime.projectSnapshot();
				String pendingBefore = runtime.pendingEdits().toString();
				int reloadsBefore = reloads.get();
				EditBatchService service = new EditBatchService(runtime, index -> {
					if (index == failedAt) throw new IllegalStateException("injected staging failure");
				});
				var first = new EditDtos.Operation(EditDtos.Kind.RENAME, owner,
						failedAt == 0 ? "NeverApplied" : dirty ? "PriorUnsavedAlias" : "SymbolFixture", null, null);
				var result = service.apply(new EditDtos.Request(null, null, List.of(first,
						new EditDtos.Operation(EditDtos.Kind.RENAME, method, "NeverAppliedMethod", null, null),
						new EditDtos.Operation(EditDtos.Kind.RENAME, field, "NeverAppliedField", null, null))));
				assertEquals("PARTIAL", result.outcome());
				assertEquals(failedAt == 0 ? List.of("FAILED", "SKIPPED", "SKIPPED") : List.of("SKIPPED", "FAILED", "SKIPPED"),
						result.items().stream().map(EditDtos.ItemResult::status).toList());
				assertEquals(List.of(0, 1, 2), result.items().stream().map(EditDtos.ItemResult::index).toList());
				assertEquals("Native edit staging failed", result.items().get(failedAt).message());
				assertEquals("NOT_EXECUTED", result.items().get(2).message());
				if (failedAt == 1) assertEquals("NO_CHANGE", result.items().getFirst().message());
				assertEquals(before.revisions().sessionId(), result.sessionId());
				assertEquals(before.revisions().logicalRevision(), result.logicalRevisionBefore());
				assertEquals(before.revisions().logicalRevision(), result.logicalRevisionAfter());
				assertEquals(before.revisions().indexRevision(), result.indexRevisionAfter());
				assertEquals(dirty, result.dirty());
				assertFalse(result.saved());
				assertEquals(List.of("No edits were committed; later items were not executed"), result.diagnostics());
				assertEquals(before.revisions(), runtime.projectSnapshot().revisions());
				assertEquals(pendingBefore, runtime.pendingEdits().toString());
				assertEquals(reloadsBefore, reloads.get());
				assertEquals("READY", runtime.status().state());
				Path captures = Path.of("build/edit-service-results");
				ObjectMapper json = new ObjectMapper();
				var spec = new ObjectMapper(new com.fasterxml.jackson.dataformat.yaml.YAMLFactory())
						.readTree(Path.of("openapi/openapi.yaml").toFile());
				OpenApiExampleValidator.assertValid(spec, "EditBatchResult", json.valueToTree(result));
				java.nio.file.Files.createDirectories(captures);
				java.nio.file.Files.writeString(captures.resolve("partial-" + failedAt + "-" + dirty + ".json"),
						json.writeValueAsString(result));
			}
		} finally { runtime.close(); }
	}

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
			var targets = JadxNativeEditAdapter.memberTargets(
					JadxSymbolAdapter.visibleClass(runtime.decompiler(), "Lprobe/ReturnClash;", 0));
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
			String untouchedAfter = JadxNativeEditAdapter.memberTargets(
					JadxSymbolAdapter.visibleClass(runtime.decompiler(), "Lprobe/ReturnClash;", 0)).stream()
					.filter(target -> target.nativeRef().getShortId() != null
							&& target.nativeRef().getShortId().equals("value()Ljava/lang/String;"))
					.findFirst().orElseThrow().displayName();
			assertEquals("m0value", untouchedBefore);
			assertEquals("value", untouchedAfter);
			var pending = runtime.pendingEdits().getAsJsonObject("codeData");
			assertEquals(1, pending.getAsJsonArray("renames").size());
			var code = runtime.withExclusiveEdit(context -> context.codeDataCopy());
			var actual = SafeReplayStrategyTest.semantic(runtime.decompiler());
			try (var fresh = SafeReplayStrategyTest.open(List.of(jar), null, code)) {
				assertEquals(SafeReplayStrategyTest.semantic(fresh), actual);
			}
			Path savedPath = dir.resolve("clash.jadx");
			runtime.saveProject(savedPath, null);
			var saved = dev.libjadx.project.NativeProjectDocument.open(savedPath);
			try (var fresh = SafeReplayStrategyTest.open(saved.getInputFiles(), saved.getMappingsPath(), saved.getCodeData())) {
				assertEquals(actual, SafeReplayStrategyTest.semantic(fresh));
			}
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
	void candidateLoadFailurePreservesRuntimeWithoutSuccessResult() throws Exception {
		Path jar = SymbolFixtureSupport.compileFixture(dir);
		ProjectRuntime runtime = new ProjectRuntime(null, List.of(jar), args -> new ProjectRuntime.ProjectEngine() {
			private final JadxDecompiler jadx = new JadxDecompiler(args);
			@Override public void load() {
				if (!((JadxCodeData) args.getCodeData()).getRenames().isEmpty()) throw new IllegalStateException("injected candidate failure");
				jadx.load();
			}
			@Override public JadxDecompiler decompiler() { return jadx; }
			@Override public void close() { jadx.close(); }
		});
		try {
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			assertThrows(IllegalStateException.class, () -> new EditBatchService(runtime).apply(new EditDtos.Request(null, null,
					List.of(new EditDtos.Operation(EditDtos.Kind.RENAME, SymbolRef.classRef("Lprobe/SymbolFixture;"),
							"PendingAfterFailure", null, null)))));
			assertEquals("READY", runtime.status().state());
			assertEquals(0, runtime.projectSnapshot().revisions().logicalRevision());
			assertFalse(runtime.projectSnapshot().dirty());
		} finally { runtime.close(); }
	}
}
