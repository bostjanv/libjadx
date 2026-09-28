package dev.libjadx.probes;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import dev.libjadx.app.SymbolFixtureSupport;
import dev.libjadx.jadxadapter.JadxMappingExportAdapter;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.data.IJavaNodeRef.RefType;
import jadx.api.data.impl.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Pinned 1.5.6: a Tiny composite is exactly representable as attached prefix + one native LINE. */
class JadxMappingImportProbeTest {
	@TempDir Path root;
	@Test void attachedPrefixAndNativeSuffixRoundTripForAllDeclarationKinds() throws Exception {
		Path jar = SymbolFixtureSupport.compileFixture(root);
		Path attached = root.resolve("attached.tiny");
		Files.writeString(attached, "tiny\t2\t0\toriginal\tmapped\n"
				+ "c\tprobe/SymbolFixture\tprobe/AttachedFixture\n\tc\tattached class\n"
				+ "\tm\t(I)I\tmix\tattachedMix\n\t\tc\tattached method\n"
				+ "\tf\tI\tcount\tattachedCount\n\t\tc\tattached field\n");
		JadxCodeData nativeData = new JadxCodeData();
		var cls = JadxNodeRef.forCls("probe.SymbolFixture");
		var method = new JadxNodeRef(RefType.METHOD, "probe.SymbolFixture", "mix(I)I");
		var field = new JadxNodeRef(RefType.FIELD, "probe.SymbolFixture", "count:I");
		nativeData.setRenames(List.of(new JadxCodeRename(cls, "NativeFixture"),
				new JadxCodeRename(method, "nativeMix"), new JadxCodeRename(field, "nativeCount")));
		nativeData.setComments(List.of(new JadxCodeComment(cls, "native class"),
				new JadxCodeComment(method, "native method"), new JadxCodeComment(field, "native field")));
		try (var engine = engine(jar, attached, nativeData)) {
			var encoded = new JadxMappingExportAdapter().encode(engine, nativeData, Files.readAllBytes(attached), true);
			Path composite = root.resolve("composite.tiny"); Files.write(composite, encoded.bytes());
			String expected = engine.searchJavaClassByOrigFullName("probe.SymbolFixture").getCode();
			try (var fresh = engine(jar, composite, new JadxCodeData())) {
				assertEquals(expected, fresh.searchJavaClassByOrigFullName("probe.SymbolFixture").getCode());
			}
			for (String part : List.of("class", "method", "field")) {
				assertTrue(expected.contains("// attached " + part)); assertTrue(expected.contains("// native " + part));
			}
		}
	}
	@Test void fullOriginalKeysResolveWithoutGeneratingOwnerSource() throws Exception {
		Path jar = SymbolFixtureSupport.returnTypeClashJar(root);
		try (var engine = engine(jar, null, new JadxCodeData())) {
			var owner = engine.getRoot().resolveRawClass("probe.ReturnClash");
			var state = owner.getState();
			assertNotSame(owner.searchMethodByShortId("value()I"), owner.searchMethodByShortId("value()Ljava/lang/String;"));
			var data = new JadxCodeData();
			data.setRenames(List.of(new JadxCodeRename(new JadxNodeRef(RefType.METHOD, "probe.ReturnClash", "value()I"), "intValue"),
					new JadxCodeRename(new JadxNodeRef(RefType.METHOD, "probe.ReturnClash", "value()Ljava/lang/String;"), "stringValue")));
			engine.getArgs().setCodeData(data); engine.reloadCodeData();
			assertEquals(state, owner.getState());
			assertEquals("intValue", owner.searchMethodByShortId("value()I").getMethodInfo().getAlias());
			assertEquals("stringValue", owner.searchMethodByShortId("value()Ljava/lang/String;").getMethodInfo().getAlias());
		}
	}
	@ParameterizedTest @ValueSource(strings = {"Inner", "Inner$Deep"})
	void parentNativeRenameRequalifiesUntouchedDescendantsIntoAnAttachedAlias(String descendant) throws Exception {
		Path jar = SymbolFixtureSupport.compileMappingHierarchyFixture(root);
		Path attached = root.resolve("attached.tiny");
		Files.writeString(attached, "tiny\t2\t0\toriginal\tmapped\n"
				+ "c\tpkg/Other\tpkg/NewOuter$" + descendant + "\n");
		try (var engine = engine(jar, attached, new JadxCodeData())) {
			var outer = engine.getRoot().resolveRawClass("pkg.Outer");
			var inner = engine.getRoot().resolveRawClass("pkg.Outer$" + descendant);
			var other = engine.getRoot().resolveRawClass("pkg.Other");
			var state = inner.getState();
			assertNull(other.getClassInfo().getParentClass(), "Attached alias retains a top-level original identity");
			assertEquals("pkg.Outer$" + descendant, inner.getClassInfo().makeAliasRawFullName());
			assertEquals("pkg.NewOuter$" + descendant, other.getClassInfo().makeAliasRawFullName());
			var data = new JadxCodeData();
			data.setRenames(List.of(new JadxCodeRename(JadxNodeRef.forCls("pkg.Outer"), "NewOuter")));
			engine.getArgs().setCodeData(data); engine.reloadCodeData();
			assertEquals("pkg.NewOuter", outer.getClassInfo().makeAliasRawFullName());
			assertEquals(other.getClassInfo().makeAliasRawFullName(), inner.getClassInfo().makeAliasRawFullName());
			assertEquals(state, inner.getState(), "Replay needs no generated descendant source");
			try (var fresh = engine(jar, attached, data)) {
				assertEquals("pkg.NewOuter$" + descendant,
						fresh.getRoot().resolveRawClass("pkg.Outer$" + descendant).getClassInfo().makeAliasRawFullName());
			}
		}
	}
	private static JadxDecompiler engine(Path jar, Path mapping, JadxCodeData data) {
		var args = new JadxArgs(); args.getInputFiles().add(jar.toFile());
		args.setUserRenamesMappingsPath(mapping); args.setCodeData(data);
		var engine = new JadxDecompiler(args);
		try { engine.load(); return engine; } catch (RuntimeException | Error e) { engine.close(); throw e; }
	}
}
