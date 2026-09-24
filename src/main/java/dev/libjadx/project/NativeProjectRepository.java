package dev.libjadx.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.google.gson.JsonObject;

import dev.libjadx.core.ProjectSnapshot;
import dev.libjadx.core.RevisionState;
import jadx.api.data.impl.JadxCodeData;

/** Serial, headless owner of native project state and its on-disk baseline. */
public final class NativeProjectRepository {
	private final String sessionId;
	private final List<Path> allowedRoots;
	private final List<Path> inputs;
	private NativeProjectDocument document;
	private JadxCodeData rawCodeData = new JadxCodeData();
	private FileFingerprint projectBaseline;
	private FileFingerprint mappingBaseline;
	private FileFingerprint stagedMappingBaseline;
	private Path baselineMappingsPath;
	private Path savedMappingsPath;
	private String savedCodeData;
	private long logicalRevision;
	private long indexRevision;
	private String persistedIdentity;
	private String persistedIdentityState = "PENDING";
	private long identityGeneration;

	private NativeProjectRepository(Path fixedProjectPath, List<Path> inputs, List<Path> allowedRoots,
			NativeProjectDocument document) throws IOException {
		this.sessionId = UUID.randomUUID().toString();
		this.allowedRoots = allowedRoots.stream().map(path -> {
			try { return path.toRealPath(); }
			catch (IOException failure) { throw new IllegalArgumentException("Allowed root cannot be resolved", failure); }
		}).toList();
		this.inputs = inputs.stream().map(path -> {
			try { return checkedReference(path); }
			catch (IOException failure) { throw new IllegalArgumentException("Input cannot be resolved", failure); }
		}).toList();
		this.document = document;
		if (fixedProjectPath != null) checkedReference(fixedProjectPath);
		if (document != null && document.getMappingsPath() != null) checkedReference(document.getMappingsPath());
		this.projectBaseline = FileFingerprint.of(fixedProjectPath);
		this.mappingBaseline = FileFingerprint.of(document == null ? null : document.getMappingsPath());
		this.baselineMappingsPath = document == null ? null : document.getMappingsPath();
		this.savedMappingsPath = baselineMappingsPath;
		this.savedCodeData = codeDataJson();
		refreshIdentity();
	}

	public static NativeProjectRepository open(Path project, List<Path> roots) throws IOException {
		NativeProjectDocument document = NativeProjectDocument.open(project);
		return new NativeProjectRepository(project.toRealPath(), document.getInputFiles(), roots, document);
	}

	public static NativeProjectRepository fromInputs(List<Path> inputs, List<Path> roots) throws IOException {
		if (inputs.isEmpty()) throw new IllegalArgumentException("At least one input is required");
		return new NativeProjectRepository(null, inputs, roots, null);
	}

	public synchronized ProjectSnapshot snapshot() {
		return new ProjectSnapshot(currentPath(), inputs, dirty(),
				new RevisionState(sessionId, logicalRevision, indexRevision, persistedIdentity, persistedIdentityState));
	}

	public synchronized JadxCodeData codeDataCopy() {
		return NativeProjectDocument.copyCodeData(document == null ? rawCodeData : document.getCodeData());
	}

	public synchronized Path mappingsPath() {
		return document == null ? null : document.getMappingsPath();
	}

	/** Validate a native mapping setting without changing the live project. */
	public synchronized MappingCandidate stageMappingsPath(Path mappings, long expectedRevision) throws IOException {
		checkRevision(expectedRevision);
		if (document == null) throw new IllegalArgumentException("Save the raw input as a native project before changing mappings");
		Path checked = mappings == null ? null : checkedReference(mappings);
		NativeProjectDocument candidate = document.withMappingsPath(checked);
		return new MappingCandidate(candidate, checked, FileFingerprint.of(checked), expectedRevision);
	}

	public synchronized MappingCandidate stageMappingsPath(Path mappings, String expectedSessionId,
			long expectedRevision) throws IOException {
		checkSession(expectedSessionId);
		return stageMappingsPath(mappings, expectedRevision);
	}

	public synchronized ProjectSnapshot commitMappingsPath(MappingCandidate candidate) {
		checkRevision(candidate.expectedRevision());
		if (!Objects.equals(mappingsPath(), candidate.path())) {
			document = candidate.document();
			stagedMappingBaseline = candidate.baseline();
			logicalRevision++;
			indexRevision++;
		}
		return snapshot();
	}

	/** Internal edit seam for validated native edits; editing endpoints arrive in Phase 5. */
	public synchronized void replaceCodeData(JadxCodeData edited, long expectedRevision) {
		checkRevision(expectedRevision);
		Objects.requireNonNull(edited, "edited");
		if (document == null) {
			String previous = codeDataJson();
			rawCodeData = NativeProjectDocument.copyCodeData(edited);
			if (!previous.equals(codeDataJson())) {
				logicalRevision++;
				indexRevision++;
			}
			return;
		}
		String previous = codeDataJson();
		document.setCodeData(NativeProjectDocument.copyCodeData(edited));
		if (!previous.equals(codeDataJson())) {
			logicalRevision++;
			indexRevision++;
		}
	}

	public synchronized SaveResult save(Path requestedTarget, Long expectedRevision) throws IOException {
		if (expectedRevision != null) checkRevision(expectedRevision);
		Path target = requestedTarget == null ? currentPath() : requestedTarget.toAbsolutePath().normalize();
		if (target == null) throw new IllegalArgumentException("A .jadx target path is required for a raw input");
		if (!target.getFileName().toString().endsWith(".jadx")) throw new IllegalArgumentException("Native project target must end in .jadx");
		checkWritableTarget(target);
		if (currentPath() != null && !target.equals(currentPath())) {
			throw new IllegalArgumentException("This process is fixed to its startup native project path");
		}
		Path mappings = document == null ? null : document.getMappingsPath();
		if (!FileFingerprint.of(currentPath()).equals(projectBaseline)
				|| !FileFingerprint.of(baselineMappingsPath).equals(mappingBaseline)
				|| (stagedMappingBaseline != null && !FileFingerprint.of(mappings).equals(stagedMappingBaseline))) {
			throw new ExternalModificationException("Native project or mappings changed on disk; pending edits remain in memory");
		}
		if (document != null) {
			List<Path> liveInputs = new ArrayList<>();
			for (Path input : document.getInputFiles()) liveInputs.add(checkedReference(input));
			if (!liveInputs.equals(inputs)) throw new SecurityException("Input reference changed since project startup");
			if (mappings != null) checkedReference(mappings);
		}
		if (currentPath() == null && Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
			throw new ExternalModificationException("Save target already exists; choose an unused .jadx path");
		}
		NativeProjectDocument toSave = document == null
				? NativeProjectDocument.newFromInputs(target, inputs)
				: document.rebasedTo(target);
		if (document == null) toSave.setCodeData(rawCodeData);
		toSave.save();
		if (document == null) document = toSave;
		projectBaseline = FileFingerprint.of(target);
		mappingBaseline = FileFingerprint.of(toSave.getMappingsPath());
		baselineMappingsPath = toSave.getMappingsPath();
		savedMappingsPath = baselineMappingsPath;
		stagedMappingBaseline = null;
		savedCodeData = codeDataJson();
		refreshIdentity();
		return new SaveResult(target, snapshot());
	}

	public synchronized SaveResult save(Path target, String expectedSessionId, Long expectedRevision) throws IOException {
		if (expectedRevision != null) checkSession(expectedSessionId);
		return save(target, expectedRevision);
	}

	public synchronized ProjectSnapshot reload(boolean discardUnsaved) throws IOException {
		if (currentPath() == null) throw new IllegalStateException("Raw input has no saved native project to reload");
		if (dirty() && !discardUnsaved) throw new UnsavedChangesException();
		NativeProjectDocument reopened = NativeProjectDocument.open(currentPath());
		List<Path> reopenedInputs = new ArrayList<>();
		for (Path input : reopened.getInputFiles()) reopenedInputs.add(checkedReference(input));
		if (!reopenedInputs.equals(inputs)) {
			throw new IllegalArgumentException("Reload would change the fixed input identity");
		}
		if (reopened.getMappingsPath() != null) checkedReference(reopened.getMappingsPath());
		document = reopened;
		projectBaseline = FileFingerprint.of(currentPath());
		mappingBaseline = FileFingerprint.of(reopened.getMappingsPath());
		baselineMappingsPath = reopened.getMappingsPath();
		savedMappingsPath = baselineMappingsPath;
		stagedMappingBaseline = null;
		savedCodeData = codeDataJson();
		refreshIdentity();
		logicalRevision++;
		indexRevision++;
		return snapshot();
	}

	public synchronized JsonObject pendingEdits() {
		JsonObject result = new JsonObject();
		result.addProperty("sessionId", sessionId);
		result.addProperty("logicalRevision", logicalRevision);
		result.addProperty("dirty", dirty());
		if (mappingsPath() == null) result.add("mappingsPath", com.google.gson.JsonNull.INSTANCE);
		else result.addProperty("mappingsPath", mappingsPath().toString());
		result.add("codeData", document == null ? new com.google.gson.Gson().toJsonTree(rawCodeData) : document.toJsonTree().get("codeData"));
		return result;
	}

	private boolean dirty() {
		return !savedCodeData.equals(codeDataJson()) || !Objects.equals(savedMappingsPath, mappingsPath());
	}

	private String codeDataJson() {
		return document == null ? new com.google.gson.Gson().toJsonTree(rawCodeData).toString()
				: document.toJsonTree().get("codeData").toString();
	}

	private Path currentPath() {
		return document == null ? null : document.getProjectPath();
	}

	private void checkRevision(long expected) {
		if (expected != logicalRevision) throw new StaleRevisionException(logicalRevision);
	}

	private void checkSession(String expected) {
		if (!sessionId.equals(expected)) throw new StaleRevisionException("Revision belongs to another process session");
	}

	private void checkWritableTarget(Path target) throws IOException {
		Path parent = target.getParent();
		if (parent == null || !Files.isDirectory(parent)) throw new IllegalArgumentException("Save target parent must exist");
		Path realParent = parent.toRealPath();
		Path actual = realParent.resolve(target.getFileName());
		if (allowedRoots.stream().noneMatch(actual::startsWith)) {
			throw new SecurityException("Save target is outside configured allowed roots");
		}
		if (Files.isSymbolicLink(target)) throw new SecurityException("Save target may not be a symbolic link");
	}

	private Path checkedReference(Path path) throws IOException {
		Path real = path.toRealPath();
		if (!Files.isRegularFile(real) || allowedRoots.stream().noneMatch(real::startsWith)) {
			throw new SecurityException("Native project reference is outside configured allowed roots or not a file");
		}
		return real;
	}

	private String identity() throws IOException {
		var digest = FileFingerprint.sha256Digest();
		List<Path> paths = new ArrayList<>();
		if (currentPath() != null) paths.add(currentPath());
		if (document != null && document.getMappingsPath() != null) paths.add(document.getMappingsPath());
		paths.addAll(inputs);
		for (Path path : paths) {
			digest.update(path.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
			FileFingerprint fingerprint = FileFingerprint.of(path);
			digest.update((fingerprint.sha256() == null ? "ABSENT" : fingerprint.sha256())
					.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		}
		return "sha256:" + java.util.HexFormat.of().formatHex(digest.digest());
	}

	private void refreshIdentity() throws IOException {
		long generation = ++identityGeneration;
		long totalBytes = 0;
		for (Path path : inputs) totalBytes += Files.size(path);
		if (totalBytes <= 32L * 1024 * 1024) {
			persistedIdentity = identity();
			persistedIdentityState = "READY";
			return;
		}
		persistedIdentity = null;
		persistedIdentityState = "PENDING";
		Thread.ofVirtual().name("libjadx-input-fingerprint").start(() -> {
			try {
				String computed = identity();
				synchronized (this) {
					if (identityGeneration == generation) {
						persistedIdentity = computed;
						persistedIdentityState = "READY";
					}
				}
			} catch (IOException failure) {
				synchronized (this) {
					if (identityGeneration == generation) persistedIdentityState = "FAILED";
				}
			}
		});
	}

	public record SaveResult(Path path, ProjectSnapshot project) { }
	public record MappingCandidate(NativeProjectDocument document, Path path, FileFingerprint baseline,
			long expectedRevision) { }
	public static final class ExternalModificationException extends IOException {
		public ExternalModificationException(String message) { super(message); }
	}
	public static final class StaleRevisionException extends IllegalStateException {
		public StaleRevisionException(long actual) { super("Logical revision is stale; current revision is " + actual); }
		public StaleRevisionException(String message) { super(message); }
	}
	public static final class UnsavedChangesException extends IllegalStateException {
		public UnsavedChangesException() { super("Unsaved edits require discardUnsaved: true to reload"); }
	}
}
