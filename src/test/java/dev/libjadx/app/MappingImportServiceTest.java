package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import dev.libjadx.core.edits.EditDtos;
import dev.libjadx.core.mappings.MappingExportDtos;
import dev.libjadx.core.mappings.MappingImportDtos;
import dev.libjadx.core.mappings.MappingImportDtos.Request;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.project.FileFingerprint;
import dev.libjadx.project.NativeProjectDocument;
import dev.libjadx.project.NativeProjectRepository;
import dev.libjadx.scheduler.ProjectBusyException;
import jadx.api.JadxDecompiler;
import jadx.api.data.CommentStyle;
import jadx.api.data.impl.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MappingImportServiceTest {
	@TempDir Path dir;
	static final String HEADER = "tiny\t2\t0\toriginal\tmapped\n";
	static final String HAPPY = HEADER + "c\tprobe/EditOwner\tprobe/ImportedOwner\n\tc\timported class\n"
			+ "\tm\t()I\twork\timportedWork\n\t\tc\timported method\n"
			+ "\tf\tI\tcount\timportedCount\n\t\tc\timported field\n";

	@Test void liveEditsCommitOnceIdempotentlyThenSaveReopenAndDiscard() throws Exception {
		try (var runtime = project(null)) {
			Path input = write(HAPPY);
			var sourceHash = FileFingerprint.of(input); var projectHash = FileFingerprint.of(dir.resolve("project.jadx"));
			var cold = runtime.decompiler().getRoot().resolveRawClass("probe.UnrelatedA").getState();
			String oldSource = source(runtime);
			var identity = runtime.searchIdentity();
			var receipt = new MappingImportService(runtime).importMappings(request(runtime, input));
			assertEquals("APPLIED", receipt.outcome()); assertEquals(new MappingImportDtos.EditCounts(3, 3), receipt.applied());
			assertEquals(new MappingExportDtos.Counts(1, 1, 1, 3), receipt.parsed());
			assertEquals(0, receipt.beforeLogicalRevision()); assertEquals(1, receipt.afterLogicalRevision());
			assertEquals(0, receipt.beforeIndexRevision()); assertEquals(1, receipt.afterIndexRevision());
			assertTrue(receipt.dirty()); assertFalse(receipt.saved()); assertFalse(receipt.mappingAttached()); assertTrue(receipt.omissions().isEmpty());
			assertEquals(sourceHash.sha256(), receipt.sha256()); assertEquals(sourceHash, FileFingerprint.of(input));
			assertEquals(projectHash, FileFingerprint.of(dir.resolve("project.jadx"))); assertNull(runtime.decompiler().getArgs().getUserRenamesMappingsPath());
			assertNotEquals(identity, runtime.searchIdentity());
			String code = source(runtime); assertNotEquals(oldSource, code);
			for (String value : List.of("ImportedOwner", "importedWork", "importedCount", "// imported class", "// imported method", "// imported field")) assertTrue(code.contains(value), code);
			assertEquals(cold, runtime.decompiler().getRoot().resolveRawClass("probe.UnrelatedA").getState());
			var before = runtime.projectSnapshot(); var stableIdentity = runtime.searchIdentity(); var pending = runtime.pendingEdits();
			var noop = new MappingImportService(runtime).importMappings(request(runtime, input));
			assertEquals("NO_CHANGE", noop.outcome()); assertEquals(new MappingImportDtos.EditCounts(0, 0), noop.applied());
			assertEquals(new MappingImportDtos.EditCounts(3, 3), noop.unchanged());
			assertEquals(before, runtime.projectSnapshot()); assertSame(stableIdentity, runtime.searchIdentity()); assertEquals(pending, runtime.pendingEdits());
			assertEquals("NO_CHANGE", new MappingImportService(runtime).importMappings(request(runtime, write(HEADER))).outcome());
			assertEquals(before, runtime.projectSnapshot());
			runtime.reloadProject(true, before.revisions().sessionId(), before.revisions().logicalRevision());
			assertFalse(runtime.projectSnapshot().dirty()); assertEquals(oldSource, source(runtime));
			write(HAPPY); new MappingImportService(runtime).importMappings(request(runtime, input));
			runtime.saveProject(null, null); assertFalse(runtime.projectSnapshot().dirty());
			var saved = NativeProjectDocument.open(dir.resolve("project.jadx")); assertEquals(3, saved.getCodeData().getRenames().size());
			assertEquals(3, saved.getCodeData().getComments().size()); assertNull(saved.getMappingsPath());
			try (var fresh = new ProjectRuntime(saved.getProjectPath(), saved.getInputFiles(), List.of(dir))) {
				fresh.initializeAsync(saved).get(20, TimeUnit.SECONDS); assertEquals(code, source(fresh));
			}
		}
	}

	@Test void attachedAliasesAndAdditiveSuffixRoundTripWithoutReplacingNativeEdits() throws Exception {
		String attached = HEADER + "c\tprobe/EditOwner\tprobe/AttachedOwner\n\tc\tattached class\n"
				+ "\tm\t()I\twork\tattachedWork\n\t\tc\tattached method\n\tf\tI\tcount\tattachedCount\n\t\tc\tattached field\n";
		try (var runtime = project(attached)) {
			var before = runtime.projectSnapshot();
			assertEquals("NO_CHANGE", new MappingImportService(runtime).importMappings(request(runtime, write(attached))).outcome());
			assertEquals(before, runtime.projectSnapshot());
			// Native aliases override attached mappings; unrelated dirty state must be retained.
			new EditBatchService(runtime).apply(new EditDtos.Request(null, null, List.of(new EditDtos.Operation(EditDtos.Kind.RENAME,
					SymbolRef.classRef("Lprobe/UnrelatedA;"), "UnsavedA", null, null))));
			String incoming = attached;
			for (String kind : List.of("class", "method", "field")) incoming = incoming.replace("attached " + kind + "\n", "attached " + kind + "\\nnative " + kind + "\n");
			var receipt = new MappingImportService(runtime).importMappings(request(runtime, write(incoming)));
			assertEquals(new MappingImportDtos.EditCounts(0, 3), receipt.applied()); assertEquals(new MappingImportDtos.EditCounts(3, 0), receipt.unchanged());
			String code = source(runtime);
			for (String kind : List.of("class", "method", "field")) { assertTrue(code.contains("// attached " + kind)); assertTrue(code.contains("// native " + kind)); }
			assertEquals("UnsavedA", runtime.decompiler().getRoot().resolveRawClass("probe.UnrelatedA").getAlias());
			Path export = dir.resolve("exported.tiny"); var rev = runtime.projectSnapshot().revisions();
			new MappingExportService(runtime).export(new MappingExportDtos.Request(export, "TINY_V2", rev.sessionId(), rev.logicalRevision()));
			assertEquals("NO_CHANGE", new MappingImportService(runtime).importMappings(request(runtime, export)).outcome());
			assertEquals("NO_CHANGE", new MappingImportService(runtime).importMappings(request(runtime, write(incoming))).outcome());
			assertRejectsWithoutChange(runtime, attached.replace("AttachedOwner", "DifferentOwner"), 409);
			assertRejectsWithoutChange(runtime, incoming.replace("native class", "changed suffix"), 409);
			assertRejectsWithoutChange(runtime, attached.replace("attached class", "different comment"), 409);
			runtime.saveProject(null, null);
			assertEquals(dir.resolve("attached.tiny"), NativeProjectDocument.open(dir.resolve("project.jadx")).getMappingsPath());
			var reopened = NativeProjectDocument.open(dir.resolve("project.jadx"));
			try (var fresh = new ProjectRuntime(reopened.getProjectPath(), reopened.getInputFiles(), List.of(dir))) {
				fresh.initializeAsync(reopened).get(20, TimeUnit.SECONDS); assertEquals(code, source(fresh));
			}
		}
	}

	@Test void nativeOverAttachedAliasMatchingIsNoopDifferingAliasConflicts() throws Exception {
		try (var runtime = project(HEADER + "c\tprobe/EditOwner\tprobe/AttachedOwner\n")) {
			new EditBatchService(runtime).apply(new EditDtos.Request(null, null, List.of(new EditDtos.Operation(EditDtos.Kind.RENAME,
					SymbolRef.classRef("Lprobe/EditOwner;"), "NativeOwner", null, null))));
			assertEquals("NO_CHANGE", new MappingImportService(runtime).importMappings(request(runtime,
					write(HEADER + "c\tprobe/EditOwner\tprobe/NativeOwner\n"))).outcome());
			assertRejectsWithoutChange(runtime, HEADER + "c\tprobe/EditOwner\tprobe/AttachedOwner\n", 409);
		}
	}

	@Test void equivalentQualifiedNativeClassAliasIsUnchanged() throws Exception {
		try (var runtime = project(null)) {
			var code = new JadxCodeData();
			code.setRenames(List.of(new JadxCodeRename(JadxNodeRef.forCls("probe.EditOwner"), "probe.NativeOwner")));
			runtime.replaceCodeData(code, 0); var before = runtime.projectSnapshot(); var identity = runtime.searchIdentity();
			assertEquals("NO_CHANGE", new MappingImportService(runtime).importMappings(request(runtime,
					write(HEADER + "c\tprobe/EditOwner\tprobe/NativeOwner\n"))).outcome());
			assertEquals(before, runtime.projectSnapshot()); assertSame(identity, runtime.searchIdentity());
		}
	}

	@ParameterizedTest @ValueSource(strings = {"", "unknown", "duplicate", "duplicate-comment", "extra-ns", "inverted", "metadata", "arg", "local", "orphan", "empty-comment", "multiline", "bad-name", "move", "missing-class", "missing-method", "wrong-field-type", "constructor", "class-collision", "method-collision", "field-collision", "incoming-collision"})
	void malformedUnsupportedUnresolvedOrConflictingInputRejectsWholePlan(String scenario) throws Exception {
		try (var runtime = project(null)) {
			String cls = "c\tprobe/EditOwner\tprobe/EditOwner\n";
			String body = switch (scenario) {
				case "" -> "";
				case "unknown" -> HEADER + cls + "\tunknown\tignored\n";
				case "duplicate" -> HEADER + cls + cls;
				case "duplicate-comment" -> HEADER + cls + "\tc\tfirst\n\tc\tsecond\n";
				case "extra-ns" -> "tiny\t2\t0\toriginal\tfirst\tsecond\n";
				case "inverted" -> "tiny\t2\t0\tmapped\toriginal\n" + cls;
				case "metadata" -> HEADER + "\tcustom-option\tdata\n" + cls;
				case "arg" -> HEADER + cls + "\tm\t()I\twork\twork\n\t\tp\t1\tx\ty\n";
				case "local" -> HEADER + cls + "\tm\t()I\twork\twork\n\t\tv\t1\t0\t0\tx\ty\n";
				case "orphan" -> HEADER + "\tm\t()I\twork\twork\n";
				case "empty-comment" -> HEADER + cls + "\tc\t\n";
				case "multiline" -> HEADER + cls + "\tc\tfirst\\nsecond\n";
				case "bad-name" -> HEADER + cls + "\tm\t()I\twork\tbad-name\n";
				case "move" -> HEADER + "c\tprobe/EditOwner\tother/Owner\n";
				case "missing-class" -> HEADER + cls.replace("EditOwner", "Absent");
				case "missing-method" -> HEADER + cls + "\tm\t()I\tabsent\talias\n";
				case "wrong-field-type" -> HEADER + cls + "\tf\tJ\tcount\talias\n";
				case "constructor" -> HEADER + cls + "\tm\t()V\t<init>\tctor\n";
				case "class-collision" -> HEADER + cls.replace("probe/EditOwner\n", "probe/UnrelatedA\n");
				case "method-collision" -> HEADER + cls + "\tm\t()I\twork\totherWork\n";
				case "field-collision" -> HEADER + cls + "\tf\tI\tcount\totherCount\n";
				case "incoming-collision" -> HEADER + cls + "\tm\t()I\twork\tsameName\n\tm\t()I\totherWork\tsameName\n";
				default -> throw new AssertionError(scenario);
			};
			int expected = scenario.startsWith("missing") || scenario.equals("wrong-field-type") ? 404 : scenario.contains("collision") ? 409 : 422;
			assertRejectsWithoutChange(runtime, body, expected);
		}
	}

	@Test void exactReturnTypeKeysAndSyntheticBridgeSpecialRestrictions() throws Exception {
		Path jar = SymbolFixtureSupport.returnTypeClashJar(dir);
		try (var runtime = raw(jar)) {
			var cls = runtime.decompiler().getRoot().resolveRawClass("probe.ReturnClash");
			String autoAlias = cls.searchMethodByShortId("value()Ljava/lang/String;").getMethodInfo().getAlias();
			assertRejectsWithoutChange(runtime, HEADER + "c\tprobe/ReturnClash\tprobe/ReturnClash\n\tm\t()I\tvalue\tintValue\n\tm\t()Ljava/lang/String;\tvalue\tstringValue\n", 409);
			String input = HEADER + "c\tprobe/ReturnClash\tprobe/ReturnClash\n\tm\t()I\tvalue\tintValue\n\tm\t()Ljava/lang/String;\tvalue\t" + autoAlias + "\n";
			var receipt = new MappingImportService(runtime).importMappings(request(runtime, write(input)));
			assertEquals(1, receipt.applied().aliases()); assertEquals(2, receipt.unchanged().aliases());
			var pending = runtime.pendingEdits().getAsJsonObject("codeData").getAsJsonArray("renames");
			assertEquals("value()I", pending.get(0).getAsJsonObject().getAsJsonObject("nodeRef").get("shortId").getAsString());
			assertEquals(autoAlias, cls.searchMethodByShortId("value()Ljava/lang/String;").getMethodInfo().getAlias());
		}
		Path synthetic = SymbolFixtureSupport.syntheticEmptyJar(dir);
		try (var runtime = raw(synthetic)) { assertRejectsWithoutChange(runtime, HEADER + "c\tprobe/EmptySynthetic\tprobe/Alias\n", 422); }
	}

	@Test void zeroByteAttachedRegressionAndMalformedUtf8AndBudgets() throws Exception {
		try (var runtime = project("")) { assertRejectsWithoutChange(runtime, HAPPY, 422); }
		try (var runtime = project(null)) {
			var input = dir.resolve("incoming.tiny"); Files.write(input, new byte[]{(byte)0xc3, (byte)0x28});
			var error = assertThrows(MappingImportDtos.Problem.class, () -> new MappingImportService(runtime).importMappings(request(runtime, input)));
			assertEquals(422, error.status()); assertEquals(0, runtime.projectSnapshot().revisions().logicalRevision());
			for (String oversized : List.of(HEADER + "c\tprobe/EditOwner\tprobe/EditOwner\n\tc\t" + "x".repeat(16_385) + "\n",
					HEADER + "c\tprobe/EditOwner\tprobe/EditOwner\n\tc\t" + "x".repeat(4 * 1024 * 1024) + "\n",
					HEADER + "c\tx/A\tx/A\n".repeat(10_002))) {
				assertRejectsWithoutChange(runtime, oversized, 429);
			}
			StringBuilder memory = new StringBuilder(HEADER);
			for (int i = 0; i < 80; i++) memory.append("c\tx/A").append(i).append("\tx/A").append(i).append("\n\tc\t").append("x".repeat(16_384)).append('\n');
			assertRejectsWithoutChange(runtime, memory.toString(), 429);
		}
	}

	@Test void sourceSeparateProcessRewriteOrReplacementIsDetectedBeforeCommit() throws Exception {
		try (var runtime = project(null)) {
			for (String mutation : List.of("rewrite", "replace", "same-mtime")) {
				Path input = write(HAPPY); var before = runtime.projectSnapshot(); var pending = runtime.pendingEdits(); var identity = runtime.searchIdentity();
				var service = new MappingImportService(runtime, stage -> {
					if (stage.equals("BEFORE_COMMIT")) {
						String program = "import os,sys,pathlib; p=pathlib.Path(sys.argv[1]); old=p.stat(); "
								+ (mutation.equals("replace") ? "q=p.with_suffix('.replacement'); q.write_text(sys.argv[2]); q.replace(p)" : "p.write_text(sys.argv[2])")
								+ (mutation.equals("same-mtime") ? "; os.utime(p, ns=(old.st_atime_ns,old.st_mtime_ns))" : "");
						assertEquals(0, new ProcessBuilder("python3", "-c", program, input.toString(), HAPPY.replace("imported class", "modified class")).inheritIO().start().waitFor());
					}
				});
				assertThrows(NativeProjectRepository.ExternalModificationException.class, () -> service.importMappings(request(runtime, input)));
				assertEquals(before, runtime.projectSnapshot()); assertEquals(pending, runtime.pendingEdits()); assertSame(identity, runtime.searchIdentity());
			}
		}
	}

	@Test void externalNativeOrAttachedModificationAndStaleBusyPreserveState() throws Exception {
		try (var runtime = project(HEADER)) {
			Path input = write(HAPPY); var before = runtime.projectSnapshot(); var rev = before.revisions();
			assertThrows(NativeProjectRepository.StaleRevisionException.class, () -> new MappingImportService(runtime).importMappings(new Request(input, "TINY_V2", "MERGE_FAIL_ON_CONFLICT", UUID.randomUUID().toString(), 0)));
			assertThrows(NativeProjectRepository.StaleRevisionException.class, () -> new MappingImportService(runtime).importMappings(new Request(input, "TINY_V2", "MERGE_FAIL_ON_CONFLICT", rev.sessionId(), 100)));
			runtime.withExclusiveEdit(context -> { assertThrows(ProjectBusyException.class, () -> new MappingImportService(runtime).importMappings(request(runtime, input))); return null; });
			for (String nativeFile : List.of("project.jadx", "attached.tiny")) {
				Path path = dir.resolve(nativeFile); byte[] accepted = Files.readAllBytes(path);
				assertThrows(NativeProjectRepository.ExternalModificationException.class, () -> new MappingImportService(runtime, stage -> {
					if (stage.equals("BEFORE_COMMIT")) Files.writeString(path, Files.readString(path) + "\n ");
				}).importMappings(request(runtime, input)));
				assertEquals(before, runtime.projectSnapshot()); Files.write(path, accepted);
			}
			new MappingImportService(runtime).importMappings(request(runtime, input));
			Files.writeString(dir.resolve("attached.tiny"), HEADER + "\n");
			assertThrows(NativeProjectRepository.ExternalModificationException.class, () -> runtime.saveProject(null, null));
			assertTrue(runtime.projectSnapshot().dirty());
		}
	}

	@Test void stagingFailureAfterAStagedChangeNeverCommitsCleanOrDirtyProject() throws Exception {
		try (var runtime = project(null)) {
			for (boolean dirty : List.of(false, true)) {
				if (dirty) new EditBatchService(runtime).apply(new EditDtos.Request(null, null, List.of(new EditDtos.Operation(EditDtos.Kind.RENAME, SymbolRef.classRef("Lprobe/UnrelatedA;"), "RetainedA", null, null))));
				var before = runtime.projectSnapshot(); var identity = runtime.searchIdentity(); var pending = runtime.pendingEdits();
				assertThrows(IllegalStateException.class, () -> new MappingImportService(runtime, stage -> {
					if (stage.equals("STAGE_1")) throw new IllegalStateException("injected private staging failure");
				}).importMappings(request(runtime, write(HAPPY))));
				assertEquals(before, runtime.projectSnapshot()); assertSame(identity, runtime.searchIdentity()); assertEquals(pending, runtime.pendingEdits());
				assertEquals("READY", runtime.status().state());
			}
		}
	}

	@Test void postReplacementReplayFailureUsesFailedLifecycleRatherThanRollback() throws Exception {
		Path jar = SymbolFixtureSupport.compileEditFixture(dir); AtomicInteger replays = new AtomicInteger();
		try (var runtime = new ProjectRuntime(null, List.of(jar), args -> new ProjectRuntime.ProjectEngine() {
			final JadxDecompiler engine = new JadxDecompiler(args);
			public void load() { engine.load(); } public JadxDecompiler decompiler() { return engine; }
			public void reloadCodeData(JadxCodeData data) { replays.incrementAndGet(); throw new IllegalStateException("injected replay failure"); }
			public void close() { engine.close(); }
		}, () -> { })) {
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			assertThrows(IllegalStateException.class, () -> new MappingImportService(runtime).importMappings(request(runtime, write(HAPPY))));
			assertEquals(1, replays.get()); assertEquals("FAILED", runtime.status().state());
		}
	}

	@Test void preservesUnknownNativeJsonAndNonLineCommentsOnAliasOnlyImport() throws Exception {
		try (var ignored = project(null)) { }
		Path nativePath = dir.resolve("project.jadx");
		var json = com.google.gson.JsonParser.parseString(Files.readString(nativePath)).getAsJsonObject();
		json.addProperty("futureField", "preserved"); json.getAsJsonObject("codeData").addProperty("futureCodeData", 12);
		var document = NativeProjectDocument.open(nativePath); var data = document.getCodeData();
		data.setComments(List.of(new JadxCodeComment(JadxNodeRef.forCls("probe.EditOwner"), "retained block", CommentStyle.BLOCK)));
		document.setCodeData(data); var codeData = document.toJsonTree().get("codeData").getAsJsonObject(); codeData.addProperty("futureCodeData", 12);
		codeData.getAsJsonArray("comments").get(0).getAsJsonObject().addProperty("futureComment", true); json.add("codeData", codeData);
		Files.writeString(nativePath, json.toString()); document = NativeProjectDocument.open(nativePath);
		try (var runtime = new ProjectRuntime(nativePath, document.getInputFiles(), List.of(dir))) {
			runtime.initializeAsync(document).get(20, TimeUnit.SECONDS);
			new MappingImportService(runtime).importMappings(request(runtime, write(HEADER + "c\tprobe/EditOwner\tprobe/ImportedOwner\n")));
			assertRejectsWithoutChange(runtime, HEADER + "c\tprobe/EditOwner\tprobe/ImportedOwner\n\tc\tnew line\n", 422);
			runtime.saveProject(null, null);
			var saved = NativeProjectDocument.open(nativePath).toJsonTree(); assertEquals("preserved", saved.get("futureField").getAsString());
			assertEquals(12, saved.getAsJsonObject("codeData").get("futureCodeData").getAsInt());
			assertTrue(saved.getAsJsonObject("codeData").getAsJsonArray("comments").get(0).getAsJsonObject().get("futureComment").getAsBoolean());
			assertEquals(CommentStyle.BLOCK, NativeProjectDocument.open(nativePath).getCodeData().getComments().getFirst().getStyle());
		}
	}

	@Test void activeImportAdmissionRejectsCompetingEditsSaveReloadSettingsAndShutdown() throws Exception {
		try (var runtime = project(null)) {
			Path input = write(HAPPY);
			var captured = new java.util.concurrent.CountDownLatch(1);
			var release = new java.util.concurrent.CountDownLatch(1);
			var request = request(runtime, input);
			var task = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
				try { return new MappingImportService(runtime, stage -> {
					if (stage.equals("CAPTURED")) { captured.countDown(); assertTrue(release.await(15, TimeUnit.SECONDS)); }
				}).importMappings(request); } catch (Exception error) { throw new RuntimeException(error); }
			});
			try {
				assertTrue(captured.await(5, TimeUnit.SECONDS));
				assertThrows(ProjectBusyException.class, () -> new MappingImportService(runtime).importMappings(request));
				assertThrows(ProjectBusyException.class, () -> runtime.saveProject(null, null));
				assertThrows(ProjectBusyException.class, () -> runtime.reloadProject(true, request.expectedSessionId(), request.expectedLogicalRevision()));
				assertThrows(ProjectBusyException.class, () -> runtime.updateMappingsPath(null, request.expectedSessionId(), request.expectedLogicalRevision()));
				assertThrows(ProjectBusyException.class, () -> new EditBatchService(runtime).apply(new EditDtos.Request(null, null,
						List.of(new EditDtos.Operation(EditDtos.Kind.RENAME, SymbolRef.classRef("Lprobe/EditOwner;"), "BusyAlias", null, null)))));
				assertEquals(409, assertThrows(ProjectRuntime.ShutdownRejectedException.class, () -> runtime.requestShutdown(ShutdownPolicy.DISCARD)).statusCode());
			} finally { release.countDown(); }
			assertEquals("APPLIED", task.get(15, TimeUnit.SECONDS).outcome()); assertEquals("READY", runtime.status().state());
		}
	}

	@Test void ambiguousNativeKeysDuplicateInputVisibilityAndBridgeTargetsRemainExplicit() throws Exception {
		try (var runtime = project(null)) {
			var code = new JadxCodeData(); var key = JadxNodeRef.forCls("probe.EditOwner");
			code.setRenames(List.of(new JadxCodeRename(key, "NativeA"), new JadxCodeRename(key, "NativeB")));
			runtime.replaceCodeData(code, 0);
			assertRejectsWithoutChange(runtime, HEADER + "c\tprobe/EditOwner\tprobe/NativeB\n", 422);
			code.setRenames(List.of()); code.setComments(List.of(new JadxCodeComment(key, "first"), new JadxCodeComment(key, "second")));
			runtime.replaceCodeData(code, runtime.projectSnapshot().revisions().logicalRevision());
			assertRejectsWithoutChange(runtime, HEADER + "c\tprobe/EditOwner\tprobe/EditOwner\n\tc\tfirst\n", 422);
		}
		Path first = SymbolFixtureSupport.duplicateJar(dir, "first", 1), second = SymbolFixtureSupport.duplicateJar(dir, "second", 2);
		try (var runtime = new ProjectRuntime(null, List.of(first, second), List.of(dir))) {
			runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
			assertEquals(1, runtime.decompiler().getClassesWithInners().stream().filter(cls -> cls.getRawName().equals("duplicate.Clash")).count());
			// The engine collapsed the original definitions. Import is scoped to Jadx-visible keys,
			// and makes no per-input identity guarantee; the existing resolve API reports that limit.
			var result = new MappingImportService(runtime).importMappings(request(runtime, write(HEADER + "c\tduplicate/Clash\tduplicate/Clash\n")));
			assertEquals("NO_CHANGE", result.outcome());
		}
		Path source = dir.resolve("GenericChild.java");
		Files.writeString(source, "package probe; class GenericBase<T> { public T value() { return null; } } "
				+ "class GenericChild extends GenericBase<String> { public String value() { return \"x\"; } }");
		Path jar = SymbolFixtureSupport.compile(dir.resolve("bridge"), source, "bridge.jar");
		try (var runtime = raw(jar)) {
			assertRejectsWithoutChange(runtime, HEADER + "c\tprobe/GenericChild\tprobe/GenericChild\n\tm\t()Ljava/lang/Object;\tvalue\tbridgeAlias\n", 422);
		}
	}

	private void assertRejectsWithoutChange(ProjectRuntime runtime, String input, int status) throws Exception {
		var before = runtime.projectSnapshot(); var pending = runtime.pendingEdits(); var identity = runtime.searchIdentity();
		Path path = write(input); var hash = FileFingerprint.of(path);
		var error = assertThrows(MappingImportDtos.Problem.class, () -> new MappingImportService(runtime).importMappings(request(runtime, path)));
		assertEquals(status, error.status(), error.details().toString()); assertEquals(before, runtime.projectSnapshot());
		assertEquals(pending, runtime.pendingEdits()); assertSame(identity, runtime.searchIdentity()); assertEquals(hash, FileFingerprint.of(path));
	}
	private ProjectRuntime project(String mapping) throws Exception {
		Path jar = SymbolFixtureSupport.compileEditFixture(dir); Path path = dir.resolve("project.jadx");
		var document = NativeProjectDocument.newFromInputs(path, List.of(jar));
		if (mapping != null) { Path attached = dir.resolve("attached.tiny"); Files.writeString(attached, mapping); document = document.withMappingsPath(attached); }
		document.save(); var runtime = new ProjectRuntime(path, List.of(jar), List.of(dir)); runtime.initializeAsync(document).get(20, TimeUnit.SECONDS); return runtime;
	}
	private ProjectRuntime raw(Path jar) throws Exception { var runtime = new ProjectRuntime(null, List.of(jar), List.of(dir)); runtime.initializeAsync(null).get(20, TimeUnit.SECONDS); return runtime; }
	private Path write(String text) throws Exception { Path path = dir.resolve("incoming.tiny"); Files.writeString(path, text); return path; }
	static Request request(ProjectRuntime runtime, Path path) { var rev = runtime.projectSnapshot().revisions(); return new Request(path, "TINY_V2", "MERGE_FAIL_ON_CONFLICT", rev.sessionId(), rev.logicalRevision()); }
	private static String source(ProjectRuntime runtime) { return runtime.withPrimaryClassRead("owner", engine -> engine.searchJavaClassByOrigFullName("probe.EditOwner").getCode()); }
}
