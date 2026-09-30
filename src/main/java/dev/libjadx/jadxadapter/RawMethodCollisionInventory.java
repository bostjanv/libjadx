package dev.libjadx.jadxadapter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import dev.libjadx.core.symbols.SymbolRef;
import jadx.api.JavaClass;
import jadx.api.data.IJavaNodeRef.RefType;
import jadx.api.data.impl.JadxNodeRef;

/** Pinned 1.5.6 ClassNode retains raw methods hidden by JavaClass's DONT_GENERATE filter.
 * Return type stays in identity but is excluded from the Java collision signature. */
public final class RawMethodCollisionInventory {
	private RawMethodCollisionInventory() { }
	public record Method(SymbolRef ref, String displayName, String declaringClass, String shortId, String arguments) {
		public JadxNodeRef nativeRef() { return new JadxNodeRef(RefType.METHOD, declaringClass, shortId); }
	}
	public static List<Method> methods(JavaClass cls, int remaining) {
		var raw = cls.getClassNode().getMethods();
		if (raw.size() > remaining) throw new JadxNativeEditAdapter.EditLimitException("Raw collision inventory exceeds 200000 methods per batch");
		var result = new ArrayList<Method>(); var keys = new HashSet<SymbolRef>();
		try {
			for (var method : raw) {
				var info = method.getMethodInfo(); var ref = JadxSymbolAdapter.originalRef(info);
				if (!keys.add(ref) || !ref.originalClassDescriptor().equals(JadxSymbolAdapter.descriptor(cls.getRawName()))
						|| !info.getShortId().equals(ref.originalName() + ref.originalDescriptor())
						|| info.getAlias() == null || info.getAlias().isEmpty()) throw new IllegalArgumentException();
				result.add(new Method(ref, info.getAlias(), info.getDeclClass().getRawName(), info.getShortId(),
						ref.originalDescriptor().substring(0, ref.originalDescriptor().indexOf(')') + 1)));
			}
		} catch (IllegalArgumentException failure) { throw new InvalidInventoryException(); }
		return List.copyOf(result);
	}
	/** The mapping pass uses destination namespace zero; native records take precedence in the caller. */
	public static String mappingAlias(jadx.api.JadxDecompiler engine, SymbolRef ref) {
		var tree = jadx.plugins.mappings.RenameMappingsData.getTree(engine.getRoot());
		if (tree == null) return null;
		String owner = ref.originalClassDescriptor().substring(1, ref.originalClassDescriptor().length() - 1);
		var cls = tree.getClass(owner);
		if (cls == null) return null;
		var method = cls.getMethod(ref.originalName(), ref.originalDescriptor());
		return method == null ? null : method.getDstName(0);
	}
	public static final class InvalidInventoryException extends RuntimeException {
		public InvalidInventoryException() { super("Raw method collision inventory is not representable"); }
	}
}
