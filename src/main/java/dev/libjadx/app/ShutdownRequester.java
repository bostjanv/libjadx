package dev.libjadx.app;

import java.io.IOException;

/** Application-owned boundary between an HTTP acknowledgment and listener teardown. */
public interface ShutdownRequester {
	void request(ShutdownPolicy policy) throws IOException;
	void responseCommitted();
}
