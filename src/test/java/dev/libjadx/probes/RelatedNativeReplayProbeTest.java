package dev.libjadx.probes;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import dev.libjadx.core.EffectiveAnalysisConfig;
import dev.libjadx.jadxadapter.JadxEngineFactory;
import dev.libjadx.jadxadapter.JadxNativeEditAdapter;
import dev.libjadx.project.NativeProjectDocument;
import jadx.api.JadxDecompiler;
import jadx.api.data.impl.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Diagnostic native strategies, deliberately bypassing the disabled propagation endpoint. */
class RelatedNativeReplayProbeTest {
	@TempDir Path dir;

	@Test void oneSeedAndPerOriginalMemberStrategiesSaveAndReplayDifferentActualAliases() throws Exception {
		prepare(dir.resolve("seed"), false);
		prepare(dir.resolve("members"), true);
	}

	@Test void prepareActualGuiCounterexamples() throws Exception {
		prepare(Path.of("build/related-gui-fixture/seed").toAbsolutePath(), false);
		prepare(Path.of("build/related-gui-fixture/members").toAbsolutePath(), true);
	}

	@Test void guiRenameBuilderDropsCandidateDeclarationRecordsButKeepsOmittedBranchAndScopedKeys() throws Exception {
		try (var engine = RelatedFixture.open(RelatedFixture.compile(dir))) {
			var seed = RelatedFixture.method(engine, RelatedFixture.ref("Joined", "joined", "(I)I"));
			var records = new java.util.HashSet<jadx.api.data.ICodeRename>();
			for (String owner : List.of("SeparateLeft", "ExtendedLeft", "Joined", "SeparateRight"))
				records.add(new JadxCodeRename(JadxNodeRef.forMth(RelatedFixture.method(engine,
						RelatedFixture.ref(owner, "joined", "(I)I"))), "old" + owner));
			var scoped = new JadxCodeRename(JadxNodeRef.forMth(seed), JadxCodeRef.forMthArg(0), "parameter");
			var guiVar = new JadxCodeRename(JadxNodeRef.forMth(seed), JadxCodeRef.forVar(1, 0), "guiParameter");
			records.add(scoped); records.add(guiVar);
			var node = new jadx.gui.treemodel.JMethod(seed, null);
			var built = node.buildCodeRename("newJoined", records);
			assertEquals(JadxNodeRef.forMth(seed), built.getNodeRef());
			assertEquals("newJoined", built.getNewName());
			assertEquals(3, records.size()); assertTrue(records.contains(scoped)); assertTrue(records.contains(guiVar));
			assertTrue(records.stream().anyMatch(r -> r.getCodeRef() == null && r.getNewName().equals("oldSeparateRight")));
		}
	}

	@Test void differingMemberRecordsAndDuplicateKeysDoNotSupplyConflictOrCompletenessGuarantees() throws Exception {
		var inputs = RelatedFixture.compile(dir);
		var project = NativeProjectDocument.newFromInputs(dir.resolve("conflict.jadx"), inputs);
		var left = new JadxNodeRef(jadx.api.data.IJavaNodeRef.RefType.METHOD, "related.Hierarchy$SeparateLeft", "joined(I)I");
		var joined = new JadxNodeRef(jadx.api.data.IJavaNodeRef.RefType.METHOD, "related.Hierarchy$Joined", "joined(I)I");
		var code = new JadxCodeData();
		code.setRenames(List.of(new JadxCodeRename(left, "leftAlias"), new JadxCodeRename(joined, "joinedAlias")));
		project.setCodeData(code);
		try (var engine = open(project)) {
			String effective = RelatedFixture.method(engine, RelatedFixture.ref("Joined", "joined", "(I)I")).getName();
			assertTrue(List.of("leftAlias", "joinedAlias").contains(effective));
			for (String owner : List.of("SeparateLeft", "ExtendedLeft", "Joined"))
				assertEquals(effective, RelatedFixture.method(engine, RelatedFixture.ref(owner, "joined", "(I)I")).getName());
			assertEquals("joined", RelatedFixture.method(engine, RelatedFixture.ref("SeparateRight", "joined", "(I)I")).getName());
			assertEquals(2, project.getCodeData().getRenames().size());
		}
		code.setRenames(List.of(new JadxCodeRename(joined, "first"), new JadxCodeRename(joined, "second")));
		project.setCodeData(code);
		assertEquals(2, JadxNativeEditAdapter.renameCount(code, joined));
		try (var engine = open(project)) {
			assertEquals("second", RelatedFixture.method(engine, RelatedFixture.ref("Joined", "joined", "(I)I")).getName());
		}
		assertFalse(Files.exists(project.getProjectPath()));
	}

	static void prepare(Path directory, boolean everyMember) throws Exception {
		Files.createDirectories(directory);
		var inputs = RelatedFixture.compile(directory);
		Path mapping = directory.resolve("mapping.tiny");
		Files.writeString(mapping, "tiny\t2\t0\tofficial\tnamed\n"
				+ "c\trelated/Hierarchy$SeparateRight\trelated/Hierarchy$SeparateRight\n"
				+ "\tm\t(I)I\tjoined\tmappedRight\n");
		Path nativePath = directory.resolve("diagnostic.jadx");
		var project = NativeProjectDocument.newFromInputs(nativePath, inputs).withMappingsPath(mapping);
		var unrelated = JadxNodeRef.forCls("related.Hierarchy$Unrelated");
		var code = new JadxCodeData();
		code.setRenames(List.of(new JadxCodeRename(unrelated, "SavedUnrelated"),
				new JadxCodeRename(new JadxNodeRef(jadx.api.data.IJavaNodeRef.RefType.METHOD,
						"related.Hierarchy$Unrelated", "work(I)I"), JadxCodeRef.forMthArg(0), "unrelatedValue")));
		code.setComments(List.of(new JadxCodeComment(unrelated, "unrelated native comment")));
		project.setCodeData(code); project.save();
		var json = project.toJsonTree(); json.addProperty("futureRoot", "keep");
		json.getAsJsonObject("codeData").addProperty("futureCodeData", "keep");
		Files.writeString(nativePath, json.toString()); project = NativeProjectDocument.open(nativePath);
		byte[] before = Files.readAllBytes(nativePath), inputBefore = Files.readAllBytes(inputs.getFirst()),
				secondBefore = Files.readAllBytes(inputs.get(1)), mappingBefore = Files.readAllBytes(mapping);
		try (var engine = open(project)) {
			// Generate hot Java before replay, then recheck original-key aliases on cold/fresh engines.
			engine.searchJavaClassByOrigFullName("related.Hierarchy").getCode();
			var edited = NativeProjectDocument.copyCodeData(project.getCodeData());
			for (String owner : everyMember ? List.of("SeparateLeft", "ExtendedLeft", "Joined", "SeparateRight") : List.of("Joined")) {
				var method = RelatedFixture.method(engine, RelatedFixture.ref(owner, "joined", "(I)I"));
				JadxNativeEditAdapter.rename(edited, JadxNodeRef.forMth(method), "renamedJoined");
			}
			project.setCodeData(edited);
			assertArrayEquals(before, Files.readAllBytes(nativePath), "No write until explicit native save");
			engine.getArgs().setCodeData(edited);
			engine.getClasses().forEach(jadx.api.JavaClass::unload);
			JadxNativeEditAdapter.prepareCodeDataReplay(engine); engine.reloadCodeData();
			verifyAliases(engine, everyMember, true);
			project.save();
		}
		assertArrayEquals(inputBefore, Files.readAllBytes(inputs.getFirst()));
		assertArrayEquals(secondBefore, Files.readAllBytes(inputs.get(1)));
		assertArrayEquals(mappingBefore, Files.readAllBytes(mapping));
		var saved = NativeProjectDocument.open(nativePath);
		assertEquals("keep", saved.toJsonTree().get("futureRoot").getAsString());
		assertEquals("keep", saved.toJsonTree().getAsJsonObject("codeData").get("futureCodeData").getAsString());
		verifyRepresentation(saved, everyMember);
		try (var engine = open(saved)) { verifyAliases(engine, everyMember); }
	}

	static JadxDecompiler open(NativeProjectDocument project) {
		var engine = new JadxDecompiler(JadxEngineFactory.arguments(project.getInputFiles(), project.getMappingsPath(),
				project.getCodeData(), EffectiveAnalysisConfig.defaults()));
		try { engine.load(); return engine; }
		catch (RuntimeException failure) { engine.close(); throw failure; }
	}

	static void verifyRepresentation(NativeProjectDocument project, boolean everyMember) {
		var keys = project.getCodeData().getRenames().stream().filter(r -> r.getCodeRef() == null && r.getNewName().equals("renamedJoined"))
				.map(r -> r.getNodeRef().getDeclaringClass() + ":" + r.getNodeRef().getShortId()).sorted().toList();
		var expected = (everyMember ? List.of("SeparateLeft", "ExtendedLeft", "Joined", "SeparateRight") : List.of("Joined"))
				.stream().map(o -> "related.Hierarchy$" + o + ":joined(I)I").sorted().toList();
		assertEquals(expected, keys, "GUI Save As must preserve exact original-key records");
		assertEquals(1, project.getCodeData().getRenames().stream().filter(r -> r.getCodeRef() != null
				&& r.getCodeRef().getAttachType() == jadx.api.data.CodeRefType.MTH_ARG && r.getCodeRef().getIndex() == 0
				&& r.getNodeRef().getShortId().equals("work(I)I") && r.getNewName().equals("unrelatedValue")).count());
		assertEquals("mapping.tiny", project.getMappingsPath().getFileName().toString());
		assertTrue(project.toJsonTree().getAsJsonArray("files").asList().stream().allMatch(e -> !Path.of(e.getAsString()).isAbsolute()));
	}

	static void verifyAliases(JadxDecompiler engine, boolean everyMember) { verifyAliases(engine, everyMember, false); }

	static void verifyAliases(JadxDecompiler engine, boolean everyMember, boolean hotReplay) {
		String java = engine.searchJavaClassByOrigFullName("related.Hierarchy").getCode();
		for (String owner : List.of("SeparateLeft", "ExtendedLeft", "Joined"))
			assertEquals(!everyMember && hotReplay && !owner.equals("Joined") ? "joined" : "renamedJoined",
					RelatedFixture.method(engine, RelatedFixture.ref(owner, "joined", "(I)I")).getName(), owner);
		assertEquals(everyMember ? "renamedJoined" : "mappedRight",
				RelatedFixture.method(engine, RelatedFixture.ref("SeparateRight", "joined", "(I)I")).getName());
		for (String owner : List.of("Base", "Middle")) {
			assertEquals("hidden", RelatedFixture.method(engine, RelatedFixture.ref(owner, "hidden", "(I)I")).getName());
			assertEquals("hiding", RelatedFixture.method(engine, RelatedFixture.ref(owner, "hiding", "(I)I")).getName());
		}
		var unrelated = RelatedFixture.method(engine, RelatedFixture.ref("Unrelated", "work", "(I)I"));
		assertEquals("work", unrelated.getName()); assertEquals("SavedUnrelated", unrelated.getDeclaringClass().getName());
		assertTrue(java.contains("renamedJoined(int"), java);
		assertTrue(java.contains(everyMember ? "renamedJoined(int" : "mappedRight(int"), java);
		assertTrue(java.contains("work(int unrelatedValue)"), java);
		assertTrue(java.contains("unrelated native comment"), java);
	}
}
