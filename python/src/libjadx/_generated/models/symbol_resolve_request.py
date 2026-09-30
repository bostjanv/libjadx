from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar
from uuid import UUID

from attrs import define as _attrs_define

from ..types import UNSET, Unset

if TYPE_CHECKING:
    from ..models.symbol_ref import SymbolRef


T = TypeVar("T", bound="SymbolResolveRequest")


@_attrs_define
class SymbolResolveRequest:
    """expectedSessionId and expectedLogicalRevision must occur together.

    Attributes:
        ref (SymbolRef): Exact original JVM/DEX identity. CLASS omits member fields or carries null; METHOD and FIELD
            require non-null values for both. Class names use Lpackage/Top$Inner; with nonempty slash-delimited segments, no
            dots or array prefix. Method descriptors contain zero or more nonvoid field types in parentheses and one return
            type, which may be V. Field descriptors contain exactly one nonvoid type. No whitespace, generic syntax or
            normalization is accepted.
        expected_session_id (UUID | Unset):
        expected_logical_revision (int | Unset):
    """

    ref: SymbolRef
    expected_session_id: UUID | Unset = UNSET
    expected_logical_revision: int | Unset = UNSET

    def to_dict(self) -> dict[str, Any]:
        ref = self.ref.to_dict()

        expected_session_id: str | Unset = UNSET
        if not isinstance(self.expected_session_id, Unset):
            expected_session_id = str(self.expected_session_id)

        expected_logical_revision = self.expected_logical_revision

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "ref": ref,
            }
        )
        if expected_session_id is not UNSET:
            field_dict["expectedSessionId"] = expected_session_id
        if expected_logical_revision is not UNSET:
            field_dict["expectedLogicalRevision"] = expected_logical_revision

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.symbol_ref import SymbolRef  # noqa: PLC0415

        d = dict(src_dict)
        ref = SymbolRef.from_dict(d.pop("ref"))

        _expected_session_id = d.pop("expectedSessionId", UNSET)
        expected_session_id: UUID | Unset
        if isinstance(_expected_session_id, Unset):
            expected_session_id = UNSET
        else:
            expected_session_id = UUID(_expected_session_id)

        expected_logical_revision = d.pop("expectedLogicalRevision", UNSET)

        symbol_resolve_request = cls(
            ref=ref,
            expected_session_id=expected_session_id,
            expected_logical_revision=expected_logical_revision,
        )

        return symbol_resolve_request
