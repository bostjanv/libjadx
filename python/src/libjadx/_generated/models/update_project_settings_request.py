from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar, cast
from uuid import UUID

from attrs import define as _attrs_define
from attrs import field as _attrs_field

T = TypeVar("T", bound="UpdateProjectSettingsRequest")


@_attrs_define
class UpdateProjectSettingsRequest:
    """
    Attributes:
        mappings_path (None | str):
        expected_logical_revision (int):
        expected_session_id (UUID):
    """

    mappings_path: None | str
    expected_logical_revision: int
    expected_session_id: UUID
    additional_properties: dict[str, Any] = _attrs_field(init=False, factory=dict)

    def to_dict(self) -> dict[str, Any]:
        mappings_path: None | str
        mappings_path = self.mappings_path

        expected_logical_revision = self.expected_logical_revision

        expected_session_id = str(self.expected_session_id)

        field_dict: dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update(
            {
                "mappingsPath": mappings_path,
                "expectedLogicalRevision": expected_logical_revision,
                "expectedSessionId": expected_session_id,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)

        def _parse_mappings_path(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        mappings_path = _parse_mappings_path(d.pop("mappingsPath"))

        expected_logical_revision = d.pop("expectedLogicalRevision")

        expected_session_id = UUID(d.pop("expectedSessionId"))

        update_project_settings_request = cls(
            mappings_path=mappings_path,
            expected_logical_revision=expected_logical_revision,
            expected_session_id=expected_session_id,
        )

        update_project_settings_request.additional_properties = d
        return update_project_settings_request

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
