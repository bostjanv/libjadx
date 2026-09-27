package dev.libjadx.jadxadapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;

import dev.libjadx.core.references.ReferenceQuery;
import dev.libjadx.core.symbols.SymbolRef;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Counts real pinned-Jadx source conversions for many callees of one caller. */
class JadxReferenceAdapterTest {
    @TempDir Path dir;

    @Test void tenCalleesShareOneCallerSourceExtraction() throws Exception {
        Path classes = Files.createDirectory(dir.resolve("classes"));
        int compiled = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-d", classes.toString(), "tests/fixtures/references/ReferenceFixture.java");
        assertEquals(0, compiled);
        Path jar = dir.resolve("references.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar));
                var files = Files.walk(classes)) {
            for (Path file : files.filter(Files::isRegularFile).sorted(Comparator.naturalOrder()).toList()) {
                output.putNextEntry(new JarEntry(classes.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, output);
                output.closeEntry();
            }
        }
        JadxArgs args = new JadxArgs();
        args.getInputFiles().add(jar.toFile());
        try (JadxDecompiler jadx = new JadxDecompiler(args)) {
            jadx.load();
            var cls = JadxSymbolAdapter.visibleClass(jadx, "Lprobe/ReferenceFixture;", 0);
            var ref = new SymbolRef(SymbolRef.Kind.METHOD, "Lprobe/ReferenceFixture;", null, "fanout", "()V");
            var query = new ReferenceQuery(ref, ReferenceQuery.Direction.OUTGOING, null, 50, null, true, false, null, null);
            AtomicInteger sourceExtractions = new AtomicInteger();
            var edges = JadxReferenceAdapter.extract(jadx, cls, query, "session", 0, 0, "settings",
                    (engine, caller, callerRef, annotations, session, revision, epoch, settings) -> {
                        sourceExtractions.incrementAndGet();
                        return JadxSourceAdapter.extract(engine, caller, callerRef, annotations,
                                session, revision, epoch, settings);
                    });
            assertEquals(10, edges.size());
            assertEquals(10, edges.stream().map(edge -> edge.targetRef().originalName()).distinct().count());
            assertTrue(edges.stream().allMatch(edge -> edge.sourceSites().size() == 1));
            assertEquals(1, sourceExtractions.get(), "One caller's source must be extracted once per query");
        }
    }
}
