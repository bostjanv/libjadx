package dev.libjadx.app;

import java.util.List;
import dev.libjadx.core.references.*;
import dev.libjadx.core.symbols.*;
import dev.libjadx.jadxadapter.*;

/** Admission precedes all repository, catalog, code and graph access. */
public final class ReferenceQueryService {
    private final ProjectRuntime runtime;
    private final SymbolCatalogProvider catalogs;
    private final byte[] key;
    public ReferenceQueryService(ProjectRuntime runtime, SymbolCatalogProvider catalogs, byte[] key) {
        this.runtime = runtime; this.catalogs = catalogs; this.key = key.clone();
    }
    public ReferencePage query(ReferenceQuery query) {
        var cursor = ReferenceSnapshot.authenticate(query.cursor(), key);
        return runtime.withPrimarySymbolRead(query.ref().originalClassDescriptor(), context -> {
            String session = context.revisions().sessionId(); long revision = context.revisions().logicalRevision();
            if (query.expectedSessionId() != null && (!session.equals(query.expectedSessionId())
                    || revision != query.expectedLogicalRevision())) throw new ReferenceSnapshot.StaleReferenceException();
            var catalog = catalogs.primary(context);
            var ref = query.ref();
            var resolution = SymbolLookup.resolve(catalog, ref, entry -> JadxSymbolAdapter.matchingMembers(
                    JadxSymbolAdapter.visibleClass(context.decompiler(), ref.originalClassDescriptor(), entry.occurrence()), ref));
            List<ReferenceEdge> edges = List.of();
            List<String> diagnostics = resolution.diagnostics();
            if (resolution.outcome() == SymbolResolution.Outcome.RESOLVED) {
                var cls = JadxSymbolAdapter.visibleClass(context.decompiler(), ref.originalClassDescriptor(), 0);
                edges = JadxReferenceAdapter.extract(context.decompiler(), cls, query, session, revision,
                        context.publicationEpoch(), context.settings().fingerprint());
                diagnostics = List.of(
                        "CALL is a distinct Jadx-reported method pair, not an invoke count or dynamic dispatch closure",
                        "Usage graph may omit recursive, optimized, unresolved or discarded input relationships; coverage is not exhaustive",
                        "FIELD_USE access direction, original bytecode offsets and per-input provenance are unavailable",
                        "Source sites require matching original target, exact P4.2 token/range and enclosing caller metadata; absent sites are unavailable",
                        "Only current primary settings are used; no native edits or save are performed");
            }
            var snapshot = new ReferenceSnapshot(edges, query, session, revision, context.publicationEpoch(), context.settings().fingerprint(), key);
            var slice = snapshot.page(query, cursor);
            if (resolution.outcome() == SymbolResolution.Outcome.RESOLVED && query.strict())
                throw new IncompleteReferencesException(diagnostics);
            boolean sites = edges.stream().anyMatch(e -> !e.sourceSites().isEmpty());
            return new ReferencePage(resolution.outcome(), ref, query.direction(), resolution.candidates(), session, revision,
                    resolution.outcome() == SymbolResolution.Outcome.RESOLVED ? snapshot.id() : null, "JADX_REPORTED",
                    new ReferencePage.Coverage("PARTIAL", "Jadx usage graph and discarded/original input coverage are not proved exhaustive"),
                    slice.edges(), slice.nextCursor(), slice.nextCursor() == null,
                    new ReferencePage.Capabilities(resolution.outcome() == SymbolResolution.Outcome.RESOLVED ? "PARTIAL" : "UNAVAILABLE",
                            sites ? "PARTIAL" : "UNAVAILABLE", "UNAVAILABLE", "PARTIAL", "UNKNOWN"), diagnostics);
        });
    }
    public static final class IncompleteReferencesException extends RuntimeException {
        private final List<String> diagnostics;
        public IncompleteReferencesException(List<String> diagnostics) { this.diagnostics = List.copyOf(diagnostics); }
        public List<String> diagnostics() { return diagnostics; }
    }
}
