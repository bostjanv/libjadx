from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar

from attrs import define as _attrs_define

if TYPE_CHECKING:
    from ..models.control_flow_case import ControlFlowCase


T = TypeVar("T", bound="ControlFlowSwitch")


@_attrs_define
class ControlFlowSwitch:
    """
    Attributes:
        offset (int):
        opcode (str):
        payload_offset (int):
        default_offset (int):
        cases (list[ControlFlowCase]):
    """

    offset: int
    opcode: str
    payload_offset: int
    default_offset: int
    cases: list[ControlFlowCase]

    def to_dict(self) -> dict[str, Any]:
        offset = self.offset

        opcode = self.opcode

        payload_offset = self.payload_offset

        default_offset = self.default_offset

        cases = []
        for cases_item_data in self.cases:
            cases_item = cases_item_data.to_dict()
            cases.append(cases_item)

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "offset": offset,
                "opcode": opcode,
                "payloadOffset": payload_offset,
                "defaultOffset": default_offset,
                "cases": cases,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.control_flow_case import ControlFlowCase  # noqa: PLC0415

        d = dict(src_dict)
        offset = d.pop("offset")

        opcode = d.pop("opcode")

        payload_offset = d.pop("payloadOffset")

        default_offset = d.pop("defaultOffset")

        cases = []
        _cases = d.pop("cases")
        for cases_item_data in _cases:
            cases_item = ControlFlowCase.from_dict(cases_item_data)

            cases.append(cases_item)

        control_flow_switch = cls(
            offset=offset,
            opcode=opcode,
            payload_offset=payload_offset,
            default_offset=default_offset,
            cases=cases,
        )

        return control_flow_switch
