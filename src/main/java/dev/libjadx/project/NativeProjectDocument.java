package dev.libjadx.project;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jadx.api.data.ICodeComment;
import jadx.api.data.ICodeRename;
import jadx.api.data.IJavaCodeRef;
import jadx.api.data.IJavaNodeRef;
import jadx.api.data.impl.JadxCodeComment;
import jadx.api.data.impl.JadxCodeData;
import jadx.api.data.impl.JadxCodeRef;
import jadx.api.data.impl.JadxCodeRename;
import jadx.api.data.impl.JadxNodeRef;
import jadx.core.utils.GsonUtils;

/**
 * Phase 0 spike for an in-memory view of a native {@code .jadx} document.
 *
 * <p>The native document is kept as a JSON tree so fields unknown to this
 * Jadx version survive edits. Only the native {@code codeData} object is
 * decoded and replaced. Opening this object never writes to disk. Callers must
 * add external-modification detection before exposing {@link #save()} through
 * a service endpoint.
 */
public final class NativeProjectDocument {
	private static final String CODE_DATA_KEY = "codeData";
	private static final Gson CODE_DATA_GSON = GsonUtils.defaultGsonBuilder()
			.registerTypeAdapter(ICodeComment.class, GsonUtils.interfaceReplace(JadxCodeComment.class))
			.registerTypeAdapter(ICodeRename.class, GsonUtils.interfaceReplace(JadxCodeRename.class))
			.registerTypeAdapter(IJavaNodeRef.class, GsonUtils.interfaceReplace(JadxNodeRef.class))
			.registerTypeAdapter(IJavaCodeRef.class, GsonUtils.interfaceReplace(JadxCodeRef.class))
			.create();
	private static final Gson DOCUMENT_GSON = GsonUtils.defaultGsonBuilder().setPrettyPrinting().create();

	private final Path projectPath;
	private final JsonObject root;
	private JadxCodeData codeData;

	private NativeProjectDocument(Path projectPath, JsonObject root) {
		this.projectPath = projectPath.toAbsolutePath().normalize();
		this.root = root;
		this.codeData = readCodeData(root);
	}

	public static NativeProjectDocument open(Path projectPath) throws IOException {
		Objects.requireNonNull(projectPath, "projectPath");
		try (Reader reader = Files.newBufferedReader(projectPath, StandardCharsets.UTF_8)) {
			JsonElement parsed = JsonParser.parseReader(reader);
			if (!parsed.isJsonObject()) {
				throw new IOException("Native Jadx project root must be a JSON object: " + projectPath);
			}
			return new NativeProjectDocument(projectPath, parsed.getAsJsonObject());
		}
	}

	public Path getProjectPath() {
		return projectPath;
	}

	public List<Path> getInputFiles() {
		JsonElement filesElement = root.get("files");
		if (filesElement == null || !filesElement.isJsonArray()) {
			throw new IllegalArgumentException("Native Jadx project files must be a JSON array");
		}
		Path basePath = projectPath.getParent();
		List<Path> files = new ArrayList<>();
		filesElement.getAsJsonArray().forEach(element -> {
			Path path = Path.of(element.getAsString());
			files.add((path.isAbsolute() ? path : basePath.resolve(path)).normalize());
		});
		return List.copyOf(files);
	}

	public Path getMappingsPath() {
		JsonElement mappingsElement = root.get("mappingsPath");
		if (mappingsElement == null || mappingsElement.isJsonNull()) {
			return null;
		}
		Path path = Path.of(mappingsElement.getAsString());
		return (path.isAbsolute() ? path : projectPath.getParent().resolve(path)).normalize();
	}

	/** Returns the current in-memory code data. Mutations remain in memory until save is called. */
	public JadxCodeData getCodeData() {
		return codeData;
	}

	public void setCodeData(JadxCodeData codeData) {
		this.codeData = Objects.requireNonNull(codeData, "codeData");
		JsonObject updated = CODE_DATA_GSON.toJsonTree(codeData).getAsJsonObject();
		JsonObject merged = root.has(CODE_DATA_KEY) && root.get(CODE_DATA_KEY).isJsonObject()
				? root.getAsJsonObject(CODE_DATA_KEY).deepCopy()
				: new JsonObject();
		updated.entrySet().forEach(entry -> merged.add(entry.getKey(), entry.getValue()));
		root.add(CODE_DATA_KEY, merged);
	}

	public JsonObject toJsonTree() {
		setCodeData(codeData);
		return root.deepCopy();
	}

	/** Writes using ordinary native JSON file semantics. This method is the only disk mutation. */
	public void save() throws IOException {
		setCodeData(codeData);
		try (Writer writer = Files.newBufferedWriter(projectPath, StandardCharsets.UTF_8)) {
			DOCUMENT_GSON.toJson(root, writer);
		}
	}

	private static JadxCodeData readCodeData(JsonObject root) {
		JsonElement codeDataElement = root.get(CODE_DATA_KEY);
		if (codeDataElement == null || codeDataElement.isJsonNull()) {
			return new JadxCodeData();
		}
		if (!codeDataElement.isJsonObject()) {
			throw new IllegalArgumentException("Native Jadx project codeData must be a JSON object");
		}
		return CODE_DATA_GSON.fromJson(codeDataElement, JadxCodeData.class);
	}
}
