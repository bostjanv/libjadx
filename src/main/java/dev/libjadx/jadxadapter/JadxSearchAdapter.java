package dev.libjadx.jadxadapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.libjadx.core.symbols.SymbolInfo;
import dev.libjadx.core.symbols.SymbolRef;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;
import jadx.api.JavaField;
import jadx.api.JavaMethod;

/** Pinned Jadx 1.5.6 conversion; raw signatures come from JadxSymbolAdapter. */
public final class JadxSearchAdapter {
	private JadxSearchAdapter() { }

	/** Original outer ownership is cheap to inspect before code generation. */
	public static Map<String, String> originalSourceOwners(JadxDecompiler jadx) {
		Map<String, String> owners = new HashMap<>();
		for (JavaClass cls : jadx.getClassesWithInners()) {
			SymbolRef ref = JadxSymbolAdapter.originalRef(cls);
			JavaClass top = cls.getOriginalTopParentClass();
			SymbolRef topRef = top == null ? null : JadxSymbolAdapter.originalRef(top);
			if (ref != null) owners.put(ref.originalClassDescriptor(),
					topRef == null ? ref.originalClassDescriptor() : topRef.originalClassDescriptor());
		}
		return Map.copyOf(owners);
	}

	public static List<SymbolInfo> members(JavaClass cls) {
		List<SymbolInfo> result = new ArrayList<>();
		SymbolRef containing = JadxSymbolAdapter.originalRef(cls);
		for (JavaMethod method : cls.getMethods()) {
			SymbolRef ref = JadxSymbolAdapter.originalRef(method);
			if (ref != null) result.add(new SymbolInfo(ref, ref.originalName(), ref.originalDescriptor(),
					method.getName(), cls.getFullName() + '.' + method.getName(), containing,
					SymbolInfo.Provenance.UNAVAILABLE));
		}
		for (JavaField field : cls.getFields()) {
			SymbolRef ref = JadxSymbolAdapter.originalRef(field);
			if (ref != null) result.add(new SymbolInfo(ref, ref.originalName(), ref.originalDescriptor(),
					field.getName(), cls.getFullName() + '.' + field.getName(), containing,
					SymbolInfo.Provenance.UNAVAILABLE));
		}
		return List.copyOf(result);
	}
}
