from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define
from attrs import field as _attrs_field

T = TypeVar("T", bound="JobProgress")


@_attrs_define
class JobProgress:
    """
    Attributes:
        completed (int):
        total (int | None): Null when the amount of work is unknown.
        stage (str):
    """

    completed: int
    total: int | None
    stage: str
    additional_properties: dict[str, Any] = _attrs_field(init=False, factory=dict)

    def to_dict(self) -> dict[str, Any]:
        completed = self.completed

        total: int | None
        total = self.total

        stage = self.stage

        field_dict: dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update(
            {
                "completed": completed,
                "total": total,
                "stage": stage,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        completed = d.pop("completed")

        def _parse_total(data: object) -> int | None:
            if data is None:
                return data
            return cast(int | None, data)

        total = _parse_total(d.pop("total"))

        stage = d.pop("stage")

        job_progress = cls(
            completed=completed,
            total=total,
            stage=stage,
        )

        job_progress.additional_properties = d
        return job_progress

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
