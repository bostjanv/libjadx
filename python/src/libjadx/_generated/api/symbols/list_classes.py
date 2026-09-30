from http import HTTPStatus
from typing import Any

import httpx

from ... import errors
from ...client import AuthenticatedClient, Client
from ...models.class_page import ClassPage
from ...models.error_envelope import ErrorEnvelope
from ...models.list_classes_name_domain import ListClassesNameDomain
from ...types import UNSET, Response, Unset


def _get_kwargs(
    *,
    page_size: int | Unset = 50,
    cursor: str | Unset = UNSET,
    package_prefix: str | Unset = UNSET,
    name_contains: str | Unset = UNSET,
    name_domain: ListClassesNameDomain | Unset = ListClassesNameDomain.ORIGINAL,
    include_inner: bool | Unset = True,
) -> dict[str, Any]:

    params: dict[str, Any] = {}

    params["pageSize"] = page_size

    params["cursor"] = cursor

    params["packagePrefix"] = package_prefix

    params["nameContains"] = name_contains

    json_name_domain: str | Unset = UNSET
    if not isinstance(name_domain, Unset):
        json_name_domain = name_domain.value

    params["nameDomain"] = json_name_domain

    params["includeInner"] = include_inner

    params = {k: v for k, v in params.items() if v is not UNSET and v is not None}

    _kwargs: dict[str, Any] = {
        "method": "get",
        "url": "/classes",
        "params": params,
    }

    return _kwargs


def _parse_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> ClassPage | ErrorEnvelope | None:
    if response.status_code == 200:
        response_200 = ClassPage.from_dict(response.json())

        return response_200

    if response.status_code == 400:
        response_400 = ErrorEnvelope.from_dict(response.json())

        return response_400

    if response.status_code == 409:
        response_409 = ErrorEnvelope.from_dict(response.json())

        return response_409

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
) -> Response[ClassPage | ErrorEnvelope]:
    return Response(
        status_code=HTTPStatus(response.status_code),
        content=response.content,
        headers=response.headers,
        parsed=_parse_response(client=client, response=response),
    )


def sync_detailed(
    *,
    client: AuthenticatedClient | Client,
    page_size: int | Unset = 50,
    cursor: str | Unset = UNSET,
    package_prefix: str | Unset = UNSET,
    name_contains: str | Unset = UNSET,
    name_domain: ListClassesNameDomain | Unset = ListClassesNameDomain.ORIGINAL,
    include_inner: bool | Unset = True,
) -> Response[ClassPage | ErrorEnvelope]:
    """Page through currently Jadx-visible classes by original descriptor

     Includes eligible inner and anonymous classes by default. This is the current Jadx-visible set, not
    a census of every source definition. Original descriptors determine order; aliases never determine
    identity or default order. complete means this filtered visible enumeration is exhausted, while
    sourceCoverage=UNVERIFIED means discarded or merged input definitions cannot be ruled out. Cursors
    bind session, logical revision, engine publication and normalized filters. Busy work returns
    retryable PROJECT_BUSY; loading/reloading/shutdown returns 503. The signature is checked before
    cursor payload fields. A tampered cursor returns 400 INVALID_REQUEST; an authentic cursor from an
    older session/revision/publication returns 409 STALE_REVISION.

    Args:
        page_size (int | Unset):  Default: 50.
        cursor (str | Unset):
        package_prefix (str | Unset):
        name_contains (str | Unset):
        name_domain (ListClassesNameDomain | Unset):  Default: ListClassesNameDomain.ORIGINAL.
        include_inner (bool | Unset):  Default: True.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ClassPage | ErrorEnvelope]
    """

    kwargs = _get_kwargs(
        page_size=page_size,
        cursor=cursor,
        package_prefix=package_prefix,
        name_contains=name_contains,
        name_domain=name_domain,
        include_inner=include_inner,
    )

    response = client.get_httpx_client().request(
        **kwargs,
    )

    return _build_response(client=client, response=response)


def sync(
    *,
    client: AuthenticatedClient | Client,
    page_size: int | Unset = 50,
    cursor: str | Unset = UNSET,
    package_prefix: str | Unset = UNSET,
    name_contains: str | Unset = UNSET,
    name_domain: ListClassesNameDomain | Unset = ListClassesNameDomain.ORIGINAL,
    include_inner: bool | Unset = True,
) -> ClassPage | ErrorEnvelope | None:
    """Page through currently Jadx-visible classes by original descriptor

     Includes eligible inner and anonymous classes by default. This is the current Jadx-visible set, not
    a census of every source definition. Original descriptors determine order; aliases never determine
    identity or default order. complete means this filtered visible enumeration is exhausted, while
    sourceCoverage=UNVERIFIED means discarded or merged input definitions cannot be ruled out. Cursors
    bind session, logical revision, engine publication and normalized filters. Busy work returns
    retryable PROJECT_BUSY; loading/reloading/shutdown returns 503. The signature is checked before
    cursor payload fields. A tampered cursor returns 400 INVALID_REQUEST; an authentic cursor from an
    older session/revision/publication returns 409 STALE_REVISION.

    Args:
        page_size (int | Unset):  Default: 50.
        cursor (str | Unset):
        package_prefix (str | Unset):
        name_contains (str | Unset):
        name_domain (ListClassesNameDomain | Unset):  Default: ListClassesNameDomain.ORIGINAL.
        include_inner (bool | Unset):  Default: True.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ClassPage | ErrorEnvelope
    """

    return sync_detailed(
        client=client,
        page_size=page_size,
        cursor=cursor,
        package_prefix=package_prefix,
        name_contains=name_contains,
        name_domain=name_domain,
        include_inner=include_inner,
    ).parsed


async def asyncio_detailed(
    *,
    client: AuthenticatedClient | Client,
    page_size: int | Unset = 50,
    cursor: str | Unset = UNSET,
    package_prefix: str | Unset = UNSET,
    name_contains: str | Unset = UNSET,
    name_domain: ListClassesNameDomain | Unset = ListClassesNameDomain.ORIGINAL,
    include_inner: bool | Unset = True,
) -> Response[ClassPage | ErrorEnvelope]:
    """Page through currently Jadx-visible classes by original descriptor

     Includes eligible inner and anonymous classes by default. This is the current Jadx-visible set, not
    a census of every source definition. Original descriptors determine order; aliases never determine
    identity or default order. complete means this filtered visible enumeration is exhausted, while
    sourceCoverage=UNVERIFIED means discarded or merged input definitions cannot be ruled out. Cursors
    bind session, logical revision, engine publication and normalized filters. Busy work returns
    retryable PROJECT_BUSY; loading/reloading/shutdown returns 503. The signature is checked before
    cursor payload fields. A tampered cursor returns 400 INVALID_REQUEST; an authentic cursor from an
    older session/revision/publication returns 409 STALE_REVISION.

    Args:
        page_size (int | Unset):  Default: 50.
        cursor (str | Unset):
        package_prefix (str | Unset):
        name_contains (str | Unset):
        name_domain (ListClassesNameDomain | Unset):  Default: ListClassesNameDomain.ORIGINAL.
        include_inner (bool | Unset):  Default: True.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ClassPage | ErrorEnvelope]
    """

    kwargs = _get_kwargs(
        page_size=page_size,
        cursor=cursor,
        package_prefix=package_prefix,
        name_contains=name_contains,
        name_domain=name_domain,
        include_inner=include_inner,
    )

    response = await client.get_async_httpx_client().request(**kwargs)

    return _build_response(client=client, response=response)


async def asyncio(
    *,
    client: AuthenticatedClient | Client,
    page_size: int | Unset = 50,
    cursor: str | Unset = UNSET,
    package_prefix: str | Unset = UNSET,
    name_contains: str | Unset = UNSET,
    name_domain: ListClassesNameDomain | Unset = ListClassesNameDomain.ORIGINAL,
    include_inner: bool | Unset = True,
) -> ClassPage | ErrorEnvelope | None:
    """Page through currently Jadx-visible classes by original descriptor

     Includes eligible inner and anonymous classes by default. This is the current Jadx-visible set, not
    a census of every source definition. Original descriptors determine order; aliases never determine
    identity or default order. complete means this filtered visible enumeration is exhausted, while
    sourceCoverage=UNVERIFIED means discarded or merged input definitions cannot be ruled out. Cursors
    bind session, logical revision, engine publication and normalized filters. Busy work returns
    retryable PROJECT_BUSY; loading/reloading/shutdown returns 503. The signature is checked before
    cursor payload fields. A tampered cursor returns 400 INVALID_REQUEST; an authentic cursor from an
    older session/revision/publication returns 409 STALE_REVISION.

    Args:
        page_size (int | Unset):  Default: 50.
        cursor (str | Unset):
        package_prefix (str | Unset):
        name_contains (str | Unset):
        name_domain (ListClassesNameDomain | Unset):  Default: ListClassesNameDomain.ORIGINAL.
        include_inner (bool | Unset):  Default: True.

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ClassPage | ErrorEnvelope
    """

    return (
        await asyncio_detailed(
            client=client,
            page_size=page_size,
            cursor=cursor,
            package_prefix=package_prefix,
            name_contains=name_contains,
            name_domain=name_domain,
            include_inner=include_inner,
        )
    ).parsed
