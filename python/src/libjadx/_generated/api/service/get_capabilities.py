from http import HTTPStatus
from typing import Any

import httpx

from ...client import AuthenticatedClient, Client
from ...models.capabilities import Capabilities
from ...models.error_envelope import ErrorEnvelope
from ...types import Response


def _get_kwargs() -> dict[str, Any]:

    _kwargs: dict[str, Any] = {
        "method": "get",
        "url": "/capabilities",
    }

    return _kwargs


def _parse_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> Capabilities | ErrorEnvelope:
    if response.status_code == 200:
        response_200 = Capabilities.from_dict(response.json())

        return response_200

    response_default = ErrorEnvelope.from_dict(response.json())

    return response_default


def _build_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> Response[Capabilities | ErrorEnvelope]:
    return Response(
        status_code=HTTPStatus(response.status_code),
        content=response.content,
        headers=response.headers,
        parsed=_parse_response(client=client, response=response),
    )


def sync_detailed(
    *,
    client: AuthenticatedClient | Client,
) -> Response[Capabilities | ErrorEnvelope]:
    """Read server and pinned-Jadx capability evidence

     Status describes the public service behavior, not an internal Jadx probe. code.smali is UNSUPPORTED
    because no public Smali representation exists. analysis.concurrent_reads describes parallel primary
    Jadx reads: it is UNSUPPORTED because those reads are serialized and conflicting admission fails
    fast with PROJECT_BUSY. Multiple HTTP clients remain supported. analysis.cancellation is PARTIAL:
    queued/running jobs support cooperative cancellation, but CANCELLING persists until work stops and
    hard interruption of arbitrary Jadx work is not guaranteed. Client deadlines, task cancellation and
    SSE disconnect do not request server cancellation.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[Capabilities | ErrorEnvelope]
    """

    kwargs = _get_kwargs()

    response = client.get_httpx_client().request(
        **kwargs,
    )

    return _build_response(client=client, response=response)


def sync(
    *,
    client: AuthenticatedClient | Client,
) -> Capabilities | ErrorEnvelope | None:
    """Read server and pinned-Jadx capability evidence

     Status describes the public service behavior, not an internal Jadx probe. code.smali is UNSUPPORTED
    because no public Smali representation exists. analysis.concurrent_reads describes parallel primary
    Jadx reads: it is UNSUPPORTED because those reads are serialized and conflicting admission fails
    fast with PROJECT_BUSY. Multiple HTTP clients remain supported. analysis.cancellation is PARTIAL:
    queued/running jobs support cooperative cancellation, but CANCELLING persists until work stops and
    hard interruption of arbitrary Jadx work is not guaranteed. Client deadlines, task cancellation and
    SSE disconnect do not request server cancellation.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Capabilities | ErrorEnvelope
    """

    return sync_detailed(
        client=client,
    ).parsed


async def asyncio_detailed(
    *,
    client: AuthenticatedClient | Client,
) -> Response[Capabilities | ErrorEnvelope]:
    """Read server and pinned-Jadx capability evidence

     Status describes the public service behavior, not an internal Jadx probe. code.smali is UNSUPPORTED
    because no public Smali representation exists. analysis.concurrent_reads describes parallel primary
    Jadx reads: it is UNSUPPORTED because those reads are serialized and conflicting admission fails
    fast with PROJECT_BUSY. Multiple HTTP clients remain supported. analysis.cancellation is PARTIAL:
    queued/running jobs support cooperative cancellation, but CANCELLING persists until work stops and
    hard interruption of arbitrary Jadx work is not guaranteed. Client deadlines, task cancellation and
    SSE disconnect do not request server cancellation.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[Capabilities | ErrorEnvelope]
    """

    kwargs = _get_kwargs()

    response = await client.get_async_httpx_client().request(**kwargs)

    return _build_response(client=client, response=response)


async def asyncio(
    *,
    client: AuthenticatedClient | Client,
) -> Capabilities | ErrorEnvelope | None:
    """Read server and pinned-Jadx capability evidence

     Status describes the public service behavior, not an internal Jadx probe. code.smali is UNSUPPORTED
    because no public Smali representation exists. analysis.concurrent_reads describes parallel primary
    Jadx reads: it is UNSUPPORTED because those reads are serialized and conflicting admission fails
    fast with PROJECT_BUSY. Multiple HTTP clients remain supported. analysis.cancellation is PARTIAL:
    queued/running jobs support cooperative cancellation, but CANCELLING persists until work stops and
    hard interruption of arbitrary Jadx work is not guaranteed. Client deadlines, task cancellation and
    SSE disconnect do not request server cancellation.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Capabilities | ErrorEnvelope
    """

    return (
        await asyncio_detailed(
            client=client,
        )
    ).parsed
