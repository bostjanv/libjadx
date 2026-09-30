package dev.libjadx.core.edits;

import java.util.List;

import dev.libjadx.core.symbols.SymbolRef;

/** Transport-independent batch edit request and immutable public result. */
public final class EditDtos {
	private EditDtos() { }
	public enum Kind { RENAME, SET_COMMENT, RENAME_PARAMETER }
	public record Operation(Kind kind, SymbolRef target, String newName, String comment, String style,
			Integer parameterIndex, String sourceSnapshotId, boolean propagateRelated) {
		public Operation(Kind kind, SymbolRef target, String newName, String comment, String style) {
			this(kind, target, newName, comment, style, null, null, false);
		}
		public Operation(Kind kind, SymbolRef target, String newName, String comment, String style,
				Integer parameterIndex, String sourceSnapshotId) {
			this(kind, target, newName, comment, style, parameterIndex, sourceSnapshotId, false);
		}
	}
	public record Request(String expectedSessionId, Long expectedLogicalRevision, List<Operation> items) {
		public Request {
			if ((expectedSessionId == null) != (expectedLogicalRevision == null)) {
				throw new IllegalArgumentException("Revision preconditions must be supplied together");
			}
			items = List.copyOf(items);
		}
	}
	public record ItemResult(int index, String status, Kind kind, SymbolRef target,
			List<SymbolRef> affectedRefs, String message, Integer parameterIndex, String sourceSnapshotId) {
		public ItemResult { affectedRefs = List.copyOf(affectedRefs); }
	}
	public record Result(String outcome, String sessionId, long logicalRevisionBefore,
			long logicalRevisionAfter, long indexRevisionAfter, boolean dirty, boolean saved,
			List<ItemResult> items, List<String> diagnostics) {
		public Result { items = List.copyOf(items); diagnostics = List.copyOf(diagnostics); }
	}
	public record ItemError(int index, String code, String message) { }
}
