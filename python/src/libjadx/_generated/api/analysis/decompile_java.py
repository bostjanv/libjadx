from http import HTTPStatus
from typing import Any

import httpx

from ... import errors
from ...client import AuthenticatedClient, Client
from ...models.decompile_request import DecompileRequest
from ...models.decompile_result import DecompileResult
from ...models.error_envelope import ErrorEnvelope
from ...types import Response


def _get_kwargs(
    *,
    body: DecompileRequest,
) -> dict[str, Any]:
    headers: dict[str, Any] = {}

    _kwargs: dict[str, Any] = {
        "method": "post",
        "url": "/decompile",
    }

    _kwargs["json"] = body.to_dict()

    headers["Content-Type"] = "application/json"

    _kwargs["headers"] = headers
    return _kwargs


def _parse_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> DecompileResult | ErrorEnvelope | None:
    if response.status_code == 200:
        response_200 = DecompileResult.from_dict(response.json())

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

    if response.status_code == 422:
        response_422 = ErrorEnvelope.from_dict(response.json())

        return response_422

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
) -> Response[DecompileResult | ErrorEnvelope]:
    return Response(
        status_code=HTTPStatus(response.status_code),
        content=response.content,
        headers=response.headers,
        parsed=_parse_response(client=client, response=response),
    )


def sync_detailed(
    *,
    client: AuthenticatedClient | Client,
    body: DecompileRequest,
) -> Response[DecompileResult | ErrorEnvelope]:
    """Get Jadx Java for one original class or method identity

     A method request returns its containing class source. A method excerpt is present only when its
    original declaration and complete boundary are independently verified. Source offsets index UTF-16
    code units in the exact returned Java string; ranges are half-open. Lines are 1-based and columns
    are 0-based Unicode code points. Source coordinates are not bytecode offsets or original debug-
    source line numbers. Strict mode returns INCOMPLETE_ANALYSIS when requested coverage is unavailable.

    Args:
        body (DecompileRequest): Only CLASS and METHOD refs are accepted. Revision preconditions
            must occur together. Only JAVA is currently supported; other representations return 422
            UNSUPPORTED_CAPABILITY. Requests are limited to 64 KiB.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[DecompileResult | ErrorEnvelope]
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
    body: DecompileRequest,
) -> DecompileResult | ErrorEnvelope | None:
    """Get Jadx Java for one original class or method identity

     A method request returns its containing class source. A method excerpt is present only when its
    original declaration and complete boundary are independently verified. Source offsets index UTF-16
    code units in the exact returned Java string; ranges are half-open. Lines are 1-based and columns
    are 0-based Unicode code points. Source coordinates are not bytecode offsets or original debug-
    source line numbers. Strict mode returns INCOMPLETE_ANALYSIS when requested coverage is unavailable.

    Args:
        body (DecompileRequest): Only CLASS and METHOD refs are accepted. Revision preconditions
            must occur together. Only JAVA is currently supported; other representations return 422
            UNSUPPORTED_CAPABILITY. Requests are limited to 64 KiB.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        DecompileResult | ErrorEnvelope
    """

    return sync_detailed(
        client=client,
        body=body,
    ).parsed


async def asyncio_detailed(
    *,
    client: AuthenticatedClient | Client,
    body: DecompileRequest,
) -> Response[DecompileResult | ErrorEnvelope]:
    """Get Jadx Java for one original class or method identity

     A method request returns its containing class source. A method excerpt is present only when its
    original declaration and complete boundary are independently verified. Source offsets index UTF-16
    code units in the exact returned Java string; ranges are half-open. Lines are 1-based and columns
    are 0-based Unicode code points. Source coordinates are not bytecode offsets or original debug-
    source line numbers. Strict mode returns INCOMPLETE_ANALYSIS when requested coverage is unavailable.

    Args:
        body (DecompileRequest): Only CLASS and METHOD refs are accepted. Revision preconditions
            must occur together. Only JAVA is currently supported; other representations return 422
            UNSUPPORTED_CAPABILITY. Requests are limited to 64 KiB.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[DecompileResult | ErrorEnvelope]
    """

    kwargs = _get_kwargs(
        body=body,
    )

    response = await client.get_async_httpx_client().request(**kwargs)

    return _build_response(client=client, response=response)


async def asyncio(
    *,
    client: AuthenticatedClient | Client,
    body: DecompileRequest,
) -> DecompileResult | ErrorEnvelope | None:
    """Get Jadx Java for one original class or method identity

     A method request returns its containing class source. A method excerpt is present only when its
    original declaration and complete boundary are independently verified. Source offsets index UTF-16
    code units in the exact returned Java string; ranges are half-open. Lines are 1-based and columns
    are 0-based Unicode code points. Source coordinates are not bytecode offsets or original debug-
    source line numbers. Strict mode returns INCOMPLETE_ANALYSIS when requested coverage is unavailable.

    Args:
        body (DecompileRequest): Only CLASS and METHOD refs are accepted. Revision preconditions
            must occur together. Only JAVA is currently supported; other representations return 422
            UNSUPPORTED_CAPABILITY. Requests are limited to 64 KiB.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        DecompileResult | ErrorEnvelope
    """

    return (
        await asyncio_detailed(
            client=client,
            body=body,
        )
    ).parsed
