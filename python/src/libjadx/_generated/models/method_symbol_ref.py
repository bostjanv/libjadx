from __future__ import annotations

from collections.abc import Mapping
from typing import (
    Any,
    Literal,
    TypeVar,
    cast,
)

from attrs import define as _attrs_define

from ..types import UNSET, Unset

T = TypeVar("T", bound="MethodSymbolRef")


@_attrs_define
class MethodSymbolRef:
    """
    Attributes:
        kind (Literal['METHOD']):
        original_class_descriptor (str):
        original_name (str):
        original_descriptor (str):
        input_identity (None | str | Unset):
    """

    kind: Literal["METHOD"]
    original_class_descriptor: str
    original_name: str
    original_descriptor: str
    input_identity: None | str | Unset = UNSET

    def to_dict(self) -> dict[str, Any]:
        kind = self.kind

        original_class_descriptor = self.original_class_descriptor

        original_name = self.original_name

        original_descriptor = self.original_descriptor

        input_identity: None | str | Unset
        if isinstance(self.input_identity, Unset):
            input_identity = UNSET
        else:
            input_identity = self.input_identity

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "kind": kind,
                "originalClassDescriptor": original_class_descriptor,
                "originalName": original_name,
                "originalDescriptor": original_descriptor,
            }
        )
        if input_identity is not UNSET:
            field_dict["inputIdentity"] = input_identity

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        kind = cast(Literal["METHOD"], d.pop("kind"))
        if kind != "METHOD":
            raise ValueError(f"kind must match const 'METHOD', got '{kind}'")

        original_class_descriptor = d.pop("originalClassDescriptor")

        original_name = d.pop("originalName")

        original_descriptor = d.pop("originalDescriptor")

        def _parse_input_identity(data: object) -> None | str | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            return cast(None | str | Unset, data)

        input_identity = _parse_input_identity(d.pop("inputIdentity", UNSET))

        method_symbol_ref = cls(
            kind=kind,
            original_class_descriptor=original_class_descriptor,
            original_name=original_name,
            original_descriptor=original_descriptor,
            input_identity=input_identity,
        )

        return method_symbol_ref
