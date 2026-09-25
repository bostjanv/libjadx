package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import dev.libjadx.core.symbols.SymbolRef;

class SymbolRefTest {
	@Test
	void acceptsCanonicalClassMemberAndArrayDescriptors() {
		assertDoesNotThrow(() -> SymbolRef.classRef("LDefault;") );
		assertDoesNotThrow(() -> SymbolRef.classRef("Lprobe/Outer$Inner;"));
		assertDoesNotThrow(() -> new SymbolRef(SymbolRef.Kind.METHOD, "Lprobe/Outer;", null,
				"<init>", "([Ljava/lang/String;I)V"));
		assertDoesNotThrow(() -> new SymbolRef(SymbolRef.Kind.FIELD, "Lprobe/Outer;", null,
				"items", "[[I"));
	}

	@Test
	void rejectsMalformedOrLossyIdentities() {
		for (String value : new String[] {"probe.Outer", "[Lprobe/Outer;", "Lprobe//Outer;", "Lprobe.Outer;",
				"Lprobe/Outer ;", "L;", "Lprobe/Outer"}) {
			assertThrows(IllegalArgumentException.class, () -> SymbolRef.classRef(value), value);
		}
		for (String value : new String[] {"(V)V", "([V)V", "(I)", "(I)Q", "(Ljava/lang/String)V",
				"(Lbad..name;)V", "(I)Vextra", "(I I)V"}) {
			assertThrows(IllegalArgumentException.class, () -> new SymbolRef(SymbolRef.Kind.METHOD,
					"Lprobe/Outer;", null, "run", value), value);
		}
		for (String value : new String[] {"V", "I;", "[V", "Ljava/lang/String", "Ljava.lang.String;"}) {
			assertThrows(IllegalArgumentException.class, () -> new SymbolRef(SymbolRef.Kind.FIELD,
					"Lprobe/Outer;", null, "field", value), value);
		}
		assertThrows(IllegalArgumentException.class, () -> new SymbolRef(SymbolRef.Kind.CLASS,
				"Lprobe/Outer;", null, "Alias", null));
		assertThrows(IllegalArgumentException.class, () -> new SymbolRef(SymbolRef.Kind.FIELD,
				"Lprobe/Outer;", null, "<init>", "I"));
		assertThrows(IllegalArgumentException.class, () -> new SymbolRef(SymbolRef.Kind.METHOD,
				"Lprobe/Outer;", null, "<init>", "()I"));
		assertThrows(IllegalArgumentException.class, () -> new SymbolRef(SymbolRef.Kind.METHOD,
				"Lprobe/Outer;", null, "<clinit>", "(I)V"));
	}
}
