package dev.libjadx.jadxadapter;

import static dev.libjadx.core.mappings.MappingExportDtos.*;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import dev.libjadx.core.symbols.SymbolRef;
import jadx.api.JadxDecompiler;
import jadx.api.data.CommentStyle;
import jadx.api.data.IJavaNodeRef;
import jadx.api.data.impl.JadxCodeData;
import jadx.core.dex.nodes.ClassNode;
import jadx.plugins.mappings.RenameMappingsData;
import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MappingTreeView;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

/** Version-sensitive Jadx 1.5.6 / mapping-io 0.8.0 conversion. No filesystem writes or processing. */
public final class JadxMappingExportAdapter {
	public record Encoded(byte[] bytes, Counts counts, Map<String, Value> semantics) { }
	public record Value(String alias, String comment) { }

	public Encoded encode(JadxDecompiler engine, JadxCodeData code, byte[] acceptedMapping) throws IOException {
		if (engine.getArgs().getPluginOptions().entrySet().stream().anyMatch(entry -> entry.getKey().startsWith("rename-mappings."))) {
			throw unsupported("MAPPING_PLUGIN_OPTIONS_OR_INVERSION");
		}
		Budget budget = new Budget();
		budget.bytes(2048L * (code.getRenames().size() + code.getComments().size()));
		for (var rename : code.getRenames()) budgetNativeRef(budget, rename.getNodeRef());
		for (var comment : code.getComments()) budgetNativeRef(budget, comment.getNodeRef());
		budget.bytes(acceptedMapping.length * 5L);
		MemoryMappingTree tree = acceptedMapping.length == 0 ? empty() : parse(acceptedMapping, budget);
		MappingTreeView loaded = RenameMappingsData.getTree(engine.getRoot());
		if ((loaded == null && acceptedMapping.length != 0)
				|| (loaded != null && !canonical(tree).equals(canonical(loaded)))) {
			throw unsupported("LOADED_MAPPING_DIFFERS_FROM_ACCEPTED_SOURCE");
		}
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

	private static MemoryMappingTree empty() {
		MemoryMappingTree tree = new MemoryMappingTree();
		tree.visitNamespaces("original", List.of("mapped")); tree.visitEnd(); return tree;
	}
	private static MemoryMappingTree parse(byte[] bytes, Budget budget) throws IOException {
		if (bytes.length > MAX_BYTES) throw limit();
		String text;
		try { text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
				.decode(ByteBuffer.wrap(bytes)).toString(); }
		catch (java.nio.charset.CharacterCodingException invalid) { throw unsupported("MALFORMED_MAPPING_ENCODING"); }
		validateRecords(text);
		MemoryMappingTree tree = new MemoryMappingTree();
		try { MappingReader.read(new StringReader(text), MappingFormat.TINY_2_FILE, new CheckedVisitor(tree, budget)); }
		catch (IOException | IllegalArgumentException invalid) { throw unsupported("MALFORMED_MAPPING_SOURCE"); }
		return tree;
	}

	/** Tiny's reader deliberately skips unknown records: fail closed before that normalization. */
	private static void validateRecords(String text) {
		int headerEnd = text.indexOf('\n');
		if (headerEnd < 0) headerEnd = text.length();
		if (headerEnd > MAX_STRING * 2 + 32) throw limit();
		String[] header = text.substring(0, headerEnd).split("\t", -1);
		if (header.length != 5 || !header[0].equals("tiny") || !header[1].equals("2") || !header[2].equals("0")
				|| header[3].isBlank() || header[4].isBlank() || header[3].equals(header[4])) throw unsupported("MAPPING_NAMESPACES_OR_FORMAT");
		int declarationDepth = -1;
		int lineNumber = 0;
		int records = 0;
		for (int start = headerEnd + 1; start < text.length();) {
			lineNumber++;
			if (++records > MAX_ENTRIES + 1) throw limit();
			int end = text.indexOf('\n', start);
			if (end < 0) end = text.length();
			if (end - start > MAX_STRING * 5) throw limit();
			String line = text.substring(start, end);
			start = end + 1;
			String[] columns = line.split("\t", -1);
			boolean valid = (columns.length == 3 && columns[0].equals("c"))
					|| (columns.length == 5 && columns[0].isEmpty() && (columns[1].equals("m") || columns[1].equals("f")))
					|| (columns.length == 3 && columns[0].isEmpty() && columns[1].equals("c"))
					|| (columns.length == 4 && columns[0].isEmpty() && columns[1].isEmpty() && columns[2].equals("c"))
					|| (lineNumber == 1 && line.equals("\tescaped-names"));
			if (!valid) throw unsupported("UNKNOWN_OR_UNSUPPORTED_MAPPING_RECORD");
			if (columns[0].equals("c")) declarationDepth = 0;
			else if (columns.length == 5) {
				if (declarationDepth < 0) throw unsupported("ORPHAN_MAPPING_MEMBER");
				declarationDepth = 1;
			} else if (!line.equals("\tescaped-names")) {
				int commentDepth = columns.length == 3 ? 0 : 1;
				if (declarationDepth != commentDepth) throw unsupported("ORPHAN_MAPPING_COMMENT");
			}
		}
	}

	private static final class Budget {
		private long memory;
		private int entries;
		void bytes(long count) { memory += count; if (memory > MAX_MEMORY) throw limit(); }
		void entry() { if (++entries > MAX_ENTRIES) throw limit(); bytes(1024); }
		void string(String value) {
			if (value == null) return;
			if (value.length() > MAX_STRING) throw limit();
			// Trees, copied native data, canonical maps, parser and writer strings.
			bytes(16L * value.length() + 64);
			for (int i = 0; i < value.length(); i++) {
				char ch = value.charAt(i);
				if (ch == '\0' || ch == '\r' || (Character.isISOControl(ch) && ch != '\n' && ch != '\t')) throw unsupported("UNREPRESENTABLE_STRING");
				if (Character.isHighSurrogate(ch)) {
					if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) throw unsupported("UNREPRESENTABLE_STRING");
				} else if (Character.isLowSurrogate(ch)) throw unsupported("UNREPRESENTABLE_STRING");
			}
		}
	}
	private static final class CheckedVisitor extends ForwardingMappingVisitor {
		private final Budget budget;
		private final Set<String> keys = new HashSet<>();
		private String owner;
		private String current;
		CheckedVisitor(MemoryMappingTree tree, Budget budget) { super(tree); this.budget = budget; }
		@Override public void visitNamespaces(String source, List<String> destinations) throws IOException {
			if (destinations.size() != 1 || Objects.equals(source, destinations.getFirst())) throw unsupported("MAPPING_NAMESPACES");
			budget.string(source); budget.string(destinations.getFirst()); super.visitNamespaces(source, destinations);
		}
		@Override public void visitMetadata(String key, String value) { throw unsupported("MAPPING_METADATA"); }
		private void key(String key) { budget.entry(); if (!keys.add(key)) throw unsupported("DUPLICATE_MAPPING_KEY"); current = key; }
		@Override public boolean visitClass(String name) throws IOException {
			budget.string(name); SymbolRef.validateClassDescriptor("L" + name + ";"); owner = name; key("C\0" + name); return super.visitClass(name);
		}
		@Override public boolean visitMethod(String name, String descriptor) throws IOException {
			budget.string(name); budget.string(descriptor);
			new SymbolRef(SymbolRef.Kind.METHOD, "L" + owner + ";", null, name, descriptor);
			key("M\0" + owner + "\0" + name + "\0" + descriptor); return super.visitMethod(name, descriptor);
		}
		@Override public boolean visitField(String name, String descriptor) throws IOException {
			budget.string(name); budget.string(descriptor);
			new SymbolRef(SymbolRef.Kind.FIELD, "L" + owner + ";", null, name, descriptor);
			key("F\0" + owner + "\0" + name + "\0" + descriptor); return super.visitField(name, descriptor);
		}
		@Override public boolean visitMethodArg(int arg, int index, String name) { throw unsupported("MAPPING_ARGUMENT"); }
		@Override public boolean visitMethodVar(int row, int index, int start, int end, String name) { throw unsupported("MAPPING_VARIABLE"); }
		@Override public void visitDstName(MappedElementKind kind, int namespace, String name) throws IOException {
			budget.string(name);
			if (name != null) {
				if (kind == MappedElementKind.CLASS) SymbolRef.validateClassDescriptor("L" + name + ";");
				else new SymbolRef(kind == MappedElementKind.METHOD ? SymbolRef.Kind.METHOD : SymbolRef.Kind.FIELD,
						"L" + owner + ";", null, name, kind == MappedElementKind.METHOD ? "()V" : "I");
			}
			super.visitDstName(kind, namespace, name);
		}
		@Override public void visitComment(MappedElementKind kind, String comment) throws IOException {
			budget.entry(); budget.string(comment);
			if (!keys.add("comment\0" + current)) throw unsupported("DUPLICATE_MAPPING_COMMENT"); super.visitComment(kind, comment);
		}
	}
	private static Map<String, Value> canonical(MappingTreeView tree) {
		if (tree.getDstNamespaces().size() != 1 || !tree.getMetadata().isEmpty()) throw unsupported("MAPPING_NAMESPACES_OR_METADATA");
		Map<String, Value> entries = new TreeMap<>();
		entries.put("namespaces", new Value(tree.getSrcNamespace(), tree.getDstNamespaces().getFirst()));
		for (var cls : tree.getClasses()) {
			put(entries, "C\0" + cls.getSrcName(), cls);
			for (var field : cls.getFields()) put(entries, "F\0" + cls.getSrcName() + "\0" + field.getSrcName() + "\0" + field.getSrcDesc(), field);
			for (var method : cls.getMethods()) {
				if (!method.getArgs().isEmpty() || !method.getVars().isEmpty()) throw unsupported("MAPPING_ARGUMENT_OR_VARIABLE");
				put(entries, "M\0" + cls.getSrcName() + "\0" + method.getSrcName() + "\0" + method.getSrcDesc(), method);
			}
		}
		return Map.copyOf(entries);
	}
	private static void put(Map<String, Value> map, String key, MappingTreeView.ElementMappingView value) {
		if (map.size() >= MAX_ENTRIES + 1) throw limit();
		if (map.put(key, new Value(value.getDstName(0), value.getComment())) != null) throw unsupported("AMBIGUOUS_MAPPING_KEY");
	}
	private static Counts counts(MappingTreeView tree) {
		int classes = 0, methods = 0, fields = 0, comments = 0;
		for (var cls : tree.getClasses()) {
			classes++; if (cls.getComment() != null) comments++;
			for (var field : cls.getFields()) { fields++; if (field.getComment() != null) comments++; }
			for (var method : cls.getMethods()) { methods++; if (method.getComment() != null) comments++; }
		}
		return new Counts(classes, methods, fields, comments);
	}
	private static final class BoundedBytes extends java.io.ByteArrayOutputStream {
		@Override public synchronized void write(int value) { if (count >= MAX_BYTES) throw limit(); super.write(value); }
		@Override public synchronized void write(byte[] values, int offset, int length) {
			if (count + (long) length > MAX_BYTES) throw limit(); super.write(values, offset, length);
		}
	}
}
