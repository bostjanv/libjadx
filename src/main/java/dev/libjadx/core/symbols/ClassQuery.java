package dev.libjadx.core.symbols;

public record ClassQuery(int pageSize, String cursor, String packagePrefix, String nameContains,
		NameDomain nameDomain, boolean includeInner) {
	public enum NameDomain { original, alias }
	public ClassQuery {
		if (pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("pageSize must be 1..100");
		if (cursor != null && (cursor.isEmpty() || cursor.length() > 4096)) throw new IllegalArgumentException("Invalid cursor");
		if (packagePrefix != null) {
			if (packagePrefix.length() > 512 || packagePrefix.isEmpty() || packagePrefix.contains("/") || packagePrefix.startsWith(".")
					|| packagePrefix.endsWith(".") || packagePrefix.contains("..")) {
				throw new IllegalArgumentException("Invalid packagePrefix");
			}
			SymbolRef.validateClassDescriptor("L" + packagePrefix.replace('.', '/') + "/Check;");
		}
		if (nameContains != null && (nameContains.isEmpty() || nameContains.length() > 256
				|| nameContains.chars().anyMatch(Character::isISOControl))) {
			throw new IllegalArgumentException("Invalid nameContains");
		}
		if (nameDomain == null) throw new IllegalArgumentException("Invalid nameDomain");
	}
}
