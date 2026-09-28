package dev.libjadx.app;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import dev.libjadx.core.mappings.MappingExportDtos;
import dev.libjadx.core.mappings.MappingExportDtos.Request;
import dev.libjadx.core.mappings.MappingExportDtos.Receipt;
import dev.libjadx.jadxadapter.JadxMappingExportAdapter;
import dev.libjadx.project.SafeMappingOutput;
import dev.libjadx.project.NativeProjectRepository;

/** Single synchronous read/export lease, bounded codec, separate safe output transaction. */
public final class MappingExportService {
	private static final System.Logger LOG = System.getLogger(MappingExportService.class.getName());
	private final ProjectRuntime runtime;
	private final SafeMappingOutput.Hook hook;
	private final JadxMappingExportAdapter adapter = new JadxMappingExportAdapter();
	public MappingExportService(ProjectRuntime runtime) { this(runtime, ignored -> { }); }
	MappingExportService(ProjectRuntime runtime, SafeMappingOutput.Hook hook) { this.runtime = runtime; this.hook = hook; }

	public Receipt export(Request request) throws Exception {
		return runtime.withExclusiveMappingExport(request, context -> {
			var source = context.source();
			SafeMappingOutput output = new SafeMappingOutput(request.targetPath(), source.allowedRoots(), source.protectedPaths(), stage -> {
				hook.at(stage);
				if (stage.equals("PUBLICATION")) context.checkBaselines();
			});
			try (output) {
				hook.at("ENCODE");
				var encoded = adapter.encode(context.decompiler(), source.codeData(), source.mappingBytes(), source.hasAttachedMapping());
				output.stage(encoded.bytes());
				hook.at("VERIFY");
				byte[] observed = output.readStaged();
				var counts = adapter.verify(observed, encoded);
				String digest = "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(observed));
				hook.at("BEFORE_BASELINE_CHECK");
				context.checkBaselines();
				output.publish(observed);
				return new Receipt("TINY_V2", request.targetPath().toString(), context.before().revisions().sessionId(),
						context.before().revisions().logicalRevision(), "CURRENT_IN_MEMORY_WITH_ATTACHED_MAPPINGS",
						"VERIFIED_DECLARATIONS", counts, observed.length, digest, List.of(), false);
			} catch (Exception failure) {
				if (output.published()) {
					LOG.log(System.Logger.Level.ERROR, "Mapping output published at " + request.targetPath() + " but completion failed", failure);
					throw new MappingExportDtos.Problem(500, "INTERNAL_ERROR", "Mapping output was published; inspect the reported path before retrying",
							Map.of("targetPath", request.targetPath().toString(), "published", true));
				}
				if (failure instanceof MappingExportDtos.Problem || failure instanceof NativeProjectRepository.ExternalModificationException
						|| failure instanceof SecurityException) throw failure;
				LOG.log(System.Logger.Level.WARNING, "Mapping export failed before publication", failure);
				throw new MappingExportDtos.Problem(500, "INTERNAL_ERROR", "Mapping output failed before publication", Map.of("published", false));
			}
		});
	}
}
