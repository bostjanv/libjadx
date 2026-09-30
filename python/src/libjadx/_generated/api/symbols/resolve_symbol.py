from http import HTTPStatus
from typing import Any

import httpx

from ... import errors
from ...client import AuthenticatedClient, Client
from ...models.error_envelope import ErrorEnvelope
from ...models.symbol_resolution import SymbolResolution
from ...models.symbol_resolve_request import SymbolResolveRequest
from ...types import Response


def _get_kwargs(
    *,
    body: SymbolResolveRequest,
) -> dict[str, Any]:
    headers: dict[str, Any] = {}

    _kwargs: dict[str, Any] = {
        "method": "post",
        "url": "/symbols/resolve",
    }

    _kwargs["json"] = body.to_dict()

    headers["Content-Type"] = "application/json"

    _kwargs["headers"] = headers
    return _kwargs


def _parse_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> ErrorEnvelope | SymbolResolution | None:
    if response.status_code == 200:
        response_200 = SymbolResolution.from_dict(response.json())

        return response_200

    if response.status_code == 400:
        response_400 = ErrorEnvelope.from_dict(response.json())

        return response_400

    if response.status_code == 409:
        response_409 = ErrorEnvelope.from_dict(response.json())

        return response_409

    if response.status_code == 415:
        response_415 = ErrorEnvelope.from_dict(response.json())

        return response_415

    if response.status_code == 429:
        response_429 = ErrorEnvelope.from_dict(response.json())

        return response_429

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
) -> Response[ErrorEnvelope | SymbolResolution]:
    return Response(
        status_code=HTTPStatus(response.status_code),
        content=response.content,
        headers=response.headers,
        parsed=_parse_response(client=client, response=response),
    )


def sync_detailed(
    *,
    client: AuthenticatedClient | Client,
    body: SymbolResolveRequest,
) -> Response[ErrorEnvelope | SymbolResolution]:
    """Resolve an exact original class, method or field identity

     Well-formed domain misses and uncertain provenance return typed HTTP 200 outcomes. A supplied
    inputIdentity cannot be selected unless exact per-input attribution is verified. Member lookup can
    load/decompile only the requested declaring class. No lookup saves or changes a revision.

    Args:
        body (SymbolResolveRequest): expectedSessionId and expectedLogicalRevision must occur
            together.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ErrorEnvelope | SymbolResolution]
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
    body: SymbolResolveRequest,
) -> ErrorEnvelope | SymbolResolution | None:
    """Resolve an exact original class, method or field identity

     Well-formed domain misses and uncertain provenance return typed HTTP 200 outcomes. A supplied
    inputIdentity cannot be selected unless exact per-input attribution is verified. Member lookup can
    load/decompile only the requested declaring class. No lookup saves or changes a revision.

    Args:
        body (SymbolResolveRequest): expectedSessionId and expectedLogicalRevision must occur
            together.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ErrorEnvelope | SymbolResolution
    """

    return sync_detailed(
        client=client,
        body=body,
    ).parsed


async def asyncio_detailed(
    *,
    client: AuthenticatedClient | Client,
    body: SymbolResolveRequest,
) -> Response[ErrorEnvelope | SymbolResolution]:
    """Resolve an exact original class, method or field identity

     Well-formed domain misses and uncertain provenance return typed HTTP 200 outcomes. A supplied
    inputIdentity cannot be selected unless exact per-input attribution is verified. Member lookup can
    load/decompile only the requested declaring class. No lookup saves or changes a revision.

    Args:
        body (SymbolResolveRequest): expectedSessionId and expectedLogicalRevision must occur
            together.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ErrorEnvelope | SymbolResolution]
    """

    kwargs = _get_kwargs(
        body=body,
    )

    response = await client.get_async_httpx_client().request(**kwargs)

    return _build_response(client=client, response=response)


async def asyncio(
    *,
    client: AuthenticatedClient | Client,
    body: SymbolResolveRequest,
) -> ErrorEnvelope | SymbolResolution | None:
    """Resolve an exact original class, method or field identity

     Well-formed domain misses and uncertain provenance return typed HTTP 200 outcomes. A supplied
    inputIdentity cannot be selected unless exact per-input attribution is verified. Member lookup can
    load/decompile only the requested declaring class. No lookup saves or changes a revision.

    Args:
        body (SymbolResolveRequest): expectedSessionId and expectedLogicalRevision must occur
            together.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ErrorEnvelope | SymbolResolution
    """

    return (
        await asyncio_detailed(
            client=client,
            body=body,
        )
    ).parsed
