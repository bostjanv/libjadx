from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define

T = TypeVar("T", bound="ControlFlowHandler")


@_attrs_define
class ControlFlowHandler:
    """
    Attributes:
        type_ (None | str): Original DEX type descriptor; null denotes catchall.
        target (int):
    """

    type_: None | str
    target: int

    def to_dict(self) -> dict[str, Any]:
        type_: None | str
        type_ = self.type_

        target = self.target

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "type": type_,
                "target": target,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)

        def _parse_type_(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        type_ = _parse_type_(d.pop("type"))

        target = d.pop("target")

        control_flow_handler = cls(
            type_=type_,
            target=target,
        )

        return control_flow_handler
