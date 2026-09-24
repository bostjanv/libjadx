package dev.libjadx.jadxadapter;

import java.nio.file.Path;
import java.util.List;

import dev.libjadx.core.EffectiveAnalysisConfig;
import dev.libjadx.project.NativeProjectDocument;
import jadx.api.DecompilationMode;
import jadx.api.JadxArgs;
import jadx.api.data.impl.JadxCodeData;

/** Pinned Jadx 1.5.6 argument construction. Each call owns a deep code-data copy. */
public final class JadxEngineFactory {
	private JadxEngineFactory() { }

	public static JadxArgs arguments(List<Path> inputs, Path mappings, JadxCodeData codeData,
			EffectiveAnalysisConfig config) {
		JadxArgs args = new JadxArgs();
		inputs.forEach(path -> args.getInputFiles().add(path.toFile()));
		if (mappings != null) args.setUserRenamesMappingsPath(mappings);
		args.setCodeData(NativeProjectDocument.copyCodeData(codeData));
		args.setDecompilationMode(DecompilationMode.valueOf(config.decompilationMode()));
		return args;
	}
}
