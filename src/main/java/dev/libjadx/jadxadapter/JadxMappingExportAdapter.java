package dev.libjadx.jadxadapter;

import static dev.libjadx.core.mappings.MappingExportDtos.*;
import static dev.libjadx.jadxadapter.JadxTinyMappings.*;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import dev.libjadx.core.symbols.SymbolRef;
import jadx.api.JadxDecompiler;
import jadx.api.data.CommentStyle;
import jadx.api.data.IJavaNodeRef;
import jadx.api.data.impl.JadxCodeData;
import jadx.core.dex.nodes.ClassNode;
import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MappingTreeView;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

/** Version-sensitive Jadx 1.5.6 / mapping-io 0.8.0 conversion. No filesystem writes or processing. */
public final class JadxMappingExportAdapter {
	public record Encoded(byte[] bytes, Counts counts, Map<String, Value> semantics) { }
	public record Value(String alias, String comment) { }

	public Encoded encode(JadxDecompiler engine, JadxCodeData code, byte[] acceptedMapping, boolean hasAttachedMapping) throws IOException {
		Budget budget = new Budget();
		budget.bytes(2048L * (code.getRenames().size() + code.getComments().size()));
		for (var rename : code.getRenames()) budgetNativeRef(budget, rename.getNodeRef());
		for (var comment : code.getComments()) budgetNativeRef(budget, comment.getNodeRef());
		MemoryMappingTree tree = accepted(engine, acceptedMapping, hasAttachedMapping, budget);
		Set<String> nativeRenames = new HashSet<>();
		for (var rename : code.getRenames()) {
			if (rename.getCodeRef() != null) throw unsupported("NATIVE_CODE_REF");
			var target = target(engine, rename.getNodeRef());
			if (!nativeRenames.add(target.key())) throw unsupported("DUPLICATE_NATIVE_RENAME");
			budget.string(rename.getNewName());
			if (rename.getNewName() == null || rename.getNewName().isEmpty()) throw unsupported("INVALID_NATIVE_ALIAS");
			// Use actual effective pinned-engine aliases, rather than assuming code-data equals output.
			String effective = target.alias();
			String nativeAlias = target.kind() == MappedElementKind.CLASS
					? owner(engine, target.owner()).getClassInfo().getAliasShortName() : effective;
			if (!Objects.equals(rename.getNewName(), nativeAlias)
					&& !(target.kind() == MappedElementKind.CLASS && Objects.equals(rename.getNewName().replace('.', '/'), effective))) {
				throw unsupported("NATIVE_ALIAS_DIFFERS_FROM_EFFECTIVE_ALIAS");
			}
			entry(tree, target).setDstName(effective, 0);
		}
		Set<String> nativeComments = new HashSet<>();
		for (var comment : code.getComments()) {
			if (comment.getCodeRef() != null) throw unsupported("NATIVE_CODE_REF");
			if (comment.getStyle() != CommentStyle.LINE) throw unsupported("NATIVE_COMMENT_STYLE");
			var target = target(engine, comment.getNodeRef());
			if (!nativeComments.add(target.key())) throw unsupported("DUPLICATE_NATIVE_COMMENT");
			budget.string(comment.getComment());
			if (comment.getComment() == null || comment.getComment().isEmpty()) throw unsupported("EMPTY_NATIVE_COMMENT");
			var element = entry(tree, target);
			String attached = element.getComment();
			String combined = attached == null ? comment.getComment() : attached + "\n" + comment.getComment();
			budget.string(combined);
			element.setComment(combined);
		}
		// Validate every attached original key, including unrelated and comment-only declarations.
		for (var cls : tree.getClasses()) {
			ClassNode owner = owner(engine, cls.getSrcName());
			checkAlias(cls, owner.getClassInfo().makeAliasRawFullName().replace('.', '/'));
			for (var field : cls.getFields()) {
				var resolved = owner.searchFieldByShortId(field.getSrcName() + ":" + field.getSrcDesc());
				if (resolved == null) throw unsupported("UNRESOLVED_ATTACHED_FIELD");
				checkAlias(field, resolved.getFieldInfo().getAlias());
			}
			for (var method : cls.getMethods()) {
				var resolved = owner.searchMethodByShortId(method.getSrcName() + method.getSrcDesc());
				if (resolved == null) throw unsupported("UNRESOLVED_ATTACHED_METHOD");
				checkAlias(method, resolved.getMethodInfo().getAlias());
			}
		}
		Counts counts = counts(tree);
		if (counts.total() > MAX_ENTRIES) throw limit();
		Map<String, Value> semantics = canonical(tree);
		BoundedBytes output = new BoundedBytes();
		try (var text = new OutputStreamWriter(output, StandardCharsets.UTF_8.newEncoder()
				.onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT));
				var writer = MappingWriter.create(text, MappingFormat.TINY_2_FILE)) {
			tree.accept(writer);
		}
		byte[] bytes = output.toByteArray();
		budget.bytes(bytes.length * 4L);
		Encoded encoded = new Encoded(bytes, counts, semantics);
		verify(bytes, encoded);
		return encoded;
	}
	private static void budgetNativeRef(Budget budget, IJavaNodeRef ref) {
		if (ref != null) { budget.string(ref.getDeclaringClass()); budget.string(ref.getShortId()); }
	}

	/** Both in-memory and owned staged bytes must reparse to identical verified values. */
	public Counts verify(byte[] bytes, Encoded expected) throws IOException {
		MemoryMappingTree readBack = parse(bytes, new Budget());
		if (!canonical(readBack).equals(expected.semantics())) throw unsupported("CODEC_ROUND_TRIP_LOSS");
		return counts(readBack);
	}

	private static void checkAlias(MappingTreeView.ElementMappingView mapping, String effective) {
		if (mapping.getDstName(0) != null && !mapping.getDstName(0).equals(effective)) {
			throw unsupported("ATTACHED_ALIAS_DIFFERS_FROM_EFFECTIVE_ALIAS");
		}
	}

	private record Target(MappedElementKind kind, String owner, String name, String descriptor, String alias) {
		String key() { return kind + "\0" + owner + "\0" + name + "\0" + descriptor; }
	}
	private static Target target(JadxDecompiler engine, IJavaNodeRef ref) {
		if (ref == null || ref.getType() == null || ref.getDeclaringClass() == null) throw unsupported("INVALID_NATIVE_KEY");
		String path = ref.getDeclaringClass().replace('.', '/');
		ClassNode owner = owner(engine, path);
		if (ref.getType() == IJavaNodeRef.RefType.CLASS) {
			if (ref.getShortId() != null) throw unsupported("INVALID_NATIVE_KEY");
			return new Target(MappedElementKind.CLASS, path, null, null, owner.getClassInfo().makeAliasRawFullName().replace('.', '/'));
		}
		String id = ref.getShortId();
		if (id == null) throw unsupported("INVALID_NATIVE_KEY");
		if (ref.getType() == IJavaNodeRef.RefType.METHOD) {
			var method = owner.searchMethodByShortId(id);
			if (method == null) throw unsupported("UNRESOLVED_NATIVE_METHOD");
			var info = method.getMethodInfo();
			String descriptor = info.getShortId().substring(info.getName().length());
			new SymbolRef(SymbolRef.Kind.METHOD, "L" + path + ";", null, info.getName(), descriptor);
			return new Target(MappedElementKind.METHOD, path, info.getName(), descriptor, info.getAlias());
		}
		if (ref.getType() == IJavaNodeRef.RefType.FIELD) {
			var field = owner.searchFieldByShortId(id);
			if (field == null) throw unsupported("UNRESOLVED_NATIVE_FIELD");
			var info = field.getFieldInfo();
			String descriptor = jadx.core.codegen.TypeGen.signature(info.getType());
			new SymbolRef(SymbolRef.Kind.FIELD, "L" + path + ";", null, info.getName(), descriptor);
			return new Target(MappedElementKind.FIELD, path, info.getName(), descriptor, info.getAlias());
		}
		throw unsupported("NATIVE_PACKAGE_OR_UNKNOWN_REF");
	}
	private static ClassNode owner(JadxDecompiler engine, String path) {
		try { SymbolRef.validateClassDescriptor("L" + path + ";"); }
		catch (IllegalArgumentException invalid) { throw unsupported("INVALID_ORIGINAL_CLASS_KEY"); }
		ClassNode owner = engine.getRoot().resolveRawClass(path.replace('/', '.'));
		if (owner == null || !owner.getClassInfo().getRawName().replace('.', '/').equals(path)) {
			throw unsupported("UNRESOLVED_ORIGINAL_CLASS");
		}
		if ((long) owner.getMethods().size() + owner.getFields().size() > MAX_ENTRIES) throw limit();
		return owner;
	}

	private static MappingTree.ElementMapping entry(MemoryMappingTree tree, Target target) {
		var cls = tree.getClass(target.owner());
		if (cls == null) {
			tree.visitNamespaces(tree.getSrcNamespace(), tree.getDstNamespaces());
			tree.visitClass(target.owner());
			tree.visitDstName(MappedElementKind.CLASS, 0, target.owner());
			tree.visitEnd(); cls = tree.getClass(target.owner());
		}
		if (target.kind() == MappedElementKind.CLASS) return cls;
		MappingTree.ElementMapping element = target.kind() == MappedElementKind.METHOD
				? cls.getMethod(target.name(), target.descriptor()) : cls.getField(target.name(), target.descriptor());
		if (element == null) {
			tree.visitNamespaces(tree.getSrcNamespace(), tree.getDstNamespaces());
			tree.visitClass(target.owner());
			if (target.kind() == MappedElementKind.METHOD) tree.visitMethod(target.name(), target.descriptor());
			else tree.visitField(target.name(), target.descriptor());
			tree.visitDstName(target.kind(), 0, target.alias());
			tree.visitEnd();
			element = target.kind() == MappedElementKind.METHOD
					? cls.getMethod(target.name(), target.descriptor()) : cls.getField(target.name(), target.descriptor());
		}
		return element;
	}

	private static final class BoundedBytes extends java.io.ByteArrayOutputStream {
		@Override public synchronized void write(int value) { if (count >= MAX_BYTES) throw limit(); super.write(value); }
		@Override public synchronized void write(byte[] values, int offset, int length) {
			if (count + (long) length > MAX_BYTES) throw limit(); super.write(values, offset, length);
		}
	}
}
