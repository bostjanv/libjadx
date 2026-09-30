from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar, cast

from attrs import define as _attrs_define
from attrs import field as _attrs_field

from ..models.project_settings_decompilation_mode import (
    ProjectSettingsDecompilationMode,
)

if TYPE_CHECKING:
    from ..models.revision_set import RevisionSet


T = TypeVar("T", bound="ProjectSettings")


@_attrs_define
class ProjectSettings:
    """
    Attributes:
        mappings_path (None | str): Native mapping reference; an accepted change remains dirty until explicit save.
        decompilation_mode (ProjectSettingsDecompilationMode): Effective primary mode; this mode has no supported native
            persistence here.
        revisions (RevisionSet):
    """

    mappings_path: None | str
    decompilation_mode: ProjectSettingsDecompilationMode
    revisions: RevisionSet
    additional_properties: dict[str, Any] = _attrs_field(init=False, factory=dict)

    def to_dict(self) -> dict[str, Any]:
        mappings_path: None | str
        mappings_path = self.mappings_path

        decompilation_mode = self.decompilation_mode.value

        revisions = self.revisions.to_dict()

        field_dict: dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update(
            {
                "mappingsPath": mappings_path,
                "decompilationMode": decompilation_mode,
                "revisions": revisions,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.revision_set import RevisionSet  # noqa: PLC0415

        d = dict(src_dict)

        def _parse_mappings_path(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        mappings_path = _parse_mappings_path(d.pop("mappingsPath"))

        decompilation_mode = ProjectSettingsDecompilationMode(
            d.pop("decompilationMode")
        )

        revisions = RevisionSet.from_dict(d.pop("revisions"))

        project_settings = cls(
            mappings_path=mappings_path,
            decompilation_mode=decompilation_mode,
            revisions=revisions,
        )

        project_settings.additional_properties = d
        return project_settings

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
