from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar

from attrs import define as _attrs_define

if TYPE_CHECKING:
    from ..models.control_flow_handler import ControlFlowHandler


T = TypeVar("T", bound="ControlFlowTry")


@_attrs_define
class ControlFlowTry:
    """
    Attributes:
        start (int):
        end_exclusive (int):
        handlers (list[ControlFlowHandler]):
    """

    start: int
    end_exclusive: int
    handlers: list[ControlFlowHandler]

    def to_dict(self) -> dict[str, Any]:
        start = self.start

        end_exclusive = self.end_exclusive

        handlers = []
        for handlers_item_data in self.handlers:
            handlers_item = handlers_item_data.to_dict()
            handlers.append(handlers_item)

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "start": start,
                "endExclusive": end_exclusive,
                "handlers": handlers,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.control_flow_handler import ControlFlowHandler  # noqa: PLC0415

        d = dict(src_dict)
        start = d.pop("start")

        end_exclusive = d.pop("endExclusive")

        handlers = []
        _handlers = d.pop("handlers")
        for handlers_item_data in _handlers:
            handlers_item = ControlFlowHandler.from_dict(handlers_item_data)

            handlers.append(handlers_item)

        control_flow_try = cls(
            start=start,
            end_exclusive=end_exclusive,
            handlers=handlers,
        )

        return control_flow_try
