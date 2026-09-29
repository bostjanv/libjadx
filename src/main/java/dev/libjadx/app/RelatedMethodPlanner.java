package dev.libjadx.app;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import dev.libjadx.core.hierarchy.RelatedHierarchyVerifier;
import dev.libjadx.core.symbols.SymbolCatalog;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.jadxadapter.JadxNativeEditAdapter;
import dev.libjadx.jadxadapter.JadxSymbolAdapter;
import jadx.api.data.impl.JadxCodeData;

/** Completeness is freshly proved inside the current exclusive edit callback. */
final class RelatedMethodPlanner {
	static final int MAX_ITEMS = 4, MAX_MEMBERS = 128;
	static final long MAX_RESERVED_WORK = 800_000;
	private int items, members;
	private long reservedWork;

	RelatedMethodPlan plan(ProjectRuntime.EditContext context, SymbolCatalog catalog,
			JadxCodeData code, SymbolRef seed, String alias, int index) {
		var budget = RelatedHierarchyVerifier.VerificationBudget.defaults();
		if (++items > MAX_ITEMS || (reservedWork += budget.work()) > MAX_RESERVED_WORK)
			throw EditBatchService.rejected(429, "RESOURCE_LIMIT", index, "Related method batch exceeds the verification budget");
		try { context.checkAnalysisBaselines(); }
		catch (ProjectRuntime.NativeEditConflictException conflict) {
			throw EditBatchService.rejected(409, "EXTERNAL_MODIFICATION_CONFLICT", index, "Project analysis files changed; explicit reload required");
		}
		var seedOwners = catalog.matching(seed.originalClassDescriptor());
		if (seedOwners.size() != 1) throw EditBatchService.rejected(seedOwners.isEmpty() ? 404 : 422,
				seedOwners.isEmpty() ? "NOT_FOUND" : "INVALID_ENTITY_ID", index, "Original related declaration is not unique");
		var seedOwner = JadxSymbolAdapter.visibleClass(context.decompiler(), seed.originalClassDescriptor(), seedOwners.getFirst().occurrence());
		var seedCount = seedOwner == null ? 0 : seedOwner.getClassNode().getMethods().stream()
				.filter(m -> m.getMethodInfo().getShortId().equals(seed.originalName() + seed.originalDescriptor())).count();
		if (seedCount != 1) throw EditBatchService.rejected(seedCount == 0 ? 404 : 422,
				seedCount == 0 ? "NOT_FOUND" : "INVALID_ENTITY_ID", index, "Original related declaration is not unique");
		RelatedHierarchyVerifier.Verification verified;
		try { verified = context.verifyRelated(seed, budget); }
		catch (RuntimeException failure) { throw incomplete(RelatedHierarchyVerifier.Status.FAILED, index); }
		if (verified.status() != RelatedHierarchyVerifier.Status.COMPLETE) throw incomplete(verified.status(), index);
		var family = verified.members();
		if (!verified.seed().equals(seed) || family.isEmpty() || family.size() > 64
				|| new HashSet<>(family).size() != family.size() || !family.contains(seed)
				|| family.stream().anyMatch(r -> r.kind() != SymbolRef.Kind.METHOD || r.inputIdentity() != null))
			throw EditBatchService.rejected(422, "INVALID_ENTITY_ID", index, "Related family has invalid original identities");
		if ((members += family.size()) > MAX_MEMBERS)
			throw EditBatchService.rejected(429, "RESOURCE_LIMIT", index, "Related method batch exceeds the family budget");
		var result = new ArrayList<RelatedMethodPlan.Member>();
		for (var ref : family.stream().sorted(Comparator.comparing(SymbolRef::originalClassDescriptor)
				.thenComparing(SymbolRef::originalName).thenComparing(SymbolRef::originalDescriptor)).toList()) {
			var owners = catalog.matching(ref.originalClassDescriptor());
			if (owners.size() != 1) throw unrepresentable(index);
			var cls = JadxSymbolAdapter.visibleClass(context.decompiler(), ref.originalClassDescriptor(), owners.getFirst().occurrence());
			var targets = cls == null ? List.<JadxNativeEditAdapter.Target>of() : JadxNativeEditAdapter.memberTargets(cls)
					.stream().filter(t -> t.ref().equals(ref)).toList();
			if (targets.size() != 1 || !targets.getFirst().editable()) throw unrepresentable(index);
			var target = targets.getFirst(); var key = target.nativeRef();
			if (!key.getShortId().equals(ref.originalName() + ref.originalDescriptor())
					|| cls.getClassNode().getMethods().stream().filter(m -> m.getMethodInfo().getShortId().equals(ref.originalName() + ref.originalDescriptor())).count() != 1)
				throw unrepresentable(index);
			if (JadxNativeEditAdapter.renameCount(code, key) > 1)
				throw EditBatchService.rejected(422, "INVALID_ENTITY_ID", index, "Multiple native renames share a related declaration key");
			result.add(new RelatedMethodPlan.Member(ref, key.getDeclaringClass(), key.getShortId(), target.argumentDescriptor()));
		}
		return new RelatedMethodPlan(seed, result, alias);
	}

	static EditBatchService.Rejected incomplete(RelatedHierarchyVerifier.Status status, int index) {
		return switch (status) {
			case NOT_FOUND -> EditBatchService.rejected(404, "NOT_FOUND", index, "Original related declaration was not found");
			case AMBIGUOUS_INPUT -> EditBatchService.rejected(422, "INVALID_ENTITY_ID", index, "Related method original input identity is ambiguous");
			case RESOURCE_LIMIT -> EditBatchService.rejected(429, "RESOURCE_LIMIT", index, "Related method family exceeds the verification budget");
			case INPUT_CHANGED -> EditBatchService.rejected(409, "EXTERNAL_MODIFICATION_CONFLICT", index, "Project inputs changed during related-method verification");
			case FAILED -> EditBatchService.rejected(500, "INTERNAL_ERROR", index, "Related method verification failed");
			case COMPLETE -> throw new IllegalArgumentException("COMPLETE requires family planning");
			default -> EditBatchService.rejected(422, "UNSUPPORTED_CAPABILITY", index, "Related method family is not complete for this project (" + status + ")");
		};
	}
	private static EditBatchService.Rejected unrepresentable(int index) {
		return EditBatchService.rejected(422, "INVALID_ENTITY_ID", index, "Related method target cannot be represented by one native declaration key");
	}
}
