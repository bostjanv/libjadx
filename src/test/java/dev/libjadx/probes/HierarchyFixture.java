package dev.libjadx.probes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;

/** All generated Java/smali is owned by this repository. No upstream fixtures. */
public final class HierarchyFixture {
	private HierarchyFixture() { }
	public static List<Path> visibility(Path dir) throws Exception {
		Path classes = Files.createDirectories(dir.resolve("visibility-classes"));
		if (ToolProvider.getSystemJavaCompiler().run(null, null, null, "-g", "-d", classes.toString(),
				"tests/fixtures/hierarchy/p/Visibility.java", "tests/fixtures/hierarchy/q/Other.java") != 0)
			throw new IllegalStateException("Visibility fixture compilation failed");
		Path one = dir.resolve("visibility.jar"), two = dir.resolve("cross-package.jar");
		try (var out1 = new JarOutputStream(Files.newOutputStream(one)); var out2 = new JarOutputStream(Files.newOutputStream(two));
				var paths = Files.walk(classes)) {
			for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
				String name = classes.relativize(file).toString().replace('\\', '/');
				if (name.endsWith("$Omitted.class")) continue;
				var out = name.contains("/q/") || name.endsWith("$Simple.class") ? out2 : out1;
				out.putNextEntry(new JarEntry(name)); Files.copy(file, out); out.closeEntry();
			}
		}
		return List.of(one, two);
	}
	public static Path javaJar(Path root, String name, Map<String, String> sources) throws Exception {
		Files.createDirectories(root);
		Path classes = Files.createDirectories(root.resolve("classes"));
		var args = new java.util.ArrayList<String>(List.of("-g", "-d", classes.toString()));
		for (var source : new java.util.TreeMap<>(sources).entrySet()) {
			Path path = root.resolve(source.getKey()); Files.createDirectories(path.getParent()); Files.writeString(path, source.getValue());
			args.add(path.toString());
		}
		if (ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(String[]::new)) != 0)
			throw new IllegalStateException("Owned Java fixture failed");
		Path jar = root.resolve(name);
		try (var out = new JarOutputStream(Files.newOutputStream(jar)); var paths = Files.walk(classes)) {
			for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
				out.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
				Files.copy(file, out); out.closeEntry();
			}
		}
		return jar;
	}
	/** Existing runtime smali dependency, invoked reflectively to keep it out of production compile dependencies. */
	public static Path dex(Path root, String name, String source) throws Exception {
		Files.createDirectories(root);
		Path smali = root.resolve(name + ".smali"); Files.writeString(smali, source);
		Class<?> optionsClass = Class.forName("com.android.tools.smali.smali.SmaliOptions");
		Object options = optionsClass.getConstructor().newInstance();
		byte[] bytes = (byte[]) Class.forName("jadx.plugins.input.smali.SmaliUtils")
				.getMethod("assemble", java.io.File.class, optionsClass).invoke(null, smali.toFile(), options);
		Path path = root.resolve(name); Files.write(path, bytes); return path;
	}
	public static String smali(String owner, String parent, String flags, String name) {
		return ".class public " + owner + "\n.super " + parent + "\n.method " + flags + " " + name
				+ "(I)I\n.registers 2\nreturn p1\n.end method\n";
	}
}
