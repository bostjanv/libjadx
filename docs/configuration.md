# Running LibJadx

The process serves exactly one fixed Jadx project. Start it with an existing native `.jadx` file or one or more local Jadx inputs:

```sh
libjadx --project /work/sample.jadx --port 18777
libjadx --input /work/sample.apk --allowed-root /work
```

The default listener is `127.0.0.1:18777`. The current server accepts only the loopback address. Native project references and input paths are resolved from the project directory as Jadx does. Every project, input and mapping path must resolve within an allowed root. If no root is configured, roots are derived from the supplied project and input parent directories. Paths are canonicalized through symlinks before the check.

Configuration can come from YAML, environment variables and command-line flags. Precedence is **CLI > environment > YAML > defaults**. The project/input selection is taken from the highest-precedence source that specifies it; lower-precedence values do not get mixed into that selection.

```yaml
project: /work/sample.jadx
bind: 127.0.0.1
port: 18777
allowedRoots:
  - /work
```

Supported environment variables are `LIBJADX_PROJECT`, `LIBJADX_INPUT`, `LIBJADX_BIND`, `LIBJADX_PORT` and `LIBJADX_ALLOWED_ROOT`. The two list-valued environment variables use the platform path separator. Repeat `--input` and `--allowed-root` for multiple CLI values.

The HTTP listener starts before Jadx loads the project. `GET /api/v1/health/live` reports process liveness and `GET /api/v1/status` reports `LOADING`, `READY`, `FAILED`, or shutdown state. `GET /api/v1/capabilities` reports tested capability evidence. Native save/reload/settings, requested shutdown, class listing and original symbol lookup are implemented. Other analysis and edit routes remain planned. Planned operations return `PROJECT_NOT_READY` with `Retry-After` while loading, the recorded non-retryable load error after failure, and a structured not-implemented response after readiness. Unknown paths return `NOT_FOUND` in every state.

On first startup, the service creates an owner-only 32-byte cursor signing key under `$XDG_STATE_HOME/libjadx/cursor-signing.key` when `XDG_STATE_HOME` is an absolute path, or `~/.local/state/libjadx/cursor-signing.key` otherwise. This operational key is separate from the native project. Keep it private and available across restarts so authentic old cursors return `STALE_REVISION`. An unreadable, malformed, or overly permissive existing key stops startup with an operational error.

Raw-input startup does not create a native project sidecar. An explicit `/project/save` with a target path creates one. `/shutdown` with `policy=save` requires an existing native project path or one already established by explicit save. There is no project-switch route.

Ordinary project-load failures leave the listener running for status diagnostics. The HTTP error stays sanitized; the original exception and stack trace are written to the local standard error stream. On ordinary process shutdown, the service waits up to five seconds for an in-progress loader to release its Jadx engine. If cleanup exceeds that limit, it logs a timeout and the JVM may terminate before cleanup finishes. A fatal JVM error follows the separate nonzero-exit path with a ten-second fail-safe halt watchdog.
