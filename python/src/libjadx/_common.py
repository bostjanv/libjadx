from collections.abc import Mapping
from threading import Lock
from typing import Any, TypeVar
from urllib.parse import urlsplit
from uuid import UUID

import httpx

from ._generated.client import Client as GeneratedClient
from ._generated.models.error_envelope import ErrorEnvelope
from ._generated.types import UNSET, Response, Unset
from .errors import UnexpectedResponseError, api_error

T = TypeVar("T")


def base_url(url: str) -> str:
    parsed = urlsplit(url)
    if (
        parsed.scheme not in {"http", "https"}
        or not parsed.netloc
        or parsed.query
        or parsed.fragment
    ):
        raise ValueError("Expected HTTP service origin or /api/v1 URL")
    if parsed.path.rstrip("/") not in {"", "/api/v1"}:
        raise ValueError("Expected service origin or /api/v1 URL")
    return f"{parsed.scheme}://{parsed.netloc}/api/v1"


def connection(
    url: str,
    timeout: float | httpx.Timeout,
    headers: Mapping[str, str] | None,
    trust_env: bool,
) -> GeneratedClient:
    return GeneratedClient(
        base_url=base_url(url),
        timeout=httpx.Timeout(timeout),
        headers=dict(headers or {}),
        httpx_args={"trust_env": trust_env},
    )


def unwrap(response: Response[Any], expected: type[T]) -> T:
    if isinstance(response.parsed, ErrorEnvelope):
        raise api_error(
            response.parsed.error, int(response.status_code), response.headers
        )
    if not 200 <= int(response.status_code) < 300 or not isinstance(
        response.parsed, expected
    ):
        raise UnexpectedResponseError(
            f"Unexpected HTTP {int(response.status_code)} response"
        )
    return response.parsed


class RevisionCache:
    """Monotonic within one session; retired sessions cannot overwrite a new boot."""

    def __init__(self) -> None:
        self._lock = Lock()
        self._retired: set[str] = set()
        self.session_id: str | None = None
        self.logical_revision: int | None = None
        self.index_revision: int | None = None

    def observe(self, result: Any) -> None:
        revisions = getattr(result, "revisions", result)
        session = getattr(revisions, "session_id", None)
        logical = getattr(revisions, "logical_revision", None)
        if logical is None:
            logical = getattr(revisions, "logical_revision_after", None)
        if logical is None:
            logical = getattr(revisions, "after_logical_revision", None)
        index = getattr(revisions, "index_revision", None)
        if index is None:
            index = getattr(revisions, "index_revision_after", None)
        if index is None:
            index = getattr(revisions, "after_index_revision", None)
        # Search index_generation is a different coordinate system.
        if session is None or logical is None:
            return
        session = str(session)
        with self._lock:
            if session in self._retired:
                return
            if self.session_id != session:
                if self.session_id is not None:
                    self._retired.add(self.session_id)
                self.session_id, self.logical_revision, self.index_revision = (
                    session,
                    logical,
                    index,
                )
            else:
                self.logical_revision = max(self.logical_revision or 0, logical)
                if index is not None:
                    self.index_revision = max(self.index_revision or 0, index)

    def expected(
        self,
        session: UUID | str | None | Unset = UNSET,
        revision: int | None | Unset = UNSET,
        *,
        required: bool = False,
    ) -> tuple[UUID | Unset, int | Unset]:
        with self._lock:
            s = self.session_id if isinstance(session, Unset) else session
            r = self.logical_revision if isinstance(revision, Unset) else revision
        if (s is None) != (r is None):
            raise ValueError("Session and logical revision must be provided together")
        if s is None or r is None:
            if required:
                raise ValueError(
                    "Observe project.snapshot() or supply session/revision explicitly"
                )
            return UNSET, UNSET
        return UUID(str(s)), r


def polling(timeout: float | None, interval: float) -> None:
    if interval <= 0 or (timeout is not None and timeout < 0):
        raise ValueError("poll_interval must be positive and timeout nonnegative")
