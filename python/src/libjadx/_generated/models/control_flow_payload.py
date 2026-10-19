from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar

from attrs import define as _attrs_define

from ..models.control_flow_payload_kind import ControlFlowPayloadKind

T = TypeVar("T", bound="ControlFlowPayload")


@_attrs_define
class ControlFlowPayload:
    """
    Attributes:
        offset (int):
        length (int):
        kind (ControlFlowPayloadKind):
    """

    offset: int
    length: int
    kind: ControlFlowPayloadKind

    def to_dict(self) -> dict[str, Any]:
        offset = self.offset

        length = self.length

        kind = self.kind.value

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "offset": offset,
                "length": length,
                "kind": kind,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        offset = d.pop("offset")

        length = d.pop("length")

        kind = ControlFlowPayloadKind(d.pop("kind"))

        control_flow_payload = cls(
            offset=offset,
            length=length,
            kind=kind,
        )

        return control_flow_payload
