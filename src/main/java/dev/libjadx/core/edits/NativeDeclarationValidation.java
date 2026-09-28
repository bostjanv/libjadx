package dev.libjadx.core.edits;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Pure declaration rules shared by native batches and strict mapping import. */
public final class NativeDeclarationValidation {
	private NativeDeclarationValidation() { }
	private static final Set<String> JAVA_RESERVED = Set.of("abstract", "assert", "boolean", "break", "byte", "case", "catch",
			"char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
			"finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int", "interface",
			"long", "native", "new", "package", "private", "protected", "public", "return", "short", "static",
			"strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
			"volatile", "while", "true", "false", "null", "_", "var", "yield", "record", "sealed", "permits");

	public static boolean validName(String name) {
		return !(name == null || name.length() > 128 || name.isEmpty() || JAVA_RESERVED.contains(name)
				|| !name.matches("[A-Za-z_][A-Za-z0-9_]*"));
	}
	public static boolean validLineComment(String value) {
		return !(value == null || value.isEmpty() || value.contains("/*") || value.contains("*/")
				|| value.codePointCount(0, value.length()) > 4096
				|| value.getBytes(StandardCharsets.UTF_8).length > 16384 || value.codePoints().anyMatch(cp ->
					Character.isISOControl(cp) || Character.getType(cp) == Character.FORMAT
							|| Character.getType(cp) == Character.LINE_SEPARATOR
							|| Character.getType(cp) == Character.PARAGRAPH_SEPARATOR
							|| Character.getType(cp) == Character.SURROGATE));
	}
}
