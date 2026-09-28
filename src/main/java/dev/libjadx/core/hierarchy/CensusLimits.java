package dev.libjadx.core.hierarchy;

/** Internal admission limits; duplicates count towards all declaration limits. */
public record CensusLimits(int inputs, int classes, int methodsPerClass, int methods,
		int edges, int duplicates, int archiveEntries, int fileBytes, long totalBytes, long characters) {
	public CensusLimits {
		if (inputs < 1 || classes < 1 || methodsPerClass < 1 || methods < 1 || edges < 1
				|| duplicates < 1 || archiveEntries < 1 || fileBytes < 1 || fileBytes == Integer.MAX_VALUE
				|| totalBytes < 1 || characters < 1) throw new IllegalArgumentException("Positive census limits required");
	}
	public static CensusLimits defaults() {
		return new CensusLimits(16, 20_000, 2_000, 100_000, 80_000, 8, 100_000,
				32 * 1024 * 1024, 128L * 1024 * 1024, 8_000_000);
	}
}
