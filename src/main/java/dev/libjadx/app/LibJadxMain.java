package dev.libjadx.app;

import dev.libjadx.http.HttpApiServer;

public final class LibJadxMain {
	private LibJadxMain() { }

	public static void main(String[] args) throws Exception {
		ServiceConfig config;
		try {
			config = ServiceConfig.parse(args);
		} catch (Exception e) {
			System.err.println("libjadx: " + e.getMessage());
			System.err.println("Use --help for usage.");
			System.exit(2);
			return;
		}
		if (config == null) return;

		ProjectRuntime runtime = new ProjectRuntime(config.projectPath(), config.inputPaths());
		HttpApiServer server = new HttpApiServer(config.bindAddress(), config.port(), runtime);
		StartupSupervisor supervisor = new StartupSupervisor(server, runtime, LibJadxMain::startFatalWatchdog,
				System::exit);
		Thread shutdownHook = new Thread(supervisor::close, "libjadx-shutdown");
		Runtime.getRuntime().addShutdownHook(shutdownHook);
		try {
			server.start();
			System.out.println("LibJadx listening on http://" + config.bindAddress() + ":" + server.localPort());
			supervisor.watch(runtime.initializeAsync(config.nativeProject()));
			server.join();
			supervisor.awaitFatalExitIfObserved();
		} finally {
			supervisor.close();
			try {
				Runtime.getRuntime().removeShutdownHook(shutdownHook);
			} catch (IllegalStateException ignored) {
				// JVM shutdown already started.
			}
		}
	}

	private static void startFatalWatchdog() {
		try {
			Thread watchdog = new Thread(() -> {
				try {
					Thread.sleep(10_000);
				} catch (InterruptedException ignored) {
					return;
				}
				Runtime.getRuntime().halt(1);
			}, "libjadx-fatal-watchdog");
			watchdog.setDaemon(true);
			watchdog.start();
		} catch (Throwable failure) {
			Runtime.getRuntime().halt(1);
		}
	}
}
