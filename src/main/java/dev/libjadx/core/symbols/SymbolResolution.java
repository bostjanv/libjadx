package dev.libjadx.core.symbols;

import java.util.List;

public record SymbolResolution(Outcome outcome, SymbolRef queriedRef, String sessionId, long logicalRevision,
		String snapshotId, SymbolInfo symbol, List<SymbolInfo> candidates, List<String> diagnostics) {
	public enum Outcome { RESOLVED, NOT_FOUND, AMBIGUOUS, PROVENANCE_UNAVAILABLE }
	public SymbolResolution {
		candidates = List.copyOf(candidates);
		diagnostics = List.copyOf(diagnostics);
	}
}
