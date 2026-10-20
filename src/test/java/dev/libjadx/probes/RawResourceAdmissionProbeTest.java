package dev.libjadx.probes;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import dev.libjadx.project.NativeProjectRepository;
import jadx.api.JadxDecompiler;
import jadx.api.ResourceFile;
import jadx.api.ResourcesLoader;
import jadx.zip.ZipReader;
import jadx.zip.ZipReaderFlags;
import jadx.zip.security.JadxZipSecurity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Evidence-only memory experiment, NOT a filesystem acquisition or complete ZIP validator.
 * Passing counterexamples below block adoption of the local-header stream as an inventory. */
class RawResourceAdmissionProbeTest {

	@TempDir Path dir;
	private static final byte[] FIRST = {0, (byte) 0xff, 3, 8, 13};
	private static final byte[] SECOND = {9, 6, 2, (byte) 0x80, 1};
	private static final String NAME = "assets/item.bin";
	private static final Limits LIMITS = new Limits(4, 32768, 65536, 32, 4096, 8192, 16384);

	@Test
	void ownedSlotCopiesMatchRealJadxAndOutliveInputsWithoutReopeningThem() throws Exception {
		byte[] one = archive(List.of(new Entry(NAME, FIRST), new Entry("empty", new byte[0])));
		byte[] two = archive(List.of(new Entry(NAME, SECOND)));
		Path first = Files.write(dir.resolve("one.apk"), one);
		Path second = Files.write(dir.resolve("two.apk"), two);
		var repository = NativeProjectRepository.fromInputs(List.of(first, second, first), List.of(dir));
		var accepted = repository.acceptedInputs();
		// Fixture acquisition only. No claim that readAllBytes enforces hostile-root containment.
		var inputs = List.of(new Input(Files.readAllBytes(first), accepted.inputs().get(0).fingerprint().sha256()),
				new Input(Files.readAllBytes(second), accepted.inputs().get(1).fingerprint().sha256()),
				new Input(Files.readAllBytes(first), accepted.inputs().get(2).fingerprint().sha256()));
		try (var jadx = new JadxDecompiler()) {
			var resources = new ArrayList<ResourceFile>();
			jadx.getResourcesLoader().defaultLoadFile(resources, first.toFile(), "");
			assertArrayEquals(FIRST, ResourcesLoader.decodeStream(resources.getFirst(), (size, stream) -> stream.readNBytes(6)));
		}
		Files.delete(first); Files.delete(second);
		var values = localView(inputs, LIMITS);
		assertEquals(List.of(new Id(0, 0, NAME), new Id(0, 1, "empty"), new Id(1, 0, NAME),
				new Id(2, 0, NAME), new Id(2, 1, "empty")), values.stream().map(Value::id).toList());
		assertArrayEquals(FIRST, values.get(0).bytes());
		assertArrayEquals(SECOND, values.get(2).bytes());
		assertArrayEquals(FIRST, values.get(3).bytes());
		assertEquals(0, values.get(1).bytes().length);
		assertEquals("0477f75981429fe533b2b25953e9546fd5a2984148900221c217a637c89597ea", values.getFirst().sha256());
		Arrays.fill(inputs.getFirst().bytes(), (byte) 99);
		values.getFirst().bytes()[0] = 42;
		assertArrayEquals(FIRST, values.getFirst().bytes());
		assertThrows(UnsupportedOperationException.class, values::clear);
		assertEquals(accepted, repository.acceptedInputs(), "Reads do not publish new accepted baselines");
	}

	@Test
	void encodedAdmissionAndAllSlotHashesPrecedeAnyZipParsing() throws Exception {
		byte[] bytes = archive(List.of(new Entry(NAME, FIRST)));
		var good = input(bytes);
		var exact = new Limits(2, bytes.length, 2 * bytes.length, 2, 2 * NAME.length(), 5, 10);
		assertEquals(2, localView(List.of(good, good), exact).size());
		assertRefused("SLOTS", List.of(good, good), new Limits(1, bytes.length, 2 * bytes.length, 2, 100, 5, 10));
		assertRefused("ARCHIVE_BYTES", List.of(good), new Limits(1, bytes.length - 1, 2 * bytes.length, 2, 100, 5, 10));
		assertRefused("ENCODED_TOTAL", List.of(good, good), new Limits(2, bytes.length, 2 * bytes.length - 1, 2, 100, 5, 10));
		byte[] corrupt = bytes.clone();
		buffer(corrupt).putShort(8, (short) 99); // Would fail if parsing started.
		assertRefused("ARCHIVE_BYTES", List.of(input(corrupt)), new Limits(1, corrupt.length - 1, 65536, 32, 4096, 8192, 16384));
		assertRefused("HASH", List.of(input(corrupt), new Input(bytes, "not-an-accepted-hash")), LIMITS);
		assertEquals(1, localView(List.of(good), LIMITS).size(), "Refusal publishes no partial list or retained parser");
	}

	@Test
	void localEntryCountNameAndExpandedCeilingsAreInclusiveAcrossRepeatedSlots() throws Exception {
		var inputs = List.of(input(archive(List.of(new Entry(NAME, FIRST), new Entry("empty", new byte[0])))));
		int chars = NAME.length() + 5;
		assertEquals(2, localView(inputs, new Limits(1, 32768, 65536, 2, chars, 5, 5)).size());
		assertRefused("ENTRIES", inputs, new Limits(1, 32768, 65536, 1, chars, 5, 5));
		assertRefused("NAME_CHARS", inputs, new Limits(1, 32768, 65536, 2, chars - 1, 5, 5));
		assertRefused("ENTRY_BYTES", inputs, new Limits(1, 32768, 65536, 2, chars, 4, 5));
		assertRefused("TOTAL_BYTES", inputs, new Limits(1, 32768, 65536, 2, chars, 5, 4));
		assertRefused("TOTAL_BYTES", List.of(inputs.getFirst(), inputs.getFirst()),
				new Limits(2, 32768, 65536, 4, 2 * chars, 5, 9));
		byte[] dishonest = inputs.getFirst().bytes().clone();
		buffer(dishonest).putInt(22, Integer.MAX_VALUE);
		assertRefused("ENTRY_BYTES", List.of(input(dishonest)), LIMITS);
	}

	@Test
	void ambiguousNamesDirectoriesAndDeflateAreRefusedNotSilentlyFiltered() throws Exception {
		byte[] duplicate = archive(List.of(new Entry(NAME, FIRST), new Entry("assets/next.bin", SECOND)));
		int cd = central(duplicate);
		int secondLocal = 30 + NAME.length() + FIRST.length;
		int secondCentral = cd + 46 + NAME.length();
		byte[] name = NAME.getBytes(StandardCharsets.UTF_8);
		System.arraycopy(name, 0, duplicate, secondLocal + 30, name.length);
		System.arraycopy(name, 0, duplicate, secondCentral + 46, name.length);
		assertRefused("DUPLICATE_NAME", List.of(input(duplicate)), LIMITS);
		for (String unsafe : List.of("../outside", "/absolute", "assets/")) {
			assertRefused("NAME_OR_DIRECTORY", List.of(input(archive(List.of(new Entry(unsafe, FIRST))))), LIMITS);
		}
		var output = new ByteArrayOutputStream();
		try (var zip = new ZipOutputStream(output)) {
			zip.putNextEntry(new ZipEntry(NAME));
			zip.write(new byte[16384]); // Unknown local size, data descriptor, high expansion.
			zip.closeEntry();
		}
		assertRefused("METHOD", List.of(input(output.toByteArray())), LIMITS);
		assertFalse(Files.exists(dir.resolve("outside")), "The experiment never extracts entries");
	}

	@Test
	void compiledApkBytesAndNestedZipStayRawWithoutInvokingBinaryDecoders() throws Exception {
		byte[] fixture = Files.readAllBytes(Path.of("tests/fixtures/resources/resources.apk"));
		assertEquals("595459df2f52b61d96023c84e3dec69d4d961fa40f21d0cf7a22cbd0a8821b5e", hash(fixture));
		var values = localView(List.of(input(fixture)), LIMITS);
		assertEquals(Set.of("AndroidManifest.xml", "resources.arsc", "res/xml/settings.xml", "assets/note.txt"),
				values.stream().map(v -> v.id().name()).collect(java.util.stream.Collectors.toSet()));
		Path apk = Files.write(dir.resolve("fixture.apk"), fixture);
		try (var zip = new ZipReader(Set.of(ZipReaderFlags.DONT_USE_FALLBACK)).open(apk.toFile())) {
			assertEquals(4, zip.getEntries().size());
			for (var entry : zip.getEntries()) {
				var value = values.stream().filter(v -> v.id().name().equals(entry.getName())).findFirst().orElseThrow();
				assertArrayEquals(entry.getBytes(), value.bytes());
			}
		}
		assertArrayEquals(new byte[] {3, 0, 8, 0}, Arrays.copyOf(values.getFirst().bytes(), 4));
		byte[] nested = archive(List.of(new Entry("inside.bin", SECOND)));
		var outer = localView(List.of(input(archive(List.of(new Entry("assets/nested.zip", nested))))), LIMITS);
		assertEquals(1, outer.size());
		assertArrayEquals(nested, outer.getFirst().bytes());
	}

	@Test
	void storeCrcAndTruncatedPayloadFailRatherThanReturningPartialSuccess() throws Exception {
		byte[] good = archive(List.of(new Entry(NAME, FIRST)));
		byte[] corrupt = good.clone(); corrupt[30 + NAME.length()] ^= 1;
		assertThrows(ZipException.class, () -> localView(List.of(input(good), input(corrupt)), LIMITS));
		byte[] truncated = Arrays.copyOf(good, 30 + NAME.length() + FIRST.length - 1);
		assertThrows(ZipException.class, () -> localView(List.of(input(truncated)), LIMITS));
		assertArrayEquals(FIRST, localView(List.of(input(good)), LIMITS).getFirst().bytes());
	}

	@Test
	void retainedCounterexampleLocalStreamAcceptsMissingCentralDirectoryAsHealthyContent() throws Exception {
		byte[] good = archive(List.of(new Entry(NAME, FIRST)));
		byte[] noCentral = Arrays.copyOf(good, central(good));
		assertArrayEquals(FIRST, localView(List.of(input(noCentral)), LIMITS).getFirst().bytes());
		Path broken = Files.write(dir.resolve("missing-central.zip"), noCentral);
		assertThrows(ZipException.class, () -> { try (var zip = new ZipFile(broken.toFile())) { zip.size(); } });
		assertTrue(localView(List.of(input(new byte[] {1, 2, 3})), LIMITS).isEmpty(),
				"Non-ZIP bytes also look like an empty local stream, not a valid empty archive");
		// Even a valid first record followed by a partial next local header is silently accepted.
		byte[] partial = Arrays.copyOf(noCentral, noCentral.length + 10);
		buffer(partial).putInt(noCentral.length, 0x04034b50);
		assertEquals(1, localView(List.of(input(partial)), LIMITS).size());
	}

	@Test
	void retainedCounterexampleCentralNameAndInventoryCanDisagreeWithLocalStream() throws Exception {
		byte[] bytes = archive(List.of(new Entry(NAME, FIRST)));
		int cd = central(bytes);
		byte[] other = "assets/next.bin".getBytes(StandardCharsets.UTF_8);
		System.arraycopy(other, 0, bytes, cd + 46, other.length);
		assertEquals(NAME, localView(List.of(input(bytes)), LIMITS).getFirst().id().name());
		Path mismatch = Files.write(dir.resolve("name-mismatch.apk"), bytes);
		try (var zip = new ZipFile(mismatch.toFile())) {
			assertEquals("assets/next.bin", zip.entries().nextElement().getName());
		}
		try (var zip = new ZipReader(Set.of(ZipReaderFlags.REPORT_TAMPERING, ZipReaderFlags.DONT_USE_FALLBACK)).open(mismatch.toFile())) {
			assertEquals(NAME, zip.getEntries().getFirst().getName(), "REPORT_TAMPERING is not a strict name validator");
		}
		// Central directory declares no entries, but the unchanged local stream still emits one.
		buffer(bytes).putShort(bytes.length - 22 + 8, (short) 0).putShort(bytes.length - 22 + 10, (short) 0);
		Files.write(mismatch, bytes);
		assertEquals(1, localView(List.of(input(bytes)), LIMITS).size());
		try (var zip = new ZipReader(Set.of(ZipReaderFlags.DONT_USE_FALLBACK)).open(mismatch.toFile())) {
			assertTrue(zip.getEntries().isEmpty());
		}
	}

	private record Input(byte[] bytes, String acceptedHash) { }
	private record Id(int slot, int ordinal, String name) { }
	private record Value(Id id, String sha256, String base64) {
		byte[] bytes() { return Base64.getDecoder().decode(base64); }
	}
	private record Limits(int slots, int archiveBytes, int encodedTotal, int entries, int nameChars,
			int entryBytes, int totalBytes) { }
	private static final class Refused extends RuntimeException {
		Refused(String reason) { super(reason); }
	}

	/** Test-only STORE local-record observation. NOT an admitted complete archive result.
	 * All raw bytes are supplied by the fixture; no filesystem opener/fallback/decoder exists here. */
	private static List<Value> localView(List<Input> inputs, Limits limits) throws Exception {
		if (inputs.size() > limits.slots()) throw new Refused("SLOTS");
		long encoded = 0;
		for (var input : inputs) {
			if (input.bytes().length > limits.archiveBytes()) throw new Refused("ARCHIVE_BYTES");
			if ((encoded += input.bytes().length) > limits.encodedTotal()) throw new Refused("ENCODED_TOTAL");
		}
		var owned = new ArrayList<byte[]>();
		for (var input : inputs) {
			byte[] copy = input.bytes().clone();
			if (!("sha256:" + hash(copy)).equals(input.acceptedHash())) throw new Refused("HASH");
			owned.add(copy);
		}
		var values = new ArrayList<Value>();
		var security = new JadxZipSecurity();
		long chars = 0, total = 0;
		for (int slot = 0; slot < owned.size(); slot++) {
			var names = new HashSet<String>();
			try (var zip = new ZipInputStream(new ByteArrayInputStream(owned.get(slot)))) {
				ZipEntry entry;
				int ordinal = 0;
				while ((entry = zip.getNextEntry()) != null) {
					if (values.size() >= limits.entries()) throw new Refused("ENTRIES");
					String name = entry.getName();
					if ((chars += name.length()) > limits.nameChars()) throw new Refused("NAME_CHARS");
					if (!names.add(name)) throw new Refused("DUPLICATE_NAME");
					if (entry.isDirectory() || !security.isValidEntryName(name)) throw new Refused("NAME_OR_DIRECTORY");
					if (entry.getMethod() != ZipEntry.STORED) throw new Refused("METHOD");
					long size = entry.getSize();
					if (size < 0 || size > limits.entryBytes()) throw new Refused("ENTRY_BYTES");
					if (size > limits.totalBytes() - total) throw new Refused("TOTAL_BYTES");
					int max = (int) Math.min(limits.entryBytes(), limits.totalBytes() - total);
					byte[] content = zip.readNBytes(max + 1);
					if (content.length > max || content.length != size) throw new Refused("ACTUAL_BYTES");
					total += content.length;
					values.add(new Value(new Id(slot, ordinal++, name), hash(content), Base64.getEncoder().encodeToString(content)));
				}
			}
		}
		return List.copyOf(values);
	}

	private static void assertRefused(String reason, List<Input> inputs, Limits limits) {
		assertEquals(reason, assertThrows(Refused.class, () -> localView(inputs, limits)).getMessage());
	}
	private static Input input(byte[] bytes) throws Exception { return new Input(bytes, "sha256:" + hash(bytes)); }
	private static String hash(byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}
	private record Entry(String name, byte[] bytes) { }
	private static byte[] archive(List<Entry> entries) throws IOException {
		var output = new ByteArrayOutputStream();
		try (var zip = new ZipOutputStream(output)) {
			for (var item : entries) {
				var entry = new ZipEntry(item.name());
				entry.setMethod(ZipEntry.STORED); entry.setSize(item.bytes().length);
				var crc = new CRC32(); crc.update(item.bytes()); entry.setCrc(crc.getValue());
				zip.putNextEntry(entry); zip.write(item.bytes()); zip.closeEntry();
			}
		}
		return output.toByteArray();
	}
	private static ByteBuffer buffer(byte[] bytes) { return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN); }
	private static int central(byte[] bytes) { return buffer(bytes).getInt(bytes.length - 22 + 16); }
}
