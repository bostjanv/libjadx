package dev.libjadx.app;

import java.util.ArrayList;
import java.util.List;

import dev.libjadx.core.EffectiveAnalysisConfig;
import dev.libjadx.core.source.DecompileRequest;
import dev.libjadx.core.source.DecompileResult;
import dev.libjadx.core.symbols.SymbolCatalog;
import dev.libjadx.core.symbols.SymbolLookup;
import dev.libjadx.core.symbols.SymbolRef;
import dev.libjadx.core.symbols.SymbolResolution;
import dev.libjadx.jadxadapter.JadxSourceAdapter;
import dev.libjadx.jadxadapter.JadxSymbolAdapter;
import jadx.api.JadxDecompiler;

/** One-operation source service; resolution, code and metadata share the same engine lease. */
public final class DecompiledSourceService {
	private final ProjectRuntime runtime;
	private final SymbolCatalogProvider catalogs;

	public DecompiledSourceService(ProjectRuntime runtime, SymbolCatalogProvider catalogs) {
		this.runtime = runtime;
		this.catalogs = catalogs;
	}

	public DecompileResult decompile(DecompileRequest request) throws Exception {
		while (true) {
			String requestedMode = request.decompilationMode();
			String primaryMode = runtime.sourceRoutingSettings().decompilationMode();
			if (requestedMode == null || requestedMode.equals(primaryMode)) {
				try {
					return runtime.withPrimarySymbolRead(request.ref().originalClassDescriptor(), context -> {
						// Settings can change between routing and admission. Retry with the admitted version.
						if (requestedMode != null && !requestedMode.equals(context.settings().decompilationMode()))
							throw new SourceModeChangedException();
						return execute(context.decompiler(), catalogs.primary(context), request, context.revisions().sessionId(),
								context.revisions().logicalRevision(), context.publicationEpoch(), context.settings());
					});
				} catch (SourceModeChangedException changed) {
					continue;
				}
			}
			return runtime.withTemporarySourceRead(new EffectiveAnalysisConfig(requestedMode), context -> {
				SymbolCatalog catalog = catalogs.temporary(context.decompiler(), context.revisions(), context.publicationEpoch());
				return execute(context.decompiler(), catalog, request, context.revisions().sessionId(),
						context.revisions().logicalRevision(), context.publicationEpoch(), context.settings());
			}).value();
		}
	}

	private static final class SourceModeChangedException extends RuntimeException { }

	private static DecompileResult execute(JadxDecompiler jadx, SymbolCatalog catalog, DecompileRequest request,
			String sessionId, long revision, long epoch, EffectiveAnalysisConfig settings) {
		if (request.expectedSessionId() != null
				&& (!sessionId.equals(request.expectedSessionId()) || revision != request.expectedLogicalRevision()))
			throw new StaleSourceException();
		SymbolRef ref = request.ref();
		SymbolResolution resolved = SymbolLookup.resolve(catalog, ref, entry -> {
			var cls = JadxSymbolAdapter.visibleClass(jadx, ref.originalClassDescriptor(), entry.occurrence());
			if (cls == null) throw new IllegalStateException("Visible class vanished during leased source lookup");
			return JadxSymbolAdapter.matchingMembers(cls, ref);
		});
		DecompileResult.EffectiveSettings effective = new DecompileResult.EffectiveSettings(
				settings.decompilationMode(), settings.fingerprint());
		if (resolved.outcome() != SymbolResolution.Outcome.RESOLVED) {
			if (request.expectedSourceSnapshotId() != null) throw new StaleSourceException();
			return new DecompileResult(resolved.outcome(), ref, null, resolved.candidates(), sessionId, revision,
					null, effective, DecompileResult.Status.UNAVAILABLE, "JAVA", null, null, null, null,
					List.of(), null, null, resolved.diagnostics(), unavailableCapabilities());
		}
		SymbolCatalog.Entry entry = catalog.matching(ref.originalClassDescriptor()).getFirst();
		var cls = JadxSymbolAdapter.visibleClass(jadx, ref.originalClassDescriptor(), entry.occurrence());
		if (cls == null) throw new IllegalStateException("Visible class vanished during leased source lookup");
		JadxSourceAdapter.SourceData data = JadxSourceAdapter.extract(jadx, cls, ref, request.includeAnnotations(),
				sessionId, revision, epoch, settings.fingerprint());
		if (request.expectedSourceSnapshotId() != null
				&& !request.expectedSourceSnapshotId().equals(data.sourceSnapshotId())) throw new StaleSourceException();
		boolean method = ref.kind() == SymbolRef.Kind.METHOD;
		List<String> diagnostics = new ArrayList<>(resolved.diagnostics());
		diagnostics.addAll(data.diagnostics());
		if (request.includeAnnotations()) diagnostics.add("Jadx does not certify complete declaration or reference coverage");
		if (request.includeRawDebugLines()) diagnostics.add("Original debug-line mapping is unverified for pinned Jadx 1.5.6");
		if (!request.includeAnnotations()) diagnostics.add("Source annotations were omitted by request");
		List<String> boundedDiagnostics = List.copyOf(diagnostics.subList(0, Math.min(16, diagnostics.size())));
		if (request.strict() && (data.status() != DecompileResult.Status.COMPLETE
				|| method && data.methodRange() == null || request.includeRawDebugLines()
				|| request.includeAnnotations() && (data.declarationAvailability() != DecompileResult.Availability.EXACT
						|| data.referenceAvailability() != DecompileResult.Availability.EXACT)))
			throw new IncompleteAnalysisException(boundedDiagnostics);
		DecompileResult.Availability source = data.source() == null
				? DecompileResult.Availability.UNAVAILABLE : DecompileResult.Availability.EXACT;
		DecompileResult.Capabilities capabilities = new DecompileResult.Capabilities(source,
				data.declarationAvailability(), data.referenceAvailability(),
				method && data.methodRange() != null ? DecompileResult.Availability.EXACT : DecompileResult.Availability.UNAVAILABLE,
				DecompileResult.Availability.UNAVAILABLE, DecompileResult.Availability.UNAVAILABLE,
				DecompileResult.Availability.UNKNOWN);
		return new DecompileResult(resolved.outcome(), ref, data.ownerRef(), resolved.candidates(), sessionId,
				revision, data.sourceSnapshotId(), effective, data.status(), "JAVA", data.source(),
				method ? data.methodRange() != null : null, data.methodRange(), data.methodSource(),
				data.annotations(), null, null, boundedDiagnostics, capabilities);
	}

	private static DecompileResult.Capabilities unavailableCapabilities() {
		return new DecompileResult.Capabilities(DecompileResult.Availability.UNAVAILABLE,
				DecompileResult.Availability.UNAVAILABLE, DecompileResult.Availability.UNAVAILABLE,
				DecompileResult.Availability.UNAVAILABLE, DecompileResult.Availability.UNAVAILABLE,
				DecompileResult.Availability.UNAVAILABLE, DecompileResult.Availability.UNKNOWN);
	}

	public static final class StaleSourceException extends RuntimeException { }
	public static final class IncompleteAnalysisException extends RuntimeException {
		private final List<String> diagnostics;
		public IncompleteAnalysisException(List<String> diagnostics) {
			super("Requested Java source or metadata coverage is unavailable");
			this.diagnostics = diagnostics;
		}
		public List<String> diagnostics() { return diagnostics; }
	}
}
