package dev.libjadx.core.symbols;

public record ClassInfo(SymbolRef ref, String originalDottedName, String displayName,
		String displayQualifiedName, boolean isInner, boolean codeAvailable, SymbolRef originalParent,
		SymbolInfo.Provenance provenance) { }
