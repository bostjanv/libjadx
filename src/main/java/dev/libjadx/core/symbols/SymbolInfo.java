package dev.libjadx.core.symbols;

public record SymbolInfo(SymbolRef ref, String originalName, String originalDescriptor,
		String displayName, String displayQualifiedName,
		SymbolRef containingClass, Provenance provenance) {
	public enum Provenance { EXACT, AMBIGUOUS, UNAVAILABLE }
	public SymbolInfo {
		if (!java.util.Objects.equals(originalName, ref.originalName())
				|| !java.util.Objects.equals(originalDescriptor, ref.originalDescriptor())) {
			throw new IllegalArgumentException("SymbolInfo original fields must match its ref");
		}
	}
}
