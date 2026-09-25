package dev.libjadx.app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;

final class SymbolFixtureSupport {
	private SymbolFixtureSupport() { }

	static Path compileFixture(Path root) throws IOException {
		return compile(root, Path.of("tests/fixtures/symbols/SymbolFixture.java"), "symbols.jar");
	}

	static Path duplicateJar(Path root, String folder, int value) throws IOException {
		Path source = root.resolve(folder + "/src/duplicate/Clash.java");
		Files.createDirectories(source.getParent());
		Files.writeString(source, "package duplicate; public class Clash { public int value() { return " + value + "; } }");
		return compile(root.resolve(folder), source, folder + ".jar");
	}

	/** Owned classfile fixture: two methods have the same name/arguments but distinct return descriptors. */
	static Path returnTypeClashJar(Path root) throws IOException {
		Path source = root.resolve("return-types/src/probe/ReturnClash.java");
		Files.createDirectories(source.getParent());
		Files.writeString(source, "package probe; public class ReturnClash { "
				+ "public int value() { return 1; } public String other() { return \"x\"; } }");
		Path classes = Files.createDirectories(root.resolve("return-types/compiled"));
		int result = ToolProvider.getSystemJavaCompiler().run(null, null, null,
				"-d", classes.toString(), source.toString());
		if (result != 0) throw new IOException("Fixture javac exited with " + result);
		byte[] bytes = Files.readAllBytes(classes.resolve("probe/ReturnClash.class"));
		byte[] needle = {1, 0, 5, 'o', 't', 'h', 'e', 'r'};
		int replacements = 0;
		for (int at = 0; at <= bytes.length - needle.length; at++) {
			boolean match = true;
			for (int offset = 0; offset < needle.length; offset++) {
				if (bytes[at + offset] != needle[offset]) { match = false; break; }
			}
			if (match) {
				System.arraycopy(new byte[] {'v', 'a', 'l', 'u', 'e'}, 0, bytes, at + 3, 5);
				replacements++;
			}
		}
		if (replacements != 1) throw new IOException("Expected exactly one original method-name pool entry");
		Path jar = root.resolve("return-clash.jar");
		try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
			output.putNextEntry(new JarEntry("probe/ReturnClash.class"));
			output.write(bytes);
			output.closeEntry();
		}
		return jar;
	}

	private static Path compile(Path root, Path source, String filename) throws IOException {
		Path classes = Files.createDirectories(root.resolve("compiled"));
		int result = ToolProvider.getSystemJavaCompiler().run(null, null, null,
				"-d", classes.toString(), source.toString());
		if (result != 0) throw new IOException("Fixture javac exited with " + result);
		Path jar = root.resolve(filename);
		try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar));
				var files = Files.walk(classes)) {
			for (Path file : files.filter(Files::isRegularFile).sorted(Comparator.naturalOrder()).toList()) {
				output.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
				Files.copy(file, output);
				output.closeEntry();
			}
		}
		return jar;
	}
}
