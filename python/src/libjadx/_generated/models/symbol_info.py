from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar, cast

from attrs import define as _attrs_define

from ..models.symbol_info_provenance import SymbolInfoProvenance
from ..types import UNSET, Unset

if TYPE_CHECKING:
    from ..models.symbol_ref import SymbolRef


T = TypeVar("T", bound="SymbolInfo")


@_attrs_define
class SymbolInfo:
    """
    Attributes:
        ref (SymbolRef): Exact original JVM/DEX identity. CLASS omits member fields or carries null; METHOD and FIELD
            require non-null values for both. Class names use Lpackage/Top$Inner; with nonempty slash-delimited segments, no
            dots or array prefix. Method descriptors contain zero or more nonvoid field types in parentheses and one return
            type, which may be V. Field descriptors contain exactly one nonvoid type. No whitespace, generic syntax or
            normalization is accepted.
        original_name (None | str):
        original_descriptor (None | str):
        display_name (str):
        display_qualified_name (str):
        provenance (SymbolInfoProvenance):
        containing_class (None | SymbolRef | Unset):
    """

    ref: SymbolRef
    original_name: None | str
    original_descriptor: None | str
    display_name: str
    display_qualified_name: str
    provenance: SymbolInfoProvenance
    containing_class: None | SymbolRef | Unset = UNSET

    def to_dict(self) -> dict[str, Any]:
        from ..models.symbol_ref import SymbolRef  # noqa: PLC0415

        ref = self.ref.to_dict()

        original_name: None | str
        original_name = self.original_name

        original_descriptor: None | str
        original_descriptor = self.original_descriptor

        display_name = self.display_name

        display_qualified_name = self.display_qualified_name

        provenance = self.provenance.value

        containing_class: dict[str, Any] | None | Unset
        if isinstance(self.containing_class, Unset):
            containing_class = UNSET
        elif isinstance(self.containing_class, SymbolRef):
            containing_class = self.containing_class.to_dict()
        else:
            containing_class = self.containing_class

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "ref": ref,
                "originalName": original_name,
                "originalDescriptor": original_descriptor,
                "displayName": display_name,
                "displayQualifiedName": display_qualified_name,
                "provenance": provenance,
            }
        )
        if containing_class is not UNSET:
            field_dict["containingClass"] = containing_class

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.symbol_ref import SymbolRef  # noqa: PLC0415

        d = dict(src_dict)
        ref = SymbolRef.from_dict(d.pop("ref"))

        def _parse_original_name(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        original_name = _parse_original_name(d.pop("originalName"))

        def _parse_original_descriptor(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        original_descriptor = _parse_original_descriptor(d.pop("originalDescriptor"))

        display_name = d.pop("displayName")

        display_qualified_name = d.pop("displayQualifiedName")

        provenance = SymbolInfoProvenance(d.pop("provenance"))

        def _parse_containing_class(data: object) -> None | SymbolRef | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            try:
                if not isinstance(data, dict):
                    raise TypeError()
                containing_class_type_0 = SymbolRef.from_dict(data)

                return containing_class_type_0
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            return cast(None | SymbolRef | Unset, data)

        containing_class = _parse_containing_class(d.pop("containingClass", UNSET))

        symbol_info = cls(
            ref=ref,
            original_name=original_name,
            original_descriptor=original_descriptor,
            display_name=display_name,
            display_qualified_name=display_qualified_name,
            provenance=provenance,
            containing_class=containing_class,
        )

        return symbol_info
