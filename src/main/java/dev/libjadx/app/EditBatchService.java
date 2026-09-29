package dev.libjadx.app;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import dev.libjadx.core.ProjectSnapshot;
import dev.libjadx.core.edits.EditDtos;
import dev.libjadx.core.edits.NativeDeclarationValidation;
import dev.libjadx.core.edits.EditDtos.ItemError;
import dev.libjadx.core.edits.EditDtos.ItemResult;
import dev.libjadx.core.edits.EditDtos.Operation;
import dev.libjadx.core.edits.EditDtos.Request;
import dev.libjadx.core.edits.EditDtos.Result;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.core.symbols.SymbolCatalog;
import dev.libjadx.core.symbols.SymbolLookup;
import dev.libjadx.core.symbols.SymbolResolution;
import dev.libjadx.jadxadapter.JadxNativeEditAdapter;
import dev.libjadx.jadxadapter.JadxNativeEditAdapter.Target;
import dev.libjadx.jadxadapter.JadxSymbolAdapter;
import dev.libjadx.jadxadapter.JadxSourceAdapter;
import dev.libjadx.core.source.DecompileResult.Variable;
import dev.libjadx.project.NativeProjectDocument;
import jadx.api.data.impl.JadxCodeData;

/** Validates and stages one native declaration batch under a single exclusive runtime lease. */
public final class EditBatchService {
	@FunctionalInterface interface StageHook { void beforeItem(int index); }

	private final ProjectRuntime runtime;
	private final StageHook hook;

	public EditBatchService(ProjectRuntime runtime) { this(runtime, ignored -> { }); }
	EditBatchService(ProjectRuntime runtime, StageHook hook) { this.runtime = runtime; this.hook = hook; }

	public Result apply(Request request) {
		if (request.items().isEmpty() || request.items().size() > 64) {
			throw rejected(400, "INVALID_REQUEST", -1, "Batch requires 1 to 64 items");
		}
		return runtime.withExclusiveEdit(context -> applyAdmitted(context, request));
	}

	private Result applyAdmitted(ProjectRuntime.EditContext context, Request request) {
		ProjectSnapshot before = context.before();
		String session = before.revisions().sessionId();
		long revision = before.revisions().logicalRevision();
		if (request.expectedSessionId() != null && (!session.equals(request.expectedSessionId())
				|| revision != request.expectedLogicalRevision())) {
			throw rejected(409, "STALE_REVISION", -1, "Expected project revision is stale");
		}
		JadxCodeData admitted = context.codeDataCopy();
		SymbolCatalog catalog = new SymbolCatalog(JadxSymbolAdapter.classes(context.decompiler()), session,
				revision, 0, SymbolCatalog.newCursorKey());
		List<Target> visibleMembers = new ArrayList<>();
		Map<String, List<Target>> memberOwners = new HashMap<>();
		List<Planned> plan = new ArrayList<>();
		Map<String, JadxSourceAdapter.SourceData> sources = new HashMap<>();
		long[] scopedBudget = new long[2];
		Set<String> editedKeys = new HashSet<>();
		for (int i = 0; i < request.items().size(); i++) {
			Operation item = request.items().get(i);
			SymbolRef ref = item.target();
			boolean scoped = item.kind() == EditDtos.Kind.RENAME_PARAMETER;
			if (scoped && (request.expectedSessionId() == null || ref.kind() != SymbolRef.Kind.METHOD
					|| item.parameterIndex() == null || item.parameterIndex() < 0 || item.parameterIndex() > 254
					|| item.sourceSnapshotId() == null || !item.sourceSnapshotId().matches("sha256:[0-9a-f]{64}"))) {
				throw rejected(400, "INVALID_REQUEST", i, "Parameter edit requires method, index, source snapshot and revision preconditions");
			}
			if (ref.inputIdentity() != null) {
				throw rejected(422, "UNSUPPORTED_CAPABILITY", i, "Exact per-input provenance is unavailable");
			}
			SymbolResolution resolution = SymbolLookup.resolve(catalog, ref, entry -> {
				var cls = JadxSymbolAdapter.visibleClass(context.decompiler(), ref.originalClassDescriptor(), entry.occurrence());
				return cls == null ? List.of() : JadxSymbolAdapter.matchingMembers(cls, ref);
			});
			if (resolution.outcome() == SymbolResolution.Outcome.NOT_FOUND) {
				throw rejected(404, "NOT_FOUND", i, "Original declaration is not Jadx-visible");
			}
			if (resolution.outcome() == SymbolResolution.Outcome.AMBIGUOUS) {
				throw rejected(422, "INVALID_ENTITY_ID", i, "Original declaration is ambiguous");
			}
			var cls = JadxSymbolAdapter.visibleClass(context.decompiler(), ref.originalClassDescriptor(),
					catalog.matching(ref.originalClassDescriptor()).getFirst().occurrence());
			List<Target> matches;
			if (ref.kind() == SymbolRef.Kind.CLASS) {
				Target target = cls == null ? null : JadxNativeEditAdapter.classTarget(cls);
				matches = target == null ? List.of() : List.of(target);
			} else {
				List<Target> members = memberOwners.computeIfAbsent(ref.originalClassDescriptor(), ignored -> {
					List<Target> targets = cls == null ? List.of() : JadxNativeEditAdapter.memberTargets(cls);
					if (visibleMembers.size() + targets.size() > 200_000) throw new JadxNativeEditAdapter.EditLimitException();
					visibleMembers.addAll(targets);
					return targets;
				});
				matches = members.stream().filter(target -> target.ref().equals(ref)).toList();
			}
			if (matches.isEmpty()) throw rejected(422, "UNSUPPORTED_CAPABILITY", i, "Visible declaration has no verified native edit key");
			if (matches.size() != 1) throw rejected(422, "INVALID_ENTITY_ID", i, "Original declaration is ambiguous");
			Target target = matches.getFirst();
			if (!target.editable()) {
				throw rejected(422, "UNSUPPORTED_CAPABILITY", i, "This declaration cannot be edited safely");
			}
			String key = item.kind() + ":" + target.nativeRef().getType() + ":"
					+ target.nativeRef().getDeclaringClass() + ":" + target.nativeRef().getShortId()
					+ (scoped ? ":" + item.parameterIndex() : "");
			if (!editedKeys.add(key)) throw rejected(400, "INVALID_REQUEST", i, "Duplicate edit of one native declaration key");
			boolean changed;
			if (scoped) {
				validateName(item.newName(), i);
				JadxSourceAdapter.SourceData source = sources.computeIfAbsent(ref.originalClassDescriptor(), ignored -> {
					var extracted = JadxSourceAdapter.extract(context.decompiler(), cls, SymbolRef.classRef(ref.originalClassDescriptor()),
							false, session, revision, context.publicationEpoch(), context.settings().fingerprint());
					scopedBudget[0] += extracted.source() == null ? 0 : extracted.source().length() * 2L;
					scopedBudget[1] += extracted.variables().size();
					if (scopedBudget[0] > 16 * 1024 * 1024 || scopedBudget[1] > 20_000)
						throw new JadxNativeEditAdapter.EditLimitException("Scoped batch source metadata exceeds the request budget");
					return extracted;
				});
				if (!item.sourceSnapshotId().equals(source.sourceSnapshotId()))
					throw rejected(409, "STALE_REVISION", i, "Parameter source snapshot is stale");
				var matchesVariables = source.variables().stream().filter(v -> v.method().equals(ref)
						&& v.kind().equals("PARAMETER") && Objects.equals(v.parameterIndex(), item.parameterIndex())).toList();
				if (matchesVariables.size() != 1 || !matchesVariables.getFirst().persistability().equals("SUPPORTED"))
					throw rejected(422, "UNSUPPORTED_CAPABILITY", i, "Parameter has no unique persistable declaration in this snapshot");
				var scope = JadxNativeEditAdapter.parameterKey(item.parameterIndex());
				if (JadxNativeEditAdapter.scopedRenameCount(admitted, target.nativeRef(), scope) > 1
						|| JadxNativeEditAdapter.hasOtherScopedRenames(admitted, target.nativeRef()))
					throw rejected(422, "INVALID_ENTITY_ID", i, "Native scoped rename identity is ambiguous");
				String prior = JadxNativeEditAdapter.existingScopedRename(admitted, target.nativeRef(), scope);
				changed = !Objects.equals(prior, item.newName())
						&& !Objects.equals(matchesVariables.getFirst().displayName(), item.newName());
			} else if (item.kind() == EditDtos.Kind.RENAME) {
				validateName(item.newName(), i);
				if (JadxNativeEditAdapter.renameCount(admitted, target.nativeRef()) > 1) {
					throw rejected(422, "INVALID_ENTITY_ID", i, "Multiple native renames share this declaration key");
				}
				String prior = JadxNativeEditAdapter.existingRename(admitted, target.nativeRef());
				changed = !Objects.equals(prior, item.newName())
						&& !(prior == null && Objects.equals(target.displayName(), item.newName()));
			} else {
				validateComment(item, i);
				if (JadxNativeEditAdapter.lineCommentCount(admitted, target.nativeRef()) > 1) {
					throw rejected(422, "INVALID_ENTITY_ID", i, "Multiple native LINE comments share this declaration key");
				}
				changed = !Objects.equals(JadxNativeEditAdapter.existingComment(admitted, target.nativeRef()), item.comment());
			}
			plan.add(new Planned(i, item, target, changed));
		}
		validateCollisions(catalog, visibleMembers, plan);
		validateParameterCollisions(sources, plan);
		JadxCodeData working = NativeProjectDocument.copyCodeData(admitted);
		List<ItemResult> results = new ArrayList<>();
		int failedAt = -1;
		for (Planned item : plan) {
			try {
				hook.beforeItem(item.index());
				if (item.changed()) {
					JadxCodeData candidate = NativeProjectDocument.copyCodeData(working);
					if (item.operation().kind() == EditDtos.Kind.RENAME) {
						JadxNativeEditAdapter.rename(candidate, item.target().nativeRef(), item.operation().newName());
					} else if (item.operation().kind() == EditDtos.Kind.RENAME_PARAMETER) {
							JadxNativeEditAdapter.renameParameter(candidate, item.target().nativeRef(),
									item.operation().parameterIndex(), item.operation().newName());
						} else JadxNativeEditAdapter.comment(candidate, item.target().nativeRef(), item.operation().comment());
					working = candidate;
				}
				results.add(result(item, item.changed() ? "APPLIED" : "SKIPPED", item.changed() ? null : "NO_CHANGE"));
			} catch (RuntimeException unexpected) {
				failedAt = item.index();
				results.add(result(item, "FAILED", "Native edit staging failed"));
				break;
			}
		}
		if (failedAt >= 0) {
			for (int i = failedAt + 1; i < plan.size(); i++) results.add(result(plan.get(i), "SKIPPED", "NOT_EXECUTED"));
		}
		boolean effective = results.stream().anyMatch(item -> item.status().equals("APPLIED"));
		ProjectSnapshot after = effective ? context.commit(working) : before;
		return new Result(failedAt >= 0 ? "PARTIAL" : effective ? "APPLIED" : "NO_CHANGE", session,
				revision, after.revisions().logicalRevision(), after.revisions().indexRevision(), after.dirty(), false,
				results, failedAt >= 0 ? List.of(effective
						? "A verified prefix was committed; later items were not executed"
						: "No edits were committed; later items were not executed") : List.of());
	}

	private static ItemResult result(Planned planned, String status, String message) {
		return new ItemResult(planned.index(), status, planned.operation().kind(), planned.operation().target(), List.of(), message, planned.operation().parameterIndex(), planned.operation().sourceSnapshotId());
	}

	private static void validateCollisions(SymbolCatalog catalog, List<Target> visibleMembers, List<Planned> plan) {
		Map<SymbolRef, String> renamed = new HashMap<>();
		for (Planned item : plan) {
			if (item.operation().kind() == EditDtos.Kind.RENAME && item.changed()) {
				renamed.put(item.operation().target(), item.operation().newName());
			}
		}
		for (Planned item : plan) {
			if (item.operation().kind() != EditDtos.Kind.RENAME || !item.changed()) continue;
			Target target = item.target();
			String candidate = item.operation().newName();
			if (target.ref().kind() == SymbolRef.Kind.CLASS) {
				for (SymbolCatalog.Entry entry : catalog.entries()) {
					var other = entry.info();
					if (!other.ref().equals(target.ref())
							&& JadxNativeEditAdapter.classCollisionScope(other.ref()).equals(target.collisionScope())
							&& candidate.equals(renamed.getOrDefault(other.ref(), other.displayName()))) {
						throw rejected(400, "INVALID_REQUEST", item.index(), "Alias collides with another visible declaration");
					}
				}
				continue;
			}
			for (Target other : visibleMembers) {
				if (other == target || other.ref().equals(target.ref())) continue;
				if (other.ref().kind() != target.ref().kind()
						|| !other.collisionScope().equals(target.collisionScope())) continue;
				if (target.ref().kind() == SymbolRef.Kind.METHOD
						&& !other.argumentDescriptor().equals(target.argumentDescriptor())) continue;
				if (candidate.equals(renamed.getOrDefault(other.ref(), other.displayName()))) {
					throw rejected(400, "INVALID_REQUEST", item.index(), "Alias collides with another visible declaration");
				}
			}
		}
	}

	private static void validateParameterCollisions(Map<String, JadxSourceAdapter.SourceData> sources, List<Planned> plan) {
		record Key(SymbolRef method, Integer index) { }
		Map<Key, String> proposed = new HashMap<>();
		for (Planned item : plan) {
			if (item.operation().kind() == EditDtos.Kind.RENAME_PARAMETER)
				proposed.put(new Key(item.operation().target(), item.operation().parameterIndex()), item.operation().newName());
		}
		for (Planned item : plan) {
			Operation operation = item.operation();
			if (operation.kind() != EditDtos.Kind.RENAME_PARAMETER || !item.changed()) continue;
			for (Variable variable : sources.get(operation.target().originalClassDescriptor()).variables()) {
				if (!variable.method().equals(operation.target())) continue;
				if (variable.kind().equals("PARAMETER") && Objects.equals(variable.parameterIndex(), operation.parameterIndex())) continue;
				String name = variable.displayName();
				// Also reject transient collisions: a committed prefix must be safe if staging fails.
				if (operation.newName().equals(name))
					throw rejected(400, "INVALID_REQUEST", item.index(), "Alias collides with a declared variable in this method");
				if (variable.kind().equals("PARAMETER"))
					name = proposed.getOrDefault(new Key(variable.method(), variable.parameterIndex()), name);
				if (operation.newName().equals(name))
					throw rejected(400, "INVALID_REQUEST", item.index(), "Aliases in the batch collide within one method");
			}
		}
	}

	private static void validateName(String name, int index) {
		if (!NativeDeclarationValidation.validName(name)) {
			throw rejected(400, "INVALID_REQUEST", index, "newName must be a nonreserved ASCII Java identifier of at most 128 characters");
		}
	}

	private static void validateComment(Operation item, int index) {
		String value = item.comment();
		if (!"LINE".equals(item.style())) throw rejected(422, "UNSUPPORTED_CAPABILITY", index, "Only LINE declaration comments are supported");
		if (!NativeDeclarationValidation.validLineComment(value)) {
			throw rejected(400, "INVALID_REQUEST", index, "comment must be one nonempty line within 4096 code points and 16 KiB");
		}
	}

	private static Rejected rejected(int status, String code, int index, String message) {
		return new Rejected(status, code, message,
				index < 0 ? List.of() : List.of(new ItemError(index, code, message)));
	}

	private record Planned(int index, Operation operation, Target target, boolean changed) { }

	public static final class Rejected extends RuntimeException {
		private final int status;
		private final String code;
		private final List<ItemError> itemErrors;
		private Rejected(int status, String code, String message, List<ItemError> itemErrors) {
			super(message);
			this.status = status;
			this.code = code;
			this.itemErrors = itemErrors;
		}
		public int status() { return status; }
		public String code() { return code; }
		public List<ItemError> itemErrors() { return itemErrors; }
	}
}
