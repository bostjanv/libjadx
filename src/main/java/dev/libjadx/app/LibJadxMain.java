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
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				server.close();
				runtime.close();
			} catch (Exception e) {
				System.err.println("Shutdown error: " + e.getMessage());
			}
		}, "libjadx-shutdown"));

		server.start();
		System.out.println("LibJadx listening on http://" + config.bindAddress() + ":" + server.localPort());
		runtime.initializeAsync(config.nativeProject());
		server.join();
	}
}
