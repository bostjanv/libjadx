package dev.libjadx.core.references;

import java.util.List;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.core.symbols.SymbolInfo;
import dev.libjadx.core.symbols.SymbolResolution;

public record ReferencePage(SymbolResolution.Outcome outcome, SymbolRef queriedRef, ReferenceQuery.Direction direction,
        List<SymbolInfo> candidates, String sessionId, long logicalRevision, String snapshotId,
        String scope, Coverage coverage, List<ReferenceEdge> edges, String nextCursor, boolean pageComplete,
        Capabilities capabilities, List<String> diagnostics) {
    public ReferencePage { candidates = List.copyOf(candidates); edges = List.copyOf(edges); diagnostics = List.copyOf(diagnostics); }
    public record Coverage(String status, String reason) { }
    public record Capabilities(String resolvedTarget, String sourceLocation, String originalOffset, String referenceKind, String globalCoverage) { }
}
