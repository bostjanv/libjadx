package dev.libjadx.probes;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

import jadx.plugins.input.java.JavaClassReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Phase A: read the original definition before RootNode's duplicate selection. */
class RawInputCensusProbeTest {
	@TempDir Path dir;

	@Test void pinnedReaderRetainsBothDefinitionsThatRootNodeCollapses() throws Exception {
		var inputs = RelatedFixture.compile(dir);
		var duplicate = RelatedFixture.duplicateLeaf(dir);
		String entry = "related/Hierarchy$Leaf.class";
		try (var first = new JarFile(inputs.getFirst().toFile()); var second = new JarFile(duplicate.toFile())) {
			var one = new JavaClassReader(1, "first:" + entry, first.getInputStream(first.getJarEntry(entry)).readAllBytes())
					.loadClassData();
			var two = new JavaClassReader(2, "second:" + entry, second.getInputStream(second.getJarEntry(entry)).readAllBytes())
					.loadClassData();
			assertEquals("Lrelated/Hierarchy$Leaf;", one.getType());
			assertEquals(one.getType(), two.getType());
			assertNotEquals(one.getInputFileName(), two.getInputFileName());
			assertEquals("Lrelated/Hierarchy$Middle;", one.getSuperType());
			assertEquals("Ljava/lang/Object;", two.getSuperType());
			var names = new java.util.ArrayList<String>();
			two.visitFieldsAndMethods(f -> { }, m -> { m.getMethodRef().load(); names.add(m.getMethodRef().getName()); });
			assertTrue(names.contains("onlyInDiscardedInput"));
		}
		try (var engine = RelatedFixture.open(List.of(inputs.getFirst(), inputs.getLast(), duplicate))) {
			var visible = engine.getRoot().getClasses().stream()
					.filter(c -> c.getRawName().equals("related.Hierarchy$Leaf")).toList();
			assertEquals(1, visible.size());
			assertTrue(visible.getFirst().getMethods().stream()
					.noneMatch(m -> m.getMethodInfo().getName().equals("onlyInDiscardedInput")));
		}
		assertFalse(Files.exists(dir.resolve("hierarchy.jar.jadx")));
	}
}
