package dev.libjadx.core.edits;

import java.util.List;

import dev.libjadx.core.symbols.SymbolRef;

/** Transport-independent batch edit request and immutable public result. */
public final class EditDtos {
	private EditDtos() { }
	public enum Kind { RENAME, SET_COMMENT }
	public record Operation(Kind kind, SymbolRef target, String newName, String comment, String style) { }
	public record Request(String expectedSessionId, Long expectedLogicalRevision, List<Operation> items) {
		public Request {
			if ((expectedSessionId == null) != (expectedLogicalRevision == null)) {
				throw new IllegalArgumentException("Revision preconditions must be supplied together");
			}
			items = List.copyOf(items);
		}
	}
	public record ItemResult(int index, String status, Kind kind, SymbolRef target,
			List<SymbolRef> affectedRefs, String message) {
		public ItemResult { affectedRefs = List.copyOf(affectedRefs); }
	}
	public record Result(String outcome, String sessionId, long logicalRevisionBefore,
			long logicalRevisionAfter, long indexRevisionAfter, boolean dirty, boolean saved,
			List<ItemResult> items, List<String> diagnostics) {
		public Result { items = List.copyOf(items); diagnostics = List.copyOf(diagnostics); }
	}
	public record ItemError(int index, String code, String message) { }
}
