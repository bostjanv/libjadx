package dev.libjadx.core.source;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Process-scoped, source-text-bound identity; never persisted. */
public final class SourceSnapshot {
	private SourceSnapshot() { }

	public static String id(String sessionId, long revision, long publicationEpoch,
			String settingsFingerprint, String ownerDescriptor, String source) {
		try {
			MessageDigest hash = MessageDigest.getInstance("SHA-256");
			for (String field : new String[] { "libjadx-source-v1", sessionId, Long.toString(revision),
					Long.toString(publicationEpoch), settingsFingerprint, ownerDescriptor, source }) {
				// Encode raw UTF-16 code units so even isolated surrogates remain distinct.
				byte[] bytes = new byte[field.length() * 2];
				for (int i = 0; i < field.length(); i++) {
					char unit = field.charAt(i);
					bytes[i * 2] = (byte) (unit >>> 8);
					bytes[i * 2 + 1] = (byte) unit;
				}
				hash.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
				hash.update(bytes);
			}
			return "sha256:" + HexFormat.of().formatHex(hash.digest());
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 unavailable", impossible);
		}
	}
}
