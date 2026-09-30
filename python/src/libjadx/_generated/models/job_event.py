from __future__ import annotations

import datetime
from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar
from uuid import UUID

from attrs import define as _attrs_define
from attrs import field as _attrs_field

from ..models.job_event_state import JobEventState
from ..models.job_event_type import JobEventType

if TYPE_CHECKING:
    from ..models.job_progress import JobProgress


T = TypeVar("T", bound="JobEvent")


@_attrs_define
class JobEvent:
    """
    Attributes:
        job_id (UUID):
        sequence (int):
        type_ (JobEventType):
        at (datetime.datetime):
        state (JobEventState):
        progress (JobProgress):
        result_url (str): Poll this URL for the authoritative terminal result or error.
    """

    job_id: UUID
    sequence: int
    type_: JobEventType
    at: datetime.datetime
    state: JobEventState
    progress: JobProgress
    result_url: str
    additional_properties: dict[str, Any] = _attrs_field(init=False, factory=dict)

    def to_dict(self) -> dict[str, Any]:
        job_id = str(self.job_id)

        sequence = self.sequence

        type_ = self.type_.value

        at = self.at.isoformat()

        state = self.state.value

        progress = self.progress.to_dict()

        result_url = self.result_url

        field_dict: dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update(
            {
                "jobId": job_id,
                "sequence": sequence,
                "type": type_,
                "at": at,
                "state": state,
                "progress": progress,
                "resultUrl": result_url,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.job_progress import JobProgress  # noqa: PLC0415

        d = dict(src_dict)
        job_id = UUID(d.pop("jobId"))

        sequence = d.pop("sequence")

        type_ = JobEventType(d.pop("type"))

        at = datetime.datetime.fromisoformat(d.pop("at"))

        state = JobEventState(d.pop("state"))

        progress = JobProgress.from_dict(d.pop("progress"))

        result_url = d.pop("resultUrl")

        job_event = cls(
            job_id=job_id,
            sequence=sequence,
            type_=type_,
            at=at,
            state=state,
            progress=progress,
            result_url=result_url,
        )

        job_event.additional_properties = d
        return job_event

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
