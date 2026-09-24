package dev.libjadx.project;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Content hash of a native file; absence is distinct from an empty file. */
public record FileFingerprint(boolean present, String sha256) {
	public static FileFingerprint of(Path path) throws IOException {
		if (path == null || !Files.exists(path)) return new FileFingerprint(false, null);
		MessageDigest digest = sha256Digest();
		try (InputStream input = Files.newInputStream(path)) {
			byte[] buffer = new byte[64 * 1024];
			int read;
			while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
		}
		return new FileFingerprint(true, "sha256:" + java.util.HexFormat.of().formatHex(digest.digest()));
	}

	static MessageDigest sha256Digest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException(impossible);
		}
	}
}
