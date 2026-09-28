package dev.libjadx.project;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SafeMappingOutputTest {
	@TempDir Path root;

	@Test
	void sourceInputNativeProjectAndAttachedMappingPathsAreProtectedEvenIfAbsent() throws Exception {
		for (String name : List.of("input.jar", "native.jadx", "attached.tiny", "operational.tiny")) {
			Path protectedPath = root.resolve(name);
			assertThrows(SecurityException.class, () -> new SafeMappingOutput(protectedPath, List.of(root), List.of(protectedPath), ignored -> { }));
			assertFalse(Files.exists(protectedPath));
		}
	}

	@Test
	void parentReplacementWithSymlinkIsRejectedAndOwnedTempIsCleanedThroughOpenDirectory() throws Exception {
		Path parent = Files.createDirectory(root.resolve("parent"));
		Path moved = root.resolve("moved");
		Path other = Files.createDirectory(root.resolve("other"));
		byte[] bytes = "verified".getBytes(java.nio.charset.StandardCharsets.UTF_8);
		try (var output = new SafeMappingOutput(parent.resolve("export.tiny"), List.of(root), List.of(), stage -> {
			if (stage.equals("BEFORE_PUBLISH")) {
				Files.move(parent, moved); Files.createSymbolicLink(parent, other);
			}
		})) {
			output.stage(bytes);
			assertThrows(SecurityException.class, () -> output.publish(bytes));
			assertFalse(output.published());
		}
		assertFalse(Files.exists(other.resolve("export.tiny")));
		try (var entries = Files.list(moved)) { assertEquals(0, entries.count()); }
	}

	@Test
	void existingIdenticalBytesAtFinalPublicationAreNeverOverwritten() throws Exception {
		Path target = root.resolve("export.tiny");
		byte[] bytes = "same bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
		try (var output = new SafeMappingOutput(target, List.of(root), List.of(), stage -> {
			if (stage.equals("PUBLICATION")) Files.write(target, bytes);
		})) {
			output.stage(bytes);
			assertThrows(NativeProjectRepository.ExternalModificationException.class, () -> output.publish(bytes));
			assertFalse(output.published());
		}
		assertArrayEquals(bytes, Files.readAllBytes(target));
		try (var entries = Files.list(root)) { assertEquals(1, entries.count()); }
	}

	@Test
	void stagingReplacementIsNeitherPublishedNorDeleted() throws Exception {
		Path target = root.resolve("export.tiny");
		byte[] bytes = "verified".getBytes(java.nio.charset.StandardCharsets.UTF_8);
		Path outsider = root.resolve("outsider"); Files.writeString(outsider, "retain");
		try (var output = new SafeMappingOutput(target, List.of(root), List.of(), stage -> {
			if (stage.equals("PUBLICATION")) {
				try (var entries = Files.list(root)) {
					Path staged = entries.filter(path -> path.getFileName().toString().startsWith(".libjadx-mapping-")).findFirst().orElseThrow();
					Files.delete(staged); Files.createSymbolicLink(staged, outsider);
				}
			}
		})) {
			output.stage(bytes);
			assertThrows(java.io.IOException.class, () -> output.publish(bytes));
		}
		assertFalse(Files.exists(target)); assertEquals("retain", Files.readString(outsider));
		try (var entries = Files.list(root)) { assertTrue(entries.anyMatch(Files::isSymbolicLink)); }
	}

	@Test
	void changedStagingBytesCannotYieldVerifiedSuccessAndPermissionsAreRestrictive() throws Exception {
		Path target = root.resolve("export.tiny");
		byte[] bytes = "verified".getBytes(java.nio.charset.StandardCharsets.UTF_8);
		try (var output = new SafeMappingOutput(target, List.of(root), List.of(), stage -> {
			if (stage.equals("PUBLICATION")) {
				try (var entries = Files.list(root)) {
					Path staged = entries.findFirst().orElseThrow();
					assertEquals(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(staged));
					Files.writeString(staged, "changed");
				}
			}
		})) {
			output.stage(bytes);
			assertThrows(java.io.IOException.class, () -> output.publish(bytes));
		}
		assertFalse(Files.exists(target));
		try (var entries = Files.list(root)) { assertEquals(0, entries.count()); }
	}
}
