from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar
from uuid import UUID

from attrs import define as _attrs_define
from attrs import field as _attrs_field

T = TypeVar("T", bound="ReloadProjectRequest")


@_attrs_define
class ReloadProjectRequest:
    """
    Attributes:
        discard_unsaved (bool):
        expected_session_id (UUID):
        expected_logical_revision (int):
    """

    discard_unsaved: bool
    expected_session_id: UUID
    expected_logical_revision: int
    additional_properties: dict[str, Any] = _attrs_field(init=False, factory=dict)

    def to_dict(self) -> dict[str, Any]:
        discard_unsaved = self.discard_unsaved

        expected_session_id = str(self.expected_session_id)

        expected_logical_revision = self.expected_logical_revision

        field_dict: dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update(
            {
                "discardUnsaved": discard_unsaved,
                "expectedSessionId": expected_session_id,
                "expectedLogicalRevision": expected_logical_revision,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        discard_unsaved = d.pop("discardUnsaved")

        expected_session_id = UUID(d.pop("expectedSessionId"))

        expected_logical_revision = d.pop("expectedLogicalRevision")

        reload_project_request = cls(
            discard_unsaved=discard_unsaved,
            expected_session_id=expected_session_id,
            expected_logical_revision=expected_logical_revision,
        )

        reload_project_request.additional_properties = d
        return reload_project_request

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
