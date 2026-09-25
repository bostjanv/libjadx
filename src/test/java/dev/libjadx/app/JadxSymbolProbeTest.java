package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.jadxadapter.JadxSymbolAdapter;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;
import jadx.api.data.impl.JadxCodeData;
import jadx.api.data.impl.JadxCodeRename;
import jadx.api.data.impl.JadxNodeRef;

class JadxSymbolProbeTest {
	@TempDir Path dir;

	@Test
	void pinnedRawNamesAndDescriptorsSurviveAliasesWithoutCatalogCodegen() throws Exception {
		Path jar = SymbolFixtureSupport.compileFixture(dir);
		JadxArgs args = new JadxArgs();
		args.getInputFiles().add(jar.toFile());
		try (JadxDecompiler jadx = new JadxDecompiler(args)) {
			jadx.load();
			JavaClass original = jadx.getClassesWithInners().stream()
					.filter(cls -> cls.getRawName().equals("probe.SymbolFixture")).findFirst().orElseThrow();
			var state = original.getClassNode().getState();
			var classes = JadxSymbolAdapter.classes(jadx);
			assertEquals(state, original.getClassNode().getState(), "Catalog enumeration must not decompile classes");
			assertTrue(classes.stream().anyMatch(cls -> cls.ref().originalClassDescriptor().equals("Lprobe/SymbolFixture$Inner;")));
			assertTrue(classes.stream().anyMatch(cls -> cls.ref().originalClassDescriptor().equals("Lprobe/SymbolFixture$1;")));
			assertEquals("()V", descriptorFor(original, "<init>"));
			assertEquals("()V", descriptorFor(original, "<clinit>"));
			assertEquals("(I)I", descriptorFor(original, "mix"));
			assertEquals("([Ljava/lang/String;I)Ljava/lang/String;", descriptorFor(original, "mix", 1));
			assertEquals("I", JadxSymbolAdapter.matchingMembers(original,
					new SymbolRef(SymbolRef.Kind.FIELD, "Lprobe/SymbolFixture;", null, "count", "I"))
					.getFirst().ref().originalDescriptor());
			assertEquals("[Ljava/lang/String;", JadxSymbolAdapter.matchingMembers(original,
					new SymbolRef(SymbolRef.Kind.FIELD, "Lprobe/SymbolFixture;", null, "names", "[Ljava/lang/String;"))
					.getFirst().ref().originalDescriptor());

			var method = original.getMethods().stream().filter(m -> m.getName().equals("mix")).findFirst().orElseThrow();
			var field = original.getFields().stream().filter(f -> f.getName().equals("count")).findFirst().orElseThrow();
			JadxCodeData code = new JadxCodeData();
			code.setRenames(List.of(new JadxCodeRename(JadxNodeRef.forCls("probe.SymbolFixture"), "MappedFixture"),
					new JadxCodeRename(JadxNodeRef.forMth(method), "aliasedMix"),
					new JadxCodeRename(JadxNodeRef.forFld(field), "aliasedCount")));
			args.setCodeData(code);
			jadx.reloadCodeData();
			var mapped = JadxSymbolAdapter.classes(jadx);
			assertTrue(mapped.stream().anyMatch(cls -> cls.ref().originalClassDescriptor().equals("Lprobe/SymbolFixture;")
					&& cls.displayQualifiedName().contains("MappedFixture")));
			assertEquals("aliasedMix", JadxSymbolAdapter.matchingMembers(original,
					new SymbolRef(SymbolRef.Kind.METHOD, "Lprobe/SymbolFixture;", null, "mix", "(I)I"))
					.getFirst().displayName());
			assertEquals("aliasedCount", JadxSymbolAdapter.matchingMembers(original,
					new SymbolRef(SymbolRef.Kind.FIELD, "Lprobe/SymbolFixture;", null, "count", "I"))
					.getFirst().displayName());
		}
	}

	@Test
	void duplicateJarDefinitionObservationIsRecordedWithoutInventingInputIdentity() throws Exception {
		Path one = SymbolFixtureSupport.duplicateJar(dir, "one", 1);
		Path two = SymbolFixtureSupport.duplicateJar(dir, "two", 2);
		JadxArgs args = new JadxArgs();
		args.getInputFiles().add(one.toFile());
		args.getInputFiles().add(two.toFile());
		try (JadxDecompiler jadx = new JadxDecompiler(args)) {
			jadx.load();
			var matching = JadxSymbolAdapter.classes(jadx).stream()
					.filter(cls -> cls.ref().originalClassDescriptor().equals("Lduplicate/Clash;")).toList();
			assertEquals(1, matching.size(), "Pinned 1.5.6 loader retained only one two-JAR definition");
			assertTrue(matching.stream().allMatch(cls -> cls.ref().inputIdentity() == null));
		}
	}

	@Test
	void classfileReturnTypeOnlyOverloadsRemainDistinctOriginalReferences() throws Exception {
		Path jar = SymbolFixtureSupport.returnTypeClashJar(dir);
		JadxArgs args = new JadxArgs();
		args.getInputFiles().add(jar.toFile());
		try (JadxDecompiler jadx = new JadxDecompiler(args)) {
			jadx.load();
			JavaClass cls = jadx.getClassesWithInners().stream()
					.filter(value -> value.getRawName().equals("probe.ReturnClash")).findFirst().orElseThrow();
			assertEquals(1, JadxSymbolAdapter.matchingMembers(cls,
					new SymbolRef(SymbolRef.Kind.METHOD, "Lprobe/ReturnClash;", null, "value", "()I")).size());
			assertEquals(1, JadxSymbolAdapter.matchingMembers(cls,
					new SymbolRef(SymbolRef.Kind.METHOD, "Lprobe/ReturnClash;", null, "value", "()Ljava/lang/String;")).size());
		}
	}

	private static String descriptorFor(JavaClass cls, String name) {
		return descriptorFor(cls, name, 0);
	}

	private static String descriptorFor(JavaClass cls, String name, int occurrence) {
		return cls.getMethods().stream().filter(m -> m.getMethodNode().getMethodInfo().getName().equals(name))
				.map(m -> m.getMethodNode().getMethodInfo().getShortId().substring(name.length()))
				.skip(occurrence).findFirst().orElseThrow();
	}
}
