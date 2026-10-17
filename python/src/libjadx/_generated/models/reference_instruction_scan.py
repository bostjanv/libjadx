from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define

from ..models.reference_instruction_scan_availability import (
    ReferenceInstructionScanAvailability,
)

T = TypeVar("T", bound="ReferenceInstructionScan")


@_attrs_define
class ReferenceInstructionScan:
    """AVAILABLE means traversal finished, not exhaustive reference coverage. FAILED may retain observations before the
    failure; never interpret its empty edges as a successful empty scan. strict rejects all resolved instruction queries
    because coverage remains PARTIAL, including failed scans, excluded forms and unavailable readers.

        Attributes:
            availability (ReferenceInstructionScanAvailability):
            reason (str):
            excluded_opcodes (list[str]):
    """

    availability: ReferenceInstructionScanAvailability
    reason: str
    excluded_opcodes: list[str]

    def to_dict(self) -> dict[str, Any]:
        availability = self.availability.value

        reason = self.reason

        excluded_opcodes = self.excluded_opcodes

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "availability": availability,
                "reason": reason,
                "excludedOpcodes": excluded_opcodes,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        availability = ReferenceInstructionScanAvailability(d.pop("availability"))

        reason = d.pop("reason")

        excluded_opcodes = cast(list[str], d.pop("excludedOpcodes"))

        reference_instruction_scan = cls(
            availability=availability,
            reason=reason,
            excluded_opcodes=excluded_opcodes,
        )

        return reference_instruction_scan
