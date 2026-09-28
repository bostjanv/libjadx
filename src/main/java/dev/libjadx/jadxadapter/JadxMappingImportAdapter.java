package dev.libjadx.jadxadapter;

import static dev.libjadx.core.mappings.MappingImportDtos.*;
import static dev.libjadx.jadxadapter.JadxTinyMappings.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import dev.libjadx.core.edits.NativeDeclarationValidation;
import dev.libjadx.core.mappings.MappingExportDtos;
import dev.libjadx.core.symbols.SymbolRef;
import jadx.api.JadxDecompiler;
import jadx.api.data.CommentStyle;
import jadx.api.data.IJavaNodeRef.RefType;
import jadx.api.data.impl.JadxCodeData;
import jadx.api.data.impl.JadxNodeRef;
import jadx.core.codegen.TypeGen;
import jadx.core.dex.nodes.ClassNode;
import net.fabricmc.mappingio.tree.MappingTreeView;

/** Pure preflight over pinned declaration metadata; never generates Java or mutates the engine. */
public final class JadxMappingImportAdapter {
	public record Change(SymbolRef target, String alias, String comment) {
		public JadxNodeRef nativeRef() {
			String owner = target.originalClassDescriptor().substring(1, target.originalClassDescriptor().length() - 1).replace('/', '.');
			return switch (target.kind()) {
				case CLASS -> JadxNodeRef.forCls(owner);
				case METHOD -> new JadxNodeRef(RefType.METHOD, owner, target.originalName() + target.originalDescriptor());
				case FIELD -> new JadxNodeRef(RefType.FIELD, owner, target.originalName() + ":" + target.originalDescriptor());
			};
		}
	}
	public record Plan(MappingExportDtos.Counts parsed, EditCounts applied, EditCounts unchanged, List<Change> changes) {
		public Plan { changes = List.copyOf(changes); }
	}
	private record Target(SymbolRef ref, JadxNodeRef nativeRef, String originalAlias, String effectiveAlias,
			boolean editable) { }

	public Plan plan(JadxDecompiler engine, JadxCodeData code, byte[] acceptedBytes, boolean attached, byte[] input) throws IOException {
		Budget budget = new Budget();
		budget.bytes(input.length * 5L + 2048L * (code.getRenames().size() + code.getComments().size()));
		for (var rename : code.getRenames()) { budget.string(rename.getNewName()); budgetRef(budget, rename.getNodeRef()); }
		for (var comment : code.getComments()) { budget.string(comment.getComment()); budgetRef(budget, comment.getNodeRef()); }
		var incoming = parse(input, budget);
		if (!incoming.getSrcNamespace().equals("original") || !incoming.getDstNamespaces().equals(List.of("mapped"))) {
			throw unsupported("IMPORT_NAMESPACES_OR_INVERSION", null);
		}
		var baseline = accepted(engine, acceptedBytes, attached, budget);
		// Catalog only existing Jadx-visible metadata, with explicit bounds, never public getMethods().
		Map<String, ClassNode> owners = new LinkedHashMap<>();
		for (var cls : engine.getClassesWithInners()) {
			budget.bytes(256); budget.string(cls.getRawName()); budget.string(cls.getFullName());
			if (owners.size() >= MappingExportDtos.MAX_ENTRIES) throw limit();
			String path = cls.getRawName().replace('.', '/');
			if (owners.put(path, cls.getClassNode()) != null) throw new Problem(422, "INVALID_ENTITY_ID", "AMBIGUOUS_OWNER", "L" + path + ";");
		}
		Map<String, Target> targets = new LinkedHashMap<>();
		Map<String, List<Target>> members = new HashMap<>();
		// Accepted source must be valid too, even for records untouched by this request.
		for (var cls : baseline.getClasses()) {
			Target owner = resolve(owners, members, targets, budget, cls.getSrcName(), null, null, SymbolRef.Kind.CLASS);
			checkAcceptedAlias(owner, cls, code);
			for (var method : cls.getMethods()) checkAcceptedAlias(resolve(owners, members, targets, budget,
					cls.getSrcName(), method.getSrcName(), method.getSrcDesc(), SymbolRef.Kind.METHOD), method, code);
			for (var field : cls.getFields()) checkAcceptedAlias(resolve(owners, members, targets, budget,
					cls.getSrcName(), field.getSrcName(), field.getSrcDesc(), SymbolRef.Kind.FIELD), field, code);
		}
		List<Change> changes = new ArrayList<>();
		int[] applied = new int[2], unchanged = new int[2];
		for (var cls : incoming.getClasses()) {
			merge(resolve(owners, members, targets, budget, cls.getSrcName(), null, null, SymbolRef.Kind.CLASS),
					cls, baseline.getClass(cls.getSrcName()), code, changes, applied, unchanged);
			for (var method : cls.getMethods()) {
				var base = baseline.getClass(cls.getSrcName());
				merge(resolve(owners, members, targets, budget, cls.getSrcName(), method.getSrcName(), method.getSrcDesc(), SymbolRef.Kind.METHOD),
						method, base == null ? null : base.getMethod(method.getSrcName(), method.getSrcDesc()), code, changes, applied, unchanged);
			}
			for (var field : cls.getFields()) {
				var base = baseline.getClass(cls.getSrcName());
				merge(resolve(owners, members, targets, budget, cls.getSrcName(), field.getSrcName(), field.getSrcDesc(), SymbolRef.Kind.FIELD),
						field, base == null ? null : base.getField(field.getSrcName(), field.getSrcDesc()), code, changes, applied, unchanged);
			}
		}
		for (var change : changes) {
			if (change.alias() == null || change.target().kind() != SymbolRef.Kind.CLASS) continue;
			String raw = change.target().originalClassDescriptor();
			String parent = raw.substring(1, raw.length() - 1);
			for (var cls : incoming.getClasses()) {
				if (cls.getSrcName().startsWith(parent + "$") && cls.getDstName(0) != null) {
					throw unsupported("INNER_CLASS_WITH_RENAMED_OWNER", "L" + cls.getSrcName() + ";");
				}
			}
		}
		validateCollisions(owners, members, changes);
		return new Plan(counts(incoming), new EditCounts(applied[0], applied[1]), new EditCounts(unchanged[0], unchanged[1]), changes);
	}
	private static void budgetRef(Budget budget, jadx.api.data.IJavaNodeRef ref) {
		if (ref != null) { budget.string(ref.getDeclaringClass()); budget.string(ref.getShortId()); }
	}
	private static String key(SymbolRef ref) {
		return ref.originalClassDescriptor() + (ref.kind() == SymbolRef.Kind.CLASS ? "" : "->" + ref.originalName()
				+ (ref.kind() == SymbolRef.Kind.FIELD ? ":" : "") + ref.originalDescriptor());
	}
	private static Target resolve(Map<String, ClassNode> owners, Map<String, List<Target>> members,
			Map<String, Target> targets, Budget budget, String path, String name, String descriptor, SymbolRef.Kind kind) {
		var ref = new SymbolRef(kind, "L" + path + ";", null, name, descriptor);
		String key = key(ref);
		var cached = targets.get(key);
		if (cached != null) return cached;
		var cls = owners.get(path);
		if (cls == null) throw new Problem(404, "NOT_FOUND", "ORIGINAL_DECLARATION_NOT_VISIBLE", key);
		Target target;
		if (kind == SymbolRef.Kind.CLASS) {
			target = new Target(ref, JadxNodeRef.forCls(path.replace('/', '.')), path,
					cls.getClassInfo().makeAliasRawFullName().replace('.', '/'), !cls.getAccessFlags().isSynthetic());
		} else {
			List<Target> matches = members.computeIfAbsent(path, ignored -> memberMetadata(cls, path, budget)).stream()
					.filter(item -> item.ref().equals(ref)).toList();
			if (matches.isEmpty()) throw new Problem(404, "NOT_FOUND", "ORIGINAL_DECLARATION_NOT_VISIBLE", key);
			if (matches.size() != 1) throw new Problem(422, "INVALID_ENTITY_ID", "AMBIGUOUS_MEMBER", key);
			target = matches.getFirst();
		}
		targets.put(key, target);
		return target;
	}
	private static List<Target> memberMetadata(ClassNode cls, String path, Budget budget) {
		if ((long) cls.getMethods().size() + cls.getFields().size() > MappingExportDtos.MAX_ENTRIES) throw limit();
		List<Target> result = new ArrayList<>();
		for (var method : cls.getMethods()) {
			var info = method.getMethodInfo();
			String descriptor = info.getShortId().substring(info.getName().length());
			budget.bytes(512); budget.string(info.getShortId()); budget.string(info.getAlias());
			var ref = new SymbolRef(SymbolRef.Kind.METHOD, "L" + path + ";", null, info.getName(), descriptor);
			result.add(new Target(ref, new JadxNodeRef(RefType.METHOD, path.replace('/', '.'), info.getShortId()),
					info.getName(), info.getAlias(), !info.getName().startsWith("<")
					&& !method.getAccessFlags().isSynthetic() && !method.getAccessFlags().isBridge()));
		}
		for (var field : cls.getFields()) {
			var info = field.getFieldInfo();
			String descriptor = TypeGen.signature(info.getType());
			budget.bytes(512); budget.string(info.getShortId()); budget.string(info.getAlias());
			var ref = new SymbolRef(SymbolRef.Kind.FIELD, "L" + path + ";", null, info.getName(), descriptor);
			result.add(new Target(ref, new JadxNodeRef(RefType.FIELD, path.replace('/', '.'), info.getShortId()),
					info.getName(), info.getAlias(), !field.getAccessFlags().isSynthetic()));
		}
		return List.copyOf(result);
	}
	private static void checkAcceptedAlias(Target target, MappingTreeView.ElementMappingView base, JadxCodeData code) {
		String nativeAlias = JadxNativeEditAdapter.existingRename(code, target.nativeRef());
		if (JadxNativeEditAdapter.renameCount(code, target.nativeRef()) > 1) throw unsupported("AMBIGUOUS_NATIVE_ALIAS", key(target.ref()));
		if (nativeAlias != null) {
			String effective = target.ref().kind() == SymbolRef.Kind.CLASS ? shortClassName(target.effectiveAlias()) : target.effectiveAlias();
			if (!nativeAlias.equals(effective) && !(target.ref().kind() == SymbolRef.Kind.CLASS
					&& nativeAlias.replace('.', '/').equals(target.effectiveAlias()))) {
				throw unsupported("NATIVE_ALIAS_DIFFERS_FROM_ENGINE", key(target.ref()));
			}
		} else if (base != null && base.getDstName(0) != null && !base.getDstName(0).equals(target.effectiveAlias())) {
			throw unsupported("ATTACHED_ALIAS_DIFFERS_FROM_ENGINE", key(target.ref()));
		}
	}
	private static void merge(Target target, MappingTreeView.ElementMappingView incoming, MappingTreeView.ElementMappingView base,
			JadxCodeData code, List<Change> changes, int[] applied, int[] unchanged) {
		String diagnostic = key(target.ref());
		checkAcceptedAlias(target, base, code);
		if (!target.editable()) throw unsupported("UNSUPPORTED_NATIVE_TARGET", diagnostic);
		if (JadxNativeEditAdapter.renameCount(code, target.nativeRef()) > 1
				|| JadxNativeEditAdapter.lineCommentCount(code, target.nativeRef()) > 1) {
			throw new Problem(422, "INVALID_ENTITY_ID", "AMBIGUOUS_NATIVE_KEY", diagnostic);
		}
		String alias = incoming.getDstName(0), stagedAlias = null, stagedComment = null;
		if (alias != null) {
			if (Objects.equals(alias, target.effectiveAlias())) unchanged[0]++;
			else {
				if (!Objects.equals(target.originalAlias(), target.effectiveAlias())
						|| JadxNativeEditAdapter.existingRename(code, target.nativeRef()) != null) throw conflict("EXISTING_ALIAS", diagnostic);
				stagedAlias = alias;
				if (target.ref().kind() == SymbolRef.Kind.CLASS) {
					String shortName = shortClassName(alias);
					String currentPrefix = target.effectiveAlias().substring(0, target.effectiveAlias().length() - shortClassName(target.effectiveAlias()).length());
					if (!alias.equals(currentPrefix + shortName)) throw unsupported("CLASS_PACKAGE_OR_OWNER_MOVE", diagnostic);
					// Changing an inner's native short name needs a separate hierarchy-fidelity probe.
					if (target.originalAlias().contains("$")) throw unsupported("INNER_CLASS_RENAME", diagnostic);
					stagedAlias = shortName;
				}
				if (!NativeDeclarationValidation.validName(stagedAlias)) throw unsupported("INVALID_NATIVE_ALIAS", diagnostic);
				applied[0]++;
			}
		}
		String comment = incoming.getComment();
		if (comment != null) {
			for (var existing : code.getComments()) {
				if (existing.getCodeRef() == null && JadxNativeEditAdapter.sameKey(existing.getNodeRef(), target.nativeRef())
						&& existing.getStyle() != CommentStyle.LINE) throw unsupported("AMBIGUOUS_COMMENT_STYLE", diagnostic);
			}
			String nativeComment = JadxNativeEditAdapter.existingComment(code, target.nativeRef());
			String attachedComment = base == null ? null : base.getComment();
			if (nativeComment != null && !NativeDeclarationValidation.validLineComment(nativeComment)) throw unsupported("UNREPRESENTABLE_NATIVE_COMMENT", diagnostic);
			String effective = attachedComment == null ? nativeComment : nativeComment == null ? attachedComment : attachedComment + "\n" + nativeComment;
			if (Objects.equals(comment, effective)) unchanged[1]++;
			else if (effective == null) stagedComment = comment;
			else if (nativeComment == null && attachedComment != null && comment.startsWith(attachedComment + "\n")) {
				stagedComment = comment.substring(attachedComment.length() + 1);
			} else throw conflict("EXISTING_COMMENT", diagnostic);
			if (stagedComment != null) {
				if (!NativeDeclarationValidation.validLineComment(stagedComment)) throw unsupported("UNREPRESENTABLE_LINE_COMMENT", diagnostic);
				applied[1]++;
			}
		}
		if (stagedAlias != null || stagedComment != null) changes.add(new Change(target.ref(), stagedAlias, stagedComment));
	}
	private static String shortClassName(String path) { return path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf('$')) + 1); }

	private static void validateCollisions(Map<String, ClassNode> owners, Map<String, List<Target>> members, List<Change> changes) {
		Map<SymbolRef, String> renamed = new HashMap<>();
		for (var change : changes) if (change.alias() != null) renamed.put(change.target(), change.alias());
		for (var change : changes) {
			if (change.alias() == null) continue;
			var ref = change.target();
			if (ref.kind() == SymbolRef.Kind.CLASS) {
				String candidate = finalClassName(owners.get(ref.originalClassDescriptor().substring(1, ref.originalClassDescriptor().length() - 1)).getClassInfo(), renamed);
				for (var entry : owners.entrySet()) {
					var other = SymbolRef.classRef("L" + entry.getKey() + ";");
					if (!ref.equals(other) && candidate.equals(finalClassName(entry.getValue().getClassInfo(), renamed))) throw conflict("ALIAS_COLLISION", key(ref));
				}
			} else {
				String path = ref.originalClassDescriptor().substring(1, ref.originalClassDescriptor().length() - 1);
				for (var other : members.get(path)) {
					if (other.ref().equals(ref) || other.ref().kind() != ref.kind()) continue;
					if (ref.kind() == SymbolRef.Kind.METHOD && !arguments(ref).equals(arguments(other.ref()))) continue;
					if (change.alias().equals(renamed.getOrDefault(other.ref(), other.effectiveAlias()))) throw conflict("ALIAS_COLLISION", key(ref));
				}
			}
		}
	}
	private static String finalClassName(jadx.core.dex.info.ClassInfo info, Map<SymbolRef, String> renamed) {
		String name = renamed.getOrDefault(SymbolRef.classRef("L" + info.getRawName().replace('.', '/') + ";"), info.getAliasShortName());
		var parent = info.getParentClass();
		if (parent != null) return finalClassName(parent, renamed) + "$" + name;
		String pkg = info.getAliasPkg().replace('.', '/');
		return pkg.isEmpty() ? name : pkg + "/" + name;
	}
	private static String arguments(SymbolRef ref) { return ref.originalDescriptor().substring(0, ref.originalDescriptor().indexOf(')') + 1); }
}
