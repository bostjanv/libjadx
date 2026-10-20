package dev.libjadx.probes;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;

import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.core.clsp.ClsSet;
import jadx.core.clsp.ClspClass;
import jadx.core.clsp.ClspGraph;
import jadx.core.clsp.ClspMethod;
import jadx.core.dex.attributes.AFlag;
import jadx.core.dex.attributes.AType;
import jadx.core.dex.instructions.args.ArgType;
import jadx.core.dex.nodes.MethodNode;
import jadx.core.dex.nodes.RootNode;
import jadx.core.utils.exceptions.JadxRuntimeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Internal feasibility only: neither an ordinary dependency JAR option nor a service adapter. */
class JadxExternalClasspathProbeTest {
	@TempDir Path dir;

	@Test void absentVersusInternalExternalDeclarationsAndOrdinaryInputControl() throws Exception {
		var fixture = compile();
		try (var absent = open(List.of(fixture.app()))) {
			assertBoundary(absent, false);
			assertAnalysis(absent, false);
		}
		Path converted = dir.resolve("dependency.jcst");
		try (var preparation = open(List.of(fixture.dependency()))) {
			// The pinned converter rejects arbitrary library filenames: prove that boundary first.
			assertThrows(JadxRuntimeException.class, () -> new ClsSet(preparation.getRoot()).loadFrom(preparation.getRoot()));
		}
		// ClsSet only recognizes three framework basenames. This owned test file is NOT Android.
		Path frameworkNamed = jar(dir.resolve("classes"), "android.jar", "dep/Parent.class", "java/lang/Object.class");
		try (var preparation = open(List.of(frameworkNamed))) {
			var set = new ClsSet(preparation.getRoot());
			set.loadFrom(preparation.getRoot());
			assertEquals(2, set.getClassesCount());
			set.save(converted);
		}
		try (var recovered = open(List.of(fixture.app()), converted)) {
			assertBoundary(recovered, true);
			assertAnalysis(recovered, true);
			assertFalse(recovered.getClasses().getFirst().getCode().isBlank());
			assertBoundary(recovered, true);
			// Source generation unloads nodes/attributes: do not claim a durable rename guard.
			assertFalse(method(recovered, "choose(I)J").contains(AFlag.DONT_RENAME));
			assertThrows(JadxRuntimeException.class,
					() -> recovered.getRoot().getClsp().addClasspath(new ClsSet(recovered.getRoot())),
					"Public-looking addClasspath cannot append after the default set is loaded");
		}
		try (var ordinaryInput = open(List.of(fixture.app(), fixture.dependency()))) {
			assertEquals(2, ordinaryInput.getClasses().size());
			assertNotNull(ordinaryInput.getRoot().resolveClass(ArgType.object("dep.Parent")));
			assertEquals(jadx.core.clsp.ClspClassSource.APP, ordinaryInput.getRoot().getClsp()
					.getClsDetails(ArgType.object("dep.Parent")).getSource());
		}
	}

	@Test void missingMalformedAndOrdinaryJarAreNotAcceptedAsConvertedClasspath() throws Exception {
		var fixture = compile();
		try (var engine = open(List.of(fixture.app()))) {
			assertThrows(java.nio.file.NoSuchFileException.class,
					() -> installInternal(engine.getRoot(), dir.resolve("missing.jcst")));
			Path malformed = Files.writeString(dir.resolve("bad.jcst"), "not-a-class-set");
			assertThrows(jadx.core.utils.exceptions.DecodeException.class,
					() -> installInternal(engine.getRoot(), malformed));
			assertThrows(jadx.core.utils.exceptions.DecodeException.class,
					() -> installInternal(engine.getRoot(), fixture.dependency()));
			assertBoundary(engine, false);
			assertAnalysis(engine, false);
		}
	}

	private static void assertBoundary(JadxDecompiler engine, boolean recovered) {
		assertEquals(List.of("app.Child"), engine.getClasses().stream().map(c -> c.getRawName()).toList());
		var root = engine.getRoot();
		assertNull(root.resolveClass(ArgType.object("dep.Parent")), "External declaration is not a project ClassNode");
		assertEquals(recovered, root.getClsp().isClsKnown("dep.Parent"));
		if (recovered) assertEquals(jadx.core.clsp.ClspClassSource.CORE,
				root.getClsp().getClsDetails(ArgType.object("dep.Parent")).getSource());
		assertFalse(root.getClsp().isClsKnown("dep.Missing"), "Omitted dependency must remain unresolved");
		assertNull(root.resolveClass(ArgType.object("dep.Missing")));
	}

	private static void assertAnalysis(JadxDecompiler engine, boolean recovered) {
		if (recovered) assertTrue(engine.getRoot().getClsp().getClsDetails(ArgType.object("dep.Parent"))
				.getMethodsMap().containsKey("choose(I)J"));
		for (String signature : List.of("choose(I)J", "choose(D)F")) {
			var method = method(engine, signature);
			assertEquals(recovered, method.contains(AFlag.DONT_RENAME), signature);
			var overrides = method.get(AType.METHOD_OVERRIDE);
			if (recovered) {
				assertNotNull(overrides, signature);
				assertTrue(overrides.getOverrideList().stream().anyMatch(m -> m instanceof ClspMethod
						&& m.getMethodInfo().getDeclClass().getRawName().equals("dep.Parent")
						&& m.getMethodInfo().getShortId().equals(signature)), signature);
			} else {
				assertNull(overrides, signature);
			}
		}
		var nonexistent = method(engine, "choose(J)I");
		assertFalse(nonexistent.contains(AFlag.DONT_RENAME));
		assertNull(nonexistent.get(AType.METHOD_OVERRIDE), "Same name alone is not a signature match");
		assertNull(method(engine, "missing()Ldep/Missing;").get(AType.METHOD_OVERRIDE));
	}

	private static MethodNode method(JadxDecompiler engine, String signature) {
		return engine.getClasses().getFirst().getClassNode().getMethods().stream()
				.filter(m -> m.getMethodInfo().getShortId().equals(signature)).findFirst().orElseThrow();
	}

	/** Two deliberate private-API bridges, not a supported Jadx configuration mechanism. */
	@SuppressWarnings("unchecked")
	private static void installInternal(RootNode root, Path converted) throws Exception {
		var set = new ClsSet(root);
		var load = ClsSet.class.getDeclaredMethod("load", InputStream.class);
		load.setAccessible(true);
		try (var input = Files.newInputStream(converted)) {
			try { load.invoke(set, input); }
			catch (InvocationTargetException error) { throw (Exception) error.getCause(); }
		}
		var names = ClspGraph.class.getDeclaredField("nameMap");
		names.setAccessible(true);
		var additions = new java.util.HashMap<String, ClspClass>();
		set.addToMap(additions);
		var existing = (Map<String, ClspClass>) names.get(root.getClsp());
		// The owned Object stub closes the serializer's universe, not the runtime framework.
		additions.forEach(existing::putIfAbsent);
		root.getClsp().initCache();
	}

	private static JadxDecompiler open(List<Path> inputs) {
		return open(inputs, null);
	}

	private static JadxDecompiler open(List<Path> inputs, Path converted) {
		var args = new JadxArgs();
		args.setInputFiles(inputs.stream().map(Path::toFile).toList());
		var engine = new JadxDecompiler(args);
		if (converted != null) engine.addCustomPass(new jadx.api.plugins.pass.types.JadxPreparePass() {
			@Override public jadx.api.plugins.pass.JadxPassInfo getInfo() {
				return new jadx.api.plugins.pass.impl.OrderedJadxPassInfo("OwnedClasspathProbe", "Test-only private bridge")
						.before("OverrideMethodVisitor");
			}
			@Override public void init(RootNode root) {
				try { installInternal(root, converted); }
				catch (Exception error) { throw new IllegalStateException("Test classpath installation failed", error); }
			}
		});
		try { engine.load(); return engine; }
		catch (RuntimeException error) { engine.close(); throw error; }
	}

	private record Fixture(Path app, Path dependency) { }

	private Fixture compile() throws Exception {
		Path sources = Files.createDirectories(dir.resolve("sources"));
		Path parent = Files.writeString(sources.resolve("Parent.java"), """
				package dep;
				public class Parent {
				  public long choose(int n) { return n; }
				  public float choose(double n) { return (float) n; }
				}
				""");
		Path missing = Files.writeString(sources.resolve("Missing.java"), "package dep; public class Missing { }");
		Path child = Files.writeString(sources.resolve("Child.java"), """
				package app;
				public class Child extends dep.Parent {
				  public long choose(int n) { return n + 1L; }
				  public float choose(double n) { return (float) n + 1; }
				  public int choose(long n) { return (int) n; }
				  public dep.Missing missing() { return null; }
				}
				""");
		Path classes = Files.createDirectories(dir.resolve("classes"));
		assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-g", "-d",
				classes.toString(), parent.toString(), missing.toString(), child.toString()));
		// ClsSet serialization requires a closed type universe, including Object.
		// Compile an owned minimal declaration, not a copied JDK/framework class.
		Path object = Files.writeString(sources.resolve("Object.java"), "package java.lang; public class Object { }");
		assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "--release", "8", "-d",
				classes.toString(), object.toString()));
		return new Fixture(jar(classes, "app.jar", "app/Child.class"), jar(classes, "dependency.jar", "dep/Parent.class"));
	}

	private Path jar(Path classes, String name, String... entries) throws Exception {
		Path path = dir.resolve(name);
		try (var output = new JarOutputStream(Files.newOutputStream(path))) {
			for (String entry : entries) {
				output.putNextEntry(new JarEntry(entry));
				Files.copy(classes.resolve(entry), output);
				output.closeEntry();
			}
		}
		return path;
	}
}
