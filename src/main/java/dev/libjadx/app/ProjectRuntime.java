package dev.libjadx.app;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
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
import java.util.function.Function;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import dev.libjadx.scheduler.OperationCoordinator;
import dev.libjadx.scheduler.OperationRequest;
import dev.libjadx.scheduler.ServiceShuttingDownException;
import dev.libjadx.scheduler.JobLimits;
import dev.libjadx.scheduler.JobRegistry;
import dev.libjadx.scheduler.JobSnapshot;
import dev.libjadx.scheduler.JobSpec;

/** Owns one fixed project and publishes its engine only after initialization succeeds. */
public final class ProjectRuntime implements AutoCloseable {
	private final Object lifecycleLock = new Object();
	private final OperationCoordinator coordinator = new OperationCoordinator();
	private final JobRegistry jobs;
	private final Path projectPath;
	private final List<Path> inputPaths;
	private final List<Path> allowedRoots;
	private final EngineFactory engineFactory;
	private final SaveAction saveAction;
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
	private volatile EffectiveAnalysisConfig effectiveConfig = EffectiveAnalysisConfig.defaults();
	private long publicationEpoch;
	private volatile OperationCoordinator.Admission publishedAdmission = new OperationCoordinator.Admission("initializing", 0);
	private final CompletableFuture<Void> fatalRuntimeFailure = new CompletableFuture<>();
	private boolean initializationStarted;
	private InitializationTask initializationTask;
	private int cleanupInProgress;
	private boolean shutdownReserved;

	public ProjectRuntime(Path projectPath, List<Path> inputPaths) {
		this(projectPath, inputPaths, defaultRoots(projectPath, inputPaths));
	}

	public ProjectRuntime(Path projectPath, List<Path> inputPaths, List<Path> allowedRoots) {
		this(projectPath, inputPaths, allowedRoots, JadxProjectEngine::new, () -> { },
				Executors.newSingleThreadExecutor(LOADER_THREADS));
	}

	ProjectRuntime(Path projectPath, List<Path> inputPaths, List<Path> allowedRoots, JobLimits limits) {
		this(projectPath, inputPaths, allowedRoots, JadxProjectEngine::new, () -> { },
				Executors.newSingleThreadExecutor(LOADER_THREADS), NativeProjectRepository::save, limits);
	}

	ProjectRuntime(Path projectPath, List<Path> inputPaths, EngineFactory engineFactory) {
		this(projectPath, inputPaths, engineFactory, () -> { });
	}

	ProjectRuntime(Path projectPath, List<Path> inputPaths, EngineFactory engineFactory, SaveAction saveAction) {
		this(projectPath, inputPaths, defaultRoots(projectPath, inputPaths), engineFactory, () -> { },
				Executors.newSingleThreadExecutor(LOADER_THREADS), saveAction);
	}

	ProjectRuntime(Path projectPath, List<Path> inputPaths, EngineFactory engineFactory, JobLimits limits) {
		this(projectPath, inputPaths, defaultRoots(projectPath, inputPaths), engineFactory, () -> { },
				Executors.newSingleThreadExecutor(LOADER_THREADS), NativeProjectRepository::save, limits);
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
		this(projectPath, inputPaths, allowedRoots, engineFactory, beforePublish, loader,
				NativeProjectRepository::save);
	}

	private ProjectRuntime(Path projectPath, List<Path> inputPaths, List<Path> allowedRoots,
			EngineFactory engineFactory, Runnable beforePublish, ExecutorService loader, SaveAction saveAction) {
		this(projectPath, inputPaths, allowedRoots, engineFactory, beforePublish, loader, saveAction, JobLimits.defaults());
	}

	private ProjectRuntime(Path projectPath, List<Path> inputPaths, List<Path> allowedRoots,
			EngineFactory engineFactory, Runnable beforePublish, ExecutorService loader, SaveAction saveAction,
			JobLimits limits) {
		this.projectPath = projectPath;
		this.inputPaths = List.copyOf(inputPaths);
		this.allowedRoots = List.copyOf(allowedRoots);
		this.engineFactory = engineFactory;
		this.saveAction = Objects.requireNonNull(saveAction, "saveAction");
		this.beforePublish = beforePublish;
		this.loader = loader;
		this.status = status("LOADING", "INITIALIZING", new RuntimeStatus.Progress(0, 1, "project"), null);
		this.jobs = new JobRegistry(limits, this::admitJob, this::reportFatalRebuild);
	}

	private static List<Path> defaultRoots(Path projectPath, List<Path> inputPaths) {
		java.util.ArrayList<Path> roots = new java.util.ArrayList<>();
		if (projectPath != null && projectPath.toAbsolutePath().getParent() != null) roots.add(projectPath.toAbsolutePath().getParent());
		for (Path input : inputPaths) if (input.toAbsolutePath().getParent() != null) roots.add(input.toAbsolutePath().getParent());
		return roots;
	}

	public ProjectSnapshot projectSnapshot() {
		return readPublished(NativeProjectRepository::snapshot);
	}

	public SettingsSnapshot settingsSnapshot() {
		return readPublished(current -> {
			synchronized (current) {
				return new SettingsSnapshot(current.mappingsPath(), effectiveConfig,
						current.snapshot().revisions());
			}
		});
	}

	public record SettingsSnapshot(Path mappingsPath, EffectiveAnalysisConfig effective,
			dev.libjadx.core.RevisionState revisions) { }

	public JsonObject pendingEdits() {
		return readPublished(NativeProjectRepository::pendingEdits);
	}

	private <T> T readPublished(Function<NativeProjectRepository, T> read) {
		// A native save holds the repository monitor during I/O. Do not wait for it
		// while holding lifecycleLock, which shutdown needs to stop admission.
		while (true) {
			NativeProjectRepository current;
			long epoch;
			synchronized (lifecycleLock) {
				requireReady();
				current = repository;
				epoch = publicationEpoch;
			}
			T result = read.apply(current);
			synchronized (lifecycleLock) {
				requireReady();
				if (current == repository && epoch == publicationEpoch) return result;
			}
		}
	}

	public ProjectSnapshot saveProject(Path target, Long expectedRevision) throws IOException {
		return saveProject(target, null, expectedRevision);
	}

	public ProjectSnapshot saveProject(Path target, String expectedSessionId, Long expectedRevision) throws IOException {
		try (var lease = admit(OperationRequest.projectExclusive("native-save"))) {
			NativeProjectRepository current = repository;
			ProjectSnapshot saved = saveAction.save(current, target, expectedSessionId, expectedRevision).project();
			synchronized (lifecycleLock) {
				publicationEpoch++;
				publishedAdmission = admission(saved);
			}
			return saved;
		}
	}

	/** Internal edit entry point; callers must validate native edit semantics before invoking it. */
	void replaceCodeData(JadxCodeData edited, long expectedRevision) {
		try (var lease = admitReloading(OperationRequest.projectExclusive("code-data-edit"), "APPLYING_CODE_DATA")) {
			ProjectEngine engine = activeEngine;
			JadxCodeData codeData;
			Error fatal = null;
			boolean nativeEditApplied = false;
			try {
				repository.replaceCodeData(edited, expectedRevision);
				nativeEditApplied = true;
				codeData = repository.codeDataCopy();
				engine.reloadCodeData(codeData);
				synchronized (lifecycleLock) {
					if (lifecycle == Lifecycle.RELOADING) {
						lifecycle = Lifecycle.READY;
						publicationEpoch++;
						publishedAdmission = admission(repository.snapshot());
						status = status("READY", "READY", new RuntimeStatus.Progress(1, 1, "project"), null);
					}
				}
			} catch (RuntimeException failure) {
				synchronized (lifecycleLock) {
					if (lifecycle == Lifecycle.RELOADING) {
						if (nativeEditApplied) {
							lifecycle = Lifecycle.FAILED;
							status = status("FAILED", "FAILED", status.progress(),
									new RuntimeStatus.ApiError("PROJECT_LOAD_FAILED", "Code-data reload failed", null));
						} else {
							lifecycle = Lifecycle.READY;
							status = status("READY", "READY", new RuntimeStatus.Progress(1, 1, "project"), null);
						}
					}
				}
				throw failure;
			} catch (Error failure) {
				fatal = failure;
				throw failure;
			} finally {
				if (fatal != null) reportFatalRebuild(fatal);
			}
		}
	}

	/** One isolated, read-only analysis operation over an admitted immutable native edit snapshot. */
	<T> TemporaryResult<T> withTemporaryAnalysis(EffectiveAnalysisConfig override,
			Function<JadxDecompiler, T> operation) throws Exception {
		ProjectEngine temporary = null;
		JadxArgs args;
		ProjectSnapshot snapshot;
		try (var lease = admit(OperationRequest.temporaryAnalysis(override.fingerprint()))) {
			// Capture under the coordinator's brief gate, outside lifecycleLock.
			snapshot = repository.snapshot();
			args = JadxEngineFactory.arguments(inputPaths, repository.mappingsPath(), repository.codeDataCopy(), override);
			lease.finishSnapshotCapture();
			try {
				temporary = engineFactory.create(args);
				temporary.load();
				String sourceSnapshotId = sourceSnapshotId(snapshot, override);
				return new TemporaryResult<>(snapshot.revisions(), override, override.fingerprint(), sourceSnapshotId,
						operation.apply(temporary.decompiler()));
			} finally {
				if (temporary != null) closeOwned(temporary, null);
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
		return rebuild(current -> {
			NativeProjectRepository.ReloadCandidate candidate =
					current.prepareReload(discardUnsaved, expectedSessionId, expectedRevision);
			JadxArgs args = JadxEngineFactory.arguments(inputPaths, candidate.document().getMappingsPath(),
					candidate.document().getCodeData(), effectiveConfig);
			return new StagedRebuild(args, () -> current.commitReload(candidate));
		});
	}

	public ProjectSnapshot updateMappingsPath(Path mappings, String expectedSessionId, long expectedRevision) throws Exception {
		return rebuild(current -> {
			NativeProjectRepository.MappingCandidate candidate =
					current.stageMappingsPath(mappings, expectedSessionId, expectedRevision);
			JadxArgs args = JadxEngineFactory.arguments(inputPaths, candidate.path(),
					candidate.document().getCodeData(), effectiveConfig);
			return new StagedRebuild(args, () -> current.commitMappingsPath(candidate));
		});
	}

	private ProjectSnapshot rebuild(RebuildStager stager) throws Exception {
		try (var lease = admitReloading(OperationRequest.projectExclusive("project-rebuild"), "STAGING_PROJECT")) {
			NativeProjectRepository current = repository;
			ProjectEngine replacement = null;
			Error fatal = null;
			try {
				StagedRebuild staged = stager.stage(current);
				synchronized (lifecycleLock) {
					if (lifecycle != Lifecycle.RELOADING) throw unavailable();
					status = status("RELOADING", "LOADING_JADX", new RuntimeStatus.Progress(0, 1, "project"), null);
				}
				replacement = engineFactory.create(staged.args());
				replacement.load();
				ProjectEngine old;
				// Native fingerprint checks may perform I/O; no lifecycle monitor is held.
				ProjectSnapshot snapshot = staged.commit().apply();
				synchronized (lifecycleLock) {
					if (lifecycle != Lifecycle.RELOADING) throw unavailable();
					old = activeEngine;
					activeEngine = replacement;
					replacement = null;
					publicationEpoch++;
					publishedAdmission = admission(snapshot);
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
			} catch (Error failure) {
				fatal = failure;
				throw failure;
			} finally {
				try {
					if (replacement != null) closeOwned(replacement, fatal);
				} finally {
					if (fatal != null) reportFatalRebuild(fatal);
				}
			}
		}
	}

	@FunctionalInterface
	private interface RebuildStager {
		StagedRebuild stage(NativeProjectRepository project) throws Exception;
	}

	@FunctionalInterface
	private interface RebuildCommit {
		ProjectSnapshot apply() throws Exception;
	}

	private record StagedRebuild(JadxArgs args, RebuildCommit commit) { }

	public CompletableFuture<Void> fatalRuntimeFailure() {
		return fatalRuntimeFailure;
	}

	private void reportFatalRebuild(Error failure) {
		ProjectEngine detached;
		synchronized (lifecycleLock) {
			if (lifecycle != Lifecycle.SHUTTING_DOWN && lifecycle != Lifecycle.STOPPED) {
				lifecycle = Lifecycle.FAILED;
				status = status("FAILED", "FAILED", status.progress(),
						new RuntimeStatus.ApiError("INTERNAL_ERROR", "A fatal JVM error interrupted the project runtime", null));
			}
			coordinator.stopAdmissions();
			jobs.stopSubmissions();
			detached = coordinator.primaryEngineInUse() ? null : activeEngine;
			if (detached != null) activeEngine = null;
			if (detached != null) cleanupInProgress++;
		}
		try {
			if (detached != null) {
				try {
					closeOwned(detached, failure);
				} finally {
					synchronized (lifecycleLock) {
						cleanupInProgress--;
						finishShutdownIfQuiescent();
					}
				}
			}
		} finally {
			fatalRuntimeFailure.completeExceptionally(failure);
		}
	}

	private void requireReady() {
		if (shutdownReserved) throw new ServiceShuttingDownException();
		if (lifecycle != Lifecycle.READY || repository == null) throw unavailable();
	}

	private IllegalStateException unavailable() {
		if (lifecycle == Lifecycle.SHUTTING_DOWN || lifecycle == Lifecycle.STOPPED) {
			return new ServiceShuttingDownException();
		}
		return new ProjectNotReadyException(status);
	}

	private static OperationCoordinator.Admission admission(ProjectSnapshot snapshot) {
		return new OperationCoordinator.Admission(snapshot.revisions().sessionId(),
				snapshot.revisions().logicalRevision());
	}

	private OperationCoordinator.Lease admit(OperationRequest request) {
		synchronized (lifecycleLock) {
			requireReady();
			return coordinator.tryAdmit(request, publishedAdmission, this::operationCompleted);
		}
	}

	private OperationCoordinator.Lease admitJob(OperationRequest request, OperationCoordinator.Admission expected) {
		synchronized (lifecycleLock) {
			if (lifecycle == Lifecycle.RELOADING) throw new JobRegistry.AdmissionDeferredException();
			requireReady();
			if (!publishedAdmission.equals(expected)) throw new JobRegistry.StaleJobSnapshotException();
			return coordinator.tryAdmit(request, publishedAdmission, this::operationCompleted);
		}
	}

	/** Internal submission seam for future typed analysis operations and deterministic tests. */
	JobSnapshot submitJob(JobSpec<?> spec) {
		synchronized (lifecycleLock) {
			requireReady();
			return jobs.submit(spec);
		}
	}

	/** Reversible reservation blocks all runtime admissions while native save runs. */
	public void requestShutdown(ShutdownPolicy policy) throws IOException {
		Objects.requireNonNull(policy, "policy");
		OperationCoordinator.Lease saveLease = null;
		boolean runtimeReserved = false;
		boolean registryReserved = false;
		boolean accepted = false;
		try {
			synchronized (lifecycleLock) {
				requireReady();
				shutdownReserved = true;
				runtimeReserved = true;
				jobs.reserveSubmissions();
				registryReserved = true;
				var activeJobs = jobs.activeJobs(16);
				int activeOperations = coordinator.inFlightCount();
				if (activeOperations != 0 || activeJobs.count() != 0) {
					throw new ShutdownRejectedException(409, "PROJECT_BUSY",
							"Wait for or cancel active work before shutting down", true,
							new ShutdownBusyDetails(activeOperations, coordinator.activeOperations(16), activeJobs.count(), activeJobs.jobs()));
				}
				if (policy == ShutdownPolicy.SAVE) {
					saveLease = coordinator.tryAdmit(OperationRequest.projectExclusive("shutdown-save"),
							publishedAdmission, this::operationCompleted);
				}
			}
			ProjectSnapshot snapshot = repository.snapshot();
			if (policy == ShutdownPolicy.REFUSE_IF_DIRTY && snapshot.dirty()) {
				throw new ShutdownRejectedException(409, "PROJECT_BUSY", "Unsaved native edits exist; save or choose discard", false, null);
			}
			if (policy == ShutdownPolicy.SAVE) {
				if (snapshot.projectPath() == null) {
					throw new ShutdownRejectedException(409, "INVALID_REQUEST",
							"Save the raw input through /api/v1/project/save with a targetPath first", false, null);
				}
				ProjectSnapshot saved = saveAction.save(repository, null, null, null).project();
				synchronized (lifecycleLock) {
					publicationEpoch++;
					publishedAdmission = admission(saved);
				}
			}
			if (saveLease != null) {
				saveLease.close();
				saveLease = null;
			}
			synchronized (lifecycleLock) {
				if (lifecycle != Lifecycle.READY) throw unavailable();
				lifecycle = Lifecycle.SHUTTING_DOWN;
				status = status("SHUTTING_DOWN", "SHUTTING_DOWN", status.progress(), null);
				coordinator.stopAdmissions();
				jobs.stopSubmissions();
				shutdownReserved = false;
				jobs.releaseSubmissionReservation();
				accepted = true;
			}
		} finally {
			if (saveLease != null) saveLease.close();
			if (!accepted && runtimeReserved) {
				synchronized (lifecycleLock) {
					shutdownReserved = false;
					if (registryReserved) jobs.releaseSubmissionReservation();
				}
				jobs.signal();
			}
		}
	}

	public record ShutdownBusyDetails(int activeOperationCount,
			List<OperationCoordinator.ActiveOperation> operations, int activeJobCount,
			List<JobRegistry.ActiveJob> jobs) { }

	public static final class ShutdownRejectedException extends IllegalStateException {
		private final int statusCode;
		private final String code;
		private final boolean retryable;
		private final Object details;
		private ShutdownRejectedException(int statusCode, String code, String message,
				boolean retryable, Object details) {
			super(message);
			this.statusCode = statusCode;
			this.code = code;
			this.retryable = retryable;
			this.details = details;
		}
		public int statusCode() { return statusCode; }
		public String code() { return code; }
		public boolean retryable() { return retryable; }
		public Object details() { return details; }
	}

	/** Read-only HTTP access to polling, cancellation and SSE; no HTTP submission route exists. */
	public JobRegistry jobRegistry() { return jobs; }

	private OperationCoordinator.Lease admitReloading(OperationRequest request, String stage) {
		synchronized (lifecycleLock) {
			requireReady();
			OperationCoordinator.Lease lease = coordinator.tryAdmit(request, publishedAdmission, this::operationCompleted);
			lifecycle = Lifecycle.RELOADING;
			publicationEpoch++;
			status = status("RELOADING", stage, new RuntimeStatus.Progress(0, 1, "project"), null);
			return lease;
		}
	}

	private void operationCompleted() {
		boolean shuttingDown;
		synchronized (lifecycleLock) {
			shuttingDown = lifecycle == Lifecycle.SHUTTING_DOWN;
			if (!shuttingDown) finishShutdownIfQuiescent();
		}
		if (shuttingDown) closeInternal();
		else jobs.signal();
	}

	public static final class ProjectNotReadyException extends IllegalStateException {
		private final RuntimeStatus status;
		private ProjectNotReadyException(RuntimeStatus status) {
			super("Project is not ready");
			this.status = status;
		}
		public RuntimeStatus status() { return status; }
	}

	public RuntimeStatus status() {
		synchronized (lifecycleLock) {
			return status;
		}
	}

	public boolean isReady() {
		synchronized (lifecycleLock) {
			return lifecycle == Lifecycle.READY && !shutdownReserved;
		}
	}

	/** Short readiness check for transport validation; never waits on native I/O. */
	public void assertReady() {
		synchronized (lifecycleLock) {
			requireReady();
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

	/** Legacy borrowed access for Phase 0 probes; new handlers must use scoped access. */
	@Deprecated
	public JadxDecompiler decompiler() {
		synchronized (lifecycleLock) {
			if (lifecycle != Lifecycle.READY || activeEngine == null) {
				throw unavailable();
			}
			return activeEngine.decompiler();
		}
	}

	/** Conservative primary-engine read. Convert Jadx objects to immutable results inside the callback. */
	public <T> T withPrimaryClassRead(String classKey, Function<JadxDecompiler, T> operation) {
		try (var lease = admit(OperationRequest.classRead(classKey))) {
			ProjectEngine engine;
			synchronized (lifecycleLock) {
				engine = activeEngine;
			}
			return operation.apply(engine.decompiler());
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
					if (localRepository != null) publishedAdmission = admission(localRepository.snapshot());
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
		closeInternal();
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
			coordinator.stopAdmissions();
			jobs.stopSubmissions();
			detached = coordinator.primaryEngineInUse() ? null : activeEngine;
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
		jobs.shutdown();
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
				&& cleanupInProgress == 0 && activeEngine == null
				&& coordinator.inFlightCount() == 0) {
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
	interface SaveAction {
		NativeProjectRepository.SaveResult save(NativeProjectRepository project, Path target,
				String expectedSessionId, Long expectedRevision) throws IOException;
	}

	@FunctionalInterface
	interface EngineFactory {
		ProjectEngine create(JadxArgs args) throws Exception;
	}

	interface ProjectEngine extends AutoCloseable {
		void load() throws Exception;

		JadxDecompiler decompiler();

		default void reloadCodeData(JadxCodeData codeData) {
			JadxDecompiler jadx = decompiler();
			jadx.getArgs().setCodeData(codeData);
			jadx.reloadCodeData();
		}

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
