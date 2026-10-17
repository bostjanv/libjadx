package dev.libjadx.jadxadapter;

import java.util.*;
import dev.libjadx.core.references.*;
import dev.libjadx.core.symbols.*;
import jadx.api.JavaClass;
import jadx.core.dex.info.FieldInfo;
import jadx.core.dex.info.MethodInfo;
import jadx.core.dex.nodes.MethodNode;
import jadx.plugins.input.dex.insns.DexInsnData;
import jadx.plugins.input.dex.sections.DexClassData;
import jadx.plugins.input.dex.sections.DexCodeReader;

/** Pinned 1.5.6 input-reader observations, never transformed IR or Java/Smali positions. */
public final class JadxInstructionReferenceAdapter {
    private JadxInstructionReferenceAdapter() { }

    /** JavaClass.getMethods() generates Java; raw method identities must not depend on that succeeding. */
    public static List<SymbolInfo> matchingMembers(JavaClass cls, SymbolRef ref) {
        return cls.getClassNode().getMethods().stream().filter(m -> ref.equals(JadxSymbolAdapter.originalRef(m.getMethodInfo())))
                .map(m -> new SymbolInfo(ref, ref.originalName(), ref.originalDescriptor(), m.getMethodInfo().getAlias(),
                        cls.getFullName() + '.' + m.getMethodInfo().getAlias(), SymbolRef.classRef(ref.originalClassDescriptor()),
                        SymbolInfo.Provenance.UNAVAILABLE)).toList();
    }

    public record Scan(List<ReferenceEdge> edges, ReferencePage.InstructionScan status) {
        public Scan { edges = List.copyOf(edges); }
    }

    public static Scan extract(JavaClass cls, SymbolRef source) {
        if (!(cls.getClassNode().getClsData() instanceof DexClassData))
            return new Scan(List.of(), new ReferencePage.InstructionScan("UNAVAILABLE", "Class is not backed by a loaded DEX reader", List.of()));
        MethodNode method = cls.getClassNode().getMethods().stream()
                .filter(m -> source.equals(JadxSymbolAdapter.originalRef(m.getMethodInfo()))).findFirst().orElseThrow();
        if (!(method.getCodeReader() instanceof DexCodeReader original))
            return new Scan(List.of(), new ReferencePage.InstructionScan("UNAVAILABLE", "Method has no loaded DEX code reader", List.of()));
        List<ReferenceEdge> edges = new ArrayList<>();
        Set<String> excluded = new TreeSet<>();
        var budget = new ReferenceSnapshot.Budget();
        int[] next = {0};
        try {
            DexCodeReader reader = original.copy();
            int units = reader.getUnitsCount();
            if (units < 0) throw new IllegalArgumentException("Invalid method size");
            if (units > 1_000_000) throw new ReferenceSnapshot.ReferenceLimitException();
            reader.visitInstructions(raw -> {
                DexInsnData insn = (DexInsnData) raw;
                if (insn.getInsnInfo() == null || insn.getOffset() != next[0])
                    throw new IllegalArgumentException("Unknown opcode or noncontiguous traversal");
                // Skip variable-size payloads without allocating arrays from untrusted payload counts.
                if (insn.getLength() < 0) {
                    insn.setLength(insn.getRawOpcodeUnit() == 0x300 ? 4 : 2);
                    var header = java.nio.ByteBuffer.wrap(insn.getByteCode()).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                    int count = Short.toUnsignedInt(header.getShort(2));
                    long payloadUnits = switch (insn.getRawOpcodeUnit()) {
                        case 0x100 -> 4L + 2L * count;
                        case 0x200 -> 2L + 4L * count;
                        case 0x300 -> {
                            if (count != 1 && count != 2 && count != 4 && count != 8)
                                throw new IllegalArgumentException("Invalid array element width");
                            yield 4 + (Integer.toUnsignedLong(header.getInt(4)) * count + 1) / 2;
                        }
                        default -> throw new IllegalArgumentException("Unknown payload");
                    };
                    if (payloadUnits > units - next[0]) throw new IllegalArgumentException("Payload crosses method boundary");
                    reader.skip(insn);
                    insn.setDecoded(true);
                } else insn.decode();
                int length = insn.getLength();
                if (length <= 0 || length > units - next[0])
                    throw new IllegalArgumentException("Instruction crosses method boundary");
                // Fixed zero-operand instructions may otherwise never read their remaining bytes.
                insn.getByteCode();
                next[0] += length;
                int opcode = insn.getRawOpcodeUnit() & 0xff;
                if (opcode >= 0xfa && opcode <= 0xff) {
                    excluded.add(insn.getOpcodeMnemonic());
                    return;
                }
                SymbolRef target;
                ReferenceQuery.Relation relation;
                boolean resolved;
                if (opcode >= 0x6e && opcode <= 0x72 || opcode >= 0x74 && opcode <= 0x78) {
                    var encoded = insn.getIndexAsMethod(); encoded.load();
                    target = new SymbolRef(SymbolRef.Kind.METHOD, encoded.getParentClassType(), null, encoded.getName(),
                            "(" + String.join("", encoded.getArgTypes()) + ")" + encoded.getReturnType());
                    resolved = method.root().resolveMethod(MethodInfo.fromRef(method.root(), encoded)) != null;
                    relation = resolved ? ReferenceQuery.Relation.CALL : ReferenceQuery.Relation.UNRESOLVED_CALL;
                } else if (opcode >= 0x52 && opcode <= 0x6d) {
                    var encoded = insn.getIndexAsField();
                    target = new SymbolRef(SymbolRef.Kind.FIELD, encoded.getParentClassType(), null, encoded.getName(), encoded.getType());
                    resolved = method.root().resolveField(FieldInfo.fromRef(method.root(), encoded)) != null;
                    relation = opcode <= 0x58 || opcode >= 0x60 && opcode <= 0x66
                            ? ReferenceQuery.Relation.FIELD_READ : ReferenceQuery.Relation.FIELD_WRITE;
                } else return; // Type/string occurrences and non-reference instructions are outside this view.
                budget.edge(source, target);
                edges.add(new ReferenceEdge(source, target, relation,
                        resolved ? ReferenceEdge.Resolution.RESOLVED : ReferenceEdge.Resolution.UNRESOLVED,
                        ReferenceEdge.Evidence.LOADED_DEX_INPUT_READER, List.of(), "UNAVAILABLE", null,
                        new ReferenceEdge.Instruction(insn.getOffset(), "DEX_CODE_UNIT_16", "LOADED_METHOD_INSNS_START", "EXACT",
                                insn.getOpcodeMnemonic())));
            });
            if (next[0] != units) throw new IllegalArgumentException("Incomplete method traversal");
            return new Scan(edges, new ReferencePage.InstructionScan("AVAILABLE",
                    "Loaded DEX traversal finished; only ordinary invokes and field accesses are reported", List.copyOf(excluded)));
        } catch (ReferenceSnapshot.ReferenceLimitException limit) {
            throw limit;
        } catch (RuntimeException failure) {
            return new Scan(edges, new ReferencePage.InstructionScan("FAILED",
                    "Loaded DEX scan failed at code-unit offset " + next[0] + "; observations before failure may be retained", List.copyOf(excluded)));
        }
    }
}
