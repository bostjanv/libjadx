package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import dev.libjadx.core.hierarchy.CensusLimits;
import dev.libjadx.jadxadapter.JadxInputCensusAdapter;
import dev.libjadx.project.NativeProjectDocument;
import jadx.api.data.impl.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Repeatable owned-size measurements, no latency threshold disguised as a correctness test. */
class SafeReplayCostTest {
	@TempDir Path dir;
	@Test void measureBoundedReplacementWithAnOldEngineStillLive() throws Exception {
		var rows = new ArrayList<Map<String, Object>>();
		for (int size : List.of(16, 128, 512)) {
			Path root = Files.createDirectories(dir.resolve("n" + size));
			Path source = root.resolve("Batch.java");
			StringBuilder program = new StringBuilder("package cost; public class Batch {}\n");
			for (int cls = 0; cls < size; cls++) {
				program.append("class C").append(cls).append(" {");
				for (int m = 0; m < 8; m++) program.append("public int f").append(m).append("(int v) {return v+").append(m).append(";}");
				program.append("}\n");
			}
			Files.writeString(source, program);
			var inputs = List.of(SymbolFixtureSupport.compile(root, source, "cost.jar"));
			for (boolean hot : List.of(false, true)) try (var old = SafeReplayStrategyTest.open(inputs, null, new JadxCodeData())) {
				if (hot) for (var cls : old.getClasses()) cls.getCode();
				for (int items : List.of(0, 1, 64)) {
					var code = new JadxCodeData();
					var renames = new ArrayList<jadx.api.data.ICodeRename>();
					for (int i = 0; i < items; i++) renames.add(new JadxCodeRename(new JadxNodeRef(
							jadx.api.data.IJavaNodeRef.RefType.METHOD, "cost.C" + i / 8, "f" + i % 8 + "(I)I"), "edited" + i));
					code.setRenames(renames);
					long start = System.nanoTime();
					NativeProjectDocument.copyCodeData(code);
					long copy = System.nanoTime() - start;
					long census = 0, load = 0;
					for (var pool : java.lang.management.ManagementFactory.getMemoryPoolMXBeans()) pool.resetPeakUsage();
					long before = usedHeap();
					if (items != 0) {
						start = System.nanoTime(); var capture = JadxInputCensusAdapter.capture(inputs, CensusLimits.defaults());
						census = System.nanoTime() - start;
						start = System.nanoTime();
						try (var candidate = SafeReplayStrategyTest.open(inputs, null, code)) {
							capture.bind(candidate); load = System.nanoTime() - start;
							assertEquals(size + 1, candidate.getClasses().size());
						}
					}
					long peak = java.lang.management.ManagementFactory.getMemoryPoolMXBeans().stream()
							.filter(p -> p.getType() == java.lang.management.MemoryType.HEAP).mapToLong(p -> p.getPeakUsage().getUsed()).sum();
					rows.add(Map.of("classes", size + 1, "hot", hot, "items", items, "copyMs", copy / 1e6,
							"censusMs", census / 1e6, "constructLoadBindMs", load / 1e6, "heapBeforeBytes", before,
							"heapPeakBytes", peak, "heapAfterBytes", usedHeap()));
				}
			}
		}
		Path output = Path.of("build/safe-replay-probe"); Files.createDirectories(output);
		Files.writeString(output.resolve("performance.json"), new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(rows));
	}
	private static long usedHeap() { return java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed(); }
}
