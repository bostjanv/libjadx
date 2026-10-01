from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar, cast

from attrs import define as _attrs_define
from attrs import field as _attrs_field

from ..types import UNSET, Unset

if TYPE_CHECKING:
    from ..models.revision_set import RevisionSet


T = TypeVar("T", bound="FixedProject")


@_attrs_define
class FixedProject:
    """
    Attributes:
        inputs (list[str]):
        dirty (bool):
        revisions (RevisionSet):
        project_path (None | str | Unset):
    """

    inputs: list[str]
    dirty: bool
    revisions: RevisionSet
    project_path: None | str | Unset = UNSET
    additional_properties: dict[str, Any] = _attrs_field(init=False, factory=dict)

    def to_dict(self) -> dict[str, Any]:
        inputs = self.inputs

        dirty = self.dirty

        revisions = self.revisions.to_dict()

        project_path: None | str | Unset
        if isinstance(self.project_path, Unset):
            project_path = UNSET
        else:
            project_path = self.project_path

        field_dict: dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update(
            {
                "inputs": inputs,
                "dirty": dirty,
                "revisions": revisions,
            }
        )
        if project_path is not UNSET:
            field_dict["projectPath"] = project_path

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.revision_set import RevisionSet  # noqa: PLC0415

        d = dict(src_dict)
        inputs = cast(list[str], d.pop("inputs"))

        dirty = d.pop("dirty")

        revisions = RevisionSet.from_dict(d.pop("revisions"))

        def _parse_project_path(data: object) -> None | str | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            return cast(None | str | Unset, data)

        project_path = _parse_project_path(d.pop("projectPath", UNSET))

        fixed_project = cls(
            inputs=inputs,
            dirty=dirty,
            revisions=revisions,
            project_path=project_path,
        )

        fixed_project.additional_properties = d
        return fixed_project

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
