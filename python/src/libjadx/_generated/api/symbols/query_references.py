from http import HTTPStatus
from typing import Any

import httpx

from ... import errors
from ...client import AuthenticatedClient, Client
from ...models.error_envelope import ErrorEnvelope
from ...models.reference_page import ReferencePage
from ...models.reference_query import ReferenceQuery
from ...types import Response


def _get_kwargs(
    *,
    body: ReferenceQuery,
) -> dict[str, Any]:
    headers: dict[str, Any] = {}

    _kwargs: dict[str, Any] = {
        "method": "post",
        "url": "/references/query",
    }

    _kwargs["json"] = body.to_dict()

    headers["Content-Type"] = "application/json"

    _kwargs["headers"] = headers
    return _kwargs


def _parse_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> ErrorEnvelope | ReferencePage | None:
    if response.status_code == 200:
        response_200 = ReferencePage.from_dict(response.json())

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
) -> Response[ErrorEnvelope | ReferencePage]:
    return Response(
        status_code=HTTPStatus(response.status_code),
        content=response.content,
        headers=response.headers,
        parsed=_parse_response(client=client, response=response),
    )


def sync_detailed(
    *,
    client: AuthenticatedClient | Client,
    body: ReferenceQuery,
) -> Response[ErrorEnvelope | ReferencePage]:
    """Query bounded Jadx-reported original reference relationships

     Uses current primary settings under one conservative CLASS_READ lease. CALL is a distinct observed
    method pair, not an invoke count or dynamic dispatch closure. FIELD_USE has unknown access
    direction. CLASS_DEPENDENCY is class-level, not a type occurrence. Coverage is always PARTIAL;
    pageComplete only exhausts the filtered observed set. strict rejects resolved queries with 409
    INCOMPLETE_ANALYSIS; domain misses remain 200. Source sites are optional, exact tokens with verified
    caller and target in a P4.2 source snapshot. Original offsets are unavailable. HMAC authentication
    precedes cursor field interpretation. Changed query options or tampering return 400; authentic stale
    session, revision, publication, settings or graph content returns 409 STALE_REVISION. Limits are
    10000 observed edges, 20000 sites and 4 MiB copied strings, enforced during collection. These do not
    bound Jadx internal allocation or wall time. Busy admission fails promptly with PROJECT_BUSY.

    Args:
        body (ReferenceQuery): Direction is always required. FIELD accepts only
            INCOMING/FIELD_USE; CLASS accepts CLASS_DEPENDENCY; METHOD INCOMING accepts CALL and
            OUTGOING accepts CALL/UNRESOLVED_CALL. Omitted relations selects all compatible relations.
            Empty, repeated or incompatible relations (including unproved READ/WRITE) return 400
            INVALID_REQUEST. Unknown/repeated fields and bodies over 64 KiB are rejected. Revision
            preconditions are checked after admission.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ErrorEnvelope | ReferencePage]
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
    body: ReferenceQuery,
) -> ErrorEnvelope | ReferencePage | None:
    """Query bounded Jadx-reported original reference relationships

     Uses current primary settings under one conservative CLASS_READ lease. CALL is a distinct observed
    method pair, not an invoke count or dynamic dispatch closure. FIELD_USE has unknown access
    direction. CLASS_DEPENDENCY is class-level, not a type occurrence. Coverage is always PARTIAL;
    pageComplete only exhausts the filtered observed set. strict rejects resolved queries with 409
    INCOMPLETE_ANALYSIS; domain misses remain 200. Source sites are optional, exact tokens with verified
    caller and target in a P4.2 source snapshot. Original offsets are unavailable. HMAC authentication
    precedes cursor field interpretation. Changed query options or tampering return 400; authentic stale
    session, revision, publication, settings or graph content returns 409 STALE_REVISION. Limits are
    10000 observed edges, 20000 sites and 4 MiB copied strings, enforced during collection. These do not
    bound Jadx internal allocation or wall time. Busy admission fails promptly with PROJECT_BUSY.

    Args:
        body (ReferenceQuery): Direction is always required. FIELD accepts only
            INCOMING/FIELD_USE; CLASS accepts CLASS_DEPENDENCY; METHOD INCOMING accepts CALL and
            OUTGOING accepts CALL/UNRESOLVED_CALL. Omitted relations selects all compatible relations.
            Empty, repeated or incompatible relations (including unproved READ/WRITE) return 400
            INVALID_REQUEST. Unknown/repeated fields and bodies over 64 KiB are rejected. Revision
            preconditions are checked after admission.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ErrorEnvelope | ReferencePage
    """

    return sync_detailed(
        client=client,
        body=body,
    ).parsed


async def asyncio_detailed(
    *,
    client: AuthenticatedClient | Client,
    body: ReferenceQuery,
) -> Response[ErrorEnvelope | ReferencePage]:
    """Query bounded Jadx-reported original reference relationships

     Uses current primary settings under one conservative CLASS_READ lease. CALL is a distinct observed
    method pair, not an invoke count or dynamic dispatch closure. FIELD_USE has unknown access
    direction. CLASS_DEPENDENCY is class-level, not a type occurrence. Coverage is always PARTIAL;
    pageComplete only exhausts the filtered observed set. strict rejects resolved queries with 409
    INCOMPLETE_ANALYSIS; domain misses remain 200. Source sites are optional, exact tokens with verified
    caller and target in a P4.2 source snapshot. Original offsets are unavailable. HMAC authentication
    precedes cursor field interpretation. Changed query options or tampering return 400; authentic stale
    session, revision, publication, settings or graph content returns 409 STALE_REVISION. Limits are
    10000 observed edges, 20000 sites and 4 MiB copied strings, enforced during collection. These do not
    bound Jadx internal allocation or wall time. Busy admission fails promptly with PROJECT_BUSY.

    Args:
        body (ReferenceQuery): Direction is always required. FIELD accepts only
            INCOMING/FIELD_USE; CLASS accepts CLASS_DEPENDENCY; METHOD INCOMING accepts CALL and
            OUTGOING accepts CALL/UNRESOLVED_CALL. Omitted relations selects all compatible relations.
            Empty, repeated or incompatible relations (including unproved READ/WRITE) return 400
            INVALID_REQUEST. Unknown/repeated fields and bodies over 64 KiB are rejected. Revision
            preconditions are checked after admission.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ErrorEnvelope | ReferencePage]
    """

    kwargs = _get_kwargs(
        body=body,
    )

    response = await client.get_async_httpx_client().request(**kwargs)

    return _build_response(client=client, response=response)


async def asyncio(
    *,
    client: AuthenticatedClient | Client,
    body: ReferenceQuery,
) -> ErrorEnvelope | ReferencePage | None:
    """Query bounded Jadx-reported original reference relationships

     Uses current primary settings under one conservative CLASS_READ lease. CALL is a distinct observed
    method pair, not an invoke count or dynamic dispatch closure. FIELD_USE has unknown access
    direction. CLASS_DEPENDENCY is class-level, not a type occurrence. Coverage is always PARTIAL;
    pageComplete only exhausts the filtered observed set. strict rejects resolved queries with 409
    INCOMPLETE_ANALYSIS; domain misses remain 200. Source sites are optional, exact tokens with verified
    caller and target in a P4.2 source snapshot. Original offsets are unavailable. HMAC authentication
    precedes cursor field interpretation. Changed query options or tampering return 400; authentic stale
    session, revision, publication, settings or graph content returns 409 STALE_REVISION. Limits are
    10000 observed edges, 20000 sites and 4 MiB copied strings, enforced during collection. These do not
    bound Jadx internal allocation or wall time. Busy admission fails promptly with PROJECT_BUSY.

    Args:
        body (ReferenceQuery): Direction is always required. FIELD accepts only
            INCOMING/FIELD_USE; CLASS accepts CLASS_DEPENDENCY; METHOD INCOMING accepts CALL and
            OUTGOING accepts CALL/UNRESOLVED_CALL. Omitted relations selects all compatible relations.
            Empty, repeated or incompatible relations (including unproved READ/WRITE) return 400
            INVALID_REQUEST. Unknown/repeated fields and bodies over 64 KiB are rejected. Revision
            preconditions are checked after admission.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ErrorEnvelope | ReferencePage
    """

    return (
        await asyncio_detailed(
            client=client,
            body=body,
        )
    ).parsed
