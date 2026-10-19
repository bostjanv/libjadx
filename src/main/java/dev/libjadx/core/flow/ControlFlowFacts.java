package dev.libjadx.core.flow;

import java.util.List;
import dev.libjadx.core.symbols.*;

/** Immutable loaded-reader values, never blocks, inferred edges or original-input addresses. */
public record ControlFlowFacts(SymbolResolution.Outcome outcome, SymbolRef queriedRef, List<SymbolInfo> candidates,
        String sessionId, long logicalRevision, long publicationEpoch, String effectiveSettingsHash, String snapshotId,
        String representation, String unit, String base, String availability, String precision, String coverage,
        String reason, Integer codeUnits, List<Instruction> instructions, List<Payload> payloads, List<Branch> branches,
        List<Switch> switches, List<ArrayData> arrayData, List<Try> tries, List<String> diagnostics) {
    public ControlFlowFacts {
        candidates = List.copyOf(candidates); instructions = List.copyOf(instructions); payloads = List.copyOf(payloads);
        branches = List.copyOf(branches); switches = List.copyOf(switches); arrayData = List.copyOf(arrayData);
        tries = List.copyOf(tries); diagnostics = List.copyOf(diagnostics);
    }
    public record Instruction(int offset, int length, String opcode) { }
    public record Payload(int offset, int length, String kind) { }
    public record Branch(int offset, String opcode, int target, int nextOffset) { }
    public record Case(int key, int target) { }
    public record Switch(int offset, String opcode, int payloadOffset, int defaultOffset, List<Case> cases) {
        public Switch { cases = List.copyOf(cases); }
    }
    public record ArrayData(int offset, int payloadOffset) { }
    public record Handler(String type, int target) { }
    public record Try(int start, int endExclusive, List<Handler> handlers) {
        public Try { handlers = List.copyOf(handlers); }
    }
    public static final class LimitException extends RuntimeException { }
    public static final class StaleException extends RuntimeException { }
    public static final class IncompleteException extends RuntimeException { }
}
