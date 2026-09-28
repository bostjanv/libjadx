package dev.libjadx.jadxadapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dev.libjadx.core.symbols.SymbolRef;
import jadx.api.JavaClass;
import jadx.api.JavaField;
import jadx.api.JavaMethod;
import jadx.api.JavaNode;
import jadx.api.data.CommentStyle;
import jadx.api.data.ICodeComment;
import jadx.api.data.ICodeRename;
import jadx.api.data.IJavaNodeRef;
import jadx.api.data.impl.JadxCodeComment;
import jadx.api.data.impl.JadxCodeData;
import jadx.api.data.impl.JadxCodeRename;
import jadx.api.data.impl.JadxCodeRef;
import jadx.api.data.IJavaCodeRef;
import jadx.api.data.impl.JadxNodeRef;

/** Jadx 1.5.6 native edit conversion. Native keys are original node refs, never aliases. */
public final class JadxNativeEditAdapter {
	private JadxNativeEditAdapter() { }

	public record Target(SymbolRef ref, JadxNodeRef nativeRef, String displayName,
			String collisionScope, String argumentDescriptor, boolean editable) { }

	/** Class metadata and native keys do not require loading its members or Java source. */
	public static Target classTarget(JavaClass cls) {
		return target(cls);
	}

	/** May decompile this owner; callers must select only owners requested by the batch. */
	public static List<Target> memberTargets(JavaClass cls) {
		List<Target> result = new ArrayList<>();
		for (JavaMethod method : cls.getMethods()) add(result, method);
		for (JavaField field : cls.getFields()) add(result, field);
		return List.copyOf(result);
	}

	public static String classCollisionScope(SymbolRef ref) {
		String rawOwner = ref.originalClassDescriptor().substring(1, ref.originalClassDescriptor().length() - 1);
		int slash = rawOwner.lastIndexOf('/');
		int inner = rawOwner.lastIndexOf('$');
		return rawOwner.substring(0, Math.max(slash, inner) + 1);
	}

	private static void add(List<Target> result, JavaNode node) {
		if (result.size() >= 200_000) throw new EditLimitException();
		Target target = target(node);
		if (target != null) result.add(target);
	}

	private static Target target(JavaNode node) {
		SymbolRef ref;
		try { ref = JadxSymbolAdapter.originalRef(node); }
		catch (IllegalArgumentException unrepresentable) { return null; }
		if (ref == null) return null;
		JadxNodeRef nativeRef = JadxNodeRef.forJavaNode(node);
		if (nativeRef == null) return null;
		String rawOwner = ref.originalClassDescriptor().substring(1, ref.originalClassDescriptor().length() - 1);
		String expectedShortId = switch (ref.kind()) {
			case CLASS -> null;
			case METHOD -> ref.originalName() + ref.originalDescriptor();
			case FIELD -> ref.originalName() + ":" + ref.originalDescriptor();
		};
		if (!rawOwner.replace('/', '.').equals(nativeRef.getDeclaringClass())
				|| !Objects.equals(expectedShortId, nativeRef.getShortId())) return null;
		String scope;
		String args = "";
		if (ref.kind() == SymbolRef.Kind.CLASS) {
			scope = classCollisionScope(ref);
		} else {
			scope = rawOwner;
			if (ref.kind() == SymbolRef.Kind.METHOD) {
				args = ref.originalDescriptor().substring(0, ref.originalDescriptor().indexOf(')') + 1);
			}
		}
		boolean editable = true;
		if (node instanceof JavaMethod method) {
			editable = !method.isConstructor() && !method.isClassInit()
					&& !method.getAccessFlags().isSynthetic() && !method.getAccessFlags().isBridge();
		} else if (node instanceof JavaField field) {
			editable = !field.getAccessFlags().isSynthetic();
		} else if (node instanceof JavaClass javaClass) {
			editable = !javaClass.getAccessInfo().isSynthetic();
		}
		return new Target(ref, nativeRef, node.getName(), scope, args, editable);
	}

	/** Cold ClassNode.unloadCode skips clearing attributes in 1.5.6. Mapping listeners
	 * re-add declaration comments on every replay, so clear them also on cold nodes. */
	public static void prepareCodeDataReplay(jadx.api.JadxDecompiler engine) {
		for (var cls : engine.getClassesWithInners()) {
			var node = cls.getClassNode();
			node.remove(jadx.core.dex.attributes.AType.CODE_COMMENTS);
			for (var method : node.getMethods()) method.remove(jadx.core.dex.attributes.AType.CODE_COMMENTS);
			for (var field : node.getFields()) field.remove(jadx.core.dex.attributes.AType.CODE_COMMENTS);
		}
	}

	public static final class EditLimitException extends RuntimeException {
		public EditLimitException() { this("Requested edit owners exceed 200000 member declarations"); }
		public EditLimitException(String message) { super(message); }
	}

	public static boolean sameKey(IJavaNodeRef left, IJavaNodeRef right) {
		return left != null && right != null && left.getType() == right.getType()
				&& Objects.equals(left.getDeclaringClass(), right.getDeclaringClass())
				&& Objects.equals(left.getShortId(), right.getShortId());
	}

	public static String existingRename(JadxCodeData code, IJavaNodeRef key) {
		for (ICodeRename rename : code.getRenames()) {
			if (rename.getCodeRef() == null && sameKey(rename.getNodeRef(), key)) return rename.getNewName();
		}
		return null;
	}

	public static long renameCount(JadxCodeData code, IJavaNodeRef key) {
		return code.getRenames().stream().filter(rename -> rename.getCodeRef() == null
				&& sameKey(rename.getNodeRef(), key)).count();
	}

	public static String existingComment(JadxCodeData code, IJavaNodeRef key) {
		for (ICodeComment comment : code.getComments()) {
			if (comment.getCodeRef() == null && comment.getStyle() == CommentStyle.LINE
					&& sameKey(comment.getNodeRef(), key)) return comment.getComment();
		}
		return null;
	}

	public static long lineCommentCount(JadxCodeData code, IJavaNodeRef key) {
		return code.getComments().stream().filter(comment -> comment.getCodeRef() == null
				&& comment.getStyle() == CommentStyle.LINE && sameKey(comment.getNodeRef(), key)).count();
	}

	public static void rename(JadxCodeData code, IJavaNodeRef key, String name) {
		List<ICodeRename> renames = new ArrayList<>(code.getRenames());
		for (int i = 0; i < renames.size(); i++) {
			ICodeRename existing = renames.get(i);
			if (existing.getCodeRef() == null && sameKey(existing.getNodeRef(), key)) {
				renames.set(i, new JadxCodeRename(key, name));
				code.setRenames(renames);
				return;
			}
		}
		renames.add(new JadxCodeRename(key, name));
		code.setRenames(renames);
	}

	public static JadxCodeRef parameterKey(int index) { return JadxCodeRef.forMthArg(index); }

	public static long scopedRenameCount(JadxCodeData code, IJavaNodeRef key, IJavaCodeRef scope) {
		return code.getRenames().stream().filter(r -> sameKey(r.getNodeRef(), key)
				&& Objects.equals(r.getCodeRef(), scope)).count();
	}

	public static String existingScopedRename(JadxCodeData code, IJavaNodeRef key, IJavaCodeRef scope) {
		return code.getRenames().stream().filter(r -> sameKey(r.getNodeRef(), key)
				&& Objects.equals(r.getCodeRef(), scope)).map(ICodeRename::getNewName).findFirst().orElse(null);
	}

	/** GUI VAR renames can also address parameters. Without a proved translation,
	 * reject competing VAR/other scoped records on the method rather than silently override them. */
	public static boolean hasOtherScopedRenames(JadxCodeData code, IJavaNodeRef key) {
		return code.getRenames().stream().anyMatch(r -> sameKey(r.getNodeRef(), key) && r.getCodeRef() != null
				&& r.getCodeRef().getAttachType() != jadx.api.data.CodeRefType.MTH_ARG);
	}

	public static void renameParameter(JadxCodeData code, IJavaNodeRef key, int index, String name) {
		var scope = parameterKey(index);
		List<ICodeRename> renames = new ArrayList<>(code.getRenames());
		renames.removeIf(r -> sameKey(r.getNodeRef(), key) && Objects.equals(r.getCodeRef(), scope));
		renames.add(new JadxCodeRename(key, scope, name));
		code.setRenames(renames);
	}

	public static void comment(JadxCodeData code, IJavaNodeRef key, String value) {
		List<ICodeComment> comments = new ArrayList<>(code.getComments());
		for (int i = 0; i < comments.size(); i++) {
			ICodeComment existing = comments.get(i);
			if (existing.getCodeRef() == null && existing.getStyle() == CommentStyle.LINE
					&& sameKey(existing.getNodeRef(), key)) {
				comments.set(i, new JadxCodeComment(key, value, CommentStyle.LINE));
				code.setComments(comments);
				return;
			}
		}
		comments.add(new JadxCodeComment(key, value, CommentStyle.LINE));
		code.setComments(comments);
	}
}
