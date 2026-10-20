package dev.libjadx.jadxadapter;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import dev.libjadx.core.EffectiveAnalysisConfig;
import dev.libjadx.probes.HierarchyFixture;
import dev.libjadx.project.NativeProjectRepository;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.data.impl.JadxCodeData;
import jadx.api.data.impl.JadxCodeRename;
import jadx.api.data.impl.JadxNodeRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OwnedDexAnalysisTest {
	@TempDir Path dir;
	JadxArgs args() { return JadxEngineFactory.arguments(List.of(), null, new JadxCodeData(), EffectiveAnalysisConfig.defaults()); }

	@Test void sameOwnedReadersRetainDivergentDuplicatesAndPathSelectionAfterEveryPathIsDeleted() throws Exception {
		Path later = HierarchyFixture.dex(dir, "classes2.dex", HierarchyFixture.smali("Ldup/D;", "Ldup/Absent;", "public", "first"));
		Path preferred = HierarchyFixture.dex(dir, "classes.dex", HierarchyFixture.smali("Ldup/D;", "Ljava/lang/Object;", "public synthetic", "second"));
		var paths = List.of(later, preferred, later);
		var repository = NativeProjectRepository.fromInputs(paths, List.of(dir));
		var accepted = repository.acceptedInputs();
		String expected;
		try (var fresh = new JadxDecompiler(JadxEngineFactory.arguments(paths, null, new JadxCodeData(), EffectiveAnalysisConfig.defaults()))) {
			fresh.load(); expected = fresh.searchJavaClassByOrigFullName("dup.D").getCode();
		}
		try (var owned = OwnedDexAnalysis.acquire(accepted)) {
			assertThrows(IllegalStateException.class, owned::decompiler);
			var defs = owned.census().classesByDescriptor().get("Ldup/D;");
			assertEquals(3, defs.size()); assertEquals("Ldup/Absent;", defs.getFirst().superclass());
			assertEquals("second", defs.get(1).methods().getFirst().ref().originalName());
			assertTrue(defs.get(1).methods().getFirst().has(4096));
			Files.delete(later); Files.delete(preferred);
			owned.load(args());
			assertEquals(expected, owned.decompiler().searchJavaClassByOrigFullName("dup.D").getCode());
			assertTrue(owned.decompiler().getArgs().getInputFiles().isEmpty());
			assertThrows(IllegalStateException.class, () -> owned.load(args()));
			owned.close(); owned.close();
			assertThrows(IllegalStateException.class, owned::decompiler);
			assertEquals(3, defs.size(), "Immutable census outlives engine close");
		}
	}

	@Test void nativeInitializationFailureCannotExposeAnEngineAndHealthyRetryRetainsUpdateListener() throws Exception {
		Path input = HierarchyFixture.dex(dir, "names.dex", HierarchyFixture.smali("Lowned/Names;", "Ljava/lang/Object;", "public", "f"));
		var accepted = NativeProjectRepository.fromInputs(List.of(input), List.of(dir)).acceptedInputs();
		try (var owned = OwnedDexAnalysis.acquire(accepted)) {
			var bad = args(); var code = new JadxCodeData();
			// Deliberate invalid native data makes the real upstream pipeline throw inside its swallowing boundary.
			code.setRenames(List.of(new JadxCodeRename(null, "invalid"))); bad.setCodeData(code);
			var failure = assertThrows(IllegalStateException.class, () -> owned.load(bad));
			assertEquals("Native initialization incomplete", failure.getMessage());
			assertThrows(IllegalStateException.class, owned::decompiler);
		}
		try (var owned = OwnedDexAnalysis.acquire(accepted)) {
			owned.load(args());
			var data = new JadxCodeData();
			data.setRenames(List.of(new JadxCodeRename(JadxNodeRef.forCls("owned.Names"), "Updated")));
			owned.decompiler().getArgs().setCodeData(data); owned.decompiler().reloadCodeData();
			assertEquals("owned.Updated", owned.decompiler().searchJavaClassByOrigFullName("owned.Names").getFullName());
		}
	}

	@Test void aggregateLimitsAndCorruptionRefuseBeforeAnyEngineCanLoad() throws Exception {
		StringBuilder large = new StringBuilder(".class public Lowned/Large;\n.super Ljava/lang/Object;\n");
		for (int m = 0; m < 16; m++) {
			large.append(".method public static m").append(m).append("()I\n.registers 1\n");
			large.append("const/16 v0, 7\n".repeat(2000)).append("return v0\n.end method\n");
		}
		Path bounded = HierarchyFixture.dex(dir, "bounded.dex", large.toString());
		var within = NativeProjectRepository.fromInputs(List.of(bounded), List.of(dir)).acceptedInputs();
		// 16 × 4001 code units and 16 × 2001 records are below both aggregate ceilings.
		try (var owned = OwnedDexAnalysis.acquire(within)) { assertEquals(16, owned.census().classesByDescriptor().get("Lowned/Large;").getFirst().methods().size()); }
		large.append(".method public static extra()I\n.registers 1\n").append("const/16 v0, 7\n".repeat(2000)).append("return v0\n.end method\n");
		Path input = HierarchyFixture.dex(dir, "large.dex", large.toString());
		var accepted = NativeProjectRepository.fromInputs(List.of(input), List.of(dir)).acceptedInputs();
		var refusal = assertThrows(IllegalArgumentException.class, () -> OwnedDexAnalysis.acquire(accepted));
		assertTrue(refusal.getMessage().contains("UNITS"), refusal.getMessage());
		Path valid = HierarchyFixture.dex(dir, "valid.dex", ".class public Lowned/Valid;\n.super Ljava/lang/Object;\n");
		var healthy = NativeProjectRepository.fromInputs(List.of(valid), List.of(dir)).acceptedInputs();
		try (var owned = OwnedDexAnalysis.acquire(healthy)) { owned.load(args()); assertNotNull(owned.decompiler()); }
		byte[] broken = Files.readAllBytes(valid); broken[8] ^= 1; Files.write(valid, broken);
		var corrupt = NativeProjectRepository.fromInputs(List.of(valid), List.of(dir)).acceptedInputs();
		assertThrows(RuntimeException.class, () -> OwnedDexAnalysis.acquire(corrupt));
	}

	@Test void censusRefusalAndSwallowedPartialClassLoadCannotExposeAnEngine() throws Exception {
		StringBuilder interfaces = new StringBuilder(".class public Lowned/Edges;\n.super Ljava/lang/Object;\n");
		for (int i = 0; i < 511; i++) interfaces.append(".implements Lmissing/I").append(i).append(";\n");
		Path exact = HierarchyFixture.dex(dir, "edges.dex", interfaces.toString());
		var accepted = NativeProjectRepository.fromInputs(List.of(exact), List.of(dir)).acceptedInputs();
		try (var owned = OwnedDexAnalysis.acquire(accepted)) {
			assertEquals(511, owned.census().classesByDescriptor().get("Lowned/Edges;").getFirst().interfaces().size());
		}
		Path over = HierarchyFixture.dex(dir, "over.dex", interfaces.append(".implements Lmissing/Extra;\n").toString());
		var rejected = NativeProjectRepository.fromInputs(List.of(over), List.of(dir)).acceptedInputs();
		assertTrue(assertThrows(IllegalArgumentException.class, () -> OwnedDexAnalysis.acquire(rejected)).getMessage().contains("Hierarchy edge limit"));
		// Raw declarations are readable but a non-Object class without a superclass cannot load.
		Path badClass = HierarchyFixture.dex(dir, "bad-class.dex", ".class public Lowned/NoSuper;\n.super Ljava/lang/Object;\n");
		byte[] bytes = Files.readAllBytes(badClass);
		var raw = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
		raw.putInt(raw.getInt(100) + 8, -1); // class_defs_off, then superclass_idx = NO_INDEX
		var checksum = new java.util.zip.Adler32(); checksum.update(bytes, 12, bytes.length - 12);
		raw.putInt(8, (int) checksum.getValue()); Files.write(badClass, bytes);
		var bad = NativeProjectRepository.fromInputs(List.of(badClass), List.of(dir)).acceptedInputs();
		try (var owned = OwnedDexAnalysis.acquire(bad)) {
			assertThrows(IllegalStateException.class, () -> owned.load(args()));
			assertThrows(IllegalStateException.class, owned::decompiler);
		}
	}
}
