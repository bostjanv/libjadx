from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar, cast

from attrs import define as _attrs_define
from attrs import field as _attrs_field

from ..types import UNSET, Unset

if TYPE_CHECKING:
    from ..models.pending_edits_code_data import PendingEditsCodeData


T = TypeVar("T", bound="PendingEdits")


@_attrs_define
class PendingEdits:
    """
    Attributes:
        session_id (str):
        logical_revision (int):
        dirty (bool):
        code_data (PendingEditsCodeData): Native Jadx codeData JSON, including unrecognized fields.
        mappings_path (None | str | Unset): Current in-memory native mapping reference.
    """

    session_id: str
    logical_revision: int
    dirty: bool
    code_data: PendingEditsCodeData
    mappings_path: None | str | Unset = UNSET
    additional_properties: dict[str, Any] = _attrs_field(init=False, factory=dict)

    def to_dict(self) -> dict[str, Any]:
        session_id = self.session_id

        logical_revision = self.logical_revision

        dirty = self.dirty

        code_data = self.code_data.to_dict()

        mappings_path: None | str | Unset
        if isinstance(self.mappings_path, Unset):
            mappings_path = UNSET
        else:
            mappings_path = self.mappings_path

        field_dict: dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update(
            {
                "sessionId": session_id,
                "logicalRevision": logical_revision,
                "dirty": dirty,
                "codeData": code_data,
            }
        )
        if mappings_path is not UNSET:
            field_dict["mappingsPath"] = mappings_path

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.pending_edits_code_data import (
            PendingEditsCodeData,  # noqa: PLC0415
        )

        d = dict(src_dict)
        session_id = d.pop("sessionId")

        logical_revision = d.pop("logicalRevision")

        dirty = d.pop("dirty")

        code_data = PendingEditsCodeData.from_dict(d.pop("codeData"))

        def _parse_mappings_path(data: object) -> None | str | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            return cast(None | str | Unset, data)

        mappings_path = _parse_mappings_path(d.pop("mappingsPath", UNSET))

        pending_edits = cls(
            session_id=session_id,
            logical_revision=logical_revision,
            dirty=dirty,
            code_data=code_data,
            mappings_path=mappings_path,
        )

        pending_edits.additional_properties = d
        return pending_edits

    @property
    def additional_keys(self) -> list[str]:
        return list(self.additional_properties.keys())

    def __getitem__(self, key: str) -> Any:
        return self.additional_properties[key]

    def __setitem__(self, key: str, value: Any) -> None:
        self.additional_properties[key] = value

    def __delitem__(self, key: str) -> None:
        del self.additional_properties[key]

    def __contains__(self, key: str) -> bool:
        return key in self.additional_properties
