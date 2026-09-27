package dev.libjadx.jadxadapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import dev.libjadx.core.symbols.SymbolRef;
import jadx.api.JadxDecompiler;
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
import jadx.api.data.impl.JadxNodeRef;

/** Jadx 1.5.6 native edit conversion. Native keys are original node refs, never aliases. */
public final class JadxNativeEditAdapter {
	private JadxNativeEditAdapter() { }

	public record Target(SymbolRef ref, JadxNodeRef nativeRef, String displayName,
			String collisionScope, String argumentDescriptor, boolean editable) { }

	public static List<Target> visibleTargets(JadxDecompiler jadx) {
		List<Target> result = new ArrayList<>();
		for (JavaClass cls : jadx.getClassesWithInners()) {
			if (result.size() >= 200_000) throw new EditLimitException();
			add(result, cls);
			for (JavaMethod method : cls.getMethods()) {
				if (result.size() >= 200_000) throw new EditLimitException();
				add(result, method);
			}
			for (JavaField field : cls.getFields()) {
				if (result.size() >= 200_000) throw new EditLimitException();
				add(result, field);
			}
		}
		return List.copyOf(result);
	}

	public static final class EditLimitException extends RuntimeException {
		public EditLimitException() { super("Jadx-visible edit catalog exceeds 200000 declarations"); }
	}

	private static void add(List<Target> result, JavaNode node) {
		SymbolRef ref;
		try { ref = JadxSymbolAdapter.originalRef(node); }
		catch (IllegalArgumentException unrepresentable) { return; }
		if (ref == null) return;
		JadxNodeRef nativeRef = JadxNodeRef.forJavaNode(node);
		if (nativeRef == null) return;
		String rawOwner = ref.originalClassDescriptor().substring(1, ref.originalClassDescriptor().length() - 1);
		String expectedShortId = switch (ref.kind()) {
			case CLASS -> null;
			case METHOD -> ref.originalName() + ref.originalDescriptor();
			case FIELD -> ref.originalName() + ":" + ref.originalDescriptor();
		};
		if (!rawOwner.replace('/', '.').equals(nativeRef.getDeclaringClass())
				|| !Objects.equals(expectedShortId, nativeRef.getShortId())) return;
		String scope;
		String args = "";
		if (ref.kind() == SymbolRef.Kind.CLASS) {
			int slash = rawOwner.lastIndexOf('/');
			int inner = rawOwner.lastIndexOf('$');
			scope = rawOwner.substring(0, Math.max(slash, inner) + 1);
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
		result.add(new Target(ref, nativeRef, node.getName(), scope, args, editable));
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
