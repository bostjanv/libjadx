package dev.libjadx.probes;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import dev.libjadx.app.EditBatchService;
import dev.libjadx.app.ProjectRuntime;
import dev.libjadx.core.edits.EditDtos;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.scheduler.ProjectBusyException;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.ResourceFile;
import jadx.api.ResourceType;
import jadx.api.ResourcesLoader;
import jadx.core.utils.android.AndroidManifestParser;
import jadx.core.utils.android.AppAttribute;
import jadx.core.utils.exceptions.JadxException;
import jadx.core.xmlgen.BinaryXMLParser;
import jadx.core.xmlgen.ResContainer;
import jadx.core.xmlgen.entry.ResourceEntry;
import jadx.core.xmlgen.entry.ValuesParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Evidence-only resource gate: no public resource API or hostile-parser safety claim. */
class JadxResourceProbeTest {

	private static final Path FIXTURE = Path.of("tests/fixtures/resources");
	private static final String PACKAGE = "dev.libjadx.resourceprobe";
	private static final byte[] BINARY = {0, 1, 10, 13, (byte) 0xff, 42, 0};
	@TempDir Path dir;

	@Test
	void compiledManifestAndXmlPreserveDeclaredFactsNotRuntimeResolution() throws Exception {
		Path apk = apk("facts.apk", Map.of());
		try (var jadx = open(List.of(apk))) {
			var resources = jadx.getResources();
			assertEquals(Set.of("AndroidManifest.xml", "resources.arsc", "res/xml/settings.xml", "assets/note.txt",
					"assets/empty", "assets/binary.bin", "assets/not-xml.xml", "classes.dex"),
					resources.stream().map(ResourceFile::getOriginalName).collect(java.util.stream.Collectors.toSet()));
			var manifest = resource(jadx, "AndroidManifest.xml");
			assertEquals(ResourceType.MANIFEST, manifest.getType());
			assertArrayEquals(new byte[] {3, 0, 8, 0}, java.util.Arrays.copyOf(raw(manifest), 4));
			Document decoded = xml(jadx, text(manifest));
			assertManifestFacts(decoded, PACKAGE + ".MainActivity");
			assertManifestFacts(xml(jadx, Files.readString(FIXTURE.resolve("AndroidManifest.xml"))), ".MainActivity");
			assertEquals("@string/app_name", element(decoded, "application").getAttribute("android:label"));

			Element settings = xml(jadx, text(resource(jadx, "res/xml/settings.xml"))).getDocumentElement();
			assertEquals("@string/alias", settings.getAttribute("android:label"));
			assertEquals("@string/greeting", settings.getAttribute("greeting"));
			assertEquals("@android:string/ok", settings.getAttribute("framework"));
			assertEquals("@null", settings.getAttribute("empty"));
			assertEquals("@0x7f0200ff", settings.getAttribute("missing"), "aapt2 encoded this as a literal STRING");
		}
	}

	@Test
	void tableRetainsIdsVariantsAndTypedReferencesWithoutChoosingLocaleOrFollowingAliases() throws Exception {
		try (var jadx = open(List.of(apk("table.apk", Map.of())))) {
			var table = resource(jadx, "resources.arsc");
			var parser = ResourcesLoader.decodeStream(table, (size, stream) -> jadx.getResourcesLoader().decodeTable(table, stream));
			var storage = parser.getResStorage();
			assertEquals(PACKAGE, storage.getAppPackage());
			var values = new ValuesParser(parser.getStrings(), storage.getResourcesNames());
			Map<String, String> observations = new TreeMap<>();
			Map<String, ResourceEntry> entries = new LinkedHashMap<>();
			for (ResourceEntry entry : storage.getResources()) {
				String key = entry.getTypeName() + "/" + entry.getKeyName() + entry.getConfig();
				assertNull(entries.put(key, entry));
				if (entry.getSimpleValue() != null) observations.put(key, values.getSimpleValueString(entry));
			}
			assertEquals(10, entries.size());
			assertEquals(Map.of(
					"string/alias", "@string/greeting", "string/chain", "@string/alias",
					"string/app_name", "Owned probe", "string/app_name-sl", "Lastna sonda",
					"string/greeting", "Hello Živa \ufffd\ufffd", "string/greeting-sl", "Pozdrav Živa \ufffd\ufffd",
					"string/framework", "@android:string/ok", "string/missing", "@0x7f0200ff",
					"xml/settings", "res/xml/settings.xml"), observations);
			// Retained failure, not a corrected/lossless decoder: aapt2 writes the
			// supplementary code point as MUTF-8 surrogates; the pin uses UTF-8.
			assertTrue(Files.readString(FIXTURE.resolve("res/values/strings.xml")).contains("Hello Živa 🚀"));
			assertTrue(java.util.HexFormat.of().formatHex(raw(table)).contains("eda0bdedba80"));
			assertNotEquals("Hello Živa 🚀", observations.get("string/greeting"));
			assertEquals(List.of("first", "@string/greeting"), entries.get("array/choices").getNamedValues().stream()
					.map(v -> values.decodeValue(v.getRawValue())).toList());
			assertEquals(0x7f020004, entries.get("string/greeting").getId());
			assertEquals(0x7f020004, entries.get("string/greeting-sl").getId(), "ID alone loses configuration");
			assertEquals(1, entries.get("string/alias").getSimpleValue().getDataType()); // TYPE_REFERENCE
			assertEquals(0x7f020004, entries.get("string/alias").getSimpleValue().getData());
			assertEquals(3, entries.get("string/missing").getSimpleValue().getDataType()); // TYPE_STRING, not an edge
			assertEquals("?unknown_ref: 7f0200ff", values.decodeValue(1, 0x7f0200ff));

			var files = table.loadContent();
			assertEquals(ResContainer.DataType.RES_TABLE, files.getDataType());
			assertEquals(Set.of("res/values/strings.xml", "res/values-sl/strings.xml", "res/values/arrays.xml"),
					files.getSubFiles().stream().map(ResContainer::getName).collect(java.util.stream.Collectors.toSet()));
			assertEquals("Pozdrav Živa \ufffd\ufffd", namedValue(xml(jadx, subFile(files, "res/values-sl/strings.xml").getText().getCodeStr()), "greeting"));
			System.out.println("TABLE " + observations);
		}
	}

	@Test
	void rawAssetsAndDecodedResourcesRemainDistinctAcrossJavaGenerationAndClose() throws Exception {
		Path apk = apk("raw.apk", Map.of());
		byte[] original = Files.readAllBytes(apk);
		try (var jadx = open(List.of(apk))) {
			var cls = jadx.searchJavaClassByOrigFullName(PACKAGE + ".MainActivity");
			var state = cls.getClassNode().getState();
			var binary = resource(jadx, "assets/binary.bin");
			assertArrayEquals(BINARY, raw(binary));
			assertEquals(0, raw(resource(jadx, "assets/empty")).length);
			assertArrayEquals(Files.readAllBytes(FIXTURE.resolve("assets/note.txt")), raw(resource(jadx, "assets/note.txt")));
			assertEquals(ResContainer.DataType.RES_LINK, binary.loadContent().getDataType());
			assertEquals(ResContainer.DataType.RES_LINK, resource(jadx, "assets/note.txt").loadContent().getDataType());
			assertEquals("definitely not XML", text(resource(jadx, "assets/not-xml.xml")));
			String manifest = text(resource(jadx, "AndroidManifest.xml"));
			assertEquals(state, cls.getClassNode().getState(), "Resource reads need not generate Java");
			assertTrue(cls.getCode().contains("return 42;"));
			assertEquals(manifest, text(resource(jadx, "AndroidManifest.xml")));
			assertArrayEquals(BINARY, raw(binary));
			assertEquals(apk.toFile(), binary.getZipEntry().getZipFile());
		}
		assertArrayEquals(original, Files.readAllBytes(apk));
		assertFalse(Files.exists(dir.resolve("raw.apk.jadx")));
		assertFalse(Files.exists(dir.resolve("raw")), "No source/resource export was requested");
		assertTrue(Files.deleteIfExists(apk));
	}

	@Test
	void manifestHelperIsNotAComponentInventoryLocaleSelectorOrEnabledLauncherResolver() throws Exception {
		try (var jadx = open(List.of(apk("helper.apk", Map.of())))) {
			var manifest = AndroidManifestParser.getAndroidManifest(jadx.getResources());
			var files = resource(jadx, "resources.arsc").loadContent();
			var defaults = new AndroidManifestParser(manifest, subFile(files, "res/values/strings.xml"),
					EnumSet.allOf(AppAttribute.class), jadx.getArgs().getSecurity()).parse();
			assertEquals("Owned probe", defaults.getApplicationLabel());
			assertEquals(23, defaults.getMinSdkVersion());
			assertEquals(35, defaults.getTargetSdkVersion());
			assertEquals(37, defaults.getVersionCode());
			assertEquals("1.2-probe", defaults.getVersionName());
			assertEquals(".ProbeApp", defaults.getApplication());
			assertEquals(".DisabledActivity", defaults.getMainActivity(), "Helper ignores enabled=false and active alias");
			assertThrows(IllegalArgumentException.class, () -> new AndroidManifestParser(manifest,
					EnumSet.of(AppAttribute.APPLICATION_LABEL), jadx.getArgs().getSecurity()).parse());
			var localized = new AndroidManifestParser(manifest, subFile(files, "res/values-sl/strings.xml"),
					EnumSet.of(AppAttribute.APPLICATION_LABEL), jadx.getArgs().getSecurity()).parse();
			assertEquals("Lastna sonda", localized.getApplicationLabel(), "Caller supplied the locale document");
		}
		String alternatePrefix = Files.readString(FIXTURE.resolve("AndroidManifest.xml")).replace("android:", "a:").replace("xmlns:android=", "xmlns:a=");
		try (var jadx = open(List.of(apk("prefix.apk", Map.of("AndroidManifest.xml", bytes(alternatePrefix)))))) {
			var helper = new AndroidManifestParser(resource(jadx, "AndroidManifest.xml"),
					EnumSet.of(AppAttribute.MAIN_ACTIVITY, AppAttribute.APPLICATION), jadx.getArgs().getSecurity()).parse();
			assertNull(helper.getMainActivity(), "Helper uses literal android: prefix, not namespace URI");
			assertNull(helper.getApplication());
			assertTrue(text(resource(jadx, "AndroidManifest.xml")).contains("a:name=\".MainActivity\""));
		}
	}

	@Test
	void decodedBinaryManifestReflectsNativeAliasesWhileRawBytesDoNot() throws Exception {
		Path apk = apk("aliases.apk", Map.of());
		byte[] input = Files.readAllBytes(apk);
		try (var runtime = new ProjectRuntime(null, List.of(apk), List.of(dir))) {
			runtime.initializeAsync(null).get(30, TimeUnit.SECONDS);
			assertEquals("APPLIED", new EditBatchService(runtime).apply(new EditDtos.Request(null, null, List.of(
					new EditDtos.Operation(EditDtos.Kind.RENAME, SymbolRef.classRef("Ldev/libjadx/resourceprobe/MainActivity;"),
							"RenamedActivity", null, null)))).outcome());
			var before = runtime.projectSnapshot();
			assertTrue(before.dirty());
			runtime.withPrimarySymbolRead("resources", read -> {
				var jadx = read.decompiler();
				assertThrows(ProjectBusyException.class, () -> runtime.withPrimarySymbolRead("other", other -> null));
				assertEquals(List.of(".DisabledActivity", PACKAGE + ".RenamedActivity"),
						attributes(xml(jadx, text(resource(jadx, "AndroidManifest.xml"))), "activity", "android:name"));
				assertArrayEquals(inputEntry("AndroidManifest.xml"), raw(resource(jadx, "AndroidManifest.xml")));
				return null;
			});
			assertEquals(before, runtime.projectSnapshot(), "Resource read must not publish a mutation or save");
		}
		assertArrayEquals(input, Files.readAllBytes(apk));
		assertFalse(Files.exists(dir.resolve("aliases.apk.jadx")));
	}

	@Test
	void malformedXmlAndTablesAreNotSuccessfulEmptyResourcesAndHealthyReadsRecover() throws Exception {
		// Valid binary-XML header, followed by a resource map with an invalid header size.
		byte[] brokenXml = {3, 0, 8, 0, 16, 0, 0, 0, (byte) 0x80, 1, 7, 0, 8, 0, 0, 0};
		try (var jadx = open(List.of(apk("broken.apk", Map.of("res/xml/broken.xml", brokenXml))))) {
			var broken = resource(jadx, "res/xml/broken.xml");
			var failure = broken.loadContent();
			assertEquals(ResContainer.DataType.TEXT, failure.getDataType());
			assertTrue(failure.getText().getCodeStr().contains("Error decode xml"));
			assertTrue(failure.getText().getCodeStr().contains("BinaryXMLParser"), "Facade embeds a stack trace, not a typed failure");
			assertThrows(JadxException.class, () -> ResourcesLoader.decodeStream(broken,
					(size, stream) -> new BinaryXMLParser(jadx.getRoot()).parse(stream)));
			assertThrows(RuntimeException.class, () -> xml(jadx, text(resource(jadx, "assets/not-xml.xml"))));
			assertThrows(RuntimeException.class, () -> xml(jadx, "<!DOCTYPE manifest [<!ENTITY x 'probe'>]><manifest>&x;</manifest>"));
			assertManifestFacts(xml(jadx, text(resource(jadx, "AndroidManifest.xml"))), PACKAGE + ".MainActivity");
		}
		try (var jadx = open(List.of(apk("broken-table.apk", Map.of("resources.arsc", new byte[12]))))) {
			var table = resource(jadx, "resources.arsc");
			assertThrows(JadxException.class, () -> ResourcesLoader.decodeStream(table,
					(size, stream) -> jadx.getResourcesLoader().decodeTable(table, stream)));
			assertEquals(ResContainer.DataType.TEXT, table.loadContent().getDataType());
			assertTrue(text(table).contains("Error decode arsc"));
			assertArrayEquals(BINARY, raw(resource(jadx, "assets/binary.bin")));
			assertTrue(jadx.searchJavaClassByOrigFullName(PACKAGE + ".MainActivity").getCode().contains("return 42;"));
		}
	}

	@Test
	void unresolvedTypedXmlReferenceIsNotConfusedWithALiteralLookingLikeAReference() throws Exception {
		byte[] xml = inputEntry("res/xml/settings.xml");
		var buffer = ByteBuffer.wrap(xml).order(ByteOrder.LITTLE_ENDIAN);
		int replaced = 0;
		for (int i = 0; i <= xml.length - 8; i += 4) {
			// Res_value: size=8, reserved=0, type=TYPE_REFERENCE, data=greeting ID.
			if (buffer.getInt(i) == 0x01000008 && buffer.getInt(i + 4) == 0x7f020004) {
				buffer.putInt(i + 4, 0x7f0200ff);
				replaced++;
			}
		}
		assertEquals(1, replaced, "Only the typed greeting reference is changed");
		try (var jadx = open(List.of(apk("unknown.apk", Map.of("res/xml/settings.xml", xml))))) {
			var settings = xml(jadx, text(resource(jadx, "res/xml/settings.xml"))).getDocumentElement();
			assertEquals("0x7f0200ff", settings.getAttribute("greeting"));
			assertEquals("@0x7f0200ff", settings.getAttribute("missing"));
		}
	}

	@Test
	void multipleInputsRetainArchiveOriginsButManifestHelperSelectsOnlyTheFirst() throws Exception {
		Path first = apk("first.apk", Map.of());
		Path second = apk("second.apk", Map.of("AndroidManifest.xml", bytes("<manifest package=\"other.probe\"/>")));
		try (var jadx = open(List.of(first, second))) {
			var manifests = jadx.getResources().stream().filter(r -> r.getType() == ResourceType.MANIFEST).toList();
			assertEquals(2, manifests.size());
			assertEquals(List.of(first.toFile(), second.toFile()), manifests.stream().map(r -> r.getZipEntry().getZipFile()).toList());
			assertEquals(PACKAGE, xml(jadx, text(manifests.getFirst())).getDocumentElement().getAttribute("package"));
			assertEquals("other.probe", xml(jadx, text(manifests.getLast())).getDocumentElement().getAttribute("package"));
			assertSame(manifests.getFirst(), AndroidManifestParser.getAndroidManifest(jadx.getResources()));
		}
		Path standalone = Files.copy(FIXTURE.resolve("AndroidManifest.xml"), dir.resolve("AndroidManifest.xml"));
		try (var jadx = open(List.of(first, standalone))) {
			var file = resource(jadx, standalone.toAbsolutePath().toString());
			assertEquals(ResourceType.XML, file.getType(), "Absolute standalone path is not the exact MANIFEST filename");
			assertNull(file.getZipEntry());
			assertArrayEquals(Files.readAllBytes(standalone), raw(file));
		}
	}

	private Path apk(String name, Map<String, byte[]> replacements) throws Exception {
		var entries = new TreeMap<String, byte[]>();
		try (var zip = new ZipFile(FIXTURE.resolve("resources.apk").toFile())) {
			for (ZipEntry entry : zip.stream().toList()) {
				try (var stream = zip.getInputStream(entry)) { entries.put(entry.getName(), stream.readAllBytes()); }
			}
		}
		Path dex = HierarchyFixture.dex(dir.resolve(name + "-dex"), "classes.dex", """
				.class public Ldev/libjadx/resourceprobe/MainActivity;
				.super Ljava/lang/Object;
				.method public static answer()I
				.registers 1
				const/16 v0, 42
				return v0
				.end method
				""");
		entries.put("classes.dex", Files.readAllBytes(dex));
		entries.put("assets/binary.bin", BINARY);
		entries.put("assets/empty", new byte[0]);
		entries.put("assets/not-xml.xml", bytes("definitely not XML"));
		entries.putAll(replacements);
		Path apk = dir.resolve(name);
		try (var zip = new ZipOutputStream(Files.newOutputStream(apk))) {
			for (var entry : entries.entrySet()) {
				zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry();
			}
		}
		return apk;
	}

	private static JadxDecompiler open(List<Path> inputs) {
		var args = new JadxArgs();
		args.setInputFiles(inputs.stream().map(Path::toFile).toList());
		var jadx = new JadxDecompiler(args);
		try { jadx.load(); return jadx; }
		catch (RuntimeException | Error e) { jadx.close(); throw e; }
	}

	private static ResourceFile resource(JadxDecompiler jadx, String name) {
		return jadx.getResources().stream().filter(r -> r.getOriginalName().equals(name)).findFirst().orElseThrow();
	}

	private static ResContainer subFile(ResContainer files, String name) {
		return files.getSubFiles().stream().filter(f -> f.getName().equals(name)).findFirst().orElseThrow();
	}

	private static byte[] raw(ResourceFile resource) {
		try {
			return ResourcesLoader.decodeStream(resource, (size, stream) -> {
				assertTrue(size >= 0 && size < 8192, "Owned probe fixture size only, not a production admission limit");
				byte[] bytes = stream.readNBytes(8193); assertEquals(size, bytes.length); return bytes;
			});
		} catch (JadxException e) { throw new AssertionError(e); }
	}

	private static String text(ResourceFile resource) {
		var content = resource.loadContent(); assertEquals(ResContainer.DataType.TEXT, content.getDataType());
		return content.getText().getCodeStr();
	}

	private static byte[] inputEntry(String name) {
		try (var zip = new ZipFile(FIXTURE.resolve("resources.apk").toFile()); var in = zip.getInputStream(zip.getEntry(name))) {
			return in.readAllBytes();
		} catch (java.io.IOException e) { throw new AssertionError(e); }
	}

	private static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
	private static Document xml(JadxDecompiler jadx, String text) {
		return jadx.getArgs().getSecurity().parseXml(new ByteArrayInputStream(bytes(text)));
	}
	private static Element element(Document doc, String tag) { return (Element) doc.getElementsByTagName(tag).item(0); }
	private static List<String> attributes(Document doc, String tag, String attribute) {
		List<String> result = new ArrayList<>(); var nodes = doc.getElementsByTagName(tag);
		for (int i = 0; i < nodes.getLength(); i++) result.add(((Element) nodes.item(i)).getAttribute(attribute));
		return result;
	}
	private static String namedValue(Document doc, String name) {
		var nodes = doc.getElementsByTagName("string");
		for (int i = 0; i < nodes.getLength(); i++) {
			Element node = (Element) nodes.item(i);
			if (name.equals(node.getAttribute("name"))) return node.getTextContent();
		}
		throw new AssertionError("Missing string: " + name);
	}

	private static void assertManifestFacts(Document doc, String mainActivityName) {
		assertEquals(PACKAGE, doc.getDocumentElement().getAttribute("package"));
		assertEquals(List.of("android.permission.INTERNET", "android.permission.CAMERA"), attributes(doc, "uses-permission", "android:name"));
		assertEquals(List.of("", "28"), attributes(doc, "uses-permission", "android:maxSdkVersion"));
		assertEquals(PACKAGE + ".READ", element(doc, "permission").getAttribute("android:name"));
		assertEquals(List.of(".DisabledActivity", mainActivityName), attributes(doc, "activity", "android:name"));
		assertEquals(List.of("false", ""), attributes(doc, "activity", "android:enabled"));
		assertEquals(List.of("true", "false"), attributes(doc, "activity", "android:exported"));
		assertEquals(".Launcher", element(doc, "activity-alias").getAttribute("android:name"));
		assertEquals(".MainActivity", element(doc, "activity-alias").getAttribute("android:targetActivity"));
		assertEquals(".SyncService", element(doc, "service").getAttribute("android:name"));
		assertEquals(".BootReceiver", element(doc, "receiver").getAttribute("android:name"));
		assertEquals(".ProbeProvider", element(doc, "provider").getAttribute("android:name"));
		assertEquals(PACKAGE + ".data", element(doc, "provider").getAttribute("android:authorities"));
		assertEquals(PACKAGE + ".READ", element(doc, "provider").getAttribute("android:permission"));
	}
}
