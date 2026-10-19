from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar

from attrs import define as _attrs_define

T = TypeVar("T", bound="ControlFlowInstruction")


@_attrs_define
class ControlFlowInstruction:
    """
    Attributes:
        offset (int):
        length (int):
        opcode (str):
    """

    offset: int
    length: int
    opcode: str

    def to_dict(self) -> dict[str, Any]:
        offset = self.offset

        length = self.length

        opcode = self.opcode

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "offset": offset,
                "length": length,
                "opcode": opcode,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        offset = d.pop("offset")

        length = d.pop("length")

        opcode = d.pop("opcode")

        control_flow_instruction = cls(
            offset=offset,
            length=length,
            opcode=opcode,
        )

        return control_flow_instruction
