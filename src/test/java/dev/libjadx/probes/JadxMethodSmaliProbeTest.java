package dev.libjadx.probes;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.core.dex.nodes.ClassNode;
import jadx.core.dex.nodes.MethodNode;
import jadx.core.dex.nodes.ProcessState;
import jadx.plugins.input.dex.sections.DexClassData;
import jadx.plugins.input.dex.sections.SectionReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Test-only use of the pinned input/disassembler; no text-based method selector. */
class JadxMethodSmaliProbeTest {

	@TempDir Path dir;
	@Test
	void fullOriginalIdentitySelectsAsymmetricOverloadsAndSurvivesAliasesAndUnload() throws Exception {
		Path dex = HierarchyFixture.dex(dir, "methods.dex",
				Files.readString(Path.of("tests/fixtures/smali/MethodDisassembly.smali")));
		try (var jadx = load(dex)) {
			var cls = jadx.getClasses().getFirst();
			var node = cls.getClassNode();
			var state = node.getState();
			List<String> signatures = List.of("pick(I)I", "pick(I)J", "pick(Ljava/lang/String;J)Ljava/lang/String;", "absent(I)I");
			List<String> texts = new ArrayList<>();
			for (String sig : signatures) {
				String text = disassemble(node, "Lprobe/MethodDisassembly;", sig, false);
				assertEquals(1, text.lines().filter(line -> line.startsWith(".method ")).count());
				assertTrue(text.contains(" " + sig + "\n"), text);
				texts.add(text);
			}
			assertTrue(texts.get(0).contains("add-int/2addr"));
			assertFalse(texts.get(1).contains("add-int/2addr"));
			assertTrue(texts.get(1).contains("return-wide"));
			assertTrue(texts.get(2).contains("return-object"));
			assertFalse(texts.get(3).contains(".registers"));
			assertNull(method(node, "absent(I)I").getCodeReader());
			assertThrows(IllegalArgumentException.class, () -> disassemble(node, "Lwrong/Owner;", "pick(I)I", false));
			assertThrows(IllegalArgumentException.class, () -> disassemble(node, "Lprobe/MethodDisassembly;", "pick(I)V", false));
			assertEquals(state, node.getState());
			cls.getCodeInfo();
			assertEquals(ProcessState.GENERATED_AND_UNLOADED, node.getState());
			node.getClassInfo().changeShortName("AliasOwner");
			node.getMethods().forEach(m -> m.getMethodInfo().setAlias("aliasMethod"));
			for (int i = 0; i < signatures.size(); i++) {
				assertEquals(texts.get(i), disassemble(node, "Lprobe/MethodDisassembly;", signatures.get(i), false));
			}
			System.out.println("Full-descriptor method selection, bodyless declaration, aliases and post-Java unload: verified");
		}
	}

	@Test
	void upstreamOffsetCommentsCorrespondToReaderButDefaultTextHasNoOffsetMap() throws Exception {
		Path dex = HierarchyFixture.dex(dir, "offsets.dex",
				Files.readString(Path.of("tests/fixtures/smali/MethodDisassembly.smali")));
		try (var jadx = load(dex)) {
			var cls = jadx.getClasses().getFirst();
			var node = cls.getClassNode();
			String plain = disassemble(node, "Lprobe/MethodDisassembly;", "flow(I)I", false);
			assertFalse(plain.contains("#@"), "Default class Smali does not carry offset annotations");
			String annotated = disassemble(node, "Lprobe/MethodDisassembly;", "flow(I)I", true);
			List<Integer> offsets = new ArrayList<>();
			method(node, "flow(I)I").getCodeReader().copy().visitInstructions(insn -> {
				insn.decode();
				offsets.add(insn.getOffset());
			});
			// Read only upstream-emitted address comments, not instruction syntax or method boundaries.
			List<Integer> comments = annotated.lines().map(String::trim).filter(s -> s.startsWith("#@"))
					.map(s -> Integer.parseUnsignedInt(s.substring(2), 16)).toList();
			assertEquals(offsets, comments, annotated);
			assertTrue(annotated.contains(".packed-switch"));
			assertTrue(annotated.contains(".catch "));
			assertTrue(annotated.contains(".line 100"));
			assertTrue(offsets.stream().anyMatch(o -> o > 10));
			assertTrue(annotated.contains("#@2\n    packed-switch"), annotated);
			assertTrue(annotated.contains("#@5\n    :try_start_5\n    invoke-static"), annotated);
			cls.getCodeInfo();
			assertEquals(ProcessState.GENERATED_AND_UNLOADED, node.getState());
			assertEquals(annotated, disassemble(node, "Lprobe/MethodDisassembly;", "flow(I)I", true));
			System.out.println("Loaded-reader code-unit offsets: " + offsets + "\n" + annotated);
		}
	}

	@Test
	void sharedSwitchPayloadCreatesTextAddressWithNoOriginalInstruction() throws Exception {
		Path dex = HierarchyFixture.dex(dir, "shared.dex",
				Files.readString(Path.of("tests/fixtures/smali/MethodDisassembly.smali")));
		// Smali 3.0.9 refuses shared payloads. Retarget the second 31t instruction
		// in our owned assembled DEX, then repair its standard signature/checksum.
		int code;
		try (var jadx = load(dex)) {
			code = method(jadx.getClasses().getFirst().getClassNode(), "shared(I)V").getCodeReader().getCodeOffset() + 16;
		}
		byte[] bytes = Files.readAllBytes(dex);
		var buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
		assertEquals(0x2b, Byte.toUnsignedInt(bytes[code]));
		assertEquals(0x2b, Byte.toUnsignedInt(bytes[code + 6]));
		buffer.putInt(code + 8, buffer.getInt(code + 2) - 3);
		var digest = java.security.MessageDigest.getInstance("SHA-1");
		digest.update(bytes, 32, bytes.length - 32);
		System.arraycopy(digest.digest(), 0, bytes, 12, 20);
		var checksum = new java.util.zip.Adler32();
		checksum.update(bytes, 12, bytes.length - 12);
		buffer.putInt(8, (int) checksum.getValue());
		Files.write(dex, bytes);
		try (var jadx = load(dex)) {
			var node = jadx.getClasses().getFirst().getClassNode();
			var shared = method(node, "shared(I)V");
			String text = disassemble(node, "Lprobe/MethodDisassembly;", "shared(I)V", true);
			int units = shared.getCodeReader().getUnitsCount();
			List<Integer> original = new ArrayList<>();
			shared.getCodeReader().copy().visitInstructions(insn -> {
				insn.decode();
				original.add(insn.getOffset());
			});
			List<Integer> emitted = text.lines().map(String::trim).filter(s -> s.startsWith("#@"))
					.map(s -> Integer.parseUnsignedInt(s.substring(2), 16)).toList();
			assertNotEquals(original, emitted, "Effective disassembly is not always original instruction inventory");
			assertTrue(emitted.stream().anyMatch(o -> o >= units), text);
			assertEquals(2, text.lines().filter(s -> s.trim().startsWith(".packed-switch ")).count(),
					"Two active payload bodies; the now-unreferenced encoded payload is commented out");
			System.out.println("Shared payload original=" + original + ", units=" + units + ", emitted=" + emitted + "\n" + text);
		}
	}

	@Test
	void nonDexIsRefusedBeforeDisassemblerUse() throws Exception {
		Path jar = HierarchyFixture.javaJar(dir, "jvm.jar", Map.of("Jvm.java",
				"public class Jvm { public static int pick(int x) { return x + 19; } }"));
		try (var jadx = load(jar)) {
			var node = jadx.getClasses().getFirst().getClassNode();
			assertFalse(node.getClsData() instanceof DexClassData);
			assertThrows(UnsupportedOperationException.class, () -> disassemble(node, "LJvm;", "pick(I)I", true));
		}
	}

	private static JadxDecompiler load(Path input) {
		var args = new JadxArgs();
		args.setInputFiles(List.of(input.toFile()));
		var jadx = new JadxDecompiler(args);
		jadx.load();
		return jadx;
	}

	private static MethodNode method(ClassNode cls, String sig) {
		return cls.getMethods().stream().filter(m -> m.getMethodInfo().getShortId().equals(sig)).findFirst().orElseThrow();
	}

	private static Class<?> type(String name) throws ClassNotFoundException {
		return Class.forName("com.android.tools.smali." + name);
	}

	/** Runtime-only dependency: reflection avoids changing dependency declarations for this probe. */
	private static Object call(Object target, String name) throws Exception {
		return target.getClass().getMethod(name).invoke(target);
	}

	private static String disassemble(ClassNode cls, String owner, String sig, boolean offsets) throws Exception {
		if (!(cls.getClsData() instanceof DexClassData data)) throw new UnsupportedOperationException("Not loaded DEX");
		// Exact same loaded buffer/class-def offset used by pinned DexClassData.getDisassembledCode().
		// No public buffer accessor exists on DexClassData: this private-field access is test-only evidence.
		var field = DexClassData.class.getDeclaredField("in");
		field.setAccessible(true);
		var section = (SectionReader) field.get(data);
		byte[] bytes = section.getDexReader().getBuf().array();
		var dexType = type("dexlib2.dexbacked.DexBackedDexFile");
		var dex = dexType.getConstructor(type("dexlib2.Opcodes"), byte[].class).newInstance(null, bytes);
		var classType = type("dexlib2.dexbacked.DexBackedClassDef");
		var classDef = classType.getConstructor(dexType, int.class, int.class).newInstance(dex, data.getClassDefOffset(), 0);
		List<Object> matches = new ArrayList<>();
		for (Object m : (Iterable<?>) call(classDef, "getMethods")) {
			StringBuilder parameters = new StringBuilder();
			for (Object parameter : (Iterable<?>) call(m, "getParameterTypes")) parameters.append(parameter);
			String full = call(m, "getName") + "(" + parameters + ")" + call(m, "getReturnType");
			if (owner.equals(call(m, "getDefiningClass")) && sig.equals(full)) matches.add(m);
		}
		if (matches.size() != 1) throw new IllegalArgumentException("Expected exactly one original method: " + matches.size());
		Object selected = matches.getFirst();
		Object options = type("baksmali.BaksmaliOptions").getConstructor().newInstance();
		options.getClass().getField("codeOffsets").setBoolean(options, offsets);
		var definitionType = type("baksmali.Adaptors.ClassDefinition");
		Object definition = definitionType.getConstructor(options.getClass(), type("dexlib2.iface.ClassDef")).newInstance(options, classDef);
		var output = new StringWriter();
		var writerType = type("baksmali.formatter.BaksmaliWriter");
		Object writer = writerType.getConstructor(java.io.Writer.class).newInstance(output);
		var methodType = type("baksmali.Adaptors.MethodDefinition");
		Object implementation = call(selected, "getImplementation");
		if (implementation == null) {
			methodType.getMethod("writeEmptyMethodTo", writerType, type("dexlib2.iface.Method"), definitionType)
					.invoke(null, writer, selected, definition);
		} else {
			Object methodDefinition = methodType.getConstructor(definitionType, type("dexlib2.iface.Method"), type("dexlib2.iface.MethodImplementation"))
					.newInstance(definition, selected, implementation);
			methodType.getMethod("writeTo", writerType).invoke(methodDefinition, writer);
		}
		return output.toString();
	}
}
