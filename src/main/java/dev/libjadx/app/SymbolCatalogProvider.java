package dev.libjadx.app;

import dev.libjadx.core.RevisionState;
import dev.libjadx.core.symbols.SymbolCatalog;
import dev.libjadx.jadxadapter.JadxSymbolAdapter;
import jadx.api.JadxDecompiler;

/** Shared P4.1 catalog cache for primary-engine symbol and source operations. */
public final class SymbolCatalogProvider {
	private final byte[] cursorKey;
	private volatile SymbolCatalog primary;

	public SymbolCatalogProvider(byte[] cursorKey) { this.cursorKey = cursorKey.clone(); }

	public SymbolCatalog primary(ProjectRuntime.PrimarySymbolRead context) {
		SymbolCatalog current = primary;
		if (current == null || !current.sessionId().equals(context.revisions().sessionId())
				|| current.logicalRevision() != context.revisions().logicalRevision()
				|| current.publicationEpoch() != context.publicationEpoch()) {
			current = temporary(context.decompiler(), context.revisions(), context.publicationEpoch());
			primary = current;
		}
		return current;
	}

	public SymbolCatalog temporary(JadxDecompiler decompiler, RevisionState revisions, long publicationEpoch) {
		return new SymbolCatalog(JadxSymbolAdapter.classes(decompiler), revisions.sessionId(),
				revisions.logicalRevision(), publicationEpoch, cursorKey);
	}
}
