package dev.libjadx.core.references;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import dev.libjadx.core.symbols.SymbolRef;

public record ReferenceQuery(SymbolRef ref, Direction direction, List<Relation> relations, int pageSize,
        String cursor, boolean includeSourceSites, boolean strict, String expectedSessionId, Long expectedLogicalRevision) {
    public enum Direction { INCOMING, OUTGOING }
    public enum Relation { CALL, UNRESOLVED_CALL, FIELD_USE, CLASS_DEPENDENCY }
    public ReferenceQuery {
        Objects.requireNonNull(ref); Objects.requireNonNull(direction);
        if (ref.kind() == SymbolRef.Kind.FIELD && direction != Direction.INCOMING)
            throw new IllegalArgumentException("FIELD only supports INCOMING");
        List<Relation> allowed = switch (ref.kind()) {
            case CLASS -> List.of(Relation.CLASS_DEPENDENCY);
            case FIELD -> List.of(Relation.FIELD_USE);
            case METHOD -> direction == Direction.INCOMING ? List.of(Relation.CALL) : List.of(Relation.CALL, Relation.UNRESOLVED_CALL);
        };
        if (relations == null) relations = allowed;
        if (relations.isEmpty() || relations.size() > 4 || !allowed.containsAll(relations)
                || relations.stream().distinct().count() != relations.size())
            throw new IllegalArgumentException("Invalid or repeated relations for symbol/direction");
        relations = relations.stream().sorted().toList();
        if (pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("pageSize must be 1..100");
        if (cursor != null && (cursor.isEmpty() || cursor.length() > 4096)) throw new IllegalArgumentException("Invalid cursor length");
        if ((expectedSessionId == null) != (expectedLogicalRevision == null))
            throw new IllegalArgumentException("Revision preconditions must be supplied together");
        if (expectedSessionId != null && (!UUID.fromString(expectedSessionId).toString().equalsIgnoreCase(expectedSessionId)
                || expectedLogicalRevision < 0)) throw new IllegalArgumentException("Invalid revision precondition");
    }
}
