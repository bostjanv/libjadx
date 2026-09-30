from __future__ import annotations

from collections.abc import Mapping
from typing import (
    Any,
    Literal,
    TypeVar,
    cast,
)

from attrs import define as _attrs_define

from ..models.source_availability import SourceAvailability

T = TypeVar("T", bound="ReferenceCapabilities")


@_attrs_define
class ReferenceCapabilities:
    """
    Attributes:
        resolved_target (SourceAvailability):
        source_location (SourceAvailability):
        original_offset (Literal['UNAVAILABLE']):
        reference_kind (Literal['PARTIAL']):
        global_coverage (Literal['UNKNOWN']):
    """

    resolved_target: SourceAvailability
    source_location: SourceAvailability
    original_offset: Literal["UNAVAILABLE"]
    reference_kind: Literal["PARTIAL"]
    global_coverage: Literal["UNKNOWN"]

    def to_dict(self) -> dict[str, Any]:
        resolved_target = self.resolved_target.value

        source_location = self.source_location.value

        original_offset = self.original_offset

        reference_kind = self.reference_kind

        global_coverage = self.global_coverage

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "resolvedTarget": resolved_target,
                "sourceLocation": source_location,
                "originalOffset": original_offset,
                "referenceKind": reference_kind,
                "globalCoverage": global_coverage,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        resolved_target = SourceAvailability(d.pop("resolvedTarget"))

        source_location = SourceAvailability(d.pop("sourceLocation"))

        original_offset = cast(Literal["UNAVAILABLE"], d.pop("originalOffset"))
        if original_offset != "UNAVAILABLE":
            raise ValueError(
                f"originalOffset must match const 'UNAVAILABLE', got '{original_offset}'"
            )

        reference_kind = cast(Literal["PARTIAL"], d.pop("referenceKind"))
        if reference_kind != "PARTIAL":
            raise ValueError(
                f"referenceKind must match const 'PARTIAL', got '{reference_kind}'"
            )

        global_coverage = cast(Literal["UNKNOWN"], d.pop("globalCoverage"))
        if global_coverage != "UNKNOWN":
            raise ValueError(
                f"globalCoverage must match const 'UNKNOWN', got '{global_coverage}'"
            )

        reference_capabilities = cls(
            resolved_target=resolved_target,
            source_location=source_location,
            original_offset=original_offset,
            reference_kind=reference_kind,
            global_coverage=global_coverage,
        )

        return reference_capabilities
