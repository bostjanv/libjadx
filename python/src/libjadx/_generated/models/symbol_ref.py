from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define

from ..models.symbol_ref_kind import SymbolRefKind
from ..types import UNSET, Unset

T = TypeVar("T", bound="SymbolRef")


@_attrs_define
class SymbolRef:
    """Exact original JVM/DEX identity. CLASS omits member fields or carries null; METHOD and FIELD require non-null values
    for both. Class names use Lpackage/Top$Inner; with nonempty slash-delimited segments, no dots or array prefix.
    Method descriptors contain zero or more nonvoid field types in parentheses and one return type, which may be V.
    Field descriptors contain exactly one nonvoid type. No whitespace, generic syntax or normalization is accepted.

        Attributes:
            kind (SymbolRefKind):
            original_class_descriptor (str):
            input_identity (None | str | Unset):
            original_name (None | str | Unset):
            original_descriptor (None | str | Unset):
    """

    kind: SymbolRefKind
    original_class_descriptor: str
    input_identity: None | str | Unset = UNSET
    original_name: None | str | Unset = UNSET
    original_descriptor: None | str | Unset = UNSET

    def to_dict(self) -> dict[str, Any]:
        kind = self.kind.value

        original_class_descriptor = self.original_class_descriptor

        input_identity: None | str | Unset
        if isinstance(self.input_identity, Unset):
            input_identity = UNSET
        else:
            input_identity = self.input_identity

        original_name: None | str | Unset
        if isinstance(self.original_name, Unset):
            original_name = UNSET
        else:
            original_name = self.original_name

        original_descriptor: None | str | Unset
        if isinstance(self.original_descriptor, Unset):
            original_descriptor = UNSET
        else:
            original_descriptor = self.original_descriptor

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "kind": kind,
                "originalClassDescriptor": original_class_descriptor,
            }
        )
        if input_identity is not UNSET:
            field_dict["inputIdentity"] = input_identity
        if original_name is not UNSET:
            field_dict["originalName"] = original_name
        if original_descriptor is not UNSET:
            field_dict["originalDescriptor"] = original_descriptor

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        kind = SymbolRefKind(d.pop("kind"))

        original_class_descriptor = d.pop("originalClassDescriptor")

        def _parse_input_identity(data: object) -> None | str | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            return cast(None | str | Unset, data)

        input_identity = _parse_input_identity(d.pop("inputIdentity", UNSET))

        def _parse_original_name(data: object) -> None | str | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            return cast(None | str | Unset, data)

        original_name = _parse_original_name(d.pop("originalName", UNSET))

        def _parse_original_descriptor(data: object) -> None | str | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            return cast(None | str | Unset, data)

        original_descriptor = _parse_original_descriptor(
            d.pop("originalDescriptor", UNSET)
        )

        symbol_ref = cls(
            kind=kind,
            original_class_descriptor=original_class_descriptor,
            input_identity=input_identity,
            original_name=original_name,
            original_descriptor=original_descriptor,
        )

        return symbol_ref
