from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar
from uuid import UUID

from attrs import define as _attrs_define
from attrs import field as _attrs_field

from ..types import UNSET, Unset

T = TypeVar("T", bound="SaveProjectRequest")


@_attrs_define
class SaveProjectRequest:
    """
    Attributes:
        target_path (str | Unset): Existing project path or a new .jadx path for raw-input startup.
        expected_logical_revision (int | Unset):
        expected_session_id (UUID | Unset):
    """

    target_path: str | Unset = UNSET
    expected_logical_revision: int | Unset = UNSET
    expected_session_id: UUID | Unset = UNSET
    additional_properties: dict[str, Any] = _attrs_field(init=False, factory=dict)

    def to_dict(self) -> dict[str, Any]:
        target_path = self.target_path

        expected_logical_revision = self.expected_logical_revision

        expected_session_id: str | Unset = UNSET
        if not isinstance(self.expected_session_id, Unset):
            expected_session_id = str(self.expected_session_id)

        field_dict: dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update({})
        if target_path is not UNSET:
            field_dict["targetPath"] = target_path
        if expected_logical_revision is not UNSET:
            field_dict["expectedLogicalRevision"] = expected_logical_revision
        if expected_session_id is not UNSET:
            field_dict["expectedSessionId"] = expected_session_id

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        target_path = d.pop("targetPath", UNSET)

        expected_logical_revision = d.pop("expectedLogicalRevision", UNSET)

        _expected_session_id = d.pop("expectedSessionId", UNSET)
        expected_session_id: UUID | Unset
        if isinstance(_expected_session_id, Unset):
            expected_session_id = UNSET
        else:
            expected_session_id = UUID(_expected_session_id)

        save_project_request = cls(
            target_path=target_path,
            expected_logical_revision=expected_logical_revision,
            expected_session_id=expected_session_id,
        )

        save_project_request.additional_properties = d
        return save_project_request

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
