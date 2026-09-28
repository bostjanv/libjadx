package dev.libjadx.probes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;

import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.jadxadapter.JadxSymbolAdapter;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.JavaMethod;

public final class RelatedFixture {
	private RelatedFixture() { }

	/** Split a known original census across two inputs and deliberately omit one parent. */
	public static List<Path> compile(Path root) throws Exception {
		Path classes = Files.createDirectories(root.resolve("classes"));
		int result = ToolProvider.getSystemJavaCompiler().run(null, null, null, "-g", "-d", classes.toString(),
				"tests/fixtures/related/Hierarchy.java");
		if (result != 0) throw new IllegalStateException("Owned hierarchy compilation failed: " + result);
		Path first = root.resolve("hierarchy.jar"), second = root.resolve("branches.jar");
		try (var one = new JarOutputStream(Files.newOutputStream(first));
				var two = new JarOutputStream(Files.newOutputStream(second));
				var files = Files.walk(classes)) {
			for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
				String name = classes.relativize(file).toString().replace('\\', '/');
				if (name.equals("related/MissingRoot.class")) continue;
				var output = name.contains("$Sibling") || name.contains("$Right") || name.contains("$Inner") ? two : one;
				output.putNextEntry(new JarEntry(name)); Files.copy(file, output); output.closeEntry();
			}
		}
		return List.of(first, second);
	}

	/** A different original definition of Leaf; Jadx's visible census collapses it. */
	public static Path duplicateLeaf(Path root) throws Exception {
		Path source = root.resolve("duplicate-src/related/Hierarchy.java");
		Files.createDirectories(source.getParent());
		Files.writeString(source, "package related; public class Hierarchy { public static class Leaf { "
				+ "public int work(int value) { return 99; } public int onlyInDiscardedInput() { return 99; } } }");
		Path classes = Files.createDirectories(root.resolve("duplicate-classes"));
		if (ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", classes.toString(), source.toString()) != 0)
			throw new IllegalStateException("Duplicate fixture compilation failed");
		Path jar = root.resolve("duplicate.jar");
		try (var out = new JarOutputStream(Files.newOutputStream(jar))) {
			out.putNextEntry(new JarEntry("related/Hierarchy$Leaf.class"));
			Files.copy(classes.resolve("related/Hierarchy$Leaf.class"), out); out.closeEntry();
		}
		return jar;
	}

	public static JadxDecompiler open(List<Path> inputs) {
		var args = new JadxArgs(); args.setInputFiles(inputs.stream().map(Path::toFile).toList());
		var engine = new JadxDecompiler(args);
		try { engine.load(); return engine; }
		catch (RuntimeException failure) { engine.close(); throw failure; }
	}

	public static SymbolRef ref(String owner, String name, String descriptor) {
		return new SymbolRef(SymbolRef.Kind.METHOD, "Lrelated/Hierarchy$" + owner + ";", null, name, descriptor);
	}

	/** Raw declarations include compiler bridges that JavaClass.getMethods may suppress. */
	public static JavaMethod method(JadxDecompiler engine, SymbolRef ref) {
		var cls = engine.getClassesWithInners().stream()
				.filter(c -> JadxSymbolAdapter.descriptor(c.getRawName()).equals(ref.originalClassDescriptor()))
				.findFirst().orElseThrow();
		var nodes = cls.getClassNode().getMethods().stream()
				.filter(m -> JadxSymbolAdapter.originalRef(m.getMethodInfo()).equals(ref)).toList();
		if (nodes.size() != 1) throw new IllegalStateException("Expected unique original key: " + ref);
		return (JavaMethod) engine.getJavaNodeByRef(nodes.getFirst());
	}

	public static List<SymbolRef> group(JavaMethod seed) {
		return seed.getOverrideRelatedMethods().stream().map(JadxSymbolAdapter::originalRef)
				.sorted(java.util.Comparator.comparing(Object::toString)).toList();
	}
}
