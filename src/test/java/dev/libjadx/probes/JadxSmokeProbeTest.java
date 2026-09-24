package dev.libjadx.probes;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;
import jadx.api.JavaMethod;
import jadx.api.data.ICodeComment;
import jadx.api.data.ICodeRename;
import jadx.api.data.impl.JadxCodeComment;
import jadx.api.data.impl.JadxCodeData;
import jadx.api.data.impl.JadxCodeRename;
import jadx.api.data.impl.JadxNodeRef;

class JadxSmokeProbeTest {

	@TempDir
	Path tempDir;

	@Test
	void loadsAndDecompilesARealJarUsingThePinnedPublicApi() throws IOException {
		Path jar = compileFixtureJar();
		JadxArgs args = new JadxArgs();
		args.getInputFiles().add(jar.toFile());
		JadxCodeData codeData = new JadxCodeData();
		List<ICodeRename> renames = new ArrayList<>();
		renames.add(new JadxCodeRename(JadxNodeRef.forCls("probe.Sample"), "RenamedSample"));
		codeData.setRenames(renames);
		List<ICodeComment> comments = new ArrayList<>();
		comments.add(new JadxCodeComment(JadxNodeRef.forCls("probe.Sample"), "phase 0 comment"));
		codeData.setComments(comments);
		args.setCodeData(codeData);

		try (JadxDecompiler jadx = new JadxDecompiler(args)) {
			jadx.load();
			List<JavaClass> classes = jadx.getClasses();
			assertTrue(classes.stream().anyMatch(cls -> cls.getFullName().equals("probe.RenamedSample")),
					() -> "Unexpected class names: " + classes.stream().map(JavaClass::getFullName).toList());
			JavaClass cls = classes.stream()
					.filter(candidate -> candidate.getFullName().equals("probe.RenamedSample"))
					.findFirst()
					.orElseThrow();
			String code = cls.getCode();
			assertTrue(code.contains("class RenamedSample"));
			assertTrue(code.contains("// phase 0 comment"));
			assertTrue(code.contains("int answer()"));
			assertTrue(cls.getCodeInfo().getCodeMetadata().getAsMap().size() > 0);
			assertTrue(cls.getSmali().contains(".class"));
			JavaMethod answer = cls.getMethods().stream()
					.filter(method -> method.getName().equals("answer"))
					.findFirst()
					.orElseThrow();
			assertTrue(answer.getUseIn().stream().anyMatch(use -> use.getName().contains("caller")));
		}
	}

	private Path compileFixtureJar() throws IOException {
		Path source = tempDir.resolve("src/probe/Sample.java");
		Files.createDirectories(source.getParent());
		Files.writeString(source, "package probe; public class Sample { public int answer() { return 42; } public int caller() { return answer(); } }");
		Path classes = Files.createDirectories(tempDir.resolve("classes"));
		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		if (compiler == null) {
			throw new IllegalStateException("A JDK compiler is required to build the probe fixture");
		}
		int result = compiler.run(null, null, null, "-d", classes.toString(), source.toString());
		if (result != 0) {
			throw new IllegalStateException("Fixture javac exited with " + result);
		}
		Path jar = tempDir.resolve("probe.jar");
		try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
			output.putNextEntry(new JarEntry("probe/Sample.class"));
			Files.copy(classes.resolve("probe/Sample.class"), output);
			output.closeEntry();
		}
		return jar;
	}
}
