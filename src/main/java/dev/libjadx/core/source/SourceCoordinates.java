package dev.libjadx.core.source;

import java.util.ArrayList;
import java.util.List;

/** Positions in the exact generated Java string, with no newline normalization. */
public final class SourceCoordinates {
	private final String source;
	private final int[] lineStarts;

	public SourceCoordinates(String source) {
		this.source = source;
		List<Integer> starts = new ArrayList<>();
		starts.add(0);
		for (int i = 0; i < source.length(); i++) {
			char ch = source.charAt(i);
			if (ch == '\r') {
				if (i + 1 < source.length() && source.charAt(i + 1) == '\n') i++;
				starts.add(i + 1);
			} else if (ch == '\n') {
				starts.add(i + 1);
			}
		}
		lineStarts = starts.stream().mapToInt(Integer::intValue).toArray();
	}

	public boolean isBoundary(int offset) {
		return offset >= 0 && offset <= source.length()
				&& !(offset > 0 && offset < source.length()
						&& Character.isHighSurrogate(source.charAt(offset - 1))
						&& Character.isLowSurrogate(source.charAt(offset)));
	}

	public Point point(int offset) {
		if (!isBoundary(offset)) throw new IllegalArgumentException("Invalid UTF-16 source boundary");
		int low = 0;
		int high = lineStarts.length;
		while (low + 1 < high) {
			int mid = (low + high) >>> 1;
			if (lineStarts[mid] <= offset) low = mid;
			else high = mid;
		}
		return new Point(offset, low + 1, source.codePointCount(lineStarts[low], offset));
	}

	public Range range(int start, int end, String sourceSnapshotId) {
		if (start > end || !isBoundary(start) || !isBoundary(end))
			throw new IllegalArgumentException("Invalid half-open source range");
		return new Range(start, end, point(start), point(end), sourceSnapshotId);
	}

	public record Point(int offsetUtf16, int line, int columnCodePoints) { }
	public record Range(int startOffsetUtf16, int endOffsetUtf16, Point start, Point end,
			String sourceSnapshotId) { }
}
