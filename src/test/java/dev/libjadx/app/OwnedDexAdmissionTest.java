package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;
import static dev.libjadx.app.NormalizedCfgIsolationTest.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributeView;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import dev.libjadx.core.EffectiveAnalysisConfig;
import dev.libjadx.core.RevisionState;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.jadxadapter.JadxEngineFactory;
import dev.libjadx.jadxadapter.JadxInputCensusAdapter;
import dev.libjadx.jadxadapter.JadxSymbolAdapter;
import dev.libjadx.probes.HierarchyFixture;
import dev.libjadx.project.FileFingerprint;
import dev.libjadx.project.NativeProjectDocument;
import dev.libjadx.scheduler.ProjectBusyException;
import jadx.api.JadxDecompiler;
import jadx.api.data.impl.JadxCodeComment;
import jadx.api.data.impl.JadxCodeData;
import jadx.api.data.impl.JadxCodeRef;
import jadx.api.data.impl.JadxCodeRename;
import jadx.api.data.impl.JadxNodeRef;
import jadx.api.plugins.input.data.IClassData;
import jadx.api.plugins.input.data.IFieldData;
import jadx.api.plugins.input.data.IMethodData;
import jadx.api.plugins.input.data.ISeqConsumer;
import jadx.api.plugins.pass.JadxPassInfo;
import jadx.api.plugins.pass.impl.OrderedJadxPassInfo;
import jadx.api.plugins.pass.types.JadxPreparePass;
import jadx.core.dex.nodes.RootNode;
import jadx.core.dex.visitors.rename.RenameVisitor;
import jadx.plugins.input.dex.*;
import jadx.plugins.input.dex.insns.DexInsnData;
import jadx.plugins.input.dex.sections.DexCodeReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Test-only ownership/admission experiment. The production path and its counterexamples stay unchanged. */
class OwnedDexAdmissionTest {
    @TempDir Path dir;
    @TempDir Path outside;

    enum Quota { INPUTS, FILE_BYTES, TOTAL_BYTES, CLASSES, FIELDS, METHODS, UNITS, RECORDS, TRIES, HANDLERS, CASES, TEXT }

    record Budget(Map<Quota, Long> maxima) {
        Budget { maxima = Map.copyOf(maxima); }
        static Budget small() {
            return new Budget(Map.ofEntries(
                    Map.entry(Quota.INPUTS, 4L), Map.entry(Quota.FILE_BYTES, 2L << 20), Map.entry(Quota.TOTAL_BYTES, 4L << 20),
                    Map.entry(Quota.CLASSES, 128L), Map.entry(Quota.FIELDS, 2048L), Map.entry(Quota.METHODS, 2048L),
                    Map.entry(Quota.UNITS, 65536L), Map.entry(Quota.RECORDS, 32768L), Map.entry(Quota.TRIES, 1024L),
                    Map.entry(Quota.HANDLERS, 4096L), Map.entry(Quota.CASES, 4096L), Map.entry(Quota.TEXT, 262144L)));
        }
        Budget with(Quota key, long value) {
            var copy = new EnumMap<Quota, Long>(Quota.class); copy.putAll(maxima); copy.put(key, value);
            return new Budget(copy);
        }
    }

    static final class Refusal extends RuntimeException {
        Refusal(String reason) { super(reason); }
    }

    static final class Meter {
        final Budget budget;
        final EnumMap<Quota, Long> used = new EnumMap<>(Quota.class);
        Meter(Budget budget) { this.budget = budget; }
        void check(Quota key, long count) {
            if (count < 0 || count > budget.maxima().get(key)) throw new Refusal(key.name());
        }
        void add(Quota key, long count) {
            long before = used.getOrDefault(key, 0L);
            if (count < 0 || count > budget.maxima().get(key) - before) throw new Refusal(key.name());
            used.put(key, before + count);
        }
        void text(String value) { if (value != null) add(Quota.TEXT, value.length()); }
    }

    static final class Hooks {
        Consumer<Path> beforeOpen = ignored -> { };
        BiConsumer<Path, Long> read = (ignored, count) -> { };
        Runnable afterCapture = () -> { };
        Runnable beforeLoad = () -> { };
        Consumer<JadxDecompiler> configure = ignored -> { };
        Consumer<JadxDecompiler> loaded = ignored -> { };
        boolean failClassLoad;
    }

    /** No byte-array accessor: DexReader borrows these private arrays until engine close. */
    static final class Owned implements AutoCloseable {
        private final List<byte[]> bytes = new ArrayList<>();
        private final List<DexReader> readers = new ArrayList<>();
        final List<String> fingerprints = new ArrayList<>();
        final Meter meter;
        long referencedHandlers;

        private Owned(Budget budget) { meter = new Meter(budget); }

        static Owned acquire(Path root, List<Path> inputs, List<FileFingerprint> accepted, Budget budget, Hooks hooks) throws Exception {
            Owned owned = new Owned(budget);
            try {
                owned.meter.add(Quota.INPUTS, inputs.size());
                if (inputs.isEmpty() || inputs.size() != accepted.size()) throw new Refusal("Input baseline set");
                var options = new DexInputOptions(); options.setOptions(Map.of());
                // A bare uninitialized DexInputPlugin helper would silently omit the advertised checksum default.
                if (!options.isVerifyChecksum()) throw new AssertionError("Pinned checksum policy");
                var loader = new DexFileLoader(options);
                for (int i = 0; i < inputs.size(); i++) {
                    Path path = inputs.get(i);
                    if (!path.isAbsolute() || !path.normalize().equals(path) || !path.startsWith(root)
                            || !path.toRealPath().equals(path)) throw new SecurityException("Canonical allowed-root input required");
                    hooks.beforeOpen.accept(path);
                    byte[] content;
                    try (var channel = secureOpen(path)) {
                        owned.meter.check(Quota.FILE_BYTES, channel.size());
                        long count = 0;
                        var output = new ByteArrayOutputStream();
                        var chunk = ByteBuffer.allocate(256);
                        int read;
                        while ((read = channel.read(chunk)) != -1) {
                            owned.meter.check(Quota.FILE_BYTES, count + read);
                            owned.meter.add(Quota.TOTAL_BYTES, read);
                            output.write(chunk.array(), 0, read); count += read; chunk.clear();
                            hooks.read.accept(path, count);
                        }
                        content = output.toByteArray();
                    }
                    String hash = hash(content);
                    if (!new FileFingerprint(true, hash).equals(accepted.get(i))) throw new Refusal("Accepted content mismatch");
                    // First subset only: ordinary DEX 035–040, never a container, conversion or archive.
                    if (content.length < 112 || content[0] != 'd' || content[1] != 'e' || content[2] != 'x' || content[3] != '\n'
                            || content[7] != 0 || !Set.of("035", "037", "038", "039", "040")
                                    .contains(new String(content, 4, 3, java.nio.charset.StandardCharsets.US_ASCII)))
                        throw new Refusal("Unsupported plain DEX header");
                    owned.bytes.add(content); owned.fingerprints.add(hash);
                    var parsed = loader.loadDexReaders("owned-" + i + ".dex", content);
                    if (parsed.size() != 1) throw new Refusal("Sub-DEX container");
                    owned.readers.addAll(parsed);
                    owned.inventory(parsed.getFirst(), content);
                }
                hooks.afterCapture.run();
                return owned;
            } catch (Exception | Error failure) { owned.close(); throw failure; }
        }

        /** Open every directory component relative to a secured handle, with no symlink traversal. Linux proof only. */
        static SeekableByteChannel secureOpen(Path path) throws IOException {
            try (var stream = Files.newDirectoryStream(path.getRoot())) {
                if (!(stream instanceof SecureDirectoryStream<Path> secured)) throw new Refusal("SecureDirectoryStream unavailable");
                return descend(secured, path, 0);
            }
        }
        static SeekableByteChannel descend(SecureDirectoryStream<Path> directory, Path path, int at) throws IOException {
            Path name = path.getName(at);
            if (at < path.getNameCount() - 1) {
                try (var child = directory.newDirectoryStream(name, LinkOption.NOFOLLOW_LINKS)) {
                    return descend(child, path, at + 1);
                }
            }
            if (!directory.getFileAttributeView(name, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS)
                    .readAttributes().isRegularFile()) throw new SecurityException("Regular input required");
            return directory.newByteChannel(name, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
        }

        void inventory(DexReader reader, byte[] content) {
            long count = Integer.toUnsignedLong(reader.getHeader().getClassDefsSize());
            meter.add(Quota.CLASSES, count); // Before visitClasses allocates/decodes individual declarations.
            reader.visitClasses(cls -> {
                meter.text(cls.getType()); meter.text(cls.getSuperType()); cls.getInterfacesTypes().forEach(meter::text);
                cls.visitFieldsAndMethods(new ISeqConsumer<IFieldData>() {
                    @Override public void init(int count) { meter.add(Quota.FIELDS, count); }
                    @Override public void accept(IFieldData field) {
                        meter.text(field.getName()); meter.text(field.getType());
                    }
                }, new ISeqConsumer<IMethodData>() {
                    @Override public void init(int count) { meter.add(Quota.METHODS, count); }
                    @Override public void accept(IMethodData method) {
                        var ref = method.getMethodRef(); ref.load();
                        meter.text(ref.getName()); ref.getArgTypes().forEach(meter::text); meter.text(ref.getReturnType());
                        if (method.getCodeReader() == null) return;
                        if (!(method.getCodeReader() instanceof DexCodeReader code)) throw new Refusal("Non-DEX reader");
                        inventoryCode(code.copy(), content);
                    }
                });
            });
        }

        /** Count/skip raw records and handlers without upstream payload/handler array decoding. Not a DEX verifier. */
        void inventoryCode(DexCodeReader code, byte[] bytes) {
            ByteBuffer raw = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            int start = code.getCodeOffset();
            long units = Integer.toUnsignedLong(code.getUnitsCount());
            meter.check(Quota.UNITS, units); meter.add(Quota.UNITS, units);
            // Reuse the reader slice's per-method code-unit ceiling, independent of aggregate totals.
            if (units > 4096 || start < 0 || (long) start + 16 + units * 2 > bytes.length) throw new Refusal("Code extent");
            long tries = Short.toUnsignedLong(raw.getShort(start + 6)); meter.add(Quota.TRIES, tries);
            int[] next = {0};
            code.visitInstructions(value -> {
                DexInsnData insn = (DexInsnData) value;
                meter.add(Quota.RECORDS, 1);
                if (insn.getInsnInfo() == null || insn.getOffset() != next[0]) throw new Refusal("Malformed opcode");
                int offset = insn.getFileOffset(), op = insn.getRawOpcodeUnit();
                long length = insn.getLength();
                if (length < 0) {
                    if ((next[0] & 1) != 0 || units - next[0] < (op == 0x200 ? 2 : 4)) throw new Refusal("Payload header");
                    int size = Short.toUnsignedInt(raw.getShort(offset + 2));
                    if (op == 0x100 || op == 0x200) {
                        meter.add(Quota.CASES, size);
                        length = op == 0x100 ? 4L + size * 2L : 2L + size * 4L;
                    } else if (op == 0x300 && Set.of(1, 2, 4, 8).contains(size)) {
                        length = 4 + (Integer.toUnsignedLong(raw.getInt(offset + 4)) * size + 1) / 2;
                    } else throw new Refusal("Payload kind");
                } else if ((op & 255) == 0 && op != 0) throw new Refusal("Unknown pseudo-opcode");
                if (length <= 0 || length > units - next[0]) throw new Refusal("Instruction extent");
                code.skip(insn); // Extents/counts validated before invoking pinned skip, never decode().
                if (insn.getLength() != length) throw new Refusal("Skip extent mismatch");
                insn.setDecoded(true); next[0] += (int) length;
            });
            if (next[0] != units) throw new Refusal("Code end");
            if (tries == 0) return;
            int table = Math.toIntExact(start + 16L + units * 2 + (units & 1) * 2);
            int handlersStart = Math.toIntExact(table + tries * 8);
            if (handlersStart > bytes.length) throw new Refusal("Try extent");
            int[] pos = {handlersStart};
            long lists = leb(bytes, pos, false);
            meter.check(Quota.HANDLERS, lists);
            var sizes = new HashMap<Integer, Long>();
            for (long i = 0; i < lists; i++) {
                int relative = pos[0] - handlersStart;
                long signed = leb(bytes, pos, true), typed = Math.abs(signed), count = typed + (signed <= 0 ? 1 : 0);
                meter.add(Quota.HANDLERS, count); // Before iterating entries, no upstream arrays allocated.
                sizes.put(relative, count);
                for (long h = 0; h < typed; h++) { leb(bytes, pos, false); leb(bytes, pos, false); }
                if (signed <= 0) leb(bytes, pos, false);
            }
            for (int i = 0; i < tries; i++) {
                Long size = sizes.get(Short.toUnsignedInt(raw.getShort(table + i * 8 + 6)));
                if (size == null) throw new Refusal("Missing catch table");
                if (size > meter.budget.maxima().get(Quota.HANDLERS) - referencedHandlers) throw new Refusal("Referenced handlers");
                referencedHandlers += size;
            }
        }

        static long leb(byte[] bytes, int[] pos, boolean signed) {
            long value = 0; int shift = 0, b;
            do {
                if (pos[0] >= bytes.length || shift >= 35) throw new Refusal("Malformed LEB");
                b = bytes[pos[0]++] & 255; value |= (long) (b & 127) << shift; shift += 7;
            } while ((b & 128) != 0);
            if (signed && (b & 64) != 0) value |= -1L << shift;
            if (signed ? value < Integer.MIN_VALUE || value > Integer.MAX_VALUE : value > 0xffff_ffffL) throw new Refusal("LEB overflow");
            return value;
        }

        @Override public void close() { bytes.clear(); readers.clear(); }
    }

    /** A real temporary engine, substituted only by the existing test EngineFactory seam. */
    final class Harness {
        final List<Path> inputs;
        List<FileFingerprint> accepted;
        Budget budget = Budget.small();
        Hooks hooks = new Hooks();
        final AtomicInteger created = new AtomicInteger(), closed = new AtomicInteger(), loads = new AtomicInteger();
        final AtomicInteger loaderReleases = new AtomicInteger();
        Map<Quota, Long> lastCounts;
        List<String> lastFingerprints;
        final ThreadLocal<Boolean> temporary = ThreadLocal.withInitial(() -> false);

        Harness(List<Path> inputs) throws Exception { this.inputs = List.copyOf(inputs); accept(); }
        void accept() throws Exception { accepted = baselines(inputs); }

        ProjectRuntime open(Path project) throws Exception {
            var runtime = new ProjectRuntime(project, inputs, args -> {
                if (!temporary.get()) return new ProjectRuntime.JadxProjectEngine(args);
                created.incrementAndGet();
                List<Path> paths = args.getInputFiles().stream().map(java.io.File::toPath).toList();
                args.setInputFiles(List.of()); // Custom code loaders are additive: filesystem paths MUST be absent.
                return new ProjectRuntime.ProjectEngine() {
                    final JadxDecompiler jadx = new JadxDecompiler(args);
                    Owned owned;
                    @Override public void load() throws Exception {
                        if (args.getUserRenamesMappingsPath() != null || args.isUseDxInput()) throw new Refusal("Mapping/conversion outside subset");
                        owned = Owned.acquire(dir, paths, accepted, budget, hooks);
                        lastCounts = Map.copyOf(owned.meter.used); lastFingerprints = List.copyOf(owned.fingerprints);
                        AtomicBoolean released = new AtomicBoolean();
                        jadx.addCustomCodeLoader(new DexLoadResult(owned.readers, () -> {
                            if (released.compareAndSet(false, true)) loaderReleases.incrementAndGet();
                        }) {
                            @Override public void visitClasses(Consumer<IClassData> consumer) {
                                if (hooks.failClassLoad) throw new IllegalStateException("Injected class-load failure");
                                super.visitClasses(consumer);
                            }
                        });
                        hooks.beforeLoad.run();
                        hooks.configure.accept(jadx);
                        loads.incrementAndGet(); jadx.load();
                        hooks.loaded.accept(jadx);
                    }
                    @Override public JadxDecompiler decompiler() { return jadx; }
                    // Deliberately no path-based census/bind: this prototype does not authorize propagated edits.
                    @Override public void close() {
                        try { jadx.close(); }
                        finally { if (owned != null) owned.close(); closed.incrementAndGet(); }
                    }
                };
            });
            try { runtime.initializeAsync(project == null ? null : NativeProjectDocument.open(project)).get(30, TimeUnit.SECONDS); return runtime; }
            catch (Exception failure) { runtime.close(); throw failure; }
        }

        Bound capture(ProjectRuntime runtime, SymbolRef selected) throws Exception { return capture(runtime, selected, AUTO); }
        Bound capture(ProjectRuntime runtime, SymbolRef selected, EffectiveAnalysisConfig mode) throws Exception {
            return temporary(runtime, mode, context -> {
                assertTrue(context.decompiler().getArgs().getInputFiles().isEmpty());
                var observation = new Capture(selected).run(context.decompiler());
                return bind(context, selected, observation);
            }).value();
        }
        <T> ProjectRuntime.TemporaryResult<T> temporary(ProjectRuntime runtime, EffectiveAnalysisConfig mode,
                java.util.function.Function<ProjectRuntime.TemporarySourceRead, T> operation) throws Exception {
            temporary.set(true);
            try { return runtime.withTemporarySourceRead(mode, operation); }
            finally { temporary.remove(); }
        }
        Bound bind(ProjectRuntime.TemporarySourceRead context, SymbolRef selected, Observation observation) {
            try {
                return new Bound(selected, "JADX_1.5.6_28ff15e4_POST_BLOCK_FINISHER", context.revisions(), context.publicationEpoch(),
                        context.settings().fingerprint(), List.copyOf(lastFingerprints), observation,
                        observation.graph() == null ? null : hash(JSON.writeValueAsBytes(observation.graph())));
            } catch (Exception failure) { throw new AssertionError(failure); }
        }
        void balanced() { assertEquals(created.get(), closed.get(), "Every owned temporary engine closes"); }
    }

    record Bound(SymbolRef method, String representation, RevisionState revisions, long publicationEpoch,
            String settings, List<String> consumed, Observation observation, String graphContent) { }

    static String hash(byte[] bytes) throws Exception {
        return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
    static List<FileFingerprint> baselines(List<Path> paths) throws Exception {
        var result = new ArrayList<FileFingerprint>();
        for (Path p : paths) result.add(FileFingerprint.of(p));
        return List.copyOf(result);
    }
    Path fixture() throws Exception {
        return HierarchyFixture.dex(dir, "cfg.dex", Files.readString(Path.of("tests/fixtures/cfg/CfgCases.smali")));
    }

    @Test void ownedBytesNotPathsDriveRealGraphsAfterReplacementDeletionAndClose() throws Exception {
        Path dex = fixture(); byte[] original = Files.readAllBytes(dex);
        Path other = HierarchyFixture.dex(dir, "second.dex", ".class public Lprobe/Second;\n.super Ljava/lang/Object;\n");
        byte[] second = Files.readAllBytes(other);
        Path opposite = HierarchyFixture.dex(outside, "opposite.dex", Files.readString(Path.of("tests/fixtures/cfg/CfgCases.smali"))
                .replace("if-lez p0, :end", "if-gtz p0, :end"));
        var h = new Harness(List.of(dex, other)); Bound retained;
        try (var runtime = h.open(null)) {
            var before = runtime.projectSnapshot(); var search = runtime.searchIdentity();
            var primaryState = method(runtime.decompiler(), ref("branchLoop")).getParentClass().getState();
            h.hooks.afterCapture = () -> {
                try { Files.delete(dex); Files.createSymbolicLink(dex, opposite); Files.delete(other); }
                catch (IOException failure) { throw new RuntimeException(failure); }
            };
            try {
                retained = h.capture(runtime, ref("branchLoop"));
                loopBranches(retained.observation().graph(), "LE");
                assertEquals(List.of(hash(original), hash(second)), retained.consumed());
                assertEquals(before, runtime.projectSnapshot()); assertEquals(search, runtime.searchIdentity());
                assertEquals(primaryState, method(runtime.decompiler(), ref("branchLoop")).getParentClass().getState());
            } finally { Files.deleteIfExists(dex); Files.write(dex, original); Files.write(other, second); h.hooks = new Hooks(); }
            assertEquals(retained.graphContent(), h.capture(runtime, ref("branchLoop")).graphContent());
            assertThrows(UnsupportedOperationException.class, () -> retained.consumed().clear());
            assertThrows(UnsupportedOperationException.class, () -> retained.observation().graph().nodes().clear());
        }
        loopBranches(retained.observation().graph(), "LE"); h.balanced();
        assertEquals(h.loads.get(), h.loaderReleases.get());
    }

    @Test void secureAcquisitionRejectsChangesGrowthAndFinalOrParentSymlinkRaces() throws Exception {
        Path nested = Files.createDirectories(dir.resolve("nested"));
        Path input = HierarchyFixture.dex(nested, "input.dex", ".class public Lprobe/Small;\n.super Ljava/lang/Object;\n");
        byte[] accepted = Files.readAllBytes(input); var baseline = baselines(List.of(input));
        Path target = Files.write(outside.resolve("input.dex"), accepted);
        for (String change : List.of("replace", "delete", "symlink", "parentSymlink", "growth", "inPlace")) {
            var hooks = new Hooks(); AtomicInteger reads = new AtomicInteger();
            hooks.read = (p, count) -> {
                reads.incrementAndGet();
                if (change.equals("growth") || change.equals("inPlace")) {
                    try {
                        if (change.equals("growth")) Files.write(p, new byte[257], StandardOpenOption.APPEND);
                        else { byte[] changed = accepted.clone(); changed[changed.length - 1] ^= 1; Files.write(p, changed); }
                    } catch (IOException e) { throw new RuntimeException(e); }
                }
            };
            hooks.beforeOpen = p -> {
                try {
                    switch (change) {
                        case "replace" -> { byte[] changed = accepted.clone(); changed[20] ^= 1; Files.write(p, changed); }
                        case "delete" -> Files.delete(p);
                        case "symlink" -> { Files.delete(p); Files.createSymbolicLink(p, target); }
                        case "parentSymlink" -> { Files.move(nested, dir.resolve("held")); Files.createSymbolicLink(nested, outside); }
                    }
                } catch (IOException e) { throw new RuntimeException(e); }
            };
            try {
                assertThrows(Exception.class, () -> Owned.acquire(dir, List.of(input), baseline,
                        Budget.small().with(Quota.FILE_BYTES, accepted.length), hooks), change);
                if (Set.of("symlink", "parentSymlink", "delete").contains(change)) assertEquals(0, reads.get(), change);
            } finally {
                if (change.equals("parentSymlink")) { Files.delete(nested); Files.move(dir.resolve("held"), nested); }
                Files.deleteIfExists(input); Files.write(input, accepted);
            }
        }
        try (var owned = Owned.acquire(dir, List.of(input), baseline, Budget.small(), new Hooks())) {
            assertEquals(List.of(hash(accepted)), owned.fingerprints);
        }
    }

    @Test void aggregateCountsHaveIndependentInclusiveBoundariesBeforeJadxLoad() throws Exception {
        Path dex = fixture(); var baseline = baselines(List.of(dex));
        // Manually counted from CfgCases.smali; RECORDS includes alignment nops and three payload records.
        var expected = new EnumMap<Quota, Long>(Quota.class);
        expected.putAll(Map.of(Quota.INPUTS, 1L, Quota.FILE_BYTES, Files.size(dex), Quota.TOTAL_BYTES, Files.size(dex),
                Quota.CLASSES, 1L, Quota.METHODS, 8L, Quota.UNITS, 103L, Quota.RECORDS, 55L,
                Quota.TRIES, 1L, Quota.HANDLERS, 2L, Quota.CASES, 4L));
        // Class/super descriptors, method names, arguments and returns, not decoded annotation/debug text.
        long text = "Lprobe/CfgCases;".length() + "Ljava/lang/Object;".length();
        for (String name : List.of("absent", "straight", "jump", "branchLoop", "packed", "sparse", "array", "catches")) text += name.length() + 1;
        text += 3 + "Ljava/lang/RuntimeException;".length() + "[I".length();
        expected.put(Quota.TEXT, text);
        try (var full = Owned.acquire(dir, List.of(dex), baseline, Budget.small(), new Hooks())) {
            for (var entry : expected.entrySet()) if (entry.getKey() != Quota.FILE_BYTES)
                assertEquals(entry.getValue(), full.meter.used.get(entry.getKey()), entry.getKey().name());
        }
        for (var entry : expected.entrySet()) {
            try (var exact = Owned.acquire(dir, List.of(dex), baseline, Budget.small().with(entry.getKey(), entry.getValue()), new Hooks())) {
                assertEquals(1, exact.readers.size());
            }
            Refusal failed = assertThrows(Refusal.class, () -> Owned.acquire(dir, List.of(dex), baseline,
                    Budget.small().with(entry.getKey(), entry.getValue() - 1), new Hooks()));
            assertEquals(entry.getKey().name(), failed.getMessage());
        }
        var h = new Harness(List.of(dex)); h.budget = Budget.small().with(Quota.RECORDS, 54);
        try (var runtime = h.open(null)) {
            assertThrows(Refusal.class, () -> h.capture(runtime, ref("branchLoop")));
            assertEquals(0, h.loads.get(), "Refusal precedes even normal load/preparation, not just graph passes");
            h.budget = Budget.small(); assertEquals("AVAILABLE", h.capture(runtime, ref("branchLoop")).observation().availability());
        }
        h.balanced();
    }

    @Test void tinySelectionCannotBypassSiblingDependencyOrDuplicateAdmission() throws Exception {
        Path dep = HierarchyFixture.dex(dir, "dep.dex", ".class public Lprobe/Dependency;\n.super Ljava/lang/Object;\n" + large("heavy"));
        Path owner = HierarchyFixture.dex(dir, "owner.dex", ".class public Lprobe/Cost;\n.super Lprobe/Dependency;\n"
                + ".method public static tiny()I\n.registers 1\nconst/4 v0, 3\nreturn v0\n.end method\n" + large("zzLarge"));
        var selected = new SymbolRef(SymbolRef.Kind.METHOD, "Lprobe/Cost;", null, "tiny", "()I");
        var h = new Harness(List.of(owner, dep)); h.budget = Budget.small().with(Quota.UNITS, 7205);
        try (var runtime = h.open(null)) {
            assertThrows(Refusal.class, () -> h.capture(runtime, selected)); assertEquals(0, h.loads.get());
            h.budget = Budget.small().with(Quota.UNITS, 7206);
            assertEquals("AVAILABLE", h.capture(runtime, selected).observation().availability());
            assertEquals(7206L, h.lastCounts.get(Quota.UNITS)); assertEquals(3606L, h.lastCounts.get(Quota.RECORDS));
            assertEquals(3L, h.lastCounts.get(Quota.METHODS)); assertEquals(2L, h.lastCounts.get(Quota.CLASSES));
        }
        h.balanced();
        String duplicate = ".class public Lprobe/Duplicate;\n.super Ljava/lang/Object;\n.field public static flag:I\n"
                + ".method public static synthetic bridge hidden()I\n.registers 1\nconst/4 v0, 3\nreturn v0\n.end method\n";
        Path a = HierarchyFixture.dex(dir, "a.dex", duplicate), b = HierarchyFixture.dex(dir, "b.dex", duplicate.replace("v0, 3", "v0, 5"));
        List<Path> inputs = List.of(a, b, a); var accepted = baselines(inputs);
        try (var all = Owned.acquire(dir, inputs, accepted, Budget.small(), new Hooks())) {
            assertEquals(3L, all.meter.used.get(Quota.CLASSES)); assertEquals(3L, all.meter.used.get(Quota.METHODS));
            assertEquals(3L, all.meter.used.get(Quota.FIELDS)); assertEquals(6L, all.meter.used.get(Quota.UNITS));
            assertEquals(List.of(hash(Files.readAllBytes(a)), hash(Files.readAllBytes(b)), hash(Files.readAllBytes(a))), all.fingerprints);
        }
        assertThrows(Refusal.class, () -> Owned.acquire(dir, inputs, accepted, Budget.small().with(Quota.METHODS, 2), new Hooks()));
        assertThrows(Refusal.class, () -> Owned.acquire(dir, inputs, accepted, Budget.small().with(Quota.FIELDS, 2), new Hooks()));
    }

    @Test void unsupportedHeadersChecksumAndOversizedPayloadsRefuseBeforeLoad() throws Exception {
        Path input = fixture(); byte[] original = Files.readAllBytes(input);
        var codeOffsets = new HashMap<String, Integer>();
        try (var admitted = Owned.acquire(dir, List.of(input), baselines(List.of(input)), Budget.small(), new Hooks())) {
            admitted.readers.getFirst().visitClasses(cls -> cls.visitFieldsAndMethods(field -> { }, method -> {
                if (method.getCodeReader() != null) {
                    method.getMethodRef().load();
                    codeOffsets.put(method.getMethodRef().getName(), ((DexCodeReader) method.getCodeReader()).getCodeOffset());
                }
            }));
        }
        for (String invalid : List.of("archive", "class", "container", "checksum", "payload", "handlers")) {
            byte[] bytes = original.clone(); var raw = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            switch (invalid) {
                case "archive" -> raw.putInt(0, 0x04034b50);
                case "class" -> raw.putInt(0, 0xbebafeca);
                case "container" -> bytes[6] = '1';
                case "checksum" -> bytes[20] ^= 1;
                // Array payload starts at code unit 10; its unsigned count must not reach upstream allocation.
                case "payload" -> raw.putInt(codeOffsets.get("array") + 16 + 10 * 2 + 4, -1);
                case "handlers" -> {
                    int code = codeOffsets.get("catches"), units = raw.getInt(code + 12);
                    int handlers = code + 16 + units * 2 + (units & 1) * 2 + 8;
                    bytes[handlers + 1] = 63; // Declares 63 typed handlers in the tiny original table.
                }
            }
            if (invalid.equals("payload") || invalid.equals("handlers")) {
                var checksum = new java.util.zip.Adler32(); checksum.update(bytes, 12, bytes.length - 12);
                raw.putInt(8, (int) checksum.getValue());
            }
            Files.write(input, bytes);
            try {
                var accepted = baselines(List.of(input));
                if (invalid.equals("checksum")) assertThrows(DexException.class,
                        () -> Owned.acquire(dir, List.of(input), accepted, Budget.small(), new Hooks()));
                else {
                    var failure = assertThrows(Refusal.class, () -> Owned.acquire(dir, List.of(input), accepted,
                            Budget.small().with(Quota.HANDLERS, 2), new Hooks()));
                    assertEquals(switch (invalid) {
                        case "payload" -> "Instruction extent";
                        case "handlers" -> "HANDLERS";
                        default -> "Unsupported plain DEX header";
                    }, failure.getMessage(), invalid);
                }
            } finally { Files.write(input, original); }
        }
    }

    static JadxCodeData edits(String alias, String comment) {
        var data = new JadxCodeData();
        var nativeRef = new JadxNodeRef(jadx.api.data.IJavaNodeRef.RefType.METHOD, "probe.CfgCases", "branchLoop(I)I");
        data.setRenames(List.of(new JadxCodeRename(nativeRef, alias))); data.setComments(List.of(new JadxCodeComment(nativeRef, comment)));
        return data;
    }

    @Test void inputlessLoadingSkipsDeclarationRenamesDespiteCopiedUnsavedDataAndComments() throws Exception {
        Path dex = fixture(), project = dir.resolve("project.jadx"); NativeProjectDocument.newFromInputs(project, List.of(dex)).save();
        var nativeHash = FileFingerprint.of(project); var inputHash = FileFingerprint.of(dex);
        var h = new Harness(List.of(dex));
        try (var runtime = h.open(project)) {
            runtime.replaceCodeData(edits("oldLoop", "old unsaved comment"), 0);
            var before = runtime.projectSnapshot(); var primaryData = runtime.decompiler().getArgs().getCodeData();
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            var pending = CompletableFuture.supplyAsync(() -> {
                try {
                    return h.temporary(runtime, new EffectiveAnalysisConfig("RESTRUCTURE"), context -> {
                        assertNotSame(primaryData, context.decompiler().getArgs().getCodeData());
                        assertNotSame(primaryData.getRenames().getFirst(), context.decompiler().getArgs().getCodeData().getRenames().getFirst());
                        assertEquals("oldLoop", context.decompiler().getArgs().getCodeData().getRenames().getFirst().getNewName());
                        entered.countDown(); await(release);
                        var observation = new Capture(ref("branchLoop")).run(context.decompiler());
                        String java = JadxSymbolAdapter.visibleClass(context.decompiler(), OWNER, 0).getCode();
                        // COUNTEREXAMPLE: RenameVisitor.init returns early for empty inputFiles.
                        // Do not manually apply UserRenames or add dummy filesystem inputs to conceal it.
                        assertTrue(java.contains("int branchLoop("), java);
                        assertFalse(java.contains("oldLoop"), java);
                        assertTrue(java.contains("old unsaved comment"), java);
                        assertFalse(java.contains("newLoop"), java);
                        return h.bind(context, ref("branchLoop"), observation);
                    }).value();
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            try {
                assertTrue(entered.await(30, TimeUnit.SECONDS));
                assertThrows(ProjectBusyException.class, () -> h.capture(runtime, ref("straight")));
                for (var policy : ShutdownPolicy.values()) assertThrows(ProjectRuntime.ShutdownRejectedException.class, () -> runtime.requestShutdown(policy));
                runtime.withPrimarySymbolRead(OWNER, ctx -> { assertTrue(JadxSymbolAdapter.visibleClass(ctx.decompiler(), OWNER, 0).getCode().contains("oldLoop")); return null; });
                runtime.replaceCodeData(edits("newLoop", "new unsaved comment"), before.revisions().logicalRevision());
            } finally { release.countDown(); }
            var old = pending.get(30, TimeUnit.SECONDS); var next = h.capture(runtime, ref("branchLoop"));
            assertEquals(before.revisions(), old.revisions()); assertEquals("branchLoop", old.observation().alias());
            assertEquals(List.of("old unsaved comment"), old.observation().comments()); assertEquals("RESTRUCTURE", old.observation().mode());
            assertEquals("branchLoop", next.observation().alias()); assertEquals(List.of("new unsaved comment"), next.observation().comments());
            assertEquals("newLoop", method(runtime.decompiler(), ref("branchLoop")).getAlias(), "Path-based production control applies the rename");
            assertEquals(old.graphContent(), next.graphContent()); assertNotEquals(old.revisions(), next.revisions()); assertNotEquals(old.settings(), next.settings());
            assertEquals(nativeHash, FileFingerprint.of(project)); assertEquals(inputHash, FileFingerprint.of(dex)); assertTrue(runtime.projectSnapshot().dirty());
            System.out.println("BLOCKER: owned inputless engine retains oldLoop/newLoop records and comments, but emits branchLoop; path-based primary emits newLoop");
        }
        h.balanced();
    }

    /** Review candidate only: invoke the whole pinned native pipeline, never patch individual aliases.
     * The public prepare-pass hook preserves ordering, but access to process is version-sensitive.
     * Jadx logs/swallow prepare exceptions, so successful load alone is not initialization proof. */
    static final class NativeInitialization implements JadxPreparePass {
        final java.lang.reflect.Method process;
        final Runnable beforeInit;
        int completed;
        RuntimeException failure;

        NativeInitialization(JadxDecompiler jadx, Runnable beforeInit) throws Exception {
            if (!jadx.getArgs().getInputFiles().isEmpty()
                    || !Set.of("AUTO", "RESTRUCTURE").contains(jadx.getArgs().getDecompilationMode().name()))
                throw new Refusal("Inputless AUTO/RESTRUCTURE only");
            process = RenameVisitor.class.getDeclaredMethod("process", RootNode.class);
            process.setAccessible(true);
            this.beforeInit = beforeInit;
            jadx.addCustomPass(this);
        }
        @Override public JadxPassInfo getInfo() {
            return new OrderedJadxPassInfo("OwnedNativeInitialization", "Pinned inputless native initialization")
                    .after("RenameVisitor").before("SaveDeobfMapping");
        }
        @Override public void init(RootNode root) {
            try {
                beforeInit.run();
                if (completed != 0 || !root.getArgs().getInputFiles().isEmpty()) throw new Refusal("Initialization state");
                apply(root);
                root.registerCodeDataUpdateListener(ignored -> apply(root));
                completed++;
            } catch (RuntimeException caught) { failure = caught; throw caught; }
        }
        void apply(RootNode root) {
            try { process.invoke(new RenameVisitor(), root); }
            catch (ReflectiveOperationException caught) { throw new IllegalStateException("Native rename initialization failed", caught); }
        }
        void verify(JadxDecompiler jadx) {
            if (failure != null || completed != 1) throw new Refusal("Native initialization incomplete");
            var names = jadx.getRoot().getPreDecompilePasses().stream().map(p -> p.getName()).toList();
            int at = names.indexOf(getInfo().getName());
            if (at < 1 || !names.get(at - 1).equals("RenameVisitor")
                    || at + 1 >= names.size() || !names.get(at + 1).equals("SaveDeobfMapping"))
                throw new Refusal("Native initialization ordering");
        }
    }

    void initializeNatively(Harness h, Runnable beforeInit) {
        NativeInitialization[] initialization = {null};
        h.hooks.configure = jadx -> {
            try { initialization[0] = new NativeInitialization(jadx, beforeInit); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
        };
        h.hooks.loaded = jadx -> initialization[0].verify(jadx);
    }

    Path initializationFixture() throws Exception {
        return HierarchyFixture.dex(dir, "initialization.dex", """
                .class public Lprobe/Init;
                .super Ljava/lang/Object;
                .field public count:I
                .method public static calculate(IJ)J
                .registers 5
                int-to-long v0, p0
                add-long/2addr v0, p1
                return-wide v0
                .end method
                .method public static value()I
                .registers 1
                const/16 v0, 37
                return v0
                .end method
                .method public static value()Ljava/lang/String;
                .registers 1
                const-string v0, "different return"
                return-object v0
                .end method
                """);
    }

    static JadxCodeData initializationEdits(String prefix) {
        var data = new JadxCodeData();
        var cls = JadxNodeRef.forCls("probe.Init");
        var method = new JadxNodeRef(jadx.api.data.IJavaNodeRef.RefType.METHOD, "probe.Init", "calculate(IJ)J");
        var field = new JadxNodeRef(jadx.api.data.IJavaNodeRef.RefType.FIELD, "probe.Init", "count:I");
        data.setRenames(List.of(new JadxCodeRename(cls, prefix + "Class"), new JadxCodeRename(method, prefix + "Method"),
                new JadxCodeRename(field, prefix + "Field"), new JadxCodeRename(method, JadxCodeRef.forMthArg(0), prefix + "Narrow"),
                new JadxCodeRename(method, JadxCodeRef.forMthArg(1), prefix + "Wide"),
                new JadxCodeRename(new JadxNodeRef(jadx.api.data.IJavaNodeRef.RefType.METHOD, "probe.Init", "value()I"), prefix + "Value")));
        data.setComments(List.of(new JadxCodeComment(cls, prefix + " class comment"),
                new JadxCodeComment(method, prefix + " method comment"), new JadxCodeComment(field, prefix + " field comment")));
        return data;
    }

    static Map<String, String> initializationSemantics(JadxDecompiler jadx) {
        var result = new TreeMap<String, String>();
        var cls = jadx.searchJavaClassByOrigFullName("probe.Init");
        // Reader labels are deliberately descriptive, not original-input provenance. Normalize only this header.
        result.put("java", cls.getCode().replace("/* JADX INFO: loaded from: " + cls.getClassNode().getInputFileName() + " */",
                "/* JADX INFO: loaded from: <input-label> */"));
        result.put("class", cls.getFullName());
        for (var m : cls.getClassNode().getMethods()) result.put(m.getMethodInfo().getShortId(), m.getAlias());
        for (var f : cls.getClassNode().getFields()) result.put(f.getFieldInfo().getShortId(), f.getAlias());
        assertTrue(jadx.getRoot().getErrorsCounter().getErrorNodes().isEmpty());
        return Map.copyOf(result);
    }

    @ParameterizedTest @ValueSource(strings = {"AUTO", "RESTRUCTURE"})
    void nativeInitializationMatchesFreshPathsIncludingScopedEditsAndAutomaticAliases(String mode) throws Exception {
        Path dex = initializationFixture(); var h = new Harness(List.of(dex));
        var config = new EffectiveAnalysisConfig(mode); var data = initializationEdits("old");
        initializeNatively(h, () -> { });
        try (var runtime = h.open(null)) {
            var fingerprint = FileFingerprint.of(dex);
            for (var code : List.of(new JadxCodeData(), data)) {
                if (!code.getRenames().isEmpty()) runtime.replaceCodeData(code, runtime.projectSnapshot().revisions().logicalRevision());
                var before = runtime.projectSnapshot(); var search = runtime.searchIdentity();
                try (var fresh = new JadxDecompiler(JadxEngineFactory.arguments(List.of(dex), null, code, config))) {
                    fresh.load(); var expected = initializationSemantics(fresh);
                    // Empty data separately tests automatic return-only collision names, not just explicit renames.
                    if (code.getRenames().isEmpty()) assertNotEquals(expected.get("value()I"), expected.get("value()Ljava/lang/String;"));
                    else assertEquals("value", expected.get("value()Ljava/lang/String;"), "Unedited alias is recomputed after explicit rename");
                    h.temporary(runtime, config, context -> {
                        assertNotSame(runtime.decompiler().getArgs().getCodeData(), context.decompiler().getArgs().getCodeData());
                        assertEquals(expected, initializationSemantics(context.decompiler()));
                        return null;
                    });
                }
                assertEquals(before, runtime.projectSnapshot()); assertEquals(search, runtime.searchIdentity());
            }
            String java = h.temporary(runtime, config, ctx -> initializationSemantics(ctx.decompiler()).get("java")).value();
            for (String token : List.of("oldClass", "oldMethod", "oldField", "oldNarrow", "oldWide",
                    "old class comment", "old method comment", "old field comment")) assertTrue(java.contains(token), java);
            assertEquals(fingerprint, FileFingerprint.of(dex)); assertFalse(Files.exists(dir.resolve("initialization.dex.jadx")));
        }
        h.balanced();
    }

    @Test void initializedOwnedCfgReplaysDeclarationEditsWithoutChangingCopiedTopology() throws Exception {
        Path dex = fixture(); var h = new Harness(List.of(dex)); initializeNatively(h, () -> { });
        try (var runtime = h.open(null)) {
            var unedited = h.capture(runtime, ref("branchLoop"));
            runtime.replaceCodeData(edits("renamedLoop", "unsaved graph comment"), 0);
            var edited = h.capture(runtime, ref("branchLoop"));
            assertEquals("renamedLoop", edited.observation().alias());
            assertEquals(List.of("unsaved graph comment"), edited.observation().comments());
            assertEquals(unedited.graphContent(), edited.graphContent());
            loopBranches(edited.observation().graph(), "LE");
        }
        h.balanced();
    }

    @Test void initializedOwnedSnapshotKeepsOldEditsAndListenerReplaysNewDataWithoutPaths() throws Exception {
        Path dex = initializationFixture(); var h = new Harness(List.of(dex)); initializeNatively(h, () -> { });
        try (var runtime = h.open(null)) {
            runtime.replaceCodeData(initializationEdits("old"), 0); var before = runtime.projectSnapshot();
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            var pending = CompletableFuture.supplyAsync(() -> {
                try {
                    return h.temporary(runtime, AUTO, context -> {
                        entered.countDown(); await(release);
                        return initializationSemantics(context.decompiler());
                    });
                } catch (Exception failure) { throw new RuntimeException(failure); }
            });
            try {
                assertTrue(entered.await(30, TimeUnit.SECONDS));
                runtime.replaceCodeData(initializationEdits("new"), before.revisions().logicalRevision());
            } finally { release.countDown(); }
            var old = pending.get(30, TimeUnit.SECONDS);
            assertEquals(before.revisions(), old.revisions());
            assertEquals("probe.oldClass", old.value().get("class"));
            assertTrue(old.value().get("java").contains("oldWide")); assertFalse(old.value().get("java").contains("newWide"));
            h.temporary(runtime, AUTO, context -> {
                assertEquals("probe.newClass", initializationSemantics(context.decompiler()).get("class"));
                context.decompiler().getArgs().setCodeData(initializationEdits("listener"));
                context.decompiler().reloadCodeData();
                assertEquals("probe.listenerClass", context.decompiler().searchJavaClassByOrigFullName("probe.Init").getFullName());
                assertEquals("listenerMethod", context.decompiler().searchJavaClassByOrigFullName("probe.Init").getClassNode()
                        .searchMethodByShortId("calculate(IJ)J").getAlias());
                return null;
            });
            assertEquals("probe.newClass", runtime.decompiler().searchJavaClassByOrigFullName("probe.Init").getFullName());
        }
        h.balanced();
    }

    @Test void swallowedInitializationFailureOrSkippedPassRefusesAndReleasesTemporaryAdmission() throws Exception {
        Path dex = initializationFixture(); var h = new Harness(List.of(dex));
        try (var runtime = h.open(null)) {
            initializeNatively(h, () -> { throw new IllegalStateException("Injected native initialization failure"); });
            assertEquals("Native initialization incomplete", assertThrows(Refusal.class,
                    () -> h.temporary(runtime, AUTO, ctx -> fail("Must not publish incomplete initialization"))).getMessage());
            initializeNatively(h, () -> { });
            var configure = h.hooks.configure;
            h.hooks.configure = engine -> { configure.accept(engine); engine.getArgs().setDecompilationMode(jadx.api.DecompilationMode.SIMPLE); };
            assertThrows(Refusal.class, () -> h.temporary(runtime, AUTO, ctx -> fail("Skipped prepare pass must refuse")));
            initializeNatively(h, () -> { });
            assertEquals("probe.Init", h.temporary(runtime, AUTO, ctx -> initializationSemantics(ctx.decompiler()).get("class")).value());
        }
        h.balanced();
    }

    @Test void existingPathCensusBindingCannotCertifyAnInputlessOwnedEngine() throws Exception {
        Path dex = fixture(); var h = new Harness(List.of(dex));
        var census = JadxInputCensusAdapter.capture(List.of(dex), dev.libjadx.core.hierarchy.CensusLimits.defaults());
        assertEquals(dev.libjadx.core.hierarchy.RelatedHierarchyVerifier.Status.COMPLETE, census.status());
        try (var runtime = h.open(null)) {
            h.temporary(runtime, AUTO, ctx -> {
                var verification = census.bind(ctx.decompiler()).verify(ref("branchLoop"),
                        dev.libjadx.core.hierarchy.RelatedHierarchyVerifier.VerificationBudget.defaults());
                assertEquals(dev.libjadx.core.hierarchy.RelatedHierarchyVerifier.Status.UNSUPPORTED_INPUT, verification.status());
                return null;
            });
        }
        h.balanced();
    }

    @Test void bindingSeparatesSavePublicationContentReloadAndRestart() throws Exception {
        Path dex = fixture(), project = dir.resolve("project.jadx"); NativeProjectDocument.newFromInputs(project, List.of(dex)).save();
        var h = new Harness(List.of(dex)); Bound reloaded;
        try (var runtime = h.open(project)) {
            Bound initial = h.capture(runtime, ref("branchLoop")); runtime.saveProject(null, null);
            Bound saved = h.capture(runtime, ref("branchLoop"));
            assertEquals(initial.graphContent(), saved.graphContent()); assertEquals(initial.consumed(), saved.consumed());
            assertEquals(initial.revisions().logicalRevision(), saved.revisions().logicalRevision());
            assertTrue(saved.publicationEpoch() > initial.publicationEpoch()); assertNotEquals(initial, saved);
            byte[] changed = Files.readAllBytes(HierarchyFixture.dex(dir, "changed.dex", Files.readString(Path.of("tests/fixtures/cfg/CfgCases.smali"))
                    .replace("if-lez p0, :end", "if-gtz p0, :end"))); Files.write(dex, changed);
            assertThrows(Exception.class, () -> h.capture(runtime, ref("branchLoop")));
            var rev = runtime.projectSnapshot().revisions(); runtime.reloadProject(true, rev.sessionId(), rev.logicalRevision());
            // Test-supplied accepted hashes follow explicit reload; production baseline access remains a design proposal.
            h.accept(); reloaded = h.capture(runtime, ref("branchLoop")); loopBranches(reloaded.observation().graph(), "GT");
            assertNotEquals(saved.consumed(), reloaded.consumed()); assertNotEquals(saved.graphContent(), reloaded.graphContent());
        }
        h.balanced();
        var restarted = new Harness(List.of(dex));
        try (var runtime = restarted.open(project)) {
            Bound after = restarted.capture(runtime, ref("branchLoop"));
            assertEquals(reloaded.graphContent(), after.graphContent()); assertEquals(reloaded.consumed(), after.consumed());
            assertNotEquals(reloaded.revisions().sessionId(), after.revisions().sessionId());
        }
        restarted.balanced();
    }

    @Test void acquisitionAndLoadFailuresReleaseOwnershipAndMappingsRefuseWithoutFallback() throws Exception {
        Path dex = fixture(), secondary = HierarchyFixture.dex(dir, "secondary.dex", ".class public Lprobe/Secondary;\n.super Ljava/lang/Object;\n");
        Path project = dir.resolve("project.jadx"); NativeProjectDocument.newFromInputs(project, List.of(dex, secondary)).save();
        var h = new Harness(List.of(dex, secondary));
        try (var runtime = h.open(project)) {
            var before = runtime.projectSnapshot();
            for (Path changed : List.of(dex, secondary)) {
                byte[] accepted = Files.readAllBytes(changed);
                h.hooks.beforeOpen = p -> {
                    if (!p.equals(changed)) return;
                    try { byte[] change = accepted.clone(); change[20] ^= 1; Files.write(p, change); }
                    catch (IOException e) { throw new RuntimeException(e); }
                };
                try { assertThrows(Refusal.class, () -> h.capture(runtime, ref("straight"))); }
                finally { Files.write(changed, accepted); h.hooks = new Hooks(); }
                assertEquals(before, runtime.projectSnapshot()); assertEquals(0, h.loads.get()); h.balanced();
            }
            h.hooks.beforeLoad = () -> { throw new IllegalStateException("Injected pre-load failure"); };
            assertThrows(IllegalStateException.class, () -> h.capture(runtime, ref("straight"))); h.hooks = new Hooks(); h.balanced();
            h.hooks.failClassLoad = true;
            assertThrows(RuntimeException.class, () -> h.capture(runtime, ref("straight"))); h.hooks = new Hooks(); h.balanced();
            assertEquals(1, h.loads.get(), "Injected failure occurs inside real Jadx load");
            assertEquals("AVAILABLE", h.capture(runtime, ref("straight")).observation().availability());
            Path mappings = Files.writeString(dir.resolve("names.tiny"), mapping("Mapped"));
            var rev = runtime.projectSnapshot().revisions(); runtime.updateMappingsPath(mappings, rev.sessionId(), rev.logicalRevision());
            int loads = h.loads.get(); var mapped = runtime.projectSnapshot();
            assertThrows(Refusal.class, () -> h.capture(runtime, ref("straight"))); assertEquals(loads, h.loads.get());
            assertEquals(mapped, runtime.projectSnapshot()); h.balanced();
            rev = runtime.projectSnapshot().revisions(); runtime.updateMappingsPath(null, rev.sessionId(), rev.logicalRevision());
            assertEquals("AVAILABLE", h.capture(runtime, ref("straight")).observation().availability());
        }
        h.balanced();
        // A valid-but-failing method is not rejected by structural admission, nor reported as successful capture.
        Path broken = HierarchyFixture.dex(dir, "broken.dex", Files.readString(Path.of("tests/fixtures/smali/Disassembly.smali")));
        var brokenHarness = new Harness(List.of(broken));
        try (var runtime = brokenHarness.open(null)) {
            var ref = new SymbolRef(SymbolRef.Kind.METHOD, "Lprobe/Disassembly;", null, "broken", "()I");
            assertEquals("FAILED", brokenHarness.capture(runtime, ref).observation().availability());
            var healthy = new SymbolRef(SymbolRef.Kind.METHOD, "Lprobe/Disassembly;", null, "answer", "()I");
            assertEquals("AVAILABLE", brokenHarness.capture(runtime, healthy).observation().availability());
        }
        brokenHarness.balanced();
    }

    @Test void runtimeCloseDuringOwnedLoadingWaitsForLeaseAndCleansUp() throws Exception {
        var h = new Harness(List.of(fixture())); CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        var runtime = h.open(null); h.hooks.beforeLoad = () -> { entered.countDown(); await(release); };
        var pending = CompletableFuture.supplyAsync(() -> {
            try { return h.capture(runtime, ref("straight")); } catch (Exception e) { throw new RuntimeException(e); }
        });
        try {
            assertTrue(entered.await(30, TimeUnit.SECONDS)); CompletableFuture.runAsync(runtime::close).get(5, TimeUnit.SECONDS);
            assertEquals("SHUTTING_DOWN", runtime.status().state()); assertFalse(runtime.awaitStopped(20, TimeUnit.MILLISECONDS));
            assertThrows(IllegalStateException.class, () -> h.capture(runtime, ref("straight")));
        } finally { release.countDown(); }
        assertEquals("AVAILABLE", pending.get(30, TimeUnit.SECONDS).observation().availability());
        assertTrue(runtime.awaitStopped(5, TimeUnit.SECONDS)); h.balanced(); runtime.close();
    }
}
