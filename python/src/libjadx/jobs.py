from __future__ import annotations

import asyncio
import time
from collections.abc import AsyncIterator, Iterator
from typing import TYPE_CHECKING
from uuid import UUID

import httpx

from ._common import polling
from ._generated.api.service import cancel_job, get_job
from ._generated.models.api_error import ApiError as WireError
from ._generated.models.error_envelope import ErrorEnvelope
from ._generated.models.job import Job as WireJob
from ._generated.models.job_event import JobEvent
from ._generated.models.job_progress import JobProgress
from ._generated.models.job_state import JobState
from ._sse import TERMINAL, Parser
from .errors import (
    ApiError,
    JobCancelled,
    JobFailed,
    JobWaitTimeout,
    RequestTimeoutError,
    TransportError,
    UnexpectedResponseError,
    api_error,
)

if TYPE_CHECKING:
    from .async_client import AsyncClient
    from .client import Client


class _Job:
    def __init__(self, raw: WireJob) -> None:
        self.raw = raw

    @property
    def id(self) -> UUID:
        return self.raw.job_id

    @property
    def type(self) -> str:
        return self.raw.type_

    @property
    def state(self) -> JobState:
        return self.raw.state

    @property
    def progress(self) -> JobProgress:
        return self.raw.progress

    @property
    def completeness(self) -> str | None:
        return None if self.raw.completeness is None else str(self.raw.completeness)

    @property
    def diagnostics(self) -> list[str]:
        return self.raw.diagnostics

    @property
    def error(self) -> ApiError | None:
        return (
            api_error(self.raw.error) if isinstance(self.raw.error, WireError) else None
        )

    @property
    def result(self) -> dict[str, object] | None:
        return None if self.raw.result is None else self.raw.result.to_dict()

    def _terminal(self) -> bool:
        if self.state == "FAILED":
            raise JobFailed(self.raw, self.error)
        if self.state == "CANCELLED":
            raise JobCancelled(self.raw)
        return self.state == "SUCCEEDED"

    def __repr__(self) -> str:
        return f"{type(self).__name__}(id={str(self.id)!r}, state={str(self.state)!r})"


def stream_status(response: httpx.Response) -> None:
    if response.status_code != 200:
        try:
            error = ErrorEnvelope.from_dict(response.json())
        except (ValueError, KeyError, TypeError, AttributeError) as exc:
            raise UnexpectedResponseError(
                f"Invalid SSE HTTP {response.status_code} response"
            ) from exc
        raise api_error(error.error, response.status_code, response.headers)
    if response.headers.get("content-type", "").split(";", 1)[0] != "text/event-stream":
        raise UnexpectedResponseError("Expected text/event-stream")


class Job(_Job):
    def __init__(self, client: Client, raw: WireJob) -> None:
        super().__init__(raw)
        self._client = client

    def refresh(self) -> WireJob:
        self.raw = self._client._invoke(get_job, WireJob, job_id=self.id)
        return self.raw

    def cancel(self) -> WireJob:
        self.raw = self._client._invoke(cancel_job, WireJob, job_id=self.id)
        return self.raw

    def wait(self, timeout: float | None = None, poll_interval: float = 0.2) -> WireJob:
        polling(timeout, poll_interval)
        deadline = None if timeout is None else time.monotonic() + timeout
        while not self._terminal():
            self.refresh()
            if self._terminal():
                break
            remaining = None if deadline is None else deadline - time.monotonic()
            if remaining is not None and remaining <= 0:
                raise JobWaitTimeout(
                    "Job wait timed out; server work was not cancelled"
                )
            time.sleep(
                poll_interval if remaining is None else min(poll_interval, remaining)
            )
        return self.raw

    def poll_updates(
        self, timeout: float | None = None, poll_interval: float = 0.2
    ) -> Iterator[WireJob]:
        polling(timeout, poll_interval)
        deadline = None if timeout is None else time.monotonic() + timeout
        while True:
            yield self.refresh()
            if self.state in TERMINAL:
                return
            remaining = None if deadline is None else deadline - time.monotonic()
            if remaining is not None and remaining <= 0:
                raise JobWaitTimeout(
                    "Progress wait timed out; server work was not cancelled"
                )
            time.sleep(
                poll_interval if remaining is None else min(poll_interval, remaining)
            )

    def events(self, *, last_event_id: str | None = None) -> Iterator[JobEvent]:
        if self._client._closed:
            raise TransportError("Client is closed")
        headers = {} if last_event_id is None else {"Last-Event-ID": last_event_id}
        # The server is authoritative for malformed/future IDs.
        try:
            with self._client.lowlevel.get_httpx_client().stream(
                "GET", f"/jobs/{self.id}/events", headers=headers
            ) as response:
                if response.status_code != 200:
                    response.read()
                stream_status(response)
                parser = Parser(self.id, last_event_id)
                for chunk in response.iter_bytes():
                    for offset in range(0, len(chunk), 16384):
                        yield from parser.feed(chunk[offset : offset + 16384])
                    if parser.terminal:
                        return
                raise TransportError(
                    "Job event stream ended before terminal event; poll or reconnect explicitly"
                )
        except httpx.TimeoutException as exc:
            raise RequestTimeoutError(str(exc)) from exc
        except httpx.HTTPError as exc:
            raise TransportError(str(exc)) from exc


class AsyncJob(_Job):
    def __init__(self, client: AsyncClient, raw: WireJob) -> None:
        super().__init__(raw)
        self._client = client

    async def refresh(self) -> WireJob:
        self.raw = await self._client._invoke(get_job, WireJob, job_id=self.id)
        return self.raw

    async def cancel(self) -> WireJob:
        self.raw = await self._client._invoke(cancel_job, WireJob, job_id=self.id)
        return self.raw

    async def wait(
        self, timeout: float | None = None, poll_interval: float = 0.2
    ) -> WireJob:
        polling(timeout, poll_interval)
        deadline = None if timeout is None else time.monotonic() + timeout
        while not self._terminal():
            await self.refresh()
            if self._terminal():
                break
            remaining = None if deadline is None else deadline - time.monotonic()
            if remaining is not None and remaining <= 0:
                raise JobWaitTimeout(
                    "Job wait timed out; server work was not cancelled"
                )
            await asyncio.sleep(
                poll_interval if remaining is None else min(poll_interval, remaining)
            )
        return self.raw

    async def poll_updates(
        self, timeout: float | None = None, poll_interval: float = 0.2
    ) -> AsyncIterator[WireJob]:
        polling(timeout, poll_interval)
        deadline = None if timeout is None else time.monotonic() + timeout
        while True:
            yield await self.refresh()
            if self.state in TERMINAL:
                return
            remaining = None if deadline is None else deadline - time.monotonic()
            if remaining is not None and remaining <= 0:
                raise JobWaitTimeout(
                    "Progress wait timed out; server work was not cancelled"
                )
            await asyncio.sleep(
                poll_interval if remaining is None else min(poll_interval, remaining)
            )

    async def events(
        self, *, last_event_id: str | None = None
    ) -> AsyncIterator[JobEvent]:
        if self._client._closed:
            raise TransportError("Client is closed")
        headers = {} if last_event_id is None else {"Last-Event-ID": last_event_id}
        # The server is authoritative for malformed/future IDs.
        try:
            async with self._client.lowlevel.get_async_httpx_client().stream(
                "GET", f"/jobs/{self.id}/events", headers=headers
            ) as response:
                if response.status_code != 200:
                    await response.aread()
                stream_status(response)
                parser = Parser(self.id, last_event_id)
                async for chunk in response.aiter_bytes():
                    for offset in range(0, len(chunk), 16384):
                        for event in parser.feed(chunk[offset : offset + 16384]):
                            yield event
                    if parser.terminal:
                        return
                raise TransportError(
                    "Job event stream ended before terminal event; poll or reconnect explicitly"
                )
        except httpx.TimeoutException as exc:
            raise RequestTimeoutError(str(exc)) from exc
        except httpx.HTTPError as exc:
            raise TransportError(str(exc)) from exc
