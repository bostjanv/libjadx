package dev.libjadx.http;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

import dev.libjadx.app.ProjectRuntime;
import dev.libjadx.app.ShutdownRequester;
import dev.libjadx.app.ShutdownService;

public final class HttpApiServer implements AutoCloseable {
	private final Server server;

	public HttpApiServer(String host, int port, ProjectRuntime runtime) {
		this(host, port, runtime, null);
	}

	public HttpApiServer(String host, int port, ProjectRuntime runtime, ShutdownRequester requester) {
		this.server = new Server();
		this.server.setStopTimeout(5_000);
		ServerConnector connector = new ServerConnector(server);
		connector.setHost(host);
		connector.setPort(port);
		server.addConnector(connector);
		ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
		context.setContextPath("/");
		ShutdownRequester shutdown = requester == null ? new ShutdownService(runtime, () -> {
			try { close(); }
			catch (Exception failure) { System.err.println("libjadx: listener shutdown failed"); }
			finally { runtime.close(); }
		}) : requester;
		ServletHolder api = new ServletHolder(new StatusServlet(runtime, JsonMapper.builder().build(), shutdown));
		api.setAsyncSupported(true);
		context.addServlet(api, "/api/v1/*");
		server.setHandler(context);
	}

	public void start() throws Exception {
		server.start();
	}

	public void join() throws InterruptedException {
		server.join();
	}

	public int localPort() {
		return ((ServerConnector) server.getConnectors()[0]).getLocalPort();
	}

	@Override
	public void close() throws Exception {
		server.stop();
	}
}
