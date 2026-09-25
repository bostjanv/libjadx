package dev.libjadx.core.symbols;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** One immutable Jadx-visible class enumeration; contains no live Jadx objects. */
public final class SymbolCatalog {
	private final String sessionId;
	private final long logicalRevision;
	private final long publicationEpoch;
	private final String snapshotId;
	private final List<Entry> entries;
	private final Map<String, List<Entry>> byDescriptor;
	private final byte[] cursorKey;

	public SymbolCatalog(List<ClassInfo> classes, String sessionId, long logicalRevision, long publicationEpoch,
			byte[] cursorKey) {
		this.sessionId = sessionId;
		this.logicalRevision = logicalRevision;
		this.publicationEpoch = publicationEpoch;
		this.snapshotId = sessionId + ":" + logicalRevision + ":" + publicationEpoch;
		this.cursorKey = cursorKey.clone();
		Map<String, Integer> ordinal = new HashMap<>();
		List<Entry> initial = new ArrayList<>(classes.size());
		for (ClassInfo info : classes) {
			String descriptor = info.ref().originalClassDescriptor();
			initial.add(new Entry(info, ordinal.merge(descriptor, 1, Integer::sum) - 1));
		}
		Map<String, Long> counts = new HashMap<>();
		initial.forEach(entry -> counts.merge(entry.info.ref().originalClassDescriptor(), 1L, Long::sum));
		initial.replaceAll(entry -> {
			ClassInfo info = entry.info;
			if (counts.get(info.ref().originalClassDescriptor()) == 1) return entry;
			return new Entry(new ClassInfo(info.ref(), info.originalDottedName(), info.displayName(),
					info.displayQualifiedName(), info.isInner(), info.codeAvailable(), info.originalParent(), SymbolInfo.Provenance.AMBIGUOUS),
					entry.occurrence);
		});
		initial.sort(Comparator.comparing((Entry e) -> e.info.ref().originalClassDescriptor())
				.thenComparingInt(Entry::occurrence));
		this.entries = List.copyOf(initial);
		Map<String, List<Entry>> grouped = new HashMap<>();
		for (Entry entry : entries) grouped.computeIfAbsent(entry.info.ref().originalClassDescriptor(), ignored -> new ArrayList<>()).add(entry);
		grouped.replaceAll((key, value) -> List.copyOf(value));
		this.byDescriptor = Map.copyOf(grouped);
	}

	public static byte[] newCursorKey() {
		byte[] key = new byte[32];
		new SecureRandom().nextBytes(key);
		return key;
	}

	public String snapshotId() { return snapshotId; }
	public String sessionId() { return sessionId; }
	public long logicalRevision() { return logicalRevision; }
	public long publicationEpoch() { return publicationEpoch; }
	public List<Entry> matching(String descriptor) { return byDescriptor.getOrDefault(descriptor, List.of()); }

	public ClassPage page(ClassQuery query) {
		Cursor start = query.cursor() == null ? null : decode(query.cursor(), query);
		List<ClassInfo> page = new ArrayList<>(query.pageSize());
		Entry last = null;
		boolean more = false;
		for (int i = start == null ? 0 : after(start); i < entries.size(); i++) {
			Entry entry = entries.get(i);
			ClassInfo info = entry.info();
			if (!query.includeInner() && info.isInner()) continue;
			if (query.packagePrefix() != null) {
				int dot = info.originalDottedName().lastIndexOf('.');
				String pkg = dot < 0 ? "" : info.originalDottedName().substring(0, dot);
				if (!pkg.equals(query.packagePrefix()) && !pkg.startsWith(query.packagePrefix() + ".")) continue;
			}
			if (query.nameContains() != null) {
				String name = query.nameDomain() == ClassQuery.NameDomain.original
						? info.originalDottedName() : info.displayQualifiedName();
				if (!name.contains(query.nameContains())) continue;
			}
			if (page.size() == query.pageSize()) { more = true; break; }
			page.add(info);
			last = entry;
		}
		return new ClassPage(sessionId, logicalRevision, snapshotId, "JADX_VISIBLE", "UNVERIFIED", page,
				more ? encode(query, last) : null, !more);
	}

	private int after(Cursor cursor) {
		int low = 0;
		int high = entries.size();
		while (low < high) {
			int mid = (low + high) >>> 1;
			if (compare(entries.get(mid), cursor) <= 0) low = mid + 1;
			else high = mid;
		}
		return low;
	}

	private static int compare(Entry entry, Cursor cursor) {
		int compared = entry.info.ref().originalClassDescriptor().compareTo(cursor.descriptor);
		return compared == 0 ? Integer.compare(entry.occurrence, cursor.occurrence) : compared;
	}

	private String encode(ClassQuery query, Entry last) {
		String payload = "1\0" + sessionId + "\0" + logicalRevision + "\0" + publicationEpoch + "\0"
				+ filterHash(query) + "\0" + last.info.ref().originalClassDescriptor() + "\0" + last.occurrence;
		byte[] raw = payload.getBytes(StandardCharsets.UTF_8);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw) + "."
				+ Base64.getUrlEncoder().withoutPadding().encodeToString(mac(raw));
	}

	private Cursor decode(String token, ClassQuery query) {
		try {
			String[] parts = token.split("\\.", -1);
			if (parts.length != 2) throw new IllegalArgumentException("Malformed cursor");
			byte[] raw = Base64.getUrlDecoder().decode(parts[0]);
			byte[] signature = Base64.getUrlDecoder().decode(parts[1]);
			String[] fields = new String(raw, StandardCharsets.UTF_8).split("\0", -1);
			if (fields.length != 7 || !"1".equals(fields[0])) throw new IllegalArgumentException("Malformed cursor");
			long revision = Long.parseLong(fields[2]);
			long epoch = Long.parseLong(fields[3]);
			int occurrence = Integer.parseInt(fields[6]);
			if (!fields[1].equals(sessionId) || revision != logicalRevision || epoch != publicationEpoch) {
				throw new StaleCursorException();
			}
			if (!MessageDigest.isEqual(signature, mac(raw)) || !fields[4].equals(filterHash(query))
					|| occurrence < 0 || occurrence > 100_000) throw new IllegalArgumentException("Invalid cursor");
			SymbolRef.validateClassDescriptor(fields[5]);
			return new Cursor(fields[5], occurrence);
		} catch (StaleCursorException stale) {
			throw stale;
		} catch (RuntimeException invalid) {
			throw new IllegalArgumentException("Malformed or incompatible cursor", invalid);
		}
	}

	private byte[] mac(byte[] raw) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(cursorKey, "HmacSHA256"));
			return mac.doFinal(raw);
		} catch (Exception impossible) {
			throw new IllegalStateException("HMAC unavailable", impossible);
		}
	}

	private static String filterHash(ClassQuery query) {
		try {
			String normalized = query.pageSize() + "\0" + value(query.packagePrefix()) + "\0"
					+ value(query.nameContains()) + "\0" + query.nameDomain() + "\0" + query.includeInner();
			return Base64.getUrlEncoder().withoutPadding().encodeToString(
					MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception impossible) {
			throw new IllegalStateException("SHA-256 unavailable", impossible);
		}
	}

	private static String value(String text) { return text == null ? "" : text; }
	public record Entry(ClassInfo info, int occurrence) { }
	private record Cursor(String descriptor, int occurrence) { }
	public static final class StaleCursorException extends RuntimeException { }
}
