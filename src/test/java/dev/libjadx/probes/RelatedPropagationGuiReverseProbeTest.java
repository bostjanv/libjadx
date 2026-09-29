package dev.libjadx.probes;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import dev.libjadx.project.NativeProjectDocument;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Actual GUI diagnostic evidence; a passing resave cannot repair incomplete discovery. */
class RelatedPropagationGuiReverseProbeTest {
	@Test void matchingGuiPreservesBothStrategiesIncludingTheOneSeedOmission() throws Exception {
		String root = System.getenv("LIBJADX_RELATED_GUI_ROOT");
		Assumptions.assumeTrue(root != null, "Run relatedPropagationGuiRoundTripTest with matching Jadx 1.5.6 GUI");
		for (String strategy : java.util.List.of("seed", "members")) {
			var project = NativeProjectDocument.open(Path.of(root, strategy, "gui-resaved.jadx"));
			RelatedNativeReplayProbeTest.verifyRepresentation(project, strategy.equals("members"));
			try (var engine = RelatedNativeReplayProbeTest.open(project)) {
				RelatedNativeReplayProbeTest.verifyAliases(engine, strategy.equals("members"));
			}
			// Known upstream GUI serialization drops unknown fields; headless codec preserves them.
			assertFalse(project.toJsonTree().has("futureRoot"));
		}
	}
}
