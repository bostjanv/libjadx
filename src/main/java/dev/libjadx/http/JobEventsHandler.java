package dev.libjadx.http;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import dev.libjadx.scheduler.JobEvent;
import dev.libjadx.scheduler.JobRegistry;
import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Bounded asynchronous SSE delivery; producers never write to clients. */
final class JobEventsHandler {
	private final JobRegistry jobs;

	JobEventsHandler(JobRegistry jobs) { this.jobs = jobs; }

	void start(HttpServletRequest request, HttpServletResponse response, UUID id) throws IOException {
		Long lastId = parseLastId(request.getHeader("Last-Event-ID"));
		JobRegistry.Subscription subscription = jobs.subscribe(id, lastId);
		try {
			response.setStatus(200);
			response.setContentType("text/event-stream");
			response.setCharacterEncoding("UTF-8");
			response.setHeader("Cache-Control", "no-store");
			response.setHeader("X-Accel-Buffering", "no");
			AsyncContext async = request.startAsync();
			async.setTimeout(0);
			Thread.ofVirtual().name("libjadx-job-sse").start(() -> serve(async, subscription));
		} catch (RuntimeException | Error failure) {
			subscription.close();
			throw failure;
		}
	}

	private void serve(AsyncContext async, JobRegistry.Subscription subscription) {
		try (subscription) {
			ServletOutputStream output = async.getResponse().getOutputStream();
			long heartbeatMillis = jobs.limits().heartbeatInterval().toMillis();
			while (true) {
				JobEvent event = subscription.next(heartbeatMillis);
				if (event == null) {
					if (subscription.isClosed()) break;
					write(output, ": heartbeat\n\n");
					continue;
				}
				write(output, "id: " + event.sequence() + "\nevent: " + event.type()
						+ "\ndata: " + event.dataJson() + "\n\n");
				if (event.terminal()) break;
			}
		} catch (IOException disconnected) {
			// Client disconnect never cancels the job; polling remains authoritative.
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		} finally {
			try { async.complete(); } catch (IllegalStateException ignored) { }
		}
	}

	private static void write(ServletOutputStream output, String frame) throws IOException {
		output.write(frame.getBytes(StandardCharsets.UTF_8));
		output.flush();
	}

	private static Long parseLastId(String raw) {
		if (raw == null) return null;
		if (raw.isEmpty() || !raw.chars().allMatch(ch -> ch >= '0' && ch <= '9')) {
			throw new IllegalArgumentException("Last-Event-ID must be a nonnegative numeric ID");
		}
		try { return Long.parseLong(raw); }
		catch (NumberFormatException invalid) { throw new IllegalArgumentException("Last-Event-ID is too large"); }
	}
}
