package dev.libjadx.core.hierarchy;

import java.util.List;
import dev.libjadx.core.symbols.SymbolRef;

/** Internal completeness evidence. COMPLETE alone does not admit a native rename. */
public interface RelatedHierarchyVerifier {
	Verification verify(SymbolRef seed, VerificationBudget budget);
	enum Status {
		COMPLETE, NOT_FOUND, AMBIGUOUS_INPUT, MISSING_SUPERTYPE, EXTERNAL_SUPERTYPE,
		UNSUPPORTED_METHOD, UNSUPPORTED_INPUT, RESOURCE_LIMIT, INVALID_HIERARCHY, INPUT_CHANGED, FAILED
	}
	record VerificationBudget(int familyMembers, int visitedNodes, long work) {
		public VerificationBudget {
			if (familyMembers < 1 || familyMembers > 64 || visitedNodes < 1 || work < 1)
				throw new IllegalArgumentException("Positive verification limits and family cap <= 64 required");
		}
		public static VerificationBudget defaults() { return new VerificationBudget(64, 10_000, 200_000); }
	}
	record Diagnostic(Status status, String descriptor, String reason) { }
	record Verification(Status status, SymbolRef seed, List<SymbolRef> members, List<Diagnostic> diagnostics, long work) {
		public Verification(Status status, SymbolRef seed, List<SymbolRef> members, List<Diagnostic> diagnostics) {
			this(status, seed, members, diagnostics, 0);
		}
		public Verification {
			members = List.copyOf(members);
			diagnostics = List.copyOf(diagnostics);
			if (work < 0 || members.size() > 64 || diagnostics.size() > 16 || (status != Status.COMPLETE && !members.isEmpty()))
				throw new IllegalArgumentException("Incomplete results cannot expose a truncated family");
		}
		public static Verification incomplete(Status status, SymbolRef seed, String descriptor, String reason) {
			return new Verification(status, seed, List.of(), List.of(new Diagnostic(status, descriptor, reason)));
		}
	}
}
