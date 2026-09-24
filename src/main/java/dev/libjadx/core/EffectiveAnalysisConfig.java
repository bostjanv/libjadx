package dev.libjadx.core;

/** Settings accepted for one analysis instance. Native persistence is not implied. */
public record EffectiveAnalysisConfig(String decompilationMode) {
	public static EffectiveAnalysisConfig defaults() { return new EffectiveAnalysisConfig("AUTO"); }

	public String fingerprint() {
		try {
			var digest = java.security.MessageDigest.getInstance("SHA-256");
			return "sha256:" + java.util.HexFormat.of().formatHex(
					digest.digest(decompilationMode.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		} catch (java.security.NoSuchAlgorithmException impossible) {
			throw new IllegalStateException(impossible);
		}
	}
}
