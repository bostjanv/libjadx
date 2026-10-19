from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar

from attrs import define as _attrs_define

T = TypeVar("T", bound="ControlFlowCase")


@_attrs_define
class ControlFlowCase:
    """
    Attributes:
        key (int):
        target (int):
    """

    key: int
    target: int

    def to_dict(self) -> dict[str, Any]:
        key = self.key

        target = self.target

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "key": key,
                "target": target,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        key = d.pop("key")

        target = d.pop("target")

        control_flow_case = cls(
            key=key,
            target=target,
        )

        return control_flow_case
