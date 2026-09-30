package dev.libjadx.probes;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** Small owned input generator reused by installed-artifact qualification, no downloads. */
class ReleaseFixturePreparationTest {
	@Test void prepareRealJarClassDexAndBoundedLongWorkload() throws Exception {
		Path root = Files.createDirectories(Path.of("build/release-fixtures"));
		Map<String, String> sources = new TreeMap<>();
		for (String file : new String[] {"variables/Variables.java", "references/ReferenceFixture.java", "related/Hierarchy.java"}) {
			Path source = Path.of("tests/fixtures", file);
			sources.put(source.getFileName().toString(), Files.readString(source));
		}
		sources.put("Unicode.java", "package probe; public class Unicode { public String text() { return \"Živjo 😀 qualification\"; } }");
		Path jar = HierarchyFixture.javaJar(root.resolve("small"), "qualification.jar", sources);
		Files.copy(jar, root.resolve("qualification.jar"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		Map<String, String> longSources = new TreeMap<>();
		for (int i = 0; i < 145; i++) {
			String name = String.format("Owned%03d", i);
			longSources.put(name + ".java", "package workload; public class " + name + " { public int value(int n) { return n + " + i + "; } }");
		}
		Path workload = HierarchyFixture.javaJar(root.resolve("long"), "workload.jar", longSources);
		Files.copy(workload, root.resolve("workload.jar"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		HierarchyFixture.dex(root, "owned.dex", Files.readString(Path.of("tests/fixtures/variables/LocalDex.smali")));
		HierarchyFixture.dex(root, "unused-catch.dex", Files.readString(Path.of("tests/fixtures/variables/UnusedCatch.smali")));
		assertTrue(Files.size(root.resolve("owned.dex")) > 100);
		assertTrue(Files.isRegularFile(root.resolve("small/classes/probe/Unicode.class")));
	}
}
