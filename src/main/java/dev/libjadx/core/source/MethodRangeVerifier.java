package dev.libjadx.core.source;

import java.util.Map;

/** Bounded lexical boundary check used only after Jadx identifies the original declaration. */
public final class MethodRangeVerifier {
	private MethodRangeVerifier() { }

	public static SourceCoordinates.Range verify(String source, int nameOffset, String emittedName,
			Map<Integer, ?> metadata, Object declaration, Object endMarker, String snapshotId) {
		if (nameOffset < 0 || nameOffset >= source.length() || !source.startsWith(emittedName, nameOffset)) return null;
		if (metadata.get(nameOffset) != declaration) return null;
		int lineStart = nameOffset;
		while (lineStart > 0 && source.charAt(lineStart - 1) != '\n' && source.charAt(lineStart - 1) != '\r') lineStart--;
		String prefix = source.substring(lineStart, nameOffset);
		if (prefix.indexOf('{') >= 0 || prefix.indexOf('}') >= 0 || prefix.indexOf(';') >= 0) return null;
		if (lineStart > 0) {
			int previousEnd = lineStart - 1;
			if (previousEnd > 0 && source.charAt(previousEnd - 1) == '\r') previousEnd--;
			int previousStart = previousEnd;
			while (previousStart > 0 && source.charAt(previousStart - 1) != '\n'
					&& source.charAt(previousStart - 1) != '\r') previousStart--;
			if (source.substring(previousStart, previousEnd).trim().startsWith("@")) return null;
		}
		int cursor = nameOffset + emittedName.length();
		while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) cursor++;
		if (cursor >= source.length() || source.charAt(cursor) != '(') return null;
		int parentheses = 0;
		boolean headerClosed = false;
		int braces = 0;
		for (int i = cursor; i < source.length(); i++) {
			char ch = source.charAt(i);
			if (ch == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
				i = source.indexOf('\n', i + 2);
				if (i < 0) return null;
				continue;
			}
			if (ch == '/' && i + 1 < source.length() && source.charAt(i + 1) == '*') {
				i = source.indexOf("*/", i + 2);
				if (i < 0) return null;
				i++;
				continue;
			}
			if (ch == '"' && i + 2 < source.length() && source.startsWith("\"\"\"", i)) {
				i = skipTextBlock(source, i + 3);
				if (i < 0) return null;
				continue;
			}
			if (ch == '"' || ch == '\'') {
				i = skipQuoted(source, i + 1, ch);
				if (i < 0) return null;
				continue;
			}
			if (!headerClosed) {
				if (ch == '(') parentheses++;
				else if (ch == ')' && --parentheses == 0) headerClosed = true;
				continue;
			}
			if (braces == 0) {
				if (ch == ';') return null; // no body and no attributable END marker
				if (ch == '{') braces = 1;
				continue;
			}
			if (ch == '{') braces++;
			else if (ch == '}' && --braces == 0) {
				int end = i + 1;
				if (metadata.get(end) != endMarker) return null;
				SourceCoordinates coordinates = new SourceCoordinates(source);
				return coordinates.isBoundary(lineStart) && coordinates.isBoundary(end)
						? coordinates.range(lineStart, end, snapshotId) : null;
			}
		}
		return null;
	}

	/** Checks pinned Jadx's plain catch headers inside an already verified method range.
	 * Missing metadata or unfamiliar syntax fails closed; trivia never declares a name. */
	public static boolean catchDeclarationsVerified(String source, int from, int to, Map<Integer, String> declarations) {
		if (from < 0 || to > source.length() || from >= to) return false;
		for (int i = from; i < to; i++) {
			char ch = source.charAt(i);
			if (source.startsWith("//", i)) {
				i = source.indexOf('\n', i + 2);
				if (i < 0 || i >= to) return false;
				continue;
			}
			if (source.startsWith("/*", i)) {
				i = source.indexOf("*/", i + 2);
				if (i < 0 || i + 1 >= to) return false;
				i++;
				continue;
			}
			if (ch == '"' || ch == '\'') {
				i = source.startsWith("\"\"\"", i) ? skipTextBlock(source, i + 3) : skipQuoted(source, i + 1, ch);
				if (i < 0 || i >= to) return false;
				continue;
			}
			if (!Character.isJavaIdentifierStart(ch)) continue;
			int tokenStart = i++;
			while (i < to && Character.isJavaIdentifierPart(source.charAt(i))) i++;
			if (!source.substring(tokenStart, i).equals("catch")) { i--; continue; }
			while (i < to && Character.isWhitespace(source.charAt(i))) i++;
			if (i >= to || source.charAt(i++) != '(') return false;
			int nameStart = -1;
			int nameEnd = -1;
			while (i < to && source.charAt(i) != ')') {
				ch = source.charAt(i);
				if (Character.isJavaIdentifierStart(ch)) {
					nameStart = i++;
					while (i < to && Character.isJavaIdentifierPart(source.charAt(i))) i++;
					nameEnd = i;
				} else if (Character.isWhitespace(ch) || ch == '.' || ch == '|') i++;
				else return false;
			}
			if (i >= to || nameStart < 0 || !source.substring(nameStart, nameEnd).equals(declarations.get(nameStart)))
				return false;
		}
		return true;
	}

	private static int skipQuoted(String source, int from, char quote) {
		for (int i = from; i < source.length(); i++) {
			if (source.charAt(i) == '\\') i++;
			else if (source.charAt(i) == quote) return i;
		}
		return -1;
	}

	private static int skipTextBlock(String source, int from) {
		for (int i = from; i + 2 < source.length(); i++) {
			if (source.charAt(i) == '\\') i++;
			else if (source.startsWith("\"\"\"", i)) return i + 2;
		}
		return -1;
	}
}
