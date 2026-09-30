"""Quiet streaming mock over real sockets, exercising HTTPX read timeouts."""

import asyncio
import json
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import httpx
import pytest

from libjadx import AsyncClient, Client

# Longer than the ordinary default request timeout; no transport injection.
QUIET_SECONDS = 10.2


@pytest.fixture
def quiet_job_server(example):
    doc = example("job-queued.json")
    stop = threading.Event()
    pauses = []
    requests = []

    def frame(sequence, type_, state):
        event = {
            "jobId": doc["jobId"],
            "sequence": sequence,
            "type": type_,
            "at": doc["createdAt"],
            "state": state,
            "progress": doc["progress"],
            "resultUrl": "/api/v1/jobs/" + doc["jobId"],
        }
        return f"id: {sequence}\nevent: {type_}\ndata: {json.dumps(event)}\n\n".encode()

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            requests.append(self.path)
            if self.path == f"/api/v1/jobs/{doc['jobId']}":
                body = json.dumps(doc).encode()
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)
                return
            assert self.path == f"/api/v1/jobs/{doc['jobId']}/events"
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.end_headers()
            self.wfile.write(frame(1, "job.started", "RUNNING"))
            self.wfile.flush()
            started = time.monotonic()
            if stop.wait(QUIET_SECONDS):
                return
            pauses.append(time.monotonic() - started)
            self.wfile.write(b": heartbeat\n\n")
            self.wfile.flush()
            self.wfile.write(frame(2, "job.completed", "SUCCEEDED"))
            self.wfile.flush()

        def log_message(self, *_):
            pass

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield f"http://127.0.0.1:{server.server_port}", doc, pauses, requests
    finally:
        stop.set()
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)


@pytest.mark.parametrize("async_", [False, True], ids=["sync", "async"])
async def test_default_sse_survives_quiet_job_before_heartbeat(
    quiet_job_server, async_
):
    url, doc, pauses, requests = quiet_job_server

    def consume_sync():
        with Client(url) as client:
            pool = client.lowlevel.get_httpx_client()
            assert pool.timeout == httpx.Timeout(10.0)
            job = client.job(doc["jobId"])
            events = list(job.events())
            job.refresh()
            assert pool.timeout == httpx.Timeout(10.0)
            return events

    if async_:
        async with AsyncClient(url) as client:
            pool = client.lowlevel.get_async_httpx_client()
            assert pool.timeout == httpx.Timeout(10.0)
            job = await client.job(doc["jobId"])
            events = [event async for event in job.events()]
            await job.refresh()
            assert pool.timeout == httpx.Timeout(10.0)
    else:
        events = await asyncio.to_thread(consume_sync)

    assert [(event.sequence, event.state) for event in events] == [
        (1, "RUNNING"),
        (2, "SUCCEEDED"),
    ]
    assert len(pauses) == 1 and pauses[0] >= QUIET_SECONDS
    job_path = f"/api/v1/jobs/{doc['jobId']}"
    assert requests == [job_path, job_path + "/events", job_path]
