package dev.libjadx.probes;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import dev.libjadx.app.EditBatchService;
import dev.libjadx.app.ProjectRuntime;
import dev.libjadx.core.RevisionState;
import dev.libjadx.core.edits.EditDtos;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.project.NativeProjectRepository;
import dev.libjadx.scheduler.ProjectBusyException;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.ResourceFile;
import jadx.api.ResourcesLoader;
import jadx.core.utils.exceptions.JadxException;
import jadx.zip.ZipReader;
import jadx.zip.ZipReaderFlags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Test-only gate. Copy ceilings apply AFTER Jadx enumeration, not before ZIP/parser allocation.
 * Neither these probes nor the candidate establish hostile-filesystem containment. */
class RawResourceIdentityProbeTest {

	@TempDir Path dir;
	private static final String NAME = "assets/item.bin";
	private static final byte[] FIRST = {0, (byte) 0xff, 3, 8, 13};
	private static final byte[] SECOND = {9, 6, 2, (byte) 0x80, 1};
	private static final Limits LIMITS = new Limits(4, 32, 4096, 8192, 16384);

	@Test
	void explicitSlotsPreserveRepeatedInputsAndNamesWithoutUsingAliasesAsIdentity() throws Exception {
		Path one = zip("one.apk", List.of(new Entry(NAME, FIRST), new Entry("assets/empty", new byte[0])));
		Path two = zip("two.apk", List.of(new Entry(NAME, SECOND)));
		List<Copied> copies;
		ResourceFile borrowed;
		try (var jadx = new JadxDecompiler()) {
			var slots = slots(jadx, List.of(one, two, one));
			borrowed = slots.getFirst().getFirst();
			slots.getFirst().getFirst().setDeobfName("display-only.bin");
			copies = copy(slots, LIMITS);
			assertEquals(List.of(new Id(0, 0, NAME), new Id(0, 1, "assets/empty"), new Id(1, 0, NAME),
					new Id(2, 0, NAME), new Id(2, 1, "assets/empty")), copies.stream().map(Copied::id).toList());
			assertArrayEquals(FIRST, copies.get(0).bytes());
			assertArrayEquals(SECOND, copies.get(2).bytes());
			assertArrayEquals(FIRST, copies.get(3).bytes());
			// Independent oracle: printf '\000\377\003\010\015' | sha256sum.
			assertEquals("0477f75981429fe533b2b25953e9546fd5a2984148900221c217a637c89597ea", copies.getFirst().sha256());
			assertEquals(0, copies.get(1).bytes().length);
		}
		assertThrows(IllegalStateException.class, () -> raw(borrowed), "A ResourceFile is borrowed, not copied ownership");
		Files.delete(one); Files.delete(two);
		assertArrayEquals(FIRST, copies.getFirst().bytes(), "Copied values outlive parser/files");
		copies.getFirst().bytes()[0] = 99;
		assertArrayEquals(FIRST, copies.getFirst().bytes(), "No mutable byte array escapes");
		assertThrows(UnsupportedOperationException.class, () -> copies.clear());
	}

	@Test
	void duplicateNamesRetainOccurrencesButFallbackDoesNotRetainOccurrencePayloads() throws Exception {
		Path duplicate = duplicates();
		// Independent local-record oracle, not the name-based ZIP lookup under investigation.
		assertArrayEquals(FIRST, storedPayload(Files.readAllBytes(duplicate), 0));
		assertArrayEquals(SECOND, storedPayload(Files.readAllBytes(duplicate), 1));
		try (var normal = new ZipReader().open(duplicate.toFile());
				var fallback = new ZipReader(Set.of(ZipReaderFlags.FALLBACK_AS_DEFAULT)).open(duplicate.toFile())) {
			assertEquals(2, normal.getEntries().size());
			assertEquals(List.of(NAME, NAME), normal.getEntries().stream().map(e -> e.getName()).toList());
			try (var first = normal.getEntries().getFirst().getInputStream();
					var second = normal.getEntries().getLast().getInputStream()) {
				assertArrayEquals(FIRST, first.readNBytes(6));
				assertArrayEquals(SECOND, second.readNBytes(6));
			}
			assertSame(normal.getEntries().getLast(), normal.searchEntry(NAME), "Name map drops the first occurrence");
			assertEquals(2, fallback.getEntries().size());
			// Retained OpenJDK-21 counterexample: both enumerated entries read the last payload.
			try (var first = fallback.getEntries().getFirst().getInputStream();
					var second = fallback.getEntries().getLast().getInputStream()) {
				assertArrayEquals(SECOND, first.readNBytes(6));
				assertArrayEquals(SECOND, second.readNBytes(6));
			}
		}
		try (var jadx = new JadxDecompiler()) {
			var slots = slots(jadx, List.of(duplicate));
			assertEquals(2, slots.getFirst().size());
			assertEquals("DUPLICATE_NAME", assertThrows(Refused.class, () -> copy(slots, LIMITS)).getMessage());
		}
	}

	@Test
	void nestedArchiveIsAnOpaqueLeafAndFilteredInventoryIsNotPhysicalCompleteness() throws Exception {
		Path nested = zip("inner.zip", List.of(new Entry("inside.txt", FIRST)));
		byte[] nestedBytes = Files.readAllBytes(nested);
		Path outer = zip("outer.apk", List.of(new Entry("assets/", new byte[0]),
				new Entry("assets/inner.zip", nestedBytes), new Entry("../outside.bin", SECOND),
				new Entry("assets/Ž🚀.bin", FIRST)));
		try (var physical = new ZipFile(outer.toFile()); var jadx = new JadxDecompiler()) {
			assertEquals(4, physical.size());
			var copied = copy(slots(jadx, List.of(outer)), LIMITS);
			// ZIP strings are UTF-8, but Jadx also parses them as host filesystem paths.
			boolean hostCanEncode = java.nio.charset.Charset.forName(System.getProperty("sun.jnu.encoding"))
					.newEncoder().canEncode("assets/Ž🚀.bin");
			assertEquals(hostCanEncode ? List.of("assets/inner.zip", "assets/Ž🚀.bin") : List.of("assets/inner.zip"),
					copied.stream().map(c -> c.id().name()).toList());
			System.out.println("RESOURCE_NAME_ENCODING " + System.getProperty("sun.jnu.encoding") + " acceptedUnicode=" + hostCanEncode);
			assertArrayEquals(nestedBytes, copied.getFirst().bytes());
			assertFalse(Files.exists(dir.resolve("outside.bin")), "No extraction/export");
		}
		// A real loaded directory is omitted, not recursively inventoried as resources.
		try (var jadx = open(List.of(dir))) { assertTrue(jadx.getResources().isEmpty()); }
	}

	@Test
	void smallArchiveAndStandaloneHaveDifferentFreshnessAtTheSamePublishedRevision() throws Exception {
		Path dex = HierarchyFixture.dex(dir.resolve("dex"), "classes.dex",
				HierarchyFixture.smali("Lraw/Owner;", "Ljava/lang/Object;", "public", "value"));
		Path archive = zip("runtime.apk", List.of(new Entry("classes.dex", Files.readAllBytes(dex)), new Entry(NAME, FIRST)));
		Path standalone = Files.write(dir.resolve("standalone.bin"), FIRST);
		byte[] acceptedArchive = Files.readAllBytes(archive);
		try (var runtime = new ProjectRuntime(null, List.of(archive, standalone), List.of(dir))) {
			runtime.initializeAsync(null).get(30, TimeUnit.SECONDS);
			assertEquals("APPLIED", new EditBatchService(runtime).apply(new EditDtos.Request(null, null, List.of(
					new EditDtos.Operation(EditDtos.Kind.RENAME, SymbolRef.classRef("Lraw/Owner;"),
							"PendingOwner", null, null)))).outcome());
			var before = observe(runtime);
			Path replacement = zip("replacement.apk", List.of(new Entry("classes.dex", Files.readAllBytes(dex)), new Entry(NAME, SECOND)));
			Files.move(replacement, archive, StandardCopyOption.REPLACE_EXISTING);
			Files.write(standalone, SECOND);
			var changed = observe(runtime);
			assertEquals(before.revisions().sessionId(), changed.revisions().sessionId());
			assertEquals(before.revisions().logicalRevision(), changed.revisions().logicalRevision());
			assertEquals(before.epoch(), changed.epoch());
			assertEquals(before.settings(), changed.settings());
			assertArrayEquals(FIRST, named(changed.entries(), NAME).bytes(), "Small ZIP retained accepted bytes");
			assertArrayEquals(SECOND, named(changed.entries(), standalone.toString()).bytes(), "Standalone reopened new bytes");
			assertTrue(runtime.projectSnapshot().dirty(), "Read preserves pending native edits");
			Files.write(archive, acceptedArchive); Files.write(standalone, FIRST);
			assertEquals(before.entries(), observe(runtime).entries(), "Restoration/healthy lease control");
			Files.delete(archive); Files.delete(standalone);
			assertThrows(IllegalStateException.class, () -> observe(runtime));
			runtime.withPrimarySymbolRead("raw-deleted", read -> {
				var resources = read.decompiler().getResources();
				assertArrayEquals(FIRST, raw(resource(resources, NAME)));
				assertInstanceOf(JadxException.class, assertThrows(IllegalStateException.class,
						() -> raw(resource(resources, standalone.toString()))).getCause());
				return null;
			});
			Files.write(archive, acceptedArchive); Files.write(standalone, FIRST);
			assertEquals(before.entries(), observe(runtime).entries());
			assertEquals("raw.PendingOwner", runtime.withPrimarySymbolRead("pending-alias", read ->
					read.decompiler().searchJavaClassByOrigFullName("raw.Owner").getFullName()));
		}
		assertArrayEquals(acceptedArchive, Files.readAllBytes(archive));
		assertFalse(Files.exists(dir.resolve("runtime.apk.jadx")));
	}

	@Test
	void acceptedRepositoryBaselineDetectsChangesButDoesNotMakeResourceHandlesAnOwnedSnapshot() throws Exception {
		Path archive = zip("baseline.apk", List.of(new Entry(NAME, FIRST)));
		byte[] accepted = Files.readAllBytes(archive);
		var repository = NativeProjectRepository.fromInputs(List.of(archive, archive), List.of(dir));
		var metadata = repository.acceptedInputs();
		try (var jadx = new JadxDecompiler()) {
			var resources = slots(jadx, List.of(archive));
			repository.checkAnalysisBaselines();
			Path replacement = zip("new.apk", List.of(new Entry(NAME, SECOND)));
			Files.move(replacement, archive, StandardCopyOption.REPLACE_EXISTING);
			assertThrows(NativeProjectRepository.ExternalModificationException.class, repository::checkAnalysisBaselines);
			assertEquals(metadata, repository.acceptedInputs(), "A rejected read must not accept a new baseline");
			assertArrayEquals(FIRST, copy(resources, LIMITS).getFirst().bytes());
			Files.write(archive, accepted);
			repository.checkAnalysisBaselines();
			assertArrayEquals(FIRST, copy(resources, LIMITS).getFirst().bytes());
		}
	}

	@Test
	void inclusiveCopyCeilingsRejectTheFirstExcessWithoutReturningPartialResults() throws Exception {
		Path archive = zip("bounds.apk", List.of(new Entry(NAME, FIRST), new Entry("assets/empty", new byte[0])));
		try (var jadx = new JadxDecompiler()) {
			var slots = slots(jadx, List.of(archive, archive));
			int chars = 2 * (NAME.length() + "assets/empty".length());
			var exact = new Limits(2, 4, chars, 5, 10);
			assertEquals(4, copy(slots, exact).size());
			assertRefused("SLOTS", slots, new Limits(1, 4, chars, 5, 10));
			assertRefused("ENTRIES", slots, new Limits(2, 3, chars, 5, 10));
			assertRefused("NAME_CHARS", slots, new Limits(2, 4, chars - 1, 5, 10));
			assertRefused("ENTRY_BYTES", slots, new Limits(2, 4, chars, 4, 10));
			assertRefused("TOTAL_BYTES", slots, new Limits(2, 4, chars, 5, 9));
			assertEquals(4, copy(slots, exact).size(), "Refusals leave the real reader usable");
		}
	}

	@Test
	void boundedStreamUsesActualBytesAndRefusesTruncationAndUnderreportedSize() throws Exception {
		assertArrayEquals(FIRST, bounded(new ByteArrayInputStream(FIRST), 5, 5));
		assertArrayEquals(new byte[0], bounded(new ByteArrayInputStream(new byte[0]), 0, 0));
		assertEquals("SIZE_MISMATCH", assertThrows(Refused.class,
				() -> bounded(new ByteArrayInputStream(FIRST), 6, 6)).getMessage());
		assertEquals("SIZE_MISMATCH", assertThrows(Refused.class,
				() -> bounded(new ByteArrayInputStream(FIRST), 4, 5)).getMessage());
		assertEquals("ENTRY_BYTES", assertThrows(Refused.class,
				() -> bounded(new ByteArrayInputStream(FIRST), 4, 4)).getMessage());
	}

	@Test
	void realMalformedDeflateFailsAtomicallyThenHealthyEntryStillReads() throws Exception {
		Path archive = zip("broken.zip", List.of(new Entry("broken.bin", FIRST), new Entry(NAME, SECOND)));
		byte[] bytes = Files.readAllBytes(archive);
		// Owned STORE payload is not a DEFLATE stream. Change the first local and CD method only.
		var buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		buffer.putShort(8, (short) 8);
		int cd = centralDirectory(bytes);
		buffer.putShort(cd + 10, (short) 8);
		Files.write(archive, bytes);
		try (var jadx = new JadxDecompiler()) {
			var resources = slots(jadx, List.of(archive));
			assertInstanceOf(JadxException.class, assertThrows(IllegalStateException.class,
					() -> copy(resources, LIMITS)).getCause());
			assertArrayEquals(SECOND, raw(resource(resources.getFirst(), NAME)));
		}
	}

	private static Observed observe(ProjectRuntime runtime) {
		return runtime.withPrimarySymbolRead("raw-resources", read -> {
			assertThrows(ProjectBusyException.class, () -> runtime.withPrimarySymbolRead("conflict", other -> null));
			// One flattened comparison group, NOT a claim to recover original input-slot identity.
			return new Observed(read.revisions(), read.publicationEpoch(), read.settings().fingerprint(),
					copy(List.of(read.decompiler().getResources()), LIMITS));
		});
	}

	private record Observed(RevisionState revisions, long epoch, String settings, List<Copied> entries) { }
	private record Id(int slot, int ordinal, String name) { }
	private record Copied(Id id, String sha256, String base64) {
		byte[] bytes() { return Base64.getDecoder().decode(base64); }
	}
	private record Limits(int slots, int entries, int nameChars, int entryBytes, int totalBytes) { }
	private static final class Refused extends RuntimeException {
		Refused(String reason) { super(reason); }
	}

	/** Experimental copy admission only: callers supply explicit groups (per-input for the
	 * identity probe). No slot recovery from a primary list or pre-parser allocation bound. */
	private static List<Copied> copy(List<List<ResourceFile>> slots, Limits limits) {
		if (slots.size() > limits.slots()) throw new Refused("SLOTS");
		long count = 0, chars = 0, total = 0;
		for (var resources : slots) {
			var names = new HashSet<String>();
			for (var resource : resources) {
				if (++count > limits.entries()) throw new Refused("ENTRIES");
				String name = resource.getOriginalName();
				if ((chars += name.length()) > limits.nameChars()) throw new Refused("NAME_CHARS");
				if (!names.add(name)) throw new Refused("DUPLICATE_NAME");
				// Standalone size is only observed inside decodeStream, not descriptor-bound here.
				if (resource.getZipEntry() != null) {
					long size = resource.getZipEntry().getUncompressedSize();
					if (size < 0 || size > limits.entryBytes()) throw new Refused("ENTRY_BYTES");
					if ((total += size) > limits.totalBytes()) throw new Refused("TOTAL_BYTES");
				}
			}
		}
		var copied = new ArrayList<Copied>();
		long actual = 0;
		for (int slot = 0; slot < slots.size(); slot++) {
			var resources = slots.get(slot);
			for (int ordinal = 0; ordinal < resources.size(); ordinal++) {
				var resource = resources.get(ordinal);
				int max = (int) Math.min(limits.entryBytes(), limits.totalBytes() - actual);
				try {
					byte[] bytes = ResourcesLoader.decodeStream(resource, (size, stream) -> bounded(stream, size, max));
					actual += bytes.length;
					String hash = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
					copied.add(new Copied(new Id(slot, ordinal, resource.getOriginalName()), hash, Base64.getEncoder().encodeToString(bytes)));
				} catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
				catch (JadxException failure) { throw new IllegalStateException(failure); }
			}
		}
		return List.copyOf(copied);
	}

	private static byte[] bounded(InputStream stream, long declared, int max) throws IOException {
		if (declared < 0 || declared > max) throw new Refused("ENTRY_BYTES");
		byte[] bytes = stream.readNBytes(max + 1);
		if (bytes.length > max) throw new Refused("ENTRY_BYTES");
		if (bytes.length != declared) throw new Refused("SIZE_MISMATCH");
		return bytes;
	}

	private static void assertRefused(String reason, List<List<ResourceFile>> slots, Limits limits) {
		assertEquals(reason, assertThrows(Refused.class, () -> copy(slots, limits)).getMessage());
	}
	private static Copied named(List<Copied> entries, String name) {
		return entries.stream().filter(e -> e.id().name().equals(name)).findFirst().orElseThrow();
	}
	private static ResourceFile resource(List<ResourceFile> resources, String name) {
		return resources.stream().filter(r -> r.getOriginalName().equals(name)).findFirst().orElseThrow();
	}
	private static byte[] raw(ResourceFile resource) {
		try { return ResourcesLoader.decodeStream(resource, (size, stream) -> bounded(stream, size, 8192)); }
		catch (JadxException failure) { throw new IllegalStateException(failure); }
	}
	private static List<List<ResourceFile>> slots(JadxDecompiler jadx, List<Path> paths) {
		var slots = new ArrayList<List<ResourceFile>>();
		for (Path path : paths) {
			var resources = new ArrayList<ResourceFile>();
			jadx.getResourcesLoader().defaultLoadFile(resources, path.toFile(), "");
			slots.add(List.copyOf(resources));
		}
		return List.copyOf(slots);
	}
	private static JadxDecompiler open(List<Path> paths) {
		var args = new JadxArgs(); args.setInputFiles(paths.stream().map(Path::toFile).toList());
		var jadx = new JadxDecompiler(args);
		try { jadx.load(); return jadx; }
		catch (RuntimeException | Error failure) { jadx.close(); throw failure; }
	}

	private record Entry(String name, byte[] bytes) { }
	private Path zip(String name, List<Entry> entries) throws IOException {
		Path path = dir.resolve(name);
		try (var zip = new ZipOutputStream(Files.newOutputStream(path))) {
			for (var item : entries) {
				var entry = new ZipEntry(item.name());
				entry.setMethod(ZipEntry.STORED); entry.setSize(item.bytes().length);
				var crc = new CRC32(); crc.update(item.bytes()); entry.setCrc(crc.getValue());
				zip.putNextEntry(entry); zip.write(item.bytes()); zip.closeEntry();
			}
		}
		return path;
	}
	private Path duplicates() throws IOException {
		String placeholder = "assets/next.bin";
		Path path = zip("duplicate.zip", List.of(new Entry(NAME, FIRST), new Entry(placeholder, SECOND)));
		byte[] bytes = Files.readAllBytes(path), from = placeholder.getBytes(StandardCharsets.UTF_8), to = NAME.getBytes(StandardCharsets.UTF_8);
		assertEquals(from.length, to.length);
		int changed = 0;
		for (int i = 0; i <= bytes.length - from.length; i++) {
			if (java.util.Arrays.equals(bytes, i, i + from.length, from, 0, from.length)) {
				System.arraycopy(to, 0, bytes, i, to.length); changed++;
			}
		}
		assertEquals(2, changed, "Local and central name only; payloads and CRCs untouched");
		return Files.write(path, bytes);
	}
	private static byte[] storedPayload(byte[] bytes, int ordinal) {
		var buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		int start = 0;
		for (int i = 0; ; i++) {
			assertEquals(0x04034b50, buffer.getInt(start));
			assertEquals(0, buffer.getShort(start + 8));
			int size = buffer.getInt(start + 18);
			int data = start + 30 + Short.toUnsignedInt(buffer.getShort(start + 26)) + Short.toUnsignedInt(buffer.getShort(start + 28));
			if (i == ordinal) return java.util.Arrays.copyOfRange(bytes, data, data + size);
			start = data + size;
		}
	}
	private static int centralDirectory(byte[] bytes) {
		var buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		return buffer.getInt(bytes.length - 22 + 16); // These owned ZIPs have no EOCD comment.
	}
}
