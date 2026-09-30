from http import HTTPStatus
from typing import Any

import httpx

from ... import errors
from ...client import AuthenticatedClient, Client
from ...models.error_envelope import ErrorEnvelope
from ...models.mapping_export_receipt import MappingExportReceipt
from ...models.mapping_export_request import MappingExportRequest
from ...types import Response


def _get_kwargs(
    *,
    body: MappingExportRequest,
) -> dict[str, Any]:
    headers: dict[str, Any] = {}

    _kwargs: dict[str, Any] = {
        "method": "post",
        "url": "/project/mappings/export",
    }

    _kwargs["json"] = body.to_dict()

    headers["Content-Type"] = "application/json"

    _kwargs["headers"] = headers
    return _kwargs


def _parse_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> ErrorEnvelope | MappingExportReceipt | None:
    if response.status_code == 200:
        response_200 = MappingExportReceipt.from_dict(response.json())

        return response_200

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
) -> Response[ErrorEnvelope | MappingExportReceipt]:
    return Response(
        status_code=HTTPStatus(response.status_code),
        content=response.content,
        headers=response.headers,
        parsed=_parse_response(client=client, response=response),
    )


def sync_detailed(
    *,
    client: AuthenticatedClient | Client,
    body: MappingExportRequest,
) -> Response[ErrorEnvelope | MappingExportReceipt]:
    """Export verified current declarations to a new Tiny v2 file

     Strict synchronous export of accepted attached Tiny v2 declarations plus current native
    class/method/field aliases and LINE declaration comments. Native aliases take precedence; attached
    and native comments are additive. Does not save, attach the output, or change project state or
    revisions. Requires an absolute normalized .tiny path with an existing nonsymlink parent under
    allowed roots. Existing targets are never overwritten, including create-after-check races.
    Unsupported structures, namespaces, inversion, duplicate/unknown records or unresolved keys fail
    before output. An attached zero-byte mapping file returns 422 UNSUPPORTED_CAPABILITY; no attached
    mapping and no native edits may export a header-only Tiny file. Limits are 64 KiB request, 4096 path
    characters, 10000 declaration/comment entries, 4 MiB source/output bytes, 16384 characters per
    string and a conservative 16 MiB intermediate allocation budget. Native project baseline hashing is
    capped at 16 MiB. No partial output. Cross-origin requests return INPUT_SECURITY_REJECTION.
    Incompatible engine work returns prompt PROJECT_BUSY. Filesystems without supported hard-link
    publication fail closed. Parent identity is rechecked before publication; uncooperative directory
    replacement during the final syscall remains an operating-system TOCTOU limitation. Post-publication
    failures report published:true and targetPath; clients must not blindly retry.

    Args:
        body (MappingExportRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ErrorEnvelope | MappingExportReceipt]
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
    body: MappingExportRequest,
) -> ErrorEnvelope | MappingExportReceipt | None:
    """Export verified current declarations to a new Tiny v2 file

     Strict synchronous export of accepted attached Tiny v2 declarations plus current native
    class/method/field aliases and LINE declaration comments. Native aliases take precedence; attached
    and native comments are additive. Does not save, attach the output, or change project state or
    revisions. Requires an absolute normalized .tiny path with an existing nonsymlink parent under
    allowed roots. Existing targets are never overwritten, including create-after-check races.
    Unsupported structures, namespaces, inversion, duplicate/unknown records or unresolved keys fail
    before output. An attached zero-byte mapping file returns 422 UNSUPPORTED_CAPABILITY; no attached
    mapping and no native edits may export a header-only Tiny file. Limits are 64 KiB request, 4096 path
    characters, 10000 declaration/comment entries, 4 MiB source/output bytes, 16384 characters per
    string and a conservative 16 MiB intermediate allocation budget. Native project baseline hashing is
    capped at 16 MiB. No partial output. Cross-origin requests return INPUT_SECURITY_REJECTION.
    Incompatible engine work returns prompt PROJECT_BUSY. Filesystems without supported hard-link
    publication fail closed. Parent identity is rechecked before publication; uncooperative directory
    replacement during the final syscall remains an operating-system TOCTOU limitation. Post-publication
    failures report published:true and targetPath; clients must not blindly retry.

    Args:
        body (MappingExportRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ErrorEnvelope | MappingExportReceipt
    """

    return sync_detailed(
        client=client,
        body=body,
    ).parsed


async def asyncio_detailed(
    *,
    client: AuthenticatedClient | Client,
    body: MappingExportRequest,
) -> Response[ErrorEnvelope | MappingExportReceipt]:
    """Export verified current declarations to a new Tiny v2 file

     Strict synchronous export of accepted attached Tiny v2 declarations plus current native
    class/method/field aliases and LINE declaration comments. Native aliases take precedence; attached
    and native comments are additive. Does not save, attach the output, or change project state or
    revisions. Requires an absolute normalized .tiny path with an existing nonsymlink parent under
    allowed roots. Existing targets are never overwritten, including create-after-check races.
    Unsupported structures, namespaces, inversion, duplicate/unknown records or unresolved keys fail
    before output. An attached zero-byte mapping file returns 422 UNSUPPORTED_CAPABILITY; no attached
    mapping and no native edits may export a header-only Tiny file. Limits are 64 KiB request, 4096 path
    characters, 10000 declaration/comment entries, 4 MiB source/output bytes, 16384 characters per
    string and a conservative 16 MiB intermediate allocation budget. Native project baseline hashing is
    capped at 16 MiB. No partial output. Cross-origin requests return INPUT_SECURITY_REJECTION.
    Incompatible engine work returns prompt PROJECT_BUSY. Filesystems without supported hard-link
    publication fail closed. Parent identity is rechecked before publication; uncooperative directory
    replacement during the final syscall remains an operating-system TOCTOU limitation. Post-publication
    failures report published:true and targetPath; clients must not blindly retry.

    Args:
        body (MappingExportRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ErrorEnvelope | MappingExportReceipt]
    """

    kwargs = _get_kwargs(
        body=body,
    )

    response = await client.get_async_httpx_client().request(**kwargs)

    return _build_response(client=client, response=response)


async def asyncio(
    *,
    client: AuthenticatedClient | Client,
    body: MappingExportRequest,
) -> ErrorEnvelope | MappingExportReceipt | None:
    """Export verified current declarations to a new Tiny v2 file

     Strict synchronous export of accepted attached Tiny v2 declarations plus current native
    class/method/field aliases and LINE declaration comments. Native aliases take precedence; attached
    and native comments are additive. Does not save, attach the output, or change project state or
    revisions. Requires an absolute normalized .tiny path with an existing nonsymlink parent under
    allowed roots. Existing targets are never overwritten, including create-after-check races.
    Unsupported structures, namespaces, inversion, duplicate/unknown records or unresolved keys fail
    before output. An attached zero-byte mapping file returns 422 UNSUPPORTED_CAPABILITY; no attached
    mapping and no native edits may export a header-only Tiny file. Limits are 64 KiB request, 4096 path
    characters, 10000 declaration/comment entries, 4 MiB source/output bytes, 16384 characters per
    string and a conservative 16 MiB intermediate allocation budget. Native project baseline hashing is
    capped at 16 MiB. No partial output. Cross-origin requests return INPUT_SECURITY_REJECTION.
    Incompatible engine work returns prompt PROJECT_BUSY. Filesystems without supported hard-link
    publication fail closed. Parent identity is rechecked before publication; uncooperative directory
    replacement during the final syscall remains an operating-system TOCTOU limitation. Post-publication
    failures report published:true and targetPath; clients must not blindly retry.

    Args:
        body (MappingExportRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ErrorEnvelope | MappingExportReceipt
    """

    return (
        await asyncio_detailed(
            client=client,
            body=body,
        )
    ).parsed
