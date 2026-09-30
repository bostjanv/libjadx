"""Extracted launchers, path policy and artifact-independent CLI behavior."""

import os
import subprocess

from release_support import OPERATIONS


def cli(*args, env=None):
    environment = {k: v for k, v in os.environ.items() if not k.startswith("LIBJADX_")}
    return subprocess.run(
        [os.environ["LIBJADX_DISTRIBUTION"], *args],
        env={**environment, **(env or {})},
        capture_output=True,
        text=True,
        timeout=15,
    )


def test_cli_help_version_invalid_config_and_loopback(factory, tmp_path):
    assert cli("--version").stdout.strip() == "LibJadx 0.1.0-alpha.1"
    assert cli("--help").returncode == 0
    for args in (
        (),
        ("--port",),
        ("--port", "-1"),
        ("--bind", "0.0.0.0"),
        ("--bind", "::1"),
        ("--bind", "192.0.2.1"),
    ):
        assert cli(*args).returncode == 2
    assert all(
        "{projectId}" not in p and "upload" not in p for _, p, _ in OPERATIONS.values()
    )
    from libjadx import AsyncClient, Client

    assert not hasattr(Client, "open_project") and not hasattr(
        AsyncClient, "open_project"
    )
    assert not hasattr(Client, "rename_local") and not hasattr(
        AsyncClient, "rename_local"
    )
    with factory() as service:
        with Client(service.url) as client:
            assert not hasattr(client.project, "rename_local")
        path = service.root / "qualification.jar"
        assert cli("--input", str(path), "--project", str(path)).returncode == 2
        assert (
            cli(
                "--input", str(path), "--allowed-root", str(tmp_path / "absent")
            ).returncode
            == 2
        )
        outside = tmp_path / "outside.jar"
        outside.write_bytes(path.read_bytes())
        assert (
            cli("--input", str(outside), "--allowed-root", str(service.root)).returncode
            == 2
        )
        symlink = service.root / "escape.jar"
        symlink.symlink_to(outside)
        assert (
            cli("--input", str(symlink), "--allowed-root", str(service.root)).returncode
            == 2
        )
        response = service.http.post(
            "/api/v1/project/save",
            json={"targetPath": str(outside.with_suffix(".jadx"))},
        )
        assert response.status_code == 403
        assert not outside.with_suffix(".jadx").exists()


def test_extracted_cli_environment_yaml_precedence(factory):
    with factory() as service:
        root = service.root
        input_path = root / "qualification.jar"
    config = root / "config.yaml"
    valid = f"""input: {input_path}
bind: 127.0.0.1
port: 0
allowedRoots: [{root}]
"""
    config.write_text(valid)
    with factory(root=root, cli_args=["--config", str(config)]) as service:
        assert service.http.get("/api/v1/status").json()["state"] == "READY"
    config.write_text("""input: /absent/file
bind: 0.0.0.0
port: -1
allowedRoots: [/absent/root]
""")
    env = {
        "LIBJADX_INPUT": str(input_path),
        "LIBJADX_BIND": "127.0.0.1",
        "LIBJADX_PORT": "0",
        "LIBJADX_ALLOWED_ROOT": str(root),
    }
    with factory(
        root=root, cli_args=["--config", str(config)], env_overrides=env
    ) as service:
        assert service.http.get("/api/v1/status").json()["state"] == "READY"
    invalid_env = {
        "LIBJADX_INPUT": "/absent/env",
        "LIBJADX_BIND": "0.0.0.0",
        "LIBJADX_PORT": "-1",
        "LIBJADX_ALLOWED_ROOT": "/absent/env-root",
    }
    args = [
        "--config",
        str(config),
        "--input",
        str(input_path),
        "--bind",
        "127.0.0.1",
        "--port",
        "0",
        "--allowed-root",
        str(root),
    ]
    with factory(root=root, cli_args=args, env_overrides=invalid_env) as service:
        assert service.http.get("/api/v1/status").json()["state"] == "READY"
