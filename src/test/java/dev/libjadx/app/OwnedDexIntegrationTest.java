package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;
import static dev.libjadx.app.NormalizedCfgIsolationTest.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import dev.libjadx.core.EffectiveAnalysisConfig;
import dev.libjadx.core.RevisionState;
import dev.libjadx.core.hierarchy.InputCensus;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.jadxadapter.JadxEngineFactory;
import dev.libjadx.project.FileFingerprint;
import dev.libjadx.project.NativeProjectDocument;
import dev.libjadx.project.NativeProjectRepository;
import dev.libjadx.project.NativeProjectRepository.AcceptedInput;
import dev.libjadx.probes.HierarchyFixture;
import dev.libjadx.scheduler.ProjectBusyException;
import jadx.api.JadxDecompiler;
import jadx.api.data.impl.JadxCodeData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Repository-backed internal gate; no HTTP graph contract or new persistence behavior. */
class OwnedDexIntegrationTest {
	@TempDir Path dir;
	@TempDir Path outside;

	Path fixture() throws Exception {
		return HierarchyFixture.dex(dir, "cfg.dex", Files.readString(Path.of("tests/fixtures/cfg/CfgCases.smali")));
	}
	ProjectRuntime open(Path project, List<Path> inputs) throws Exception {
		var runtime = new ProjectRuntime(project, inputs, List.of(dir));
		try { runtime.initializeAsync(project == null ? null : NativeProjectDocument.open(project)).get(30, TimeUnit.SECONDS); return runtime; }
		catch (Exception failure) { runtime.close(); throw failure; }
	}
	// The graph stays a test observation, bound independently of temporary Java source IDs.
	record Bound(SymbolRef method, String representation, RevisionState revisions, long publication,
			String settings, List<AcceptedInput> consumed, InputCensus census, Observation observation, String graphDigest) { }
	Bound capture(ProjectRuntime.OwnedDexRead read) {
		var method = ref("branchLoop");
		var observation = new Capture(method).run(read.decompiler());
		try {
			return new Bound(method, "JADX_1.5.6_28ff15e4_POST_BLOCK_FINISHER", read.revisions(), read.publicationEpoch(),
					read.settings().fingerprint(), read.consumedInputs(), read.census(), observation,
					OwnedDexAdmissionTest.hash(JSON.writeValueAsBytes(observation.graph())));
		} catch (Exception failure) { throw new AssertionError(failure); }
	}

	@ParameterizedTest @ValueSource(strings = {"AUTO", "RESTRUCTURE"})
	void realRepositorySnapshotMatchesFreshNativeInitializationAndScopedReplay(String mode) throws Exception {
		Path dex = HierarchyFixture.dex(dir, "init.dex", """
				.class public Lprobe/Init;
				.super Ljava/lang/Object;
				.field public count:I
				.method public static calculate(IJ)J
				.registers 5
				int-to-long v0, p0
				add-long/2addr v0, p1
				return-wide v0
				.end method
				.method public static value()I
				.registers 1
				const/16 v0, 37
				return v0
				.end method
				.method public static value()Ljava/lang/String;
				.registers 1
				const-string v0, "different return"
				return-object v0
				.end method
				""");
		var config = new EffectiveAnalysisConfig(mode);
		try (var runtime = open(null, List.of(dex))) {
			for (var edits : List.of(new JadxCodeData(), OwnedDexAdmissionTest.initializationEdits("Unsaved"))) {
				runtime.replaceCodeData(edits, runtime.projectSnapshot().revisions().logicalRevision());
				try (var fresh = new JadxDecompiler(JadxEngineFactory.arguments(List.of(dex), null, edits, config))) {
					fresh.load();
					var before = runtime.projectSnapshot(); var search = runtime.searchIdentity();
					assertEquals(OwnedDexAdmissionTest.initializationSemantics(fresh), runtime.withOwnedDexRead(config, r -> {
						assertTrue(r.decompiler().getArgs().getInputFiles().isEmpty());
						assertEquals(before.revisions(), r.revisions());
						return OwnedDexAdmissionTest.initializationSemantics(r.decompiler());
					}));
					assertEquals(before, runtime.projectSnapshot()); assertEquals(search, runtime.searchIdentity());
				}
			}
			assertFalse(Files.exists(dir.resolve("init.dex.jadx")));
		}
	}

	@Test void changedSecondaryDeletedAndSymlinkInputsRefuseWithoutAcceptingNewHashesAndRecover() throws Exception {
		Path dex = fixture();
		Path second = HierarchyFixture.dex(dir, "second.dex", HierarchyFixture.smali("Lprobe/Second;", "Ljava/lang/Object;", "public", "f"));
		byte[] original = Files.readAllBytes(second);
		try (var runtime = open(null, List.of(dex, second, dex))) {
			var before = runtime.projectSnapshot();
			for (String change : List.of("changed", "deleted", "symlink")) {
				if (change.equals("changed")) Files.writeString(second, "not accepted");
				else {
					Files.delete(second);
					if (change.equals("symlink")) Files.createSymbolicLink(second, Files.write(outside.resolve("outside.dex"), original));
				}
				if (change.equals("symlink")) assertThrows(SecurityException.class, () -> runtime.withOwnedDexRead(AUTO, r -> fail("callback")));
				else assertThrows(NativeProjectRepository.ExternalModificationException.class, () -> runtime.withOwnedDexRead(AUTO, r -> fail("callback")));
				assertEquals(before, runtime.projectSnapshot());
				Files.deleteIfExists(second); Files.write(second, original);
				var result = runtime.withOwnedDexRead(AUTO, this::capture);
				assertEquals(3, result.consumed().size());
				assertEquals(List.of(dex, second, dex), result.consumed().stream().map(AcceptedInput::path).toList());
				assertEquals(2, result.census().classesByDescriptor().get(OWNER).size());
				assertEquals("AVAILABLE", result.observation().availability());
				assertEquals(before, runtime.projectSnapshot());
			}
		}
	}

	@Test void snapshotSurvivesEditSaveReloadAndReplacementBytesWhileNextReadUsesNewPublication() throws Exception {
		Path dex = fixture();
		byte[] accepted = Files.readAllBytes(dex);
		Path project = dir.resolve("native.jadx");
		Bound retained;
		try (var runtime = open(null, List.of(dex))) {
			runtime.replaceCodeData(edits("OldLoop", "old comment"), 0);
			var before = runtime.projectSnapshot();
			CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
			var pending = CompletableFuture.supplyAsync(() -> {
				try { return runtime.withOwnedDexRead(AUTO, r -> {
					entered.countDown();
					try { if (!release.await(30, TimeUnit.SECONDS)) throw new AssertionError("release timeout"); }
					catch (InterruptedException failure) { throw new AssertionError(failure); }
					return capture(r);
				}); } catch (Exception failure) { throw new RuntimeException(failure); }
			});
			try {
				assertTrue(entered.await(30, TimeUnit.SECONDS));
				assertThrows(ProjectBusyException.class, () -> runtime.withOwnedDexRead(AUTO, this::capture));
				assertThrows(ProjectBusyException.class, () -> runtime.withTemporaryAnalysis(AUTO, r -> "other"));
				for (var policy : ShutdownPolicy.values()) assertThrows(ProjectRuntime.ShutdownRejectedException.class, () -> runtime.requestShutdown(policy));
				runtime.replaceCodeData(edits("NewLoop", "new comment"), before.revisions().logicalRevision());
				runtime.saveProject(project, null);
				var text = Files.readString(Path.of("tests/fixtures/cfg/CfgCases.smali")).replace("if-le", "if-gt");
				Path opposite = HierarchyFixture.dex(dir, "opposite.dex", text);
				Files.copy(opposite, dex, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				var current = runtime.projectSnapshot().revisions();
				runtime.reloadProject(false, current.sessionId(), current.logicalRevision());
			} finally { release.countDown(); }
			var old = pending.get(30, TimeUnit.SECONDS);
			loopBranches(old.observation().graph(), "LE");
			assertEquals(before.revisions(), old.revisions()); assertEquals("OldLoop", old.observation().alias());
			assertEquals(List.of("old comment"), old.observation().comments());
			assertEquals(new FileFingerprint(true, OwnedDexAdmissionTest.hash(accepted)), old.consumed().getFirst().fingerprint());
			var next = runtime.withOwnedDexRead(new EffectiveAnalysisConfig("RESTRUCTURE"), this::capture);
			loopBranches(next.observation().graph(), "GT");
			assertEquals("NewLoop", next.observation().alias()); assertEquals(List.of("new comment"), next.observation().comments());
			assertNotEquals(old.graphDigest(), next.graphDigest()); assertNotEquals(old.consumed(), next.consumed());
			assertNotEquals(old.publication(), next.publication()); assertNotEquals(old.settings(), next.settings());
			var saved = runtime.withOwnedDexRead(AUTO, this::capture);
			runtime.saveProject(null, null);
			var saveOnly = runtime.withOwnedDexRead(AUTO, this::capture);
			assertEquals(saved.graphDigest(), saveOnly.graphDigest()); assertEquals(saved.consumed(), saveOnly.consumed());
			assertNotEquals(saved.publication(), saveOnly.publication());
			retained = saveOnly;
		}
		try (var restarted = open(project, List.of(dex))) {
			var after = restarted.withOwnedDexRead(AUTO, this::capture);
			assertEquals("NewLoop", after.observation().alias()); assertEquals(retained.graphDigest(), after.graphDigest());
			assertNotEquals(retained.revisions().sessionId(), after.revisions().sessionId());
		}
	}

	static JadxCodeData edits(String alias, String comment) {
		var data = new JadxCodeData();
		var node = new jadx.api.data.impl.JadxNodeRef(jadx.api.data.IJavaNodeRef.RefType.METHOD, "probe.CfgCases", "branchLoop(I)I");
		data.setRenames(List.of(new jadx.api.data.impl.JadxCodeRename(node, alias)));
		data.setComments(List.of(new jadx.api.data.impl.JadxCodeComment(node, comment)));
		return data;
	}

	@Test void unsavedMappingAndUnsupportedModeRefuseAndCallbackFailureReleasesLease() throws Exception {
		Path dex = fixture();
		try (var runtime = open(null, List.of(dex))) {
			assertThrows(IllegalArgumentException.class, () -> runtime.withOwnedDexRead(new EffectiveAnalysisConfig("SIMPLE"), r -> fail("callback")));
			assertThrows(IllegalStateException.class, () -> runtime.withOwnedDexRead(AUTO, r -> { throw new IllegalStateException("callback failure"); }));
			assertEquals("AVAILABLE", runtime.withOwnedDexRead(AUTO, this::capture).observation().availability());
			runtime.saveProject(dir.resolve("attached.jadx"), null);
			Path mapping = Files.writeString(dir.resolve("attached.tiny"), "tiny\t2\t0\toriginal\tmapped\n");
			var rev = runtime.projectSnapshot().revisions(); runtime.updateMappingsPath(mapping, rev.sessionId(), rev.logicalRevision());
			assertTrue(runtime.projectSnapshot().dirty());
			assertThrows(IllegalArgumentException.class, () -> runtime.withOwnedDexRead(AUTO, r -> fail("callback")));
			rev = runtime.projectSnapshot().revisions(); runtime.updateMappingsPath(null, rev.sessionId(), rev.logicalRevision());
			assertEquals("AVAILABLE", runtime.withOwnedDexRead(AUTO, this::capture).observation().availability());
		}
	}

	@Test void failedMethodCopyAndLaterGenerationFailureReleaseTemporaryOwnership() throws Exception {
		Path dex = HierarchyFixture.dex(dir, "broken.dex", Files.readString(Path.of("tests/fixtures/smali/Disassembly.smali")));
		try (var runtime = open(null, List.of(dex))) {
			var before = runtime.projectSnapshot();
			var broken = new SymbolRef(SymbolRef.Kind.METHOD, "Lprobe/Disassembly;", null, "broken", "()I");
			var healthy = new SymbolRef(SymbolRef.Kind.METHOD, "Lprobe/Disassembly;", null, "answer", "()I");
			assertEquals("FAILED", runtime.withOwnedDexRead(AUTO, r -> new Capture(broken).run(r.decompiler())).availability());
			assertThrows(IllegalArgumentException.class, () -> runtime.withOwnedDexRead(AUTO, r ->
					new Capture(healthy, new dev.libjadx.probes.JadxCfgProbeTest.Limits(1, 512, 2048, 2 << 20)).run(r.decompiler())));
			assertEquals("FAILED", runtime.withOwnedDexRead(AUTO, r -> {
				var capture = new Capture(healthy); capture.failLater = true;
				return capture.run(r.decompiler());
			}).availability());
			assertEquals("AVAILABLE", runtime.withOwnedDexRead(AUTO, r -> new Capture(healthy).run(r.decompiler())).availability());
			assertEquals(before, runtime.projectSnapshot());
		}
	}
}
