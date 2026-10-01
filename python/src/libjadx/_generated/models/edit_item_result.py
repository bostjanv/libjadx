from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar, cast

from attrs import define as _attrs_define

from ..models.edit_item_result_kind import EditItemResultKind
from ..models.edit_item_result_status import EditItemResultStatus
from ..types import UNSET, Unset

if TYPE_CHECKING:
    from ..models.symbol_ref import SymbolRef


T = TypeVar("T", bound="EditItemResult")


@_attrs_define
class EditItemResult:
    """
    Attributes:
        index (int):
        status (EditItemResultStatus):
        kind (EditItemResultKind):
        target (SymbolRef): Exact original JVM/DEX identity. CLASS omits member fields or carries null; METHOD and FIELD
            require non-null values for both. Class names use Lpackage/Top$Inner; with nonempty slash-delimited segments, no
            dots or array prefix. Method descriptors contain zero or more nonvoid field types in parentheses and one return
            type, which may be V. Field descriptors contain exactly one nonvoid type. No whitespace, generic syntax or
            normalization is accepted.
        affected_refs (list[SymbolRef]): Exact sorted independently verified method family for propagated APPLIED or
            verified NO_CHANGE; empty otherwise.
        message (None | str):
        parameter_index (int | None | Unset):
        source_snapshot_id (None | str | Unset):
    """

    index: int
    status: EditItemResultStatus
    kind: EditItemResultKind
    target: SymbolRef
    affected_refs: list[SymbolRef]
    message: None | str
    parameter_index: int | None | Unset = UNSET
    source_snapshot_id: None | str | Unset = UNSET

    def to_dict(self) -> dict[str, Any]:
        index = self.index

        status = self.status.value

        kind = self.kind.value

        target = self.target.to_dict()

        affected_refs = []
        for affected_refs_item_data in self.affected_refs:
            affected_refs_item = affected_refs_item_data.to_dict()
            affected_refs.append(affected_refs_item)

        message: None | str
        message = self.message

        parameter_index: int | None | Unset
        if isinstance(self.parameter_index, Unset):
            parameter_index = UNSET
        else:
            parameter_index = self.parameter_index

        source_snapshot_id: None | str | Unset
        if isinstance(self.source_snapshot_id, Unset):
            source_snapshot_id = UNSET
        else:
            source_snapshot_id = self.source_snapshot_id

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "index": index,
                "status": status,
                "kind": kind,
                "target": target,
                "affectedRefs": affected_refs,
                "message": message,
            }
        )
        if parameter_index is not UNSET:
            field_dict["parameterIndex"] = parameter_index
        if source_snapshot_id is not UNSET:
            field_dict["sourceSnapshotId"] = source_snapshot_id

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.symbol_ref import SymbolRef  # noqa: PLC0415

        d = dict(src_dict)
        index = d.pop("index")

        status = EditItemResultStatus(d.pop("status"))

        kind = EditItemResultKind(d.pop("kind"))

        target = SymbolRef.from_dict(d.pop("target"))

        affected_refs = []
        _affected_refs = d.pop("affectedRefs")
        for affected_refs_item_data in _affected_refs:
            affected_refs_item = SymbolRef.from_dict(affected_refs_item_data)

            affected_refs.append(affected_refs_item)

        def _parse_message(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        message = _parse_message(d.pop("message"))

        def _parse_parameter_index(data: object) -> int | None | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            return cast(int | None | Unset, data)

        parameter_index = _parse_parameter_index(d.pop("parameterIndex", UNSET))

        def _parse_source_snapshot_id(data: object) -> None | str | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            return cast(None | str | Unset, data)

        source_snapshot_id = _parse_source_snapshot_id(d.pop("sourceSnapshotId", UNSET))

        edit_item_result = cls(
            index=index,
            status=status,
            kind=kind,
            target=target,
            affected_refs=affected_refs,
            message=message,
            parameter_index=parameter_index,
            source_snapshot_id=source_snapshot_id,
        )

        return edit_item_result
