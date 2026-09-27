package dev.libjadx.core.search;

import java.util.List;

public record SearchQuery(String query, List<Domain> domains, MatchMode matchMode, boolean caseSensitive,
		int pageSize, String cursor, boolean strict, boolean requireComplete,
		String expectedSessionId, Long expectedLogicalRevision) {
	public enum Domain { CLASS_NAME, MEMBER_NAME, SOURCE_TEXT, STRING_LITERAL }
	public enum MatchMode { EXACT, CONTAINS, REGEX }
	public SearchQuery {
		if (query == null || query.isBlank() || query.length() > 512 || query.indexOf('\0') >= 0)
			throw new IllegalArgumentException("query must contain 1 to 512 non-NUL characters");
		if (domains == null || domains.isEmpty() || domains.size() > 4 || domains.stream().distinct().count() != domains.size())
			throw new IllegalArgumentException("domains must contain 1 to 4 distinct values");
		if (domains.contains(Domain.STRING_LITERAL)) throw new UnsupportedDomainException("STRING_LITERAL lexical ownership is not verified");
		if (matchMode == MatchMode.REGEX && domains.stream().anyMatch(domain -> domain != Domain.SOURCE_TEXT))
			throw new UnsupportedDomainException("REGEX is supported only for SOURCE_TEXT");
		if (pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("pageSize must be 1 to 100");
		if ((expectedSessionId == null) != (expectedLogicalRevision == null) || expectedLogicalRevision != null && expectedLogicalRevision < 0)
			throw new IllegalArgumentException("Revision preconditions must be supplied together");
		if (cursor != null && (cursor.isBlank() || cursor.length() > 1024)) throw new IllegalArgumentException("Invalid cursor length");
		domains = List.copyOf(domains);
	}
	public static final class UnsupportedDomainException extends RuntimeException {
		public UnsupportedDomainException(String message) { super(message); }
	}
}
