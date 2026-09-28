package dev.libjadx.project;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.NoSuchFileException;
import java.util.List;
import dev.libjadx.core.mappings.MappingImportDtos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SafeMappingInputTest {
	@TempDir Path root;
	@Test void capturesReadOnlyBytesAndRejectsMissingEscapeSymlinkParentsAndLeafAndNonregular() throws Exception {
		Path input = root.resolve("input.tiny"); Files.writeString(input, "owned bytes");
		var before = FileFingerprint.of(input);
		var captured = new SafeMappingInput(input, List.of(root), List.of());
		assertEquals(before.sha256(), captured.sha256()); captured.verifyUnchanged(); assertEquals(before, FileFingerprint.of(input));
		assertThrows(NoSuchFileException.class, () -> new SafeMappingInput(root.resolve("absent.tiny"), List.of(root), List.of()));
		assertThrows(SecurityException.class, () -> new SafeMappingInput(root.getParent().resolve("escape.tiny"), List.of(root), List.of()));
		Path leaf = root.resolve("leaf.tiny"); Files.createSymbolicLink(leaf, input);
		assertThrows(SecurityException.class, () -> new SafeMappingInput(leaf, List.of(root), List.of()));
		Path parent = root.resolve("parent"); Files.createSymbolicLink(parent, root);
		assertThrows(SecurityException.class, () -> new SafeMappingInput(parent.resolve("input.tiny"), List.of(root), List.of()));
		Path directory = Files.createDirectory(root.resolve("dir.tiny"));
		assertThrows(SecurityException.class, () -> new SafeMappingInput(directory, List.of(root), List.of()));
		assertThrows(SecurityException.class, () -> new SafeMappingInput(input, List.of(root), List.of(input)));
		Path link = root.resolve("hard.tiny"); Files.createLink(link, input);
		assertThrows(SecurityException.class, () -> new SafeMappingInput(link, List.of(root), List.of(input)));
		Path oversized = root.resolve("large.tiny");
		try (var out = java.nio.channels.FileChannel.open(oversized, java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE)) {
			out.position(4 * 1024 * 1024); out.write(java.nio.ByteBuffer.wrap(new byte[1]));
		}
		assertEquals(429, assertThrows(MappingImportDtos.Problem.class, () -> new SafeMappingInput(oversized, List.of(root), List.of())).status());
	}
	@Test void deletionReplacementSameSizeRewriteAndDirectoryRenameCannotPassFinalCheck() throws Exception {
		Path folder = Files.createDirectory(root.resolve("folder")); Path input = folder.resolve("source.tiny");
		Files.writeString(input, "first"); var captured = new SafeMappingInput(input, List.of(root), List.of());
		var time = Files.getLastModifiedTime(input); Files.writeString(input, "other"); Files.setLastModifiedTime(input, time);
		assertThrows(NativeProjectRepository.ExternalModificationException.class, captured::verifyUnchanged);
		Files.writeString(input, "first"); captured = new SafeMappingInput(input, List.of(root), List.of());
		Files.delete(input); Files.writeString(input, "first");
		assertThrows(NativeProjectRepository.ExternalModificationException.class, captured::verifyUnchanged);
		captured = new SafeMappingInput(input, List.of(root), List.of()); Files.delete(input);
		assertThrows(NativeProjectRepository.ExternalModificationException.class, captured::verifyUnchanged);
		Files.writeString(input, "first"); captured = new SafeMappingInput(input, List.of(root), List.of());
		Files.move(folder, root.resolve("moved")); Files.createDirectory(folder); Files.writeString(input, "first");
		assertThrows(NativeProjectRepository.ExternalModificationException.class, captured::verifyUnchanged);
	}
}
