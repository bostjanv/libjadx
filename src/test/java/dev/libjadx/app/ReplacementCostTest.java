package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import dev.libjadx.core.edits.EditDtos;
import dev.libjadx.core.symbols.SymbolRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReplacementCostTest {
	@TempDir Path dir;
	@Test void measureActualServiceNoopAndOneReplacementPerBatch() throws Exception {
		var rows = new ArrayList<Map<String,Object>>();
		for (int size : List.of(16, 128, 512)) {
			Path root = Files.createDirectories(dir.resolve("n" + size)); Path source = root.resolve("Batch.java");
			var program = new StringBuilder("package cost; public class Batch {}\n");
			for (int c = 0; c < size; c++) {
				program.append("class C").append(c).append(" {");
				for (int m = 0; m < 8; m++) program.append("public int f").append(m).append("(int v) { return v+").append(m).append("; }");
				program.append("}\n");
			}
			Files.writeString(source, program); var inputs = List.of(SymbolFixtureSupport.compile(root, source, "cost.jar"));
			for (boolean hot : List.of(false, true)) for (int items : List.of(0, 1, 64)) {
				AtomicInteger creations = new AtomicInteger(); long[] timings = new long[4];
				try (var runtime = new ProjectRuntime(null, inputs, args -> {
					int sequence = creations.incrementAndGet(); long construction = System.nanoTime();
					var real = new ProjectRuntime.JadxProjectEngine(args, stage -> {
						if (sequence > 1) timings[stage.ordinal() + 1] = System.nanoTime();
					});
					if (sequence > 1) timings[0] = System.nanoTime() - construction;
					return real;
				})) {
					runtime.initializeAsync(null).get(20, TimeUnit.SECONDS);
					if (hot) runtime.decompiler().getClasses().forEach(c -> c.getCode());
					var edits = new ArrayList<EditDtos.Operation>();
					for (int i = 0; i < Math.max(1, items); i++) edits.add(new EditDtos.Operation(EditDtos.Kind.RENAME,
							new SymbolRef(SymbolRef.Kind.METHOD, "Lcost/C" + i / 8 + ";", null, "f" + i % 8, "(I)I"),
							items == 0 ? "f0" : "edited" + i, null, null));
					for (var pool : java.lang.management.ManagementFactory.getMemoryPoolMXBeans()) pool.resetPeakUsage();
					long heapBefore = java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
					long[] bound = new long[1]; runtime.replacementHook(stage -> { if (stage == ProjectRuntime.ReplacementStage.LOADED) bound[0] = System.nanoTime(); });
					long start = System.nanoTime(); var receipt = new EditBatchService(runtime).apply(new EditDtos.Request(null, null, edits)); long elapsed = System.nanoTime() - start;
					assertEquals(items == 0 ? "NO_CHANGE" : "APPLIED", receipt.outcome()); assertEquals(items == 0 ? 1 : 2, creations.get());
					long peak = java.lang.management.ManagementFactory.getMemoryPoolMXBeans().stream().filter(p -> p.getType() == java.lang.management.MemoryType.HEAP).mapToLong(p -> p.getPeakUsage().getUsed()).sum();
					var row = new LinkedHashMap<String,Object>();
					row.put("classes", size + 1); row.put("hot", hot); row.put("items", items); row.put("serviceMs", elapsed / 1e6);
					row.put("constructionMs", timings[0] / 1e6); row.put("censusMs", items == 0 ? 0 : (timings[2] - timings[1]) / 1e6);
					row.put("loadMs", items == 0 ? 0 : (timings[3] - timings[2]) / 1e6); row.put("bindMs", items == 0 ? 0 : (bound[0] - timings[3]) / 1e6);
					row.put("heapBeforeBytes", heapBefore); row.put("heapPeakBytes", peak); row.put("heapAfterBytes", java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
					rows.add(row);
				}
			}
		}
		Path output = Path.of("build/replacement-publication-probe"); Files.createDirectories(output);
		Files.writeString(output.resolve("performance.json"), new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(rows));
	}
}
