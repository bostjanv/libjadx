package dev.libjadx.jadxadapter;

import java.util.ArrayList;
import java.util.List;

import dev.libjadx.core.symbols.ClassInfo;
import dev.libjadx.core.symbols.SymbolInfo;
import dev.libjadx.core.symbols.SymbolRef;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;
import jadx.api.JavaField;
import jadx.api.JavaMethod;
import jadx.core.codegen.TypeGen;
import jadx.core.dex.info.FieldInfo;
import jadx.core.dex.info.MethodInfo;

/** Audited against jadx-core 1.5.6, commit 4c0ac37699aa8c9803f1c73cfaacd9205acb044b. */
public final class JadxSymbolAdapter {
	private JadxSymbolAdapter() { }

	/** No member access or class code generation during enumeration. */
	public static List<ClassInfo> classes(JadxDecompiler jadx) {
		List<ClassInfo> result = new ArrayList<>();
		long copiedCharacters = 0;
		for (JavaClass cls : jadx.getClassesWithInners()) {
			if (result.size() >= 100_000) throw new CatalogLimitException();
			String raw = cls.getRawName();
			String displayName = cls.getName();
			String displayQualifiedName = cls.getFullName();
			copiedCharacters += raw.length() + displayName.length() + displayQualifiedName.length();
			if (copiedCharacters > 8_000_000) throw new CatalogLimitException();
			SymbolRef ref;
			try { ref = SymbolRef.classRef(descriptor(raw)); }
			catch (IllegalArgumentException unrepresentable) { continue; }
			SymbolRef parent = null;
			if (cls.getDeclaringClass() != null) {
				try { parent = SymbolRef.classRef(descriptor(cls.getDeclaringClass().getRawName())); }
				catch (IllegalArgumentException unrepresentable) { /* Parent is not representable as a canonical ref. */ }
			}
			result.add(new ClassInfo(ref, raw, displayName,
					displayQualifiedName, cls.isInner(), !cls.isNoCode(), parent, SymbolInfo.Provenance.UNAVAILABLE));
		}
		return List.copyOf(result);
	}

	public static JavaClass visibleClass(JadxDecompiler jadx, String descriptor, int occurrence) {
		int seen = 0;
		for (JavaClass cls : jadx.getClassesWithInners()) {
			if (descriptor(cls.getRawName()).equals(descriptor)) {
				if (seen++ == occurrence) return cls;
			}
		}
		return null;
	}

	/** Called only after the requested class has been selected inside a primary-engine lease. */
	public static List<SymbolInfo> matchingMembers(JavaClass cls, SymbolRef ref) {
		List<SymbolInfo> found = new ArrayList<>();
		SymbolRef owner = SymbolRef.classRef(ref.originalClassDescriptor());
		if (ref.kind() == SymbolRef.Kind.METHOD) {
			for (JavaMethod method : cls.getMethods()) {
				// MethodInfo is an internal pinned API. getName/getArgumentsTypes/getReturnType
				// retain the original signature, unlike JavaMethod's alias-facing methods.
				MethodInfo info = method.getMethodNode().getMethodInfo();
				String rawDescriptor = methodDescriptor(info);
				if (ref.originalName().equals(info.getName()) && ref.originalDescriptor().equals(rawDescriptor)) {
					found.add(new SymbolInfo(new SymbolRef(SymbolRef.Kind.METHOD, ref.originalClassDescriptor(), null,
							info.getName(), rawDescriptor), info.getName(), rawDescriptor,
							method.getName(), cls.getFullName() + '.' + method.getName(),
							owner, SymbolInfo.Provenance.UNAVAILABLE));
				}
			}
		} else if (ref.kind() == SymbolRef.Kind.FIELD) {
			for (JavaField field : cls.getFields()) {
				FieldInfo info = field.getFieldNode().getFieldInfo();
				String rawDescriptor = TypeGen.signature(info.getType());
				if (ref.originalName().equals(info.getName()) && ref.originalDescriptor().equals(rawDescriptor)) {
					found.add(new SymbolInfo(new SymbolRef(SymbolRef.Kind.FIELD, ref.originalClassDescriptor(), null,
							info.getName(), rawDescriptor), info.getName(), rawDescriptor,
							field.getName(), cls.getFullName() + '.' + field.getName(),
							owner, SymbolInfo.Provenance.UNAVAILABLE));
				}
			}
		}
		return List.copyOf(found);
	}

	private static String methodDescriptor(MethodInfo info) {
		StringBuilder descriptor = new StringBuilder("(");
		info.getArgumentsTypes().forEach(type -> descriptor.append(TypeGen.signature(type)));
		return descriptor.append(')').append(TypeGen.signature(info.getReturnType())).toString();
	}

	public static String descriptor(String rawDottedName) {
		return "L" + rawDottedName.replace('.', '/') + ";";
	}

	public static final class CatalogLimitException extends RuntimeException {
		public CatalogLimitException() { super("Jadx-visible class catalog exceeds its 100000-class or 8000000-character in-memory limit"); }
	}
}
