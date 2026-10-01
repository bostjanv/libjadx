from __future__ import annotations

from collections.abc import Mapping
from typing import (
    Any,
    Literal,
    TypeVar,
    cast,
)

from attrs import define as _attrs_define

T = TypeVar("T", bound="ReferencePageCoverage")


@_attrs_define
class ReferencePageCoverage:
    """
    Attributes:
        status (Literal['PARTIAL']):
        reason (str):
    """

    status: Literal["PARTIAL"]
    reason: str

    def to_dict(self) -> dict[str, Any]:
        status = self.status

        reason = self.reason

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "status": status,
                "reason": reason,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        status = cast(Literal["PARTIAL"], d.pop("status"))
        if status != "PARTIAL":
            raise ValueError(f"status must match const 'PARTIAL', got '{status}'")

        reason = d.pop("reason")

        reference_page_coverage = cls(
            status=status,
            reason=reason,
        )

        return reference_page_coverage
