package dev.libjadx.core.hierarchy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import dev.libjadx.core.hierarchy.InputCensus.Access;
import dev.libjadx.core.hierarchy.InputCensus.ClassRecord;
import dev.libjadx.core.hierarchy.InputCensus.MethodRecord;
import dev.libjadx.core.symbols.SymbolRef;

/** Closed-input verifier; all computation uses original immutable declarations, never Jadx candidates. */
public final class IndependentHierarchyVerifier implements RelatedHierarchyVerifier {
	private final HierarchyGraph graph;
	public IndependentHierarchyVerifier(HierarchyGraph graph) { this.graph = graph; }

	@Override public Verification verify(SymbolRef seed, VerificationBudget budget) {
		try { return new Walk(seed, budget).verify(); }
		catch (Incomplete e) { return Verification.incomplete(e.status, seed, e.descriptor, e.getMessage()); }
	}

	private final class Walk {
		private final SymbolRef seed;
		private final VerificationBudget budget;
		private long work;
		private final Map<String, ClassRecord> component = new java.util.TreeMap<>();
		private final Map<String, Set<String>> ancestors = new HashMap<>();
		private final Map<SymbolRef, SymbolRef> groups = new HashMap<>();
		Walk(SymbolRef seed, VerificationBudget budget) { this.seed = seed; this.budget = budget; }
		void step() { if (++work > budget.work()) fail(Status.RESOURCE_LIMIT, null, "Traversal work limit"); }

		Verification verify() {
			if (seed.kind() != SymbolRef.Kind.METHOD || seed.inputIdentity() != null)
				fail(Status.UNSUPPORTED_METHOD, seed.originalClassDescriptor(), "Original method key without input identity required");
			ClassRecord owner = unique(seed.originalClassDescriptor());
			MethodRecord method = owner.methods().stream().filter(m -> m.ref().equals(seed)).findFirst().orElse(null);
			if (method == null) fail(Status.NOT_FOUND, owner.descriptor(), "Seed declaration absent");
			if (!method.instance() || method.has(Access.BRIDGE | Access.SYNTHETIC) || owner.has(Access.SYNTHETIC))
				fail(Status.UNSUPPORTED_METHOD, owner.descriptor(), "Special/static/private/bridge/synthetic seed");
			collectComponent(method.signature());
			for (String name : component.keySet()) collectAncestors(name);
			validateEdges();
			var matching = new java.util.TreeMap<String, List<MethodRecord>>();
			for (var cls : component.values()) {
				var methods = new ArrayList<MethodRecord>();
				for (var m : cls.methods()) {
					step();
					// Reject the whole structural signature component: bridges can connect different erased signatures.
					if (m.ref().originalName().equals(method.ref().originalName())
							&& (m.has(Access.BRIDGE | Access.SYNTHETIC) || cls.has(Access.SYNTHETIC)))
						fail(Status.UNSUPPORTED_METHOD, cls.descriptor(), "Bridge/synthetic signature component requires persistence proof");
					if (!m.signature().equals(method.signature())) continue;
					if (Integer.bitCount(m.access() & (Access.PUBLIC | Access.PRIVATE | Access.PROTECTED)) > 1
							|| m.has(Access.ABSTRACT) && m.has(Access.STATIC | Access.PRIVATE | Access.FINAL))
						fail(Status.INVALID_HIERARCHY, cls.descriptor(), "Inconsistent method flags");
					if (!m.instance()) {
						for (String ancestor : new TreeSet<>(ancestors.get(cls.descriptor()))) {
							for (var inherited : component.get(ancestor).methods()) {
								step();
								if (inherited.instance() && inherited.signature().equals(m.signature()) && visibleIn(inherited, cls.descriptor()))
									fail(Status.INVALID_HIERARCHY, cls.descriptor(), "Static/private declaration hides inherited instance method");
							}
						}
					}
					if (m.instance()) { methods.add(m); groups.put(m.ref(), m.ref()); }
				}
				if (methods.size() > 1) fail(Status.UNSUPPORTED_METHOD, cls.descriptor(), "Multiple return descriptors for one override signature");
				matching.put(cls.descriptor(), List.copyOf(methods));
				if (cls.has(Access.FINAL) && !graph.children(cls.descriptor()).isEmpty())
					fail(Status.INVALID_HIERARCHY, cls.descriptor(), "Final class has input descendants");
			}
			// At each inheritance context, connect overriding declarations and inherited public implementations
			// of interface declarations, including contexts that do not declare the method themselves.
			for (var cls : component.values()) {
				var visible = new ArrayList<MethodRecord>(matching.get(cls.descriptor()));
				for (String parent : new TreeSet<>(ancestors.get(cls.descriptor()))) {
					for (var inherited : matching.get(parent)) {
						step();
						if (visibleIn(inherited, cls.descriptor())) visible.add(inherited);
					}
				}
				for (int i = 0; i < visible.size(); i++) for (int j = i + 1; j < visible.size(); j++) {
					step();
					var a = visible.get(i); var b = visible.get(j);
					String ao = a.ref().originalClassDescriptor(), bo = b.ref().originalClassDescriptor();
					boolean aParent = ancestors.get(bo).contains(ao), bParent = ancestors.get(ao).contains(bo);
					boolean interfaces = component.get(ao).has(Access.INTERFACE) || component.get(bo).has(Access.INTERFACE);
					if (!aParent && !bParent) {
						if (!interfaces) continue;
						if (!a.has(Access.PUBLIC) || !b.has(Access.PUBLIC))
							fail(Status.UNSUPPORTED_METHOD, cls.descriptor(), "Nonpublic inherited interface implementation");
					}
					MethodRecord parent = aParent ? a : b, child = aParent ? b : a;
					if (aParent || bParent) {
						if (!visibleIn(parent, child.ref().originalClassDescriptor())) continue;
						if (parent.has(Access.FINAL)) fail(Status.INVALID_HIERARCHY, parent.ref().originalClassDescriptor(), "Override of final declaration");
						if (parent.has(Access.PUBLIC) && !child.has(Access.PUBLIC)
								|| parent.has(Access.PROTECTED) && !child.has(Access.PUBLIC | Access.PROTECTED))
							fail(Status.INVALID_HIERARCHY, child.ref().originalClassDescriptor(), "Reduced override visibility");
					}
					if (!a.returns().equals(b.returns()))
						fail(Status.UNSUPPORTED_METHOD, cls.descriptor(), "Covariant/incompatible return family requires bridge and persistence proof");
					union(a.ref(), b.ref());
				}
			}
			SymbolRef root = find(seed);
			var family = new ArrayList<SymbolRef>();
			for (var ref : groups.keySet()) {
				step();
				if (find(ref).equals(root)) {
					if (family.size() >= budget.familyMembers()) fail(Status.RESOURCE_LIMIT, owner.descriptor(), "Family member limit");
					family.add(ref);
				}
			}
			family.sort(Comparator.comparing(SymbolRef::toString));
			return new Verification(Status.COMPLETE, seed, family, List.of());
		}

		ClassRecord unique(String descriptor) {
			var records = graph.census().classesByDescriptor().get(descriptor);
			if (records == null) fail(Status.NOT_FOUND, descriptor, "Original class absent");
			if (records.size() != 1) fail(Status.AMBIGUOUS_INPUT, descriptor, "Multiple raw definitions");
			return records.getFirst();
		}
		void collectComponent(String signature) {
			var queue = new ArrayDeque<String>(); queue.add(seed.originalClassDescriptor());
			while (!queue.isEmpty()) {
				step(); String name = queue.removeFirst();
				if (component.containsKey(name)) continue;
				if (component.size() >= budget.visitedNodes()) fail(Status.RESOURCE_LIMIT, name, "Visited node limit");
				component.put(name, unique(name));
				for (var edge : graph.parents(name)) {
					step();
					if (graph.objectTerminal(edge, signature)) continue;
					switch (edge.classification()) {
						case IN_CENSUS_UNIQUE, IN_CENSUS_DUPLICATE -> queue.add(edge.target());
						case EXTERNAL_CLASSPATH -> fail(Status.EXTERNAL_SUPERTYPE, edge.target(), "External hierarchy branch");
						case MISSING -> fail(Status.MISSING_SUPERTYPE, edge.target(), "Missing hierarchy branch");
					}
				}
				for (String child : graph.children(name)) { step(); queue.add(child); }
			}
		}
		void collectAncestors(String name) {
			Set<String> seen = new HashSet<>(); var queue = new ArrayDeque<String>(); queue.add(name);
			while (!queue.isEmpty()) {
				step(); String current = queue.removeFirst();
				for (var edge : graph.parents(current)) {
					step(); String parent = edge.target();
					if (!component.containsKey(parent)) continue;
					if (parent.equals(name)) fail(Status.INVALID_HIERARCHY, name, "Inheritance cycle");
					if (seen.add(parent)) queue.add(parent);
				}
			}
			ancestors.put(name, Set.copyOf(seen));
		}
		void validateEdges() {
			for (var cls : component.values()) {
				if (cls.has(Access.INTERFACE) && cls.superclass() != null && !cls.superclass().equals("Ljava/lang/Object;"))
					fail(Status.INVALID_HIERARCHY, cls.descriptor(), "Interface has non-Object superclass");
				for (String parent : cls.parents()) {
					step(); var target = component.get(parent);
					if (target == null) continue; // Explicitly modeled Object terminal only.
					if (parent.equals(cls.superclass()) && target.has(Access.INTERFACE)
							|| cls.interfaces().contains(parent) && !target.has(Access.INTERFACE))
						fail(Status.INVALID_HIERARCHY, cls.descriptor(), "Class/interface edge kind mismatch");
					if (!target.has(Access.PUBLIC) && !pkg(parent).equals(pkg(cls.descriptor())))
						fail(Status.INVALID_HIERARCHY, cls.descriptor(), "Inaccessible superclass/interface");
				}
			}
		}
		boolean visibleIn(MethodRecord method, String descendant) {
			if (method.has(Access.PUBLIC | Access.PROTECTED)) return true;
			return pkg(method.ref().originalClassDescriptor()).equals(pkg(descendant));
		}
		SymbolRef find(SymbolRef ref) {
			SymbolRef parent = groups.get(ref);
			while (!parent.equals(ref)) { step(); ref = parent; parent = groups.get(ref); }
			return ref;
		}
		void union(SymbolRef a, SymbolRef b) {
			a = find(a); b = find(b);
			if (a.toString().compareTo(b.toString()) <= 0) groups.put(b, a); else groups.put(a, b);
		}
	}
	private static String pkg(String descriptor) { return descriptor.substring(0, descriptor.lastIndexOf('/') + 1); }
	private static void fail(Status status, String descriptor, String reason) { throw new Incomplete(status, descriptor, reason); }
	private static final class Incomplete extends RuntimeException {
		final Status status; final String descriptor;
		Incomplete(Status status, String descriptor, String reason) { super(reason); this.status = status; this.descriptor = descriptor; }
	}
}
