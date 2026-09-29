package dev.libjadx.core.hierarchy;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Immutable forward and reverse graph. Duplicate definitions contribute every edge. */
public final class HierarchyGraph {
	public enum Target { IN_CENSUS_UNIQUE, IN_CENSUS_DUPLICATE, EXTERNAL_CLASSPATH, MISSING }
	public record Edge(String owner, String target, Target classification) { }
	private final InputCensus census;
	private final Map<String, List<Edge>> parents;
	private final Map<String, List<String>> children;
	private final Set<String> objectSignatures;

	public HierarchyGraph(InputCensus census, Set<String> externalTypes, Set<String> objectSignatures) {
		this.census = census;
		this.objectSignatures = Set.copyOf(objectSignatures);
		var forward = new TreeMap<String, List<Edge>>();
		var reverse = new TreeMap<String, TreeSet<String>>();
		census.classesByDescriptor().forEach((owner, definitions) -> {
			var targets = new TreeSet<String>();
			definitions.forEach(c -> targets.addAll(c.parents()));
			forward.put(owner, targets.stream().map(target -> {
				var records = census.classesByDescriptor().get(target);
				Target kind = records == null ? (externalTypes.contains(target) ? Target.EXTERNAL_CLASSPATH : Target.MISSING)
						: records.size() == 1 ? Target.IN_CENSUS_UNIQUE : Target.IN_CENSUS_DUPLICATE;
				reverse.computeIfAbsent(target, ignored -> new TreeSet<>()).add(owner);
				return new Edge(owner, target, kind);
			}).toList());
		});
		var reverseCopy = new TreeMap<String, List<String>>();
		reverse.forEach((key, values) -> reverseCopy.put(key, List.copyOf(values)));
		parents = Collections.unmodifiableMap(forward);
		children = Collections.unmodifiableMap(reverseCopy);
	}
	public InputCensus census() { return census; }
	public List<Edge> parents(String owner) { return parents.getOrDefault(owner, List.of()); }
	public List<String> children(String owner) { return children.getOrDefault(owner, List.of()); }
	/** Explicitly model Object as a terminal only for signatures proved absent from its pinned classpath declaration. */
	public boolean objectTerminal(Edge edge, String signature) {
		return edge.target().equals("Ljava/lang/Object;") && edge.classification() == Target.EXTERNAL_CLASSPATH
				&& !objectSignatures.isEmpty() && !objectSignatures.contains(signature);
	}
}
