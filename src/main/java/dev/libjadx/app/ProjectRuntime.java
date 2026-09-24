package dev.libjadx.app;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import dev.libjadx.project.NativeProjectDocument;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;

/** Owns the one fixed project and publishes it only after the initial load succeeds. */
public final class ProjectRuntime implements AutoCloseable {
	private final Path projectPath;
	private final List<Path> inputPaths;
	private final ExecutorService loader = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "libjadx-project-loader");
		thread.setDaemon(true);
		return thread;
	});
	private final AtomicReference<RuntimeStatus> status;
	private volatile JadxDecompiler decompiler;

	public ProjectRuntime(Path projectPath, List<Path> inputPaths) {
		this.projectPath = projectPath;
		this.inputPaths = List.copyOf(inputPaths);
		this.status = new AtomicReference<>(new RuntimeStatus(
				"LOADING", "INITIALIZING", new RuntimeStatus.Progress(0, 1, "project"), projectPath, inputPaths, null));
	}

	public RuntimeStatus status() {
		return status.get();
	}

	public boolean isReady() {
		return "READY".equals(status.get().state());
	}

	public CompletableFuture<Void> initializeAsync(NativeProjectDocument nativeProject) {
		return CompletableFuture.runAsync(() -> {
			JadxDecompiler loaded = null;
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
				loaded = new JadxDecompiler(args);
				loaded.load();
				decompiler = loaded;
				loaded = null;
				status.set(new RuntimeStatus("READY", "READY", new RuntimeStatus.Progress(1, 1, "project"), projectPath, inputPaths, null));
			} catch (Throwable failure) {
				if (loaded != null) loaded.close();
				String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
				status.set(new RuntimeStatus("FAILED", "FAILED", new RuntimeStatus.Progress(0, 1, "project"), projectPath, inputPaths,
						new RuntimeStatus.ApiError("PROJECT_LOAD_FAILED", message, null)));
			}
		}, loader);
	}

	public JadxDecompiler decompiler() {
		if (!isReady()) {
			throw new IllegalStateException("Project is not ready");
		}
		return decompiler;
	}

	@Override
	public void close() throws IOException {
		loader.shutdownNow();
		JadxDecompiler active = decompiler;
		if (active != null) {
			active.close();
		}
	}
}
