package dev.libjadx.core.search;

import java.util.List;
import dev.libjadx.core.source.SourceCoordinates;
import dev.libjadx.core.symbols.SymbolRef;

/** Copied public search values; no live Jadx node or source metadata escapes a lease. */
public final class SearchDtos {
	private SearchDtos() { }
	public record Coverage(SearchQuery.Domain domain, String availability, String state, String scope,
			String sourceInputCoverage, int eligible, int indexed, int pending, int skipped, int failed,
			int evicted, List<String> diagnostics) { }
	public record Hit(SearchQuery.Domain domain, SymbolRef ref, SymbolRef sourceOwnerRef,
			String displayName, String matchedText, SearchQuery.MatchMode matchMode,
			SourceCoordinates.Range range, String sourceSnapshotId) { }
	public record Page(String sessionId, long logicalRevision, String settingsFingerprint,
			String indexSnapshotId, long indexGeneration, String resultSnapshotId,
			List<Coverage> coverage, List<Hit> hits, String nextCursor, boolean pageComplete,
			List<String> diagnostics) { }
	public record IndexStatus(String sessionId, long logicalRevision, String settingsFingerprint,
			String indexSnapshotId, long indexGeneration, List<Coverage> coverage) { }
}
