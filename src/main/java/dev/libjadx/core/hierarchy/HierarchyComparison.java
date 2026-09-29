package dev.libjadx.core.hierarchy;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import dev.libjadx.core.symbols.SymbolRef;

/** Diagnostic comparison only; an agreement never strengthens the independent completeness result. */
public record HierarchyComparison(Status status, Set<Difference> differences,
		Map<SymbolRef, List<SymbolRef>> candidatesBySeed, List<SymbolRef> missing, List<SymbolRef> extra) {
	public enum Status { EXACT_AGREEMENT, JADX_CANDIDATE_MISMATCH, INDEPENDENT_INCOMPLETE }
	public enum Difference { MISSING_MEMBERS, EXTRA_MEMBERS, SEED_DEPENDENT, BRIDGE_SYNTHETIC }
	public HierarchyComparison {
		differences = Set.copyOf(differences);
		var copy = new java.util.HashMap<SymbolRef, List<SymbolRef>>();
		candidatesBySeed.forEach((seed, refs) -> copy.put(seed, List.copyOf(refs)));
		candidatesBySeed = Map.copyOf(copy); missing = List.copyOf(missing); extra = List.copyOf(extra);
	}
	public static HierarchyComparison compare(RelatedHierarchyVerifier.Verification independent,
			Map<SymbolRef, List<SymbolRef>> candidates, Set<SymbolRef> bridges) {
		if (independent.status() != RelatedHierarchyVerifier.Status.COMPLETE)
			return new HierarchyComparison(Status.INDEPENDENT_INCOMPLETE, Set.of(), Map.of(), List.of(), List.of());
		var expected = Set.copyOf(independent.members());
		var missing = new TreeSet<SymbolRef>(Comparator.comparing(SymbolRef::toString));
		var extra = new TreeSet<SymbolRef>(Comparator.comparing(SymbolRef::toString));
		var kinds = new java.util.HashSet<Difference>();
		var sets = new java.util.HashSet<Set<SymbolRef>>();
		for (SymbolRef seed : independent.members()) {
			var actual = Set.copyOf(candidates.getOrDefault(seed, List.of())); sets.add(actual);
			for (var ref : expected) if (!actual.contains(ref)) missing.add(ref);
			for (var ref : actual) if (!expected.contains(ref)) extra.add(ref);
		}
		if (!missing.isEmpty()) kinds.add(Difference.MISSING_MEMBERS);
		if (!extra.isEmpty()) kinds.add(Difference.EXTRA_MEMBERS);
		if (sets.size() > 1) kinds.add(Difference.SEED_DEPENDENT);
		if (java.util.stream.Stream.concat(missing.stream(), extra.stream()).anyMatch(bridges::contains))
			kinds.add(Difference.BRIDGE_SYNTHETIC);
		return new HierarchyComparison(kinds.isEmpty() ? Status.EXACT_AGREEMENT : Status.JADX_CANDIDATE_MISMATCH,
				kinds, candidates, List.copyOf(missing), List.copyOf(extra));
	}
}
