package dev.libjadx.http;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CursorSigningKeyTest {
	@TempDir Path dir;

	@Test
	void keySurvivesNewLoaderAndIsPrivateOnPosix() throws Exception {
		Path path = dir.resolve("operational/cursor-signing.key");
		byte[] first = CursorSigningKey.loadOrCreate(path);
		assertEquals(32, first.length);
		assertArrayEquals(first, CursorSigningKey.loadOrCreate(path));
		if (Files.getFileStore(path).supportsFileAttributeView("posix")) {
			assertEquals(Set.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,
					java.nio.file.attribute.PosixFilePermission.OWNER_WRITE), Files.getPosixFilePermissions(path));
		}
	}

	@Test
	void refusesSymlinkOrMalformedExistingKey() throws Exception {
		Path target = dir.resolve("target");
		Files.writeString(target, "bad");
		assertThrows(IOException.class, () -> CursorSigningKey.loadOrCreate(target));
		Path link = dir.resolve("link");
		Files.createSymbolicLink(link, target);
		assertThrows(IOException.class, () -> CursorSigningKey.loadOrCreate(link));
	}
}
