from http import HTTPStatus
from typing import Any

import httpx

from ...client import AuthenticatedClient, Client
from ...models.error_envelope import ErrorEnvelope
from ...models.project_settings import ProjectSettings
from ...models.update_project_settings_request import UpdateProjectSettingsRequest
from ...types import Response


def _get_kwargs(
    *,
    body: UpdateProjectSettingsRequest,
) -> dict[str, Any]:
    headers: dict[str, Any] = {}

    _kwargs: dict[str, Any] = {
        "method": "patch",
        "url": "/project/settings",
    }

    _kwargs["json"] = body.to_dict()

    headers["Content-Type"] = "application/json"

    _kwargs["headers"] = headers
    return _kwargs


def _parse_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> ErrorEnvelope | ProjectSettings:
    if response.status_code == 200:
        response_200 = ProjectSettings.from_dict(response.json())

        return response_200

    response_default = ErrorEnvelope.from_dict(response.json())

    return response_default


def _build_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> Response[ErrorEnvelope | ProjectSettings]:
    return Response(
        status_code=HTTPStatus(response.status_code),
        content=response.content,
        headers=response.headers,
        parsed=_parse_response(client=client, response=response),
    )


def sync_detailed(
    *,
    client: AuthenticatedClient | Client,
    body: UpdateProjectSettingsRequest,
) -> Response[ErrorEnvelope | ProjectSettings]:
    """Stage a native mapping path and rebuild Jadx analysis

     The decompilation mode is temporary-only and cannot be persisted here. Conflicting in-flight project
    operations fail promptly with HTTP 409 PROJECT_BUSY (retryable); they are not queued.

    Args:
        body (UpdateProjectSettingsRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ErrorEnvelope | ProjectSettings]
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
    body: UpdateProjectSettingsRequest,
) -> ErrorEnvelope | ProjectSettings | None:
    """Stage a native mapping path and rebuild Jadx analysis

     The decompilation mode is temporary-only and cannot be persisted here. Conflicting in-flight project
    operations fail promptly with HTTP 409 PROJECT_BUSY (retryable); they are not queued.

    Args:
        body (UpdateProjectSettingsRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ErrorEnvelope | ProjectSettings
    """

    return sync_detailed(
        client=client,
        body=body,
    ).parsed


async def asyncio_detailed(
    *,
    client: AuthenticatedClient | Client,
    body: UpdateProjectSettingsRequest,
) -> Response[ErrorEnvelope | ProjectSettings]:
    """Stage a native mapping path and rebuild Jadx analysis

     The decompilation mode is temporary-only and cannot be persisted here. Conflicting in-flight project
    operations fail promptly with HTTP 409 PROJECT_BUSY (retryable); they are not queued.

    Args:
        body (UpdateProjectSettingsRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ErrorEnvelope | ProjectSettings]
    """

    kwargs = _get_kwargs(
        body=body,
    )

    response = await client.get_async_httpx_client().request(**kwargs)

    return _build_response(client=client, response=response)


async def asyncio(
    *,
    client: AuthenticatedClient | Client,
    body: UpdateProjectSettingsRequest,
) -> ErrorEnvelope | ProjectSettings | None:
    """Stage a native mapping path and rebuild Jadx analysis

     The decompilation mode is temporary-only and cannot be persisted here. Conflicting in-flight project
    operations fail promptly with HTTP 409 PROJECT_BUSY (retryable); they are not queued.

    Args:
        body (UpdateProjectSettingsRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ErrorEnvelope | ProjectSettings
    """

    return (
        await asyncio_detailed(
            client=client,
            body=body,
        )
    ).parsed
