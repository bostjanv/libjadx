package dev.libjadx.jadxadapter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import dev.libjadx.core.hierarchy.CensusLimits;
import dev.libjadx.core.hierarchy.InputCensus;
import dev.libjadx.core.hierarchy.InputCensus.InputOrigin;
import dev.libjadx.core.hierarchy.RelatedHierarchyVerifier.Status;
import dev.libjadx.project.FileFingerprint;
import dev.libjadx.project.NativeProjectRepository.AcceptedInputs;
import dev.libjadx.project.NativeProjectRepository.ExternalModificationException;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.plugins.input.data.IClassData;
import jadx.api.plugins.input.data.IFieldData;
import jadx.api.plugins.input.data.IMethodData;
import jadx.api.plugins.input.data.ISeqConsumer;
import jadx.api.plugins.pass.JadxPassInfo;
import jadx.api.plugins.pass.impl.OrderedJadxPassInfo;
import jadx.api.plugins.pass.types.JadxPreparePass;
import jadx.core.dex.nodes.RootNode;
import jadx.core.dex.visitors.rename.RenameVisitor;
import jadx.plugins.input.dex.DexFileLoader;
import jadx.plugins.input.dex.DexInputOptions;
import jadx.plugins.input.dex.DexLoadResult;
import jadx.plugins.input.dex.DexReader;
import jadx.plugins.input.dex.insns.DexInsnData;
import jadx.plugins.input.dex.sections.DexCodeReader;

/** Internal plain-DEX integration gate, not a public graph service or hostile-input sandbox.
 * Pinned to Jadx 1.5.6 / 28ff15e4ae69950aebea110a13e5ab895d234dfc.
 * Arrays/readers never escape; the census and engine consume the very same readers.
 * SecureDirectoryStream requires platform support; the containment gate retains outside-root
 * ancestor-relocation reads and blocking special-file opens. Structural admission is not a heap/time bound. */
public final class OwnedDexAnalysis implements AutoCloseable {
	// The preceding real-Jadx feasibility gate's inclusive ceilings, including raw duplicates.
	private enum Quota { INPUTS, FILE_BYTES, TOTAL_BYTES, CLASSES, FIELDS, METHODS, UNITS, RECORDS, TRIES, HANDLERS, CASES, TEXT }
	private static final Map<Quota, Long> LIMITS = Map.ofEntries(
			Map.entry(Quota.INPUTS, 4L), Map.entry(Quota.FILE_BYTES, 2L << 20), Map.entry(Quota.TOTAL_BYTES, 4L << 20),
			Map.entry(Quota.CLASSES, 128L), Map.entry(Quota.FIELDS, 2048L), Map.entry(Quota.METHODS, 2048L),
			Map.entry(Quota.UNITS, 65536L), Map.entry(Quota.RECORDS, 32768L), Map.entry(Quota.TRIES, 1024L),
			Map.entry(Quota.HANDLERS, 4096L), Map.entry(Quota.CASES, 4096L), Map.entry(Quota.TEXT, 262144L));
	private static final CensusLimits CENSUS_LIMITS = new CensusLimits(4, 128, 2048, 2048, 512, 4,
			1, 2 << 20, 4L << 20, 262144);
	private final AcceptedInputs accepted;
	private final List<byte[]> bytes = new ArrayList<>();
	private final List<DexReader> readers = new ArrayList<>();
	private final EnumMap<Quota, Long> used = new EnumMap<>(Quota.class);
	private InputCensus census;
	private JadxDecompiler decompiler;
	private long referencedHandlers;
	private boolean closed;
	private boolean ready;

	private OwnedDexAnalysis(AcceptedInputs accepted) { this.accepted = accepted; }

	public static OwnedDexAnalysis acquire(AcceptedInputs accepted) throws IOException {
		return acquire(accepted, (stage, path, count) -> { });
	}

	// Per-invocation timing seam for the containment gate; public acquisition uses a no-op.
	enum AcquisitionStage { BEFORE_DIRECTORY, DIRECTORY_OPENED, BEFORE_ATTRIBUTES, BEFORE_FILE_OPEN, FILE_OPENED, READ }
	@FunctionalInterface
	interface AcquisitionProbe { void at(AcquisitionStage stage, Path path, long count) throws IOException; }

	static OwnedDexAnalysis acquire(AcceptedInputs accepted, AcquisitionProbe probe) throws IOException {
		var owned = new OwnedDexAnalysis(accepted);
		try {
			if (accepted.mappingsAttached()) throw new IllegalArgumentException("Owned DEX analysis does not support attached mappings");
			owned.add(Quota.INPUTS, accepted.inputs().size());
			if (accepted.inputs().isEmpty()) throw new IllegalArgumentException("Owned inputs required");
			var options = new DexInputOptions(); options.setOptions(Map.of());
			if (!options.isVerifyChecksum()) throw new IllegalStateException("Pinned checksum policy changed");
			var loader = new DexFileLoader(options);
			var origins = new ArrayList<InputOrigin>();
			for (var input : accepted.inputs()) {
				Path path = input.path();
				if (!path.isAbsolute() || !path.normalize().equals(path)
						|| accepted.allowedRoots().stream().noneMatch(path::startsWith))
					throw new SecurityException("Canonical allowed-root input required");
				byte[] content;
				try {
					if (!path.toRealPath().equals(path)) throw new SecurityException("Input reference changed");
					try (var channel = secureOpen(path, probe)) {
						probe.at(AcquisitionStage.FILE_OPENED, path, 0);
						owned.check(Quota.FILE_BYTES, channel.size());
						long count = 0;
						var output = new ByteArrayOutputStream();
						var chunk = ByteBuffer.allocate(256);
						int read;
						while ((read = channel.read(chunk)) != -1) {
							interrupted();
							owned.check(Quota.FILE_BYTES, count + read); owned.add(Quota.TOTAL_BYTES, read);
							output.write(chunk.array(), 0, read); count += read; chunk.clear();
							probe.at(AcquisitionStage.READ, path, count);
						}
						content = output.toByteArray();
					}
				} catch (NoSuchFileException missing) {
					throw new ExternalModificationException("Input removed since accepted analysis; explicitly reload the project");
				}
				if (!input.fingerprint().equals(fingerprint(content)))
					throw new ExternalModificationException("Input changed since accepted analysis; explicitly reload the project");
				if (content.length < 112 || content[0] != 'd' || content[1] != 'e' || content[2] != 'x' || content[3] != '\n'
						|| content[7] != 0 || !Set.of("035", "037", "038", "039", "040")
								.contains(new String(content, 4, 3, java.nio.charset.StandardCharsets.US_ASCII)))
					throw new IllegalArgumentException("Only plain DEX 035–040 supported by owned analysis");
				owned.bytes.add(content);
				// Match direct-DEX loader labels for duplicate selection, without reopening these paths.
				var parsed = loader.loadDexReaders(path.toString(), content);
				if (parsed.size() != 1) throw new IllegalArgumentException("DEX containers unsupported");
				owned.readers.add(parsed.getFirst());
				owned.inventory(parsed.getFirst(), content);
				origins.add(new InputOrigin(origins.size(), path.toString()));
			}
			var capture = JadxInputCensusAdapter.captureOwned(owned.readers, origins,
					owned.bytes.stream().map(b -> b.length).toList(), CENSUS_LIMITS);
			if (capture.status() != Status.COMPLETE) throw new IllegalArgumentException("Owned census refused: " + capture.reason());
			owned.census = capture.census();
			return owned;
		} catch (IOException | RuntimeException | Error failure) { owned.close(); throw failure; }
	}

	/** A single load only. Callbacks must not save, reload, reconfigure or retain the borrowed engine. */
	public void load(JadxArgs args) throws ReflectiveOperationException {
		if (closed || decompiler != null) throw new IllegalStateException("Owned analysis is closed or already loaded");
		if (!args.getInputFiles().isEmpty() || args.getUserRenamesMappingsPath() != null || args.isUseDxInput()
				|| args.isDeobfuscationOn() || !Set.of("AUTO", "RESTRUCTURE").contains(args.getDecompilationMode().name()))
			throw new IllegalArgumentException("Inputless AUTO/RESTRUCTURE without mappings or conversion required");
		decompiler = new JadxDecompiler(args);
		var initialization = new NativeInitialization(decompiler);
		var consumed = new ArrayList<InputOrigin>();
		int[] count = {0};
		decompiler.addCustomCodeLoader(new DexLoadResult(readers, null) {
			@Override public void visitClasses(Consumer<IClassData> consumer) {
				if (!consumed.isEmpty()) throw new IllegalStateException("Owned loader consumed more than once");
				for (int i = 0; i < readers.size(); i++) {
					consumed.add(census.inputs().get(i));
					readers.get(i).visitClasses(cls -> { consumer.accept(cls); count[0]++; });
				}
			}
		});
		decompiler.load();
		initialization.verify(decompiler);
		if (!consumed.equals(census.inputs()) || count[0] != used.getOrDefault(Quota.CLASSES, 0L)
				|| decompiler.getErrorsCount() != 0 || !args.getInputFiles().isEmpty())
			throw new IllegalStateException("Incomplete owned class loading");
		for (int i = 0; i < bytes.size(); i++) {
			if (!fingerprint(bytes.get(i)).equals(accepted.inputs().get(i).fingerprint()))
				throw new IllegalStateException("Consumed owned content changed");
		}
		verifyLoadedDeclarations();
		ready = true;
	}

	private void verifyLoadedDeclarations() {
		var loaded = new HashSet<String>();
		for (var cls : decompiler.getRoot().getClasses()) {
			String descriptor = JadxSymbolAdapter.descriptor(cls.getRawName());
			var definitions = census.classesByDescriptor().get(descriptor);
			var methods = cls.getMethods().stream().map(m -> JadxSymbolAdapter.originalRef(m.getMethodInfo())).collect(java.util.stream.Collectors.toSet());
			if (!loaded.add(descriptor) || definitions == null || definitions.stream().noneMatch(record ->
					record.origin().canonicalPath().equals(cls.getInputFileName())
							&& record.methods().stream().map(InputCensus.MethodRecord::ref).collect(java.util.stream.Collectors.toSet()).equals(methods)))
				throw new IllegalStateException("Loaded declarations differ from owned census");
		}
		if (!loaded.equals(census.classesByDescriptor().keySet())) throw new IllegalStateException("Missing owned declarations");
	}

	public InputCensus census() { return census; }
	public JadxDecompiler decompiler() {
		if (closed || !ready) throw new IllegalStateException("No verified live owned engine");
		return decompiler;
	}

	private static FileFingerprint fingerprint(byte[] content) {
		try { return new FileFingerprint(true, "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content))); }
		catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
	}
	private static SeekableByteChannel secureOpen(Path path, AcquisitionProbe probe) throws IOException {
		try (var stream = Files.newDirectoryStream(path.getRoot())) {
			if (!(stream instanceof SecureDirectoryStream<Path> secured)) throw new SecurityException("SecureDirectoryStream unavailable");
			return descend(secured, path, 0, probe);
		}
	}
	private static SeekableByteChannel descend(SecureDirectoryStream<Path> directory, Path path, int at, AcquisitionProbe probe) throws IOException {
		Path name = path.getName(at);
		if (at < path.getNameCount() - 1) {
			Path component = path.getRoot().resolve(path.subpath(0, at + 1));
			probe.at(AcquisitionStage.BEFORE_DIRECTORY, component, 0);
			try (var child = directory.newDirectoryStream(name, LinkOption.NOFOLLOW_LINKS)) {
				probe.at(AcquisitionStage.DIRECTORY_OPENED, component, 0);
				return descend(child, path, at + 1, probe);
			}
		}
		probe.at(AcquisitionStage.BEFORE_ATTRIBUTES, path, 0);
		if (!directory.getFileAttributeView(name, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS).readAttributes().isRegularFile())
			throw new SecurityException("Regular input required");
		probe.at(AcquisitionStage.BEFORE_FILE_OPEN, path, 0);
		return directory.newByteChannel(name, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
	}
	private void check(Quota key, long count) {
		if (count < 0 || count > LIMITS.get(key)) throw new IllegalArgumentException("Owned admission limit: " + key);
	}
	private void add(Quota key, long count) {
		long before = used.getOrDefault(key, 0L);
		if (count < 0 || count > LIMITS.get(key) - before) throw new IllegalArgumentException("Owned admission limit: " + key);
		used.put(key, before + count);
	}
	private void text(String value) { if (value != null) add(Quota.TEXT, value.length()); }
	private static void interrupted() { if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Owned analysis interrupted cooperatively"); }

	private void inventory(DexReader reader, byte[] content) {
		add(Quota.CLASSES, Integer.toUnsignedLong(reader.getHeader().getClassDefsSize()));
		reader.visitClasses(cls -> {
			interrupted(); text(cls.getType()); text(cls.getSuperType()); cls.getInterfacesTypes().forEach(this::text);
			cls.visitFieldsAndMethods(new ISeqConsumer<IFieldData>() {
				@Override public void init(int count) { add(Quota.FIELDS, count); }
				@Override public void accept(IFieldData field) { text(field.getName()); text(field.getType()); }
			}, new ISeqConsumer<IMethodData>() {
				@Override public void init(int count) { add(Quota.METHODS, count); }
				@Override public void accept(IMethodData method) {
					var ref = method.getMethodRef(); ref.load();
					text(ref.getName()); ref.getArgTypes().forEach(OwnedDexAnalysis.this::text); text(ref.getReturnType());
					if (method.getCodeReader() == null) return;
					if (!(method.getCodeReader() instanceof DexCodeReader code)) throw new IllegalArgumentException("Non-DEX reader");
					inventoryCode(code.copy(), content);
				}
			});
		});
	}
	/** Raw inventory avoids unchecked upstream payload/handler array decoding; not full DEX validation. */
	private void inventoryCode(DexCodeReader code, byte[] bytes) {
		ByteBuffer raw = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
		int start = code.getCodeOffset();
		long units = Integer.toUnsignedLong(code.getUnitsCount()); add(Quota.UNITS, units);
		if (units > 4096 || start < 0 || (long) start + 16 + units * 2 > bytes.length) throw new IllegalArgumentException("Code extent");
		long tries = Short.toUnsignedLong(raw.getShort(start + 6)); add(Quota.TRIES, tries);
		int[] next = {0};
		code.visitInstructions(value -> {
			interrupted();
			DexInsnData insn = (DexInsnData) value; add(Quota.RECORDS, 1);
			if (insn.getInsnInfo() == null || insn.getOffset() != next[0]) throw new IllegalArgumentException("Malformed opcode");
			int offset = insn.getFileOffset(), op = insn.getRawOpcodeUnit();
			long length = insn.getLength();
			if (length < 0) {
				if ((next[0] & 1) != 0 || units - next[0] < (op == 0x200 ? 2 : 4)) throw new IllegalArgumentException("Payload header");
				int size = Short.toUnsignedInt(raw.getShort(offset + 2));
				if (op == 0x100 || op == 0x200) { add(Quota.CASES, size); length = op == 0x100 ? 4L + size * 2L : 2L + size * 4L; }
				else if (op == 0x300 && Set.of(1, 2, 4, 8).contains(size)) length = 4 + (Integer.toUnsignedLong(raw.getInt(offset + 4)) * size + 1) / 2;
				else throw new IllegalArgumentException("Payload kind");
			} else if ((op & 255) == 0 && op != 0) throw new IllegalArgumentException("Unknown pseudo-opcode");
			if (length <= 0 || length > units - next[0]) throw new IllegalArgumentException("Instruction extent");
			code.skip(insn);
			if (insn.getLength() != length) throw new IllegalArgumentException("Skip extent mismatch");
			insn.setDecoded(true); next[0] += (int) length;
		});
		if (next[0] != units) throw new IllegalArgumentException("Code end");
		if (tries == 0) return;
		int table = Math.toIntExact(start + 16L + units * 2 + (units & 1) * 2);
		int handlersStart = Math.toIntExact(table + tries * 8);
		if (handlersStart > bytes.length) throw new IllegalArgumentException("Try extent");
		int[] pos = {handlersStart}; long lists = leb(bytes, pos, false); check(Quota.HANDLERS, lists);
		var sizes = new HashMap<Integer, Long>();
		for (long i = 0; i < lists; i++) {
			int relative = pos[0] - handlersStart;
			long signed = leb(bytes, pos, true), typed = Math.abs(signed), count = typed + (signed <= 0 ? 1 : 0);
			add(Quota.HANDLERS, count); sizes.put(relative, count);
			for (long h = 0; h < typed; h++) { leb(bytes, pos, false); leb(bytes, pos, false); }
			if (signed <= 0) leb(bytes, pos, false);
		}
		for (int i = 0; i < tries; i++) {
			Long size = sizes.get(Short.toUnsignedInt(raw.getShort(table + i * 8 + 6)));
			if (size == null || size > LIMITS.get(Quota.HANDLERS) - referencedHandlers) throw new IllegalArgumentException("Referenced handlers");
			referencedHandlers += size;
		}
	}
	private static long leb(byte[] bytes, int[] pos, boolean signed) {
		long value = 0; int shift = 0, b;
		do {
			if (pos[0] >= bytes.length || shift >= 35) throw new IllegalArgumentException("Malformed LEB");
			b = bytes[pos[0]++] & 255; value |= (long) (b & 127) << shift; shift += 7;
		} while ((b & 128) != 0);
		if (signed && (b & 64) != 0) value |= -1L << shift;
		if (signed ? value < Integer.MIN_VALUE || value > Integer.MAX_VALUE : value > 0xffff_ffffL) throw new IllegalArgumentException("LEB overflow");
		return value;
	}

	/** Complete pinned native pipeline at the original boundary, with its update listener. */
	static final class NativeInitialization implements JadxPreparePass {
		private final java.lang.reflect.Method process;
		private int completed;
		private RuntimeException failure;
		NativeInitialization(JadxDecompiler jadx) throws ReflectiveOperationException {
			if (!JadxDecompiler.getVersion().equals("1.5.6")) throw new IllegalStateException("Owned initialization requires Jadx 1.5.6");
			process = RenameVisitor.class.getDeclaredMethod("process", RootNode.class);
			if (!process.trySetAccessible()) throw new IllegalStateException("Native rename initialization inaccessible");
			jadx.addCustomPass(this);
		}
		@Override public JadxPassInfo getInfo() {
			return new OrderedJadxPassInfo("OwnedNativeInitialization", "Pinned inputless native initialization")
					.after("RenameVisitor").before("SaveDeobfMapping");
		}
		@Override public void init(RootNode root) {
			try {
				if (completed != 0 || !root.getArgs().getInputFiles().isEmpty()) throw new IllegalStateException("Native initialization state");
				apply(root); root.registerCodeDataUpdateListener(ignored -> apply(root)); completed++;
			} catch (RuntimeException caught) { failure = caught; throw caught; }
		}
		private void apply(RootNode root) {
			try { process.invoke(new RenameVisitor(), root); }
			catch (ReflectiveOperationException caught) { throw new IllegalStateException("Native rename initialization failed", caught); }
		}
		void verify(JadxDecompiler jadx) {
			if (failure != null || completed != 1) throw new IllegalStateException("Native initialization incomplete", failure);
			var names = jadx.getRoot().getPreDecompilePasses().stream().map(p -> p.getName()).toList();
			int at = names.indexOf(getInfo().getName());
			if (at < 1 || !names.get(at - 1).equals("RenameVisitor") || at + 1 >= names.size() || !names.get(at + 1).equals("SaveDeobfMapping"))
				throw new IllegalStateException("Native initialization ordering");
		}
	}

	@Override public void close() {
		if (closed) return;
		closed = true;
		try { if (decompiler != null) decompiler.close(); }
		finally { readers.clear(); bytes.clear(); }
	}
}
