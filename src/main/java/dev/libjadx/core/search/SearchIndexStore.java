package dev.libjadx.core.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import dev.libjadx.core.search.SearchDtos.Coverage;
import dev.libjadx.core.source.DecompileResult;
import dev.libjadx.core.symbols.ClassInfo;
import dev.libjadx.core.symbols.SymbolInfo;

/** Bounded process-local copied fragments. Jadx work is always outside this monitor. */
public final class SearchIndexStore {
	public enum FragmentState { PENDING, INDEXED, SKIPPED_WITH_VERIFIED_REASON, FAILED_WITH_BOUNDED_DIAGNOSTIC, EVICTED }
	public record FragmentStatus(FragmentState state, String reason) { }
	public record SourceFragment(String ownerDescriptor, String sourceSnapshotId, String source,
			DecompileResult.Status decompileStatus) { }
	public record View(SearchIndexKey key, long generation, List<ClassInfo> classes,
			Map<String, String> owners, Map<String, List<SymbolInfo>> members,
			Map<String, SourceFragment> sources, Map<String, FragmentStatus> memberStatus,
			Map<String, FragmentStatus> sourceStatus, Set<SearchQuery.Domain> unfinishedBuilds) {
		public List<Coverage> coverage(List<SearchQuery.Domain> domains) {
			List<Coverage> result = new ArrayList<>();
			for (SearchQuery.Domain domain : domains) {
				if (domain == SearchQuery.Domain.CLASS_NAME) {
					result.add(new Coverage(domain, "SUPPORTED", "COMPLETE", "JADX_VISIBLE_ELIGIBLE", "UNVERIFIED",
							classes.size(), classes.size(), 0, 0, 0, 0, List.of()));
					continue;
				}
				Map<String, FragmentStatus> statuses = domain == SearchQuery.Domain.MEMBER_NAME ? memberStatus : sourceStatus;
				int indexed = 0, pending = 0, skipped = 0, failed = 0, evicted = 0;
				List<String> diagnostics = new ArrayList<>();
				for (var entry : statuses.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
					switch (entry.getValue().state()) {
						case INDEXED -> indexed++;
						case PENDING -> pending++;
						case SKIPPED_WITH_VERIFIED_REASON -> skipped++;
						case FAILED_WITH_BOUNDED_DIAGNOSTIC -> failed++;
						case EVICTED -> evicted++;
					}
					if (entry.getValue().reason() != null && diagnostics.size() < 16)
						diagnostics.add(entry.getKey() + ": " + entry.getValue().reason());
				}
				String state = pending + failed + evicted == 0 && !unfinishedBuilds.contains(domain) ? "COMPLETE" : "PARTIAL";
				result.add(new Coverage(domain, "SUPPORTED", state, "JADX_VISIBLE_ELIGIBLE", "UNVERIFIED",
						statuses.size(), indexed, pending, skipped, failed, evicted, List.copyOf(diagnostics)));
			}
			return List.copyOf(result);
		}
	}

	private static final FragmentStatus PENDING = new FragmentStatus(FragmentState.PENDING, null);
	private static final FragmentStatus INDEXED = new FragmentStatus(FragmentState.INDEXED, null);
	private static final FragmentStatus EVICTED = new FragmentStatus(FragmentState.EVICTED, "Memory budget evicted fragment");
	private final Object lock = new Object();
	private final Supplier<SearchIndexKey> publication;
	private final long maxBytes;
	private SearchIndexKey key;
	private long generation;
	private List<ClassInfo> classes = List.of();
	private Map<String, String> owners = Map.of();
	private final Map<String, List<SymbolInfo>> members = new HashMap<>();
	private final Map<String, SourceFragment> sources = new HashMap<>();
	private final Map<String, FragmentStatus> memberStatus = new LinkedHashMap<>();
	private final Map<String, FragmentStatus> sourceStatus = new LinkedHashMap<>();
	private final LinkedHashMap<String, Long> lru = new LinkedHashMap<>(16, .75f, true);
	private long retainedBytes;
	private long catalogBytes;
	private final Set<SearchQuery.Domain> unfinishedBuilds = new LinkedHashSet<>();

	public SearchIndexStore(Supplier<SearchIndexKey> publication, long maxBytes) {
		if (maxBytes < 1024) throw new IllegalArgumentException("Search index budget is too small");
		this.publication = Objects.requireNonNull(publication);
		this.maxBytes = maxBytes;
	}

	public boolean initialized(SearchIndexKey expected) {
		synchronized (lock) { return expected != null && expected.equals(key); }
	}

	public boolean initialize(SearchIndexKey expected, List<ClassInfo> catalog, Map<String, String> sourceOwners) {
		if (!expected.equals(publication.get())) return false;
		long baseBytes = 256;
		for (ClassInfo info : catalog) baseBytes += 768L + textBytes(info.originalDottedName())
				+ textBytes(info.displayName()) + textBytes(info.displayQualifiedName())
				+ refBytes(info.ref()) + refBytes(info.originalParent());
		for (var entry : sourceOwners.entrySet()) baseBytes += 128L + textBytes(entry.getKey()) + textBytes(entry.getValue());
		if (baseBytes > maxBytes) throw new IndexLimitException();
		synchronized (lock) {
			if (!expected.equals(publication.get())) return false;
			if (expected.equals(key)) return true;
			key = expected;
			generation++;
			classes = List.copyOf(catalog);
			owners = Map.copyOf(sourceOwners);
			unfinishedBuilds.clear(); members.clear(); sources.clear(); memberStatus.clear(); sourceStatus.clear(); lru.clear(); catalogBytes = baseBytes; retainedBytes = catalogBytes;
			Set<String> seenOwners = new LinkedHashSet<>();
			for (ClassInfo info : catalog) {
				String descriptor = info.ref().originalClassDescriptor();
				memberStatus.put(descriptor, PENDING);
				seenOwners.add(sourceOwners.getOrDefault(descriptor, descriptor));
			}
			for (String owner : seenOwners) sourceStatus.put(owner, PENDING);
			for (ClassInfo info : catalog) {
				String descriptor = info.ref().originalClassDescriptor();
				if (descriptor.equals(sourceOwners.getOrDefault(descriptor, descriptor)) && !info.codeAvailable())
					sourceStatus.put(descriptor, new FragmentStatus(FragmentState.SKIPPED_WITH_VERIFIED_REASON,
							"Jadx JavaClass.isNoCode()"));
			}
			return true;
		}
	}

	public boolean publishMembers(SearchIndexKey expected, String descriptor, List<SymbolInfo> copied) {
		if (!expected.equals(publication.get())) return false;
		List<SymbolInfo> fragment = List.copyOf(copied);
		synchronized (lock) {
			if (!accept(expected) || !memberStatus.containsKey(descriptor)) return false;
			if (fragment.equals(members.get(descriptor)) && INDEXED.equals(memberStatus.get(descriptor))) {
				lru.get("M:" + descriptor);
				return true;
			}
			remove("M:" + descriptor);
			members.put(descriptor, fragment);
			memberStatus.put(descriptor, INDEXED);
			lru.put("M:" + descriptor, estimate(fragment));
			retainedBytes += estimate(fragment);
			generation++;
			evict();
			return true;
		}
	}

	public boolean publishSource(SearchIndexKey expected, SourceFragment fragment) {
		if (!expected.equals(publication.get())) return false;
		if (fragment.source() == null || fragment.source().length() * 2L > maxBytes || fragment.source().length() > 4_000_000)
			return failSource(expected, fragment.ownerDescriptor(), "Emitted owner Java exceeds source/index limit");
		synchronized (lock) {
			if (!accept(expected) || !sourceStatus.containsKey(fragment.ownerDescriptor())) return false;
			SourceFragment prior = sources.get(fragment.ownerDescriptor());
			FragmentStatus nextStatus = fragment.decompileStatus() == DecompileResult.Status.COMPLETE ? INDEXED
					: new FragmentStatus(FragmentState.FAILED_WITH_BOUNDED_DIAGNOSTIC,
							"Jadx emitted partial Java with recorded node errors");
			if (fragment.equals(prior) && nextStatus.equals(sourceStatus.get(fragment.ownerDescriptor()))) {
				lru.get("S:" + fragment.ownerDescriptor());
				return true;
			}
			remove("S:" + fragment.ownerDescriptor());
			sources.put(fragment.ownerDescriptor(), fragment);
			sourceStatus.put(fragment.ownerDescriptor(), nextStatus);
			long bytes = textBytes(fragment.source()) + textBytes(fragment.sourceSnapshotId())
					+ textBytes(fragment.ownerDescriptor()) + 256;
			lru.put("S:" + fragment.ownerDescriptor(), bytes);
			retainedBytes += bytes;
			generation++;
			evict();
			return true;
		}
	}

	public boolean failMembers(SearchIndexKey expected, String descriptor, String reason) {
		return mark(expected, memberStatus, descriptor, "M:" + descriptor, reason);
	}
	public boolean failSource(SearchIndexKey expected, String owner, String reason) {
		return mark(expected, sourceStatus, owner, "S:" + owner, reason);
	}
	public boolean skipSource(SearchIndexKey expected, String owner, String reason) {
		synchronized (lock) {
			if (!accept(expected) || !sourceStatus.containsKey(owner)) return false;
			remove("S:" + owner);
			sourceStatus.put(owner, new FragmentStatus(FragmentState.SKIPPED_WITH_VERIFIED_REASON,
					reason.length() > 120 ? reason.substring(0, 120) : reason));
			generation++;
			return true;
		}
	}
	private boolean mark(SearchIndexKey expected, Map<String, FragmentStatus> statuses, String descriptor,
			String fragmentKey, String reason) {
		synchronized (lock) {
			if (!accept(expected) || !statuses.containsKey(descriptor)) return false;
			remove(fragmentKey);
			statuses.put(descriptor, new FragmentStatus(FragmentState.FAILED_WITH_BOUNDED_DIAGNOSTIC,
					reason.length() > 120 ? reason.substring(0, 120) : reason));
			generation++;
			return true;
		}
	}

	public View view(SearchIndexKey expected) {
		synchronized (lock) {
			if (!accept(expected)) throw new StaleIndexException();
			return new View(key, generation, classes, owners, Map.copyOf(members), Map.copyOf(sources),
					Map.copyOf(memberStatus), Map.copyOf(sourceStatus), Set.copyOf(unfinishedBuilds));
		}
	}
	/** Remains incomplete on cancellation/failure until a later build verifies the domain. */
	public void beginBuild(SearchIndexKey expected, List<SearchQuery.Domain> domains) {
		synchronized (lock) {
			if (!accept(expected)) throw new StaleIndexException();
			if (unfinishedBuilds.addAll(domains)) generation++;
		}
	}
	public void finishBuild(SearchIndexKey expected, List<SearchQuery.Domain> domains) {
		synchronized (lock) {
			if (!accept(expected)) throw new StaleIndexException();
			if (unfinishedBuilds.removeAll(domains)) generation++;
		}
	}

	public void resetDomains(SearchIndexKey expected, List<SearchQuery.Domain> domains) {
		synchronized (lock) {
			if (!accept(expected)) throw new StaleIndexException();
			if (domains.contains(SearchQuery.Domain.MEMBER_NAME)) {
				for (String descriptor : memberStatus.keySet()) { remove("M:" + descriptor); memberStatus.put(descriptor, PENDING); }
			}
			if (domains.contains(SearchQuery.Domain.SOURCE_TEXT)) {
				for (String owner : sourceStatus.keySet()) {
					if (sourceStatus.get(owner).state() != FragmentState.SKIPPED_WITH_VERIFIED_REASON) {
						remove("S:" + owner); sourceStatus.put(owner, PENDING);
					}
				}
			}
			generation++;
		}
	}
	public long generation(SearchIndexKey expected) {
		synchronized (lock) { if (!accept(expected)) throw new StaleIndexException(); return generation; }
	}
	private boolean accept(SearchIndexKey expected) {
		return expected != null && expected.equals(key) && expected.equals(publication.get());
	}
	private void remove(String fragmentKey) {
		Long bytes = lru.remove(fragmentKey);
		if (bytes != null) retainedBytes -= bytes;
		if (fragmentKey.startsWith("M:")) members.remove(fragmentKey.substring(2));
		else sources.remove(fragmentKey.substring(2));
	}
	private void evict() {
		while (retainedBytes > maxBytes && !lru.isEmpty()) {
			String oldest = lru.keySet().iterator().next();
			remove(oldest);
			if (oldest.startsWith("M:")) memberStatus.put(oldest.substring(2), EVICTED);
			else sourceStatus.put(oldest.substring(2), EVICTED);
			generation++;
		}
	}
	private static long textBytes(String value) { return value == null ? 0 : 48L + 2L * value.length(); }
	private static long refBytes(dev.libjadx.core.symbols.SymbolRef ref) {
		return ref == null ? 0 : 64L + textBytes(ref.originalClassDescriptor()) + textBytes(ref.inputIdentity())
				+ textBytes(ref.originalName()) + textBytes(ref.originalDescriptor());
	}
	private static long estimate(List<SymbolInfo> fragment) {
		long bytes = 128;
		for (SymbolInfo info : fragment) bytes += 256L + textBytes(info.originalName())
				+ textBytes(info.originalDescriptor()) + textBytes(info.displayName()) + textBytes(info.displayQualifiedName())
				+ refBytes(info.ref()) + refBytes(info.containingClass());
		return bytes;
	}
	public static final class IndexLimitException extends RuntimeException {
		public IndexLimitException() { super("Search class catalog exceeds the index memory budget"); }
	}

	public static final class StaleIndexException extends RuntimeException { }
}
