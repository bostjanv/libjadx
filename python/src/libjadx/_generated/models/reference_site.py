from __future__ import annotations

from collections.abc import Mapping
from typing import (
    TYPE_CHECKING,
    Any,
    Literal,
    TypeVar,
    cast,
)

from attrs import define as _attrs_define

if TYPE_CHECKING:
    from ..models.source_point import SourcePoint
    from ..models.symbol_ref import SymbolRef


T = TypeVar("T", bound="ReferenceSite")


@_attrs_define
class ReferenceSite:
    """
    Attributes:
        source_owner_ref (SymbolRef): Exact original JVM/DEX identity. CLASS omits member fields or carries null; METHOD
            and FIELD require non-null values for both. Class names use Lpackage/Top$Inner; with nonempty slash-delimited
            segments, no dots or array prefix. Method descriptors contain zero or more nonvoid field types in parentheses
            and one return type, which may be V. Field descriptors contain exactly one nonvoid type. No whitespace, generic
            syntax or normalization is accepted.
        source_snapshot_id (str):
        position (SourcePoint): On CRLF, the CR boundary belongs to the preceding line and the LF boundary belongs to
            the same line; the boundary after LF starts the next line.
        precision (Literal['EXACT']):
    """

    source_owner_ref: SymbolRef
    source_snapshot_id: str
    position: SourcePoint
    precision: Literal["EXACT"]

    def to_dict(self) -> dict[str, Any]:
        source_owner_ref = self.source_owner_ref.to_dict()

        source_snapshot_id = self.source_snapshot_id

        position = self.position.to_dict()

        precision = self.precision

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "sourceOwnerRef": source_owner_ref,
                "sourceSnapshotId": source_snapshot_id,
                "position": position,
                "precision": precision,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.source_point import SourcePoint  # noqa: PLC0415
        from ..models.symbol_ref import SymbolRef  # noqa: PLC0415

        d = dict(src_dict)
        source_owner_ref = SymbolRef.from_dict(d.pop("sourceOwnerRef"))

        source_snapshot_id = d.pop("sourceSnapshotId")

        position = SourcePoint.from_dict(d.pop("position"))

        precision = cast(Literal["EXACT"], d.pop("precision"))
        if precision != "EXACT":
            raise ValueError(f"precision must match const 'EXACT', got '{precision}'")

        reference_site = cls(
            source_owner_ref=source_owner_ref,
            source_snapshot_id=source_snapshot_id,
            position=position,
            precision=precision,
        )

        return reference_site
