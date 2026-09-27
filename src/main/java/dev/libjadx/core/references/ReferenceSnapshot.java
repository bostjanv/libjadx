package dev.libjadx.core.references;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import dev.libjadx.core.symbols.SymbolRef;

/** Immutable content-bound paging, with no Jadx objects or retained process cache. */
public final class ReferenceSnapshot {
    private static final String DOMAIN = "libjadx-references-v1";
    private static final Comparator<SymbolRef> REF_ORDER = Comparator.comparing((SymbolRef r) -> r.kind().name())
            .thenComparing(SymbolRef::originalClassDescriptor)
            .thenComparing(SymbolRef::inputIdentity, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(SymbolRef::originalName, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(SymbolRef::originalDescriptor, Comparator.nullsFirst(Comparator.naturalOrder()));
    private final List<ReferenceEdge> edges;
    private final String state;
    private final String queryHash;
    private final String id;
    private final byte[] key;
    private final Map<String, Integer> edgePositionByDigest;
    private final List<String> edgeDigests;

    public ReferenceSnapshot(List<ReferenceEdge> observed, ReferenceQuery query, String session, long revision,
            long epoch, String settings, byte[] key) {
        this.key = key.clone();
        this.edges = observed.stream().distinct().sorted(Comparator.comparing((ReferenceEdge e) -> e.relation().name())
                .thenComparing(ReferenceEdge::sourceRef, REF_ORDER).thenComparing(ReferenceEdge::targetRef, REF_ORDER)
                .thenComparing(e -> e.resolution().name()).thenComparing(ReferenceSnapshot::orderingKey)).toList();
        state = hash(List.of(DOMAIN, session, Long.toString(revision), Long.toString(epoch), settings));
        queryHash = queryHash(query);
        List<String> fields = new ArrayList<>(List.of(DOMAIN, state, queryHash));
        Map<String, Integer> positions = new HashMap<>();
        List<String> digests = new ArrayList<>(edges.size());
        for (int i = 0; i < edges.size(); i++) {
            String fullKey = orderingKey(edges.get(i));
            fields.add(fullKey); // Full site identity remains part of the content snapshot.
            String digest = edgeDigest(fullKey);
            digests.add(digest);
            if (positions.putIfAbsent(digest, i) != null) {
                throw new IllegalStateException("Reference edge digest collision");
            }
        }
        edgePositionByDigest = Map.copyOf(positions);
        edgeDigests = List.copyOf(digests);
        id = hash(fields);
    }
    public String id() { return id; }
    public List<ReferenceEdge> edges() { return edges; }
    public Slice page(ReferenceQuery query, Cursor cursor) {
        int from = 0;
        if (cursor != null) {
            if (!state.equals(cursor.state)) throw new StaleReferenceException();
            if (!queryHash.equals(cursor.queryHash)) throw new IllegalArgumentException("Cursor query options changed");
            if (!id.equals(cursor.snapshot)) throw new StaleReferenceException();
            Integer lastPosition = edgePositionByDigest.get(cursor.last);
            if (lastPosition == null) throw new IllegalArgumentException("Cursor edge is absent");
            from = lastPosition + 1;
        }
        int to = Math.min(edges.size(), from + query.pageSize());
        String next = to < edges.size() ? encode(new Cursor(state, queryHash, id, edgeDigests.get(to - 1))) : null;
        return new Slice(edges.subList(from, to), next);
    }
    public static Cursor authenticate(String token, byte[] key) {
        if (token == null) return null;
        try {
            if (token.length() > 4096) throw new IllegalArgumentException();
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            byte[] raw = Base64.getUrlDecoder().decode(parts[0]);
            byte[] signature = Base64.getUrlDecoder().decode(parts[1]);
            if (signature.length != 32 || !MessageDigest.isEqual(signature, mac(raw, key))) throw new IllegalArgumentException();
            // Only now interpret authenticated fields, including the endpoint domain.
            try (var input = new DataInputStream(new ByteArrayInputStream(raw))) {
                if (!DOMAIN.equals(read(input))) throw new IllegalArgumentException();
                Cursor cursor = new Cursor(read(input), read(input), read(input), read(input));
                if (input.available() != 0) throw new IllegalArgumentException();
                return cursor;
            }
        } catch (IOException | RuntimeException invalid) {
            throw new IllegalArgumentException("Malformed or incompatible reference cursor", invalid);
        }
    }
    private String encode(Cursor cursor) {
        byte[] raw = bytes(List.of(DOMAIN, cursor.state, cursor.queryHash, cursor.snapshot, cursor.last));
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw) + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(mac(raw, key));
        if (token.length() > 4096) throw new ReferenceLimitException();
        return token;
    }
    public static String orderingKey(ReferenceEdge edge) {
        List<String> fields = new ArrayList<>(List.of(edge.relation().name(), refKey(edge.sourceRef()), refKey(edge.targetRef()),
                edge.resolution().name(), edge.evidence().name(), edge.sourceSiteCoverage()));
        for (var site : edge.sourceSites()) fields.add(pack(List.of(refKey(site.sourceOwnerRef()), site.sourceSnapshotId(),
                Integer.toString(site.position().offsetUtf16()), Integer.toString(site.position().line()),
                Integer.toString(site.position().columnCodePoints()), site.precision())));
        return pack(fields);
    }
    public static String refKey(SymbolRef ref) {
        return pack(Arrays.asList(ref.kind().name(), ref.originalClassDescriptor(), ref.inputIdentity(), ref.originalName(), ref.originalDescriptor()));
    }
    private static String queryHash(ReferenceQuery q) {
        return hash(List.of(refKey(q.ref()), q.direction().name(), q.relations().toString(), Integer.toString(q.pageSize()),
                Boolean.toString(q.includeSourceSites()), Boolean.toString(q.strict())));
    }
    private static String pack(List<String> fields) {
        StringBuilder result = new StringBuilder();
        for (String field : fields) result.append(field == null ? -1 : field.length()).append(':').append(field == null ? "" : field);
        return result.toString();
    }
    private static byte[] bytes(List<String> fields) {
        try {
            var bytes = new ByteArrayOutputStream();
            var output = new DataOutputStream(bytes);
            for (String field : fields) {
                output.writeInt(field.length());
                for (int i = 0; i < field.length(); i++) output.writeChar(field.charAt(i));
            }
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String read(DataInputStream input) throws IOException {
        int length = input.readInt();
        if (length < 0 || length > input.available() / 2) throw new IOException("Invalid field size");
        StringBuilder field = new StringBuilder(length);
        for (int i = 0; i < length; i++) field.append(input.readChar());
        return field.toString();
    }
    private static String hash(List<String> fields) {
        try { return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes(fields))); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
    private static byte[] mac(byte[] raw, byte[] key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key, "HmacSHA256")); return mac.doFinal(raw);
        } catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
    private static String edgeDigest(String fullKey) {
        return hash(List.of("libjadx-reference-edge-v1", fullKey));
    }
    public record Cursor(String state, String queryHash, String snapshot, String last) { }
    public record Slice(List<ReferenceEdge> edges, String nextCursor) { public Slice { edges = List.copyOf(edges); } }
    public static final class StaleReferenceException extends RuntimeException { }
    public static final class ReferenceLimitException extends RuntimeException { }

    /** Budget is consumed before adding each copied output record; never silently truncate. */
    public static final class Budget {
        private int edges; private int sites; private long bytes;
        public void edge(SymbolRef source, SymbolRef target) {
            if (++edges > 10_000) throw new ReferenceLimitException();
            strings(refKey(source), refKey(target));
        }
        public void site(ReferenceEdge.Site site) {
            if (++sites > 20_000) throw new ReferenceLimitException();
            strings(refKey(site.sourceOwnerRef()), site.sourceSnapshotId(), site.precision());
        }
        private void strings(String... values) {
            for (String value : values) {
                // Conservative upper bound on UTF-8, including isolated surrogate escaping.
                bytes += (long) value.length() * 6;
                if (bytes > 4 * 1024 * 1024) throw new ReferenceLimitException();
            }
        }
    }
}
