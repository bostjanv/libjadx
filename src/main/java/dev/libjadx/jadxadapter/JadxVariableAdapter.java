package dev.libjadx.jadxadapter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import dev.libjadx.core.source.DecompileResult.Variable;
import dev.libjadx.core.source.MethodRangeVerifier;
import dev.libjadx.core.source.SourceCoordinates;
import dev.libjadx.core.symbols.SymbolRef;
import jadx.api.JadxDecompiler;
import jadx.api.JavaMethod;
import jadx.api.metadata.ICodeAnnotation;
import jadx.api.metadata.annotations.NodeDeclareRef;
import jadx.api.metadata.annotations.NodeEnd;
import jadx.api.metadata.annotations.VarNode;
import jadx.core.dex.attributes.AType;
import jadx.core.dex.nodes.MethodNode;

/** Pinned MethodGen emits argument definitions in semantic argument order, excluding this.
 * We accept only complete plain signatures, never infer identity from a displayed name.
 * VarNode remains usable after ProcessClass unloads the mutable argument registers. */
public final class JadxVariableAdapter {
	private JadxVariableAdapter() { }
	private record Declaration(int offset, VarNode variable) { }

	public static List<Variable> extract(JadxDecompiler engine, String source, String snapshot,
			Map<Integer, ICodeAnnotation> metadata, boolean structuredMode) {
		if (metadata.size() > 200_000) throw new JadxSourceAdapter.AnnotationLimitException();
		Map<MethodNode, List<Declaration>> variables = new HashMap<>();
		Map<MethodNode, Map.Entry<Integer, ICodeAnnotation>> methods = new HashMap<>();
		int count = 0;
		for (var entry : metadata.entrySet()) {
			if (!(entry.getValue() instanceof NodeDeclareRef declaration)) continue;
			if (declaration.getNode() instanceof VarNode var) {
				if (++count > 20_000) throw new JadxSourceAdapter.AnnotationLimitException();
				variables.computeIfAbsent(var.getMth(), ignored -> new ArrayList<>()).add(new Declaration(entry.getKey(), var));
			} else if (declaration.getNode() instanceof MethodNode method) methods.put(method, entry);
		}
		SourceCoordinates coordinates = new SourceCoordinates(source);
		List<Variable> result = new ArrayList<>();
		for (var group : variables.entrySet()) {
			MethodNode method = group.getKey();
			var javaNode = engine.getJavaNodeByRef(method);
			if (!(javaNode instanceof JavaMethod javaMethod)) continue;
			SymbolRef ref = JadxSymbolAdapter.originalRef(javaMethod);
			if (ref == null) continue;
			List<Declaration> declarations = group.getValue();
			declarations.sort(Comparator.comparingInt(Declaration::offset));
			var methodDeclaration = methods.get(method);
			int start = -1;
			int end = -1;
			boolean supported = false;
			if (methodDeclaration != null && !javaMethod.isConstructor() && !javaMethod.isClassInit()) {
				int nameOffset = methodDeclaration.getKey();
				var range = MethodRangeVerifier.verify(source, nameOffset, javaMethod.getName(), metadata,
						methodDeclaration.getValue(), NodeEnd.VALUE, snapshot);
				start = nameOffset + javaMethod.getName().length();
				// Fail closed for annotations/nested parameter syntax, skipped/transformed signatures,
				// bodyless methods, partial code and nonstructured modes.
				if (range != null && start < source.length() && source.charAt(start) == '(') {
					end = source.indexOf(')', start);
					String header = end < 0 ? "" : source.substring(start + 1, end);
					supported = structuredMode && end > start && end < range.endOffsetUtf16()
							&& header.indexOf('(') < 0 && header.indexOf('@') < 0
							&& header.indexOf('{') < 0 && header.indexOf('"') < 0
							&& !method.getAccessFlags().isSynthetic() && !method.getAccessFlags().isBridge()
							&& method.getAll(AType.JADX_ERROR).isEmpty()
							&& method.getArgTypes().equals(method.getMethodInfo().getArgumentsTypes());
				}
			}
			if (start < 0 || end <= start) continue; // Cannot truthfully classify this signature.
			int parameterCount = 0;
			boolean exact = true;
			var keys = new HashSet<String>();
			for (Declaration declaration : declarations) {
				VarNode var = declaration.variable();
				exact &= tokenExact(source, coordinates, declaration.offset(), var.getName());
				exact &= keys.add(var.getReg() + ":" + var.getSsa());
				if (declaration.offset() > start && declaration.offset() < end) parameterCount++;
			}
			supported &= exact && parameterCount == method.getMethodInfo().getArgumentsTypes().size();
			int parameterIndex = 0;
			for (Declaration declaration : declarations) {
				String name = declaration.variable().getName();
				if (!tokenExact(source, coordinates, declaration.offset(), name)) continue;
				boolean parameter = declaration.offset() > start && declaration.offset() < end;
				result.add(new Variable(parameter ? "PARAMETER" : "LOCAL", ref,
						parameter && supported ? parameterIndex++ : null, name,
						coordinates.range(declaration.offset(), declaration.offset() + name.length(), snapshot),
						snapshot, parameter && supported ? "SUPPORTED" : "UNSUPPORTED"));
			}
		}
		result.sort(Comparator.comparingInt(v -> v.range().startOffsetUtf16()));
		return List.copyOf(result);
	}

	private static boolean tokenExact(String source, SourceCoordinates coordinates, int offset, String name) {
		return name != null && !name.isEmpty() && coordinates.isBoundary(offset)
				&& coordinates.isBoundary(offset + name.length()) && source.startsWith(name, offset)
				&& (offset == 0 || !Character.isJavaIdentifierPart(source.charAt(offset - 1)))
				&& (offset + name.length() == source.length() || !Character.isJavaIdentifierPart(source.charAt(offset + name.length())));
	}
}
