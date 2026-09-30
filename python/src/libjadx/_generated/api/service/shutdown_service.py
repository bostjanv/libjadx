from http import HTTPStatus
from typing import Any

import httpx

from ... import errors
from ...client import AuthenticatedClient, Client
from ...models.error_envelope import ErrorEnvelope
from ...models.shutdown_accepted import ShutdownAccepted
from ...models.shutdown_request import ShutdownRequest
from ...types import UNSET, Response, Unset


def _get_kwargs(
    *,
    body: ShutdownRequest | Unset = UNSET,
) -> dict[str, Any]:
    headers: dict[str, Any] = {}

    _kwargs: dict[str, Any] = {
        "method": "post",
        "url": "/shutdown",
    }

    if not isinstance(body, Unset):
        _kwargs["json"] = body.to_dict()

    headers["Content-Type"] = "application/json"

    _kwargs["headers"] = headers
    return _kwargs


def _parse_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> ErrorEnvelope | ShutdownAccepted | None:
    if response.status_code == 202:
        response_202 = ShutdownAccepted.from_dict(response.json())

        return response_202

    if response.status_code == 400:
        response_400 = ErrorEnvelope.from_dict(response.json())

        return response_400

    if response.status_code == 403:
        response_403 = ErrorEnvelope.from_dict(response.json())

        return response_403

    if response.status_code == 409:
        response_409 = ErrorEnvelope.from_dict(response.json())

        return response_409

    if response.status_code == 500:
        response_500 = ErrorEnvelope.from_dict(response.json())

        return response_500

    if response.status_code == 503:
        response_503 = ErrorEnvelope.from_dict(response.json())

        return response_503

    if client.raise_on_unexpected_status:
        raise errors.UnexpectedStatus(response.status_code, response.content)
    else:
        return None


def _build_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> Response[ErrorEnvelope | ShutdownAccepted]:
    return Response(
        status_code=HTTPStatus(response.status_code),
        content=response.content,
        headers=response.headers,
        parsed=_parse_response(client=client, response=response),
    )


def sync_detailed(
    *,
    client: AuthenticatedClient | Client,
    body: ShutdownRequest | Unset = UNSET,
) -> Response[ErrorEnvelope | ShutdownAccepted]:
    """Request graceful shutdown of this fixed-project process

     Omitted policy means discard. Active operations and nonterminal jobs return retryable PROJECT_BUSY
    without cancellation. refuse_if_dirty rejects unsaved edits without changing admission. save uses
    the native project path already established by startup or an explicit save and checks external
    changes before shutdown. The acknowledgment is sent before listener teardown; SIGKILL cannot
    guarantee persistence. A clearly cross-origin Origin returns INPUT_SECURITY_REJECTION.

    Args:
        body (ShutdownRequest | Unset):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ErrorEnvelope | ShutdownAccepted]
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
    body: ShutdownRequest | Unset = UNSET,
) -> ErrorEnvelope | ShutdownAccepted | None:
    """Request graceful shutdown of this fixed-project process

     Omitted policy means discard. Active operations and nonterminal jobs return retryable PROJECT_BUSY
    without cancellation. refuse_if_dirty rejects unsaved edits without changing admission. save uses
    the native project path already established by startup or an explicit save and checks external
    changes before shutdown. The acknowledgment is sent before listener teardown; SIGKILL cannot
    guarantee persistence. A clearly cross-origin Origin returns INPUT_SECURITY_REJECTION.

    Args:
        body (ShutdownRequest | Unset):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ErrorEnvelope | ShutdownAccepted
    """

    return sync_detailed(
        client=client,
        body=body,
    ).parsed


async def asyncio_detailed(
    *,
    client: AuthenticatedClient | Client,
    body: ShutdownRequest | Unset = UNSET,
) -> Response[ErrorEnvelope | ShutdownAccepted]:
    """Request graceful shutdown of this fixed-project process

     Omitted policy means discard. Active operations and nonterminal jobs return retryable PROJECT_BUSY
    without cancellation. refuse_if_dirty rejects unsaved edits without changing admission. save uses
    the native project path already established by startup or an explicit save and checks external
    changes before shutdown. The acknowledgment is sent before listener teardown; SIGKILL cannot
    guarantee persistence. A clearly cross-origin Origin returns INPUT_SECURITY_REJECTION.

    Args:
        body (ShutdownRequest | Unset):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ErrorEnvelope | ShutdownAccepted]
    """

    kwargs = _get_kwargs(
        body=body,
    )

    response = await client.get_async_httpx_client().request(**kwargs)

    return _build_response(client=client, response=response)


async def asyncio(
    *,
    client: AuthenticatedClient | Client,
    body: ShutdownRequest | Unset = UNSET,
) -> ErrorEnvelope | ShutdownAccepted | None:
    """Request graceful shutdown of this fixed-project process

     Omitted policy means discard. Active operations and nonterminal jobs return retryable PROJECT_BUSY
    without cancellation. refuse_if_dirty rejects unsaved edits without changing admission. save uses
    the native project path already established by startup or an explicit save and checks external
    changes before shutdown. The acknowledgment is sent before listener teardown; SIGKILL cannot
    guarantee persistence. A clearly cross-origin Origin returns INPUT_SECURITY_REJECTION.

    Args:
        body (ShutdownRequest | Unset):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ErrorEnvelope | ShutdownAccepted
    """

    return (
        await asyncio_detailed(
            client=client,
            body=body,
        )
    ).parsed
