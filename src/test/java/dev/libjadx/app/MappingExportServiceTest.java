package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import dev.libjadx.core.edits.EditDtos;
import dev.libjadx.core.mappings.MappingExportDtos;
import dev.libjadx.core.mappings.MappingExportDtos.Request;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.project.NativeProjectDocument;
import dev.libjadx.project.NativeProjectRepository;
import dev.libjadx.project.FileFingerprint;
import dev.libjadx.scheduler.ProjectBusyException;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.data.CommentStyle;
import jadx.api.data.IJavaNodeRef.RefType;
import jadx.api.data.impl.*;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MappingExportServiceTest {
	@TempDir Path dir;
	private static final String ATTACHED = "tiny\t2\t0\toriginal\tmapped\n"
			+ "c\tprobe/UnrelatedA\tprobe/MappedA\n"
			+ "c\tprobe/EditOwner\tprobe/AttachedOwner\n"
			+ "\tc\tattached class comment\n"
			+ "\tm\t()I\twork\tattachedWork\n\t\tc\tattached method comment\n"
			+ "\tf\tI\tcount\tattachedCount\n\t\tc\tattached field comment\n";

	@Test
	void unattachedRawInputWithEmptyCodeDataExportsOnlyTinyHeader() throws Exception {
		Path jar = SymbolFixtureSupport.compileEditFixture(dir);
		try (var runtime = new ProjectRuntime(null, List.of(jar), List.of(dir))) {
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			assertNull(runtime.decompiler().getArgs().getUserRenamesMappingsPath());
			var before = runtime.projectSnapshot();
			var identity = runtime.searchIdentity();
			var receipt = new MappingExportService(runtime).export(request(runtime, "header.tiny"));
			assertEquals("tiny\t2\t0\toriginal\tmapped\n", Files.readString(dir.resolve("header.tiny")));
			assertEquals(new MappingExportDtos.Counts(0, 0, 0, 0), receipt.exported());
			assertEquals("VERIFIED_DECLARATIONS", receipt.completeness());
			assertFalse(receipt.projectMutated()); assertTrue(receipt.omissions().isEmpty());
			assertFalse(before.dirty()); assertEquals(before, runtime.projectSnapshot());
			assertEquals(identity, runtime.searchIdentity()); assertNoStaging();
		}
	}

	@Test
	void attachedZeroByteMappingRejectsBeforeStagingAndPreservesCleanAndDirtyState() throws Exception {
		try (var runtime = project("")) {
			assertEquals("READY", runtime.status().state());
			assertNull(jadx.plugins.mappings.RenameMappingsData.getTree(runtime.decompiler().getRoot()));
			assertEquals(dir.resolve("attached.tiny"), runtime.decompiler().getArgs().getUserRenamesMappingsPath());
			var nativeHash = FileFingerprint.of(dir.resolve("project.jadx"));
			var mappingHash = FileFingerprint.of(dir.resolve("attached.tiny"));
			for (boolean dirty : List.of(false, true)) {
				if (dirty) new EditBatchService(runtime).apply(new EditDtos.Request(null, null, List.of(
						new EditDtos.Operation(EditDtos.Kind.RENAME, SymbolRef.classRef("Lprobe/EditOwner;"), "UnsavedOwner", null, null))));
				var before = runtime.projectSnapshot();
				var identity = runtime.searchIdentity();
				String pending = runtime.pendingEdits().toString();
				String target = "empty-attached-" + dirty + ".tiny";
				var error = assertThrows(MappingExportDtos.Problem.class, () -> new MappingExportService(runtime, stage -> {
					assertEquals("ENCODE", stage, "Must reject before staging or publication");
				}).export(request(runtime, target)));
				assertEquals(422, error.status()); assertEquals("UNSUPPORTED_CAPABILITY", error.code());
				assertEquals(dirty, before.dirty()); assertEquals(before, runtime.projectSnapshot());
				assertEquals(identity, runtime.searchIdentity()); assertEquals(pending, runtime.pendingEdits().toString());
				assertEquals(nativeHash, FileFingerprint.of(dir.resolve("project.jadx")));
				assertEquals(mappingHash, FileFingerprint.of(dir.resolve("attached.tiny")));
				assertFalse(Files.exists(dir.resolve(target))); assertNoStaging();
			}
		}
	}

	@Test
	void exportIncludesAttachedAndUnsavedDeclarationsWithoutChangingAnyProjectIdentity() throws Exception {
		try (var runtime = project(ATTACHED)) {
			var service = new EditBatchService(runtime);
			var cls = SymbolRef.classRef("Lprobe/EditOwner;");
			var method = new SymbolRef(SymbolRef.Kind.METHOD, "Lprobe/EditOwner;", null, "work", "()I");
			var field = new SymbolRef(SymbolRef.Kind.FIELD, "Lprobe/EditOwner;", null, "count", "I");
			service.apply(new EditDtos.Request(null, null, List.of(
					new EditDtos.Operation(EditDtos.Kind.RENAME, cls, "NativeOwner", null, null),
					new EditDtos.Operation(EditDtos.Kind.RENAME, method, "nativeWork", null, null),
					new EditDtos.Operation(EditDtos.Kind.RENAME, field, "nativeCount", null, null),
					new EditDtos.Operation(EditDtos.Kind.SET_COMMENT, cls, null, "class comment", "LINE"),
					new EditDtos.Operation(EditDtos.Kind.SET_COMMENT, method, null, "method comment", "LINE"),
					new EditDtos.Operation(EditDtos.Kind.SET_COMMENT, field, null, "field comment", "LINE"))));
			var before = runtime.projectSnapshot();
			String pending = runtime.pendingEdits().toString();
			var identity = runtime.searchIdentity();
			var nativeHash = FileFingerprint.of(dir.resolve("project.jadx"));
			var mappingHash = FileFingerprint.of(dir.resolve("attached.tiny"));
			String java = runtime.decompiler().searchJavaClassByOrigFullName("probe.EditOwner").getCode();
			var result = new MappingExportService(runtime, stage -> assertCold(runtime)).export(request(runtime, "new.tiny"));
			assertEquals(new MappingExportDtos.Counts(2, 1, 1, 3), result.exported());
			assertEquals(Files.size(dir.resolve("new.tiny")), result.bytes());
			assertEquals(FileFingerprint.of(dir.resolve("new.tiny")).sha256(), result.sha256());
			assertTrue(before.dirty()); assertFalse(result.projectMutated()); assertTrue(result.omissions().isEmpty());
			assertEquals(before, runtime.projectSnapshot()); assertEquals(identity, runtime.searchIdentity());
			assertEquals(pending, runtime.pendingEdits().toString());
			assertEquals(nativeHash, FileFingerprint.of(dir.resolve("project.jadx")));
			assertEquals(mappingHash, FileFingerprint.of(dir.resolve("attached.tiny")));
			try (var fresh = fresh(runtime, dir.resolve("new.tiny"))) {
				assertEquals(java, fresh.searchJavaClassByOrigFullName("probe.EditOwner").getCode());
				assertEquals("probe.MappedA", fresh.searchJavaClassByOrigFullName("probe.UnrelatedA").getFullName());
			}
			assertNoStaging();
		}
	}

	@Test
	void cleanDeclarationExportKeepsAllColdClassesUnprocessedInsideCallback() throws Exception {
		try (var runtime = project(ATTACHED)) {
			var before = runtime.projectSnapshot();
			assertCold(runtime);
			new MappingExportService(runtime, stage -> {
				assertCold(runtime);
				assertEquals(jadx.core.dex.nodes.ProcessState.NOT_LOADED,
						runtime.decompiler().getRoot().resolveRawClass("probe.EditOwner").getState());
			}).export(request(runtime, "cold.tiny"));
			assertFalse(before.dirty()); assertEquals(before, runtime.projectSnapshot()); assertCold(runtime);
		}
	}

	@Test
	void unsupportedNativeEntriesRejectWithoutCreatingOutput() throws Exception {
		try (var runtime = project(ATTACHED)) {
			var cls = JadxNodeRef.forCls("probe.EditOwner");
			var method = new JadxNodeRef(RefType.METHOD, "probe.EditOwner", "work()I");
			for (int i = 0; i < 6; i++) {
				var code = new JadxCodeData();
				switch (i) {
					case 0 -> code.setRenames(List.of(new JadxCodeRename(method, JadxCodeRef.forMthArg(0), "argument")));
					case 1 -> code.setRenames(List.of(new JadxCodeRename(method, JadxCodeRef.forVar(1, 0), "variable")));
					case 2 -> code.setComments(List.of(new JadxCodeComment(cls, "block comment", CommentStyle.BLOCK)));
					case 3 -> code.setRenames(List.of(new JadxCodeRename(JadxNodeRef.forCls("probe.Missing"), "missing")));
					case 4 -> code.setComments(List.of(new JadxCodeComment(cls, "one"), new JadxCodeComment(cls, "two")));
					case 5 -> code.setRenames(List.of(new JadxCodeRename(cls, "NativeOwner"), new JadxCodeRename(cls, "NativeOwner")));
				}
				runtime.replaceCodeData(code, runtime.projectSnapshot().revisions().logicalRevision());
				var before = runtime.projectSnapshot();
				String target = "unsupported-" + i + ".tiny";
				var error = assertThrows(MappingExportDtos.Problem.class, () -> new MappingExportService(runtime).export(request(runtime, target)));
				assertEquals(422, error.status()); assertEquals("UNSUPPORTED_CAPABILITY", error.code());
				assertFalse(Files.exists(dir.resolve(target))); assertEquals(before, runtime.projectSnapshot()); assertNoStaging();
			}
		}
	}

	@Test
	void ambiguousAndUnknownAttachedStructuresNeverBecomeAuthoritative() throws Exception {
		String header = "tiny\t2\t0\toriginal\tmapped\n";
		for (String source : List.of(
				header + "c\tprobe/EditOwner\tprobe/One\nc\tprobe/EditOwner\tprobe/Two\n",
				header + "c\tprobe/EditOwner\tprobe/EditOwner\n\tunknown\tignored\n",
				header + "c\tprobe/EditOwner\tprobe/EditOwner\n\tm\t()I\twork\twork\n\t\tp\t1\targ\talias\n",
				header + "c\tprobe/Missing\tprobe/Missing\n",
				header + "c\tprobe/EditOwner\tprobe/EditOwner\n\tf\tI\tmissing\tmissing\n",
				header + "c\tprobe/EditOwner\tprobe/EditOwner\n\tm\t()I\tmissing\tmissing\n")) {
			try (var runtime = project(source)) {
				var before = runtime.projectSnapshot();
				var error = assertThrows(MappingExportDtos.Problem.class, () -> new MappingExportService(runtime).export(request(runtime, "bad.tiny")));
				assertEquals(422, error.status()); assertEquals(before, runtime.projectSnapshot());
				assertFalse(Files.exists(dir.resolve("bad.tiny"))); assertNoStaging();
			}
		}
	}

	@Test
	void destinationCreationRaceAndExternalMappingChangeDoNotClobberOrPublish() throws Exception {
		try (var runtime = project(ATTACHED)) {
			var before = runtime.projectSnapshot();
			Path target = dir.resolve("race.tiny");
			var conflict = assertThrows(NativeProjectRepository.ExternalModificationException.class,
					() -> new MappingExportService(runtime, stage -> {
						if (stage.equals("PUBLICATION")) {
							Process adversary = new ProcessBuilder("python3", "-c", "import pathlib,sys; pathlib.Path(sys.argv[1]).write_text('adversary bytes')", target.toString()).start();
							assertTrue(adversary.waitFor(5, TimeUnit.SECONDS)); assertEquals(0, adversary.exitValue());
						}
					}).export(request(runtime, "race.tiny")));
			assertEquals("adversary bytes", Files.readString(target)); assertEquals(before, runtime.projectSnapshot()); assertNoStaging();
			assertThrows(NativeProjectRepository.ExternalModificationException.class,
					() -> new MappingExportService(runtime, stage -> {
						if (stage.equals("PUBLICATION")) Files.writeString(dir.resolve("attached.tiny"), ATTACHED + "c\tprobe/UnrelatedB\tprobe/NewB\n");
					}).export(request(runtime, "changed.tiny")));
			assertFalse(Files.exists(dir.resolve("changed.tiny"))); assertEquals(before, runtime.projectSnapshot()); assertNoStaging();
		}
	}

	@Test
	void faultsCleanOnlyOwnedStagingAndReportUncertainCompletedOutput() throws Exception {
		try (var runtime = project(ATTACHED)) {
			var before = runtime.projectSnapshot();
			Path unrelated = dir.resolve("unrelated.tmp"); Files.writeString(unrelated, "retain");
			for (String fault : List.of("ENCODE", "WRITE", "VERIFY", "PUBLICATION", "AFTER_PUBLISH")) {
				String name = "fault-" + fault + ".tiny";
				var error = assertThrows(MappingExportDtos.Problem.class,
						() -> new MappingExportService(runtime, stage -> { if (stage.equals(fault)) throw new IOException("injected secret contents"); })
								.export(request(runtime, name)));
				assertEquals(500, error.status()); assertFalse(error.getMessage().contains("secret"));
				assertEquals(fault.equals("AFTER_PUBLISH"), Files.exists(dir.resolve(name)));
				assertEquals(fault.equals("AFTER_PUBLISH"), error.details().get("published"));
				assertEquals(before, runtime.projectSnapshot()); assertEquals("retain", Files.readString(unrelated)); assertNoStaging();
			}
		}
	}

	@Test
	void unsafePathsExistingTargetsAndStaleRequestsFailBeforeStaging() throws Exception {
		try (var runtime = project(ATTACHED)) {
			Files.writeString(dir.resolve("existing.tiny"), "retain");
			Files.createSymbolicLink(dir.resolve("link.tiny"), dir.resolve("existing.tiny"));
			Files.createSymbolicLink(dir.resolve("parent-link"), dir);
			var service = new MappingExportService(runtime);
			assertThrows(NativeProjectRepository.ExternalModificationException.class, () -> service.export(request(runtime, "existing.tiny")));
			assertThrows(SecurityException.class, () -> service.export(request(runtime, "link.tiny")));
			assertThrows(SecurityException.class, () -> service.export(request(runtime, "parent-link/new.tiny")));
			assertThrows(SecurityException.class, () -> service.export(request(runtime, "attached.tiny")));
			var before = runtime.projectSnapshot();
			assertThrows(NativeProjectRepository.StaleRevisionException.class, () -> service.export(new Request(dir.resolve("stale.tiny"), "TINY_V2", before.revisions().sessionId(), 100)));
			assertEquals("retain", Files.readString(dir.resolve("existing.tiny"))); assertNoStaging();
		}
	}

	@Test
	void fullReturnDescriptorsAndInnerNativeClassAliasesAreVerifiedOnRealEngines() throws Exception {
		Path jar = SymbolFixtureSupport.returnTypeClashJar(dir);
		try (var runtime = new ProjectRuntime(null, List.of(jar), List.of(dir))) {
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			var code = new JadxCodeData();
			code.setRenames(List.of(new JadxCodeRename(new JadxNodeRef(RefType.METHOD, "probe.ReturnClash", "value()I"), "intValue"),
					new JadxCodeRename(new JadxNodeRef(RefType.METHOD, "probe.ReturnClash", "value()Ljava/lang/String;"), "stringValue")));
			runtime.replaceCodeData(code, 0);
			var receipt = new MappingExportService(runtime).export(request(runtime, "return-types.tiny"));
			assertEquals(2, receipt.exported().methods());
			try (var fresh = fresh(runtime, dir.resolve("return-types.tiny"))) {
				var owner = fresh.getRoot().resolveRawClass("probe.ReturnClash");
				assertEquals("intValue", owner.searchMethodByShortId("value()I").getMethodInfo().getAlias());
				assertEquals("stringValue", owner.searchMethodByShortId("value()Ljava/lang/String;").getMethodInfo().getAlias());
			}
		}
		jar = SymbolFixtureSupport.compileFixture(dir);
		try (var runtime = new ProjectRuntime(null, List.of(jar), List.of(dir))) {
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			var code = new JadxCodeData();
			code.setRenames(List.of(new JadxCodeRename(JadxNodeRef.forCls("probe.SymbolFixture$Inner"), "NativeInner")));
			runtime.replaceCodeData(code, 0);
			new MappingExportService(runtime).export(request(runtime, "inner.tiny"));
			try (var fresh = fresh(runtime, dir.resolve("inner.tiny"))) {
				assertEquals("NativeInner", fresh.getRoot().resolveRawClass("probe.SymbolFixture$Inner").getClassInfo().getAliasShortName());
			}
		}
	}

	@Test
	void entryAndConservativeMemoryCeilingsAreCheckedBeforeCopyingOrCreatingFiles() throws Exception {
		try (var runtime = project(ATTACHED)) {
			var code = new JadxCodeData();
			code.setComments(java.util.Collections.nCopies(10_001, new JadxCodeComment(JadxNodeRef.forCls("probe.EditOwner"), "entry")));
			runtime.replaceCodeData(code, 0);
			var error = assertThrows(MappingExportDtos.Problem.class, () -> new MappingExportService(runtime).export(request(runtime, "entries.tiny")));
			assertEquals(429, error.status()); assertFalse(Files.exists(dir.resolve("entries.tiny")));
			code.setComments(java.util.Collections.nCopies(100, new JadxCodeComment(JadxNodeRef.forCls("probe.EditOwner"), "x".repeat(16_384))));
			runtime.replaceCodeData(code, runtime.projectSnapshot().revisions().logicalRevision());
			error = assertThrows(MappingExportDtos.Problem.class, () -> new MappingExportService(runtime).export(request(runtime, "memory.tiny")));
			assertEquals(429, error.status()); assertFalse(Files.exists(dir.resolve("memory.tiny"))); assertNoStaging();
		}
	}

	@Test
	void activeExportRejectsConcurrentLifecycleOperationsPromptlyAndReleasesItsLease() throws Exception {
		var entered = new java.util.concurrent.CountDownLatch(1);
		var release = new java.util.concurrent.CountDownLatch(1);
		try (var runtime = project(ATTACHED)) {
			Request request = request(runtime, "concurrent.tiny");
			var exported = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
				try {
					return new MappingExportService(runtime, stage -> {
						if (stage.equals("ENCODE")) { entered.countDown(); assertTrue(release.await(10, TimeUnit.SECONDS)); }
					}).export(request);
				} catch (Exception failure) { throw new RuntimeException(failure); }
			});
			try {
				assertTrue(entered.await(5, TimeUnit.SECONDS));
				var competing = java.util.concurrent.CompletableFuture.runAsync(() -> {
					assertThrows(ProjectBusyException.class, () -> runtime.saveProject(null, null));
					assertThrows(ProjectBusyException.class, () -> runtime.updateMappingsPath(null, request.expectedSessionId(), request.expectedLogicalRevision()));
					assertThrows(ProjectBusyException.class, () -> new EditBatchService(runtime).apply(new EditDtos.Request(null, null,
							List.of(new EditDtos.Operation(EditDtos.Kind.RENAME, SymbolRef.classRef("Lprobe/EditOwner;"), "BusyOwner", null, null)))));
					var rejected = assertThrows(ProjectRuntime.ShutdownRejectedException.class, () -> runtime.requestShutdown(ShutdownPolicy.DISCARD));
					assertEquals(409, rejected.statusCode());
				});
				competing.get(2, TimeUnit.SECONDS);
			} finally { release.countDown(); }
			exported.get(10, TimeUnit.SECONDS);
			assertEquals("READY", runtime.status().state()); assertFalse(runtime.projectSnapshot().dirty());
		} finally { release.countDown(); }
	}

	@Test
	void incompatibleLeaseReturnsPromptBusyAndOversizeNativeDataNeverCreatesOutput() throws Exception {
		try (var runtime = project(ATTACHED)) {
			Request busyRequest = request(runtime, "busy.tiny");
			runtime.withExclusiveEdit(context -> {
				assertThrows(ProjectBusyException.class, () -> new MappingExportService(runtime).export(busyRequest));
				return null;
			});
			var code = new JadxCodeData();
			code.setComments(List.of(new JadxCodeComment(JadxNodeRef.forCls("probe.EditOwner"), "x".repeat(16_385))));
			runtime.replaceCodeData(code, 0);
			var error = assertThrows(MappingExportDtos.Problem.class, () -> new MappingExportService(runtime).export(request(runtime, "large.tiny")));
			assertEquals(429, error.status()); assertFalse(Files.exists(dir.resolve("large.tiny"))); assertNoStaging();
		}
	}

	private ProjectRuntime project(String mappings) throws Exception {
		Path jar = SymbolFixtureSupport.compileEditFixture(dir);
		Path attached = dir.resolve("attached.tiny"); Files.writeString(attached, mappings);
		Path nativePath = dir.resolve("project.jadx");
		var document = NativeProjectDocument.newFromInputs(nativePath, List.of(jar)).withMappingsPath(attached);
		document.save();
		ProjectRuntime runtime = new ProjectRuntime(nativePath, List.of(jar), List.of(dir));
		try { runtime.initializeAsync(document).get(20, TimeUnit.SECONDS); return runtime; }
		catch (Exception failure) { runtime.close(); throw failure; }
	}
	private Request request(ProjectRuntime runtime, String name) {
		var revisions = runtime.projectSnapshot().revisions();
		return new Request(dir.resolve(name), "TINY_V2", revisions.sessionId(), revisions.logicalRevision());
	}
	private static JadxDecompiler fresh(ProjectRuntime runtime, Path mapping) {
		JadxArgs args = new JadxArgs(); args.setUserRenamesMappingsPath(mapping);
		runtime.projectSnapshot().inputs().forEach(path -> args.getInputFiles().add(path.toFile()));
		var engine = new JadxDecompiler(args); engine.load(); return engine;
	}
	private static void assertCold(ProjectRuntime runtime) {
		for (String cls : List.of("probe.UnrelatedA", "probe.UnrelatedB")) {
			assertEquals(jadx.core.dex.nodes.ProcessState.NOT_LOADED, runtime.decompiler().getRoot().resolveRawClass(cls).getState(), cls);
		}
	}
	private void assertNoStaging() throws IOException {
		try (var paths = Files.list(dir)) { assertTrue(paths.noneMatch(path -> path.getFileName().toString().startsWith(".libjadx-mapping-"))); }
	}
}
