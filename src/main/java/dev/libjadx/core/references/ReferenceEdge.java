package dev.libjadx.core.references;

import java.util.List;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.core.source.SourceCoordinates;

public record ReferenceEdge(SymbolRef sourceRef, SymbolRef targetRef, ReferenceQuery.Relation relation,
        Resolution resolution, Evidence evidence, List<Site> sourceSites, String sourceSiteCoverage, String originalOffset) {
    public ReferenceEdge { sourceSites = List.copyOf(sourceSites); }
    public enum Resolution { RESOLVED, UNRESOLVED, OBSERVED }
    public enum Evidence { JADX_METHOD_USED, JADX_METHOD_USE_IN, JADX_METHOD_UNRESOLVED_USED,
        JADX_FIELD_USE_IN, JADX_CLASS_DEPENDENCY, JADX_CLASS_USE_IN }
    public record Site(SymbolRef sourceOwnerRef, String sourceSnapshotId, SourceCoordinates.Point position, String precision) { }
}
