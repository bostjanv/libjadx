package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import dev.libjadx.core.symbols.ClassInfo;
import dev.libjadx.core.symbols.ClassQuery;
import dev.libjadx.core.symbols.SymbolCatalog;
import dev.libjadx.core.symbols.SymbolInfo;
import dev.libjadx.core.symbols.SymbolLookup;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.core.symbols.SymbolResolution;

class SymbolCatalogTest {
	@Test
	void pagesStableRawOrderIncludingDuplicateCandidatesAndRejectsChangedFilters() {
		byte[] key = SymbolCatalog.newCursorKey();
		var catalog = new SymbolCatalog(List.of(info("Lz/Z;", "z.Z"), info("La/A;", "a.A"),
				info("La/A;", "a.Alias"), info("La/A$Inner;", "a.A.Inner")),
				"session", 4, 7, key);
		var query = new ClassQuery(1, null, null, null, ClassQuery.NameDomain.original, true);
		assertEquals(2, catalog.matching("La/A;").size());
		var first = catalog.page(query);
		assertEquals("La/A$Inner;", first.items().getFirst().ref().originalClassDescriptor());
		assertNotNull(first.nextCursor());
		var second = catalog.page(new ClassQuery(1, first.nextCursor(), null, null,
				ClassQuery.NameDomain.original, true));
		assertEquals("La/A;", second.items().getFirst().ref().originalClassDescriptor());
		assertEquals(SymbolInfo.Provenance.AMBIGUOUS, second.items().getFirst().provenance());
		var third = catalog.page(new ClassQuery(1, second.nextCursor(), null, null,
				ClassQuery.NameDomain.original, true));
		assertEquals("La/A;", third.items().getFirst().ref().originalClassDescriptor());
		var fourth = catalog.page(new ClassQuery(1, third.nextCursor(), null, null,
				ClassQuery.NameDomain.original, true));
		assertEquals("Lz/Z;", fourth.items().getFirst().ref().originalClassDescriptor());
		assertNull(fourth.nextCursor());
		assertEquals(true, fourth.complete());
		assertThrows(IllegalArgumentException.class, () -> catalog.page(new ClassQuery(1, first.nextCursor(),
				"a", null, ClassQuery.NameDomain.original, true)));
		for (int field : new int[] {1, 2, 3}) {
			String tampered = tamper(first.nextCursor(), field);
			assertThrows(IllegalArgumentException.class, () -> catalog.page(new ClassQuery(1, tampered,
					null, null, ClassQuery.NameDomain.original, true)));
		}
		assertThrows(SymbolCatalog.StaleCursorException.class, () -> new SymbolCatalog(List.of(),
				"new-session", 4, 7, key).page(new ClassQuery(1, first.nextCursor(),
						null, null, ClassQuery.NameDomain.original, true)));
		assertThrows(SymbolCatalog.StaleCursorException.class, () -> new SymbolCatalog(List.of(),
				"session", 5, 7, key).page(new ClassQuery(1, first.nextCursor(),
						null, null, ClassQuery.NameDomain.original, true)));
	}

	private static String tamper(String cursor, int field) {
		String[] parts = cursor.split("\\.", -1);
		String[] fields = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8).split("\0", -1);
		fields[field] = field == 1 ? "other-session" : "999";
		String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
				String.join("\0", fields).getBytes(StandardCharsets.UTF_8));
		return payload + "." + parts[1];
	}

	@Test
	void duplicateResolutionHasNoArbitraryWinnerAndBoundsCandidates() {
		var catalog = new SymbolCatalog(java.util.Collections.nCopies(20, info("La/A;", "a.A")),
				"session", 0, 0, SymbolCatalog.newCursorKey());
		SymbolRef ref = SymbolRef.classRef("La/A;");
		var outcome = SymbolLookup.resolve(catalog, ref, entry -> {
			throw new AssertionError("Members must not be examined for duplicate classes");
		});
		assertEquals(SymbolResolution.Outcome.AMBIGUOUS, outcome.outcome());
		assertNull(outcome.symbol());
		assertEquals(16, outcome.candidates().size());
		assertEquals(SymbolResolution.Outcome.PROVENANCE_UNAVAILABLE,
				SymbolLookup.resolve(catalog, new SymbolRef(SymbolRef.Kind.CLASS, "La/A;", "claimed", null, null),
						entry -> List.of()).outcome());
		assertEquals(SymbolResolution.Outcome.NOT_FOUND,
				SymbolLookup.resolve(catalog, SymbolRef.classRef("La/Missing;"), entry -> List.of()).outcome());
	}

	private static ClassInfo info(String descriptor, String alias) {
		return new ClassInfo(SymbolRef.classRef(descriptor), descriptor.substring(1, descriptor.length() - 1).replace('/', '.'),
				alias.substring(alias.lastIndexOf('.') + 1), alias, descriptor.contains("$"), true,
				null, SymbolInfo.Provenance.UNAVAILABLE);
	}
}
