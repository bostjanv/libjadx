package dev.libjadx.http;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import dev.libjadx.core.symbols.SymbolCatalog;

/** A server-operational secret, separate from native project state. */
final class CursorSigningKey {
	private CursorSigningKey() { }

	static byte[] loadDefault() {
		try {
			String configured = System.getenv("XDG_STATE_HOME");
			Path base = configured != null && !configured.isBlank() && Path.of(configured).isAbsolute()
					? Path.of(configured) : Path.of(System.getProperty("user.home"), ".local", "state");
			return loadOrCreate(base.resolve("libjadx/cursor-signing.key"));
		} catch (IOException failure) {
			throw new IllegalStateException("Cannot load the operational cursor signing key", failure);
		}
	}

	static byte[] loadOrCreate(Path path) throws IOException {
		Path target = path.toAbsolutePath().normalize();
		Files.createDirectories(target.getParent());
		if (Files.isSymbolicLink(target)) throw new IOException("Cursor signing key must not be a symlink");
		if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
			byte[] generated = SymbolCatalog.newCursorKey();
			try {
				Set<java.nio.file.OpenOption> options = Set.of(StandardOpenOption.CREATE_NEW,
						StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
				try (FileChannel channel = FileSystems.getDefault().supportedFileAttributeViews().contains("posix")
						? FileChannel.open(target, options, PosixFilePermissions.asFileAttribute(
								PosixFilePermissions.fromString("rw-------")))
						: FileChannel.open(target, options)) {
					ByteBuffer bytes = ByteBuffer.wrap(generated);
					while (bytes.hasRemaining()) channel.write(bytes);
					channel.force(true);
				}
			} catch (FileAlreadyExistsException concurrentStart) {
				// Another process created the shared operational key first.
			}
		}
		if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
			throw new IOException("Cursor signing key must be a regular file");
		}
		if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
			Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(target, LinkOption.NOFOLLOW_LINKS);
			if (permissions.stream().anyMatch(permission -> permission.name().startsWith("GROUP_")
					|| permission.name().startsWith("OTHERS_"))) {
				throw new IOException("Cursor signing key must be private to the current user");
			}
		}
		for (int retry = 0; retry < 20; retry++) {
			byte[] key;
			try (InputStream stream = Files.newInputStream(target, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
				key = stream.readNBytes(33);
			}
			if (key.length == 32) return key;
			if (key.length > 32) break;
			try { Thread.sleep(10); }
			catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				throw new IOException("Interrupted while loading the cursor signing key", interrupted);
			}
		}
		throw new IOException("Cursor signing key must contain exactly 32 bytes");
	}
}
