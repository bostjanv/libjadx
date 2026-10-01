from http import HTTPStatus
from typing import Any

import httpx

from ... import errors
from ...client import AuthenticatedClient, Client
from ...models.error_envelope import ErrorEnvelope
from ...models.mapping_import_receipt import MappingImportReceipt
from ...models.mapping_import_request import MappingImportRequest
from ...types import Response


def _get_kwargs(
    *,
    body: MappingImportRequest,
) -> dict[str, Any]:
    headers: dict[str, Any] = {}

    _kwargs: dict[str, Any] = {
        "method": "post",
        "url": "/project/mappings/import",
    }

    _kwargs["json"] = body.to_dict()

    headers["Content-Type"] = "application/json"

    _kwargs["headers"] = headers
    return _kwargs


def _parse_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> ErrorEnvelope | MappingImportReceipt | None:
    if response.status_code == 200:
        response_200 = MappingImportReceipt.from_dict(response.json())

        return response_200

    if response.status_code == 400:
        response_400 = ErrorEnvelope.from_dict(response.json())

        return response_400

    if response.status_code == 403:
        response_403 = ErrorEnvelope.from_dict(response.json())

        return response_403

    if response.status_code == 404:
        response_404 = ErrorEnvelope.from_dict(response.json())

        return response_404

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
) -> Response[ErrorEnvelope | MappingImportReceipt]:
    return Response(
        status_code=HTTPStatus(response.status_code),
        content=response.content,
        headers=response.headers,
        parsed=_parse_response(client=client, response=response),
    )


def sync_detailed(
    *,
    client: AuthenticatedClient | Client,
    body: MappingImportRequest,
) -> Response[ErrorEnvelope | MappingImportReceipt]:
    """Merge validated local Tiny v2 declarations into unsaved native edits

     MERGE_FAIL_ON_CONFLICT is the only mode. Requires all five fields and an existing absolute
    normalized .tiny regular file under an allowed root, with no symlink components. Protected input and
    native project paths are rejected; the accepted attached mapping may be read without attaching or
    modifying it. Captured UTF-8 Tiny 2.0 bytes must use original/mapped namespaces. Unknown or
    duplicate records, metadata/options, inversion, arguments, locals, unresolved or unsupported
    declaration keys fail closed. Full original JVM member descriptors include return and field types.
    Identical effective aliases/comments are unchanged. A differing existing alias (including
    automatically generated engine aliases) or a resulting name collision returns
    MAPPING_MERGE_CONFLICT. New names follow declaration edit ASCII identifier rules. Class renames
    preserve the current package; package moves, changed inner aliases and inner alias records with a
    concurrently renamed enclosing class are unsupported. Class renames require uniqueness across all
    resulting visible qualified aliases, including untouched descendants requalified by an enclosing
    class rename. A descendant collision rejects the entire import before native staging. Comments are
    representable single LINE values. A composite is supported only as an exact accepted attached prefix
    plus a newline and one new native LINE suffix, when no native declaration comment conflicts. Other
    changed comments conflict; ambiguous styles/composites are unsupported. No records are omitted.
    Parsed counts count declarations/comments; applied and unchanged count alias and comment operations
    (an empty destination contributes no alias operation). The whole plan is validated and staged
    privately before one native replacement. Rejection, staging failure and NO_CHANGE leave
    native/index/cache state intact. APPLIED increments logical/index revisions once and invalidates
    derived state. No file is written, saved or attached; explicit native save remains required. A post-
    replacement replay failure returns INTERNAL_ERROR and fails the runtime; rollback is not promised.
    Source identity/bytes and accepted native/mapping baselines are checked immediately before commit,
    without filesystem locking. Effective imports also require every input and the attached mapping to
    match the accepted analysis baseline before and after replacement loading. External changes return
    EXTERNAL_MODIFICATION_CONFLICT without publishing any edits; explicitly reload the project to accept
    changed bytes. External writers after the final check remain a residual race. Limits are 64 KiB HTTP
    body, 4096 path characters, 4 MiB source bytes, 10000 parsed declaration/comment records, 16384
    characters per parsed string, a conservative aggregate 16 MiB intermediate budget and bounded
    metadata enumeration. A request may reach the memory budget before other ceilings.

    Args:
        body (MappingImportRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ErrorEnvelope | MappingImportReceipt]
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
    body: MappingImportRequest,
) -> ErrorEnvelope | MappingImportReceipt | None:
    """Merge validated local Tiny v2 declarations into unsaved native edits

     MERGE_FAIL_ON_CONFLICT is the only mode. Requires all five fields and an existing absolute
    normalized .tiny regular file under an allowed root, with no symlink components. Protected input and
    native project paths are rejected; the accepted attached mapping may be read without attaching or
    modifying it. Captured UTF-8 Tiny 2.0 bytes must use original/mapped namespaces. Unknown or
    duplicate records, metadata/options, inversion, arguments, locals, unresolved or unsupported
    declaration keys fail closed. Full original JVM member descriptors include return and field types.
    Identical effective aliases/comments are unchanged. A differing existing alias (including
    automatically generated engine aliases) or a resulting name collision returns
    MAPPING_MERGE_CONFLICT. New names follow declaration edit ASCII identifier rules. Class renames
    preserve the current package; package moves, changed inner aliases and inner alias records with a
    concurrently renamed enclosing class are unsupported. Class renames require uniqueness across all
    resulting visible qualified aliases, including untouched descendants requalified by an enclosing
    class rename. A descendant collision rejects the entire import before native staging. Comments are
    representable single LINE values. A composite is supported only as an exact accepted attached prefix
    plus a newline and one new native LINE suffix, when no native declaration comment conflicts. Other
    changed comments conflict; ambiguous styles/composites are unsupported. No records are omitted.
    Parsed counts count declarations/comments; applied and unchanged count alias and comment operations
    (an empty destination contributes no alias operation). The whole plan is validated and staged
    privately before one native replacement. Rejection, staging failure and NO_CHANGE leave
    native/index/cache state intact. APPLIED increments logical/index revisions once and invalidates
    derived state. No file is written, saved or attached; explicit native save remains required. A post-
    replacement replay failure returns INTERNAL_ERROR and fails the runtime; rollback is not promised.
    Source identity/bytes and accepted native/mapping baselines are checked immediately before commit,
    without filesystem locking. Effective imports also require every input and the attached mapping to
    match the accepted analysis baseline before and after replacement loading. External changes return
    EXTERNAL_MODIFICATION_CONFLICT without publishing any edits; explicitly reload the project to accept
    changed bytes. External writers after the final check remain a residual race. Limits are 64 KiB HTTP
    body, 4096 path characters, 4 MiB source bytes, 10000 parsed declaration/comment records, 16384
    characters per parsed string, a conservative aggregate 16 MiB intermediate budget and bounded
    metadata enumeration. A request may reach the memory budget before other ceilings.

    Args:
        body (MappingImportRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ErrorEnvelope | MappingImportReceipt
    """

    return sync_detailed(
        client=client,
        body=body,
    ).parsed


async def asyncio_detailed(
    *,
    client: AuthenticatedClient | Client,
    body: MappingImportRequest,
) -> Response[ErrorEnvelope | MappingImportReceipt]:
    """Merge validated local Tiny v2 declarations into unsaved native edits

     MERGE_FAIL_ON_CONFLICT is the only mode. Requires all five fields and an existing absolute
    normalized .tiny regular file under an allowed root, with no symlink components. Protected input and
    native project paths are rejected; the accepted attached mapping may be read without attaching or
    modifying it. Captured UTF-8 Tiny 2.0 bytes must use original/mapped namespaces. Unknown or
    duplicate records, metadata/options, inversion, arguments, locals, unresolved or unsupported
    declaration keys fail closed. Full original JVM member descriptors include return and field types.
    Identical effective aliases/comments are unchanged. A differing existing alias (including
    automatically generated engine aliases) or a resulting name collision returns
    MAPPING_MERGE_CONFLICT. New names follow declaration edit ASCII identifier rules. Class renames
    preserve the current package; package moves, changed inner aliases and inner alias records with a
    concurrently renamed enclosing class are unsupported. Class renames require uniqueness across all
    resulting visible qualified aliases, including untouched descendants requalified by an enclosing
    class rename. A descendant collision rejects the entire import before native staging. Comments are
    representable single LINE values. A composite is supported only as an exact accepted attached prefix
    plus a newline and one new native LINE suffix, when no native declaration comment conflicts. Other
    changed comments conflict; ambiguous styles/composites are unsupported. No records are omitted.
    Parsed counts count declarations/comments; applied and unchanged count alias and comment operations
    (an empty destination contributes no alias operation). The whole plan is validated and staged
    privately before one native replacement. Rejection, staging failure and NO_CHANGE leave
    native/index/cache state intact. APPLIED increments logical/index revisions once and invalidates
    derived state. No file is written, saved or attached; explicit native save remains required. A post-
    replacement replay failure returns INTERNAL_ERROR and fails the runtime; rollback is not promised.
    Source identity/bytes and accepted native/mapping baselines are checked immediately before commit,
    without filesystem locking. Effective imports also require every input and the attached mapping to
    match the accepted analysis baseline before and after replacement loading. External changes return
    EXTERNAL_MODIFICATION_CONFLICT without publishing any edits; explicitly reload the project to accept
    changed bytes. External writers after the final check remain a residual race. Limits are 64 KiB HTTP
    body, 4096 path characters, 4 MiB source bytes, 10000 parsed declaration/comment records, 16384
    characters per parsed string, a conservative aggregate 16 MiB intermediate budget and bounded
    metadata enumeration. A request may reach the memory budget before other ceilings.

    Args:
        body (MappingImportRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ErrorEnvelope | MappingImportReceipt]
    """

    kwargs = _get_kwargs(
        body=body,
    )

    response = await client.get_async_httpx_client().request(**kwargs)

    return _build_response(client=client, response=response)


async def asyncio(
    *,
    client: AuthenticatedClient | Client,
    body: MappingImportRequest,
) -> ErrorEnvelope | MappingImportReceipt | None:
    """Merge validated local Tiny v2 declarations into unsaved native edits

     MERGE_FAIL_ON_CONFLICT is the only mode. Requires all five fields and an existing absolute
    normalized .tiny regular file under an allowed root, with no symlink components. Protected input and
    native project paths are rejected; the accepted attached mapping may be read without attaching or
    modifying it. Captured UTF-8 Tiny 2.0 bytes must use original/mapped namespaces. Unknown or
    duplicate records, metadata/options, inversion, arguments, locals, unresolved or unsupported
    declaration keys fail closed. Full original JVM member descriptors include return and field types.
    Identical effective aliases/comments are unchanged. A differing existing alias (including
    automatically generated engine aliases) or a resulting name collision returns
    MAPPING_MERGE_CONFLICT. New names follow declaration edit ASCII identifier rules. Class renames
    preserve the current package; package moves, changed inner aliases and inner alias records with a
    concurrently renamed enclosing class are unsupported. Class renames require uniqueness across all
    resulting visible qualified aliases, including untouched descendants requalified by an enclosing
    class rename. A descendant collision rejects the entire import before native staging. Comments are
    representable single LINE values. A composite is supported only as an exact accepted attached prefix
    plus a newline and one new native LINE suffix, when no native declaration comment conflicts. Other
    changed comments conflict; ambiguous styles/composites are unsupported. No records are omitted.
    Parsed counts count declarations/comments; applied and unchanged count alias and comment operations
    (an empty destination contributes no alias operation). The whole plan is validated and staged
    privately before one native replacement. Rejection, staging failure and NO_CHANGE leave
    native/index/cache state intact. APPLIED increments logical/index revisions once and invalidates
    derived state. No file is written, saved or attached; explicit native save remains required. A post-
    replacement replay failure returns INTERNAL_ERROR and fails the runtime; rollback is not promised.
    Source identity/bytes and accepted native/mapping baselines are checked immediately before commit,
    without filesystem locking. Effective imports also require every input and the attached mapping to
    match the accepted analysis baseline before and after replacement loading. External changes return
    EXTERNAL_MODIFICATION_CONFLICT without publishing any edits; explicitly reload the project to accept
    changed bytes. External writers after the final check remain a residual race. Limits are 64 KiB HTTP
    body, 4096 path characters, 4 MiB source bytes, 10000 parsed declaration/comment records, 16384
    characters per parsed string, a conservative aggregate 16 MiB intermediate budget and bounded
    metadata enumeration. A request may reach the memory budget before other ceilings.

    Args:
        body (MappingImportRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ErrorEnvelope | MappingImportReceipt
    """

    return (
        await asyncio_detailed(
            client=client,
            body=body,
        )
    ).parsed
