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

from ..models.source_annotation_kind import SourceAnnotationKind

if TYPE_CHECKING:
    from ..models.source_point import SourcePoint
    from ..models.symbol_ref import SymbolRef


T = TypeVar("T", bound="SourceAnnotation")


@_attrs_define
class SourceAnnotation:
    """Validated token-start location, never a complete declaration range.

    Attributes:
        kind (SourceAnnotationKind):
        position (SourcePoint): On CRLF, the CR boundary belongs to the preceding line and the LF boundary belongs to
            the same line; the boundary after LF starts the next line.
        target_ref (SymbolRef): Exact original JVM/DEX identity. CLASS omits member fields or carries null; METHOD and
            FIELD require non-null values for both. Class names use Lpackage/Top$Inner; with nonempty slash-delimited
            segments, no dots or array prefix. Method descriptors contain zero or more nonvoid field types in parentheses
            and one return type, which may be V. Field descriptors contain exactly one nonvoid type. No whitespace, generic
            syntax or normalization is accepted.
        precision (Literal['EXACT']):
        source_snapshot_id (str):
    """

    kind: SourceAnnotationKind
    position: SourcePoint
    target_ref: SymbolRef
    precision: Literal["EXACT"]
    source_snapshot_id: str

    def to_dict(self) -> dict[str, Any]:
        kind = self.kind.value

        position = self.position.to_dict()

        target_ref = self.target_ref.to_dict()

        precision = self.precision

        source_snapshot_id = self.source_snapshot_id

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "kind": kind,
                "position": position,
                "targetRef": target_ref,
                "precision": precision,
                "sourceSnapshotId": source_snapshot_id,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.source_point import SourcePoint  # noqa: PLC0415
        from ..models.symbol_ref import SymbolRef  # noqa: PLC0415

        d = dict(src_dict)
        kind = SourceAnnotationKind(d.pop("kind"))

        position = SourcePoint.from_dict(d.pop("position"))

        target_ref = SymbolRef.from_dict(d.pop("targetRef"))

        precision = cast(Literal["EXACT"], d.pop("precision"))
        if precision != "EXACT":
            raise ValueError(f"precision must match const 'EXACT', got '{precision}'")

        source_snapshot_id = d.pop("sourceSnapshotId")

        source_annotation = cls(
            kind=kind,
            position=position,
            target_ref=target_ref,
            precision=precision,
            source_snapshot_id=source_snapshot_id,
        )

        return source_annotation
