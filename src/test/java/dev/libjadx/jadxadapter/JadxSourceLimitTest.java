package dev.libjadx.jadxadapter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class JadxSourceLimitTest {
	@Test
	void sourceCapMeasuresUtf8AndAnnotationsFailBeforeOverflow() {
		assertDoesNotThrow(() -> JadxSourceAdapter.requireSourceWithinLimit("x".repeat(4 * 1024 * 1024)));
		assertThrows(JadxSourceAdapter.SourceLimitException.class,
				() -> JadxSourceAdapter.requireSourceWithinLimit("x".repeat(4 * 1024 * 1024 + 1)));
		assertThrows(JadxSourceAdapter.SourceLimitException.class,
				() -> JadxSourceAdapter.requireSourceWithinLimit("😀".repeat(1_048_577)));
		assertDoesNotThrow(() -> JadxSourceAdapter.requireAnnotationCapacity(19_999));
		assertThrows(JadxSourceAdapter.AnnotationLimitException.class,
				() -> JadxSourceAdapter.requireAnnotationCapacity(20_000));
	}
}
