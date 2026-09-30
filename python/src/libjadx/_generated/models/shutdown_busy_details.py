from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar

from attrs import define as _attrs_define
from attrs import field as _attrs_field

if TYPE_CHECKING:
    from ..models.shutdown_busy_details_jobs_item import ShutdownBusyDetailsJobsItem
    from ..models.shutdown_busy_details_operations_item import (
        ShutdownBusyDetailsOperationsItem,
    )


T = TypeVar("T", bound="ShutdownBusyDetails")


@_attrs_define
class ShutdownBusyDetails:
    """
    Attributes:
        active_operation_count (int):
        operations (list[ShutdownBusyDetailsOperationsItem]):
        active_job_count (int):
        jobs (list[ShutdownBusyDetailsJobsItem]):
    """

    active_operation_count: int
    operations: list[ShutdownBusyDetailsOperationsItem]
    active_job_count: int
    jobs: list[ShutdownBusyDetailsJobsItem]
    additional_properties: dict[str, Any] = _attrs_field(init=False, factory=dict)

    def to_dict(self) -> dict[str, Any]:
        active_operation_count = self.active_operation_count

        operations = []
        for operations_item_data in self.operations:
            operations_item = operations_item_data.to_dict()
            operations.append(operations_item)

        active_job_count = self.active_job_count

        jobs = []
        for jobs_item_data in self.jobs:
            jobs_item = jobs_item_data.to_dict()
            jobs.append(jobs_item)

        field_dict: dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update(
            {
                "activeOperationCount": active_operation_count,
                "operations": operations,
                "activeJobCount": active_job_count,
                "jobs": jobs,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.shutdown_busy_details_jobs_item import (
            ShutdownBusyDetailsJobsItem,  # noqa: PLC0415
        )
        from ..models.shutdown_busy_details_operations_item import (
            ShutdownBusyDetailsOperationsItem,  # noqa: PLC0415
        )

        d = dict(src_dict)
        active_operation_count = d.pop("activeOperationCount")

        operations = []
        _operations = d.pop("operations")
        for operations_item_data in _operations:
            operations_item = ShutdownBusyDetailsOperationsItem.from_dict(
                operations_item_data
            )

            operations.append(operations_item)

        active_job_count = d.pop("activeJobCount")

        jobs = []
        _jobs = d.pop("jobs")
        for jobs_item_data in _jobs:
            jobs_item = ShutdownBusyDetailsJobsItem.from_dict(jobs_item_data)

            jobs.append(jobs_item)

        shutdown_busy_details = cls(
            active_operation_count=active_operation_count,
            operations=operations,
            active_job_count=active_job_count,
            jobs=jobs,
        )

        shutdown_busy_details.additional_properties = d
        return shutdown_busy_details

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
