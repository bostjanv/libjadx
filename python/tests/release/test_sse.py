"""Actual Java heartbeat, subscriber budget, overflow and stream deadline behavior."""

import json
import threading
import time
from contextlib import ExitStack

import httpx
import pytest
from release_support import SURFACES, Surface
from test_routes import wait_job

from libjadx import AsyncClient, Client


def start_job(service):
    response = service.http.post(
        "/api/v1/search/build-index", json={"domains": ["SOURCE_TEXT"]}
    )
    assert response.status_code == 202
    service.entered("job")
    return response.json()


def test_real_java_heartbeat_disconnect_and_reconnect(factory):
    with factory(holds=("job",)) as service:
        job = start_job(service)
        path = "/api/v1/jobs/" + job["jobId"] + "/events"
        with service.http.stream("GET", path, timeout=25) as response:
            assert response.status_code == 200
            last = None
            saw_heartbeat = False
            for line in response.iter_lines():
                if line.startswith("id: "):
                    last = line[4:]
                if line == ": heartbeat":
                    saw_heartbeat = True
                    break
            assert saw_heartbeat and last
        assert (
            service.http.get("/api/v1/jobs/" + job["jobId"]).json()["state"]
            == "RUNNING"
        )
        service.release("job")
        terminal = wait_job(service, job)
        replay = service.http.get(path, headers={"Last-Event-ID": last})
        assert replay.status_code == 200
        events = [
            json.loads(line[6:])
            for line in replay.text.splitlines()
            if line.startswith("data: ")
        ]
        assert events[-1]["state"] == terminal["state"] == "SUCCEEDED"
        assert all(e["sequence"] > int(last) for e in events)


@pytest.mark.parametrize("surface", SURFACES)
async def test_subscriber_resource_limit_preserves_job(factory, surface):
    with factory(holds=("job",)) as service:
        job = start_job(service)
        path = "/api/v1/jobs/" + job["jobId"] + "/events"
        with ExitStack() as stack:
            # Consumption of the first frame proves each real subscription is admitted.
            for _ in range(8):
                pool = stack.enter_context(
                    httpx.Client(base_url=service.url, trust_env=False, timeout=30)
                )
                response = stack.enter_context(pool.stream("GET", path))
                assert response.status_code == 200
                assert next(response.iter_lines()).startswith("id: ")
            api = Surface(service, surface)
            try:
                error = await api.call(
                    "getJobEvents", job_id=job["jobId"], expected=429
                )
                assert error["error"]["code"] == "RESOURCE_LIMIT"
                assert (
                    service.http.get("/api/v1/jobs/" + job["jobId"]).json()["state"]
                    == "RUNNING"
                )
            finally:
                await api.close()
        service.release("job")
        assert wait_job(service, job)["state"] == "SUCCEEDED"


def test_slow_subscriber_overflow_is_disconnect_not_cancel(factory):
    with factory(kind="long", holds=("job", "sse-delivery")) as service:
        job = start_job(service)
        path = "/api/v1/jobs/" + job["jobId"] + "/events"
        with service.http.stream("GET", path) as response:
            lines = response.iter_lines()
            assert next(lines).startswith("id: ")
            service.entered("sse-delivery")
            service.release("job")
            terminal = wait_job(service, job)
            assert terminal["state"] == "SUCCEEDED"
            service.release("sse-delivery")
            remaining = list(lines)
            assert not any('"state":"SUCCEEDED"' in line for line in remaining)
        assert service.http.get("/api/v1/jobs/" + job["jobId"]).json() == terminal


@pytest.mark.parametrize("asynchronous", (False, True))
async def test_sdk_real_quiet_stream_exceeds_normal_timeout(factory, asynchronous):
    with factory(holds=("job",)) as service:
        job = start_job(service)
        # Release only after the server's real 15-second heartbeat and ordinary 10s timeout.
        release = threading.Timer(16, service.release, args=("job",))
        started = time.monotonic()
        release.start()
        try:
            if asynchronous:
                async with AsyncClient(service.url) as client:
                    watched = await client.job(job["jobId"])
                    events = [event async for event in watched.events()]
                    assert (await watched.refresh()).state == "SUCCEEDED"
                    assert client.lowlevel.get_async_httpx_client().timeout.read == 10
            else:
                with Client(service.url) as client:
                    watched = client.job(job["jobId"])
                    events = list(watched.events())
                    assert watched.refresh().state == "SUCCEEDED"
                    assert client.lowlevel.get_httpx_client().timeout.read == 10
            assert events[-1].state == "SUCCEEDED"
            assert time.monotonic() - started > 10
        finally:
            release.cancel()
            service.release("job")
