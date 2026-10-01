package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import javax.tools.ToolProvider;

import dev.libjadx.core.source.SourceSnapshot;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.jadxadapter.JadxSymbolAdapter;
import dev.libjadx.probes.HierarchyFixture;
import dev.libjadx.project.NativeProjectDocument;
import jadx.api.*;
import jadx.api.data.impl.*;
import jadx.api.metadata.annotations.*;
import jadx.core.dex.attributes.AFlag;
import jadx.core.dex.instructions.args.SSAVar;

/** Test-only immutable diagnostics. Neither ranges, roles nor keys authorize edits. */
final class LocalIdentityDiagnostics {
    record Definition(int register, int ssa, boolean argument, boolean phi,
            Integer offset, String instruction, List<String> uses) { }
    record NativeConsumption(String shortId, int register, int ssa, boolean argument,
            String instruction, List<Definition> definitions) { }
    record Declaration(SymbolRef method, String shortId, int register, int ssa,
            String name, String type, int start, int end, String token, boolean argument,
            boolean exact, String snapshot, DecompilationMode mode, List<Definition> definitions,
            boolean uniqueSsaKey, boolean methodRegisterReuse, boolean methodPhi) {
        String key() { return shortId + ":" + register + ":" + ssa; }
        // Deliberately compares definition roles, not names, declaration order or key equality.
        List<String> roles() { return definitions.stream().map(d -> d.argument() + ":" + d.phi()
                + ":" + d.offset() + ":" + d.instruction() + ":" + d.uses()).sorted().toList(); }
    }
    record Snapshot(String source, List<Declaration> declarations) {
        List<Declaration> locals() { return declarations.stream().filter(d -> !d.argument()).toList(); }
        List<Declaration> named(String name) { return declarations.stream().filter(d -> name.equals(d.name())).toList(); }
    }

    static Path jvm(Path root) throws Exception {
        Path classes = Files.createDirectories(root.resolve("classes"));
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-g:none", "-d",
                classes.toString(), "tests/fixtures/variables/LocalShapes.java"));
        Path jar = root.resolve("locals.jar");
        try (var out = new JarOutputStream(Files.newOutputStream(jar)); var paths = Files.walk(classes)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                out.putNextEntry(new JarEntry(classes.relativize(path).toString().replace('\\', '/')));
                Files.copy(path, out); out.closeEntry();
            }
        }
        return jar;
    }

    static Path dex(Path root) throws Exception {
        return HierarchyFixture.dex(root, "locals.dex", Files.readString(Path.of("tests/fixtures/variables/LocalDex.smali")));
    }

    static JadxDecompiler open(Path input, JadxCodeData data, DecompilationMode mode) {
        return open(input, data, mode, "DEFAULT");
    }

    static JadxDecompiler open(List<Path> inputs, JadxCodeData data, DecompilationMode mode) {
        return open(inputs, data, mode, "DEFAULT", new ArrayList<>());
    }

    static JadxDecompiler open(Path input, JadxCodeData data, DecompilationMode mode, String settings) {
        return open(input, data, mode, settings, new ArrayList<>());
    }

    static JadxDecompiler open(Path input, JadxCodeData data, DecompilationMode mode, String settings,
            List<NativeConsumption> consumption) {
        return open(List.of(input), data, mode, settings, consumption);
    }

    static JadxDecompiler open(List<Path> inputs, JadxCodeData data, DecompilationMode mode, String settings,
            List<NativeConsumption> consumption) {
        var args = new JadxArgs(); args.setInputFiles(inputs.stream().map(Path::toFile).toList());
        args.setCodeData(NativeProjectDocument.copyCodeData(data)); args.setDecompilationMode(mode);
        args.setThreadsCount(2);
        switch (settings) {
            case "DEFAULT" -> { }
            case "NO_FINALLY" -> args.setExtractFinally(false);
            case "NO_INLINE" -> args.setInlineMethods(false);
            case "DX" -> args.setUseDxInput(true);
            default -> throw new IllegalArgumentException(settings);
        }
        var engine = new JadxDecompiler(args);
        try {
            engine.load(); engine.getClasses().forEach(c -> c.getClassNode().add(AFlag.DONT_UNLOAD_CLASS));
            // Test-only observation immediately after the actual consumer, before shrink/codegen.
            // Pinned RootNode ignores custom plugin passes in non-AUTO modes; insert into the
            // actual visitor list instead, without changing visitor order or any analysis state.
            var passes = engine.getRoot().getPasses();
            for (int i = 0; i < passes.size(); i++) {
                if (!passes.get(i).getName().equals("CodeRenameVisitor")) continue;
                passes.add(i + 1, new jadx.core.dex.visitors.AbstractVisitor() {
                    @Override public void visit(jadx.core.dex.nodes.MethodNode method) {
                        for (var rename : data.getRenames()) {
                            var key = rename.getCodeRef();
                            if (key == null || key.getAttachType() != jadx.api.data.CodeRefType.VAR
                                    || !rename.getNodeRef().getDeclaringClass().equals(method.getParentClass().getRawName())
                                    || !rename.getNodeRef().getShortId().equals(method.getMethodInfo().getShortId())) continue;
                            for (var variable : method.getSVars()) {
                                if (variable.getRegNum() != key.getIndex() >> 16 || variable.getVersion() != (key.getIndex() & 0xffff)) continue;
                                var instruction = variable.getAssignInsn();
                                consumption.add(new NativeConsumption(method.getMethodInfo().getShortId(), variable.getRegNum(),
                                        variable.getVersion(), method.getArgRegs().stream().anyMatch(a -> a.getSVar().getCodeVar() == variable.getCodeVar()),
                                        instruction == null ? "ARGUMENT" : instruction.toString(),
                                        variable.getCodeVar().getSsaVars().stream().map(LocalIdentityDiagnostics::definition).toList()));
                            }
                        }
                    }
                });
                break;
            }
            return engine;
        } catch (RuntimeException | Error failure) { engine.close(); throw failure; }
    }

    static Snapshot snapshot(JadxDecompiler engine, String owner) {
        JavaClass cls = engine.searchJavaClassByOrigFullName(owner);
        var info = cls.getCodeInfo(); String source = info.getCodeStr();
        var mode = engine.getArgs().getDecompilationMode();
        String snapshot = SourceSnapshot.id("local-probe", 0, 0, mode.name(), owner, source);
        List<Declaration> declarations = new ArrayList<>();
        for (var entry : info.getCodeMetadata().getAsMap().entrySet()) {
            if (!(entry.getValue() instanceof NodeDeclareRef ref) || !(ref.getNode() instanceof VarNode var)) continue;
            var method = var.getMth();
            var matches = method.getSVars().stream().filter(v -> v.getRegNum() == var.getReg() && v.getVersion() == var.getSsa()).toList();
            boolean argument = matches.size() == 1 && method.getArgRegs().stream()
                    .anyMatch(a -> a.getSVar().getCodeVar() == matches.getFirst().getCodeVar());
            var defs = matches.size() == 1 ? matches.getFirst().getCodeVar().getSsaVars().stream()
                    .map(LocalIdentityDiagnostics::definition).sorted(Comparator.comparing(Object::toString)).toList() : List.<Definition>of();
            int start = entry.getKey(), end = start + var.getName().length();
            boolean exact = start >= 0 && end <= source.length() && source.startsWith(var.getName(), start)
                    && (start == 0 || !Character.isJavaIdentifierPart(source.charAt(start - 1)))
                    && (end == source.length() || !Character.isJavaIdentifierPart(source.charAt(end)));
            declarations.add(new Declaration(JadxSymbolAdapter.originalRef(method.getMethodInfo()),
                    method.getMethodInfo().getShortId(), var.getReg(), var.getSsa(), var.getName(),
                    var.getType().toString(), start, end, exact ? source.substring(start, end) : "",
                    argument, exact, snapshot, mode, defs, matches.size() == 1,
                    method.getSVars().stream().collect(java.util.stream.Collectors.groupingBy(SSAVar::getRegNum))
                            .values().stream().anyMatch(v -> v.size() > 1),
                    method.getSVars().stream().anyMatch(v -> v.getAssignInsn() != null
                            && v.getAssignInsn().getType() == jadx.core.dex.instructions.InsnType.PHI)));
        }
        declarations.sort(Comparator.comparingInt(Declaration::start));
        return new Snapshot(source, List.copyOf(declarations));
    }

    static Definition definition(SSAVar var) {
        var insn = var.getAssignInsn();
        return new Definition(var.getRegNum(), var.getVersion(), var.getAssign().contains(AFlag.METHOD_ARGUMENT),
                insn != null && insn.getType() == jadx.core.dex.instructions.InsnType.PHI,
                insn == null ? null : insn.getOffset(), insn == null ? "ARGUMENT" : insn.getType().name(),
                var.getUseList().stream().map(u -> u.getParentInsn() == null ? "UNKNOWN"
                        : u.getParentInsn().getOffset() + ":" + u.getParentInsn().getType()).sorted().toList());
    }

    static JadxCodeData rename(Declaration target, String name) {
        var data = new JadxCodeData();
        data.setRenames(List.of(new JadxCodeRename(new JadxNodeRef(jadx.api.data.IJavaNodeRef.RefType.METHOD,
                target.method().originalClassDescriptor().substring(1, target.method().originalClassDescriptor().length() - 1).replace('/', '.'),
                target.shortId()), JadxCodeRef.forVar(target.register(), target.ssa()), name)));
        return data;
    }

    /** Only loader-generated temporary-path comments vary between fresh DX conversions.
     * Original snapshots/ranges remain untouched; this is a source-content comparison helper. */
    static String withoutConversionOrigin(String source) {
        return source.replaceAll("(?m)^/\\* JADX INFO: loaded from: /tmp/jadx-[0-9]+/classes\\.dex \\*/\\R", "");
    }

}
