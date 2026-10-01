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

if TYPE_CHECKING:
    from ..models.symbol_ref import SymbolRef


T = TypeVar("T", bound="SetCommentOperation")


@_attrs_define
class SetCommentOperation:
    """
    Attributes:
        kind (Literal['SET_COMMENT']):
        target (SymbolRef): Exact original JVM/DEX identity. CLASS omits member fields or carries null; METHOD and FIELD
            require non-null values for both. Class names use Lpackage/Top$Inner; with nonempty slash-delimited segments, no
            dots or array prefix. Method descriptors contain zero or more nonvoid field types in parentheses and one return
            type, which may be V. Field descriptors contain exactly one nonvoid type. No whitespace, generic syntax or
            normalization is accepted.
        comment (str):
        style (Literal['LINE']):
    """

    kind: Literal["SET_COMMENT"]
    target: SymbolRef
    comment: str
    style: Literal["LINE"]

    def to_dict(self) -> dict[str, Any]:
        kind = self.kind

        target = self.target.to_dict()

        comment = self.comment

        style = self.style

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "kind": kind,
                "target": target,
                "comment": comment,
                "style": style,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.symbol_ref import SymbolRef  # noqa: PLC0415

        d = dict(src_dict)
        kind = cast(Literal["SET_COMMENT"], d.pop("kind"))
        if kind != "SET_COMMENT":
            raise ValueError(f"kind must match const 'SET_COMMENT', got '{kind}'")

        target = SymbolRef.from_dict(d.pop("target"))

        comment = d.pop("comment")

        style = cast(Literal["LINE"], d.pop("style"))
        if style != "LINE":
            raise ValueError(f"style must match const 'LINE', got '{style}'")

        set_comment_operation = cls(
            kind=kind,
            target=target,
            comment=comment,
            style=style,
        )

        return set_comment_operation
