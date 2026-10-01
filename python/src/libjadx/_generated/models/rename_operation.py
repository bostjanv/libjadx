from __future__ import annotations

from collections.abc import Mapping
from typing import (
    TYPE_CHECKING,
    Any,
    Literal,
    TypeVar,
    cast,
)

from attrs import define as _attrs_define

from ..types import UNSET, Unset

if TYPE_CHECKING:
    from ..models.symbol_ref import SymbolRef


T = TypeVar("T", bound="RenameOperation")


@_attrs_define
class RenameOperation:
    """
    Attributes:
        kind (Literal['RENAME']):
        target (SymbolRef): Exact original JVM/DEX identity. CLASS omits member fields or carries null; METHOD and FIELD
            require non-null values for both. Class names use Lpackage/Top$Inner; with nonempty slash-delimited segments, no
            dots or array prefix. Method descriptors contain zero or more nonvoid field types in parentheses and one return
            type, which may be V. Field descriptors contain exactly one nonvoid type. No whitespace, generic syntax or
            normalization is accepted.
        new_name (str):
        propagate_related (bool | Unset):  Default: False.
    """

    kind: Literal["RENAME"]
    target: SymbolRef
    new_name: str
    propagate_related: bool | Unset = False

    def to_dict(self) -> dict[str, Any]:
        kind = self.kind

        target = self.target.to_dict()

        new_name = self.new_name

        propagate_related = self.propagate_related

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "kind": kind,
                "target": target,
                "newName": new_name,
            }
        )
        if propagate_related is not UNSET:
            field_dict["propagateRelated"] = propagate_related

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.symbol_ref import SymbolRef  # noqa: PLC0415

        d = dict(src_dict)
        kind = cast(Literal["RENAME"], d.pop("kind"))
        if kind != "RENAME":
            raise ValueError(f"kind must match const 'RENAME', got '{kind}'")

        target = SymbolRef.from_dict(d.pop("target"))

        new_name = d.pop("newName")

        propagate_related = d.pop("propagateRelated", UNSET)

        rename_operation = cls(
            kind=kind,
            target=target,
            new_name=new_name,
            propagate_related=propagate_related,
        )

        return rename_operation
