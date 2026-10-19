from http import HTTPStatus
from typing import Any

import httpx

from ... import errors
from ...client import AuthenticatedClient, Client
from ...models.control_flow_request import ControlFlowRequest
from ...models.control_flow_result import ControlFlowResult
from ...models.error_envelope import ErrorEnvelope
from ...types import Response


def _get_kwargs(
    *,
    body: ControlFlowRequest,
) -> dict[str, Any]:
    headers: dict[str, Any] = {}

    _kwargs: dict[str, Any] = {
        "method": "post",
        "url": "/analysis/control-flow",
    }

    _kwargs["json"] = body.to_dict()

    headers["Content-Type"] = "application/json"

    _kwargs["headers"] = headers
    return _kwargs


def _parse_response(
    *, client: AuthenticatedClient | Client, response: httpx.Response
) -> ControlFlowResult | ErrorEnvelope | None:
    if response.status_code == 200:
        response_200 = ControlFlowResult.from_dict(response.json())

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
) -> Response[ControlFlowResult | ErrorEnvelope]:
    return Response(
        status_code=HTTPStatus(response.status_code),
        content=response.content,
        headers=response.headers,
        parsed=_parse_response(client=client, response=response),
    )


def sync_detailed(
    *,
    client: AuthenticatedClient | Client,
    body: ControlFlowRequest,
) -> Response[ControlFlowResult | ErrorEnvelope]:
    """Observe bounded loaded-DEX method control-flow facts (not a CFG)

     One original METHOD under the serialized primary-read lease, without Java generation or graph
    passes. Coordinates are method-relative DEX_CODE_UNIT_16 from LOADED_METHOD_INSNS_START, not
    original-input addresses or verified input/archive provenance (conversion plugins may supply DEX).
    Instructions include NOPs without inferring intent; payloads are separate non-executable records.
    Branch nextOffset is the sequential boundary, not an inferred feasible edge. Switch destinations are
    absolute loaded-method positions, computed relative to the switch, not its payload. Try intervals
    are half-open; ordered typed handlers followed by optional catchall are table facts, not exhaustive
    exception dispatch. Coverage is always PARTIAL. AVAILABLE includes straight-line methods with no
    branches; UNAVAILABLE means no unique loaded DEX code; FAILED means malformed traversal with all
    fact arrays empty. Precision is EXACT only for AVAILABLE loaded operands. Resolution misses remain
    200 even with strict. After readiness, validation, admission and paired revision checks, resolved
    strict requests reject with INCOMPLETE_ANALYSIS; materialization RESOURCE_LIMIT takes precedence.
    Fixed inclusive limits: 4096 code units, 1024 records including payloads, 1024 aggregate switch
    cases, 256 tries, 1024 handler entries (including catchalls, counted across ranges and encoded
    lists), 1024 UTF-16 units per handler type, and 2097152 actual UTF-8 JSON response bytes. Oversize
    refuses atomically with RESOURCE_LIMIT, never truncates. Malformed counts/extents fail atomically
    before upstream variable-size decode. Bounds do not cover upstream loading, JVM heap, deadlines or
    hostile-input isolation. Snapshot binds method, representation, observed content, session, revision,
    publication and settings; explicit save may rebind without a logical edit. Unknown/repeated fields,
    incompatible refs/options and invalid paired preconditions reject with INVALID_REQUEST. No
    overrides, cursor or jobs. analysis.cfg remains UNKNOWN; /analysis/cfg remains unimplemented.

    Args:
        body (ControlFlowRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ControlFlowResult | ErrorEnvelope]
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
    body: ControlFlowRequest,
) -> ControlFlowResult | ErrorEnvelope | None:
    """Observe bounded loaded-DEX method control-flow facts (not a CFG)

     One original METHOD under the serialized primary-read lease, without Java generation or graph
    passes. Coordinates are method-relative DEX_CODE_UNIT_16 from LOADED_METHOD_INSNS_START, not
    original-input addresses or verified input/archive provenance (conversion plugins may supply DEX).
    Instructions include NOPs without inferring intent; payloads are separate non-executable records.
    Branch nextOffset is the sequential boundary, not an inferred feasible edge. Switch destinations are
    absolute loaded-method positions, computed relative to the switch, not its payload. Try intervals
    are half-open; ordered typed handlers followed by optional catchall are table facts, not exhaustive
    exception dispatch. Coverage is always PARTIAL. AVAILABLE includes straight-line methods with no
    branches; UNAVAILABLE means no unique loaded DEX code; FAILED means malformed traversal with all
    fact arrays empty. Precision is EXACT only for AVAILABLE loaded operands. Resolution misses remain
    200 even with strict. After readiness, validation, admission and paired revision checks, resolved
    strict requests reject with INCOMPLETE_ANALYSIS; materialization RESOURCE_LIMIT takes precedence.
    Fixed inclusive limits: 4096 code units, 1024 records including payloads, 1024 aggregate switch
    cases, 256 tries, 1024 handler entries (including catchalls, counted across ranges and encoded
    lists), 1024 UTF-16 units per handler type, and 2097152 actual UTF-8 JSON response bytes. Oversize
    refuses atomically with RESOURCE_LIMIT, never truncates. Malformed counts/extents fail atomically
    before upstream variable-size decode. Bounds do not cover upstream loading, JVM heap, deadlines or
    hostile-input isolation. Snapshot binds method, representation, observed content, session, revision,
    publication and settings; explicit save may rebind without a logical edit. Unknown/repeated fields,
    incompatible refs/options and invalid paired preconditions reject with INVALID_REQUEST. No
    overrides, cursor or jobs. analysis.cfg remains UNKNOWN; /analysis/cfg remains unimplemented.

    Args:
        body (ControlFlowRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ControlFlowResult | ErrorEnvelope
    """

    return sync_detailed(
        client=client,
        body=body,
    ).parsed


async def asyncio_detailed(
    *,
    client: AuthenticatedClient | Client,
    body: ControlFlowRequest,
) -> Response[ControlFlowResult | ErrorEnvelope]:
    """Observe bounded loaded-DEX method control-flow facts (not a CFG)

     One original METHOD under the serialized primary-read lease, without Java generation or graph
    passes. Coordinates are method-relative DEX_CODE_UNIT_16 from LOADED_METHOD_INSNS_START, not
    original-input addresses or verified input/archive provenance (conversion plugins may supply DEX).
    Instructions include NOPs without inferring intent; payloads are separate non-executable records.
    Branch nextOffset is the sequential boundary, not an inferred feasible edge. Switch destinations are
    absolute loaded-method positions, computed relative to the switch, not its payload. Try intervals
    are half-open; ordered typed handlers followed by optional catchall are table facts, not exhaustive
    exception dispatch. Coverage is always PARTIAL. AVAILABLE includes straight-line methods with no
    branches; UNAVAILABLE means no unique loaded DEX code; FAILED means malformed traversal with all
    fact arrays empty. Precision is EXACT only for AVAILABLE loaded operands. Resolution misses remain
    200 even with strict. After readiness, validation, admission and paired revision checks, resolved
    strict requests reject with INCOMPLETE_ANALYSIS; materialization RESOURCE_LIMIT takes precedence.
    Fixed inclusive limits: 4096 code units, 1024 records including payloads, 1024 aggregate switch
    cases, 256 tries, 1024 handler entries (including catchalls, counted across ranges and encoded
    lists), 1024 UTF-16 units per handler type, and 2097152 actual UTF-8 JSON response bytes. Oversize
    refuses atomically with RESOURCE_LIMIT, never truncates. Malformed counts/extents fail atomically
    before upstream variable-size decode. Bounds do not cover upstream loading, JVM heap, deadlines or
    hostile-input isolation. Snapshot binds method, representation, observed content, session, revision,
    publication and settings; explicit save may rebind without a logical edit. Unknown/repeated fields,
    incompatible refs/options and invalid paired preconditions reject with INVALID_REQUEST. No
    overrides, cursor or jobs. analysis.cfg remains UNKNOWN; /analysis/cfg remains unimplemented.

    Args:
        body (ControlFlowRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        Response[ControlFlowResult | ErrorEnvelope]
    """

    kwargs = _get_kwargs(
        body=body,
    )

    response = await client.get_async_httpx_client().request(**kwargs)

    return _build_response(client=client, response=response)


async def asyncio(
    *,
    client: AuthenticatedClient | Client,
    body: ControlFlowRequest,
) -> ControlFlowResult | ErrorEnvelope | None:
    """Observe bounded loaded-DEX method control-flow facts (not a CFG)

     One original METHOD under the serialized primary-read lease, without Java generation or graph
    passes. Coordinates are method-relative DEX_CODE_UNIT_16 from LOADED_METHOD_INSNS_START, not
    original-input addresses or verified input/archive provenance (conversion plugins may supply DEX).
    Instructions include NOPs without inferring intent; payloads are separate non-executable records.
    Branch nextOffset is the sequential boundary, not an inferred feasible edge. Switch destinations are
    absolute loaded-method positions, computed relative to the switch, not its payload. Try intervals
    are half-open; ordered typed handlers followed by optional catchall are table facts, not exhaustive
    exception dispatch. Coverage is always PARTIAL. AVAILABLE includes straight-line methods with no
    branches; UNAVAILABLE means no unique loaded DEX code; FAILED means malformed traversal with all
    fact arrays empty. Precision is EXACT only for AVAILABLE loaded operands. Resolution misses remain
    200 even with strict. After readiness, validation, admission and paired revision checks, resolved
    strict requests reject with INCOMPLETE_ANALYSIS; materialization RESOURCE_LIMIT takes precedence.
    Fixed inclusive limits: 4096 code units, 1024 records including payloads, 1024 aggregate switch
    cases, 256 tries, 1024 handler entries (including catchalls, counted across ranges and encoded
    lists), 1024 UTF-16 units per handler type, and 2097152 actual UTF-8 JSON response bytes. Oversize
    refuses atomically with RESOURCE_LIMIT, never truncates. Malformed counts/extents fail atomically
    before upstream variable-size decode. Bounds do not cover upstream loading, JVM heap, deadlines or
    hostile-input isolation. Snapshot binds method, representation, observed content, session, revision,
    publication and settings; explicit save may rebind without a logical edit. Unknown/repeated fields,
    incompatible refs/options and invalid paired preconditions reject with INVALID_REQUEST. No
    overrides, cursor or jobs. analysis.cfg remains UNKNOWN; /analysis/cfg remains unimplemented.

    Args:
        body (ControlFlowRequest):

    Raises:
        errors.UnexpectedStatus: If the server returns an undocumented status code and Client.raise_on_unexpected_status is True.
        httpx.TimeoutException: If the request takes longer than Client.timeout.

    Returns:
        ControlFlowResult | ErrorEnvelope
    """

    return (
        await asyncio_detailed(
            client=client,
            body=body,
        )
    ).parsed
