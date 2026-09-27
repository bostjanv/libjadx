package dev.libjadx.jadxadapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;

import dev.libjadx.app.SymbolFixtureSupport;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real pinned-Jadx 1.5.6 observations for the search adapter. */
class JadxSearchProbeTest {
	@TempDir Path dir;

	@Test
	void coldCatalogPreservesClassProcessStateAndUsesOneOriginalOuterOwner() throws Exception {
		JadxArgs args = new JadxArgs();
		args.getInputFiles().add(SymbolFixtureSupport.compileFixture(dir).toFile());
		try (JadxDecompiler jadx = new JadxDecompiler(args)) {
			jadx.load();
			var visible = jadx.getClassesWithInners();
			Map<String, Object> states = visible.stream().collect(java.util.stream.Collectors.toMap(
					cls -> cls.getRawName(), cls -> cls.getClassNode().getState()));
			var catalog = JadxSymbolAdapter.classes(jadx);
			var owners = JadxSearchAdapter.originalSourceOwners(jadx);
			for (var cls : visible) assertEquals(states.get(cls.getRawName()), cls.getClassNode().getState());
			assertEquals(3, catalog.size());
			assertEquals("Lprobe/SymbolFixture;", owners.get("Lprobe/SymbolFixture$1;"));
			assertEquals("Lprobe/SymbolFixture;", owners.get("Lprobe/SymbolFixture$Inner;"));
		}
	}

	@Test
	void returnOnlyOverloadsRetainDistinctOriginalDescriptors() throws Exception {
		JadxArgs args = new JadxArgs();
		args.getInputFiles().add(SymbolFixtureSupport.returnTypeClashJar(dir).toFile());
		try (JadxDecompiler jadx = new JadxDecompiler(args)) {
			jadx.load();
			var cls = JadxSymbolAdapter.visibleClass(jadx, "Lprobe/ReturnClash;", 0);
			var members = JadxSearchAdapter.members(cls);
			assertTrue(members.stream().anyMatch(m -> "()I".equals(m.ref().originalDescriptor())));
			assertTrue(members.stream().anyMatch(m -> "()Ljava/lang/String;".equals(m.ref().originalDescriptor())));
		}
	}
}
