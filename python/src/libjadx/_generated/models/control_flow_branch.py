from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar

from attrs import define as _attrs_define

T = TypeVar("T", bound="ControlFlowBranch")


@_attrs_define
class ControlFlowBranch:
    """
    Attributes:
        offset (int):
        opcode (str):
        target (int):
        next_offset (int):
    """

    offset: int
    opcode: str
    target: int
    next_offset: int

    def to_dict(self) -> dict[str, Any]:
        offset = self.offset

        opcode = self.opcode

        target = self.target

        next_offset = self.next_offset

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "offset": offset,
                "opcode": opcode,
                "target": target,
                "nextOffset": next_offset,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        offset = d.pop("offset")

        opcode = d.pop("opcode")

        target = d.pop("target")

        next_offset = d.pop("nextOffset")

        control_flow_branch = cls(
            offset=offset,
            opcode=opcode,
            target=target,
            next_offset=next_offset,
        )

        return control_flow_branch
