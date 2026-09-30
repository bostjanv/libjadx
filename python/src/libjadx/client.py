from __future__ import annotations

import time
from collections.abc import Mapping
from types import ModuleType
from typing import Any, Self, TypeVar
from uuid import UUID

import httpx

from ._common import base_url, connection, polling, unwrap
from ._generated.api.service import (
    get_capabilities,
    get_job,
    get_liveness,
    get_status,
    shutdown_service,
)
from ._generated.models.api_error import ApiError as WireError
from ._generated.models.capabilities import Capabilities
from ._generated.models.job import Job as WireJob
from ._generated.models.liveness import Liveness
from ._generated.models.service_status import ServiceStatus
from ._generated.models.shutdown_accepted import ShutdownAccepted
from ._generated.models.shutdown_request import ShutdownRequest
from ._generated.models.shutdown_request_policy import ShutdownRequestPolicy
from .errors import (
    RequestTimeoutError,
    ServiceLoadFailed,
    TransportError,
    UnexpectedResponseError,
    WaitReadyTimeout,
    api_error,
)
from .jobs import Job
from .project import Project

T = TypeVar("T")


class Client:
    def __init__(
        self,
        url: str,
        *,
        timeout: float | httpx.Timeout = 10.0,
        headers: Mapping[str, str] | None = None,
        trust_env: bool = False,
        http_client: httpx.Client | None = None,
    ) -> None:
        self.base_url = base_url(url)
        self.lowlevel = connection(url, timeout, headers, trust_env)
        if http_client is not None:
            if str(http_client.base_url).rstrip("/") != self.base_url:
                raise ValueError(
                    "Injected HTTP client's base_url must match normalized /api/v1 URL"
                )
            self.lowlevel.set_httpx_client(http_client)
        self._closed = False
        self.project = Project(self)

    def _invoke(self, operation: ModuleType, expected: type[T], **kwargs: Any) -> T:
        if self._closed:
            raise TransportError("Client is closed")
        try:
            response = operation.sync_detailed(client=self.lowlevel, **kwargs)
        except httpx.TimeoutException as exc:
            raise RequestTimeoutError(str(exc)) from exc
        except httpx.HTTPError as exc:
            raise TransportError(str(exc)) from exc
        except (ValueError, TypeError, KeyError, AttributeError) as exc:
            raise UnexpectedResponseError(
                "Response could not be decoded against the contract"
            ) from exc
        result = unwrap(response, expected)
        self.project._revisions.observe(result)
        return result

    def liveness(self) -> Liveness:
        return self._invoke(get_liveness, Liveness)

    def status(self) -> ServiceStatus:
        return self._invoke(get_status, ServiceStatus)

    def capabilities(self) -> Capabilities:
        return self._invoke(get_capabilities, Capabilities)

    def wait_ready(
        self, timeout: float = 30.0, poll_interval: float = 0.1
    ) -> ServiceStatus:
        polling(timeout, poll_interval)
        deadline = time.monotonic() + timeout
        while True:
            status = self.status()
            if status.state == "READY":
                return status
            if status.state == "FAILED":
                error = (
                    api_error(status.error)
                    if isinstance(status.error, WireError)
                    else None
                )
                raise ServiceLoadFailed(status, error)
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise WaitReadyTimeout("Service did not become READY before timeout")
            time.sleep(min(poll_interval, remaining))

    def job(self, job_id: UUID | str) -> Job:
        return Job(self, self._invoke(get_job, WireJob, job_id=UUID(str(job_id))))

    def shutdown(
        self, policy: ShutdownRequestPolicy | str = "discard"
    ) -> ShutdownAccepted:
        return self._invoke(
            shutdown_service,
            ShutdownAccepted,
            body=ShutdownRequest(policy=ShutdownRequestPolicy(policy)),
        )

    def close(self) -> None:
        if not self._closed:
            self._closed = True
            self.lowlevel.get_httpx_client().close()

    def __enter__(self) -> Self:
        if self._closed:
            raise TransportError("Client is closed")
        return self

    def __exit__(self, *args: object) -> None:
        self.close()
