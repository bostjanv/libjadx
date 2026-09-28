package dev.libjadx.jadxadapter;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.libjadx.core.hierarchy.CensusLimits;
import dev.libjadx.core.hierarchy.RelatedHierarchyVerifier.Status;
import dev.libjadx.probes.HierarchyFixture;
import dev.libjadx.probes.RelatedFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class InputCensusTest {
	@TempDir Path dir;

	@Test void exactImmutableOriginalDefinitionsHaveConfiguredOriginsAndDoNotWarmOrMutatePrimary() throws Exception {
		var inputs = RelatedFixture.compile(dir); var capture = JadxInputCensusAdapter.capture(inputs, CensusLimits.defaults());
		assertEquals(Status.COMPLETE, capture.status());
		assertEquals(23, capture.census().classesByDescriptor().size());
		var joined = capture.census().classesByDescriptor().get("Lrelated/Hierarchy$Joined;").getFirst();
		assertEquals("Ljava/lang/Object;", joined.superclass());
		assertEquals(List.of("Lrelated/Hierarchy$ExtendedLeft;", "Lrelated/Hierarchy$SeparateRight;"), joined.interfaces());
		assertEquals("(I)I", joined.methods().stream().filter(m -> m.ref().originalName().equals("joined")).findFirst().orElseThrow().ref().originalDescriptor());
		assertEquals(0, joined.origin().position());
		assertEquals(inputs.getFirst().toRealPath().toString(), joined.origin().canonicalPath());
		assertThrows(UnsupportedOperationException.class, () -> joined.interfaces().clear());
		assertThrows(UnsupportedOperationException.class, () -> capture.census().classesByDescriptor().clear());
		try (var engine = RelatedFixture.open(inputs)) {
			var before = engine.getArgs().getCodeData();
			var states = engine.getRoot().getClasses().stream().map(c -> c.getState()).toList();
			var aliases = engine.getRoot().getClasses().stream().map(c -> c.getClassInfo().getAliasFullName()).toList();
			assertEquals(Status.COMPLETE, capture.bind(engine).verify(RelatedFixture.ref("Joined", "joined", "(I)I"),
					dev.libjadx.core.hierarchy.RelatedHierarchyVerifier.VerificationBudget.defaults()).status());
			assertEquals(states, engine.getRoot().getClasses().stream().map(c -> c.getState()).toList());
			assertTrue(states.stream().allMatch(s -> s == jadx.core.dex.nodes.ProcessState.NOT_LOADED));
			assertEquals(aliases, engine.getRoot().getClasses().stream().map(c -> c.getClassInfo().getAliasFullName()).toList());
			assertSame(before, engine.getArgs().getCodeData());
		}
		assertTrue(capture.unchanged()); assertFalse(Files.exists(dir.resolve("hierarchy.jar.jadx")));
	}

	@Test void duplicateClassAndInterfaceMetadataAndDiscardedEdgesAreRetained() throws Exception {
		Path one = HierarchyFixture.javaJar(dir.resolve("one"), "one.jar", Map.of("dup/D.java",
				"package dup; interface I { int m(int n); } class P { public int m(int n){return n;} } public class D extends P implements I { public int m(int n){return n;} }"));
		Path two = HierarchyFixture.javaJar(dir.resolve("two"), "two.jar", Map.of("dup/D.java",
				"package dup; interface I { default int m(int n){return n;} } interface Extra {int extra();} public class D implements I,Extra { protected int other(int n){return n;} public int extra(){return 0;} }"));
		var capture = JadxInputCensusAdapter.capture(List.of(one, two), CensusLimits.defaults());
		assertEquals(Status.COMPLETE, capture.status());
		for (String name : List.of("Ldup/D;", "Ldup/I;")) {
			var definitions = capture.census().classesByDescriptor().get(name); assertEquals(2, definitions.size());
			assertNotEquals(definitions.get(0).origin(), definitions.get(1).origin());
		}
		var definitions = capture.census().classesByDescriptor().get("Ldup/D;");
		assertEquals("Ldup/P;", definitions.get(0).superclass());
		assertTrue(definitions.get(1).interfaces().contains("Ldup/Extra;"));
		assertTrue(definitions.get(1).methods().stream().anyMatch(m -> m.ref().originalName().equals("other")));
		var interfaceDefs = capture.census().classesByDescriptor().get("Ldup/I;");
		assertNotEquals(interfaceDefs.get(0).methods().getFirst().access(), interfaceDefs.get(1).methods().getFirst().access());
		Path three = HierarchyFixture.javaJar(dir.resolve("three"), "three.jar", Map.of("dup/D.java",
				"package dup; public class D { protected int m(int n){return n;} }"));
		var divergent = JadxInputCensusAdapter.capture(List.of(one, three), CensusLimits.defaults()).census().classesByDescriptor().get("Ldup/D;");
		var original = divergent.get(0).methods().stream().filter(m -> m.ref().originalName().equals("m")).findFirst().orElseThrow();
		var alternate = divergent.get(1).methods().stream().filter(m -> m.ref().originalName().equals("m")).findFirst().orElseThrow();
		assertEquals(original.ref(), alternate.ref()); assertNotEquals(original.access(), alternate.access());
		assertNotEquals(divergent.get(0).superclass(), divergent.get(1).superclass());
	}

	@Test void dexMetadataAndSelectFromDuplicatesPreferenceSeeBothRawOrigins() throws Exception {
		Path later = HierarchyFixture.dex(dir, "classes2.dex", HierarchyFixture.smali("Ldup/Dex;", "Ldup/Absent;", "public", "first"));
		Path preferred = HierarchyFixture.dex(dir, "classes.dex", HierarchyFixture.smali("Ldup/Dex;", "Ljava/lang/Object;", "public final", "chosen"));
		var capture = JadxInputCensusAdapter.capture(List.of(later, preferred), CensusLimits.defaults());
		assertEquals(Status.COMPLETE, capture.status());
		var definitions = capture.census().classesByDescriptor().get("Ldup/Dex;"); assertEquals(2, definitions.size());
		assertEquals("Ldup/Absent;", definitions.get(0).superclass());
		assertEquals("(I)I", definitions.get(1).methods().getFirst().ref().originalDescriptor());
		try (var engine = RelatedFixture.open(List.of(later, preferred))) {
			var visible = engine.getRoot().getClasses(); assertEquals(1, visible.size());
			assertEquals("first", visible.getFirst().getMethods().getFirst().getMethodInfo().getName(),
					"Raw DEX labels contain absolute paths, so selection uses the first definition");
			assertEquals(Status.AMBIGUOUS_INPUT, capture.bind(engine).verify(definitions.get(1).methods().getFirst().ref(),
					dev.libjadx.core.hierarchy.RelatedHierarchyVerifier.VerificationBudget.defaults()).status());
		}
		Path laterJar = archive("later.jar", "classes2.dex", later), preferredJar = archive("preferred.jar", "classes.dex", preferred);
		var embedded = JadxInputCensusAdapter.capture(List.of(laterJar, preferredJar), CensusLimits.defaults());
		assertEquals(Status.COMPLETE, embedded.status());
		assertEquals(2, embedded.census().classesByDescriptor().get("Ldup/Dex;").size());
		try (var engine = RelatedFixture.open(List.of(laterJar, preferredJar))) {
			assertEquals("chosen", engine.getRoot().getClasses().getFirst().getMethods().getFirst().getMethodInfo().getName(),
					"Embedded DEX labels allow SelectFromDuplicates to prefer the later classes.dex input");
		}
	}
	private Path archive(String name, String entry, Path input) throws Exception {
		Path jar = dir.resolve(name);
		try (var out = new java.util.jar.JarOutputStream(Files.newOutputStream(jar))) {
			out.putNextEntry(new java.util.jar.JarEntry(entry)); Files.copy(input, out); out.closeEntry();
		}
		return jar;
	}

	@ParameterizedTest @ValueSource(strings = {"inputs", "classes", "methodsPerClass", "methods", "edges", "duplicates", "archiveEntries", "fileBytes", "totalBytes", "characters"})
	void eachHardCensusLimitFailsWithoutReturningATruncatedCensus(String field) throws Exception {
		var inputs = new ArrayList<>(RelatedFixture.compile(dir)); inputs.add(RelatedFixture.duplicateLeaf(dir));
		var d = CensusLimits.defaults();
		var limits = new CensusLimits(field.equals("inputs") ? 1 : d.inputs(), field.equals("classes") ? 1 : d.classes(),
				field.equals("methodsPerClass") ? 1 : d.methodsPerClass(), field.equals("methods") ? 1 : d.methods(),
				field.equals("edges") ? 1 : d.edges(), field.equals("duplicates") ? 1 : d.duplicates(),
				field.equals("archiveEntries") ? 1 : d.archiveEntries(), field.equals("fileBytes") ? 1 : d.fileBytes(),
				field.equals("totalBytes") ? 1 : d.totalBytes(), field.equals("characters") ? 1 : d.characters());
		var capture = JadxInputCensusAdapter.capture(inputs, limits);
		assertEquals(Status.RESOURCE_LIMIT, capture.status(), field); assertNull(capture.census());
		assertTrue(capture.fingerprints().isEmpty());
	}

	@Test void exactAdmissionBoundaryCountsDuplicatesAndAllDeclaredMethods() throws Exception {
		var inputs = RelatedFixture.compile(dir); var full = JadxInputCensusAdapter.capture(inputs, CensusLimits.defaults());
		var classes = full.census().classesByDescriptor().values().stream().flatMap(List::stream).toList();
		int methods = classes.stream().mapToInt(c -> c.methods().size()).sum();
		int edges = classes.stream().mapToInt(c -> c.interfaces().size() + (c.superclass() == null ? 0 : 1)).sum();
		int max = classes.stream().mapToInt(c -> c.methods().size()).max().orElseThrow();
		var d = CensusLimits.defaults();
		assertEquals(Status.COMPLETE, JadxInputCensusAdapter.capture(inputs, new CensusLimits(inputs.size(), classes.size(), max, methods,
				edges, 1, d.archiveEntries(), d.fileBytes(), d.totalBytes(), d.characters())).status());
	}

	@Test void byteCharacterArchiveAndDuplicateBoundariesAreInclusive() throws Exception {
		Path jar = HierarchyFixture.javaJar(dir, "one.jar", Map.of("boundary/C.java", "package boundary; public class C { public int f(int n){return n;} }"));
		var inputs = List.of(jar, jar); var d = CensusLimits.defaults();
		var full = JadxInputCensusAdapter.capture(inputs, d); assertEquals(Status.COMPLETE, full.status());
		long expanded = 0; int entries;
		try (var archive = new java.util.jar.JarFile(jar.toFile())) {
			entries = archive.size() * 2;
			var enumeration = archive.entries();
			while (enumeration.hasMoreElements()) expanded += enumeration.nextElement().getSize() * 2;
		}
		long total = Files.size(jar) * 2 + expanded;
		long chars = full.census().inputs().stream().mapToLong(i -> i.canonicalPath().length()).sum();
		for (var definitions : full.census().classesByDescriptor().values()) for (var cls : definitions) {
			chars += cls.descriptor().length() + cls.superclass().length();
			for (String iface : cls.interfaces()) chars += iface.length();
			for (var method : cls.methods()) chars += method.ref().originalName().length() + method.ref().originalDescriptor().length();
		}
		int fileBytes = (int) Files.size(jar);
		for (String field : List.of("inputs", "classes", "methodsPerClass", "methods", "edges", "duplicates", "archiveEntries", "fileBytes", "totalBytes", "characters")) {
			for (int delta : List.of(0, 1)) {
				var limits = new CensusLimits(field.equals("inputs") ? 2 - delta : d.inputs(), field.equals("classes") ? 2 - delta : d.classes(),
						field.equals("methodsPerClass") ? 2 - delta : d.methodsPerClass(), field.equals("methods") ? 4 - delta : d.methods(),
						field.equals("edges") ? 2 - delta : d.edges(), field.equals("duplicates") ? 2 - delta : d.duplicates(),
						field.equals("archiveEntries") ? entries - delta : d.archiveEntries(), field.equals("fileBytes") ? fileBytes - delta : d.fileBytes(),
						field.equals("totalBytes") ? total - delta : d.totalBytes(), field.equals("characters") ? chars - delta : d.characters());
				assertEquals(delta == 0 ? Status.COMPLETE : Status.RESOURCE_LIMIT, JadxInputCensusAdapter.capture(inputs, limits).status(), field + " delta=" + delta);
			}
		}
	}

	@Test void malformedUnsupportedAndNestedInputsFailClosed() throws Exception {
		Path bad = dir.resolve("bad.class"); Files.write(bad, new byte[]{0, 1, 2, 3});
		assertEquals(Status.FAILED, JadxInputCensusAdapter.capture(List.of(bad), CensusLimits.defaults()).status());
		Path truncated = dir.resolve("truncated.class"); Files.write(truncated, new byte[]{(byte)0xca, (byte)0xfe, (byte)0xba, (byte)0xbe});
		assertEquals(Status.FAILED, JadxInputCensusAdapter.capture(List.of(truncated), CensusLimits.defaults()).status());
		Path apk = dir.resolve("unverified.apk"); Files.write(apk, new byte[]{0});
		assertEquals(Status.UNSUPPORTED_INPUT, JadxInputCensusAdapter.capture(List.of(apk), CensusLimits.defaults()).status());
		Path nested = dir.resolve("nested.jar");
		try (var out = new java.util.jar.JarOutputStream(Files.newOutputStream(nested))) {
			out.putNextEntry(new java.util.jar.JarEntry("inner.jar")); out.write(new byte[]{'P', 'K', 3, 4}); out.closeEntry();
		}
		assertEquals(Status.UNSUPPORTED_INPUT, JadxInputCensusAdapter.capture(List.of(nested), CensusLimits.defaults()).status());
		Path badArchive = archive("bad.jar", "broken.class", truncated);
		assertEquals(Status.FAILED, JadxInputCensusAdapter.capture(List.of(badArchive), CensusLimits.defaults()).status(), "No swallowed class callback error");
		Path badDex = dir.resolve("bad.dex"); Files.write(badDex, new byte[]{'d', 'e', 'x', '\n'});
		assertEquals(Status.FAILED, JadxInputCensusAdapter.capture(List.of(badDex), CensusLimits.defaults()).status());
	}

	@Test void checksumAndCooperativeInterruptionCannotPublishACompleteCensus() throws Exception {
		Path dex = HierarchyFixture.dex(dir, "checksum.dex", HierarchyFixture.smali("Lchecksum/C;", "Ljava/lang/Object;", "public", "f"));
		byte[] bytes = Files.readAllBytes(dex); bytes[8] ^= 1; Files.write(dex, bytes);
		assertEquals(Status.FAILED, JadxInputCensusAdapter.capture(List.of(dex), CensusLimits.defaults()).status());
		Thread.currentThread().interrupt();
		try { assertEquals(Status.FAILED, JadxInputCensusAdapter.capture(List.of(dex), CensusLimits.defaults()).status()); }
		finally { Thread.interrupted(); }
	}

	@Test void standaloneClassInputAndPrimaryInputMismatchAreExercised() throws Exception {
		Path jar = HierarchyFixture.javaJar(dir, "single.jar", Map.of("single/C.java", "package single; public class C { public int f(int n){return n;} }"));
		Path cls = dir.resolve("classes/single/C.class");
		var raw = JadxInputCensusAdapter.capture(List.of(cls), CensusLimits.defaults());
		assertEquals(Status.COMPLETE, raw.status()); assertEquals(1, raw.census().classesByDescriptor().size());
		var seed = new dev.libjadx.core.symbols.SymbolRef(dev.libjadx.core.symbols.SymbolRef.Kind.METHOD, "Lsingle/C;", null, "f", "(I)I");
		try (var engine = RelatedFixture.open(List.of(cls))) {
			assertEquals(Status.COMPLETE, raw.bind(engine).verify(seed, dev.libjadx.core.hierarchy.RelatedHierarchyVerifier.VerificationBudget.defaults()).status());
		}
		try (var engine = RelatedFixture.open(List.of(jar))) {
			assertEquals(Status.UNSUPPORTED_INPUT, raw.bind(engine).verify(seed, dev.libjadx.core.hierarchy.RelatedHierarchyVerifier.VerificationBudget.defaults()).status());
		}
	}

	@Test void boundedFingerprintUsesTheSameContentIdentityAndRefusesOverLimitBytes() throws Exception {
		Path file = dir.resolve("fingerprint.bin"); Files.write(file, new byte[100]);
		assertEquals(dev.libjadx.project.FileFingerprint.of(file), dev.libjadx.project.FileFingerprint.of(file, 100));
		assertThrows(dev.libjadx.project.FileFingerprint.LimitExceededException.class, () -> dev.libjadx.project.FileFingerprint.of(file, 99));
	}

	@Test void replacingAValidatedCanonicalInputWithASymlinkInvalidatesCaptureAndUse() throws Exception {
		Path path = HierarchyFixture.javaJar(dir.resolve("original"), "fixed.jar", Map.of("identity/C.java", "package identity; public class C { public int f(int n){return n;} }"));
		var captured = JadxInputCensusAdapter.capture(List.of(path), CensusLimits.defaults());
		assertEquals(Status.COMPLETE, captured.status());
		Path replacement = dir.resolve("replacement.jar"); Files.move(path, replacement);
		Files.createSymbolicLink(path, replacement);
		assertFalse(captured.unchanged());
		var rebuilt = JadxInputCensusAdapter.capture(List.of(path), CensusLimits.defaults());
		assertEquals(Status.INPUT_CHANGED, rebuilt.status()); assertNull(rebuilt.census());
	}
}
