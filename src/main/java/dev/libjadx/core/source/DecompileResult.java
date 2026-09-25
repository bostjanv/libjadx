package dev.libjadx.core.source;

import java.util.List;

import dev.libjadx.core.symbols.SymbolInfo;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.core.symbols.SymbolResolution;

/** Immutable public result: no live Jadx node or metadata escapes its engine lease. */
public record DecompileResult(SymbolResolution.Outcome outcome, SymbolRef queriedRef, SymbolRef sourceOwnerRef,
		List<SymbolInfo> candidates, String sessionId, long logicalRevision, String sourceSnapshotId,
		EffectiveSettings effectiveSettings, Status status, String representation, String source,
		Boolean methodRangeAvailable, SourceCoordinates.Range methodRange, String methodSource,
		List<Annotation> annotations, Object rawDebugLines, Integer classErrorCount, List<String> diagnostics,
		Capabilities capabilities) {
	public DecompileResult {
		if (rawDebugLines != null) throw new IllegalArgumentException("Unverified raw debug lines cannot be returned");
		candidates = List.copyOf(candidates);
		annotations = List.copyOf(annotations);
		diagnostics = List.copyOf(diagnostics);
	}
	public enum Status { COMPLETE, PARTIAL, UNAVAILABLE }
	public enum Availability { EXACT, PARTIAL, UNKNOWN, UNAVAILABLE }
	public record EffectiveSettings(String decompilationMode, String fingerprint) { }
	public record Annotation(String kind, SourceCoordinates.Point position, SymbolRef targetRef,
			String precision, String sourceSnapshotId) { }
	public record Capabilities(Availability source, Availability declarationPositions,
			Availability referenceTargets, Availability methodRange, Availability rawDebugLines,
			Availability originalBytecodeOffsets, Availability classErrors) { }
}
