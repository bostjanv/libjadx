package dev.libjadx.core.symbols;

import java.util.Objects;

/** Original binary identity. Display aliases are deliberately absent. */
public record SymbolRef(Kind kind, String originalClassDescriptor, String inputIdentity,
		String originalName, String originalDescriptor) {
	public enum Kind { CLASS, METHOD, FIELD }

	public SymbolRef {
		Objects.requireNonNull(kind, "kind");
		validateClassDescriptor(originalClassDescriptor);
		if (inputIdentity != null && (inputIdentity.isBlank() || inputIdentity.length() > 256
				|| inputIdentity.chars().anyMatch(Character::isWhitespace))) {
			throw new IllegalArgumentException("Invalid inputIdentity");
		}
		if (kind == Kind.CLASS) {
			if (originalName != null || originalDescriptor != null) {
				throw new IllegalArgumentException("CLASS must omit member fields");
			}
		} else {
			validateMemberName(originalName, kind);
			if (originalDescriptor == null || originalDescriptor.length() > 2048) {
				throw new IllegalArgumentException("Invalid originalDescriptor");
			}
			if (kind == Kind.FIELD) validateFieldDescriptor(originalDescriptor);
			else {
				validateMethodDescriptor(originalDescriptor);
				if (originalName.equals("<init>") && !originalDescriptor.endsWith(")V"))
					throw new IllegalArgumentException("Constructor must return V");
				if (originalName.equals("<clinit>") && !originalDescriptor.equals("()V"))
					throw new IllegalArgumentException("Class initializer must be ()V");
			}
		}
	}

	public static SymbolRef classRef(String descriptor) {
		return new SymbolRef(Kind.CLASS, descriptor, null, null, null);
	}

	public static void validateClassDescriptor(String value) {
		if (value == null || value.length() < 3 || value.length() > 1024
				|| value.charAt(0) != 'L' || value.charAt(value.length() - 1) != ';') {
			throw new IllegalArgumentException("Invalid originalClassDescriptor");
		}
		validateObjectBody(value, 1, value.length() - 1);
	}

	private static void validateObjectBody(String value, int from, int to) {
		if (from == to || value.charAt(from) == '/' || value.charAt(to - 1) == '/') {
			throw new IllegalArgumentException("Invalid object descriptor");
		}
		for (int i = from; i < to; i++) {
			char c = value.charAt(i);
			if (c == '/' && value.charAt(i - 1) == '/') throw new IllegalArgumentException("Empty package segment");
			if (c == '.' || c == ';' || c == '[' || c == '<' || c == '>' || c == '\\'
					|| Character.isWhitespace(c) || Character.isISOControl(c)) {
				throw new IllegalArgumentException("Invalid object descriptor character");
			}
		}
	}

	private static void validateMemberName(String name, Kind kind) {
		if (name == null || name.isEmpty() || name.length() > 512) throw new IllegalArgumentException("Invalid originalName");
		if (name.equals("<init>") || name.equals("<clinit>")) {
			if (kind != Kind.METHOD) throw new IllegalArgumentException("Special name requires METHOD");
			return;
		}
		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);
			if (c == '.' || c == ';' || c == '[' || c == '/' || c == '<' || c == '>'
					|| Character.isWhitespace(c) || Character.isISOControl(c)) {
				throw new IllegalArgumentException("Invalid originalName");
			}
		}
	}

	private static void validateFieldDescriptor(String value) {
		int end = typeEnd(value, 0, false);
		if (end != value.length()) throw new IllegalArgumentException("Invalid field descriptor");
	}

	private static void validateMethodDescriptor(String value) {
		if (!value.startsWith("(")) throw new IllegalArgumentException("Invalid method descriptor");
		int at = 1;
		while (at < value.length() && value.charAt(at) != ')') at = typeEnd(value, at, false);
		if (at >= value.length() || value.charAt(at) != ')') throw new IllegalArgumentException("Invalid method descriptor");
		if (typeEnd(value, at + 1, true) != value.length()) throw new IllegalArgumentException("Invalid method descriptor");
	}

	private static int typeEnd(String value, int at, boolean allowVoid) {
		int dimensions = 0;
		while (at < value.length() && value.charAt(at) == '[') {
			if (++dimensions > 255) throw new IllegalArgumentException("Too many array dimensions");
			at++;
		}
		if (at == value.length()) throw new IllegalArgumentException("Incomplete type descriptor");
		char tag = value.charAt(at++);
		if ("BCDFIJSZ".indexOf(tag) >= 0) return at;
		if (tag == 'V' && allowVoid && dimensions == 0) return at;
		if (tag == 'L') {
			int end = value.indexOf(';', at);
			if (end < 0) throw new IllegalArgumentException("Unterminated object descriptor");
			validateObjectBody(value, at, end);
			return end + 1;
		}
		throw new IllegalArgumentException("Invalid type descriptor");
	}
}
