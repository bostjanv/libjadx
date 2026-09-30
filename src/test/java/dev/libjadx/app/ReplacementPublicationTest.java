package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import dev.libjadx.core.edits.EditDtos;
import dev.libjadx.core.EffectiveAnalysisConfig;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.project.*;
import dev.libjadx.scheduler.ProjectBusyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReplacementPublicationTest {
	@TempDir Path dir;
	static EditDtos.Operation rename(String name) {
		return new EditDtos.Operation(EditDtos.Kind.RENAME, SymbolRef.classRef("Lprobe/SymbolFixture;"), name, null, null);
	}
	static EditDtos.Request request(EditDtos.Operation... items) { return new EditDtos.Request(null, null, List.of(items)); }

	@ParameterizedTest @ValueSource(strings={"BEFORE_CONSTRUCTION", "FACTORY", "CENSUS", "LOAD", "BIND", "LOADED", "VERIFIER_BOUND", "CONSISTENT", "BEFORE_COMMIT", "BEFORE_PUBLICATION", "REPOSITORY_COMMIT", "MISMATCH"})
	void preparationFailuresLeaveCleanAndDirtyProjectUntouched(String fault) throws Exception {
		Path input = SymbolFixtureSupport.compileFixture(dir);
		Path path = dir.resolve("project.jadx"); NativeProjectDocument.newFromInputs(path, List.of(input)).save();
		AtomicInteger creates = new AtomicInteger(), closes = new AtomicInteger();
		boolean[] armed = {false};
		try (var runtime = new ProjectRuntime(path, List.of(input), args -> {
			creates.incrementAndGet();
			if (armed[0] && fault.equals("FACTORY")) throw new IllegalStateException("injected factory");
			var real = new ProjectRuntime.JadxProjectEngine(args, stage -> {
				if (armed[0] && stage.name().equals(fault)) throw new IllegalStateException("injected " + stage);
			});
			return new ProjectRuntime.ProjectEngine() {
				public void load() { real.load(); if (armed[0] && fault.equals("MISMATCH")) real.decompiler().getArgs().setCodeData(new jadx.api.data.impl.JadxCodeData()); }
				public jadx.api.JadxDecompiler decompiler() { return real.decompiler(); }
				public dev.libjadx.core.hierarchy.RelatedHierarchyVerifier hierarchyVerifier() { return real.hierarchyVerifier(); }
				public void close() { closes.incrementAndGet(); real.close(); }
			};
		})) {
			runtime.initializeAsync(NativeProjectDocument.open(path)).get(20, TimeUnit.SECONDS);
			for (boolean dirty : List.of(false, true)) {
				armed[0] = false;
				if (dirty) new EditBatchService(runtime).apply(request(rename("PriorAlias")));
				var before = runtime.projectSnapshot(); var pending = runtime.pendingEdits(); var identity = runtime.searchIdentity();
				var old = runtime.decompiler(); var source = SafeReplayStrategyTest.semantic(old);
				var disk = FileFingerprint.of(path); int closedBefore = closes.get(); int createsBefore = creates.get();
				armed[0] = true;
				runtime.replacementHook(stage -> { if (stage.name().equals(fault)) throw new IllegalStateException("injected " + stage); });
				assertThrows(IllegalStateException.class, () -> new EditBatchService(runtime).apply(request(rename("NextAlias"))));
				assertSame(old, runtime.decompiler()); assertEquals(before, runtime.projectSnapshot()); assertEquals(pending, runtime.pendingEdits());
				assertEquals(identity, runtime.searchIdentity()); assertEquals(source, SafeReplayStrategyTest.semantic(old));
				assertEquals("READY", runtime.status().state()); assertEquals(disk, FileFingerprint.of(path));
				boolean built = !List.of("BEFORE_CONSTRUCTION", "FACTORY").contains(fault);
				assertEquals(closedBefore + (built ? 1 : 0), closes.get());
				assertEquals(createsBefore + (fault.equals("BEFORE_CONSTRUCTION") ? 0 : 1), creates.get());
				armed[0] = false; runtime.replacementHook(ignored -> { });
			}
		}
	}

	@Test void effectivePrefixFailureNoopPrevalidationAndCleanupAreTruthful() throws Exception {
		Path input = SymbolFixtureSupport.compileFixture(dir); AtomicInteger creates = new AtomicInteger(), closes = new AtomicInteger();
		try (var runtime = new ProjectRuntime(null, List.of(input), args -> {
			int sequence = creates.incrementAndGet(); var real = new ProjectRuntime.JadxProjectEngine(args);
			return new ProjectRuntime.ProjectEngine() {
				public void load() { real.load(); } public jadx.api.JadxDecompiler decompiler() { return real.decompiler(); }
				public dev.libjadx.core.hierarchy.RelatedHierarchyVerifier hierarchyVerifier() { return real.hierarchyVerifier(); }
				public void close() { closes.incrementAndGet(); real.close(); if (sequence == 1) throw new IllegalStateException("obsolete cleanup"); }
			};
		})) {
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			var identity = runtime.searchIdentity(); var old = runtime.decompiler();
			var second = new EditDtos.Operation(EditDtos.Kind.SET_COMMENT, SymbolRef.classRef("Lprobe/SymbolFixture;"), null, "comment", "LINE");
			var service = new EditBatchService(runtime, index -> { if (index == 1) throw new IllegalStateException("item staging"); });
			runtime.replacementHook(stage -> { if (stage == ProjectRuntime.ReplacementStage.LOADED) throw new IllegalStateException("prefix candidate failed"); });
			assertThrows(IllegalStateException.class, () -> service.apply(request(rename("PrefixAlias"), second)));
			assertSame(old, runtime.decompiler()); assertEquals(0, runtime.projectSnapshot().revisions().logicalRevision());
			assertFalse(runtime.projectSnapshot().dirty()); assertEquals(identity, runtime.searchIdentity());
			runtime.replacementHook(ignored -> { });
			var partial = service.apply(request(rename("PrefixAlias"), second));
			assertEquals("PARTIAL", partial.outcome()); assertEquals(List.of("APPLIED", "FAILED"), partial.items().stream().map(EditDtos.ItemResult::status).toList());
			assertEquals(1, partial.logicalRevisionAfter()); assertEquals(1, partial.indexRevisionAfter());
			assertEquals(identity.publicationEpoch() + 1, runtime.searchIdentity().publicationEpoch());
			assertNotSame(old, runtime.decompiler()); assertEquals("READY", runtime.status().state());
			assertEquals(3, creates.get()); assertEquals(2, closes.get());
			var current = runtime.decompiler(); var after = runtime.projectSnapshot(); var afterIdentity = runtime.searchIdentity();
			assertEquals("NO_CHANGE", new EditBatchService(runtime).apply(request(rename("PrefixAlias"))).outcome());
			assertSame(current, runtime.decompiler()); assertEquals(after, runtime.projectSnapshot()); assertEquals(afterIdentity, runtime.searchIdentity()); assertEquals(3, creates.get());
			assertThrows(EditBatchService.Rejected.class, () -> new EditBatchService(runtime).apply(request(rename("bad-name"))));
			assertEquals(3, creates.get());
		}
	}

	@ParameterizedTest @ValueSource(strings={"LOADED", "BEFORE_COMMIT", "BEFORE_PUBLICATION", "CLOSE"})
	void exclusiveLeaseHidesPrivateStateAndClosesOldOnlyAfterPublication(String checkpoint) throws Exception {
		Path input = SymbolFixtureSupport.compileFixture(dir);
		CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
		AtomicInteger creates = new AtomicInteger(), closes = new AtomicInteger();
		try (var runtime = new ProjectRuntime(null, List.of(input), args -> {
			int sequence = creates.incrementAndGet(); var real = new ProjectRuntime.JadxProjectEngine(args);
			return new ProjectRuntime.ProjectEngine() {
				public void load() { real.load(); } public jadx.api.JadxDecompiler decompiler() { return real.decompiler(); }
				public dev.libjadx.core.hierarchy.RelatedHierarchyVerifier hierarchyVerifier() { return real.hierarchyVerifier(); }
				public void close() { if (sequence == 1 && checkpoint.equals("CLOSE")) { entered.countDown(); await(release); } closes.incrementAndGet(); real.close(); }
			};
		})) {
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			var old = runtime.decompiler(); var before = runtime.projectSnapshot();
			runtime.replacementHook(stage -> { if (stage.name().equals(checkpoint)) { entered.countDown(); await(release); } });
			var edit = CompletableFuture.supplyAsync(() -> new EditBatchService(runtime).apply(request(rename("PublishedAlias"))));
			try {
				assertTrue(entered.await(10, TimeUnit.SECONDS));
				assertEquals(0, closes.get()); assertEquals("READY", runtime.status().state());
				if (checkpoint.equals("CLOSE")) {
					assertEquals(1, runtime.projectSnapshot().revisions().logicalRevision()); assertNotSame(old, runtime.decompiler());
				} else { assertEquals(before, runtime.projectSnapshot()); assertSame(old, runtime.decompiler()); }
				assertThrows(ProjectBusyException.class, () -> runtime.withPrimaryClassRead("any", jadx -> 1));
				assertThrows(ProjectBusyException.class, () -> new EditBatchService(runtime).apply(request(rename("Interleaved"))));
				assertThrows(ProjectBusyException.class, () -> runtime.saveProject(dir.resolve("busy.jadx"), null));
				assertThrows(ProjectBusyException.class, () -> runtime.reloadProject(true, before.revisions().sessionId(), 0));
				assertThrows(ProjectBusyException.class, () -> runtime.updateMappingsPath(null, before.revisions().sessionId(), 0));
				assertThrows(ProjectBusyException.class, () -> runtime.withTemporaryAnalysis(EffectiveAnalysisConfig.defaults(), jadx -> 1));
				assertThrows(ProjectRuntime.ShutdownRejectedException.class, () -> runtime.requestShutdown(ShutdownPolicy.DISCARD));
			} finally { release.countDown(); }
			assertEquals("APPLIED", edit.get(20, TimeUnit.SECONDS).outcome());
			assertEquals(1, closes.get());
			runtime.withPrimarySymbolRead("owner", context -> {
				assertEquals(1, context.revisions().logicalRevision());
				assertTrue(((jadx.api.data.impl.JadxCodeData) context.decompiler().getArgs().getCodeData()).getRenames().getFirst().getNewName().equals("PublishedAlias"));
				return null;
			});
			assertThrows(EditBatchService.Rejected.class, () -> new EditBatchService(runtime).apply(new EditDtos.Request(before.revisions().sessionId(), 0L, List.of(rename("Stale")))));
		} finally { release.countDown(); }
	}
	@Test void repositoryReadersWaitAcrossCommitAndPublicationBoundary() throws Exception {
		Path input = SymbolFixtureSupport.compileFixture(dir);
		CountDownLatch committing = new CountDownLatch(1), release = new CountDownLatch(1);
		try (var executor = Executors.newVirtualThreadPerTaskExecutor(); var runtime = new ProjectRuntime(null, List.of(input), List.of(dir))) {
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			runtime.replacementHook(stage -> {
				if (stage == ProjectRuntime.ReplacementStage.REPOSITORY_COMMIT) { committing.countDown(); await(release); }
			});
			var edit = CompletableFuture.supplyAsync(() -> new EditBatchService(runtime).apply(request(rename("AtomicAlias"))));
			try {
				assertTrue(committing.await(10, TimeUnit.SECONDS));
				CountDownLatch readersStarted = new CountDownLatch(3);
				var snapshot = CompletableFuture.supplyAsync(() -> { readersStarted.countDown(); return runtime.projectSnapshot(); }, executor);
				var settings = CompletableFuture.supplyAsync(() -> { readersStarted.countDown(); return runtime.settingsSnapshot(); }, executor);
				var pending = CompletableFuture.supplyAsync(() -> { readersStarted.countDown(); return runtime.pendingEdits(); }, executor);
				assertTrue(readersStarted.await(5, TimeUnit.SECONDS));
				assertFalse(snapshot.isDone()); assertFalse(settings.isDone()); assertFalse(pending.isDone());
				release.countDown();
				assertEquals("APPLIED", edit.get(20, TimeUnit.SECONDS).outcome());
				assertEquals(1, snapshot.get(5, TimeUnit.SECONDS).revisions().logicalRevision());
				assertEquals(1, settings.get(5, TimeUnit.SECONDS).revisions().logicalRevision());
				assertEquals(1, pending.get(5, TimeUnit.SECONDS).get("logicalRevision").getAsLong());
				assertEquals("AtomicAlias", runtime.withPrimaryClassRead("owner", jadx ->
						((jadx.api.data.impl.JadxCodeData) jadx.getArgs().getCodeData()).getRenames().getFirst().getNewName()));
			} finally { release.countDown(); }
		} finally { release.countDown(); }
	}
	static void await(CountDownLatch latch) { try { if (!latch.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("latch timeout"); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); } }
}
