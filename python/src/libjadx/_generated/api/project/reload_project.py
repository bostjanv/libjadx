from http import HTTPStatus
from typing import Any

import httpx

from ...client import AuthenticatedClient, Client
from ...models.error_envelope import ErrorEnvelope
from ...models.fixed_project import FixedProject
from ...models.reload_project_request import ReloadProjectRequest
from ...types import Response


def _get_kwargs(
    *,
    body: ReloadProjectRequest,
) -> dict[str, Any]:
    headers: dict[str, Any] = {}

    _kwargs: dict[str, Any] = {
        "method": "post",
        "url": "/project/reload",
    }

    _kwargs["json"] = body.to_dict()

    headers["Content-Type"] = "application/json"

    _kwargs["headers"] = headers
    return _kwargs


def _parse_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> ErrorEnvelope | FixedProject:
    if response.status_code == 200:
        response_200 = FixedProject.from_dict(response.json())

        return response_200

    response_default = ErrorEnvelope.from_dict(response.json())

    return response_default


def _build_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> Response[ErrorEnvelope | FixedProject]:
    return Response(
        status_code=HTTPStatus(response.status_code),
        content=response.content,
        headers=response.headers,
        parsed=_parse_response(client=client, response=response),
    )


def sync_detailed(
    *,
    client: AuthenticatedClient | Client,
    body: ReloadProjectRequest,
) -> Response[ErrorEnvelope | FixedProject]:
    """Reload the same native project from disk

     The caller must provide the session and logical revision it observed. A stale precondition is
    rejected before any unsaved edit is discarded. Conflicting in-flight project operations fail
    promptly with HTTP 409 PROJECT_BUSY (retryable); they are not queued.

    Args:
        body (ReloadProjectRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ErrorEnvelope | FixedProject]
    """

    kwargs = _get_kwargs(
        body=body,
    )

    response = client.get_httpx_client().request(
        **kwargs,
    )

    return _build_response(client=client, response=response)


def sync(
    *,
    client: AuthenticatedClient | Client,
    body: ReloadProjectRequest,
) -> ErrorEnvelope | FixedProject | None:
    """Reload the same native project from disk

     The caller must provide the session and logical revision it observed. A stale precondition is
    rejected before any unsaved edit is discarded. Conflicting in-flight project operations fail
    promptly with HTTP 409 PROJECT_BUSY (retryable); they are not queued.

    Args:
        body (ReloadProjectRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ErrorEnvelope | FixedProject
    """

    return sync_detailed(
        client=client,
        body=body,
    ).parsed


async def asyncio_detailed(
    *,
    client: AuthenticatedClient | Client,
    body: ReloadProjectRequest,
) -> Response[ErrorEnvelope | FixedProject]:
    """Reload the same native project from disk

     The caller must provide the session and logical revision it observed. A stale precondition is
    rejected before any unsaved edit is discarded. Conflicting in-flight project operations fail
    promptly with HTTP 409 PROJECT_BUSY (retryable); they are not queued.

    Args:
        body (ReloadProjectRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ErrorEnvelope | FixedProject]
    """

    kwargs = _get_kwargs(
        body=body,
    )

    response = await client.get_async_httpx_client().request(**kwargs)

    return _build_response(client=client, response=response)


async def asyncio(
    *,
    client: AuthenticatedClient | Client,
    body: ReloadProjectRequest,
) -> ErrorEnvelope | FixedProject | None:
    """Reload the same native project from disk

     The caller must provide the session and logical revision it observed. A stale precondition is
    rejected before any unsaved edit is discarded. Conflicting in-flight project operations fail
    promptly with HTTP 409 PROJECT_BUSY (retryable); they are not queued.

    Args:
        body (ReloadProjectRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ErrorEnvelope | FixedProject
    """

    return (
        await asyncio_detailed(
            client=client,
            body=body,
        )
    ).parsed
