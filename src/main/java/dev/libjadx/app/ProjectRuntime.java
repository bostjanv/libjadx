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
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;

/** Owns one fixed project and publishes its engine only after initialization succeeds. */
public final class ProjectRuntime implements AutoCloseable {
	private final Object lifecycleLock = new Object();
	private final Path projectPath;
	private final List<Path> inputPaths;
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
	private boolean initializationStarted;
	private InitializationTask initializationTask;
	private int cleanupInProgress;

	public ProjectRuntime(Path projectPath, List<Path> inputPaths) {
		this(projectPath, inputPaths, JadxProjectEngine::new);
	}

	ProjectRuntime(Path projectPath, List<Path> inputPaths, EngineFactory engineFactory) {
		this(projectPath, inputPaths, engineFactory, () -> { });
	}

	ProjectRuntime(Path projectPath, List<Path> inputPaths, EngineFactory engineFactory, Runnable beforePublish) {
		this(projectPath, inputPaths, engineFactory, beforePublish, Executors.newSingleThreadExecutor(LOADER_THREADS));
	}

	ProjectRuntime(Path projectPath, List<Path> inputPaths, EngineFactory engineFactory,
			Runnable beforePublish, ExecutorService loader) {
		this.projectPath = projectPath;
		this.inputPaths = List.copyOf(inputPaths);
		this.engineFactory = engineFactory;
		this.beforePublish = beforePublish;
		this.loader = loader;
		this.status = status("LOADING", "INITIALIZING", new RuntimeStatus.Progress(0, 1, "project"), null);
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
		Exception expectedFailure = null;
		Error fatalFailure = null;
		try {
			JadxArgs args = new JadxArgs();
			inputPaths.forEach(path -> args.getInputFiles().add(path.toFile()));
			if (nativeProject != null) {
				args.setCodeData(nativeProject.getCodeData());
				Path mappings = nativeProject.getMappingsPath();
				if (mappings != null) {
					args.setUserRenamesMappingsPath(mappings);
				}
			}
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

	@Override
	public void close() {
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
				&& cleanupInProgress == 0 && activeEngine == null) {
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
