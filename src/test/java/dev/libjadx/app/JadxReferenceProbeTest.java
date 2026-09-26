package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import dev.libjadx.jadxadapter.JadxSymbolAdapter;
import jadx.api.*;
import jadx.api.metadata.ICodeAnnotation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JadxReferenceProbeTest {
    @TempDir Path dir;
    @Test void coldAndOwnerProcessedGraph() throws Exception {
        JadxArgs args = new JadxArgs();
        args.getInputFiles().add(SymbolFixtureSupport.referenceFixture(dir).toFile());
        try (JadxDecompiler jadx = new JadxDecompiler(args)) {
            jadx.load();
            JavaClass cls = JadxSymbolAdapter.visibleClass(jadx, "Lprobe/ReferenceFixture;", 0);
            System.out.println("COLD state=" + cls.getClassNode().getState() + " deps=" + cls.getDependencies());
            var entryNode = cls.getClassNode().getMethods().stream().filter(m -> m.getName().equals("entry")).findFirst().orElseThrow();
            assertEquals(4, entryNode.getUsed().size());
            assertEquals(1, entryNode.getUnresolvedUsed().size());
            System.out.println("COLD used=" + entryNode.getUsed() + " unresolved=" + entryNode.getUnresolvedUsed());
            cls.getCodeInfo();
            JavaMethod entry = method(cls, "entry", "()V");
            JavaMethod helper = method(cls, "helper", "()V");
            System.out.println("PROCESSED used=" + entry.getUsed() + " unresolved=" + entry.getUnresolvedUsed());
            assertTrue(entry.getUsed().contains(helper));
            assertTrue(helper.getUseIn().contains(entry));
            assertEquals(1, entry.getUsed().stream().filter(helper::equals).count());
            assertTrue(entry.getUsed().stream().anyMatch(n -> n instanceof JavaMethod m && m.isConstructor()));
            assertTrue(entry.getUnresolvedUsed().stream().anyMatch(m -> m.getDeclClass().getRawName().equals("probe.MissingDependency")
                    && m.getName().equals("external") && m.getShortId().equals("external(Ljava/lang/String;)Ljava/lang/String;")));
            assertTrue(cls.getFields().stream().filter(f -> List.of("value", "total").contains(f.getName()))
                    .allMatch(f -> f.getUseIn().contains(entry)));
            JavaClass other = JadxSymbolAdapter.visibleClass(jadx, "Lprobe/ReferenceOther;", 0);
            assertTrue(cls.getDependencies().contains(other));
            assertTrue(other.getUseIn().contains(cls));
            assertTrue(method(cls, "helper", "(I)I").callsSelf());
            assertTrue(method(cls, "helper", "(I)I").getUsed().isEmpty());
            System.out.println("RECURSION used=" + method(cls, "helper", "(I)I").getUsed()
                    + " self=" + method(cls, "helper", "(I)I").callsSelf());
            System.out.println("DISPATCH " + method(cls, "dispatch", "(Lprobe/ReferenceApi;Lprobe/ReferenceBase;)V").getUsed());
            System.out.println("OVERRIDES " + method(cls, "invoke", "()V").getOverrideRelatedMethods());
            assertFalse(method(cls, "dispatch", "(Lprobe/ReferenceApi;Lprobe/ReferenceBase;)V").getUsed().contains(method(cls,"invoke","()V")));
            assertFalse(cls.getDependencies().stream().anyMatch(c -> c.getRawName().equals("probe.ReflectiveOnly")));
            for (var m : cls.getMethods()) System.out.println("METHOD " + JadxSymbolAdapter.originalRef(m) + " used=" + m.getUsed());
            var data = dev.libjadx.jadxadapter.JadxSourceAdapter.extract(jadx, cls,
                    JadxSymbolAdapter.originalRef(entry), true, "probe", 0, 0, "settings");
            assertNotNull(data.methodRange());
            var sites = data.annotations().stream().filter(a -> a.kind().equals("REFERENCE")
                    && a.targetRef().equals(JadxSymbolAdapter.originalRef(helper))
                    && cls.getCodeInfo().getCodeMetadata().getNodeAt(a.position().offsetUtf16()) == entry.getCodeNodeRef()).toList();
            assertEquals(2, sites.size());
            for (var site : sites) {
                assertTrue(site.position().offsetUtf16() >= data.methodRange().startOffsetUtf16());
                assertTrue(site.position().offsetUtf16() < data.methodRange().endOffsetUtf16());
            }
            assertTrue(data.annotations().stream().anyMatch(a -> a.kind().equals("REFERENCE")
                    && "value".equals(a.targetRef().originalName())
                    && cls.getCodeInfo().getCodeMetadata().getNodeAt(a.position().offsetUtf16()) == entry.getCodeNodeRef()));
            var javap = new ProcessBuilder("javap", "-c", "-classpath", args.getInputFiles().getFirst().toString(),
                    "probe.ReferenceFixture").redirectErrorStream(true).start();
            String disassembly = new String(javap.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(0, javap.waitFor());
            assertTrue(disassembly.contains("getfield") && disassembly.contains("putfield"));
            System.out.println("JVM DISASSEMBLY\n" + disassembly);
            System.out.println("OFFSETS " + cls.getCodeInfo().getCodeMetadata().getAsMap().values().stream()
                    .filter(a -> a.getAnnType() == ICodeAnnotation.AnnType.OFFSET).toList());
        }
    }
    static JavaMethod method(JavaClass cls, String name, String descriptor) {
        return cls.getMethods().stream().filter(m -> {
            var ref = JadxSymbolAdapter.originalRef(m);
            return ref.originalName().equals(name) && ref.originalDescriptor().equals(descriptor);
        }).findFirst().orElseThrow();
    }
}
