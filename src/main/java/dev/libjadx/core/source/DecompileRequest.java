package dev.libjadx.core.source;

import dev.libjadx.core.symbols.SymbolRef;

public record DecompileRequest(SymbolRef ref, String representation, String decompilationMode, boolean includeAnnotations,
		boolean includeRawDebugLines, boolean strict, String expectedSessionId, Long expectedLogicalRevision,
		String expectedSourceSnapshotId) { }
