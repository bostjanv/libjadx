from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar
from uuid import UUID

from attrs import define as _attrs_define

from ..types import UNSET, Unset

if TYPE_CHECKING:
    from ..models.method_symbol_ref import MethodSymbolRef


T = TypeVar("T", bound="ControlFlowRequest")


@_attrs_define
class ControlFlowRequest:
    """
    Attributes:
        ref (MethodSymbolRef):
        strict (bool | Unset):  Default: False.
        expected_session_id (UUID | Unset):
        expected_logical_revision (int | Unset):
    """

    ref: MethodSymbolRef
    strict: bool | Unset = False
    expected_session_id: UUID | Unset = UNSET
    expected_logical_revision: int | Unset = UNSET

    def to_dict(self) -> dict[str, Any]:
        ref = self.ref.to_dict()

        strict = self.strict

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
        if strict is not UNSET:
            field_dict["strict"] = strict
        if expected_session_id is not UNSET:
            field_dict["expectedSessionId"] = expected_session_id
        if expected_logical_revision is not UNSET:
            field_dict["expectedLogicalRevision"] = expected_logical_revision

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.method_symbol_ref import MethodSymbolRef  # noqa: PLC0415

        d = dict(src_dict)
        ref = MethodSymbolRef.from_dict(d.pop("ref"))

        strict = d.pop("strict", UNSET)

        _expected_session_id = d.pop("expectedSessionId", UNSET)
        expected_session_id: UUID | Unset
        if isinstance(_expected_session_id, Unset):
            expected_session_id = UNSET
        else:
            expected_session_id = UUID(_expected_session_id)

        expected_logical_revision = d.pop("expectedLogicalRevision", UNSET)

        control_flow_request = cls(
            ref=ref,
            strict=strict,
            expected_session_id=expected_session_id,
            expected_logical_revision=expected_logical_revision,
        )

        return control_flow_request
