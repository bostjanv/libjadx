package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.libjadx.core.source.SourceCoordinates;
import dev.libjadx.core.source.SourceSnapshot;
import dev.libjadx.core.source.MethodRangeVerifier;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SourceCoordinatesTest {
	@Test
	void positionsUseUtf16OffsetsAndCodePointColumnsWithoutNormalizingNewlines() {
		String source = "A\r\n😀e\u0301\nZ\r";
		SourceCoordinates positions = new SourceCoordinates(source);
		assertEquals(new SourceCoordinates.Point(0, 1, 0), positions.point(0));
		assertEquals(new SourceCoordinates.Point(1, 1, 1), positions.point(1)); // CR
		assertEquals(new SourceCoordinates.Point(2, 1, 2), positions.point(2)); // LF boundary
		assertEquals(new SourceCoordinates.Point(3, 2, 0), positions.point(3));
		assertFalse(positions.isBoundary(4)); // inside the surrogate pair
		assertThrows(IllegalArgumentException.class, () -> positions.point(4));
		assertEquals(new SourceCoordinates.Point(5, 2, 1), positions.point(5));
		assertEquals(new SourceCoordinates.Point(7, 2, 3), positions.point(7));
		assertEquals(new SourceCoordinates.Point(8, 3, 0), positions.point(8));
		assertEquals(new SourceCoordinates.Point(source.length(), 4, 0), positions.point(source.length()));
		var range = positions.range(3, 7, "snapshot");
		assertEquals("😀e\u0301", source.substring(range.startOffsetUtf16(), range.endOffsetUtf16()));
		assertThrows(IllegalArgumentException.class, () -> positions.range(5, 4, "snapshot"));
		assertEquals(new SourceCoordinates.Point(0, 1, 0), new SourceCoordinates("").point(0));
		assertEquals(new SourceCoordinates.Point(2, 2, 0), new SourceCoordinates("x\n").point(2));
	}

	@Test
	void sourceIdentityBindsEveryRelevantComponent() {
		String base = SourceSnapshot.id("session", 1, 2, "settings", "Lx/Y;", "text");
		assertEquals(base, SourceSnapshot.id("session", 1, 2, "settings", "Lx/Y;", "text"));
		for (String changed : new String[] {
			SourceSnapshot.id("another", 1, 2, "settings", "Lx/Y;", "text"),
			SourceSnapshot.id("session", 2, 2, "settings", "Lx/Y;", "text"),
			SourceSnapshot.id("session", 1, 3, "settings", "Lx/Y;", "text"),
			SourceSnapshot.id("session", 1, 2, "other", "Lx/Y;", "text"),
			SourceSnapshot.id("session", 1, 2, "settings", "Lx/Z;", "text"),
			SourceSnapshot.id("session", 1, 2, "settings", "Lx/Y;", "changed") }) {
			org.junit.jupiter.api.Assertions.assertNotEquals(base, changed);
		}
		org.junit.jupiter.api.Assertions.assertNotEquals(
				SourceSnapshot.id("session", 1, 2, "settings", "Lx/Y;", "\uD800"),
				SourceSnapshot.id("session", 1, 2, "settings", "Lx/Y;", "\uDFFF"));
	}

	@Test
	void boundedMethodLexerRequiresAnAttributedEndAfterNestedSyntax() {
		String source = "class C {\n    int value() { String x = \"}\\\"\"; /* } */ "
				+ "Runnable r = () -> { System.out.println(\"{\"); }; return 1; }\n}\n";
		int name = source.indexOf("value");
		int end = source.indexOf("return 1; }") + "return 1; }".length();
		Object declaration = new Object();
		Object marker = new Object();
		Map<Integer, Object> metadata = Map.of(name, declaration, end, marker);
		var range = MethodRangeVerifier.verify(source, name, "value", metadata, declaration, marker, "snapshot");
		assertEquals(source.substring(range.startOffsetUtf16(), range.endOffsetUtf16()),
				"    int value() { String x = \"}\\\"\"; /* } */ Runnable r = () -> { System.out.println(\"{\"); }; return 1; }");
		assertEquals(null, MethodRangeVerifier.verify(source, name, "value", Map.of(name, declaration),
				declaration, marker, "snapshot"));
		String noBody = "abstract int value();";
		assertEquals(null, MethodRangeVerifier.verify(noBody, noBody.indexOf("value"), "value",
				Map.of(noBody.indexOf("value"), declaration), declaration, marker, "snapshot"));
		String textBlock = "class C {\n    String value() { String x = \"\"\"{ }\"\"\"; return x; }\n}\n";
		int blockName = textBlock.indexOf("value");
		int blockEnd = textBlock.indexOf("return x; }") + "return x; }".length();
		var blockRange = MethodRangeVerifier.verify(textBlock, blockName, "value",
				Map.of(blockName, declaration, blockEnd, marker), declaration, marker, "snapshot");
		assertEquals(blockEnd, blockRange.endOffsetUtf16());
		String sameLine = "class C { int value() { return 1; } }";
		int sameName = sameLine.indexOf("value");
		int sameEnd = sameLine.indexOf("return 1; }") + "return 1; }".length();
		assertEquals(null, MethodRangeVerifier.verify(sameLine, sameName, "value",
				Map.of(sameName, declaration, sameEnd, marker), declaration, marker, "snapshot"));
	}
}
