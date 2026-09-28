package dev.libjadx.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.libjadx.core.mappings.MappingImportDtos;
import dev.libjadx.core.mappings.MappingImportDtos.Request;
import dev.libjadx.core.mappings.MappingImportDtos.Receipt;
import dev.libjadx.core.mappings.MappingExportDtos;
import dev.libjadx.jadxadapter.JadxMappingImportAdapter;
import jadx.api.data.impl.JadxCodeRename;
import jadx.api.data.impl.JadxCodeComment;
import dev.libjadx.project.NativeProjectDocument;
import dev.libjadx.project.SafeMappingInput;

/** One admission, entire private plan/staging, final external checks and at most one native commit. */
public final class MappingImportService {
	@FunctionalInterface interface Hook { void at(String stage) throws Exception; }
	private final ProjectRuntime runtime;
	private final Hook hook;
	public MappingImportService(ProjectRuntime runtime) { this(runtime, ignored -> { }); }
	MappingImportService(ProjectRuntime runtime, Hook hook) { this.runtime = runtime; this.hook = hook; }

	public Receipt importMappings(Request request) throws Exception {
		try {
			return runtime.withExclusiveMappingImport(request, context -> {
				var before = context.before();
				var baseline = context.mappingSource();
				List<java.nio.file.Path> protectedPaths = new ArrayList<>(before.inputs());
				if (before.projectPath() != null) protectedPaths.add(before.projectPath());
				var source = new SafeMappingInput(request.sourcePath(), baseline.allowedRoots(), protectedPaths);
				hook.at("CAPTURED");
				var plan = new JadxMappingImportAdapter().plan(context.decompiler(), baseline.codeData(),
						baseline.mappingBytes(), baseline.hasAttachedMapping(), source.bytes());
				// Candidate contains every prior native field. Staging exceptions never publish a prefix.
				var candidate = NativeProjectDocument.copyCodeData(baseline.codeData());
				var renames = new ArrayList<>(candidate.getRenames());
				var comments = new ArrayList<>(candidate.getComments());
				int item = 0;
				for (var change : plan.changes()) {
					hook.at("STAGE_" + item++);
					if (change.alias() != null) renames.add(new JadxCodeRename(change.nativeRef(), change.alias()));
					if (change.comment() != null) comments.add(new JadxCodeComment(change.nativeRef(), change.comment(), jadx.api.data.CommentStyle.LINE));
				}
				candidate.setRenames(renames); candidate.setComments(comments);
				hook.at("BEFORE_COMMIT");
				source.verifyUnchanged();
				context.checkMappingBaselines();
				var after = plan.changes().isEmpty() ? before : context.commit(candidate);
				return new Receipt(request.format(), request.mode(), source.path().toString(), source.sha256(), source.bytes().length,
						plan.parsed(), plan.applied(), plan.unchanged(), before.revisions().sessionId(),
						before.revisions().logicalRevision(), after.revisions().logicalRevision(),
						before.revisions().indexRevision(), after.revisions().indexRevision(), after.dirty(), false, false,
						plan.changes().isEmpty() ? "NO_CHANGE" : "APPLIED", List.of());
			});
		} catch (MappingExportDtos.Problem shared) {
			if (shared.status() == 429) throw MappingImportDtos.limit();
			Object omissions = shared.details().get("omissions");
			String category = "UNSUPPORTED_MAPPING_BASELINE";
			if (omissions instanceof List<?> list && !list.isEmpty() && list.getFirst() instanceof Map<?, ?> entry) {
				category = String.valueOf(entry.get("category"));
			}
			throw MappingImportDtos.unsupported(category, null);
		}
	}
}
