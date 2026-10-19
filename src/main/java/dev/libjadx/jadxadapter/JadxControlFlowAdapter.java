package dev.libjadx.jadxadapter;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import dev.libjadx.core.flow.ControlFlowFacts.*;
import dev.libjadx.core.symbols.SymbolRef;
import jadx.api.JavaClass;
import jadx.plugins.input.dex.DexException;
import jadx.plugins.input.dex.insns.DexInsnData;
import jadx.plugins.input.dex.sections.DexClassData;
import jadx.plugins.input.dex.sections.DexCodeReader;

/** Jadx 1.5.6 / 28ff15e4: bounded code_item operands, not transformed instructions. */
public final class JadxControlFlowAdapter {
    private JadxControlFlowAdapter() { }

    public record Scan(String availability, String reason, Integer units, List<Instruction> instructions,
            List<Payload> payloads, List<Branch> branches, List<Switch> switches, List<ArrayData> arrayData, List<Try> tries) {
        public Scan {
            instructions = List.copyOf(instructions); payloads = List.copyOf(payloads); branches = List.copyOf(branches);
            switches = List.copyOf(switches); arrayData = List.copyOf(arrayData); tries = List.copyOf(tries);
        }
        public static Scan empty(String availability, String reason) {
            return new Scan(availability, reason, null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }
    }

    public static Scan extract(JavaClass cls, SymbolRef ref) {
        if (!(cls.getClassNode().getClsData() instanceof DexClassData))
            return Scan.empty("UNAVAILABLE", "Class is not backed by loaded DEX");
        var method = cls.getClassNode().getMethods().stream()
                .filter(m -> ref.equals(JadxSymbolAdapter.originalRef(m.getMethodInfo()))).findFirst().orElseThrow();
        if (!(method.getCodeReader() instanceof DexCodeReader original))
            return Scan.empty("UNAVAILABLE", "Method has no loaded DEX code reader");
        try {
            return new Decoder(original.copy()).scan();
        } catch (Malformed | BufferUnderflowException | IndexOutOfBoundsException | DexException malformed) {
            return Scan.empty("FAILED", "Malformed loaded DEX code, payload, target or try table; no facts published");
        }
    }

    private static final class Malformed extends RuntimeException { }
    private static void require(boolean valid) { if (!valid) throw new Malformed(); }
    private static void limit(long count, int max) { if (count > max) throw new LimitException(); }

    private static final class Decoder {
        final DexCodeReader reader;
        final List<Instruction> instructions = new ArrayList<>();
        final List<Payload> payloads = new ArrayList<>();
        final List<Branch> branches = new ArrayList<>();
        final List<Switch> switches = new ArrayList<>();
        final List<ArrayData> arrays = new ArrayList<>();
        final Map<Integer, byte[]> payloadBytes = new HashMap<>();
        final Set<Integer> starts = new HashSet<>();
        final List<int[]> payloadUses = new ArrayList<>();
        DexInsnData access;
        int units, next, records, cases;
        Decoder(DexCodeReader reader) { this.reader = reader; }

        Scan scan() {
            try { units = reader.getUnitsCount(); }
            catch (IllegalArgumentException invalidHeaderOffset) { throw new Malformed(); }
            limit(Integer.toUnsignedLong(units), 4096); require(units > 0);
            reader.visitInstructions(raw -> {
                DexInsnData insn = (DexInsnData) raw;
                access = insn;
                if (next == 0) limit(u16(reader.getCodeOffset() + 6), 256);
                require(insn.getInsnInfo() != null && insn.getOffset() == next);
                limit(++records, 1024);
                int offset = next, length = insn.getLength(), op = insn.getRawOpcodeUnit();
                if (length < 0) {
                    require(offset % 2 == 0);
                    int headerUnits = op == 0x300 ? 4 : op == 0x100 ? 4 : 2;
                    require(headerUnits <= units - offset);
                    insn.setLength(headerUnits);
                    ByteBuffer header = le(insn.getByteCode());
                    int count = Short.toUnsignedInt(header.getShort(2));
                    long extent;
                    String kind;
                    if (op == 0x100 || op == 0x200) {
                        cases += count; limit(cases, 1024);
                        extent = op == 0x100 ? 4L + count * 2L : 2L + count * 4L;
                        kind = op == 0x100 ? "PACKED_SWITCH" : "SPARSE_SWITCH";
                    } else {
                        require(op == 0x300 && (count == 1 || count == 2 || count == 4 || count == 8));
                        extent = 4L + (Integer.toUnsignedLong(header.getInt(4)) * count + 1) / 2;
                        kind = "ARRAY_DATA";
                    }
                    require(extent <= units - offset);
                    length = (int) extent;
                    insn.setLength(length);
                    byte[] bytes = insn.getByteCode(); // Validate remaining bytes before upstream skip; never decode payload arrays.
                    payloads.add(new Payload(offset, length, kind));
                    payloadBytes.put(offset, bytes);
                } else {
                    require(length > 0 && length <= units - offset);
                    ByteBuffer bytes = le(insn.getByteCode());
                    int opcode = op & 0xff;
                    require(opcode != 0 || op == 0); // Unknown pseudo-opcodes are not ordinary NOPs.
                    String mnemonic = insn.getOpcodeMnemonic();
                    instructions.add(new Instruction(offset, length, mnemonic));
                    starts.add(offset);
                    if (opcode >= 0x28 && opcode <= 0x2a || opcode >= 0x32 && opcode <= 0x3d) {
                        long delta = opcode == 0x28 ? (byte) (op >>> 8) : opcode == 0x2a ? bytes.getInt(2) : bytes.getShort(2);
                        branches.add(new Branch(offset, mnemonic, destination(offset, delta), offset + length));
                    } else if (opcode == 0x26 || opcode == 0x2b || opcode == 0x2c) {
                        payloadUses.add(new int[]{offset, opcode, destination(offset, bytes.getInt(2))});
                    }
                }
                // Both fixed and variable skips are now bounded by checked extents. No reused target field is read.
                reader.skip(insn);
                insn.setDecoded(true);
                next += length;
            });
            require(next == units && starts.contains(0));
            for (Branch b : branches) {
                executable(b.target());
                if (!b.opcode().startsWith("goto")) executable(b.nextOffset());
            }
            int usedCases = 0;
            for (int[] use : payloadUses) {
                int offset = use[0], op = use[1], payload = use[2];
                byte[] bytes = payloadBytes.get(payload);
                require(bytes != null);
                ByteBuffer data = le(bytes);
                require(Short.toUnsignedInt(data.getShort(0)) == (op == 0x26 ? 0x300 : op == 0x2b ? 0x100 : 0x200));
                if (op == 0x26) { arrays.add(new ArrayData(offset, payload)); continue; }
                executable(offset + 3);
                int count = Short.toUnsignedInt(data.getShort(2));
                usedCases += count; limit(usedCases, 1024); // Includes repeated uses of a shared payload.
                List<Case> entries = new ArrayList<>(count);
                long previous = Long.MIN_VALUE;
                for (int i = 0; i < count; i++) {
                    long key = op == 0x2b ? (long) data.getInt(4) + i : data.getInt(4 + i * 4);
                    require(key >= Integer.MIN_VALUE && key <= Integer.MAX_VALUE && key > previous);
                    previous = key;
                    int target = destination(offset, data.getInt(op == 0x2b ? 8 + i * 4 : 4 + count * 4 + i * 4));
                    executable(target); entries.add(new Case((int) key, target));
                }
                switches.add(new Switch(offset, op == 0x2b ? "packed-switch" : "sparse-switch", payload, offset + 3, entries));
            }
            List<Try> tries = tries();
            return new Scan("AVAILABLE", "Complete bounded loaded-reader traversal; semantic coverage remains PARTIAL", units,
                    instructions, payloads, branches, switches, arrays, tries);
        }

        int destination(int offset, long delta) {
            long target = offset + delta;
            require(target >= 0 && target < units);
            return (int) target;
        }
        void executable(int offset) { require(starts.contains(offset)); }

        List<Try> tries() {
            int code = reader.getCodeOffset();
            int count = u16(code + 6); limit(count, 256);
            if (count == 0) return List.of();
            long tableLong = (long) code + 16 + units * 2L + (units % 2) * 2;
            require(tableLong <= Integer.MAX_VALUE - count * 8L);
            int table = (int) tableLong, handlersStart = table + count * 8;
            // Validate all fixed try bytes before variable table decode/allocation.
            bytes(table, count * 8);
            int[] pos = {handlersStart};
            long lists = leb(pos, false); limit(lists, 1024);
            Map<Integer, List<Handler>> handlers = new HashMap<>();
            int total = 0;
            for (int i = 0; i < lists; i++) {
                int relative = pos[0] - handlersStart;
                long signed = leb(pos, true), typed = Math.abs(signed);
                long entries = typed + (signed <= 0 ? 1 : 0);
                limit(total + entries, 1024); total += (int) entries;
                int minimumBytes = (int) (typed * 2 + (signed <= 0 ? 1 : 0));
                bytes(pos[0] & ~1, (minimumBytes + (pos[0] & 1) + 1) & ~1);
                List<Handler> values = new ArrayList<>((int) entries);
                for (int h = 0; h < typed; h++) {
                    long type = leb(pos, false);
                    String name = type(type);
                    long target = leb(pos, false); require(target < units); executable((int) target);
                    values.add(new Handler(name, (int) target));
                }
                if (signed <= 0) {
                    long target = leb(pos, false); require(target < units); executable((int) target);
                    values.add(new Handler(null, (int) target));
                }
                handlers.put(relative, List.copyOf(values));
            }
            List<Try> result = new ArrayList<>(count);
            int aggregate = 0, previousEnd = 0;
            for (int i = 0; i < count; i++) {
                int p = table + i * 8;
                long start = u32(p), end = start + u16(p + 4);
                require(start < end && end <= units && start >= previousEnd);
                executable((int) start);
                require(end == units || starts.contains((int) end) || payloadBytes.containsKey((int) end));
                for (Payload payload : payloads) require(payload.offset() >= end || payload.offset() + payload.length() <= start);
                List<Handler> values = handlers.get(u16(p + 6)); require(values != null);
                aggregate += values.size(); limit(aggregate, 1024);
                result.add(new Try((int) start, (int) end, values)); previousEnd = (int) end;
            }
            return result;
        }

        String type(long index) {
            long count = u32(64), table = u32(68);
            require(index < count && table + index * 4 <= Integer.MAX_VALUE);
            long string = u32((int) (table + index * 4));
            long strings = u32(56), stringTable = u32(60);
            require(string < strings && stringTable + string * 4 <= Integer.MAX_VALUE);
            long offset = u32((int) (stringTable + string * 4)); require(offset <= Integer.MAX_VALUE);
            int[] p = {(int) offset}; long chars = leb(p, false); limit(chars, 1024);
            int start = p[0];
            while (u8(p[0]) != 0) { require(p[0] - start < chars * 3); p[0]++; }
            // Only after bounded string preflight use pinned MUTF-8/type decoding.
            int old = access.getIndex();
            try {
                access.setIndex((int) index);
                String name = access.getIndexAsType();
                require(name != null && name.length() == chars && name.startsWith("L") && name.endsWith(";"));
                return name;
            } finally { access.setIndex(old); }
        }

        long leb(int[] pos, boolean signed) {
            long value = 0; int shift = 0, b;
            do {
                require(shift < 35);
                b = u8(pos[0]++); value |= (long) (b & 0x7f) << shift; shift += 7;
            } while ((b & 0x80) != 0);
            if (signed && (b & 0x40) != 0) value |= -1L << shift;
            require(signed ? value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE : value <= 0xffff_ffffL);
            return value;
        }
        int u8(int offset) { require(offset >= 0); return bytes(offset & ~1, 2)[offset & 1] & 0xff; }
        int u16(int offset) { return Short.toUnsignedInt(le(bytes(offset, 2)).getShort()); }
        long u32(int offset) { return Integer.toUnsignedLong(le(bytes(offset, 4)).getInt()); }

        /** Public pinned byte-copy accessor; temporarily retarget only the borrowed visitor, restoring it immediately. */
        byte[] bytes(int offset, int length) {
            require(offset >= 0 && length >= 0 && (length & 1) == 0 && (long) offset + length <= Integer.MAX_VALUE);
            int oldStart = access.getFileOffset(), oldLength = access.getLength();
            try {
                access.setInsnStart(offset); access.setLength(length / 2);
                return access.getByteCode();
            } catch (IllegalArgumentException invalidOffset) {
                throw new Malformed();
            } finally { access.setInsnStart(oldStart); access.setLength(oldLength); }
        }
    }
    private static ByteBuffer le(byte[] bytes) { return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN); }
}
