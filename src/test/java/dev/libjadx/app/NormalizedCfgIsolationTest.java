package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;
import static dev.libjadx.probes.JadxCfgProbeTest.graph;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.libjadx.core.EffectiveAnalysisConfig;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.jadxadapter.JadxControlFlowAdapter;
import dev.libjadx.jadxadapter.JadxSymbolAdapter;
import dev.libjadx.probes.HierarchyFixture;
import dev.libjadx.probes.JadxCfgProbeTest.Graph;
import dev.libjadx.probes.JadxCfgProbeTest.Limits;
import dev.libjadx.project.FileFingerprint;
import dev.libjadx.project.NativeProjectDocument;
import dev.libjadx.scheduler.ProjectBusyException;
import jadx.api.JadxDecompiler;
import jadx.api.data.impl.JadxCodeComment;
import jadx.api.data.impl.JadxCodeData;
import jadx.api.data.impl.JadxCodeRename;
import jadx.api.data.impl.JadxNodeRef;
import jadx.core.dex.attributes.AType;
import jadx.core.dex.nodes.MethodNode;
import jadx.core.dex.visitors.AbstractVisitor;
import jadx.plugins.input.dex.sections.DexClassData;
import jadx.plugins.input.dex.sections.DexCodeReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Internal feasibility gate only: no graph endpoint, primary processing or production graph types. */
class NormalizedCfgIsolationTest {
    @TempDir Path dir;
    @TempDir Path outsideRoot;
    static final ObjectMapper JSON = new ObjectMapper();
    static final EffectiveAnalysisConfig AUTO = new EffectiveAnalysisConfig("AUTO");
    static final String OWNER = "Lprobe/CfgCases;";

    static SymbolRef ref(String name) {
        String desc = switch (name) {
            case "branchLoop", "packed", "sparse" -> "(I)I";
            case "catches" -> "(Ljava/lang/RuntimeException;[I)I";
            case "absent" -> "()V";
            default -> "()I";
        };
        return new SymbolRef(SymbolRef.Kind.METHOD, OWNER, null, name, desc);
    }

    Path fixture() throws Exception {
        return HierarchyFixture.dex(dir, "cfg.dex", Files.readString(Path.of("tests/fixtures/cfg/CfgCases.smali")));
    }

    ProjectRuntime open(Path project, List<Path> inputs) throws Exception {
        var runtime = new ProjectRuntime(project, inputs, List.of(dir));
        try {
            runtime.initializeAsync(project == null ? null : NativeProjectDocument.open(project)).get(30, TimeUnit.SECONDS);
            return runtime;
        } catch (Exception e) { runtime.close(); throw e; }
    }

    static MethodNode method(JadxDecompiler jadx, SymbolRef ref) {
        return JadxSymbolAdapter.visibleClass(jadx, ref.originalClassDescriptor(), 0).getClassNode().getMethods().stream()
                .filter(m -> ref.equals(JadxSymbolAdapter.originalRef(m.getMethodInfo()))).findFirst().orElseThrow();
    }

    // This is a bounded test observation, NOT a proposed public DTO or original-offset model.
    record Observation(String availability, Graph graph, String alias, List<String> comments, String mode) { }

    static final class Capture {
        final SymbolRef selected;
        final Limits limits;
        final List<String> visited = new ArrayList<>();
        Graph copied;
        RuntimeException refusal;
        int encodedBytes;
        boolean failLater;
        boolean failOwnerLater;

        Capture(SymbolRef selected) { this(selected, Limits.DEFAULT); }
        Capture(SymbolRef selected, Limits limits) { this.selected = selected; this.limits = limits; }

        Observation run(JadxDecompiler jadx) {
            var cls = JadxSymbolAdapter.visibleClass(jadx, selected.originalClassDescriptor(), 0);
            var m = method(jadx, selected);
            String mode = jadx.getArgs().getDecompilationMode().name();
            var data = jadx.getArgs().getCodeData();
            List<String> comments = data.getComments().stream().map(c -> c.getComment()).toList();
            if (!(cls.getClassNode().getClsData() instanceof DexClassData) || !(m.getCodeReader() instanceof DexCodeReader)
                    || !(mode.equals("AUTO") || mode.equals("RESTRUCTURE")))
                return new Observation("UNAVAILABLE", null, m.getAlias(), comments, mode);
            // Reuse the bounded reader's safe payload/handler checks, not unchecked upstream decode.
            var preflight = JadxControlFlowAdapter.extract(cls, selected);
            if (!preflight.availability().equals("AVAILABLE"))
                return new Observation(preflight.availability(), null, m.getAlias(), comments, mode);
            var passes = jadx.getRoot().getPasses();
            int boundary = java.util.stream.IntStream.range(0, passes.size())
                    .filter(i -> passes.get(i).getClass().getSimpleName().equals("BlockFinisher")).findFirst().orElseThrow();
            passes.add(boundary + 1, new AbstractVisitor() {
                @Override public void visit(MethodNode current) {
                    if (current.isNoCode()) return;
                    visited.add(current.getParentClass().getClassInfo().getRawName() + "." + current.getMethodInfo().getName()
                            + ":" + current.getBasicBlocks().stream().mapToInt(b -> b.getInstructions().size()).sum());
                    if (!selected.equals(JadxSymbolAdapter.originalRef(current.getMethodInfo()))) return;
                    try {
                        // The old probe's conservative allocation bound is independent of actual JSON bytes.
                        Graph candidate = graph(current, new Limits(limits.instructions(), limits.blocks(), limits.edges(), Limits.DEFAULT.bytes()));
                        // Count actual UTF-8 without allocating a response-sized byte array.
                        JSON.writeValue(new java.io.OutputStream() {
                            @Override public void write(int value) { encodedBytes++; }
                            @Override public void write(byte[] values, int offset, int length) { encodedBytes += length; }
                        }, candidate);
                        if (encodedBytes > limits.bytes()) throw new IllegalArgumentException("Actual encoded graph byte limit");
                        copied = candidate;
                    } catch (RuntimeException e) {
                        refusal = e;
                        throw e; // DepthTraversal records this instead of aborting the owner/dependencies.
                    } catch (Exception e) { throw new AssertionError(e); }
                }
            });
            if (failLater || failOwnerLater) passes.add(boundary + 2, new AbstractVisitor() {
                @Override public boolean visit(jadx.core.dex.nodes.ClassNode current) {
                    if (failOwnerLater && current == m.getParentClass()) throw new IllegalStateException("controlled post-capture owner failure");
                    return true;
                }
                @Override public void visit(MethodNode current) {
                    if (failLater && current == m) throw new IllegalStateException("controlled post-capture generation failure");
                }
            });
            cls.getCodeInfo();
            if (refusal != null) throw refusal;
            // Reaching BlockFinisher does not prove later generation, the method or its owner succeeded.
            boolean failed = m.contains(AType.JADX_ERROR) || cls.getClassNode().contains(AType.JADX_ERROR)
                    || jadx.getRoot().getErrorsCounter().getErrorNodes().contains(m)
                    || jadx.getRoot().getErrorsCounter().getErrorNodes().contains(cls.getClassNode());
            return new Observation(failed || copied == null ? "FAILED" : "AVAILABLE", failed ? null : copied,
                    m.getAlias(), comments, mode);
        }
    }

    static ProjectRuntime.TemporaryResult<Observation> capture(ProjectRuntime runtime, SymbolRef selected) throws Exception {
        return runtime.withTemporaryAnalysis(AUTO, new Capture(selected)::run);
    }

    @Test void realTemporaryGraphsMatchIndependentOraclesAndOwnOnlyImmutableValues() throws Exception {
        Path dex = fixture(); byte[] bytes = Files.readAllBytes(dex);
        Graph retained;
        try (var runtime = open(null, List.of(dex))) {
            var before = runtime.projectSnapshot();
            var state = method(runtime.decompiler(), ref("branchLoop")).getParentClass().getState();
            var facts = JadxControlFlowAdapter.extract(JadxSymbolAdapter.visibleClass(runtime.decompiler(), OWNER, 0), ref("branchLoop"));
            for (String mode : List.of("AUTO", "RESTRUCTURE")) {
                for (String name : List.of("straight", "jump", "branchLoop", "packed", "sparse", "array", "catches")) {
                    var result = runtime.withTemporaryAnalysis(new EffectiveAnalysisConfig(mode), new Capture(ref(name))::run);
                    assertEquals("AVAILABLE", result.value().availability(), name);
                    Graph g = result.value().graph();
                    immutable(g);
                    assertThrows(UnsupportedOperationException.class, () -> g.nodes().clear());
                    assertThrows(UnsupportedOperationException.class, () -> g.nodes().getFirst().types().add("FAKE"));
                    if (name.equals("branchLoop")) {
                        loopBranches(g, "LE");
                        assertFalse(g.cleanEdges().contains("10>1"));
                    } else if (name.equals("packed") || name.equals("sparse")) {
                        assertEquals(List.of(name.equals("packed") ? "1:4>7,5>10,default>4" : "1:-5>7,1000>10,default>4"), g.switches());
                        assertTrue(g.nodes().stream().noneMatch(n -> n.offsets().contains(14)));
                    } else if (name.equals("catches")) {
                        assertEquals(List.of("6:false", "10:true"), g.handlers());
                        assertTrue(g.temporaryEdges().isEmpty());
                        assertFalse(g.edges().contains("4>6"));
                        String top = g.nodes().stream().filter(n -> n.synthetic() && n.start() == 1).findFirst().orElseThrow().id();
                        assertEquals(List.of(top + ">1", top + ">6", top + ">10"),
                                g.edges().stream().filter(e -> e.startsWith(top + ">")).toList());
                    } else if (name.equals("array")) {
                        assertEquals(List.of("ENTRY>0", "0>9", "9>EXIT"), g.edges());
                        assertTrue(g.nodes().stream().noneMatch(n -> n.offsets().contains(10)));
                    } else {
                        assertEquals(List.of("ENTRY>0", "0>EXIT"), g.edges());
                    }
                    System.out.println("TEMP " + mode + " " + name + " " + JSON.writeValueAsString(g));
                }
            }
            assertEquals("UNAVAILABLE", capture(runtime, ref("absent")).value().availability());
            for (String mode : List.of("SIMPLE", "FALLBACK"))
                assertEquals("UNAVAILABLE", runtime.withTemporaryAnalysis(new EffectiveAnalysisConfig(mode), new Capture(ref("straight"))::run).value().availability());
            retained = capture(runtime, ref("branchLoop")).value().graph();
            assertEquals(state, method(runtime.decompiler(), ref("branchLoop")).getParentClass().getState());
            assertEquals(facts, JadxControlFlowAdapter.extract(JadxSymbolAdapter.visibleClass(runtime.decompiler(), OWNER, 0), ref("branchLoop")));
            runtime.withPrimarySymbolRead(OWNER, c -> {
                var cls = JadxSymbolAdapter.visibleClass(c.decompiler(), OWNER, 0);
                assertNull(method(c.decompiler(), ref("branchLoop")).getBasicBlocks());
                cls.getCodeInfo();
                assertNull(method(c.decompiler(), ref("branchLoop")).getBasicBlocks());
                return null;
            });
            assertEquals(retained, capture(runtime, ref("branchLoop")).value().graph(), "Primary Java success/unload is irrelevant to fresh capture");
            assertEquals(before, runtime.projectSnapshot());
            assertArrayEquals(bytes, Files.readAllBytes(dex));
        }
        loopBranches(retained, "LE"); // Still usable after all engines close.
        Path jar = HierarchyFixture.javaJar(dir.resolve("jvm"), "jvm.jar", Map.of("Jvm.java", "public class Jvm { public static int f(){return 31;} }"));
        try (var runtime = open(null, List.of(jar))) {
            var jvm = new SymbolRef(SymbolRef.Kind.METHOD, "LJvm;", null, "f", "()I");
            assertEquals("UNAVAILABLE", capture(runtime, jvm).value().availability());
        }
        Path onlyReturn = HierarchyFixture.dex(dir, "return.dex", ".class public Lprobe/ReturnOnly;\n.super Ljava/lang/Object;\n"
                + ".method public static done()V\n.registers 0\nreturn-void\n.end method\n");
        try (var runtime = open(null, List.of(onlyReturn))) {
            var result = capture(runtime, new SymbolRef(SymbolRef.Kind.METHOD, "Lprobe/ReturnOnly;", null, "done", "()V"));
            assertEquals("AVAILABLE", result.value().availability());
            assertEquals(List.of("ENTRY>0", "0>EXIT"), result.value().graph().edges());
            assertEquals(List.of("RETURN"), result.value().graph().nodes().get(1).types());
        }
    }

    /** Recursive allowlist: returning an apparently immutable record containing a Jadx node must fail. */
    static void immutable(Object value) throws Exception {
        if (value == null || value instanceof String || value instanceof Integer || value instanceof Boolean) return;
        if (value instanceof List<?> list) {
            for (Object item : list) immutable(item);
        } else {
            assertTrue(value.getClass().isRecord(), value.getClass().getName());
            assertTrue(value.getClass().getName().startsWith("dev.libjadx.probes.JadxCfgProbeTest$"));
            for (var field : value.getClass().getRecordComponents()) immutable(field.getAccessor().invoke(value));
        }
    }

    static void loopBranches(Graph g, String operator) {
        // BlockProcessor inserts a synthetic loop-exit splitter before original RETURN at 13.
        String exitSplitter = g.nodes().stream().filter(n -> n.synthetic() && n.start() == 13).findFirst().orElseThrow().id();
        var joinSplitters = g.nodes().stream().filter(n -> n.synthetic() && n.start() == 10).toList();
        assertEquals(2, joinSplitters.size());
        String right = joinSplitters.get(0).id(), left = joinSplitters.get(1).id();
        assertEquals(List.of("1:" + operator + ":" + exitSplitter + ":3", "3:EQ:8:5"), g.branches());
        assertEquals(List.of("ENTRY>0", "0>1", "1>3", "1>" + exitSplitter, "3>5", "3>8", "5>" + right,
                right + ">10", "10>1", "8>" + left, left + ">10", exitSplitter + ">13", "13>EXIT"), g.edges());
    }

    @Test void unsavedReplayAllowsPrimaryMutationButNotAnotherTemporaryOrRequestedShutdown() throws Exception {
        Path dex = fixture(), project = dir.resolve("project.jadx");
        NativeProjectDocument.newFromInputs(project, List.of(dex)).save();
        var nativeHash = FileFingerprint.of(project); var inputHash = FileFingerprint.of(dex);
        try (var runtime = open(project, List.of(dex))) {
            var edits = new JadxCodeData();
            var nativeRef = new JadxNodeRef(jadx.api.data.IJavaNodeRef.RefType.METHOD, "probe.CfgCases", "branchLoop(I)I");
            edits.setRenames(List.of(new JadxCodeRename(nativeRef, "unsavedLoop")));
            edits.setComments(List.of(new JadxCodeComment(nativeRef, "unsaved graph comment")));
            runtime.replaceCodeData(edits, 0);
            var before = runtime.projectSnapshot(); var identity = runtime.searchIdentity();
            var primaryData = runtime.decompiler().getArgs().getCodeData();
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            var pending = CompletableFuture.supplyAsync(() -> {
                try {
                    return runtime.withTemporarySourceRead(new EffectiveAnalysisConfig("RESTRUCTURE"), context -> {
                        assertNotSame(primaryData, context.decompiler().getArgs().getCodeData());
                        assertNotSame(primaryData.getRenames().getFirst(), context.decompiler().getArgs().getCodeData().getRenames().getFirst());
                        assertNotSame(primaryData.getComments().getFirst(), context.decompiler().getArgs().getCodeData().getComments().getFirst());
                        entered.countDown(); await(release);
                        var result = new Capture(ref("branchLoop")).run(context.decompiler());
                        String source = JadxSymbolAdapter.visibleClass(context.decompiler(), OWNER, 0).getCode();
                        assertTrue(source.contains("unsaved graph comment")); assertTrue(source.contains("unsavedLoop"));
                        assertFalse(source.contains("newLoop"));
                        return result;
                    });
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            try {
                assertTrue(entered.await(30, TimeUnit.SECONDS));
                assertThrows(ProjectBusyException.class, () -> capture(runtime, ref("straight")));
                for (var policy : ShutdownPolicy.values())
                    assertThrows(ProjectRuntime.ShutdownRejectedException.class, () -> runtime.requestShutdown(policy));
                runtime.withPrimarySymbolRead(OWNER, c -> {
                    assertTrue(JadxSymbolAdapter.visibleClass(c.decompiler(), OWNER, 0).getCode().contains("unsaved graph comment"));
                    assertThrows(ProjectBusyException.class, () -> capture(runtime, ref("straight")));
                    return null;
                });
                assertEquals(before, runtime.projectSnapshot()); assertEquals(identity, runtime.searchIdentity());
                edits.setRenames(List.of(new JadxCodeRename(nativeRef, "newLoop")));
                edits.setComments(List.of(new JadxCodeComment(nativeRef, "new comment")));
                runtime.replaceCodeData(edits, before.revisions().logicalRevision());
            } finally { release.countDown(); }
            var old = pending.get(30, TimeUnit.SECONDS);
            assertEquals(before.revisions(), old.revisions());
            assertEquals("unsavedLoop", old.value().alias()); assertEquals(List.of("unsaved graph comment"), old.value().comments());
            assertEquals("RESTRUCTURE", old.value().mode());
            var current = capture(runtime, ref("branchLoop"));
            assertEquals("newLoop", current.value().alias()); assertEquals(List.of("new comment"), current.value().comments());
            assertNotEquals(old.sourceSnapshotId(), current.sourceSnapshotId());
            assertEquals(old.value().graph(), current.value().graph());
            assertEquals(before.revisions().logicalRevision() + 1, runtime.projectSnapshot().revisions().logicalRevision());
            assertTrue(runtime.projectSnapshot().dirty());
            assertEquals(nativeHash, FileFingerprint.of(project)); assertEquals(inputHash, FileFingerprint.of(dex));
            assertEquals("AUTO", runtime.decompiler().getArgs().getDecompilationMode().name());
            runtime.withExclusiveEdit(c -> {
                assertThrows(ProjectBusyException.class, () -> capture(runtime, ref("straight")));
                return null;
            });
        }
    }

    @Test void pathSnapshotDoesNotFreezeInputOrMappingAndSaveAloneDoesNotRebindTemporaryId() throws Exception {
        Path dex = fixture(), project = dir.resolve("project.jadx"), mappings = dir.resolve("names.tiny");
        byte[] oldDex = Files.readAllBytes(dex);
        Path changed = HierarchyFixture.dex(dir, "changed.dex", Files.readString(Path.of("tests/fixtures/cfg/CfgCases.smali")).replace("if-lez p0, :end", "if-gtz p0, :end"));
        byte[] newDex = Files.readAllBytes(changed);
        Files.writeString(mappings, mapping("MappedBefore"));
        NativeProjectDocument.newFromInputs(project, List.of(dex)).save();
        var engines = new Engines();
        try (var runtime = engines.open(project, List.of(dex))) {
            var rev = runtime.projectSnapshot().revisions(); runtime.updateMappingsPath(mappings, rev.sessionId(), rev.logicalRevision());
            var baseline = capture(runtime, ref("branchLoop"));
            var before = runtime.projectSnapshot();
            engines.beforeLoad = () -> {
                try { Files.write(dex, newDex); Files.writeString(mappings, mapping("MappedAfter")); }
                catch (Exception e) { throw new RuntimeException(e); }
            };
            var changedResult = runtime.withTemporarySourceRead(AUTO, context -> {
                assertEquals("MappedAfter", JadxSymbolAdapter.visibleClass(context.decompiler(), OWNER, 0).getName());
                // Once loaded, plain DEX and mappings have been consumed; undoing the files does NOT undo the loaded state.
                try { Files.write(dex, oldDex); Files.writeString(mappings, mapping("MappedBefore")); }
                catch (Exception e) { throw new RuntimeException(e); }
                return new Capture(ref("branchLoop")).run(context.decompiler());
            });
            assertEquals(before, runtime.projectSnapshot());
            assertEquals(baseline.revisions(), changedResult.revisions());
            assertEquals(baseline.sourceSnapshotId(), changedResult.sourceSnapshotId(), "COUNTEREXAMPLE: old identity, changed loaded graph");
            loopBranches(changedResult.value().graph(), "GT");
            assertNotEquals(baseline.value().graph(), changedResult.value().graph());
            assertEquals(baseline.value().graph(), capture(runtime, ref("branchLoop")).value().graph());
            long epoch = runtime.searchIdentity().publicationEpoch();
            runtime.saveProject(null, null);
            var saved = capture(runtime, ref("branchLoop"));
            assertEquals(baseline.revisions().logicalRevision(), saved.revisions().logicalRevision());
            assertNotEquals(baseline.revisions().persistedIdentity(), saved.revisions().persistedIdentity());
            assertTrue(runtime.searchIdentity().publicationEpoch() > epoch);
            assertEquals(baseline.sourceSnapshotId(), saved.sourceSnapshotId(), "Temporary helper identity omits save/publication; not a graph ID");
            Files.write(dex, newDex); Files.writeString(mappings, mapping("MappedAfter"));
            var r = runtime.projectSnapshot().revisions(); runtime.reloadProject(true, r.sessionId(), r.logicalRevision());
            var reloaded = capture(runtime, ref("branchLoop"));
            assertNotEquals(saved.sourceSnapshotId(), reloaded.sourceSnapshotId());
            assertEquals(changedResult.value().graph(), reloaded.value().graph());
            System.out.println("BINDING same temporary id with changed DEX/mapping before load; loaded DEX survives file restore; save-only id unchanged; explicit reload rebinds");
        }
        try (var runtime = open(project, List.of(dex))) {
            loopBranches(capture(runtime, ref("branchLoop")).value().graph(), "GT");
        }
        assertEquals(engines.created.get(), engines.closed.get());
    }

    static String mapping(String alias) { return "tiny\t2\t0\tofficial\tnamed\nc\tprobe/CfgCases\tprobe/" + alias + "\n"; }

    @Test void temporaryLoadDoesNotRevalidateAnInputReplacedByAnOutsideRootSymlink() throws Exception {
        Path dex = fixture(); byte[] accepted = Files.readAllBytes(dex);
        Path outside = HierarchyFixture.dex(outsideRoot, "outside.dex",
                Files.readString(Path.of("tests/fixtures/cfg/CfgCases.smali")).replace("if-lez p0, :end", "if-gtz p0, :end"));
        var engines = new Engines();
        try (var runtime = engines.open(null, List.of(dex))) {
            var before = runtime.projectSnapshot();
            engines.beforeLoad = () -> {
                try { Files.delete(dex); Files.createSymbolicLink(dex, outside); }
                catch (Exception e) { throw new RuntimeException(e); }
            };
            try {
                var result = capture(runtime, ref("branchLoop"));
                loopBranches(result.value().graph(), "GT");
                assertEquals(before.revisions(), result.revisions());
                // The existing repository baseline guard rejects the same filesystem state.
                assertThrows(SecurityException.class, () -> runtime.withExclusiveEdit(c -> { c.checkAnalysisBaselines(); return null; }));
                assertEquals(before, runtime.projectSnapshot());
                System.out.println("PATH COUNTEREXAMPLE temporary load follows outside-root symlink; repository analysis guard rejects it");
            } finally { Files.delete(dex); Files.write(dex, accepted); }
            loopBranches(capture(runtime, ref("branchLoop")).value().graph(), "LE");
        }
        assertEquals(engines.created.get(), engines.closed.get());
    }

    @Test void acceptedSaveSettingsAndReloadCanOverlapCapturedTemporaryWork() throws Exception {
        Path dex = fixture(), project = dir.resolve("project.jadx"), mapping = dir.resolve("other.tiny");
        NativeProjectDocument.newFromInputs(project, List.of(dex)).save();
        Files.writeString(mapping, mapping("ReloadedAlias"));
        String session;
        try (var runtime = open(project, List.of(dex))) {
            session = runtime.projectSnapshot().revisions().sessionId();
            var old = runtime.withTemporarySourceRead(AUTO, context -> {
                long epoch = context.publicationEpoch();
                try {
                    runtime.saveProject(null, null);
                    var r = runtime.projectSnapshot().revisions();
                    runtime.updateMappingsPath(mapping, r.sessionId(), r.logicalRevision());
                    runtime.saveProject(null, null); // Reload must not silently preserve unsaved native settings.
                    r = runtime.projectSnapshot().revisions();
                    runtime.reloadProject(true, r.sessionId(), r.logicalRevision());
                } catch (Exception e) { throw new RuntimeException(e); }
                assertTrue(runtime.searchIdentity().publicationEpoch() > epoch);
                assertEquals("CfgCases", JadxSymbolAdapter.visibleClass(context.decompiler(), OWNER, 0).getName());
                assertEquals("ReloadedAlias", JadxSymbolAdapter.visibleClass(runtime.decompiler(), OWNER, 0).getName());
                return new Capture(ref("straight")).run(context.decompiler());
            });
            var newer = capture(runtime, ref("straight"));
            assertEquals(old.value().graph(), newer.value().graph());
            assertTrue(newer.revisions().logicalRevision() > old.revisions().logicalRevision());
            assertNotEquals(old.sourceSnapshotId(), newer.sourceSnapshotId());
        }
        try (var runtime = open(project, List.of(dex))) {
            assertNotEquals(session, capture(runtime, ref("straight")).revisions().sessionId());
        }
    }

    @Test void failureAndRefusalReleaseRealEnginesAndDoNotPublishEarlierCapture() throws Exception {
        Path dex = fixture();
        Path broken = HierarchyFixture.dex(dir, "broken.dex", Files.readString(Path.of("tests/fixtures/smali/Disassembly.smali")));
        var engines = new Engines();
        try (var runtime = engines.open(null, List.of(dex, broken))) {
            var before = runtime.projectSnapshot();
            engines.beforeLoad = () -> { throw new IllegalStateException("controlled load failure"); };
            assertThrows(IllegalStateException.class, () -> capture(runtime, ref("straight")));
            assertEquals(1, engines.closed.get());
            var limit = new Capture(ref("branchLoop"), new Limits(1024, 0, 2048, 2097152));
            assertThrows(IllegalArgumentException.class, () -> runtime.withTemporaryAnalysis(AUTO, limit::run));
            assertNull(limit.copied); assertEquals(2, engines.closed.get());
            var late = new Capture(ref("straight")); late.failLater = true;
            var failed = runtime.withTemporaryAnalysis(AUTO, late::run);
            assertNotNull(late.copied, "Control reached actual capture before later failure");
            assertEquals("FAILED", failed.value().availability()); assertNull(failed.value().graph());
            var ownerFailure = new Capture(ref("straight")); ownerFailure.failOwnerLater = true;
            var failedOwner = runtime.withTemporaryAnalysis(AUTO, ownerFailure::run).value();
            assertNotNull(ownerFailure.copied); assertEquals("FAILED", failedOwner.availability()); assertNull(failedOwner.graph());
            var bad = new SymbolRef(SymbolRef.Kind.METHOD, "Lprobe/Disassembly;", null, "broken", "()I");
            runtime.withPrimarySymbolRead("broken", c -> {
                JadxSymbolAdapter.visibleClass(c.decompiler(), "Lprobe/Disassembly;", 0).getCodeInfo();
                assertTrue(c.decompiler().getRoot().getErrorsCounter().getErrorNodes().contains(method(c.decompiler(), bad)));
                return null;
            });
            assertEquals("FAILED", capture(runtime, bad).value().availability());
            var healthy = new SymbolRef(SymbolRef.Kind.METHOD, "Lprobe/Disassembly;", null, "answer", "()I");
            assertEquals("AVAILABLE", capture(runtime, healthy).value().availability(), "Healthy neighboring method is not poisoned");
            assertEquals("AVAILABLE", capture(runtime, ref("straight")).value().availability());
            assertEquals(before, runtime.projectSnapshot());
            assertEquals(engines.created.get() - 1, engines.closed.get());
        }
        assertEquals(engines.created.get(), engines.closed.get());
    }

    @Test void tinySelectionDoesNotBoundOwnerOrDependencyWorkAndVisitorRefusalDoesNotStopIt() throws Exception {
        Path dep = HierarchyFixture.dex(dir, "dep.dex", ".class public Lprobe/Dependency;\n.super Ljava/lang/Object;\n" + large("heavy"));
        Path owner = HierarchyFixture.dex(dir, "owner.dex", ".class public Lprobe/Cost;\n.super Lprobe/Dependency;\n"
                + ".method public static tiny()I\n.registers 1\nconst/4 v0, 3\nreturn v0\n.end method\n" + large("zzLarge"));
        var selected = new SymbolRef(SymbolRef.Kind.METHOD, "Lprobe/Cost;", null, "tiny", "()I");
        var engines = new Engines();
        try (var runtime = engines.open(null, List.of(owner, dep))) {
            var refusal = new Capture(selected, new Limits(1024, 0, 2048, 2097152));
            long start = System.nanoTime();
            long[] processingNanos = {0};
            assertThrows(IllegalArgumentException.class, () -> runtime.withTemporaryAnalysis(AUTO, jadx -> {
                assertEquals(2, method(jadx, selected).getCodeReader().getUnitsCount());
                assertEquals(3602, method(jadx, new SymbolRef(SymbolRef.Kind.METHOD, "Lprobe/Cost;", null, "zzLarge", "()I")).getCodeReader().getUnitsCount());
                long processingStart = System.nanoTime();
                try { return refusal.run(jadx); }
                finally { processingNanos[0] = System.nanoTime() - processingStart; }
            }));
            long total = System.nanoTime() - start;
            assertEquals(List.of("probe.Dependency.heavy:1802", "probe.Cost.tiny:2", "probe.Cost.zzLarge:1802"), refusal.visited);
            assertNull(refusal.copied);
            System.out.println("COST tiny=2 code units; post-finisher visits=" + refusal.visited
                    + "; temporary_load_ms=" + TimeUnit.NANOSECONDS.toMillis(engines.lastLoadNanos)
                    + "; owner_processing_ms=" + TimeUnit.NANOSECONDS.toMillis(processingNanos[0])
                    + "; refusal_total_ms=" + TimeUnit.NANOSECONDS.toMillis(total));
            assertEquals("AVAILABLE", runtime.withTemporaryAnalysis(AUTO, new Capture(selected)::run).value().availability());
        }
        assertEquals(engines.created.get(), engines.closed.get());
    }

    static String large(String name) {
        return ".method public static " + name + "()I\n.registers 1\nconst/4 v0, 0\n"
                + "add-int/lit8 v0, v0, 1\n".repeat(1800) + "return v0\n.end method\n";
    }

    @Test void graphLimitsAreInclusiveAndActualEncodingIsCheckedIndependently() throws Exception {
        try (var runtime = open(null, List.of(fixture()))) {
            // Independent normalized oracle from CfgCases and the original feasibility gate.
            var exact = new Capture(ref("branchLoop"), new Limits(7, 12, 13, 2097152));
            Graph g = runtime.withTemporaryAnalysis(AUTO, exact::run).value().graph();
            assertEquals(12, g.nodes().size()); assertEquals(13, g.edges().size());
            assertEquals(7, g.nodes().stream().mapToInt(n -> n.types().size()).sum());
            for (var limits : List.of(new Limits(6, 12, 13, 2097152), new Limits(7, 11, 13, 2097152), new Limits(7, 12, 12, 2097152)))
                assertThrows(IllegalArgumentException.class, () -> runtime.withTemporaryAnalysis(AUTO, new Capture(ref("branchLoop"), limits)::run));
            byte[] encoded = JSON.writeValueAsBytes(g);
            assertEquals(encoded.length, exact.encodedBytes);
            // Reference encoder measures actual UTF-8, not the probe's conservative copyBound helper.
            assertEquals(g, runtime.withTemporaryAnalysis(AUTO,
                    new Capture(ref("branchLoop"), new Limits(7, 12, 13, encoded.length))::run).value().graph());
            assertThrows(IllegalArgumentException.class, () -> runtime.withTemporaryAnalysis(AUTO,
                    new Capture(ref("branchLoop"), new Limits(7, 12, 13, encoded.length - 1))::run));
            assertEquals("AVAILABLE", capture(runtime, ref("branchLoop")).value().availability());
            System.out.println("BOUNDS 7 instructions / 12 blocks / 13 edges; actual_JSON_bytes=" + encoded.length
                    + "; exact encoding budget succeeds; one byte less refuses atomically");
        }
    }

    @Test void closeDuringRealTemporaryLoadWaitsForActualLeaseRelease() throws Exception {
        var engines = new Engines(); CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        var runtime = engines.open(null, List.of(fixture()));
        engines.beforeLoad = () -> { entered.countDown(); await(release); };
        var pending = CompletableFuture.supplyAsync(() -> {
            try { return capture(runtime, ref("straight")); }
            catch (Exception e) { throw new RuntimeException(e); }
        });
        try {
            assertTrue(entered.await(30, TimeUnit.SECONDS));
            CompletableFuture.runAsync(runtime::close).get(5, TimeUnit.SECONDS);
            assertEquals("SHUTTING_DOWN", runtime.status().state());
            assertFalse(runtime.awaitStopped(20, TimeUnit.MILLISECONDS));
            assertThrows(IllegalStateException.class, () -> capture(runtime, ref("straight")));
        } finally { release.countDown(); }
        assertEquals("AVAILABLE", pending.get(30, TimeUnit.SECONDS).value().availability());
        assertTrue(runtime.awaitStopped(5, TimeUnit.SECONDS));
        assertEquals(engines.created.get(), engines.closed.get());
        runtime.close();
    }

    static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(30, TimeUnit.SECONDS)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }

    /** Instruments ownership only; all graph/loading/failure behavior still uses real pinned Jadx. */
    static final class Engines {
        final AtomicInteger created = new AtomicInteger(), closed = new AtomicInteger();
        volatile Runnable beforeLoad = () -> { };
        volatile long lastLoadNanos;
        ProjectRuntime open(Path project, List<Path> inputs) throws Exception {
            var runtime = new ProjectRuntime(project, inputs, args -> {
                created.incrementAndGet();
                var engine = new ProjectRuntime.JadxProjectEngine(args);
                Runnable hook = beforeLoad; beforeLoad = () -> { };
                return new ProjectRuntime.ProjectEngine() {
                    @Override public void load() {
                        hook.run(); long start = System.nanoTime();
                        try { engine.load(); }
                        finally { lastLoadNanos = System.nanoTime() - start; }
                    }
                    @Override public JadxDecompiler decompiler() { return engine.decompiler(); }
                    @Override public dev.libjadx.core.hierarchy.RelatedHierarchyVerifier hierarchyVerifier() { return engine.hierarchyVerifier(); }
                    @Override public void close() { closed.incrementAndGet(); engine.close(); }
                };
            });
            try { runtime.initializeAsync(project == null ? null : NativeProjectDocument.open(project)).get(30, TimeUnit.SECONDS); return runtime; }
            catch (Exception e) { runtime.close(); throw e; }
        }
    }
}
