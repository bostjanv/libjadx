package dev.libjadx.app;

import java.util.List;
import dev.libjadx.core.symbols.SymbolRef;
import jadx.api.data.IJavaNodeRef.RefType;
import jadx.api.data.impl.JadxNodeRef;

/** Immutable original identities and copied native keys; no live Jadx state survives planning. */
record RelatedMethodPlan(SymbolRef seed, List<Member> members, String alias) {
	RelatedMethodPlan { members = List.copyOf(members); }
	List<SymbolRef> affectedRefs() { return members.stream().map(Member::ref).toList(); }
	record Member(SymbolRef ref, String declaringClass, String shortId, String arguments) {
		JadxNodeRef nativeRef() { return new JadxNodeRef(RefType.METHOD, declaringClass, shortId); }
	}
}
