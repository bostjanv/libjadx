package dev.libjadx.project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import com.google.gson.JsonObject;

import dev.libjadx.core.ProjectSnapshot;
import dev.libjadx.core.RevisionState;
import jadx.api.data.impl.JadxCodeData;

/** Serial, headless owner of native project state and its on-disk baseline. */
public final class NativeProjectRepository {
	private final String sessionId;
	private final List<Path> allowedRoots;
	private final List<Path> inputs;
	private final Consumer<Runnable> fingerprintLauncher;
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
			NativeProjectDocument document, Consumer<Runnable> fingerprintLauncher) throws IOException {
		this.sessionId = UUID.randomUUID().toString();
		this.fingerprintLauncher = fingerprintLauncher;
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
		return open(project, roots, task -> Thread.ofVirtual().name("libjadx-input-fingerprint").start(task));
	}

	static NativeProjectRepository open(Path project, List<Path> roots, Consumer<Runnable> fingerprintLauncher) throws IOException {
		NativeProjectDocument document = NativeProjectDocument.open(project);
		return new NativeProjectRepository(project.toRealPath(), document.getInputFiles(), roots, document, fingerprintLauncher);
	}

	public static NativeProjectRepository fromInputs(List<Path> inputs, List<Path> roots) throws IOException {
		if (inputs.isEmpty()) throw new IllegalArgumentException("At least one input is required");
		return new NativeProjectRepository(null, inputs, roots, null,
				task -> Thread.ofVirtual().name("libjadx-input-fingerprint").start(task));
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

	public synchronized ProjectSnapshot commitMappingsPath(MappingCandidate candidate) throws IOException {
		checkRevision(candidate.expectedRevision());
		if (!FileFingerprint.of(candidate.path()).equals(candidate.baseline())) {
			throw new ExternalModificationException("Mappings changed during rebuild; retry with a fresh revision");
		}
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
		if (document == null) {
			try {
				toSave.saveNew();
			} catch (FileAlreadyExistsException conflict) {
				throw new ExternalModificationException("Save target was created by another process; choose an unused .jadx path");
			}
		} else {
			toSave.save();
		}
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

	/** Reads and validates a replacement without changing the published project. */
	public synchronized ReloadCandidate prepareReload(boolean discardUnsaved, String expectedSessionId,
			long expectedRevision) throws IOException {
		checkSession(expectedSessionId);
		checkRevision(expectedRevision);
		if (currentPath() == null) throw new IllegalStateException("Raw input has no saved native project to reload");
		if (dirty() && !discardUnsaved) throw new UnsavedChangesException();
		Path path = currentPath();
		FileFingerprint beforeOpen = FileFingerprint.of(path);
		NativeProjectDocument reopened = NativeProjectDocument.open(path);
		FileFingerprint afterOpen = FileFingerprint.of(path);
		if (!beforeOpen.equals(afterOpen)) {
			throw new ExternalModificationException("Native project changed during reload; retry with a fresh revision");
		}
		List<Path> reopenedInputs = new ArrayList<>();
		for (Path input : reopened.getInputFiles()) reopenedInputs.add(checkedReference(input));
		if (!reopenedInputs.equals(inputs)) {
			throw new IllegalArgumentException("Reload would change the fixed input identity");
		}
		if (reopened.getMappingsPath() != null) checkedReference(reopened.getMappingsPath());
		return new ReloadCandidate(reopened, afterOpen,
				FileFingerprint.of(reopened.getMappingsPath()), expectedRevision);
	}

	/** Publishes a staged native document after its matching Jadx engine has loaded. */
	public synchronized ProjectSnapshot commitReload(ReloadCandidate candidate) throws IOException {
		checkRevision(candidate.expectedRevision());
		if (!FileFingerprint.of(candidate.document().getProjectPath()).equals(candidate.projectFingerprint())
				|| !FileFingerprint.of(candidate.document().getMappingsPath()).equals(candidate.mappingFingerprint())) {
			throw new ExternalModificationException("Native project or mappings changed during reload; retry with a fresh revision");
		}
		document = candidate.document();
		projectBaseline = candidate.projectFingerprint();
		mappingBaseline = candidate.mappingFingerprint();
		baselineMappingsPath = candidate.document().getMappingsPath();
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

	private String identity(List<Path> paths) throws IOException {
		var digest = FileFingerprint.sha256Digest();
		for (Path path : paths) {
			digest.update(path.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
			FileFingerprint fingerprint = FileFingerprint.of(path);
			digest.update((fingerprint.sha256() == null ? "ABSENT" : fingerprint.sha256())
					.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		}
		return "sha256:" + java.util.HexFormat.of().formatHex(digest.digest());
	}

	private void refreshIdentity() {
		long generation = ++identityGeneration;
		List<Path> savedPaths = new ArrayList<>();
		if (currentPath() != null) savedPaths.add(currentPath());
		if (savedMappingsPath != null) savedPaths.add(savedMappingsPath);
		savedPaths.addAll(inputs);
		savedPaths = List.copyOf(savedPaths);
		try {
			long totalBytes = 0;
			for (Path path : inputs) totalBytes += Files.size(path);
			if (totalBytes <= 32L * 1024 * 1024) {
				persistedIdentity = identity(savedPaths);
				persistedIdentityState = "READY";
				return;
			}
		} catch (IOException failure) {
			persistedIdentity = null;
			persistedIdentityState = "FAILED";
			return;
		}
		persistedIdentity = null;
		persistedIdentityState = "PENDING";
		List<Path> immutablePaths = savedPaths;
		fingerprintLauncher.accept(() -> {
			try {
				String computed = identity(immutablePaths);
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
	public record ReloadCandidate(NativeProjectDocument document, FileFingerprint projectFingerprint,
			FileFingerprint mappingFingerprint, long expectedRevision) { }
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
