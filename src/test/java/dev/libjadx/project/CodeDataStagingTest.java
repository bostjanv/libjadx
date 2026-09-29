package dev.libjadx.project;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.List;
import jadx.api.data.impl.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeDataStagingTest {
	@TempDir Path dir;
	@Test void privateDeepCopySingleCommitNoopAndStaleCandidates() throws Exception {
		Path input = Files.copy(Path.of("tests/fixtures/native-project/sample.jar"), dir.resolve("input.jar"));
		for (boolean raw : List.of(false, true)) {
			Path path = dir.resolve("project.jadx");
			if (!raw) NativeProjectDocument.newFromInputs(path, List.of(input)).save();
			var repo = raw ? NativeProjectRepository.fromInputs(List.of(input), List.of(dir))
					: NativeProjectRepository.open(path, List.of(dir));
			var before = repo.snapshot(); var pending = repo.pendingEdits();
			var code = new JadxCodeData();
			code.setRenames(List.of(new JadxCodeRename(JadxNodeRef.forCls("probe.Sample"), "OwnedAlias")));
			var candidate = repo.stageCodeData(code, 0);
			assertTrue(candidate.effective()); assertEquals(before, repo.snapshot()); assertEquals(pending, repo.pendingEdits());
			((JadxCodeRename) code.getRenames().getFirst()).setNewName("ExternalMutation");
			((JadxCodeRename) candidate.codeDataCopy().getRenames().getFirst()).setNewName("AccessorMutation");
			var after = repo.commitCodeData(candidate);
			assertEquals("OwnedAlias", repo.codeDataCopy().getRenames().getFirst().getNewName());
			assertEquals(1, after.revisions().logicalRevision()); assertEquals(1, after.revisions().indexRevision()); assertTrue(after.dirty());
			assertThrows(IllegalStateException.class, () -> repo.commitCodeData(candidate));
			var noop = repo.stageCodeData(repo.codeDataCopy(), 1); assertFalse(noop.effective());
			assertEquals(after, repo.commitCodeData(noop));
			var stale = repo.stageCodeData(repo.codeDataCopy(), 1);
			var next = repo.codeDataCopy(); ((JadxCodeRename) next.getRenames().getFirst()).setNewName("NextAlias");
			repo.commitCodeData(repo.stageCodeData(next, 1));
			assertThrows(NativeProjectRepository.StaleRevisionException.class, () -> repo.commitCodeData(stale));
			var other = NativeProjectRepository.fromInputs(List.of(input), List.of(dir));
			assertThrows(IllegalStateException.class, () -> other.commitCodeData(repo.stageCodeData(next, 2)));
			if (!raw) assertTrue(NativeProjectDocument.open(path).getCodeData().getRenames().isEmpty());
			var beforeSave = repo.stageCodeData(next, 2);
			repo.save(raw ? dir.resolve("saved-raw.jadx") : null, null);
			assertThrows(IllegalStateException.class, () -> repo.commitCodeData(beforeSave));
			assertEquals(2, repo.snapshot().revisions().logicalRevision());
		}
	}
}
