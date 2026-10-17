package dev.libjadx.probes;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import dev.libjadx.jadxadapter.JadxSymbolAdapter;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.core.dex.info.MethodInfo;
import jadx.core.dex.nodes.MethodNode;
import jadx.plugins.input.dex.sections.DexClassData;
import jadx.plugins.input.dex.sections.DexCodeReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pinned loaded-DEX input-reader feasibility and boundary evidence. */
class JadxInstructionReferenceProbeTest {

	@TempDir Path dir;

	@Test
	void loadedDexReferencesHaveMethodCodeUnitsNotContainerByteOffsets() throws Exception {
		Path dex = HierarchyFixture.dex(dir, "instructions.dex",
				Files.readString(Path.of("tests/fixtures/references/InstructionRefs.smali")));
		byte[] bytes = Files.readAllBytes(dex);
		Path archive = dir.resolve("embedded.jar");
		try (var out = new JarOutputStream(Files.newOutputStream(archive))) {
			out.putNextEntry(new JarEntry("classes.dex"));
			out.write(bytes);
			out.closeEntry();
		}
		List<Ref> expected = List.of(
				new Ref(2, "sget", "Lprobe/InstructionRefs;->total:I"),
				new Ref(4, "sput", "Lprobe/InstructionRefs;->total:I"),
				new Ref(6, "iget", "Lprobe/InstructionRefs;->value:I"),
				new Ref(8, "iput", "Lprobe/InstructionRefs;->value:I"),
				new Ref(10, "invoke-static", "Lprobe/InstructionRefs;->target()V"),
				new Ref(13, "invoke-static", "Lprobe/InstructionRefs;->target()V"),
				new Ref(16, "invoke-static", "Lmissing/External;->lost()V"),
				new Ref(19, "invoke-interface", "Lmissing/Api;->run()V"),
				new Ref(22, "invoke-static", "Lprobe/InstructionRefs;->entry(Lprobe/InstructionRefs;Lmissing/Api;)V"),
				new Ref(25, "const-class", "Lmissing/Type;"),
				new Ref(27, "invoke-static", "Lprobe/InstructionRefs;->target(Ljava/lang/Class;)V"));
		for (Path input : List.of(dex, archive)) {
			JadxArgs args = new JadxArgs();
			args.setInputFiles(List.of(input.toFile()));
			try (JadxDecompiler jadx = new JadxDecompiler(args)) {
				jadx.load();
				var cls = JadxSymbolAdapter.visibleClass(jadx, "Lprobe/InstructionRefs;", 0);
				assertInstanceOf(DexClassData.class, cls.getClassNode().getClsData());
				var entry = cls.getClassNode().getMethods().stream()
						.filter(method -> method.getName().equals("entry")).findFirst().orElseThrow();
				var state = cls.getClassNode().getState();
				assertEquals(expected, scan(entry, bytes));
				assertEquals(state, cls.getClassNode().getState(), "Raw scan must not trigger Java generation");
				assertNull(cls.getClassNode().getMethods().stream()
						.filter(method -> method.getName().equals("absent")).findFirst().orElseThrow().getCodeReader());
				assertEquals(31, entry.getCodeReader().getUnitsCount());
				if (input.equals(archive)) assertEquals("classes.dex", cls.getClassNode().getInputFileName(),
						"The loaded label alone does not identify the original archive");
				cls.getCodeInfo();
				assertEquals(expected, scan(entry, bytes), "Original reader survives Java transformation/unload");
				cls.getClassNode().getClassInfo().changeShortName("Alias");
				entry.getMethodInfo().setAlias("renamedEntry");
				assertEquals(expected, scan(entry, bytes), "Aliases must not rewrite encoded DEX references");
				System.out.println(input.getFileName() + " loaded-DEX refs (16-bit method code units): " + expected);
			}
		}
	}

	@Test
	void failedJavaRetainsRawDexReaderButJvmReaderDoesNotProveDexOffsets() throws Exception {
		Path dex = HierarchyFixture.dex(dir, "invalid.dex",
				Files.readString(Path.of("tests/fixtures/smali/Disassembly.smali")));
		Path jar = HierarchyFixture.javaJar(dir, "jvm.jar", Map.of("Healthy.java",
				"public class Healthy { public static int answer() { return 17; } }"));
		JadxArgs args = new JadxArgs();
		args.setInputFiles(List.of(dex.toFile(), jar.toFile()));
		try (JadxDecompiler jadx = new JadxDecompiler(args)) {
			jadx.load();
			var cls = JadxSymbolAdapter.visibleClass(jadx, "Lprobe/Disassembly;", 0);
			var broken = cls.getClassNode().getMethods().stream()
					.filter(method -> method.getName().equals("broken")).findFirst().orElseThrow();
			var reader = assertInstanceOf(DexCodeReader.class, broken.getCodeReader()).copy();
			List<Integer> before = new ArrayList<>();
			reader.visitInstructions(insn -> before.add(insn.getOffset()));
			cls.getCodeInfo();
			assertTrue(jadx.getRoot().getErrorsCounter().getErrorNodes().contains(broken));
			List<Integer> after = new ArrayList<>();
			broken.getCodeReader().copy().visitInstructions(insn -> after.add(insn.getOffset()));
			assertEquals(List.of(0), before);
			assertEquals(before, after);
			var jvm = JadxSymbolAdapter.visibleClass(jadx, "LHealthy;", 0);
			assertFalse(jvm.getClassNode().getClsData() instanceof DexClassData);
			assertFalse(jvm.getClassNode().getMethods().stream().filter(method -> method.getName().equals("answer"))
					.findFirst().orElseThrow().getCodeReader() instanceof DexCodeReader);
		}
	}

	@Test
	void invokeVariantsPayloadsAndReferencesSurviveRealJavaFailure() throws Exception {
		Path dex = HierarchyFixture.dex(dir, "variants.dex",
				Files.readString(Path.of("tests/fixtures/references/InstructionVariants.smali")), 28);
		JadxArgs args = new JadxArgs();
		args.setInputFiles(List.of(dex.toFile()));
		try (JadxDecompiler jadx = new JadxDecompiler(args)) {
			jadx.load();
			var cls = JadxSymbolAdapter.visibleClass(jadx, "Lprobe/InstructionVariants;", 0);
			var methods = cls.getClassNode().getMethods();
			var variants = methods.stream().filter(m -> m.getName().equals("variants")).findFirst().orElseThrow();
			List<Ref> refs = rawRefs(variants);
			assertEquals(List.of(0, 3, 6, 9, 12, 15, 18, 21, 24, 27), refs.stream().map(Ref::offsetCodeUnits).toList());
			assertEquals(List.of("invoke-virtual", "invoke-super", "invoke-direct", "invoke-static", "invoke-interface",
					"invoke-virtual/range", "invoke-super/range", "invoke-direct/range", "invoke-static/range", "invoke-interface/range"),
					refs.stream().map(Ref::opcode).toList());
			assertEquals("Lmissing/External;->wide(J[[I)[J", refs.get(3).target());
			assertEquals(refs.get(3).target(), refs.get(8).target());
			var fields = rawRefs(methods.stream().filter(m -> m.getName().equals("fields")).findFirst().orElseThrow());
			assertEquals(List.of(new Ref(0, "sget-wide", "Lmissing/Fields;->wide:J"),
					new Ref(2, "sput-wide", "Lmissing/Fields;->wide:J"),
					new Ref(4, "sget-object", "Lmissing/Fields;->matrix:[[I"),
					new Ref(6, "sput-object", "Lmissing/Fields;->matrix:[[I")), fields);
			var payloads = methods.stream().filter(m -> m.getName().equals("payloads")).findFirst().orElseThrow();
			assertEquals(List.of(new Ref(9, "invoke-static", "Lmissing/External;->before()V"),
					new Ref(32, "invoke-static", "Lmissing/External;->after()V")), rawRefs(payloads));
			assertEquals(1, payloads.getCodeReader().getTries().size());
			var broken = methods.stream().filter(m -> m.getName().equals("broken")).findFirst().orElseThrow();
			var expected = List.of(new Ref(0, "invoke-static", "Lmissing/External;->recover()V"));
			assertEquals(expected, rawRefs(broken));
			cls.getCodeInfo();
			assertTrue(jadx.getRoot().getErrorsCounter().getErrorNodes().contains(broken));
			assertEquals(expected, rawRefs(broken));
		}
	}

	@Test
	void truncatedReaderThrowsButUnknownOpcodeNeedsExplicitRejection() throws Exception {
		Path dex = HierarchyFixture.dex(dir, "malformed.dex",
				Files.readString(Path.of("tests/fixtures/references/InstructionRefs.smali")));
		JadxArgs args = new JadxArgs(); args.setInputFiles(List.of(dex.toFile()));
		try (JadxDecompiler jadx = new JadxDecompiler(args)) {
			jadx.load();
			var entry = JadxSymbolAdapter.visibleClass(jadx, "Lprobe/InstructionRefs;", 0).getClassNode().getMethods()
					.stream().filter(m -> m.getName().equals("entry")).findFirst().orElseThrow();
			int code = entry.getCodeReader().getCodeOffset();
			byte[] bytes = Files.readAllBytes(dex);
			var truncated = new jadx.plugins.input.dex.DexReader(1, "truncated.dex", bytes, 0);
			truncated.getBuf().limit(code + 17); // Header loaded; instruction buffer ends halfway through its first unit.
			var reader = new DexCodeReader(new jadx.plugins.input.dex.sections.SectionReader(
					truncated, code));
			assertThrows(RuntimeException.class, () -> reader.visitInstructions(insn -> insn.decode()));
			bytes[code + 16] = (byte) 0x73; // Reserved opcode: decode() alone does not reject it.
			var unknown = new DexCodeReader(new jadx.plugins.input.dex.sections.SectionReader(
					new jadx.plugins.input.dex.DexReader(2, "unknown.dex", bytes, 0), code));
			assertThrows(IllegalArgumentException.class, () -> unknown.visitInstructions(insn -> {
				if (((jadx.plugins.input.dex.insns.DexInsnData) insn).getInsnInfo() == null)
					throw new IllegalArgumentException("Unknown opcode");
				insn.decode();
			}));
		}
	}

	private static List<Ref> rawRefs(MethodNode method) {
		List<Ref> refs = new ArrayList<>();
		method.getCodeReader().copy().visitInstructions(insn -> {
			insn.decode();
			if (insn.getIndexType() == jadx.api.plugins.input.insns.InsnIndexType.METHOD_REF) {
				var target = insn.getIndexAsMethod(); target.load();
				refs.add(new Ref(insn.getOffset(), insn.getOpcodeMnemonic(), target.getParentClassType() + "->"
						+ target.getName() + "(" + String.join("", target.getArgTypes()) + ")" + target.getReturnType()));
			} else if (insn.getIndexType() == jadx.api.plugins.input.insns.InsnIndexType.FIELD_REF) {
				var target = insn.getIndexAsField();
				refs.add(new Ref(insn.getOffset(), insn.getOpcodeMnemonic(), target.getParentClassType() + "->"
						+ target.getName() + ":" + target.getType()));
			}
		});
		return refs;
	}

	private record Ref(int offsetCodeUnits, String opcode, String target) { }

	private static List<Ref> scan(MethodNode method, byte[] dexBytes) {
		var reader = assertInstanceOf(DexCodeReader.class, method.getCodeReader()).copy();
		List<Ref> refs = new ArrayList<>();
		List<Integer> offsets = new ArrayList<>();
		reader.visitInstructions(insn -> {
			insn.decode();
			offsets.add(insn.getOffset());
			// DEX code_item header is 16 bytes; insns[] consists of 16-bit code units.
			int byteOffset = reader.getCodeOffset() + 16 + insn.getOffset() * 2;
			assertEquals(byteOffset, insn.getFileOffset());
			String opcode = insn.getOpcodeMnemonic();
			String target = switch (insn.getIndexType()) {
				case METHOD_REF -> {
					var ref = insn.getIndexAsMethod();
					ref.load();
					var resolved = method.root().resolveMethod(MethodInfo.fromRef(method.root(), ref));
					assertEquals(ref.getParentClassType().equals("Lprobe/InstructionRefs;"), resolved != null);
					yield ref.getParentClassType() + "->" + ref.getName() + "("
							+ String.join("", ref.getArgTypes()) + ")" + ref.getReturnType();
				}
				case FIELD_REF -> {
					var ref = insn.getIndexAsField();
					yield ref.getParentClassType() + "->" + ref.getName() + ":" + ref.getType();
				}
				case TYPE_REF -> insn.getIndexAsType();
				case NONE -> null;
				default -> throw new AssertionError("Unproved index kind: " + insn.getIndexType());
			};
			int byteLength = opcode.startsWith("invoke-") ? 6 : opcode.equals("return-void") ? 2 : 4;
			assertArrayEquals(Arrays.copyOfRange(dexBytes, byteOffset, byteOffset + byteLength), insn.getByteCode());
			if (target != null) refs.add(new Ref(insn.getOffset(), opcode, target));
		});
		assertEquals(List.of(0, 2, 4, 6, 8, 10, 13, 16, 19, 22, 25, 27, 30), offsets);
		return List.copyOf(refs);
	}
}
