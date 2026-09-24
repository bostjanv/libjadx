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

The HTTP listener starts before Jadx loads the project. `GET /api/v1/health/live` reports process liveness and `GET /api/v1/status` reports `LOADING`, `READY`, `FAILED`, or shutdown state. `GET /api/v1/capabilities` reports tested capability evidence. Analysis, edit and save routes are not implemented yet. Planned operations return `PROJECT_NOT_READY` with `Retry-After` while loading, the recorded non-retryable load error after failure, and a structured not-implemented response after readiness. Unknown paths return `NOT_FOUND` in every state.

The current startup layer does not create a native project sidecar for raw inputs and does not save project changes. There is no project-switch route.
