package dev.libjadx.app;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import dev.libjadx.project.NativeProjectDocument;
import dev.libjadx.project.NativeProjectRepository;
import dev.libjadx.core.ProjectSnapshot;
import dev.libjadx.core.EffectiveAnalysisConfig;
import dev.libjadx.jadxadapter.JadxEngineFactory;
import com.google.gson.JsonObject;
import java.io.IOException;
import jadx.api.data.impl.JadxCodeData;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;

/** Owns one fixed project and publishes its engine only after initialization succeeds. */
public final class ProjectRuntime implements AutoCloseable {
	private final Object lifecycleLock = new Object();
	private final Object operationLock = new Object();
	private final Semaphore temporarySlots = new Semaphore(1);
	private final Path projectPath;
	private final List<Path> inputPaths;
	private final List<Path> allowedRoots;
	private final EngineFactory engineFactory;
	private final Runnable beforePublish;
	private final ExecutorService loader;
	private static final ThreadFactory LOADER_THREADS = r -> {
		Thread thread = new Thread(r, "libjadx-project-loader");
		thread.setDaemon(true);
		return thread;
	};

	private Lifecycle lifecycle = Lifecycle.LOADING;
	private RuntimeStatus status;
	private ProjectEngine activeEngine;
	private NativeProjectRepository repository;
	private EffectiveAnalysisConfig effectiveConfig = EffectiveAnalysisConfig.defaults();
	private int activeTemporaryAnalyses;
	private boolean initializationStarted;
	private InitializationTask initializationTask;
	private int cleanupInProgress;

	public ProjectRuntime(Path projectPath, List<Path> inputPaths) {
		this(projectPath, inputPaths, defaultRoots(projectPath, inputPaths));
	}

	public ProjectRuntime(Path projectPath, List<Path> inputPaths, List<Path> allowedRoots) {
		this(projectPath, inputPaths, allowedRoots, JadxProjectEngine::new, () -> { },
				Executors.newSingleThreadExecutor(LOADER_THREADS));
	}

	ProjectRuntime(Path projectPath, List<Path> inputPaths, EngineFactory engineFactory) {
		this(projectPath, inputPaths, engineFactory, () -> { });
	}

	ProjectRuntime(Path projectPath, List<Path> inputPaths, EngineFactory engineFactory, Runnable beforePublish) {
		this(projectPath, inputPaths, engineFactory, beforePublish, Executors.newSingleThreadExecutor(LOADER_THREADS));
	}

	ProjectRuntime(Path projectPath, List<Path> inputPaths, EngineFactory engineFactory,
			Runnable beforePublish, ExecutorService loader) {
		this(projectPath, inputPaths, defaultRoots(projectPath, inputPaths), engineFactory, beforePublish, loader);
	}

	private ProjectRuntime(Path projectPath, List<Path> inputPaths, List<Path> allowedRoots,
			EngineFactory engineFactory, Runnable beforePublish, ExecutorService loader) {
		this.projectPath = projectPath;
		this.inputPaths = List.copyOf(inputPaths);
		this.allowedRoots = List.copyOf(allowedRoots);
		this.engineFactory = engineFactory;
		this.beforePublish = beforePublish;
		this.loader = loader;
		this.status = status("LOADING", "INITIALIZING", new RuntimeStatus.Progress(0, 1, "project"), null);
	}

	private static List<Path> defaultRoots(Path projectPath, List<Path> inputPaths) {
		java.util.ArrayList<Path> roots = new java.util.ArrayList<>();
		if (projectPath != null && projectPath.toAbsolutePath().getParent() != null) roots.add(projectPath.toAbsolutePath().getParent());
		for (Path input : inputPaths) if (input.toAbsolutePath().getParent() != null) roots.add(input.toAbsolutePath().getParent());
		return roots;
	}

	public ProjectSnapshot projectSnapshot() {
		synchronized (lifecycleLock) {
			requireReady();
			return repository.snapshot();
		}
	}

	public SettingsSnapshot settingsSnapshot() {
		synchronized (lifecycleLock) {
			requireReady();
			return new SettingsSnapshot(repository.mappingsPath(), effectiveConfig, repository.snapshot().revisions());
		}
	}

	public record SettingsSnapshot(Path mappingsPath, EffectiveAnalysisConfig effective,
			dev.libjadx.core.RevisionState revisions) { }

	public JsonObject pendingEdits() {
		synchronized (lifecycleLock) {
			requireReady();
			return repository.pendingEdits();
		}
	}

	public ProjectSnapshot saveProject(Path target, Long expectedRevision) throws IOException {
		return saveProject(target, null, expectedRevision);
	}

	public ProjectSnapshot saveProject(Path target, String expectedSessionId, Long expectedRevision) throws IOException {
		synchronized (operationLock) {
			synchronized (lifecycleLock) {
				requireReady();
				return repository.save(target, expectedSessionId, expectedRevision).project();
			}
		}
	}

	/** Internal edit entry point; callers must validate native edit semantics before invoking it. */
	void replaceCodeData(JadxCodeData edited, long expectedRevision) {
		synchronized (operationLock) {
			synchronized (lifecycleLock) {
				requireReady();
				repository.replaceCodeData(edited, expectedRevision);
				activeEngine.decompiler().getArgs().setCodeData(repository.codeDataCopy());
				activeEngine.decompiler().reloadCodeData();
			}
		}
	}

	/** One isolated, read-only analysis operation over an admitted immutable native edit snapshot. */
	<T> TemporaryResult<T> withTemporaryAnalysis(EffectiveAnalysisConfig override,
			Function<JadxDecompiler, T> operation) throws Exception {
		ProjectEngine temporary = null;
		JadxArgs args;
		ProjectSnapshot snapshot;
		synchronized (operationLock) {
			synchronized (lifecycleLock) {
				requireReady();
				if (!temporarySlots.tryAcquire()) throw new IllegalStateException("Temporary analysis capacity is busy");
				activeTemporaryAnalyses++;
				try {
					snapshot = repository.snapshot();
					args = JadxEngineFactory.arguments(inputPaths, repository.mappingsPath(), repository.codeDataCopy(), override);
				} catch (RuntimeException | Error failure) {
					activeTemporaryAnalyses--;
					temporarySlots.release();
					throw failure;
				}
			}
		}
		try {
			temporary = engineFactory.create(args);
			temporary.load();
			String sourceSnapshotId = sourceSnapshotId(snapshot, override);
			return new TemporaryResult<>(snapshot.revisions(), override, override.fingerprint(), sourceSnapshotId,
					operation.apply(temporary.decompiler()));
		} finally {
			try {
				if (temporary != null) closeOwned(temporary, null);
			} finally {
				synchronized (lifecycleLock) {
					activeTemporaryAnalyses--;
					temporarySlots.release();
					finishShutdownIfQuiescent();
				}
			}
		}
	}

	private static String sourceSnapshotId(ProjectSnapshot snapshot, EffectiveAnalysisConfig config) {
		try {
			var digest = java.security.MessageDigest.getInstance("SHA-256");
			String material = snapshot.revisions().sessionId() + ":" + snapshot.revisions().logicalRevision()
					+ ":" + config.fingerprint();
			return "sha256:" + java.util.HexFormat.of().formatHex(digest.digest(material.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
		} catch (java.security.NoSuchAlgorithmException impossible) {
			throw new IllegalStateException(impossible);
		}
	}

	record TemporaryResult<T>(dev.libjadx.core.RevisionState revisions, EffectiveAnalysisConfig settings,
			String settingsFingerprint, String sourceSnapshotId, T value) { }

	public ProjectSnapshot reloadProject(boolean discardUnsaved, String expectedSessionId,
			long expectedRevision) throws Exception {
		synchronized (operationLock) {
			NativeProjectRepository current;
			synchronized (lifecycleLock) {
				requireReady();
				current = repository;
			}
			// A failed replacement leaves the fixed service in FAILED rather than publishing stale analysis.
			ProjectSnapshot next = current.reload(discardUnsaved, expectedSessionId, expectedRevision);
			ProjectEngine replacement = null;
			try {
				synchronized (lifecycleLock) {
					requireReady();
					lifecycle = Lifecycle.RELOADING;
					status = status("RELOADING", "LOADING_JADX", new RuntimeStatus.Progress(0, 1, "project"), null);
				}
				replacement = engineFactory.create(buildArgs(current));
				replacement.load();
				ProjectEngine old;
				synchronized (lifecycleLock) {
					if (lifecycle != Lifecycle.RELOADING) throw new IllegalStateException("Reload interrupted by shutdown");
					old = activeEngine;
					activeEngine = replacement;
					replacement = null;
					lifecycle = Lifecycle.READY;
					status = status("READY", "READY", new RuntimeStatus.Progress(1, 1, "project"), null);
				}
				closeOwned(old, null);
				return next;
			} catch (Exception failure) {
				ProjectEngine old;
				synchronized (lifecycleLock) {
					old = activeEngine;
					activeEngine = null;
					if (lifecycle == Lifecycle.RELOADING) {
						lifecycle = Lifecycle.FAILED;
						status = status("FAILED", "FAILED", status.progress(),
								new RuntimeStatus.ApiError("PROJECT_LOAD_FAILED", safeFailureMessage(failure), null));
					}
				}
				if (old != null) closeOwned(old, failure);
				throw failure;
			} finally {
				if (replacement != null) closeOwned(replacement, null);
			}
		}
	}

	public ProjectSnapshot updateMappingsPath(Path mappings, String expectedSessionId, long expectedRevision) throws Exception {
		synchronized (operationLock) {
			NativeProjectRepository current;
			NativeProjectRepository.MappingCandidate candidate;
			synchronized (lifecycleLock) {
				requireReady();
				current = repository;
				candidate = current.stageMappingsPath(mappings, expectedSessionId, expectedRevision);
				lifecycle = Lifecycle.RELOADING;
				status = status("RELOADING", "LOADING_JADX", new RuntimeStatus.Progress(0, 1, "project"), null);
			}
			ProjectEngine replacement = null;
			try {
				JadxArgs args = JadxEngineFactory.arguments(inputPaths, candidate.path(),
						candidate.document().getCodeData(), effectiveConfig);
				replacement = engineFactory.create(args);
				replacement.load();
				ProjectEngine old;
				ProjectSnapshot snapshot;
				synchronized (lifecycleLock) {
					if (lifecycle != Lifecycle.RELOADING) throw new IllegalStateException("Settings rebuild interrupted by shutdown");
					snapshot = current.commitMappingsPath(candidate);
					old = activeEngine;
					activeEngine = replacement;
					replacement = null;
					lifecycle = Lifecycle.READY;
					status = status("READY", "READY", new RuntimeStatus.Progress(1, 1, "project"), null);
				}
				closeOwned(old, null);
				return snapshot;
			} catch (Exception failure) {
				synchronized (lifecycleLock) {
					if (lifecycle == Lifecycle.RELOADING) {
						lifecycle = Lifecycle.READY;
						status = status("READY", "READY", new RuntimeStatus.Progress(1, 1, "project"), null);
					}
				}
				throw failure;
			} finally {
				if (replacement != null) closeOwned(replacement, null);
			}
		}
	}

	private void requireReady() {
		if (lifecycle != Lifecycle.READY || repository == null) throw new IllegalStateException("Project is not ready");
	}

	public RuntimeStatus status() {
		synchronized (lifecycleLock) {
			return status;
		}
	}

	public boolean isReady() {
		synchronized (lifecycleLock) {
			return lifecycle == Lifecycle.READY;
		}
	}

	public CompletableFuture<Void> initializeAsync(NativeProjectDocument nativeProject) {
		synchronized (lifecycleLock) {
			if (initializationStarted) {
				throw new IllegalStateException("Project initialization has already been started");
			}
			initializationStarted = true;
			if (lifecycle != Lifecycle.LOADING) {
				return CompletableFuture.failedFuture(new IllegalStateException("Project runtime is shutting down"));
			}
			InitializationTask task = new InitializationTask(nativeProject);
			initializationTask = task;
			try {
				loader.execute(task);
			} catch (RejectedExecutionException failure) {
				task.state = TaskState.FINISHED;
				lifecycle = Lifecycle.FAILED;
				status = status("FAILED", "FAILED", status.progress(),
						new RuntimeStatus.ApiError("INTERNAL_ERROR", "Project initialization could not be scheduled", null));
				task.completion.completeExceptionally(failure);
			}
			return task.completion;
		}
	}

	/**
	 * Returns a borrowed Jadx object only while READY. This does not provide a lease:
	 * a future operation coordinator must prevent use from overlapping shutdown.
	 */
	public JadxDecompiler decompiler() {
		synchronized (lifecycleLock) {
			if (lifecycle != Lifecycle.READY || activeEngine == null) {
				throw new IllegalStateException("Project is not ready");
			}
			return activeEngine.decompiler();
		}
	}

	private void initialize(NativeProjectDocument nativeProject) {
		ProjectEngine local = null;
		NativeProjectRepository localRepository = null;
		Exception expectedFailure = null;
		Error fatalFailure = null;
		try {
			if (nativeProject != null) localRepository = NativeProjectRepository.open(nativeProject.getProjectPath(), allowedRoots);
			else if (!inputPaths.isEmpty()) localRepository = NativeProjectRepository.fromInputs(inputPaths, allowedRoots);
			if (localRepository != null && !localRepository.snapshot().inputs().equals(inputPaths)) {
				throw new IllegalStateException("Native project input references changed after startup validation");
			}
			JadxArgs args = localRepository == null ? new JadxArgs() : buildArgs(localRepository);
			if (!isLoading()) {
				return;
			}
			local = engineFactory.create(args);
			if (!isLoading()) {
				return;
			}
			local.load();
			beforePublish.run();
			synchronized (lifecycleLock) {
				if (lifecycle == Lifecycle.LOADING) {
					activeEngine = local;
					repository = localRepository;
					local = null; // ownership transferred to the runtime
					lifecycle = Lifecycle.READY;
					status = status("READY", "READY", new RuntimeStatus.Progress(1, 1, "project"), null);
				}
			}
		} catch (Exception failure) {
			expectedFailure = failure;
			System.err.println("libjadx: project initialization failed");
			failure.printStackTrace(System.err);
			if (failure instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
			synchronized (lifecycleLock) {
				if (lifecycle == Lifecycle.LOADING) {
					lifecycle = Lifecycle.FAILED;
					status = status("FAILED", "FAILED", new RuntimeStatus.Progress(0, 1, "project"),
							new RuntimeStatus.ApiError("PROJECT_LOAD_FAILED", safeFailureMessage(failure), null));
				}
			}
		} catch (Error failure) {
			fatalFailure = failure;
			synchronized (lifecycleLock) {
				if (lifecycle == Lifecycle.LOADING) {
					lifecycle = Lifecycle.FAILED;
					status = status("FAILED", "FAILED", new RuntimeStatus.Progress(0, 1, "project"),
							new RuntimeStatus.ApiError("INTERNAL_ERROR", "A fatal JVM error interrupted project initialization", null));
				}
			}
		} finally {
			if (local != null) {
				closeOwned(local, expectedFailure != null ? expectedFailure : fatalFailure);
			}
		}
		if (fatalFailure != null) {
			throw fatalFailure;
		}
	}

	private JadxArgs buildArgs(NativeProjectRepository project) {
		return JadxEngineFactory.arguments(inputPaths, project.mappingsPath(), project.codeDataCopy(), effectiveConfig);
	}

	@Override
	public void close() {
		synchronized (operationLock) {
			closeInternal();
		}
	}

	private void closeInternal() {
		ProjectEngine detached;
		InitializationTask cancelled = null;
		synchronized (lifecycleLock) {
			if (lifecycle == Lifecycle.STOPPED) {
				return;
			}
			if (lifecycle != Lifecycle.SHUTTING_DOWN) {
				lifecycle = Lifecycle.SHUTTING_DOWN;
				status = status("SHUTTING_DOWN", "SHUTTING_DOWN", status.progress(), null);
			}
			detached = activeEngine;
			if (detached != null) {
				activeEngine = null;
				cleanupInProgress++;
			}
			if (initializationTask != null && initializationTask.state == TaskState.QUEUED) {
				cancelled = initializationTask;
				cancelled.state = TaskState.FINISHED;
			}
		}

		// State and ownership are settled before interrupting the loader. A load that
		// ignores interruption remains responsible for its local engine in initialize().
		loader.shutdownNow();
		if (cancelled != null) {
			cancelled.completion.completeExceptionally(new CancellationException("Project initialization cancelled by shutdown"));
		}
		if (detached != null) {
			try {
				closeOwned(detached, null);
			} finally {
				synchronized (lifecycleLock) {
					cleanupInProgress--;
					finishShutdownIfQuiescent();
				}
			}
		} else {
			synchronized (lifecycleLock) {
				finishShutdownIfQuiescent();
			}
		}
	}

	private void finishShutdownIfQuiescent() {
		if (lifecycle == Lifecycle.SHUTTING_DOWN
				&& (initializationTask == null || initializationTask.state == TaskState.FINISHED)
				&& cleanupInProgress == 0 && activeEngine == null && activeTemporaryAnalyses == 0) {
			lifecycle = Lifecycle.STOPPED;
			status = status("STOPPED", "STOPPED", status.progress(), null);
			lifecycleLock.notifyAll();
		}
	}

	/** Waits for engine cleanup after shutdown has been requested. Never call from the loader thread. */
	boolean awaitStopped(long timeout, TimeUnit unit) throws InterruptedException {
		long deadline = System.nanoTime() + unit.toNanos(timeout);
		synchronized (lifecycleLock) {
			while (lifecycle != Lifecycle.STOPPED) {
				long remaining = deadline - System.nanoTime();
				if (remaining <= 0) return false;
				TimeUnit.NANOSECONDS.timedWait(lifecycleLock, remaining);
			}
			return true;
		}
	}

	private boolean isLoading() {
		synchronized (lifecycleLock) {
			return lifecycle == Lifecycle.LOADING;
		}
	}

	private RuntimeStatus status(String state, String stage, RuntimeStatus.Progress progress, RuntimeStatus.ApiError error) {
		return new RuntimeStatus(state, stage, progress, projectPath, inputPaths, error);
	}

	private static void closeOwned(ProjectEngine engine, Throwable primaryFailure) {
		try {
			engine.close();
		} catch (Exception cleanupFailure) {
			if (primaryFailure != null) {
				primaryFailure.addSuppressed(cleanupFailure);
			} else {
				System.err.println("Project cleanup failed: " + cleanupFailure.getClass().getSimpleName());
			}
		}
	}

	private static String safeFailureMessage(Exception failure) {
		return "Project initialization failed (" + failure.getClass().getSimpleName() + "); check the configured project and input files";
	}

	enum Lifecycle {
		LOADING,
		READY,
		RELOADING,
		FAILED,
		SHUTTING_DOWN,
		STOPPED
	}

	private enum TaskState { QUEUED, RUNNING, FINISHED }

	private final class InitializationTask implements Runnable {
		private final NativeProjectDocument nativeProject;
		private final CompletableFuture<Void> completion = new CompletableFuture<>();
		private TaskState state = TaskState.QUEUED;

		private InitializationTask(NativeProjectDocument nativeProject) {
			this.nativeProject = nativeProject;
		}

		@Override
		public void run() {
			synchronized (lifecycleLock) {
				if (state != TaskState.QUEUED) return;
				state = TaskState.RUNNING;
			}
			Throwable failure = null;
			try {
				initialize(nativeProject);
			} catch (Throwable caught) {
				failure = caught;
			} finally {
				synchronized (lifecycleLock) {
					state = TaskState.FINISHED;
					finishShutdownIfQuiescent();
				}
				if (failure == null) completion.complete(null);
				else completion.completeExceptionally(failure);
			}
		}
	}

	@FunctionalInterface
	interface EngineFactory {
		ProjectEngine create(JadxArgs args) throws Exception;
	}

	interface ProjectEngine extends AutoCloseable {
		void load() throws Exception;

		JadxDecompiler decompiler();

		@Override
		void close() throws Exception;
	}

	private static final class JadxProjectEngine implements ProjectEngine {
		private final JadxDecompiler decompiler;

		private JadxProjectEngine(JadxArgs args) {
			this.decompiler = new JadxDecompiler(args);
		}

		@Override
		public void load() {
			decompiler.load();
		}

		@Override
		public JadxDecompiler decompiler() {
			return decompiler;
		}

		@Override
		public void close() {
			decompiler.close();
		}
	}
}
