package dev.libjadx.jadxadapter;

import static dev.libjadx.core.mappings.MappingExportDtos.*;
import java.io.IOException;
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
import dev.libjadx.jadxadapter.JadxMappingExportAdapter.Value;
import jadx.api.JadxDecompiler;
import jadx.plugins.mappings.RenameMappingsData;
import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor;
import net.fabricmc.mappingio.tree.MappingTreeView;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.mappingio.format.MappingFormat;

/** Shared strict lexical validation precedes mapping-io normalization. Pinned 0.8.0. */
final class JadxTinyMappings {
	private JadxTinyMappings() { }

	static MemoryMappingTree accepted(JadxDecompiler engine, byte[] bytes, boolean attached, Budget budget) throws IOException {
		if (engine.getArgs().getPluginOptions().keySet().stream().anyMatch(key -> key.startsWith("rename-mappings."))) {
			throw unsupported("MAPPING_PLUGIN_OPTIONS_OR_INVERSION");
		}
		budget.bytes(bytes.length * 5L);
		MemoryMappingTree tree = attached ? parse(bytes, budget) : empty();
		MappingTreeView loaded = RenameMappingsData.getTree(engine.getRoot());
		if ((loaded == null && attached) || (loaded != null && !canonical(tree).equals(canonical(loaded)))) {
			throw unsupported("LOADED_MAPPING_DIFFERS_FROM_ACCEPTED_SOURCE");
		}
		return tree;
	}

	static MemoryMappingTree empty() {
		MemoryMappingTree tree = new MemoryMappingTree();
		tree.visitNamespaces("original", List.of("mapped")); tree.visitEnd(); return tree;
	}
	static MemoryMappingTree parse(byte[] bytes, Budget budget) throws IOException {
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

	static final class Budget {
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
	static Map<String, Value> canonical(MappingTreeView tree) {
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
	static Counts counts(MappingTreeView tree) {
		int classes = 0, methods = 0, fields = 0, comments = 0;
		for (var cls : tree.getClasses()) {
			classes++; if (cls.getComment() != null) comments++;
			for (var field : cls.getFields()) { fields++; if (field.getComment() != null) comments++; }
			for (var method : cls.getMethods()) { methods++; if (method.getComment() != null) comments++; }
		}
		return new Counts(classes, methods, fields, comments);
	}
}
