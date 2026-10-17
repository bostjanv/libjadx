package dev.libjadx.probes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.core.dex.attributes.AType;
import jadx.core.dex.nodes.ProcessState;
import jadx.plugins.input.dex.sections.DexClassData;
import jadx.plugins.input.java.data.JavaClassData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pins the disassembly format and scope before exposing a public representation. */
class JadxSmaliProbeTest {

	@TempDir Path dir;

	@Test
	void dexDisassemblyRetainsInstructionsEvenWhenJavaDecodingFails() throws Exception {
		Path dex = HierarchyFixture.dex(dir, "smali.dex",
				Files.readString(Path.of("tests/fixtures/smali/Disassembly.smali")));
		JadxArgs args = new JadxArgs();
		args.setInputFiles(List.of(dex.toFile()));
		try (JadxDecompiler jadx = new JadxDecompiler(args)) {
			jadx.load();
			var cls = jadx.getClasses().getFirst();
			var data = assertInstanceOf(DexClassData.class, cls.getClassNode().getClsData());
			var state = cls.getClassNode().getState();
			String smali = data.getDisassembledCode();
			assertEquals(state, cls.getClassNode().getState());
			assertTrue(smali.contains(".class public Lprobe/Disassembly;"));
			assertTrue(smali.contains("const/16 v0, 0x2a"));
			assertTrue(smali.contains(".method public static broken()I"), smali);
			cls.getCode();
			assertTrue(jadx.getErrorsCount() > 0, "Owned invalid register must exercise failed Java decoding");
			assertEquals(ProcessState.GENERATED_AND_UNLOADED, cls.getClassNode().getState());
			var broken = cls.getClassNode().getMethods().stream()
					.filter(method -> method.getName().equals("broken")).findFirst().orElseThrow();
			assertTrue(broken.getAll(AType.JADX_ERROR).isEmpty(), "Codegen unload discards error attributes");
			assertTrue(jadx.getRoot().getErrorsCounter().getErrorNodes().contains(broken),
					"Pinned counter retains failed node identities after codegen");
			assertEquals(smali, data.getDisassembledCode());
		}
	}

	@Test
	void jarGetSmaliIsRaungAndConcatenatesInnerClasses() throws Exception {
		Path jar = HierarchyFixture.javaJar(dir, "smali.jar", Map.of("probe/Outer.java", """
				package probe;
				public class Outer {
				    public static int answer() { return 42; }
				    public static class Inner { public int other() { return 17; } }
				}
				"""));
		JadxArgs args = new JadxArgs();
		args.setInputFiles(List.of(jar.toFile()));
		try (JadxDecompiler jadx = new JadxDecompiler(args)) {
			jadx.load();
			var cls = jadx.getClasses().getFirst();
			var data = assertInstanceOf(JavaClassData.class, cls.getClassNode().getClsData());
			String disassembly = data.getDisassembledCode();
			assertTrue(disassembly.contains("ireturn"));
			assertFalse(disassembly.contains("const/16"));
			assertFalse(disassembly.contains("bipush 17"));
			assertTrue(cls.getSmali().contains("(probe.Outer$Inner)"), cls.getSmali());
			assertTrue(cls.getSmali().contains("ireturn"));
		}
	}
}
