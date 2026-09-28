package dev.libjadx.core.hierarchy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import dev.libjadx.core.symbols.SymbolRef;

/** Only original declaration metadata. Origins are private to this process, never SymbolRef provenance. */
public record InputCensus(List<InputOrigin> inputs, Map<String, List<ClassRecord>> classesByDescriptor) {
	public record InputOrigin(int position, String canonicalPath) { }
	public record MethodRecord(SymbolRef ref, int access) {
		public MethodRecord {
			if (ref.kind() != SymbolRef.Kind.METHOD || ref.inputIdentity() != null)
				throw new IllegalArgumentException("Original method without public input identity required");
		}
		public String arguments() { return ref.originalDescriptor().substring(0, ref.originalDescriptor().indexOf(')') + 1); }
		public String returns() { return ref.originalDescriptor().substring(ref.originalDescriptor().indexOf(')') + 1); }
		public String signature() { return ref.originalName() + arguments(); }
		public boolean has(int flag) { return (access & flag) != 0; }
		public boolean instance() { return !ref.originalName().startsWith("<") && !has(Access.STATIC | Access.PRIVATE); }
	}
	public record ClassRecord(String descriptor, InputOrigin origin, String superclass,
			List<String> interfaces, int access, List<MethodRecord> methods) {
		public ClassRecord {
			SymbolRef.validateClassDescriptor(descriptor);
			if (superclass != null) SymbolRef.validateClassDescriptor(superclass);
			interfaces = List.copyOf(interfaces);
			interfaces.forEach(SymbolRef::validateClassDescriptor);
			methods = List.copyOf(methods);
			var keys = new java.util.HashSet<SymbolRef>();
			for (var method : methods) {
				if (!descriptor.equals(method.ref().originalClassDescriptor()) || !keys.add(method.ref()))
					throw new IllegalArgumentException("Invalid or duplicate declared method key");
			}
		}
		public boolean has(int flag) { return (access & flag) != 0; }
		public List<String> parents() {
			var result = new ArrayList<>(interfaces);
			if (superclass != null) result.add(superclass);
			return result.stream().distinct().sorted().toList();
		}
	}
	/** JVM and Dalvik declaration flag bits, independently of Jadx mutable nodes. */
	public static final class Access {
		private Access() { }
		public static final int PUBLIC = 1, PRIVATE = 2, PROTECTED = 4, STATIC = 8, FINAL = 16,
				BRIDGE = 64, INTERFACE = 512, ABSTRACT = 1024, SYNTHETIC = 4096;
	}
	public InputCensus {
		inputs = List.copyOf(inputs);
		var copy = new TreeMap<String, List<ClassRecord>>();
		classesByDescriptor.forEach((key, values) -> copy.put(key, List.copyOf(values)));
		classesByDescriptor = Collections.unmodifiableMap(copy);
	}
}
