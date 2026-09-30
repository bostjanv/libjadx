from http import HTTPStatus
from typing import Any

import httpx

from ... import errors
from ...client import AuthenticatedClient, Client
from ...models.edit_batch_request import EditBatchRequest
from ...models.edit_batch_result import EditBatchResult
from ...models.error_envelope import ErrorEnvelope
from ...types import Response


def _get_kwargs(
    *,
    body: EditBatchRequest,
) -> dict[str, Any]:
    headers: dict[str, Any] = {}

    _kwargs: dict[str, Any] = {
        "method": "post",
        "url": "/edits/batch",
    }

    _kwargs["json"] = body.to_dict()

    headers["Content-Type"] = "application/json"

    _kwargs["headers"] = headers
    return _kwargs


def _parse_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> EditBatchResult | ErrorEnvelope | None:
    if response.status_code == 200:
        response_200 = EditBatchResult.from_dict(response.json())

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
) -> Response[EditBatchResult | ErrorEnvelope]:
    return Response(
        status_code=HTTPStatus(response.status_code),
        content=response.content,
        headers=response.headers,
        parsed=_parse_response(client=client, response=response),
    )


def sync_detailed(
    *,
    client: AuthenticatedClient | Client,
    body: EditBatchRequest,
) -> Response[EditBatchResult | ErrorEnvelope]:
    """Apply native declaration and scoped parameter edits to the in-memory project

     Validates every item under one exclusive admission before changing native code data. Declaration-
    only batches may supply both revision preconditions or omit both for unconditional admission against
    the current revision. A complete no-op retains all revisions and dirty state. A successful edit
    remains memory-only until explicit native save. An unexpected staging failure returns PARTIAL with
    itemized FAILED and SKIPPED results, committing a verified applied prefix when one exists. If no
    item was applied, logical/index revisions and dirty state remain unchanged. Only LINE declaration
    comments are currently accepted. Aliases without an explicit native, mapping or supported scoped
    rename are derived Jadx analysis state and may be recomputed after an edit or rebuild. Automatic
    recomputation creates no additional native edit record. Effective batches load one private fresh
    replacement before committing; candidate failure publishes no edits. Inputs and the attached mapping
    must match the accepted analysis baseline before and after replacement loading. An external change
    returns 409 EXTERNAL_MODIFICATION_CONFLICT without changing the active engine, edits or revisions;
    explicitly reload to accept changed bytes. A propagated request always checks accepted input/mapping
    baselines, including no-op. Ordinary no-op does not load or adopt external bytes. Optional
    propagateRelated defaults to false, equivalent to omission. True requires a METHOD original ref and
    both revision preconditions. The current engine independently verifies a COMPLETE closed-input
    family under the exclusive edit lease; missing/external, duplicate, bridge/covariant and unsupported
    families fail closed. Limits are four propagated items, 128 total family members and 800000 reserved
    verification work per batch (64 members, 10000 nodes, 200000 work per verification). Each owner is
    checked against all raw methods, including hidden synthetic/bridge declarations, using name plus
    original arguments (excluding return type). Overlapping renames, parameter edits in a group, class
    renames and ordinary METHOD renames in a batch containing a group return 400 INVALID_REQUEST.
    Ordinary METHOD renames are excluded even when disjoint or no-op because pinned Jadx can implicitly
    alias declarations in other owners. Field renames and declaration comments may coexist with a group.
    Collision-removing batches are conservatively rejected to keep every possible committed prefix safe.
    One logical group stages privately and atomically, writing standard explicit native records for
    every member; native save remains explicit. APPLIED and verified NO_CHANGE return exact sorted
    original family affectedRefs (max 64); failed/unexecuted groups and ordinary edits return an empty
    list. No-op requires every member already explicitly renamed to newName and retains
    engine/revisions. Ordinary method RENAME retains pinned Jadx implicit candidate alias propagation;
    it does not guarantee exhaustive membership and its affectedRefs is empty. Local editing and
    parameter propagation remain unsupported. Mapping import/export have separate routes. Scoped
    parameter renames use original semantic parameter indexes and require current session/revision and
    source snapshot preconditions. Targets come from decompile.variables. Only verified plain concrete
    AUTO/RESTRUCTURE signatures are supported; locals remain unsupported. Methods with unverified catch
    declarations (including Jadx's unannotated unused catch arguments) have unsupported parameter
    targets and return UNSUPPORTED_CAPABILITY before staging any batch item. All declared method
    variables are conservatively considered overlapping, including current and proposed names, so no
    staged prefix creates a name collision. No-op batches preserve revisions and source identity.

    Args:
        body (EditBatchRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[EditBatchResult | ErrorEnvelope]
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
    body: EditBatchRequest,
) -> EditBatchResult | ErrorEnvelope | None:
    """Apply native declaration and scoped parameter edits to the in-memory project

     Validates every item under one exclusive admission before changing native code data. Declaration-
    only batches may supply both revision preconditions or omit both for unconditional admission against
    the current revision. A complete no-op retains all revisions and dirty state. A successful edit
    remains memory-only until explicit native save. An unexpected staging failure returns PARTIAL with
    itemized FAILED and SKIPPED results, committing a verified applied prefix when one exists. If no
    item was applied, logical/index revisions and dirty state remain unchanged. Only LINE declaration
    comments are currently accepted. Aliases without an explicit native, mapping or supported scoped
    rename are derived Jadx analysis state and may be recomputed after an edit or rebuild. Automatic
    recomputation creates no additional native edit record. Effective batches load one private fresh
    replacement before committing; candidate failure publishes no edits. Inputs and the attached mapping
    must match the accepted analysis baseline before and after replacement loading. An external change
    returns 409 EXTERNAL_MODIFICATION_CONFLICT without changing the active engine, edits or revisions;
    explicitly reload to accept changed bytes. A propagated request always checks accepted input/mapping
    baselines, including no-op. Ordinary no-op does not load or adopt external bytes. Optional
    propagateRelated defaults to false, equivalent to omission. True requires a METHOD original ref and
    both revision preconditions. The current engine independently verifies a COMPLETE closed-input
    family under the exclusive edit lease; missing/external, duplicate, bridge/covariant and unsupported
    families fail closed. Limits are four propagated items, 128 total family members and 800000 reserved
    verification work per batch (64 members, 10000 nodes, 200000 work per verification). Each owner is
    checked against all raw methods, including hidden synthetic/bridge declarations, using name plus
    original arguments (excluding return type). Overlapping renames, parameter edits in a group, class
    renames and ordinary METHOD renames in a batch containing a group return 400 INVALID_REQUEST.
    Ordinary METHOD renames are excluded even when disjoint or no-op because pinned Jadx can implicitly
    alias declarations in other owners. Field renames and declaration comments may coexist with a group.
    Collision-removing batches are conservatively rejected to keep every possible committed prefix safe.
    One logical group stages privately and atomically, writing standard explicit native records for
    every member; native save remains explicit. APPLIED and verified NO_CHANGE return exact sorted
    original family affectedRefs (max 64); failed/unexecuted groups and ordinary edits return an empty
    list. No-op requires every member already explicitly renamed to newName and retains
    engine/revisions. Ordinary method RENAME retains pinned Jadx implicit candidate alias propagation;
    it does not guarantee exhaustive membership and its affectedRefs is empty. Local editing and
    parameter propagation remain unsupported. Mapping import/export have separate routes. Scoped
    parameter renames use original semantic parameter indexes and require current session/revision and
    source snapshot preconditions. Targets come from decompile.variables. Only verified plain concrete
    AUTO/RESTRUCTURE signatures are supported; locals remain unsupported. Methods with unverified catch
    declarations (including Jadx's unannotated unused catch arguments) have unsupported parameter
    targets and return UNSUPPORTED_CAPABILITY before staging any batch item. All declared method
    variables are conservatively considered overlapping, including current and proposed names, so no
    staged prefix creates a name collision. No-op batches preserve revisions and source identity.

    Args:
        body (EditBatchRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        EditBatchResult | ErrorEnvelope
    """

    return sync_detailed(
        client=client,
        body=body,
    ).parsed


async def asyncio_detailed(
    *,
    client: AuthenticatedClient | Client,
    body: EditBatchRequest,
) -> Response[EditBatchResult | ErrorEnvelope]:
    """Apply native declaration and scoped parameter edits to the in-memory project

     Validates every item under one exclusive admission before changing native code data. Declaration-
    only batches may supply both revision preconditions or omit both for unconditional admission against
    the current revision. A complete no-op retains all revisions and dirty state. A successful edit
    remains memory-only until explicit native save. An unexpected staging failure returns PARTIAL with
    itemized FAILED and SKIPPED results, committing a verified applied prefix when one exists. If no
    item was applied, logical/index revisions and dirty state remain unchanged. Only LINE declaration
    comments are currently accepted. Aliases without an explicit native, mapping or supported scoped
    rename are derived Jadx analysis state and may be recomputed after an edit or rebuild. Automatic
    recomputation creates no additional native edit record. Effective batches load one private fresh
    replacement before committing; candidate failure publishes no edits. Inputs and the attached mapping
    must match the accepted analysis baseline before and after replacement loading. An external change
    returns 409 EXTERNAL_MODIFICATION_CONFLICT without changing the active engine, edits or revisions;
    explicitly reload to accept changed bytes. A propagated request always checks accepted input/mapping
    baselines, including no-op. Ordinary no-op does not load or adopt external bytes. Optional
    propagateRelated defaults to false, equivalent to omission. True requires a METHOD original ref and
    both revision preconditions. The current engine independently verifies a COMPLETE closed-input
    family under the exclusive edit lease; missing/external, duplicate, bridge/covariant and unsupported
    families fail closed. Limits are four propagated items, 128 total family members and 800000 reserved
    verification work per batch (64 members, 10000 nodes, 200000 work per verification). Each owner is
    checked against all raw methods, including hidden synthetic/bridge declarations, using name plus
    original arguments (excluding return type). Overlapping renames, parameter edits in a group, class
    renames and ordinary METHOD renames in a batch containing a group return 400 INVALID_REQUEST.
    Ordinary METHOD renames are excluded even when disjoint or no-op because pinned Jadx can implicitly
    alias declarations in other owners. Field renames and declaration comments may coexist with a group.
    Collision-removing batches are conservatively rejected to keep every possible committed prefix safe.
    One logical group stages privately and atomically, writing standard explicit native records for
    every member; native save remains explicit. APPLIED and verified NO_CHANGE return exact sorted
    original family affectedRefs (max 64); failed/unexecuted groups and ordinary edits return an empty
    list. No-op requires every member already explicitly renamed to newName and retains
    engine/revisions. Ordinary method RENAME retains pinned Jadx implicit candidate alias propagation;
    it does not guarantee exhaustive membership and its affectedRefs is empty. Local editing and
    parameter propagation remain unsupported. Mapping import/export have separate routes. Scoped
    parameter renames use original semantic parameter indexes and require current session/revision and
    source snapshot preconditions. Targets come from decompile.variables. Only verified plain concrete
    AUTO/RESTRUCTURE signatures are supported; locals remain unsupported. Methods with unverified catch
    declarations (including Jadx's unannotated unused catch arguments) have unsupported parameter
    targets and return UNSUPPORTED_CAPABILITY before staging any batch item. All declared method
    variables are conservatively considered overlapping, including current and proposed names, so no
    staged prefix creates a name collision. No-op batches preserve revisions and source identity.

    Args:
        body (EditBatchRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[EditBatchResult | ErrorEnvelope]
    """

    kwargs = _get_kwargs(
        body=body,
    )

    response = await client.get_async_httpx_client().request(**kwargs)

    return _build_response(client=client, response=response)


async def asyncio(
    *,
    client: AuthenticatedClient | Client,
    body: EditBatchRequest,
) -> EditBatchResult | ErrorEnvelope | None:
    """Apply native declaration and scoped parameter edits to the in-memory project

     Validates every item under one exclusive admission before changing native code data. Declaration-
    only batches may supply both revision preconditions or omit both for unconditional admission against
    the current revision. A complete no-op retains all revisions and dirty state. A successful edit
    remains memory-only until explicit native save. An unexpected staging failure returns PARTIAL with
    itemized FAILED and SKIPPED results, committing a verified applied prefix when one exists. If no
    item was applied, logical/index revisions and dirty state remain unchanged. Only LINE declaration
    comments are currently accepted. Aliases without an explicit native, mapping or supported scoped
    rename are derived Jadx analysis state and may be recomputed after an edit or rebuild. Automatic
    recomputation creates no additional native edit record. Effective batches load one private fresh
    replacement before committing; candidate failure publishes no edits. Inputs and the attached mapping
    must match the accepted analysis baseline before and after replacement loading. An external change
    returns 409 EXTERNAL_MODIFICATION_CONFLICT without changing the active engine, edits or revisions;
    explicitly reload to accept changed bytes. A propagated request always checks accepted input/mapping
    baselines, including no-op. Ordinary no-op does not load or adopt external bytes. Optional
    propagateRelated defaults to false, equivalent to omission. True requires a METHOD original ref and
    both revision preconditions. The current engine independently verifies a COMPLETE closed-input
    family under the exclusive edit lease; missing/external, duplicate, bridge/covariant and unsupported
    families fail closed. Limits are four propagated items, 128 total family members and 800000 reserved
    verification work per batch (64 members, 10000 nodes, 200000 work per verification). Each owner is
    checked against all raw methods, including hidden synthetic/bridge declarations, using name plus
    original arguments (excluding return type). Overlapping renames, parameter edits in a group, class
    renames and ordinary METHOD renames in a batch containing a group return 400 INVALID_REQUEST.
    Ordinary METHOD renames are excluded even when disjoint or no-op because pinned Jadx can implicitly
    alias declarations in other owners. Field renames and declaration comments may coexist with a group.
    Collision-removing batches are conservatively rejected to keep every possible committed prefix safe.
    One logical group stages privately and atomically, writing standard explicit native records for
    every member; native save remains explicit. APPLIED and verified NO_CHANGE return exact sorted
    original family affectedRefs (max 64); failed/unexecuted groups and ordinary edits return an empty
    list. No-op requires every member already explicitly renamed to newName and retains
    engine/revisions. Ordinary method RENAME retains pinned Jadx implicit candidate alias propagation;
    it does not guarantee exhaustive membership and its affectedRefs is empty. Local editing and
    parameter propagation remain unsupported. Mapping import/export have separate routes. Scoped
    parameter renames use original semantic parameter indexes and require current session/revision and
    source snapshot preconditions. Targets come from decompile.variables. Only verified plain concrete
    AUTO/RESTRUCTURE signatures are supported; locals remain unsupported. Methods with unverified catch
    declarations (including Jadx's unannotated unused catch arguments) have unsupported parameter
    targets and return UNSUPPORTED_CAPABILITY before staging any batch item. All declared method
    variables are conservatively considered overlapping, including current and proposed names, so no
    staged prefix creates a name collision. No-op batches preserve revisions and source identity.

    Args:
        body (EditBatchRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        EditBatchResult | ErrorEnvelope
    """

    return (
        await asyncio_detailed(
            client=client,
            body=body,
        )
    ).parsed
