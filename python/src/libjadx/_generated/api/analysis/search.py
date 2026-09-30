from http import HTTPStatus
from typing import Any

import httpx

from ... import errors
from ...client import AuthenticatedClient, Client
from ...models.error_envelope import ErrorEnvelope
from ...models.job import Job
from ...models.search_page import SearchPage
from ...models.search_request import SearchRequest
from ...types import Response


def _get_kwargs(
    *,
    body: SearchRequest,
) -> dict[str, Any]:
    headers: dict[str, Any] = {}

    _kwargs: dict[str, Any] = {
        "method": "post",
        "url": "/search",
    }

    _kwargs["json"] = body.to_dict()

    headers["Content-Type"] = "application/json"

    _kwargs["headers"] = headers
    return _kwargs


def _parse_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> ErrorEnvelope | Job | SearchPage | None:
    if response.status_code == 200:
        response_200 = SearchPage.from_dict(response.json())

        return response_200

    if response.status_code == 202:
        response_202 = Job.from_dict(response.json())

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
) -> Response[ErrorEnvelope | Job | SearchPage]:
    return Response(
        status_code=HTTPStatus(response.status_code),
        content=response.content,
        headers=response.headers,
        parsed=_parse_response(client=client, response=response),
    )


def sync_detailed(
    *,
    client: AuthenticatedClient | Client,
    body: SearchRequest,
) -> Response[ErrorEnvelope | Job | SearchPage]:
    """Search the current process-local Jadx-visible index

     Class names are cataloged without decompiling Java. Member and emitted-owner Java coverage grows as
    classes are processed. COMPLETE describes only currently Jadx-visible eligible entities, never every
    original input definition. Source ranges are UTF-16 half-open offsets into the exact emitted Java
    source snapshot. strict rejects partial coverage. If requireComplete is set, an incomplete index
    starts or reuses an async build and returns 202; repeat the query after that job finishes.

    Args:
        body (SearchRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ErrorEnvelope | Job | SearchPage]
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
    body: SearchRequest,
) -> ErrorEnvelope | Job | SearchPage | None:
    """Search the current process-local Jadx-visible index

     Class names are cataloged without decompiling Java. Member and emitted-owner Java coverage grows as
    classes are processed. COMPLETE describes only currently Jadx-visible eligible entities, never every
    original input definition. Source ranges are UTF-16 half-open offsets into the exact emitted Java
    source snapshot. strict rejects partial coverage. If requireComplete is set, an incomplete index
    starts or reuses an async build and returns 202; repeat the query after that job finishes.

    Args:
        body (SearchRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ErrorEnvelope | Job | SearchPage
    """

    return sync_detailed(
        client=client,
        body=body,
    ).parsed


async def asyncio_detailed(
    *,
    client: AuthenticatedClient | Client,
    body: SearchRequest,
) -> Response[ErrorEnvelope | Job | SearchPage]:
    """Search the current process-local Jadx-visible index

     Class names are cataloged without decompiling Java. Member and emitted-owner Java coverage grows as
    classes are processed. COMPLETE describes only currently Jadx-visible eligible entities, never every
    original input definition. Source ranges are UTF-16 half-open offsets into the exact emitted Java
    source snapshot. strict rejects partial coverage. If requireComplete is set, an incomplete index
    starts or reuses an async build and returns 202; repeat the query after that job finishes.

    Args:
        body (SearchRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ErrorEnvelope | Job | SearchPage]
    """

    kwargs = _get_kwargs(
        body=body,
    )

    response = await client.get_async_httpx_client().request(**kwargs)

    return _build_response(client=client, response=response)


async def asyncio(
    *,
    client: AuthenticatedClient | Client,
    body: SearchRequest,
) -> ErrorEnvelope | Job | SearchPage | None:
    """Search the current process-local Jadx-visible index

     Class names are cataloged without decompiling Java. Member and emitted-owner Java coverage grows as
    classes are processed. COMPLETE describes only currently Jadx-visible eligible entities, never every
    original input definition. Source ranges are UTF-16 half-open offsets into the exact emitted Java
    source snapshot. strict rejects partial coverage. If requireComplete is set, an incomplete index
    starts or reuses an async build and returns 202; repeat the query after that job finishes.

    Args:
        body (SearchRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ErrorEnvelope | Job | SearchPage
    """

    return (
        await asyncio_detailed(
            client=client,
            body=body,
        )
    ).parsed
