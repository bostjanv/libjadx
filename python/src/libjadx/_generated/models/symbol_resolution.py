from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar, cast
from uuid import UUID

from attrs import define as _attrs_define

from ..models.symbol_resolution_outcome import SymbolResolutionOutcome
from ..types import UNSET, Unset

if TYPE_CHECKING:
    from ..models.symbol_info import SymbolInfo
    from ..models.symbol_ref import SymbolRef


T = TypeVar("T", bound="SymbolResolution")


@_attrs_define
class SymbolResolution:
    """
    Attributes:
        outcome (SymbolResolutionOutcome):
        queried_ref (SymbolRef): Exact original JVM/DEX identity. CLASS omits member fields or carries null; METHOD and
            FIELD require non-null values for both. Class names use Lpackage/Top$Inner; with nonempty slash-delimited
            segments, no dots or array prefix. Method descriptors contain zero or more nonvoid field types in parentheses
            and one return type, which may be V. Field descriptors contain exactly one nonvoid type. No whitespace, generic
            syntax or normalization is accepted.
        session_id (UUID):
        logical_revision (int):
        snapshot_id (str):
        candidates (list[SymbolInfo]):
        diagnostics (list[str]):
        symbol (None | SymbolInfo | Unset):
    """

    outcome: SymbolResolutionOutcome
    queried_ref: SymbolRef
    session_id: UUID
    logical_revision: int
    snapshot_id: str
    candidates: list[SymbolInfo]
    diagnostics: list[str]
    symbol: None | SymbolInfo | Unset = UNSET

    def to_dict(self) -> dict[str, Any]:
        from ..models.symbol_info import SymbolInfo  # noqa: PLC0415

        outcome = self.outcome.value

        queried_ref = self.queried_ref.to_dict()

        session_id = str(self.session_id)

        logical_revision = self.logical_revision

        snapshot_id = self.snapshot_id

        candidates = []
        for candidates_item_data in self.candidates:
            candidates_item = candidates_item_data.to_dict()
            candidates.append(candidates_item)

        diagnostics = self.diagnostics

        symbol: dict[str, Any] | None | Unset
        if isinstance(self.symbol, Unset):
            symbol = UNSET
        elif isinstance(self.symbol, SymbolInfo):
            symbol = self.symbol.to_dict()
        else:
            symbol = self.symbol

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "outcome": outcome,
                "queriedRef": queried_ref,
                "sessionId": session_id,
                "logicalRevision": logical_revision,
                "snapshotId": snapshot_id,
                "candidates": candidates,
                "diagnostics": diagnostics,
            }
        )
        if symbol is not UNSET:
            field_dict["symbol"] = symbol

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.symbol_info import SymbolInfo  # noqa: PLC0415
        from ..models.symbol_ref import SymbolRef  # noqa: PLC0415

        d = dict(src_dict)
        outcome = SymbolResolutionOutcome(d.pop("outcome"))

        queried_ref = SymbolRef.from_dict(d.pop("queriedRef"))

        session_id = UUID(d.pop("sessionId"))

        logical_revision = d.pop("logicalRevision")

        snapshot_id = d.pop("snapshotId")

        candidates = []
        _candidates = d.pop("candidates")
        for candidates_item_data in _candidates:
            candidates_item = SymbolInfo.from_dict(candidates_item_data)

            candidates.append(candidates_item)

        diagnostics = cast(list[str], d.pop("diagnostics"))

        def _parse_symbol(data: object) -> None | SymbolInfo | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            try:
                if not isinstance(data, dict):
                    raise TypeError()
                symbol_type_0 = SymbolInfo.from_dict(data)

                return symbol_type_0
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            return cast(None | SymbolInfo | Unset, data)

        symbol = _parse_symbol(d.pop("symbol", UNSET))

        symbol_resolution = cls(
            outcome=outcome,
            queried_ref=queried_ref,
            session_id=session_id,
            logical_revision=logical_revision,
            snapshot_id=snapshot_id,
            candidates=candidates,
            diagnostics=diagnostics,
            symbol=symbol,
        )

        return symbol_resolution
