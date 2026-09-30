from __future__ import annotations

from collections.abc import Mapping
from typing import (
    TYPE_CHECKING,
    Any,
    Literal,
    TypeVar,
    cast,
)
from uuid import UUID

from attrs import define as _attrs_define

from ..models.reference_page_direction import ReferencePageDirection
from ..models.reference_page_outcome import ReferencePageOutcome

if TYPE_CHECKING:
    from ..models.reference_capabilities import ReferenceCapabilities
    from ..models.reference_edge import ReferenceEdge
    from ..models.reference_page_coverage import ReferencePageCoverage
    from ..models.symbol_info import SymbolInfo
    from ..models.symbol_ref import SymbolRef


T = TypeVar("T", bound="ReferencePage")


@_attrs_define
class ReferencePage:
    """
    Attributes:
        outcome (ReferencePageOutcome):
        queried_ref (SymbolRef): Exact original JVM/DEX identity. CLASS omits member fields or carries null; METHOD and
            FIELD require non-null values for both. Class names use Lpackage/Top$Inner; with nonempty slash-delimited
            segments, no dots or array prefix. Method descriptors contain zero or more nonvoid field types in parentheses
            and one return type, which may be V. Field descriptors contain exactly one nonvoid type. No whitespace, generic
            syntax or normalization is accepted.
        direction (ReferencePageDirection):
        candidates (list[SymbolInfo]):
        session_id (UUID):
        logical_revision (int):
        snapshot_id (None | str):
        scope (Literal['JADX_REPORTED']):
        coverage (ReferencePageCoverage):
        edges (list[ReferenceEdge]):
        next_cursor (None | str):
        page_complete (bool):
        capabilities (ReferenceCapabilities):
        diagnostics (list[str]):
    """

    outcome: ReferencePageOutcome
    queried_ref: SymbolRef
    direction: ReferencePageDirection
    candidates: list[SymbolInfo]
    session_id: UUID
    logical_revision: int
    snapshot_id: None | str
    scope: Literal["JADX_REPORTED"]
    coverage: ReferencePageCoverage
    edges: list[ReferenceEdge]
    next_cursor: None | str
    page_complete: bool
    capabilities: ReferenceCapabilities
    diagnostics: list[str]

    def to_dict(self) -> dict[str, Any]:
        outcome = self.outcome.value

        queried_ref = self.queried_ref.to_dict()

        direction = self.direction.value

        candidates = []
        for candidates_item_data in self.candidates:
            candidates_item = candidates_item_data.to_dict()
            candidates.append(candidates_item)

        session_id = str(self.session_id)

        logical_revision = self.logical_revision

        snapshot_id: None | str
        snapshot_id = self.snapshot_id

        scope = self.scope

        coverage = self.coverage.to_dict()

        edges = []
        for edges_item_data in self.edges:
            edges_item = edges_item_data.to_dict()
            edges.append(edges_item)

        next_cursor: None | str
        next_cursor = self.next_cursor

        page_complete = self.page_complete

        capabilities = self.capabilities.to_dict()

        diagnostics = self.diagnostics

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "outcome": outcome,
                "queriedRef": queried_ref,
                "direction": direction,
                "candidates": candidates,
                "sessionId": session_id,
                "logicalRevision": logical_revision,
                "snapshotId": snapshot_id,
                "scope": scope,
                "coverage": coverage,
                "edges": edges,
                "nextCursor": next_cursor,
                "pageComplete": page_complete,
                "capabilities": capabilities,
                "diagnostics": diagnostics,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.reference_capabilities import (
            ReferenceCapabilities,  # noqa: PLC0415
        )
        from ..models.reference_edge import ReferenceEdge  # noqa: PLC0415
        from ..models.reference_page_coverage import (
            ReferencePageCoverage,  # noqa: PLC0415
        )
        from ..models.symbol_info import SymbolInfo  # noqa: PLC0415
        from ..models.symbol_ref import SymbolRef  # noqa: PLC0415

        d = dict(src_dict)
        outcome = ReferencePageOutcome(d.pop("outcome"))

        queried_ref = SymbolRef.from_dict(d.pop("queriedRef"))

        direction = ReferencePageDirection(d.pop("direction"))

        candidates = []
        _candidates = d.pop("candidates")
        for candidates_item_data in _candidates:
            candidates_item = SymbolInfo.from_dict(candidates_item_data)

            candidates.append(candidates_item)

        session_id = UUID(d.pop("sessionId"))

        logical_revision = d.pop("logicalRevision")

        def _parse_snapshot_id(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        snapshot_id = _parse_snapshot_id(d.pop("snapshotId"))

        scope = cast(Literal["JADX_REPORTED"], d.pop("scope"))
        if scope != "JADX_REPORTED":
            raise ValueError(f"scope must match const 'JADX_REPORTED', got '{scope}'")

        coverage = ReferencePageCoverage.from_dict(d.pop("coverage"))

        edges = []
        _edges = d.pop("edges")
        for edges_item_data in _edges:
            edges_item = ReferenceEdge.from_dict(edges_item_data)

            edges.append(edges_item)

        def _parse_next_cursor(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        next_cursor = _parse_next_cursor(d.pop("nextCursor"))

        page_complete = d.pop("pageComplete")

        capabilities = ReferenceCapabilities.from_dict(d.pop("capabilities"))

        diagnostics = cast(list[str], d.pop("diagnostics"))

        reference_page = cls(
            outcome=outcome,
            queried_ref=queried_ref,
            direction=direction,
            candidates=candidates,
            session_id=session_id,
            logical_revision=logical_revision,
            snapshot_id=snapshot_id,
            scope=scope,
            coverage=coverage,
            edges=edges,
            next_cursor=next_cursor,
            page_complete=page_complete,
            capabilities=capabilities,
            diagnostics=diagnostics,
        )

        return reference_page
