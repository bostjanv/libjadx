package dev.libjadx.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.io.RandomAccessFile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jadx.api.data.impl.JadxCodeComment;
import jadx.api.data.impl.JadxCodeRename;
import jadx.api.data.impl.JadxNodeRef;

class NativeProjectRepositoryTest {
	@TempDir Path dir;

	@Test
	void explicitSaveChecksProjectAndMappingBaselineAndPreservesPendingEdits() throws Exception {
		Path project = fixture();
		NativeProjectRepository repository = NativeProjectRepository.open(project, List.of(dir));
		assertFalse(repository.snapshot().dirty());
		var code = repository.codeDataCopy();
		code.setRenames(List.of(new JadxCodeRename(JadxNodeRef.forCls("probe.Sample"), "Changed")));
		code.setComments(List.of(new JadxCodeComment(JadxNodeRef.forCls("probe.Sample"), "pending")));
		repository.replaceCodeData(code, 0);
		assertTrue(repository.snapshot().dirty());
		assertEquals(1, repository.snapshot().revisions().logicalRevision());
		assertFalse(Files.readString(project).contains("Changed"));
		assertTrue(repository.pendingEdits().toString().contains("pending"));
		assertThrows(NativeProjectRepository.StaleRevisionException.class, () -> repository.save(null, 0L));

		Files.writeString(project, Files.readString(project).replace("futureGuiField", "otherGuiField"));
		assertThrows(NativeProjectRepository.ExternalModificationException.class, () -> repository.save(null, 1L));
		assertTrue(repository.snapshot().dirty());
		assertTrue(repository.pendingEdits().toString().contains("Changed"));
		assertThrows(NativeProjectRepository.UnsavedChangesException.class, () -> repository.reload(false));
		repository.reload(true);
		assertFalse(repository.snapshot().dirty());
		assertFalse(repository.pendingEdits().toString().contains("Changed"));

		var next = repository.codeDataCopy();
		next.setRenames(List.of(new JadxCodeRename(JadxNodeRef.forCls("probe.Sample"), "Saved")));
		repository.replaceCodeData(next, 2);
		Files.writeString(dir.resolve("sample.tiny"), "v2\tintermediary\tchanged\n");
		assertThrows(NativeProjectRepository.ExternalModificationException.class, () -> repository.save(null, 3L));
		assertTrue(repository.snapshot().dirty());
	}

	@Test
	void rawInputWritesNothingUntilSaveAndUsesNativeRelativePath() throws Exception {
		Path input = Files.createFile(dir.resolve("input.jar"));
		NativeProjectRepository repository = NativeProjectRepository.fromInputs(List.of(input), List.of(dir));
		String session = repository.snapshot().revisions().sessionId();
		assertEquals(null, repository.snapshot().projectPath());
		var code = repository.codeDataCopy();
		code.setRenames(List.of(new JadxCodeRename(JadxNodeRef.forCls("probe.Sample"), "RawAlias")));
		repository.replaceCodeData(code, 0);
		Path target = dir.resolve("raw.jadx");
		assertFalse(Files.exists(target));
		assertThrows(IllegalArgumentException.class, () -> repository.save(null, 1L));
		repository.save(target, 1L);
		assertTrue(Files.exists(target));
		assertTrue(Files.readString(target).contains("input.jar"));
		assertTrue(Files.readString(target).contains("RawAlias"));
		assertFalse(repository.snapshot().dirty());
		assertEquals(session, repository.snapshot().revisions().sessionId());
		assertThrows(IllegalArgumentException.class, () -> repository.save(dir.resolve("other.jadx"), null));
	}

	@Test
	void successfulSaveSurvivesRestartAndSessionsDiffer() throws Exception {
		Path project = fixture();
		NativeProjectRepository first = NativeProjectRepository.open(project, List.of(dir));
		var code = first.codeDataCopy();
		code.setRenames(List.of(new JadxCodeRename(JadxNodeRef.forCls("probe.Sample"), "Durable")));
		first.replaceCodeData(code, 0);
		first.save(null, 1L);
		assertFalse(first.snapshot().dirty());
		assertTrue(Files.readString(project).contains("futureGuiField"));
		assertTrue(Files.readString(project).contains("futureCodeField"));
		NativeProjectRepository restarted = NativeProjectRepository.open(project, List.of(dir));
		assertFalse(restarted.snapshot().dirty());
		assertTrue(restarted.pendingEdits().toString().contains("Durable"));
		assertFalse(first.snapshot().revisions().sessionId().equals(restarted.snapshot().revisions().sessionId()));
		assertEquals(first.snapshot().revisions().persistedIdentity(), restarted.snapshot().revisions().persistedIdentity());
	}

	@Test
	void unsavedEditIsAbsentAfterRepositoryRestart() throws Exception {
		Path project = fixture();
		NativeProjectRepository first = NativeProjectRepository.open(project, List.of(dir));
		var code = first.codeDataCopy();
		code.setRenames(List.of(new JadxCodeRename(JadxNodeRef.forCls("probe.Sample"), "Transient")));
		first.replaceCodeData(code, 0);
		assertTrue(first.snapshot().dirty());
		NativeProjectRepository restarted = NativeProjectRepository.open(project, List.of(dir));
		assertFalse(restarted.snapshot().dirty());
		assertFalse(restarted.pendingEdits().toString().contains("Transient"));
	}

	@Test
	void largeInputIdentityBecomesAvailableAfterBackgroundHash() throws Exception {
		Path large = dir.resolve("large.bin");
		try (RandomAccessFile file = new RandomAccessFile(large.toFile(), "rw")) {
			file.setLength(33L * 1024 * 1024);
		}
		NativeProjectRepository repository = NativeProjectRepository.fromInputs(List.of(large), List.of(dir));
		long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
		while ("PENDING".equals(repository.snapshot().revisions().persistedIdentityState())
				&& System.nanoTime() < deadline) Thread.sleep(10);
		assertEquals("READY", repository.snapshot().revisions().persistedIdentityState());
		assertTrue(repository.snapshot().revisions().persistedIdentity().startsWith("sha256:"));
	}

	private Path fixture() throws Exception {
		Files.createFile(dir.resolve("sample.jar"));
		Files.writeString(dir.resolve("sample.tiny"), "v2\tintermediary\tnamed\n");
		Path project = dir.resolve("sample.jadx");
		Files.writeString(project, """
				{"projectVersion":2,"files":["sample.jar"],"mappingsPath":"sample.tiny",
				 "codeData":{"renames":[],"comments":[],"futureCodeField":9},"futureGuiField":true}
				""");
		return project;
	}
}
