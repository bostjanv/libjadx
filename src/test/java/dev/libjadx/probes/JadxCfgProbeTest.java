package dev.libjadx.probes;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import dev.libjadx.app.ProjectRuntime;
import dev.libjadx.jadxadapter.JadxSymbolAdapter;
import dev.libjadx.scheduler.ProjectBusyException;
import jadx.api.DecompilationMode;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.plugins.input.insns.custom.ISwitchPayload;
import jadx.core.dex.attributes.AFlag;
import jadx.core.dex.attributes.AType;
import jadx.core.dex.instructions.IfNode;
import jadx.core.dex.instructions.SwitchInsn;
import jadx.core.dex.nodes.BlockNode;
import jadx.core.dex.nodes.MethodNode;
import jadx.core.dex.nodes.ProcessState;
import jadx.core.dex.visitors.AbstractVisitor;
import jadx.plugins.input.dex.DexReader;
import jadx.plugins.input.dex.insns.DexInsnData;
import jadx.plugins.input.dex.sections.DexCodeReader;
import jadx.plugins.input.dex.sections.SectionReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Evidence-only pinned internal observations, not an implementation of a raw CFG engine. */
class JadxCfgProbeTest {
    @TempDir Path dir;

    private Path fixture() throws Exception {
        return HierarchyFixture.dex(dir, "cfg.dex", Files.readString(Path.of("tests/fixtures/cfg/CfgCases.smali")));
    }

    @Test void independentDexFactsAndEarlyNormalizedEdges() throws Exception {
        try (var jadx = open(fixture())) {
            var cls = JadxSymbolAdapter.visibleClass(jadx, "Lprobe/CfgCases;", 0);
            var methods = cls.getClassNode().getMethods();
            var originalState = cls.getClassNode().getState();
            Map<String, List<InputInsn>> inputs = new LinkedHashMap<>();
            for (var m : methods) if (!m.isNoCode()) inputs.put(m.getName(), input(m));
            assertEquals(originalState, cls.getClassNode().getState());
            starts(inputs, "straight", 0, 2);
            starts(inputs, "jump", 0, 1, 2, 4, 5, 7);
            starts(inputs, "branchLoop", 0, 1, 3, 5, 7, 8, 10, 12, 13);
            starts(inputs, "packed", 0, 1, 4, 6, 7, 9, 10, 12, 13, 14);
            starts(inputs, "sparse", 0, 1, 4, 6, 7, 9, 10, 12, 13, 14);
            starts(inputs, "array", 0, 1, 3, 6, 7, 9, 10);
            starts(inputs, "catches", 0, 1, 2, 4, 5, 6, 7, 9, 10, 11, 13);
            assertEquals(5, at(inputs.get("jump"), 1).target());
            assertEquals(List.of(13, 8, 10, 1), List.of(1, 3, 7, 12).stream()
                    .map(o -> at(inputs.get("branchLoop"), o).target()).toList());
            for (String name : List.of("packed", "sparse")) {
                var insns = inputs.get(name);
                assertEquals(14, at(insns, 1).target());
                assertEquals(List.of(6, 9), at(insns, 14).targets(), "Input switch deltas are relative to switch at 1");
                assertEquals(name.equals("packed") ? List.of(4, 5) : List.of(-5, 1000), at(insns, 14).keys());
            }
            assertEquals(10, at(inputs.get("array"), 3).target());
            assertEquals("FILL_ARRAY_DATA_PAYLOAD", at(inputs.get("array"), 10).opcode());
            assertEquals(List.of(new TryFact(1, 4, List.of("Ljava/lang/RuntimeException;"), List.of(6), 10)),
                    tries(method(methods, "catches")));

            Map<String, Graph> split = new LinkedHashMap<>(), finished = new LinkedHashMap<>(), transformed = new LinkedHashMap<>();
            capture(jadx, "BlockSplitter", false, split);
            capture(jadx, "BlockFinisher", false, finished);
            capture(jadx, "RegionMakerVisitor", true, transformed);
            cls.getCodeInfo();
            System.out.println("INPUT " + inputs);
            System.out.println("SPLIT " + split);
            System.out.println("FINISHED " + finished);
            System.out.println("PRE_REGIONS " + transformed);
            assertTrue(jadx.getRoot().getErrorsCounter().getErrorNodes().isEmpty(), "Positive fixture must generate Java");
            assertEdges(split.get("straight"), "ENTRY>0", "0>2", "2>EXIT");
            assertEdges(split.get("jump"), "ENTRY>0", "0>5", "2>4", "4>EXIT", "5>7", "7>EXIT");
            assertEdges(split.get("branchLoop"), "ENTRY>0", "0>1", "1>3", "1>13", "3>5", "3>8", "5>10", "8>10", "10>1", "13>EXIT");
            assertEquals(List.of("1:LE:13:3", "3:EQ:8:5"), split.get("branchLoop").branches());
            for (String name : List.of("packed", "sparse")) {
                var graph = split.get(name);
                assertEdges(graph, "ENTRY>0", "0>1", "1>4", "1>7", "1>10", "4>6", "6>EXIT", "7>9", "9>EXIT", "10>12", "12>EXIT");
                assertEquals(List.of(name.equals("packed") ? "1:4>7,5>10,default>4" : "1:-5>7,1000>10,default>4"), graph.switches());
                assertFalse(graph.offsets().contains(14), "Payload must not become an executable block/instruction");
            }
            assertEdges(split.get("array"), "ENTRY>0", "0>9", "9>EXIT");
            assertFalse(split.get("array").offsets().contains(10));
            assertEdges(split.get("catches"), "ENTRY>0", "0>1", "0>6", "0>10", "1>2", "2>4", "2>5",
                    "4>EXIT", "5>EXIT", "6>7", "7>9", "9>EXIT", "10>11", "11>13", "13>EXIT");
            assertFalse(split.get("jump").types().contains("GOTO"), "Even earliest block graph is normalized");
            assertTrue(split.get("jump").offsets().containsAll(List.of(2, 4)));
            assertFalse(finished.get("jump").offsets().contains(2), "BlockProcessor removes unreachable code");
            assertFalse(finished.get("jump").offsets().contains(4));
            assertNotEquals(split.get("jump"), transformed.get("jump"));
            assertEdges(finished.get("jump"), "ENTRY>0", "0>EXIT");
            assertEdges(transformed.get("jump"), "ENTRY>0", "0>EXIT");
            assertEquals(List.of(7), transformed.get("jump").offsets(), "Constants inline into RETURN, not original instruction coverage");
            for (String name : List.of("packed", "sparse")) {
                assertEdges(finished.get(name), "ENTRY>0", "0>1", "1>4", "1>7", "1>10", "4>EXIT", "7>EXIT", "10>EXIT");
                assertEquals(split.get(name).switches(), transformed.get(name).switches());
            }
            assertEquals(List.of("0>6", "0>10"), split.get("catches").temporaryEdges());
            assertFalse(split.get("catches").edges().contains("4>6"), "Temporary handler links are not throw-site edges");
            assertTrue(finished.get("catches").temporaryEdges().isEmpty());
            assertEquals(List.of("6:false", "10:true"), finished.get("catches").handlers());
            assertTrue(finished.get("catches").syntheticBlocks() > 2, "Exception processing adds synthetic splitters");
            var catches = finished.get("catches");
            String top = catches.nodes().stream().filter(n -> n.synthetic() && n.start() == 1).findFirst().orElseThrow().id();
            String bottom = catches.nodes().stream().filter(n -> n.synthetic() && n.id().startsWith("S") && n.start() == -1)
                    .findFirst().orElseThrow().id();
            assertEdges(catches, "ENTRY>0", "0>" + top, top + ">1", top + ">6", top + ">10", "1>2", "2>4", "2>5",
                    "4>EXIT", "4>" + bottom, bottom + ">6", bottom + ">10", "5>EXIT", "6>7", "7>EXIT", "10>11", "11>EXIT");
            assertEquals(catches.edges(), transformed.get("catches").edges(), "This fixture retains splitter topology, not per-site exception edges");
            assertTrue(finished.get("branchLoop").edges().size() > finished.get("branchLoop").cleanEdges().size(),
                    "Clean successors discard loop back edges, not just exceptions");
            assertTrue(finished.get("branchLoop").edges().contains("10>1"));
            assertFalse(finished.get("branchLoop").cleanEdges().contains("10>1"));
            assertNull(method(methods, "straight").getBasicBlocks(), "Codegen unloads live graph");
            assertFalse(split.containsKey("absent"), "No-code is not a successful empty graph");
            for (var m : methods) if (!m.isNoCode()) assertEquals(inputs.get(m.getName()), input(m));
        }
    }

    @Test void repeatedPostJavaFailureAliasAndJvmBoundaries() throws Exception {
        Path dex = fixture();
        Path broken = HierarchyFixture.dex(dir, "broken.dex", Files.readString(Path.of("tests/fixtures/smali/Disassembly.smali")));
        Path jar = HierarchyFixture.javaJar(dir.resolve("jvm"), "healthy.jar", Map.of("Healthy.java",
                "public class Healthy { public static int answer() { return 37; } }"));
        try (var jadx = open(dex, broken, jar)) {
            var cls = JadxSymbolAdapter.visibleClass(jadx, "Lprobe/CfgCases;", 0);
            var m = method(cls.getClassNode().getMethods(), "branchLoop");
            var raw = input(m);
            assertNull(m.getBasicBlocks());
            // This diagnostic API unconditionally unloads/reloads one method. It is NOT a safe read API.
            var process = jadx.getRoot().getProcessClasses();
            assertTrue(process.processMethodUntilVisitor(m, "BlockFinisher", true));
            var cold = graph(m, Limits.DEFAULT);
            assertTrue(process.processMethodUntilVisitor(m, "BlockFinisher", true));
            assertEquals(cold, graph(m, Limits.DEFAULT));
            assertEquals(ProcessState.NOT_LOADED, cls.getClassNode().getState(), "Method processing does not advance owner state");
            // Restore method so the ordinary class pipeline starts coherently.
            m.unload();
            cls.getCodeInfo();
            assertNull(m.getBasicBlocks());
            cls.getCodeInfo();
            assertNull(m.getBasicBlocks(), "Cached Java does not rebuild graphs");
            assertTrue(process.processMethodUntilVisitor(m, "BlockFinisher", true));
            assertEquals(cold, graph(m, Limits.DEFAULT));
            assertEquals(ProcessState.GENERATED_AND_UNLOADED, cls.getClassNode().getState(), "Owner still says unloaded while method has live blocks");
            cls.getClassNode().getClassInfo().changeShortName("AliasedCfg");
            m.getMethodInfo().setAlias("renamed");
            assertTrue(process.processMethodUntilVisitor(m, "BlockFinisher", true));
            assertEquals(cold, graph(m, Limits.DEFAULT));
            assertEquals(raw, input(m));
            m.unload();

            var badClass = JadxSymbolAdapter.visibleClass(jadx, "Lprobe/Disassembly;", 0);
            var bad = method(badClass.getClassNode().getMethods(), "broken");
            var before = input(bad);
            assertEquals(List.of(0), before.stream().map(InputInsn::offset).toList());
            badClass.getCodeInfo();
            assertTrue(jadx.getRoot().getErrorsCounter().getErrorNodes().contains(bad));
            assertNull(bad.getBasicBlocks());
            assertEquals(before, input(bad));
            assertTrue(process.processMethodUntilVisitor(bad, "BlockFinisher", true), "True only says pass boundary was visited");
            assertNull(bad.getBasicBlocks(), "CheckCode error skips remaining method visitors; no graph recovered");
            assertNotNull(bad.get(AType.JADX_ERROR));
            var healthy = JadxSymbolAdapter.visibleClass(jadx, "LHealthy;", 0);
            var answer = method(healthy.getClassNode().getMethods(), "answer");
            assertFalse(answer.getCodeReader() instanceof DexCodeReader, "JVM reader cannot inherit DEX coordinate claims");
            assertTrue(process.processMethodUntilVisitor(answer, "BlockFinisher", true));
            assertFalse(graph(answer, Limits.DEFAULT).nodes().isEmpty(), "Normalized graph is not inherently DEX-only");
            answer.unload();
            assertTrue(healthy.getCodeInfo().getCodeStr().contains("return 37;"));
            assertFalse(jadx.getRoot().getErrorsCounter().getErrorNodes().contains(answer));
            System.out.println("LIFETIME cold/repeated/post-Java/alias=" + cold + "; broken=FAILED; JVM=normalized-only");
        }
    }

    @Test void boundedCopiesRejectBeforeCollectionAndMalformedReadersNeverSucceedEmpty() throws Exception {
        Path dex = fixture();
        try (var jadx = open(dex)) {
            var methods = JadxSymbolAdapter.visibleClass(jadx, "Lprobe/CfgCases;", 0).getClassNode().getMethods();
            assertNull(method(methods, "absent").getCodeReader());
            var m = method(methods, "branchLoop");
            assertTrue(jadx.getRoot().getProcessClasses().processMethodUntilVisitor(m, "BlockFinisher", true));
            Graph copy = graph(m, Limits.DEFAULT);
            int blocks = m.getBasicBlocks().size();
            int insns = m.getBasicBlocks().stream().mapToInt(b -> b.getInstructions().size()).sum();
            int edges = m.getBasicBlocks().stream().mapToInt(b -> b.getSuccessors().size()).sum();
            long bytesBound = copyBound(m);
            assertTrue(copy.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= bytesBound);
            assertEquals(copy, graph(m, new Limits(insns, blocks, edges, bytesBound)));
            assertThrows(IllegalArgumentException.class, () -> graph(m, new Limits(insns - 1, blocks, edges, bytesBound)));
            assertThrows(IllegalArgumentException.class, () -> graph(m, new Limits(insns, blocks - 1, edges, bytesBound)));
            assertThrows(IllegalArgumentException.class, () -> graph(m, new Limits(insns, blocks, edges - 1, bytesBound)));
            assertThrows(IllegalArgumentException.class, () -> graph(m, new Limits(insns, blocks, edges, bytesBound - 1)));
            int code = m.getCodeReader().getCodeOffset();
            byte[] bytes = Files.readAllBytes(dex);
            var truncated = new DexReader(1, "truncated.dex", bytes, 0);
            truncated.getBuf().limit(code + 17);
            var reader = new DexCodeReader(new SectionReader(truncated, code));
            assertThrows(RuntimeException.class, () -> reader.visitInstructions(i -> i.decode()));
            bytes[code + 16] = (byte) 0x73;
            var unknown = new DexCodeReader(new SectionReader(new DexReader(2, "unknown.dex", bytes, 0), code));
            assertThrows(IllegalArgumentException.class, () -> unknown.visitInstructions(i -> {
                if (((DexInsnData) i).getInsnInfo() == null) throw new IllegalArgumentException("Unknown opcode");
                i.decode();
            }));
            System.out.println("BOUNDS inclusive instructions=" + insns + " blocks=" + blocks + " edges=" + edges + " bytesBound=" + bytesBound
                    + "; each limit-1 rejected; truncated/unknown=FAILED");
        }
        Path oversized = HierarchyFixture.dex(dir, "oversized.dex", ".class public Lprobe/Oversized;\n.super Ljava/lang/Object;\n"
                + ".method public static big()V\n.registers 0\n" + "nop\n".repeat(4096) + "return-void\n.end method\n");
        try (var jadx = open(oversized)) {
            var m = method(JadxSymbolAdapter.visibleClass(jadx, "Lprobe/Oversized;", 0).getClassNode().getMethods(), "big");
            assertEquals(4097, m.getCodeReader().getUnitsCount());
            assertThrows(IllegalArgumentException.class, () -> input(m));
            assertNull(m.getBasicBlocks(), "Oversized reader facts rejected without starting processing");
        }
    }

    @Test void graphBoundariesDependOnModeWhileInputFactsDoNot() throws Exception {
        Path dex = fixture();
        List<InputInsn> baseline = null;
        for (var mode : DecompilationMode.values()) {
            var args = new JadxArgs(); args.setInputFiles(List.of(dex.toFile())); args.setDecompilationMode(mode);
            try (var jadx = new JadxDecompiler(args)) {
                jadx.load();
                var cls = JadxSymbolAdapter.visibleClass(jadx, "Lprobe/CfgCases;", 0);
                var m = method(cls.getClassNode().getMethods(), "packed");
                var facts = input(m);
                if (baseline == null) baseline = facts; else assertEquals(baseline, facts);
                var passes = jadx.getRoot().getPasses().stream().map(p -> p.getClass().getSimpleName()).toList();
                assertEquals(mode != DecompilationMode.FALLBACK, passes.contains("BlockSplitter"));
                boolean regions = mode == DecompilationMode.AUTO || mode == DecompilationMode.RESTRUCTURE;
                assertEquals(regions, passes.contains("BlockFinisher"));
                assertEquals(regions, passes.contains("RegionMakerVisitor"));
                cls.getCodeInfo();
                assertEquals(facts, input(m));
                assertNull(m.getBasicBlocks());
                assertTrue(jadx.getRoot().getErrorsCounter().getErrorNodes().isEmpty());
                System.out.println("MODE " + mode + " splitter=" + passes.contains("BlockSplitter") + " finisher=" + regions
                        + "; input facts unchanged; post-codegen blocks=null");
            }
        }
    }

    @Test void primaryLeaseAllowsReaderFactsButGraphProcessingIsNotAnObservationalRead() throws Exception {
        Path dex = fixture();
        byte[] original = Files.readAllBytes(dex);
        var runtime = new ProjectRuntime(null, List.of(dex), List.of(dir));
        try {
            runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
            var revisions = runtime.projectSnapshot();
            List<InputInsn> copied = runtime.withPrimarySymbolRead("cfg-probe", context -> {
                var cls = JadxSymbolAdapter.visibleClass(context.decompiler(), "Lprobe/CfgCases;", 0);
                var state = cls.getClassNode().getState();
                var m = method(cls.getClassNode().getMethods(), "branchLoop");
                var facts = input(m);
                assertEquals(state, cls.getClassNode().getState());
                assertNull(m.getBasicBlocks());
                assertThrows(ProjectBusyException.class, () -> runtime.withPrimarySymbolRead("different-owner", c -> null));
                return facts;
            });
            assertEquals(List.of(0, 1, 3, 5, 7, 8, 10, 12, 13), copied.stream().map(InputInsn::offset).toList());
            assertEquals(revisions, runtime.projectSnapshot());
            assertArrayEquals(original, Files.readAllBytes(dex));
            System.out.println("PRIMARY serialized reader facts preserve state/revisions/dirty/input; conflicting read=PROJECT_BUSY");
        } finally { runtime.close(); }
    }

    private static JadxDecompiler open(Path... paths) {
        var args = new JadxArgs(); args.setInputFiles(Arrays.stream(paths).map(Path::toFile).toList());
        var jadx = new JadxDecompiler(args); jadx.load(); return jadx;
    }

    private static MethodNode method(List<MethodNode> methods, String name) {
        return methods.stream().filter(m -> m.getMethodInfo().getName().equals(name)).findFirst().orElseThrow();
    }

    private record InputInsn(int offset, String opcode, Integer target, List<Integer> keys, List<Integer> targets) { }
    private record TryFact(int start, int inclusiveEnd, List<String> types, List<Integer> handlers, int catchall) { }

    private static List<InputInsn> input(MethodNode method) {
        var reader = assertInstanceOf(DexCodeReader.class, method.getCodeReader()).copy();
        if (reader.getUnitsCount() > 4096) throw new IllegalArgumentException("Input code units limit");
        List<InputInsn> result = new ArrayList<>();
        reader.visitInstructions(i -> {
            if (result.size() == 1024) throw new IllegalArgumentException("Input instruction limit");
            if (((DexInsnData) i).getInsnInfo() == null) throw new IllegalArgumentException("Unknown opcode");
            i.decode();
            var payload = i.getPayload();
            String opcode = i.getOpcode().name();
            boolean targetPresent = opcode.startsWith("IF_") || switch (i.getOpcode()) {
                case GOTO, PACKED_SWITCH, SPARSE_SWITCH, FILL_ARRAY_DATA -> true;
                default -> false;
            };
            // InsnData is reused; target is not reset for instructions without a target operand.
            result.add(new InputInsn(i.getOffset(), opcode, targetPresent ? i.getTarget() : null,
                    payload instanceof ISwitchPayload sw ? ints(sw.getKeys()) : List.of(),
                    payload instanceof ISwitchPayload sw ? ints(sw.getTargets()) : List.of()));
        });
        return List.copyOf(result);
    }

    private static List<TryFact> tries(MethodNode method) {
        return method.getCodeReader().copy().getTries().stream().map(t -> new TryFact(t.getStartOffset(), t.getEndOffset(),
                List.of(t.getCatch().getTypes()), ints(t.getCatch().getHandlers()), t.getCatch().getCatchAllHandler())).toList();
    }

    private static List<Integer> ints(int[] values) { return Arrays.stream(values).boxed().toList(); }
    private static InputInsn at(List<InputInsn> insns, int offset) {
        return insns.stream().filter(i -> i.offset() == offset).findFirst().orElseThrow();
    }
    private static void starts(Map<String, List<InputInsn>> inputs, String name, Integer... expected) {
        assertEquals(List.of(expected), inputs.get(name).stream().map(InputInsn::offset).toList(), name);
    }

    private static void capture(JadxDecompiler jadx, String pass, boolean before, Map<String, Graph> results) {
        var passes = jadx.getRoot().getPasses();
        int index = java.util.stream.IntStream.range(0, passes.size())
                .filter(i -> passes.get(i).getClass().getSimpleName().equals(pass)).findFirst().orElseThrow();
        passes.add(index + (before ? 0 : 1), new AbstractVisitor() {
            @Override public void visit(MethodNode method) {
                if (!method.isNoCode()) results.put(method.getMethodInfo().getName(), graph(method, Limits.DEFAULT));
            }
        });
    }

    private record Limits(int instructions, int blocks, int edges, long bytes) {
        static final Limits DEFAULT = new Limits(1024, 512, 2048, 2 * 1024 * 1024);
    }
    // Pure copied values: no BlockNode, MethodNode, instruction, input visitor, mutable array or collection escapes.
    private record Node(String id, int start, List<Integer> offsets, List<String> types, boolean synthetic) { }
    private record Graph(List<Node> nodes, List<String> edges, List<String> cleanEdges, List<String> temporaryEdges,
                         List<String> branches, List<String> switches, List<String> handlers) {
        List<Integer> offsets() { return nodes.stream().flatMap(n -> n.offsets().stream()).toList(); }
        List<String> types() { return nodes.stream().flatMap(n -> n.types().stream()).toList(); }
        long syntheticBlocks() { return nodes.stream().filter(Node::synthetic).count(); }
    }

    private static String id(BlockNode block) {
        if (block.contains(AFlag.MTH_ENTER_BLOCK)) return "ENTRY";
        if (block.contains(AFlag.MTH_EXIT_BLOCK)) return "EXIT";
        // For observations only: cId disambiguates synthetic blocks sharing the same offset.
        return block.contains(AFlag.SYNTHETIC) ? "S" + block.getCId() : Integer.toString(block.getStartOffset());
    }

    private static Graph graph(MethodNode method, Limits limits) {
        var blocks = method.getBasicBlocks();
        if (blocks == null) throw new IllegalArgumentException("Graph unavailable");
        long insns = 0, edges = 0;
        if (blocks.size() > limits.blocks()) throw new IllegalArgumentException("Block limit");
        for (var block : blocks) {
            insns += block.getInstructions().size(); edges += block.getSuccessors().size();
            if (insns > limits.instructions() || edges > limits.edges()) throw new IllegalArgumentException("Graph limit");
        }
        if (copyBound(method) > limits.bytes()) throw new IllegalArgumentException("Copied observation bytes limit");
        List<Node> nodes = new ArrayList<>();
        List<String> all = new ArrayList<>(), clean = new ArrayList<>(), tmp = new ArrayList<>(), branches = new ArrayList<>(), switches = new ArrayList<>();
        for (var b : blocks) {
            nodes.add(new Node(id(b), b.getStartOffset(), b.getInstructions().stream().map(i -> i.getOffset()).toList(),
                    b.getInstructions().stream().map(i -> i.getType().name()).toList(),
                    b.contains(AFlag.SYNTHETIC) || b.contains(AFlag.MTH_ENTER_BLOCK) || b.contains(AFlag.MTH_EXIT_BLOCK)));
            for (var to : b.getSuccessors()) all.add(id(b) + ">" + id(to));
            if (b.getCleanSuccessors() != null) for (var to : b.getCleanSuccessors()) clean.add(id(b) + ">" + id(to));
            var temp = b.get(AType.TMP_EDGE);
            if (temp != null) tmp.add(id(temp.getBlock()) + ">" + id(b));
            for (var i : b.getInstructions()) {
                if (i instanceof IfNode test) branches.add(i.getOffset() + ":" + test.getOp() + ":" + id(test.getThenBlock()) + ":" + id(test.getElseBlock()));
                if (i instanceof SwitchInsn sw) {
                    List<String> cases = new ArrayList<>();
                    for (int k = 0; k < sw.getKeys().length; k++) cases.add(sw.getKeys()[k] + ">" + id(sw.getTargetBlocks()[k]));
                    cases.add("default>" + id(sw.getDefTargetBlock()));
                    switches.add(i.getOffset() + ":" + String.join(",", cases));
                }
            }
        }
        List<String> handlers = new ArrayList<>();
        for (var h : method.getExceptionHandlers()) handlers.add(h.getHandlerOffset() + ":" + h.isCatchAll());
        return new Graph(List.copyOf(nodes), List.copyOf(all), List.copyOf(clean), List.copyOf(tmp), List.copyOf(branches), List.copyOf(switches), List.copyOf(handlers));
    }

    /** Conservative ASCII serialized-observation bound, not JVM heap or upstream processing memory. */
    private static long copyBound(MethodNode method) {
        long bytes = 512;
        for (var b : method.getBasicBlocks()) {
            bytes += 512L + 256L * b.getInstructions().size() + 128L * b.getSuccessors().size();
            for (var i : b.getInstructions()) if (i instanceof SwitchInsn sw) bytes += 64L * sw.getKeys().length;
        }
        for (var h : method.getExceptionHandlers()) bytes += 128;
        return bytes;
    }

    private static void assertEdges(Graph graph, String... expected) {
        assertNotNull(graph);
        assertEquals(new TreeSet<>(Set.of(expected)), new TreeSet<>(graph.edges()));
        assertEquals(expected.length, graph.edges().size(), "No duplicates hidden by set comparison");
    }
}
