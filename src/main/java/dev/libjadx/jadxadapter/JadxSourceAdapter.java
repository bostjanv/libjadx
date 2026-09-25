package dev.libjadx.jadxadapter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import dev.libjadx.core.source.DecompileResult;
import dev.libjadx.core.source.MethodRangeVerifier;
import dev.libjadx.core.source.SourceCoordinates;
import dev.libjadx.core.source.SourceSnapshot;
import dev.libjadx.core.symbols.SymbolRef;
import jadx.api.ICodeInfo;
import jadx.api.CommentsLevel;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;
import jadx.api.JavaField;
import jadx.api.JavaMethod;
import jadx.api.JavaNode;
import jadx.api.metadata.ICodeAnnotation;
import jadx.api.metadata.ICodeNodeRef;
import jadx.api.metadata.annotations.NodeDeclareRef;
import jadx.api.metadata.annotations.NodeEnd;
import jadx.core.dex.attributes.AType;
import jadx.core.dex.attributes.nodes.JadxCommentsAttr;
import jadx.core.dex.attributes.nodes.JadxError;
import jadx.core.dex.attributes.nodes.NotificationAttrNode;

/** Jadx 1.5.6 source/metadata conversion; all returned values are immutable. */
public final class JadxSourceAdapter {
	private static final int MAX_SOURCE_BYTES = 4 * 1024 * 1024;
	private static final int MAX_ANNOTATIONS = 20_000;
	private JadxSourceAdapter() { }

	public static SourceData extract(JadxDecompiler jadx, JavaClass declaringClass, SymbolRef requested,
			boolean includeAnnotations, String sessionId, long revision, long publicationEpoch, String settingsFingerprint) {
		if (declaringClass.isNoCode()) return SourceData.unavailable("Jadx does not emit standalone source for this class");
		JavaClass owner = declaringClass.getTopParentClass();
		if (owner == null || owner.isNoCode()) return SourceData.unavailable("Emitted source owner is unavailable");
		SymbolRef ownerRef = JadxSymbolAdapter.originalRef(owner);
		if (ownerRef == null) return SourceData.unavailable("Emitted source owner has no verified original descriptor");
		ICodeInfo code = owner.getCodeInfo();
		// Top-parent decompilation may mark an anonymous/inlined child DONT_GENERATE.
		if (declaringClass.isNoCode()) return SourceData.unavailable("Jadx does not emit standalone source for this class");
		String source = code.getCodeStr();
		if (source == null || source.isBlank()) return SourceData.unavailable("Jadx produced no Java source for the selected class");
		requireSourceWithinLimit(source);
		String snapshot = SourceSnapshot.id(sessionId, revision, publicationEpoch, settingsFingerprint,
				ownerRef.originalClassDescriptor(), source);
		SourceCoordinates coordinates = new SourceCoordinates(source);
		Map<Integer, ICodeAnnotation> metadata = code.getCodeMetadata().getAsMap();
		List<DecompileResult.Annotation> annotations = new ArrayList<>();
		int declarations = 0;
		int references = 0;
		int omitted = 0;
		if (includeAnnotations) {
			for (Map.Entry<Integer, ICodeAnnotation> entry : metadata.entrySet()) {
				ICodeAnnotation annotation = entry.getValue();
				boolean declaration = annotation instanceof NodeDeclareRef;
				boolean reference = annotation.getAnnType() == ICodeAnnotation.AnnType.CLASS
						|| annotation.getAnnType() == ICodeAnnotation.AnnType.METHOD
						|| annotation.getAnnType() == ICodeAnnotation.AnnType.FIELD;
				if (!declaration && !reference) continue;
				int offset = entry.getKey();
				if (!coordinates.isBoundary(offset) || offset == source.length()) { omitted++; continue; }
				ICodeNodeRef target = declaration ? ((NodeDeclareRef) annotation).getNode()
						: annotation instanceof ICodeNodeRef ref ? ref : null;
				if (target == null) { omitted++; continue; }
				JavaNode node = jadx.getJavaNodeByRef(target);
				SymbolRef targetRef = node == null ? null : JadxSymbolAdapter.originalRef(node);
				String token = displayedToken(node);
				if (targetRef == null || token == null || !source.startsWith(token, offset)
						|| offset > 0 && Character.isJavaIdentifierPart(source.charAt(offset - 1))
						|| (offset + token.length() < source.length()
								&& Character.isJavaIdentifierPart(source.charAt(offset + token.length())))) {
					omitted++;
					continue;
				}
				requireAnnotationCapacity(annotations.size());
				annotations.add(new DecompileResult.Annotation(declaration ? "DECLARATION" : "REFERENCE",
						coordinates.point(offset), targetRef, "EXACT", snapshot));
				if (declaration) declarations++;
				else references++;
			}
			annotations.sort(Comparator.comparingInt((DecompileResult.Annotation a) -> a.position().offsetUtf16())
					.thenComparing(DecompileResult.Annotation::kind)
					.thenComparing(a -> a.targetRef().toString()));
			Set<DecompileResult.Annotation> unique = new HashSet<>(annotations);
			if (unique.size() != annotations.size()) annotations = new ArrayList<>(unique);
			annotations.sort(Comparator.comparingInt((DecompileResult.Annotation a) -> a.position().offsetUtf16())
					.thenComparing(DecompileResult.Annotation::kind)
					.thenComparing(a -> a.targetRef().toString()));
		}
		SourceCoordinates.Range methodRange = null;
		if (requested.kind() == SymbolRef.Kind.METHOD) {
			JavaMethod method = JadxSymbolAdapter.matchingMethod(declaringClass, requested);
			if (method != null && !method.isClassInit()) {
				for (Map.Entry<Integer, ICodeAnnotation> entry : metadata.entrySet()) {
					if (entry.getValue() instanceof NodeDeclareRef declaration
							&& declaration.getNode() == method.getCodeNodeRef()) {
						String name = displayedToken(method);
						methodRange = MethodRangeVerifier.verify(source, entry.getKey(), name, metadata,
								declaration, NodeEnd.VALUE, snapshot);
						break;
					}
				}
			}
		}
		List<String> diagnostics = new ArrayList<>();
		if (omitted > 0) diagnostics.add("Some Jadx annotations were omitted because their target or source token could not be verified");
		if (requested.kind() == SymbolRef.Kind.METHOD && methodRange == null)
			diagnostics.add("Method declaration or complete boundary could not be verified in emitted class source");
		boolean knownError = appendNodeDiagnostics(owner.getClassNode(), diagnostics);
		for (JavaMethod method : owner.getMethods()) knownError |= appendNodeDiagnostics(method.getMethodNode(), diagnostics);
		if (knownError && diagnostics.stream().noneMatch(value -> value.startsWith("Jadx error:")))
			diagnostics.add("Jadx recorded a class or method decompilation error; available Java may be partial");
		diagnostics.add("Class-local error count and original debug-line origin are not verified in pinned Jadx");
		return new SourceData(ownerRef, source, snapshot, methodRange,
				methodRange == null ? null : source.substring(methodRange.startOffsetUtf16(), methodRange.endOffsetUtf16()),
				List.copyOf(annotations), List.copyOf(diagnostics), knownError ? DecompileResult.Status.PARTIAL : DecompileResult.Status.COMPLETE,
				declarations > 0 ? DecompileResult.Availability.PARTIAL : DecompileResult.Availability.UNKNOWN,
				references > 0 ? DecompileResult.Availability.PARTIAL : DecompileResult.Availability.UNKNOWN);
	}

	private static boolean appendNodeDiagnostics(NotificationAttrNode node, List<String> diagnostics) {
		List<JadxError> errors = node.getAll(AType.JADX_ERROR);
		TreeSet<String> selected = new TreeSet<>();
		int remaining = Math.max(0, 12 - diagnostics.size());
		if (remaining == 0) return !errors.isEmpty();
		for (JadxError error : errors) {
			select(selected, "Jadx error: " + sanitize(error.getError()), remaining);
		}
		JadxCommentsAttr comments = node.get(AType.JADX_COMMENTS);
		if (comments != null) {
			for (String warning : comments.getComments().getOrDefault(CommentsLevel.WARN, Set.of())) {
				select(selected, "Jadx warning: " + sanitize(warning), remaining);
			}
		}
		diagnostics.addAll(selected);
		return !errors.isEmpty();
	}

	private static void select(TreeSet<String> values, String value, int limit) {
		if (limit == 0) return;
		values.add(value);
		if (values.size() > limit) values.pollLast();
	}

	private static String sanitize(String value) {
		String flattened = value.substring(0, Math.min(value.length(), 512))
				.replaceAll("[\\p{Cntrl}\\p{Zl}\\p{Zp}]", " ").trim();
		return flattened.length() <= 200 ? flattened : flattened.substring(0, 200);
	}

	private static String displayedToken(JavaNode node) {
		if (node instanceof JavaClass cls) return cls.getName();
		if (node instanceof JavaMethod method) {
			if (method.isClassInit()) return null;
			return method.isConstructor() ? method.getDeclaringClass().getName() : method.getName();
		}
		if (node instanceof JavaField field) return field.getName();
		return null;
	}

	static void requireSourceWithinLimit(String source) {
		if (source.getBytes(StandardCharsets.UTF_8).length > MAX_SOURCE_BYTES) throw new SourceLimitException();
	}

	static void requireAnnotationCapacity(int alreadyReturned) {
		if (alreadyReturned >= MAX_ANNOTATIONS) throw new AnnotationLimitException();
	}

	public record SourceData(SymbolRef ownerRef, String source, String sourceSnapshotId,
			SourceCoordinates.Range methodRange, String methodSource, List<DecompileResult.Annotation> annotations,
			List<String> diagnostics, DecompileResult.Status status,
			DecompileResult.Availability declarationAvailability,
			DecompileResult.Availability referenceAvailability) {
		static SourceData unavailable(String reason) {
			return new SourceData(null, null, null, null, null, List.of(), List.of(reason),
					DecompileResult.Status.UNAVAILABLE, DecompileResult.Availability.UNAVAILABLE,
					DecompileResult.Availability.UNAVAILABLE);
		}
	}

	public static final class SourceLimitException extends RuntimeException { }
	public static final class AnnotationLimitException extends RuntimeException { }
}
