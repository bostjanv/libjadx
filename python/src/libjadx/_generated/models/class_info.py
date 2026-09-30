from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar, cast

from attrs import define as _attrs_define

from ..models.class_info_provenance import ClassInfoProvenance
from ..types import UNSET, Unset

if TYPE_CHECKING:
    from ..models.symbol_ref import SymbolRef


T = TypeVar("T", bound="ClassInfo")


@_attrs_define
class ClassInfo:
    """
    Attributes:
        ref (SymbolRef): Exact original JVM/DEX identity. CLASS omits member fields or carries null; METHOD and FIELD
            require non-null values for both. Class names use Lpackage/Top$Inner; with nonempty slash-delimited segments, no
            dots or array prefix. Method descriptors contain zero or more nonvoid field types in parentheses and one return
            type, which may be V. Field descriptors contain exactly one nonvoid type. No whitespace, generic syntax or
            normalization is accepted.
        original_dotted_name (str):
        display_name (str):
        display_qualified_name (str):
        is_inner (bool):
        code_available (bool): False when Jadx marks the class DONT_GENERATE, including inlined anonymous classes.
        provenance (ClassInfoProvenance):
        original_parent (None | SymbolRef | Unset):
    """

    ref: SymbolRef
    original_dotted_name: str
    display_name: str
    display_qualified_name: str
    is_inner: bool
    code_available: bool
    provenance: ClassInfoProvenance
    original_parent: None | SymbolRef | Unset = UNSET

    def to_dict(self) -> dict[str, Any]:
        from ..models.symbol_ref import SymbolRef  # noqa: PLC0415

        ref = self.ref.to_dict()

        original_dotted_name = self.original_dotted_name

        display_name = self.display_name

        display_qualified_name = self.display_qualified_name

        is_inner = self.is_inner

        code_available = self.code_available

        provenance = self.provenance.value

        original_parent: dict[str, Any] | None | Unset
        if isinstance(self.original_parent, Unset):
            original_parent = UNSET
        elif isinstance(self.original_parent, SymbolRef):
            original_parent = self.original_parent.to_dict()
        else:
            original_parent = self.original_parent

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "ref": ref,
                "originalDottedName": original_dotted_name,
                "displayName": display_name,
                "displayQualifiedName": display_qualified_name,
                "isInner": is_inner,
                "codeAvailable": code_available,
                "provenance": provenance,
            }
        )
        if original_parent is not UNSET:
            field_dict["originalParent"] = original_parent

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.symbol_ref import SymbolRef  # noqa: PLC0415

        d = dict(src_dict)
        ref = SymbolRef.from_dict(d.pop("ref"))

        original_dotted_name = d.pop("originalDottedName")

        display_name = d.pop("displayName")

        display_qualified_name = d.pop("displayQualifiedName")

        is_inner = d.pop("isInner")

        code_available = d.pop("codeAvailable")

        provenance = ClassInfoProvenance(d.pop("provenance"))

        def _parse_original_parent(data: object) -> None | SymbolRef | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            try:
                if not isinstance(data, dict):
                    raise TypeError()
                original_parent_type_0 = SymbolRef.from_dict(data)

                return original_parent_type_0
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            return cast(None | SymbolRef | Unset, data)

        original_parent = _parse_original_parent(d.pop("originalParent", UNSET))

        class_info = cls(
            ref=ref,
            original_dotted_name=original_dotted_name,
            display_name=display_name,
            display_qualified_name=display_qualified_name,
            is_inner=is_inner,
            code_available=code_available,
            provenance=provenance,
            original_parent=original_parent,
        )

        return class_info
