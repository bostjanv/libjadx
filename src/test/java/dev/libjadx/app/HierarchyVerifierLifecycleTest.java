package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import dev.libjadx.core.edits.EditDtos;
import dev.libjadx.core.hierarchy.RelatedHierarchyVerifier;
import dev.libjadx.core.hierarchy.RelatedHierarchyVerifier.Status;
import dev.libjadx.core.hierarchy.RelatedHierarchyVerifier.VerificationBudget;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.probes.HierarchyFixture;
import dev.libjadx.probes.RelatedFixture;
import dev.libjadx.project.FileFingerprint;
import dev.libjadx.project.NativeProjectDocument;
import dev.libjadx.scheduler.ProjectBusyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HierarchyVerifierLifecycleTest {
	@TempDir Path dir;
	static final SymbolRef SEED = RelatedFixture.ref("Joined", "joined", "(I)I");
	static RelatedHierarchyVerifier.Verification verify(ProjectRuntime runtime) {
		return runtime.verifyRelatedHierarchy(SEED, VerificationBudget.defaults());
	}
	ProjectRuntime open(Path path, List<Path> inputs) throws Exception {
		var runtime = new ProjectRuntime(path, inputs, List.of(dir));
		try { runtime.initializeAsync(path == null ? null : NativeProjectDocument.open(path)).get(20, TimeUnit.SECONDS); return runtime; }
		catch (Exception failure) { runtime.close(); throw failure; }
	}

	@Test void verificationIsReadOnlyAcrossNativeEditsExplicitSaveMappingsModesAndRestart() throws Exception {
		var inputs = RelatedFixture.compile(dir); Path path = dir.resolve("project.jadx");
		NativeProjectDocument.newFromInputs(path, inputs).save();
		var hashes = inputs.stream().map(p -> { try { return FileFingerprint.of(p); } catch (Exception e) { throw new RuntimeException(e); } }).toList();
		RelatedHierarchyVerifier original; String session;
		try (var runtime = open(path, inputs)) {
			var before = runtime.projectSnapshot(); var pending = runtime.pendingEdits(); var identity = runtime.searchIdentity();
			var nativeHash = FileFingerprint.of(path);
			original = runtime.withHierarchyVerifier(v -> v); session = before.revisions().sessionId();
			var expected = verify(runtime); assertEquals(Status.COMPLETE, expected.status()); assertEquals(4, expected.members().size());
			assertEquals(before, runtime.projectSnapshot()); assertEquals(pending, runtime.pendingEdits());
			assertEquals(identity, runtime.searchIdentity()); assertEquals(nativeHash, FileFingerprint.of(path));
			assertTrue(runtime.decompiler().getRoot().getClasses().stream().allMatch(c -> c.getState() == jadx.core.dex.nodes.ProcessState.NOT_LOADED));
			new EditBatchService(runtime).apply(new EditDtos.Request(null, null, List.of(
					new EditDtos.Operation(EditDtos.Kind.RENAME, SEED, "pendingJoined", null, null),
					new EditDtos.Operation(EditDtos.Kind.SET_COMMENT, SymbolRef.classRef("Lrelated/Hierarchy;"), null, "pending", "LINE"))));
			assertEquals(expected, verify(runtime)); assertSame(original, runtime.withHierarchyVerifier(v -> v));
			assertEquals(nativeHash, FileFingerprint.of(path));
			runtime.saveProject(null, null); assertSame(original, runtime.withHierarchyVerifier(v -> v));
			assertEquals(expected, verify(runtime));
			Path mapping = dir.resolve("aliases.tiny");
			Files.writeString(mapping, "tiny\t2\t0\tofficial\tnamed\nc\trelated/Hierarchy$SeparateRight\trelated/Hierarchy$SeparateRight\n\tm\t(I)I\tjoined\tmappedRight\n");
			var revision = runtime.projectSnapshot().revisions();
			runtime.updateMappingsPath(mapping, revision.sessionId(), revision.logicalRevision());
			assertEquals(expected, verify(runtime)); assertNotSame(original, runtime.withHierarchyVerifier(v -> v));
			for (String mode : List.of("AUTO", "SIMPLE", "FALLBACK", "RESTRUCTURE")) {
				var temporary = runtime.withTemporaryAnalysis(new dev.libjadx.core.EffectiveAnalysisConfig(mode), engine -> {
					var capture = dev.libjadx.jadxadapter.JadxInputCensusAdapter.capture(inputs, dev.libjadx.core.hierarchy.CensusLimits.defaults());
					return capture.bind(engine).verify(SEED, VerificationBudget.defaults());
				});
				assertEquals(expected, temporary.value());
			}
			runtime.saveProject(null, null);
		}
		try (var runtime = open(path, inputs)) {
			assertNotEquals(session, runtime.projectSnapshot().revisions().sessionId());
			assertNotSame(original, runtime.withHierarchyVerifier(v -> v)); assertEquals(Status.COMPLETE, verify(runtime).status());
			assertEquals(4, verify(runtime).members().size());
		}
		for (int i = 0; i < inputs.size(); i++) assertEquals(hashes.get(i), FileFingerprint.of(inputs.get(i)));
		try (var paths = Files.list(dir)) {
			assertEquals(List.of("aliases.tiny", "branches.jar", "classes", "hierarchy.jar", "project.jadx"), paths.map(p -> p.getFileName().toString()).sorted().toList());
		}
	}

	@Test void concurrentImmutableReadsExcludeReloadAndShutdownUntilTheLeaseEnds() throws Exception {
		var inputs = RelatedFixture.compile(dir); Path path = dir.resolve("project.jadx"); NativeProjectDocument.newFromInputs(path, inputs).save();
		try (var runtime = open(path, inputs); var executor = Executors.newFixedThreadPool(4)) {
			CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
			var holding = executor.submit(() -> runtime.withHierarchyVerifier(v -> {
				entered.countDown();
				try { assertTrue(release.await(10, TimeUnit.SECONDS)); }
				catch (InterruptedException e) { throw new AssertionError(e); }
				return v.verify(SEED, VerificationBudget.defaults());
			}));
			try {
				assertTrue(entered.await(5, TimeUnit.SECONDS));
				var before = runtime.projectSnapshot().revisions();
				assertThrows(ProjectBusyException.class, () -> runtime.reloadProject(true, before.sessionId(), before.logicalRevision()));
				assertThrows(ProjectRuntime.ShutdownRejectedException.class, () -> runtime.requestShutdown(ShutdownPolicy.DISCARD));
				var reads = new java.util.ArrayList<java.util.concurrent.Future<RelatedHierarchyVerifier.Verification>>();
				for (int i = 0; i < 12; i++) reads.add(executor.submit(() -> verify(runtime)));
				for (var read : reads) assertEquals(Status.COMPLETE, read.get(5, TimeUnit.SECONDS).status());
			} finally { release.countDown(); }
			assertEquals(Status.COMPLETE, holding.get(5, TimeUnit.SECONDS).status());
			var before = runtime.projectSnapshot().revisions();
			runtime.reloadProject(true, before.sessionId(), before.logicalRevision());
			assertEquals(Status.COMPLETE, verify(runtime).status());
		}
	}

	@Test void inputModificationInvalidatesUntilExplicitReloadRebuildsOriginalDeclarations() throws Exception {
		Path jar = HierarchyFixture.javaJar(dir.resolve("first"), "fixed.jar", Map.of("change/C.java", "package change; public class C { public int f(int n){return n;} }"));
		Path replacement = HierarchyFixture.javaJar(dir.resolve("next"), "replacement.jar", Map.of("change/C.java", "package change; public class C { public int f(int n){return n;} public int added(int n){return n;} }"));
		var seed = new SymbolRef(SymbolRef.Kind.METHOD, "Lchange/C;", null, "f", "(I)I");
		var added = new SymbolRef(SymbolRef.Kind.METHOD, "Lchange/C;", null, "added", "(I)I");
		Path path = dir.resolve("project.jadx"); NativeProjectDocument.newFromInputs(path, List.of(jar)).save();
		try (var runtime = open(path, List.of(jar))) {
			assertEquals(Status.COMPLETE, runtime.verifyRelatedHierarchy(seed, VerificationBudget.defaults()).status());
			assertEquals(Status.NOT_FOUND, runtime.verifyRelatedHierarchy(added, VerificationBudget.defaults()).status());
			Files.copy(replacement, jar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			assertEquals(Status.INPUT_CHANGED, runtime.verifyRelatedHierarchy(seed, VerificationBudget.defaults()).status());
			assertEquals(Status.INPUT_CHANGED, runtime.verifyRelatedHierarchy(added, VerificationBudget.defaults()).status());
			var before = runtime.projectSnapshot().revisions(); runtime.reloadProject(true, before.sessionId(), before.logicalRevision());
			assertEquals(Status.COMPLETE, runtime.verifyRelatedHierarchy(added, VerificationBudget.defaults()).status());
		}
	}
}
