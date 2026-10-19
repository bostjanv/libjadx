from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar

from attrs import define as _attrs_define

T = TypeVar("T", bound="ControlFlowArrayData")


@_attrs_define
class ControlFlowArrayData:
    """
    Attributes:
        offset (int):
        payload_offset (int):
    """

    offset: int
    payload_offset: int

    def to_dict(self) -> dict[str, Any]:
        offset = self.offset

        payload_offset = self.payload_offset

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "offset": offset,
                "payloadOffset": payload_offset,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        offset = d.pop("offset")

        payload_offset = d.pop("payloadOffset")

        control_flow_array_data = cls(
            offset=offset,
            payload_offset=payload_offset,
        )

        return control_flow_array_data
