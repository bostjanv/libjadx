from http import HTTPStatus
from typing import Any
from urllib.parse import quote
from uuid import UUID

import httpx

from ... import errors
from ...client import AuthenticatedClient, Client
from ...models.error_envelope import ErrorEnvelope
from ...types import UNSET, Response, Unset


def _get_kwargs(
    job_id: UUID,
    *,
    last_event_id: str | Unset = UNSET,
) -> dict[str, Any]:
    headers: dict[str, Any] = {}
    if not isinstance(last_event_id, Unset):
        headers["Last-Event-ID"] = last_event_id

    _kwargs: dict[str, Any] = {
        "method": "get",
        "url": "/jobs/{job_id}/events".format(
            job_id=quote(str(job_id), safe=""),
        ),
    }

    _kwargs["headers"] = headers
    return _kwargs


def _parse_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> ErrorEnvelope | str | None:
    if response.status_code == 200:
        response_200 = response.text
        return response_200

    if response.status_code == 400:
        response_400 = ErrorEnvelope.from_dict(response.json())

        return response_400

    if response.status_code == 404:
        response_404 = ErrorEnvelope.from_dict(response.json())

        return response_404

    if response.status_code == 409:
        response_409 = ErrorEnvelope.from_dict(response.json())

        return response_409

    if response.status_code == 429:
        response_429 = ErrorEnvelope.from_dict(response.json())

        return response_429

    if client.raise_on_unexpected_status:
        raise errors.UnexpectedStatus(response.status_code, response.content)
    else:
        return None


def _build_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> Response[ErrorEnvelope | str]:
    return Response(
        status_code=HTTPStatus(response.status_code),
        content=response.content,
        headers=response.headers,
        parsed=_parse_response(client=client, response=response),
    )


def sync_detailed(
    job_id: UUID,
    *,
    client: AuthenticatedClient | Client,
    last_event_id: str | Unset = UNSET,
) -> Response[ErrorEnvelope | str]:
    """Replay and follow bounded job events

     text/event-stream with numeric per-job IDs starting at 1. Events are job.queued, job.started,
    job.progress, job.cancel_requested, job.completed, job.failed, and job.cancelled. Omit Last-Event-ID
    to replay all retained events. A header older than retained history returns 409
    EVENT_HISTORY_EXPIRED before streaming; a future or malformed ID returns 400 INVALID_REQUEST.
    Clients may poll and reconnect without the stale ID. Comment heartbeats have no ID. Overflow
    disconnects the slow subscriber without cancelling the job. Terminal events close the stream.
    Unknown, evicted, or expired jobs return 404.

    Args:
        job_id (UUID):
        last_event_id (str | Unset):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ErrorEnvelope | str]
    """

    kwargs = _get_kwargs(
        job_id=job_id,
        last_event_id=last_event_id,
    )

    response = client.get_httpx_client().request(
        **kwargs,
    )

    return _build_response(client=client, response=response)


def sync(
    job_id: UUID,
    *,
    client: AuthenticatedClient | Client,
    last_event_id: str | Unset = UNSET,
) -> ErrorEnvelope | str | None:
    """Replay and follow bounded job events

     text/event-stream with numeric per-job IDs starting at 1. Events are job.queued, job.started,
    job.progress, job.cancel_requested, job.completed, job.failed, and job.cancelled. Omit Last-Event-ID
    to replay all retained events. A header older than retained history returns 409
    EVENT_HISTORY_EXPIRED before streaming; a future or malformed ID returns 400 INVALID_REQUEST.
    Clients may poll and reconnect without the stale ID. Comment heartbeats have no ID. Overflow
    disconnects the slow subscriber without cancelling the job. Terminal events close the stream.
    Unknown, evicted, or expired jobs return 404.

    Args:
        job_id (UUID):
        last_event_id (str | Unset):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ErrorEnvelope | str
    """

    return sync_detailed(
        job_id=job_id,
        client=client,
        last_event_id=last_event_id,
    ).parsed


async def asyncio_detailed(
    job_id: UUID,
    *,
    client: AuthenticatedClient | Client,
    last_event_id: str | Unset = UNSET,
) -> Response[ErrorEnvelope | str]:
    """Replay and follow bounded job events

     text/event-stream with numeric per-job IDs starting at 1. Events are job.queued, job.started,
    job.progress, job.cancel_requested, job.completed, job.failed, and job.cancelled. Omit Last-Event-ID
    to replay all retained events. A header older than retained history returns 409
    EVENT_HISTORY_EXPIRED before streaming; a future or malformed ID returns 400 INVALID_REQUEST.
    Clients may poll and reconnect without the stale ID. Comment heartbeats have no ID. Overflow
    disconnects the slow subscriber without cancelling the job. Terminal events close the stream.
    Unknown, evicted, or expired jobs return 404.

    Args:
        job_id (UUID):
        last_event_id (str | Unset):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ErrorEnvelope | str]
    """

    kwargs = _get_kwargs(
        job_id=job_id,
        last_event_id=last_event_id,
    )

    response = await client.get_async_httpx_client().request(**kwargs)

    return _build_response(client=client, response=response)


async def asyncio(
    job_id: UUID,
    *,
    client: AuthenticatedClient | Client,
    last_event_id: str | Unset = UNSET,
) -> ErrorEnvelope | str | None:
    """Replay and follow bounded job events

     text/event-stream with numeric per-job IDs starting at 1. Events are job.queued, job.started,
    job.progress, job.cancel_requested, job.completed, job.failed, and job.cancelled. Omit Last-Event-ID
    to replay all retained events. A header older than retained history returns 409
    EVENT_HISTORY_EXPIRED before streaming; a future or malformed ID returns 400 INVALID_REQUEST.
    Clients may poll and reconnect without the stale ID. Comment heartbeats have no ID. Overflow
    disconnects the slow subscriber without cancelling the job. Terminal events close the stream.
    Unknown, evicted, or expired jobs return 404.

    Args:
        job_id (UUID):
        last_event_id (str | Unset):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ErrorEnvelope | str
    """

    return (
        await asyncio_detailed(
            job_id=job_id,
            client=client,
            last_event_id=last_event_id,
        )
    ).parsed
