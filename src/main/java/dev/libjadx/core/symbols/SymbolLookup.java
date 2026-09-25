package dev.libjadx.core.symbols;

import java.util.List;
import java.util.function.Function;

/** Exact original-identity lookup over one immutable catalog snapshot. */
public final class SymbolLookup {
	private SymbolLookup() { }

	public static SymbolResolution resolve(SymbolCatalog catalog, SymbolRef ref,
			Function<SymbolCatalog.Entry, List<SymbolInfo>> members) {
		String session = catalog.sessionId();
		long revision = catalog.logicalRevision();
		String snapshot = catalog.snapshotId();
		if (ref.inputIdentity() != null) return new SymbolResolution(SymbolResolution.Outcome.PROVENANCE_UNAVAILABLE,
				ref, session, revision, snapshot, null, List.of(),
				List.of("Pinned Jadx does not verify exact per-input attribution for this symbol"));
		List<SymbolCatalog.Entry> matches = catalog.matching(ref.originalClassDescriptor());
		if (matches.isEmpty()) return new SymbolResolution(SymbolResolution.Outcome.NOT_FOUND,
				ref, session, revision, snapshot, null, List.of(),
				List.of("No matching declaration among currently Jadx-visible classes"));
		if (matches.size() > 1) return new SymbolResolution(SymbolResolution.Outcome.AMBIGUOUS,
				ref, session, revision, snapshot, null, matches.stream().limit(16)
						.map(entry -> classSymbol(entry.info())).toList(),
				List.of("Multiple Jadx-visible definitions share this original class descriptor"));
		ClassInfo info = matches.getFirst().info();
		if (ref.kind() == SymbolRef.Kind.CLASS) return new SymbolResolution(SymbolResolution.Outcome.RESOLVED,
				ref, session, revision, snapshot, classSymbol(info), List.of(),
				List.of("Per-input origin and discarded definitions are not verified"));
		List<SymbolInfo> found = members.apply(matches.getFirst());
		if (found.isEmpty()) return new SymbolResolution(SymbolResolution.Outcome.NOT_FOUND,
				ref, session, revision, snapshot, null, List.of(),
				List.of("No matching member among Jadx-visible declarations; hidden or inlined members may be unavailable"));
		if (found.size() > 1) return new SymbolResolution(SymbolResolution.Outcome.AMBIGUOUS,
				ref, session, revision, snapshot, null, found.stream().limit(16).toList(),
				List.of("Multiple visible members share this original identity"));
		return new SymbolResolution(SymbolResolution.Outcome.RESOLVED,
				ref, session, revision, snapshot, found.getFirst(), List.of(),
				List.of("Per-input origin and discarded definitions are not verified"));
	}

	private static SymbolInfo classSymbol(ClassInfo info) {
		return new SymbolInfo(info.ref(), null, null, info.displayName(), info.displayQualifiedName(), null, info.provenance());
	}
}
