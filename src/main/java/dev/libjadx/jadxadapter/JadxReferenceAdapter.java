package dev.libjadx.jadxadapter;

import java.util.*;
import dev.libjadx.core.references.*;
import dev.libjadx.core.symbols.SymbolRef;
import jadx.api.*;
import static dev.libjadx.core.references.ReferenceEdge.Evidence.*;
import static dev.libjadx.core.references.ReferenceEdge.Resolution.*;
import static dev.libjadx.core.references.ReferenceQuery.Relation.*;

/** Jadx 1.5.6 graph conversion. Caller owns a single conservative engine lease. */
public final class JadxReferenceAdapter {
    private JadxReferenceAdapter() { }
    public static List<ReferenceEdge> extract(JadxDecompiler jadx, JavaClass cls, ReferenceQuery query,
            String session, long revision, long epoch, String settings) {
        return extract(jadx, cls, query, session, revision, epoch, settings, JadxSourceAdapter::extract);
    }

    @FunctionalInterface
    interface SourceExtractor {
        JadxSourceAdapter.SourceData extract(JadxDecompiler jadx, JavaClass caller, SymbolRef callerRef,
                boolean includeAnnotations, String session, long revision, long epoch, String settings);
    }

    /** Package-private reader seam lets a real-Jadx test count full source extractions. */
    static List<ReferenceEdge> extract(JadxDecompiler jadx, JavaClass cls, ReferenceQuery query,
            String session, long revision, long epoch, String settings, SourceExtractor sourceExtractor) {
        // Match P4.1 member resolution timing and establish optimized owner state.
        cls.getCodeInfo();
        List<ReferenceEdge> edges = new ArrayList<>();
        var budget = new ReferenceSnapshot.Budget();
        SymbolRef ref = query.ref();
        boolean incoming = query.direction() == ReferenceQuery.Direction.INCOMING;
        if (ref.kind() == SymbolRef.Kind.METHOD) {
            JavaMethod method = JadxSymbolAdapter.matchingMethod(cls, ref);
            if (method == null) throw new IllegalStateException("Resolved method vanished");
            for (JavaNode node : incoming ? method.getUseIn() : method.getUsed()) {
                if (node instanceof JavaMethod other) {
                    add(edges, budget, incoming ? other : method, incoming ? method : other, CALL,
                            incoming ? JADX_METHOD_USE_IN : JADX_METHOD_USED);
                }
            }
            if (!incoming) for (var target : method.getUnresolvedUsed()) {
                SymbolRef targetRef = JadxSymbolAdapter.originalRef(target);
                budget.edge(ref, targetRef);
                edges.add(new ReferenceEdge(ref, targetRef, UNRESOLVED_CALL, UNRESOLVED,
                        JADX_METHOD_UNRESOLVED_USED, List.of(), "UNAVAILABLE", null));
            }
        } else if (ref.kind() == SymbolRef.Kind.FIELD) {
            JavaField field = cls.getFields().stream().filter(f -> ref.equals(JadxSymbolAdapter.originalRef(f))).findFirst().orElseThrow();
            for (JavaNode node : field.getUseIn()) if (node instanceof JavaMethod method)
                add(edges, budget, method, field, FIELD_USE, JADX_FIELD_USE_IN);
        } else {
            for (JavaNode other : incoming ? cls.getUseIn() : cls.getDependencies()) if (other instanceof JavaClass)
                add(edges, budget, incoming ? other : cls, incoming ? cls : other, CLASS_DEPENDENCY,
                        incoming ? JADX_CLASS_USE_IN : JADX_CLASS_DEPENDENCY);
        }
        if (query.includeSourceSites() && ref.kind() != SymbolRef.Kind.CLASS) {
            ClassLookup classes = new ClassLookup(jadx);
            // Process only reported caller owners, never a whole-project traversal.
            // Re-extract graph afterwards: source generation can prune Jadx's relationships.
            Set<JavaClass> processedOwners = new HashSet<>();
            for (var edge : List.copyOf(edges)) if (edge.sourceRef().kind() == SymbolRef.Kind.METHOD) {
                JavaClass caller = classes.unique(edge.sourceRef());
                if (caller != null) {
                    JavaClass owner = caller.getOriginalTopParentClass();
                    if (processedOwners.add(owner)) owner.getCodeInfo();
                }
            }
            var withoutSites = new ReferenceQuery(ref, query.direction(), query.relations(), query.pageSize(), null,
                    false, false, null, null);
            edges = new ArrayList<>(extract(jadx, cls, withoutSites, session, revision, epoch, settings));
            budget = new ReferenceSnapshot.Budget();
            for (var edge : edges) budget.edge(edge.sourceRef(), edge.targetRef());
            Map<SymbolRef, Map<SymbolRef, List<ReferenceEdge.Site>>> sitesByCaller = new HashMap<>();
            for (int i = 0; i < edges.size(); i++) {
                var edge = edges.get(i);
                if (!query.relations().contains(edge.relation())) continue;
                List<ReferenceEdge.Site> sites = List.of();
                if (edge.relation() == CALL || edge.relation() == FIELD_USE) {
                    sites = sitesByCaller.computeIfAbsent(edge.sourceRef(), callerRef ->
                            sitesByTarget(jadx, classes, callerRef, session, revision, epoch, settings, sourceExtractor))
                            .getOrDefault(edge.targetRef(), List.of());
                    for (var site : sites) budget.site(site);
                }
                edges.set(i, new ReferenceEdge(edge.sourceRef(), edge.targetRef(), edge.relation(), edge.resolution(),
                        edge.evidence(), sites, sites.isEmpty() ? "UNAVAILABLE" : "PARTIAL", null));
            }
        }
        return edges.stream().filter(e -> query.relations().contains(e.relation())).toList();
    }
    private static void add(List<ReferenceEdge> edges, ReferenceSnapshot.Budget budget, JavaNode source, JavaNode target,
            ReferenceQuery.Relation relation, ReferenceEdge.Evidence evidence) {
        SymbolRef from = JadxSymbolAdapter.originalRef(source), to = JadxSymbolAdapter.originalRef(target);
        budget.edge(from, to);
        boolean hidden = source instanceof JavaClass sc && sc.isNoCode()
                || target instanceof JavaClass tc && tc.isNoCode()
                || source instanceof JavaMethod sm && sm.getMethodNode().contains(jadx.core.dex.attributes.AFlag.DONT_GENERATE)
                || target instanceof JavaMethod tm && tm.getMethodNode().contains(jadx.core.dex.attributes.AFlag.DONT_GENERATE)
                || source.getDeclaringClass() != null && source.getDeclaringClass().isNoCode()
                || target.getDeclaringClass() != null && target.getDeclaringClass().isNoCode();
        edges.add(new ReferenceEdge(from, to, relation, hidden ? OBSERVED : RESOLVED, evidence, List.of(), "UNAVAILABLE", null));
    }
    private static final class ClassLookup {
        private final Map<String, JavaClass> unique = new HashMap<>();
        private final Set<String> ambiguous = new HashSet<>();

        ClassLookup(JadxDecompiler jadx) {
            for (JavaClass cls : jadx.getClassesWithInners()) {
                String descriptor = JadxSymbolAdapter.descriptor(cls.getRawName());
                if (unique.putIfAbsent(descriptor, cls) != null) ambiguous.add(descriptor);
            }
        }

        JavaClass unique(SymbolRef ref) {
            String descriptor = ref.originalClassDescriptor();
            return ambiguous.contains(descriptor) ? null : unique.get(descriptor);
        }
    }

    private static Map<SymbolRef, List<ReferenceEdge.Site>> sitesByTarget(JadxDecompiler jadx, ClassLookup classes,
            SymbolRef callerRef, String session, long revision, long epoch, String settings,
            SourceExtractor sourceExtractor) {
        JavaClass caller = classes.unique(callerRef);
        if (caller == null || caller.isNoCode()) return Map.of();
        JavaMethod method = JadxSymbolAdapter.matchingMethod(caller, callerRef);
        if (method == null) return Map.of();
        var data = sourceExtractor.extract(jadx, caller, callerRef, true, session, revision, epoch, settings);
        if (data.methodRange() == null) return Map.of();
        var metadata = caller.getTopParentClass().getCodeInfo().getCodeMetadata();
        Map<SymbolRef, List<ReferenceEdge.Site>> byTarget = new HashMap<>();
        for (var annotation : data.annotations()) {
            int offset = annotation.position().offsetUtf16();
            if (!annotation.kind().equals("REFERENCE")
                    || offset < data.methodRange().startOffsetUtf16() || offset >= data.methodRange().endOffsetUtf16()
                    || metadata.getNodeAt(offset) != method.getCodeNodeRef()) continue;
            var site = new ReferenceEdge.Site(data.ownerRef(), data.sourceSnapshotId(), annotation.position(), "EXACT");
            byTarget.computeIfAbsent(annotation.targetRef(), ignored -> new ArrayList<>()).add(site);
        }
        byTarget.replaceAll((target, sites) -> List.copyOf(sites));
        return Map.copyOf(byTarget);
    }
}
