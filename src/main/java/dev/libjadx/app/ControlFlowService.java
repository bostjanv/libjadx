package dev.libjadx.app;

import java.security.MessageDigest;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.libjadx.core.flow.ControlFlowFacts;
import dev.libjadx.core.symbols.*;
import dev.libjadx.jadxadapter.*;

/** One leased read, no cursor/cache and no processing passes. */
public final class ControlFlowService {
    private final ProjectRuntime runtime;
    private final SymbolCatalogProvider catalogs;
    private final ObjectMapper json = new ObjectMapper();
    public ControlFlowService(ProjectRuntime runtime, SymbolCatalogProvider catalogs) {
        this.runtime = runtime; this.catalogs = catalogs;
    }
    public record Request(SymbolRef ref, boolean strict, String expectedSessionId, Long expectedLogicalRevision) {
        public Request {
            Objects.requireNonNull(ref);
            if (ref.kind() != SymbolRef.Kind.METHOD) throw new IllegalArgumentException("METHOD required");
            if ((expectedSessionId == null) != (expectedLogicalRevision == null))
                throw new IllegalArgumentException("Preconditions must be paired");
            if (expectedSessionId != null && (!UUID.fromString(expectedSessionId).toString().equalsIgnoreCase(expectedSessionId)
                    || expectedLogicalRevision < 0)) throw new IllegalArgumentException("Invalid preconditions");
        }
    }
    public ControlFlowFacts query(Request request) {
        return runtime.withPrimarySymbolRead(request.ref().originalClassDescriptor(), context -> {
            var revisions = context.revisions();
            if (request.expectedSessionId() != null && (!request.expectedSessionId().equals(revisions.sessionId())
                    || request.expectedLogicalRevision() != revisions.logicalRevision())) throw new ControlFlowFacts.StaleException();
            var ref = request.ref();
            var resolution = SymbolLookup.resolve(catalogs.primary(context), ref, entry ->
                    JadxInstructionReferenceAdapter.matchingMembers(JadxSymbolAdapter.visibleClass(context.decompiler(),
                            ref.originalClassDescriptor(), entry.occurrence()), ref));
            var scan = JadxControlFlowAdapter.Scan.empty("UNAVAILABLE", "No unique loaded method was selected");
            boolean resolved = resolution.outcome() == SymbolResolution.Outcome.RESOLVED;
            if (resolved) scan = JadxControlFlowAdapter.extract(
                    JadxSymbolAdapter.visibleClass(context.decompiler(), ref.originalClassDescriptor(), 0), ref);
            String representation = "LOADED_DEX_CONTROL_FLOW_FACTS_V1";
            String snapshot = resolved ? hash(List.of(representation, ref, revisions.sessionId(), revisions.logicalRevision(),
                    revisions.persistedIdentity(), context.publicationEpoch(),
                    context.settings().fingerprint(), scan)) : null;
            var result = new ControlFlowFacts(resolution.outcome(), ref, resolution.candidates(), revisions.sessionId(),
                    revisions.logicalRevision(), context.publicationEpoch(), context.settings().fingerprint(), snapshot,
                    representation, "DEX_CODE_UNIT_16", "LOADED_METHOD_INSNS_START", scan.availability(),
                    scan.availability().equals("AVAILABLE") ? "EXACT" : "UNAVAILABLE", "PARTIAL", scan.reason(), scan.units(),
                    scan.instructions(), scan.payloads(), scan.branches(), scan.switches(), scan.arrayData(), scan.tries(),
                    List.of("Loaded DEX only; configured input/archive identity and conversion lineage are unverified",
                            "No blocks, feasible successor edges, exhaustive exception dispatch or Java correspondence",
                            "Current primary settings only; no Java generation, native edits or autosave"));
            try {
                if (json.writeValueAsBytes(result).length > 2 * 1024 * 1024) throw new ControlFlowFacts.LimitException();
            } catch (com.fasterxml.jackson.core.JsonProcessingException impossible) { throw new IllegalStateException(impossible); }
            if (resolved && request.strict()) throw new ControlFlowFacts.IncompleteException();
            return result;
        });
    }
    private String hash(Object fields) {
        try { return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(fields))); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
}
