package dev.libjadx.app;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;
import dev.libjadx.core.search.SearchDtos;
import dev.libjadx.core.search.SearchDtos.Coverage;
import dev.libjadx.core.search.SearchDtos.Hit;
import dev.libjadx.core.search.SearchDtos.Page;
import dev.libjadx.core.search.SearchIndexKey;
import dev.libjadx.core.search.SearchIndexStore;
import dev.libjadx.core.search.SearchQuery;
import dev.libjadx.core.source.DecompileResult;
import dev.libjadx.core.source.SourceCoordinates;
import dev.libjadx.core.symbols.ClassInfo;
import dev.libjadx.core.symbols.SymbolInfo;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.jadxadapter.JadxSearchAdapter;
import dev.libjadx.jadxadapter.JadxSourceAdapter;
import dev.libjadx.jadxadapter.JadxSymbolAdapter;
import dev.libjadx.scheduler.JobRegistry;
import dev.libjadx.scheduler.JobSnapshot;
import dev.libjadx.scheduler.JobSpec;
import dev.libjadx.scheduler.OperationCoordinator;
import dev.libjadx.scheduler.OperationRequest;
import jadx.api.JavaClass;
import jadx.api.JadxDecompiler;

/** Bounded primary-engine search and complete-index job. All retained values are copied. */
public final class SearchService {
	private static final int MAX_HITS = 5_000;
	private static final int MAX_HIT_BYTES = 4 * 1024 * 1024;
	private static final long CURSOR_TTL_NANOS = Duration.ofMinutes(10).toNanos();
	private static final long RESULT_BUDGET = 16L * 1024 * 1024;
	private static final String CURSOR_DOMAIN = "libjadx-search-v1";
	private static final Object BUILD_NEEDED = new Object();
	private final ProjectRuntime runtime;
	private final SymbolCatalogProvider catalogs;
	private final SearchIndexStore store;
	private final byte[] cursorKey;
	private final BuildBoundary buildBoundary;
	private final SourceExtractor sourceExtractor;
	private final java.util.function.LongSupplier nanoClock;
	private final Object resultLock = new Object();
	private final LinkedHashMap<String, ResultSnapshot> results = new LinkedHashMap<>(16, .75f, true);
	private long resultBytes;
	private final Object buildLock = new Object();
	private SearchIndexKey buildIdentity;
	private final Map<String, UUID> builds = new LinkedHashMap<>();

	public SearchService(ProjectRuntime runtime, SymbolCatalogProvider catalogs, byte[] cursorKey) {
		this(runtime, catalogs, cursorKey, processed -> { }, SearchService::extractSource);
	}

	SearchService(ProjectRuntime runtime, SymbolCatalogProvider catalogs, byte[] cursorKey, BuildBoundary boundary) {
		this(runtime, catalogs, cursorKey, boundary, SearchService::extractSource);
	}

	SearchService(ProjectRuntime runtime, SymbolCatalogProvider catalogs, byte[] cursorKey,
			BuildBoundary boundary, SourceExtractor extractor) {
		this(runtime, catalogs, cursorKey, boundary, extractor, System::nanoTime);
	}

	SearchService(ProjectRuntime runtime, SymbolCatalogProvider catalogs, byte[] cursorKey,
			BuildBoundary boundary, SourceExtractor extractor, java.util.function.LongSupplier nanoClock) {
		this(runtime, catalogs, cursorKey, boundary, extractor, nanoClock, 64L * 1024 * 1024);
	}

	SearchService(ProjectRuntime runtime, SymbolCatalogProvider catalogs, byte[] cursorKey,
			BuildBoundary boundary, SourceExtractor extractor, java.util.function.LongSupplier nanoClock, long indexBudget) {
		this.nanoClock = nanoClock;
		this.runtime = runtime;
		this.catalogs = catalogs;
		this.cursorKey = cursorKey.clone();
		this.buildBoundary = boundary;
		this.sourceExtractor = extractor;
		this.store = new SearchIndexStore(runtime::searchIdentity, indexBudget);
	}

	@FunctionalInterface interface BuildBoundary { void afterClass(int processed); }
	@FunctionalInterface interface SourceExtractor {
		JadxSourceAdapter.SourceData extract(JadxDecompiler jadx, JavaClass owner, SymbolRef ref, SearchIndexKey key);
	}
	static JadxSourceAdapter.SourceData extractSource(JadxDecompiler jadx, JavaClass owner,
			SymbolRef ref, SearchIndexKey key) {
		return JadxSourceAdapter.extract(jadx, owner, ref, false, key.sessionId(), key.logicalRevision(),
				key.publicationEpoch(), key.settingsFingerprint());
	}

	public void ingest(ProjectRuntime.PrimarySymbolRead context, DecompileResult result, List<SymbolInfo> members) {
		SearchIndexKey key = key(context);
		if (!store.initialized(key)) initialize(context);
		if (result.outcome() != dev.libjadx.core.symbols.SymbolResolution.Outcome.RESOLVED) return;
		String declaring = result.queriedRef().originalClassDescriptor();
		store.publishMembers(key, declaring, members);
		if (result.source() != null && result.sourceOwnerRef() != null && result.sourceSnapshotId() != null)
			store.publishSource(key, new SearchIndexStore.SourceFragment(result.sourceOwnerRef().originalClassDescriptor(),
					result.sourceSnapshotId(), result.source(), result.status()));
		else if (result.sourceOwnerRef() != null)
			store.failSource(key, result.sourceOwnerRef().originalClassDescriptor(), "Jadx source unavailable");
	}

	private SearchIndexKey key(ProjectRuntime.PrimarySymbolRead context) {
		return new SearchIndexKey(context.revisions().sessionId(), context.revisions().logicalRevision(),
				context.publicationEpoch(), context.settings().fingerprint());
	}

	private void initialize(ProjectRuntime.PrimarySymbolRead context) {
		store.initialize(key(context), catalogs.primary(context).entries().stream().map(entry -> entry.info()).toList(),
				JadxSearchAdapter.originalSourceOwners(context.decompiler()));
	}

	private void ensureCatalog() {
		SearchIndexKey identity = runtime.searchIdentity();
		if (store.initialized(identity)) return;
		runtime.withPrimarySymbolRead("search-catalog", context -> { initialize(context); return null; });
	}

	/** Returns either a SearchPage or an existing/new JobSnapshot for requireComplete. */
	public Object query(SearchQuery query) {
		Cursor cursor = query.cursor() == null ? null : authenticate(query.cursor());
		runtime.assertReady();
		Pattern compiledRegex = compileRegex(query);
		ensureCatalog();
		if (query.requireComplete() && cursor == null) {
			JobSnapshot running = runningBuild(query);
			if (running != null) return running;
		}
		Object result = runtime.withSearchSnapshotRead(key -> {
			if (query.expectedSessionId() != null && (!query.expectedSessionId().equals(key.sessionId())
					|| query.expectedLogicalRevision() != key.logicalRevision())) throw new StaleSearchException("Search precondition is stale");
			SearchIndexStore.View view = store.view(key);
			List<Coverage> coverage = view.coverage(query.domains());
			if (cursor != null) return pageFromCursor(query, cursor, view);
			List<String> incomplete = coverage.stream().filter(c -> !"COMPLETE".equals(c.state()))
					.map(c -> c.domain().name()).toList();
			if (!incomplete.isEmpty() && query.requireComplete()) return BUILD_NEEDED;
			if (!incomplete.isEmpty() && query.strict()) throw new IncompleteSearchException(incomplete, coverage);
			List<Hit> hits = materialize(view, query, compiledRegex);
			ResultSnapshot snapshot = retain(view, query, coverage, hits);
			return page(snapshot, query.pageSize(), 0);
		});
		return result == BUILD_NEEDED ? buildIndex(query.domains(), false) : result;
	}

	/** Inspect only copied index/job metadata; the running build holds the incompatible engine lease. */
	private JobSnapshot runningBuild(SearchQuery query) {
		synchronized (buildLock) {
			SearchIndexKey key = runtime.searchIdentity();
			if (key == null) throw new SearchIndexStore.StaleIndexException();
			if (query.expectedSessionId() != null && (!query.expectedSessionId().equals(key.sessionId())
					|| query.expectedLogicalRevision() != key.logicalRevision()))
				throw new StaleSearchException("Search precondition is stale");
			if (!key.equals(buildIdentity)) return null;
			UUID id = builds.get(flight(key, query.domains()));
			if (id == null) return null;
			try {
				JobSnapshot job = runtime.jobRegistry().snapshot(id);
				if (job.terminal() || store.view(key).coverage(query.domains()).stream()
						.allMatch(c -> "COMPLETE".equals(c.state()))) return null;
				if (!key.equals(runtime.searchIdentity())) throw new SearchIndexStore.StaleIndexException();
				return job;
			} catch (java.util.NoSuchElementException expired) { return null; }
		}
	}

	private static String flight(SearchIndexKey key, List<SearchQuery.Domain> domains) {
		return key.snapshotId() + ":" + domains.stream().sorted().toList();
	}

	/** Returns current status or a real CLASS_READ job. */
	public Object buildIndex(List<SearchQuery.Domain> domains, boolean force) {
		if (domains.contains(SearchQuery.Domain.STRING_LITERAL))
			throw new SearchQuery.UnsupportedDomainException("STRING_LITERAL lexical ownership is not verified");
		runtime.assertReady();
		ensureCatalog();
		SearchIndexKey key = runtime.searchIdentity();
		if (key == null) throw new SearchIndexStore.StaleIndexException();
		SearchIndexStore.View view = store.view(key);
		List<Coverage> coverage = view.coverage(domains);
		if (!force && coverage.stream().allMatch(c -> "COMPLETE".equals(c.state()))) return status(view, domains);
		String flight = flight(key, domains);
		synchronized (buildLock) {
			if (!key.equals(buildIdentity)) { builds.clear(); buildIdentity = key; }
			UUID activeBuildId = builds.get(flight);
			if (activeBuildId != null) {
				try {
					JobSnapshot existing = runtime.jobRegistry().snapshot(activeBuildId);
					if (!existing.terminal()) return existing;
					if (!force && existing.state() == JobSnapshot.State.SUCCEEDED
							&& existing.completeness() == JobSpec.Completeness.PARTIAL) {
						List<String> missing = coverage.stream().filter(c -> !"COMPLETE".equals(c.state()))
								.map(c -> c.domain().name()).toList();
						throw new IncompleteSearchException(missing, coverage);
					}
				} catch (java.util.NoSuchElementException expired) { /* Submit again. */ }
			}
			runtime.assertSearchBuildAvailable();
			JobSpec<ProjectRuntime.PrimarySymbolRead> spec = new JobSpec<>("search-build-index",
					OperationRequest.classRead("search-build-index"),
					new OperationCoordinator.Admission(key.sessionId(), key.logicalRevision()),
					key.snapshotId(), Duration.ofMinutes(30), () -> runtime.captureSearchRead(key),
					(context, captured) -> runBuild(context, captured, key, domains, force));
			JobSnapshot submitted = runtime.submitJob(spec);
			builds.put(flight, submitted.jobId());
			return submitted;
		}
	}

	private JobSpec.JobResult runBuild(JobSpec.JobContext job, ProjectRuntime.PrimarySymbolRead captured,
			SearchIndexKey expected, List<SearchQuery.Domain> domains, boolean force) {
		dev.libjadx.testing.ReleaseTestHooks.gate("job");
		dev.libjadx.testing.ReleaseTestHooks.fail("job");
		job.cancellation().throwIfCancellationRequested();
		if (!expected.equals(runtime.searchIdentity())) throw new JobRegistry.StaleJobSnapshotException();
		if (force) store.resetDomains(expected, domains);
		store.beginBuild(expected, domains);
		List<JavaClass> classes = List.copyOf(captured.decompiler().getClassesWithInners());
		Set<String> visitedOwners = new HashSet<>();
		Map<String, JavaClass> sourceOwners = new LinkedHashMap<>();
		Map<String, String> emittedSources = new LinkedHashMap<>();
		int completed = 0;
		job.progress(0, (long) classes.size(), "INDEXING_CLASSES");
		for (JavaClass cls : classes) {
			job.cancellation().throwIfCancellationRequested();
			if (!expected.equals(runtime.searchIdentity())) throw new JobRegistry.StaleJobSnapshotException();
			SymbolRef ref = JadxSymbolAdapter.originalRef(cls);
			String descriptor = ref.originalClassDescriptor();
			if (domains.contains(SearchQuery.Domain.MEMBER_NAME)) {
				try {
					var members = JadxSearchAdapter.members(cls);
					job.cancellation().throwIfCancellationRequested();
					store.publishMembers(expected, descriptor, members);
				} catch (RuntimeException failure) {
					job.cancellation().throwIfCancellationRequested();
					store.failMembers(expected, descriptor, "Pinned Jadx member extraction failed: " + failure.getClass().getSimpleName());
				}
			}
			if (domains.contains(SearchQuery.Domain.SOURCE_TEXT)) {
				String owner = JadxSymbolAdapter.originalRef(cls.getOriginalTopParentClass()).originalClassDescriptor();
				if (visitedOwners.add(owner)) {
					try {
						JavaClass outer = cls.getOriginalTopParentClass();
						sourceOwners.put(owner, outer);
						if (outer.isNoCode()) store.skipSource(expected, owner, "Jadx JavaClass.isNoCode()");
						else {
							var data = sourceExtractor.extract(captured.decompiler(), outer,
									SymbolRef.classRef(owner), expected);
							job.cancellation().throwIfCancellationRequested();
							if (data.source() == null || data.ownerRef() == null) {
								if (outer.isNoCode() || outer.getAccessInfo().isSynthetic()
										&& outer.getMethods().isEmpty() && outer.getFields().isEmpty())
									store.skipSource(expected, owner, "Jadx suppressed a verified empty synthetic class");
								else {
									store.failSource(expected, owner, "Pinned Jadx emitted source unavailable");
								}
							} else {
								store.publishSource(expected, new SearchIndexStore.SourceFragment(
										data.ownerRef().originalClassDescriptor(), data.sourceSnapshotId(), data.source(), data.status()));
								emittedSources.put(owner, data.sourceSnapshotId());
							}
						}
					} catch (RuntimeException failure) {
						job.cancellation().throwIfCancellationRequested();
						store.failSource(expected, owner, "Pinned Jadx source extraction failed: " + failure.getClass().getSimpleName());
					}
				}
			}
			completed++;
			job.progress(completed, (long) classes.size(), "INDEXING_CLASSES");
			buildBoundary.afterClass(completed);
		}
		job.cancellation().throwIfCancellationRequested();
		// Later Jadx class processing can alter an earlier emitted owner. A bounded
		// second observation and stability check prevent claiming that first text as final.
		if (domains.contains(SearchQuery.Domain.SOURCE_TEXT)) {
			for (int pass = 0; pass < 2; pass++) {
				for (var entry : sourceOwners.entrySet()) {
					job.cancellation().throwIfCancellationRequested();
					String owner = entry.getKey();
					if (!emittedSources.containsKey(owner)) continue;
					try {
						var data = sourceExtractor.extract(captured.decompiler(), entry.getValue(),
								SymbolRef.classRef(owner), expected);
						job.cancellation().throwIfCancellationRequested();
						if (data.source() == null || data.ownerRef() == null) {
							store.failSource(expected, owner, "Owner Java unavailable on consistency pass");
							emittedSources.remove(owner);
						} else if (pass == 1 && !data.sourceSnapshotId().equals(emittedSources.get(owner))) {
							store.failSource(expected, owner, "Owner Java changed after bounded consistency pass");
						} else {
							store.publishSource(expected, new SearchIndexStore.SourceFragment(
									data.ownerRef().originalClassDescriptor(), data.sourceSnapshotId(), data.source(), data.status()));
							emittedSources.put(owner, data.sourceSnapshotId());
						}
					} catch (RuntimeException failure) {
						job.cancellation().throwIfCancellationRequested();
						store.failSource(expected, owner,
								"Owner consistency check failed: " + failure.getClass().getSimpleName());
						emittedSources.remove(owner);
					}
				}
			}
		}
		job.cancellation().throwIfCancellationRequested();
		store.finishBuild(expected, domains);
		SearchIndexStore.View finalView = store.view(expected);
		List<Coverage> coverage = finalView.coverage(domains);
		boolean complete = coverage.stream().allMatch(c -> "COMPLETE".equals(c.state()));
		int memberFailures = coverage.stream().filter(c -> c.domain() == SearchQuery.Domain.MEMBER_NAME)
				.mapToInt(Coverage::failed).sum();
		int sourceFailures = coverage.stream().filter(c -> c.domain() == SearchQuery.Domain.SOURCE_TEXT)
				.mapToInt(Coverage::failed).sum();
		String summary = "{\"processed\":" + completed + ",\"eligible\":" + classes.size()
				+ ",\"memberFailures\":" + memberFailures + ",\"sourceFailures\":" + sourceFailures
				+ ",\"indexGeneration\":" + finalView.generation() + ",\"coverage\":\""
				+ (complete ? "COMPLETE" : "PARTIAL") + "\"}";
		return new JobSpec.JobResult(summary, complete ? JobSpec.Completeness.COMPLETE : JobSpec.Completeness.PARTIAL);
	}

	private SearchDtos.IndexStatus status(SearchIndexStore.View view, List<SearchQuery.Domain> domains) {
		SearchIndexKey key = view.key();
		return new SearchDtos.IndexStatus(key.sessionId(), key.logicalRevision(), key.settingsFingerprint(),
				key.snapshotId(), view.generation(), view.coverage(domains));
	}

	private static Pattern compileRegex(SearchQuery query) {
		if (query.matchMode() != SearchQuery.MatchMode.REGEX) return null;
		if (query.query().length() > 256) throw new SearchLimitException("REGEX expression exceeds 256 characters");
		try { return Pattern.compile(query.query(), query.caseSensitive() ? 0 : Pattern.CASE_INSENSITIVE); }
		catch (PatternSyntaxException unsupported) { throw new UnsupportedRegexException(); }
	}

	private List<Hit> materialize(SearchIndexStore.View view, SearchQuery query, Pattern regex) {
		Pattern sourcePattern = regex != null ? regex
				: query.caseSensitive() ? null : Pattern.compile(Pattern.quote(query.query()), Pattern.CASE_INSENSITIVE);
		List<Hit> found = new ArrayList<>();
		Set<String> unique = new HashSet<>();
		int bytes = 0;
		for (SearchQuery.Domain domain : query.domains()) {
			if (domain == SearchQuery.Domain.CLASS_NAME) {
				for (ClassInfo info : view.classes()) {
					String matched = firstMatch(query, info.originalDottedName(), info.ref().originalClassDescriptor(),
							info.displayQualifiedName());
					if (matched == null) continue;
					String id = "C:" + info.ref().originalClassDescriptor();
					if (unique.add(id)) bytes = add(found, new Hit(domain, info.ref(), null, info.displayQualifiedName(),
							bounded(matched), query.matchMode(), null, null), bytes);
				}
			} else if (domain == SearchQuery.Domain.MEMBER_NAME) {
				for (List<SymbolInfo> fragment : view.members().values()) for (SymbolInfo info : fragment) {
					String matched = firstMatch(query, info.originalName(), info.originalDescriptor(),
							info.originalName() + info.originalDescriptor(), info.displayName(), info.displayQualifiedName());
					if (matched == null) continue;
					String id = "M:" + info.ref();
					if (unique.add(id)) bytes = add(found, new Hit(domain, info.ref(), null, info.displayQualifiedName(),
							bounded(matched), query.matchMode(), null, null), bytes);
				}
			} else if (domain == SearchQuery.Domain.SOURCE_TEXT) {
				for (SearchIndexStore.SourceFragment fragment : view.sources().values()) {
					String source = fragment.source();
					SourceCoordinates coordinates = new SourceCoordinates(source);
					SymbolRef owner = SymbolRef.classRef(fragment.ownerDescriptor());
					if (sourcePattern != null) {
						Matcher matcher = sourcePattern.matcher(source);
						while (matcher.find()) {
							int start = matcher.start(), end = matcher.end();
							if (start == end || !coordinates.isBoundary(start) || !coordinates.isBoundary(end)
									|| query.matchMode() == SearchQuery.MatchMode.EXACT && !tokenBoundaries(source, start, end)) continue;
							bytes = add(found, new Hit(domain, null, owner, null, bounded(source, start, end),
									query.matchMode(), coordinates.range(start, end, fragment.sourceSnapshotId()),
									fragment.sourceSnapshotId()), bytes);
						}
					} else {
						int length = query.query().length();
						for (int start = source.indexOf(query.query()); start >= 0;
								start = source.indexOf(query.query(), start + length)) {
							int end = start + length;
							if (!coordinates.isBoundary(start) || !coordinates.isBoundary(end)
									|| query.matchMode() == SearchQuery.MatchMode.EXACT && !tokenBoundaries(source, start, end)) continue;
							bytes = add(found, new Hit(domain, null, owner, null, bounded(source, start, end),
									query.matchMode(), coordinates.range(start, end, fragment.sourceSnapshotId()),
									fragment.sourceSnapshotId()), bytes);
						}
					}
				}
			}
		}
		found.sort(Comparator.comparing((Hit h) -> h.domain().name())
				.thenComparing(h -> h.ref() == null ? h.sourceOwnerRef().originalClassDescriptor() : h.ref().originalClassDescriptor())
				.thenComparing(h -> h.ref() == null ? "" : Objects.toString(h.ref().originalName(), ""))
				.thenComparing(h -> h.ref() == null ? "" : Objects.toString(h.ref().originalDescriptor(), ""))
				.thenComparing(h -> Objects.toString(h.sourceSnapshotId(), ""))
				.thenComparingInt(h -> h.range() == null ? -1 : h.range().startOffsetUtf16())
				.thenComparingInt(h -> h.range() == null ? -1 : h.range().endOffsetUtf16())
				.thenComparing(h -> Objects.toString(h.displayName(), "")));
		return List.copyOf(found);
	}
	private static boolean tokenBoundaries(String source, int start, int end) {
		return (start == 0 || !Character.isJavaIdentifierPart(source.codePointBefore(start)))
				&& (end == source.length() || !Character.isJavaIdentifierPart(source.codePointAt(end)));
	}

	private static int add(List<Hit> hits, Hit hit, int currentBytes) {
		long estimate = hitBytes(hit);
		long next = currentBytes + estimate;
		if (hits.size() >= MAX_HITS || next > MAX_HIT_BYTES)
			throw new SearchLimitException("Search would exceed 5000 matches or 4 MiB; narrow the query");
		hits.add(hit);
		return (int) next;
	}
	private static String bounded(String value) { return bounded(value, 0, value.length()); }
	private static String bounded(String value, int start, int end) {
		int limit = Math.min(end, start + 160);
		if (limit < end && limit > start && Character.isHighSurrogate(value.charAt(limit - 1))
				&& Character.isLowSurrogate(value.charAt(limit))) limit--;
		return value.substring(start, limit);
	}
	private static long stringBytes(String value) { return value == null ? 0 : 48L + 6L * value.length(); }
	private static long refBytes(SymbolRef ref) {
		return ref == null ? 0 : 64L + stringBytes(ref.originalClassDescriptor()) + stringBytes(ref.inputIdentity())
				+ stringBytes(ref.originalName()) + stringBytes(ref.originalDescriptor());
	}
	private static long hitBytes(Hit hit) {
		return 512L + refBytes(hit.ref()) + refBytes(hit.sourceOwnerRef()) + stringBytes(hit.displayName())
				+ stringBytes(hit.matchedText()) + stringBytes(hit.sourceSnapshotId());
	}
	private static String firstMatch(SearchQuery query, String... values) {
		for (String value : values) {
			if (value == null) continue;
			if (query.matchMode() == SearchQuery.MatchMode.EXACT
					&& (query.caseSensitive() ? value.equals(query.query()) : value.equalsIgnoreCase(query.query()))) return value;
			if (query.matchMode() == SearchQuery.MatchMode.CONTAINS) {
				if (query.caseSensitive() ? value.contains(query.query())
						: value.toLowerCase(Locale.ROOT).contains(query.query().toLowerCase(Locale.ROOT))) return value;
			}
		}
		return null;
	}

	private ResultSnapshot retain(SearchIndexStore.View view, SearchQuery query, List<Coverage> coverage, List<Hit> hits) {
		String hash = hash(view.key().snapshotId() + "\0" + view.generation() + "\0" + queryHash(query)
				+ "\0" + hits.toString());
		String id = UUID.randomUUID().toString();
		ResultSnapshot snapshot = new ResultSnapshot(id, hash, view.key(), view.generation(), queryHash(query),
				List.copyOf(coverage), hits, nanoClock.getAsLong(), 1024L + hits.stream().mapToLong(SearchService::hitBytes).sum()
				+ coverage.stream().flatMap(c -> c.diagnostics().stream()).mapToLong(SearchService::stringBytes).sum());
		synchronized (resultLock) {
			results.entrySet().removeIf(entry -> {
				boolean obsolete = !entry.getValue().key.equals(view.key())
						|| entry.getValue().generation != view.generation()
						|| nanoClock.getAsLong() - entry.getValue().createdAt > CURSOR_TTL_NANOS;
				if (obsolete) resultBytes -= entry.getValue().bytes;
				return obsolete;
			});
			while (!results.isEmpty() && (results.size() >= 256 || resultBytes + snapshot.bytes > RESULT_BUDGET)) {
				ResultSnapshot evicted = results.remove(results.keySet().iterator().next());
				resultBytes -= evicted.bytes;
			}
			if (snapshot.bytes > RESULT_BUDGET) throw new SearchLimitException("Search result snapshot exceeds retention budget");
			results.put(id, snapshot);
			resultBytes += snapshot.bytes;
		}
		return snapshot;
	}

	private Page pageFromCursor(SearchQuery query, Cursor cursor, SearchIndexStore.View view) {
		if (!cursor.queryHash.equals(queryHash(query))) throw new IllegalArgumentException("Search cursor options changed");
		if (!cursor.keyHash.equals(hash(view.key().snapshotId())) || cursor.generation != view.generation())
			throw new StaleSearchException("Search index generation is stale");
		ResultSnapshot snapshot;
		synchronized (resultLock) {
			snapshot = results.get(cursor.storeId);
			if (snapshot == null || nanoClock.getAsLong() - snapshot.createdAt > CURSOR_TTL_NANOS) {
				if (snapshot != null) { results.remove(cursor.storeId); resultBytes -= snapshot.bytes; }
				throw new StaleSearchException("SEARCH_SNAPSHOT_EVICTED");
			}
		}
		if (!snapshot.resultHash.equals(cursor.resultHash) || !snapshot.key.equals(view.key())
				|| snapshot.generation != view.generation()) throw new StaleSearchException("Search result snapshot is stale");
		if (cursor.position < 1 || cursor.position >= snapshot.hits.size()) throw new IllegalArgumentException("Invalid search cursor position");
		return page(snapshot, query.pageSize(), cursor.position);
	}

	private Page page(ResultSnapshot snapshot, int pageSize, int from) {
		int to = Math.min(snapshot.hits.size(), from + pageSize);
		String next = to < snapshot.hits.size() ? encode(new Cursor(hash(snapshot.key.snapshotId()),
				snapshot.generation, snapshot.queryHash, snapshot.resultHash, snapshot.storeId, to)) : null;
		return new Page(snapshot.key.sessionId(), snapshot.key.logicalRevision(), snapshot.key.settingsFingerprint(),
				snapshot.key.snapshotId(), snapshot.generation, snapshot.resultHash, snapshot.coverage,
				snapshot.hits.subList(from, to), next, next == null, List.of());
	}

	private String encode(Cursor cursor) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (DataOutputStream output = new DataOutputStream(bytes)) {
				output.writeUTF(CURSOR_DOMAIN); output.writeUTF(cursor.keyHash); output.writeLong(cursor.generation);
				output.writeUTF(cursor.queryHash); output.writeUTF(cursor.resultHash);
				output.writeUTF(cursor.storeId); output.writeInt(cursor.position);
			}
			byte[] raw = bytes.toByteArray();
			return Base64.getUrlEncoder().withoutPadding().encodeToString(raw) + "."
					+ Base64.getUrlEncoder().withoutPadding().encodeToString(mac(raw));
		} catch (IOException impossible) { throw new IllegalStateException(impossible); }
	}

	private Cursor authenticate(String token) {
		try {
			if (token.length() > 1024) throw new IllegalArgumentException();
			String[] parts = token.split("\\.", -1);
			if (parts.length != 2) throw new IllegalArgumentException();
			byte[] raw = Base64.getUrlDecoder().decode(parts[0]);
			byte[] signature = Base64.getUrlDecoder().decode(parts[1]);
			if (signature.length != 32 || !MessageDigest.isEqual(signature, mac(raw))) throw new IllegalArgumentException();
			try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(raw))) {
				if (!CURSOR_DOMAIN.equals(input.readUTF())) throw new IllegalArgumentException();
				Cursor cursor = new Cursor(input.readUTF(), input.readLong(), input.readUTF(), input.readUTF(),
						input.readUTF(), input.readInt());
				if (input.available() != 0) throw new IllegalArgumentException();
				return cursor;
			}
		} catch (RuntimeException | IOException invalid) { throw new IllegalArgumentException("Malformed or incompatible search cursor"); }
	}

	private byte[] mac(byte[] raw) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(cursorKey, "HmacSHA256"));
			return mac.doFinal(raw);
		} catch (Exception impossible) { throw new IllegalStateException(impossible); }
	}
	private static String queryHash(SearchQuery query) {
		return hash(query.query() + "\0" + query.domains().stream().sorted().toList() + "\0"
				+ query.matchMode() + "\0" + query.caseSensitive() + "\0" + query.pageSize()
				+ "\0" + query.strict() + "\0" + query.requireComplete());
	}
	private static String hash(String value) {
		try {
			return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception impossible) { throw new IllegalStateException(impossible); }
	}
	private record ResultSnapshot(String storeId, String resultHash, SearchIndexKey key, long generation,
			String queryHash, List<Coverage> coverage, List<Hit> hits, long createdAt, long bytes) { }
	private record Cursor(String keyHash, long generation, String queryHash, String resultHash,
			String storeId, int position) { }
	public static final class StaleSearchException extends RuntimeException {
		public StaleSearchException(String message) { super(message); }
	}
	public static final class IncompleteSearchException extends RuntimeException {
		private final List<String> domains;
		private final List<Coverage> coverage;
		public IncompleteSearchException(List<String> domains, List<Coverage> coverage) {
			super("Requested search domains are incomplete");
			this.domains = List.copyOf(domains); this.coverage = List.copyOf(coverage);
		}
		public Map<String, Object> details() { return Map.of("missingDomains", domains, "coverage", coverage); }
	}
	public static final class SearchLimitException extends RuntimeException {
		public SearchLimitException(String message) { super(message); }
	}
	public static final class UnsupportedRegexException extends RuntimeException {
		public UnsupportedRegexException() { super("Unsupported RE2/J regular expression syntax"); }
	}
}
