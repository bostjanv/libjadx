package dev.libjadx.core.symbols;

import java.util.List;

public record ClassPage(String sessionId, long logicalRevision, String snapshotId, String scope,
		String sourceCoverage, List<ClassInfo> items, String nextCursor, boolean complete) {
	public ClassPage { items = List.copyOf(items); }
}
