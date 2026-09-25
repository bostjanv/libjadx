package dev.libjadx.app;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;

import dev.libjadx.jadxadapter.JadxSymbolAdapter;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.ICodeInfo;
import jadx.api.JavaClass;
import jadx.api.metadata.ICodeAnnotation;
import jadx.api.metadata.annotations.NodeDeclareRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Executable observations against the pinned Jadx 1.5.6 engine, not a public API guarantee. */
class JadxSourceProbeTest {
	@TempDir Path dir;

	@Test
	void ownedClassHasJavaAndTokenBoundDeclarationMetadata() throws Exception {
		JadxArgs args = new JadxArgs();
		args.getInputFiles().add(SymbolFixtureSupport.compileFixture(dir).toFile());
		try (JadxDecompiler jadx = new JadxDecompiler(args)) {
			jadx.load();
			JavaClass cls = JadxSymbolAdapter.visibleClass(jadx, "Lprobe/SymbolFixture;", 0);
			ICodeInfo code = cls.getCodeInfo();
			String source = code.getCodeStr();
			assertFalse(source.isBlank());
			System.out.println("SOURCE PROBE JAVA:\n" + source);
			for (Map.Entry<Integer, ICodeAnnotation> entry : code.getCodeMetadata().getAsMap().entrySet()) {
				int pos = entry.getKey();
				ICodeAnnotation annotation = entry.getValue();
				String target = annotation instanceof NodeDeclareRef declaration
						? declaration.getNode().getClass().getSimpleName() + "/" + declaration.getDefPos() : "";
				System.out.println("SOURCE PROBE ANN " + pos + " " + annotation.getAnnType() + " " + target
						+ " TOKEN=" + (pos >= 0 && pos < source.length() ? source.substring(pos, Math.min(source.length(), pos + 20)).replace('\n', ' ') : "OUTSIDE"));
			}
			assertTrue(code.getCodeMetadata().getAsMap().entrySet().stream()
					.anyMatch(entry -> entry.getValue() instanceof NodeDeclareRef
							&& entry.getKey() >= 0 && entry.getKey() < source.length()));
		}
	}
}
