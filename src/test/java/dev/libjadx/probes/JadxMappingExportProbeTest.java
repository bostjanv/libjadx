package dev.libjadx.probes;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import dev.libjadx.app.SymbolFixtureSupport;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.data.CommentStyle;
import jadx.api.data.impl.JadxCodeComment;
import jadx.api.data.impl.JadxCodeData;
import jadx.api.data.impl.JadxCodeRename;
import jadx.api.data.impl.JadxNodeRef;
import jadx.core.dex.nodes.ClassNode;
import jadx.plugins.mappings.RenameMappingsData;
import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Owned executable evidence for Jadx commit 28ff15e and mapping-io 0.8.0. */
class JadxMappingExportProbeTest {

	@TempDir Path root;

	@Test
	void declarationsAndLineCommentsRoundTripWithAttachedMappingsAndUnsavedEdits() throws Exception {
		Path jar = SymbolFixtureSupport.compileFixture(root);
		Path mapping = root.resolve("attached.tiny");
		Files.writeString(mapping, "tiny\t2\t0\toriginal\tmapped\n"
				+ "c\tprobe/SymbolFixture\tprobe/AttachedFixture\n"
				+ "\tm\t(I)I\tmix\tattachedMix\n"
				+ "\tf\tI\tcount\tattachedCount\n"
				+ "c\tprobe/SymbolFixture$Inner\tprobe/AttachedFixture$MappedInner\n");
		var cls = JadxNodeRef.forCls("probe.SymbolFixture");
		var method = new JadxNodeRef(jadx.api.data.IJavaNodeRef.RefType.METHOD, "probe.SymbolFixture", "mix(I)I");
		var field = new JadxNodeRef(jadx.api.data.IJavaNodeRef.RefType.FIELD, "probe.SymbolFixture", "count:I");
		JadxCodeData edits = new JadxCodeData();
		edits.setRenames(List.of(new JadxCodeRename(cls, "NativeFixture"),
				new JadxCodeRename(method, "nativeMix"), new JadxCodeRename(field, "nativeCount")));
		edits.setComments(List.of(new JadxCodeComment(cls, "native class", CommentStyle.LINE),
				new JadxCodeComment(method, "native method", CommentStyle.LINE),
				new JadxCodeComment(field, "native field", CommentStyle.LINE)));
		try (var engine = engine(jar, mapping, edits)) {
			ClassNode owner = owner(engine, "probe.SymbolFixture");
			assertEquals("NativeFixture", owner.getClassInfo().getAliasShortName());
			assertEquals("nativeMix", owner.searchMethodByShortId("mix(I)I").getMethodInfo().getAlias());
			assertEquals("nativeCount", owner.searchFieldByShortId("count:I").getFieldInfo().getAlias());
			MemoryMappingTree tree = new MemoryMappingTree();
			RenameMappingsData.getTree(engine.getRoot()).accept(tree);
			tree.visitNamespaces(tree.getSrcNamespace(), tree.getDstNamespaces());
			tree.visitClass("probe/SymbolFixture");
			tree.visitDstName(MappedElementKind.CLASS, 0, "probe/NativeFixture");
			tree.visitComment(MappedElementKind.CLASS, "native class");
			tree.visitMethod("mix", "(I)I");
			tree.visitDstName(MappedElementKind.METHOD, 0, "nativeMix");
			tree.visitComment(MappedElementKind.METHOD, "native method");
			tree.visitField("count", "I");
			tree.visitDstName(MappedElementKind.FIELD, 0, "nativeCount");
			tree.visitComment(MappedElementKind.FIELD, "native field");
			tree.visitEnd();
			Path exported = root.resolve("exported.tiny");
			MemoryMappingTree reparsed = roundTrip(tree, exported);
			assertEquals("native method", reparsed.getClass("probe/SymbolFixture").getMethod("mix", "(I)I").getComment());
			assertEquals("native field", reparsed.getClass("probe/SymbolFixture").getField("count", "I").getComment());
			assertEquals("probe/AttachedFixture$MappedInner", reparsed.getClass("probe/SymbolFixture$Inner").getDstName(0));
			try (var fresh = engine(jar, exported, new JadxCodeData())) {
				assertEquals(owner.getClassInfo().getAliasShortName(), owner(fresh, "probe.SymbolFixture").getClassInfo().getAliasShortName());
				String original = engine.searchJavaClassByOrigFullName("probe.SymbolFixture").getCode();
				String reopened = fresh.searchJavaClassByOrigFullName("probe.SymbolFixture").getCode();
				assertEquals(original, reopened);
			}
		}
	}

	@Test
	void returnTypeOnlyOverloadsRetainFullDescriptors() throws Exception {
		Path jar = SymbolFixtureSupport.returnTypeClashJar(root);
		MemoryMappingTree tree = new MemoryMappingTree();
		tree.visitNamespaces("original", List.of("mapped"));
		tree.visitClass("probe/ReturnClash");
		tree.visitDstName(MappedElementKind.CLASS, 0, "probe/ReturnClash");
		tree.visitMethod("value", "()I");
		tree.visitDstName(MappedElementKind.METHOD, 0, "intValue");
		tree.visitMethod("value", "()Ljava/lang/String;");
		tree.visitDstName(MappedElementKind.METHOD, 0, "stringValue");
		tree.visitEnd();
		Path output = root.resolve("return-types.tiny");
		var parsed = roundTrip(tree, output);
		assertEquals(2, parsed.getClass("probe/ReturnClash").getMethods().size());
		try (var fresh = engine(jar, output, new JadxCodeData())) {
			var owner = owner(fresh, "probe.ReturnClash");
			assertEquals("intValue", owner.searchMethodByShortId("value()I").getMethodInfo().getAlias());
			assertEquals("stringValue", owner.searchMethodByShortId("value()Ljava/lang/String;").getMethodInfo().getAlias());
		}
	}

	@Test
	void attachedAndNativeCommentsAreAdditiveRatherThanReplacement() throws Exception {
		Path jar = SymbolFixtureSupport.compileFixture(root);
		Path mapping = root.resolve("comments.tiny");
		Files.writeString(mapping, "tiny\t2\t0\toriginal\tmapped\n"
				+ "c\tprobe/SymbolFixture\tprobe/SymbolFixture\n\tc\tattached comment\n");
		JadxCodeData data = new JadxCodeData();
		data.setComments(List.of(new JadxCodeComment(JadxNodeRef.forCls("probe.SymbolFixture"), "native comment")));
		try (var engine = engine(jar, mapping, data)) {
			String source = engine.searchJavaClassByOrigFullName("probe.SymbolFixture").getCode();
			assertTrue(source.contains("// attached comment"), source);
			assertTrue(source.contains("// native comment"), source);
			MemoryMappingTree combined = new MemoryMappingTree();
			RenameMappingsData.getTree(engine.getRoot()).accept(combined);
			combined.getClass("probe/SymbolFixture").setComment("attached comment\nnative comment");
			Path output = root.resolve("combined.tiny");
			roundTrip(combined, output);
			try (var fresh = engine(jar, output, new JadxCodeData())) {
				assertEquals(source, fresh.searchJavaClassByOrigFullName("probe.SymbolFixture").getCode());
			}
		}
	}

	@Test
	void namespaceInversionAndMultipleDestinationsCannotBeExportedAsVerifiedOriginalKeys() throws Exception {
		Path jar = SymbolFixtureSupport.compileFixture(root);
		Path mapping = root.resolve("inverted.tiny");
		Files.writeString(mapping, "tiny\t2\t0\toriginal\tmapped\nc\tprobe/SymbolFixture\tprobe/MappedFixture\n");
		JadxArgs args = new JadxArgs(); args.getInputFiles().add(jar.toFile()); args.setUserRenamesMappingsPath(mapping);
		args.getPluginOptions().put("rename-mappings.invert", "yes");
		try (var engine = new JadxDecompiler(args)) {
			engine.load();
			assertEquals("mapped", RenameMappingsData.getTree(engine.getRoot()).getSrcNamespace());
			var failure = assertThrows(dev.libjadx.core.mappings.MappingExportDtos.Problem.class,
					() -> new dev.libjadx.jadxadapter.JadxMappingExportAdapter().encode(engine, new JadxCodeData(), Files.readAllBytes(mapping)));
			assertEquals(422, failure.status());
		}
		Files.writeString(mapping, "tiny\t2\t0\toriginal\tfirst\tsecond\nc\tprobe/SymbolFixture\tprobe/First\tprobe/Second\n");
		// Pinned Jadx logs the prepare-pass error and continues without a loaded tree.
		// The exporter must reject the source itself; load() returning is no proof of acceptance.
		try (var engine = engine(jar, mapping, new JadxCodeData())) {
			assertNull(RenameMappingsData.getTree(engine.getRoot()));
			var failure = assertThrows(dev.libjadx.core.mappings.MappingExportDtos.Problem.class,
					() -> new dev.libjadx.jadxadapter.JadxMappingExportAdapter().encode(engine, new JadxCodeData(), Files.readAllBytes(mapping)));
			assertEquals(422, failure.status());
		}
	}

	@Test
	void readerNormalizesDuplicateDeclarationsAndIgnoresUnknownRecords() throws Exception {
		MemoryMappingTree tree = new MemoryMappingTree();
		MappingReader.read(new StringReader("tiny\t2\t0\toriginal\tmapped\n"
				+ "c\tprobe/Sample\tprobe/First\nc\tprobe/Sample\tprobe/Second\n"
				+ "\tunknown\tdata\n"), MappingFormat.TINY_2_FILE, tree);
		assertEquals(1, tree.getClasses().size());
		assertEquals("probe/Second", tree.getClass("probe/Sample").getDstName(0));
		// A loaded tree alone cannot establish completeness of the original file.
	}

	@Test
	void codecHasNoNativeCommentStyleAndRetainsUnverifiedArgumentAndVariableStructures() throws Exception {
		MemoryMappingTree tree = new MemoryMappingTree();
		MappingReader.read(new StringReader("tiny\t2\t0\toriginal\tmapped\n"
				+ "c\tprobe/Sample\tprobe/Sample\n\tm\t(I)I\tanswer\tanswer\n"
				+ "\t\tp\t1\tvalue\targAlias\n\t\tv\t1\t0\t0\tlocal\tlocalAlias\n"),
				MappingFormat.TINY_2_FILE, tree);
		var method = tree.getClass("probe/Sample").getMethod("answer", "(I)I");
		assertEquals(1, method.getArgs().size());
		assertEquals(1, method.getVars().size());
		assertThrows(java.io.IOException.class, () -> MappingReader.read(new StringReader("bad header\n"), tree));
		// These elements require independent native identities/style evidence: not declaration output.
	}

	private static ClassNode owner(JadxDecompiler engine, String name) {
		return engine.getRoot().getClasses().stream().filter(cls -> cls.getClassInfo().getRawName().equals(name)).findFirst().orElseThrow();
	}

	private static JadxDecompiler engine(Path jar, Path mapping, JadxCodeData data) {
		JadxArgs args = new JadxArgs();
		args.getInputFiles().add(jar.toFile());
		args.setUserRenamesMappingsPath(mapping);
		args.setCodeData(data);
		JadxDecompiler engine = new JadxDecompiler(args);
		try { engine.load(); return engine; }
		catch (RuntimeException | Error failure) { engine.close(); throw failure; }
	}

	private static MemoryMappingTree roundTrip(MemoryMappingTree tree, Path output) throws Exception {
		StringWriter encoded = new StringWriter();
		try (MappingWriter writer = MappingWriter.create(encoded, MappingFormat.TINY_2_FILE)) { tree.accept(writer); }
		MemoryMappingTree parsed = new MemoryMappingTree();
		MappingReader.read(new StringReader(encoded.toString()), MappingFormat.TINY_2_FILE, parsed);
		Files.writeString(output, encoded.toString());
		return parsed;
	}
}
