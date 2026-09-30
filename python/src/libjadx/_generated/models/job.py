from __future__ import annotations

import datetime
from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar, cast
from uuid import UUID

from attrs import define as _attrs_define
from attrs import field as _attrs_field

from ..models.job_cancellation_reason_type_1 import JobCancellationReasonType1
from ..models.job_cancellation_reason_type_2_type_1 import (
    JobCancellationReasonType2Type1,
)
from ..models.job_cancellation_reason_type_3_type_1 import (
    JobCancellationReasonType3Type1,
)
from ..models.job_completeness_type_1 import JobCompletenessType1
from ..models.job_completeness_type_2_type_1 import JobCompletenessType2Type1
from ..models.job_completeness_type_3_type_1 import JobCompletenessType3Type1
from ..models.job_state import JobState

if TYPE_CHECKING:
    from ..models.api_error import ApiError
    from ..models.job_progress import JobProgress
    from ..models.job_result_type_0 import JobResultType0


T = TypeVar("T", bound="Job")


@_attrs_define
class Job:
    """
    Attributes:
        job_id (UUID):
        type_ (str): Internal typed operation name; no generic HTTP submission route exists.
        state (JobState):
        created_at (datetime.datetime):
        started_at (datetime.datetime | None):
        completed_at (datetime.datetime | None):
        deadline_at (datetime.datetime | None):
        session_id (str):
        logical_revision (int):
        source_snapshot_id (str):
        progress (JobProgress):
        result (JobResultType0 | None): Bounded inline JSON result, present only after successful execution.
        completeness (JobCompletenessType1 | JobCompletenessType2Type1 | JobCompletenessType3Type1 | None): Analysis
            result completeness, independent of execution state.
        error (ApiError | None):
        diagnostics (list[str]):
        cancellation_reason (JobCancellationReasonType1 | JobCancellationReasonType2Type1 |
            JobCancellationReasonType3Type1 | None):
    """

    job_id: UUID
    type_: str
    state: JobState
    created_at: datetime.datetime
    started_at: datetime.datetime | None
    completed_at: datetime.datetime | None
    deadline_at: datetime.datetime | None
    session_id: str
    logical_revision: int
    source_snapshot_id: str
    progress: JobProgress
    result: JobResultType0 | None
    completeness: (
        JobCompletenessType1
        | JobCompletenessType2Type1
        | JobCompletenessType3Type1
        | None
    )
    error: ApiError | None
    diagnostics: list[str]
    cancellation_reason: (
        JobCancellationReasonType1
        | JobCancellationReasonType2Type1
        | JobCancellationReasonType3Type1
        | None
    )
    additional_properties: dict[str, Any] = _attrs_field(init=False, factory=dict)

    def to_dict(self) -> dict[str, Any]:
        from ..models.api_error import ApiError  # noqa: PLC0415
        from ..models.job_result_type_0 import JobResultType0  # noqa: PLC0415

        job_id = str(self.job_id)

        type_ = self.type_

        state = self.state.value

        created_at = self.created_at.isoformat()

        started_at: None | str
        if isinstance(self.started_at, datetime.datetime):
            started_at = self.started_at.isoformat()
        else:
            started_at = self.started_at

        completed_at: None | str
        if isinstance(self.completed_at, datetime.datetime):
            completed_at = self.completed_at.isoformat()
        else:
            completed_at = self.completed_at

        deadline_at: None | str
        if isinstance(self.deadline_at, datetime.datetime):
            deadline_at = self.deadline_at.isoformat()
        else:
            deadline_at = self.deadline_at

        session_id = self.session_id

        logical_revision = self.logical_revision

        source_snapshot_id = self.source_snapshot_id

        progress = self.progress.to_dict()

        result: dict[str, Any] | None
        if isinstance(self.result, JobResultType0):
            result = self.result.to_dict()
        else:
            result = self.result

        completeness: None | str
        if isinstance(self.completeness, JobCompletenessType1):
            completeness = self.completeness.value
        elif isinstance(self.completeness, JobCompletenessType2Type1):
            completeness = self.completeness.value
        elif isinstance(self.completeness, JobCompletenessType3Type1):
            completeness = self.completeness.value
        else:
            completeness = self.completeness

        error: dict[str, Any] | None
        if isinstance(self.error, ApiError):
            error = self.error.to_dict()
        else:
            error = self.error

        diagnostics = self.diagnostics

        cancellation_reason: None | str
        if isinstance(self.cancellation_reason, JobCancellationReasonType1):
            cancellation_reason = self.cancellation_reason.value
        elif isinstance(self.cancellation_reason, JobCancellationReasonType2Type1):
            cancellation_reason = self.cancellation_reason.value
        elif isinstance(self.cancellation_reason, JobCancellationReasonType3Type1):
            cancellation_reason = self.cancellation_reason.value
        else:
            cancellation_reason = self.cancellation_reason

        field_dict: dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update(
            {
                "jobId": job_id,
                "type": type_,
                "state": state,
                "createdAt": created_at,
                "startedAt": started_at,
                "completedAt": completed_at,
                "deadlineAt": deadline_at,
                "sessionId": session_id,
                "logicalRevision": logical_revision,
                "sourceSnapshotId": source_snapshot_id,
                "progress": progress,
                "result": result,
                "completeness": completeness,
                "error": error,
                "diagnostics": diagnostics,
                "cancellationReason": cancellation_reason,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.api_error import ApiError  # noqa: PLC0415
        from ..models.job_progress import JobProgress  # noqa: PLC0415
        from ..models.job_result_type_0 import JobResultType0  # noqa: PLC0415

        d = dict(src_dict)
        job_id = UUID(d.pop("jobId"))

        type_ = d.pop("type")

        state = JobState(d.pop("state"))

        created_at = datetime.datetime.fromisoformat(d.pop("createdAt"))

        def _parse_started_at(data: object) -> datetime.datetime | None:
            if data is None:
                return data
            try:
                if not isinstance(data, str):
                    raise TypeError()
                started_at_type_0 = datetime.datetime.fromisoformat(data)

                return started_at_type_0
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            return cast(datetime.datetime | None, data)

        started_at = _parse_started_at(d.pop("startedAt"))

        def _parse_completed_at(data: object) -> datetime.datetime | None:
            if data is None:
                return data
            try:
                if not isinstance(data, str):
                    raise TypeError()
                completed_at_type_0 = datetime.datetime.fromisoformat(data)

                return completed_at_type_0
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            return cast(datetime.datetime | None, data)

        completed_at = _parse_completed_at(d.pop("completedAt"))

        def _parse_deadline_at(data: object) -> datetime.datetime | None:
            if data is None:
                return data
            try:
                if not isinstance(data, str):
                    raise TypeError()
                deadline_at_type_0 = datetime.datetime.fromisoformat(data)

                return deadline_at_type_0
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            return cast(datetime.datetime | None, data)

        deadline_at = _parse_deadline_at(d.pop("deadlineAt"))

        session_id = d.pop("sessionId")

        logical_revision = d.pop("logicalRevision")

        source_snapshot_id = d.pop("sourceSnapshotId")

        progress = JobProgress.from_dict(d.pop("progress"))

        def _parse_result(data: object) -> JobResultType0 | None:
            if data is None:
                return data
            try:
                if not isinstance(data, dict):
                    raise TypeError()
                result_type_0 = JobResultType0.from_dict(data)

                return result_type_0
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            return cast(JobResultType0 | None, data)

        result = _parse_result(d.pop("result"))

        def _parse_completeness(
            data: object,
        ) -> (
            JobCompletenessType1
            | JobCompletenessType2Type1
            | JobCompletenessType3Type1
            | None
        ):
            if data is None:
                return data
            try:
                if not isinstance(data, str):
                    raise TypeError()
                completeness_type_1 = JobCompletenessType1(data)

                return completeness_type_1
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            try:
                if not isinstance(data, str):
                    raise TypeError()
                completeness_type_2_type_1 = JobCompletenessType2Type1(data)

                return completeness_type_2_type_1
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            try:
                if not isinstance(data, str):
                    raise TypeError()
                completeness_type_3_type_1 = JobCompletenessType3Type1(data)

                return completeness_type_3_type_1
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            return cast(
                JobCompletenessType1
                | JobCompletenessType2Type1
                | JobCompletenessType3Type1
                | None,
                data,
            )

        completeness = _parse_completeness(d.pop("completeness"))

        def _parse_error(data: object) -> ApiError | None:
            if data is None:
                return data
            try:
                if not isinstance(data, dict):
                    raise TypeError()
                error_type_0 = ApiError.from_dict(data)

                return error_type_0
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            return cast(ApiError | None, data)

        error = _parse_error(d.pop("error"))

        diagnostics = cast(list[str], d.pop("diagnostics"))

        def _parse_cancellation_reason(
            data: object,
        ) -> (
            JobCancellationReasonType1
            | JobCancellationReasonType2Type1
            | JobCancellationReasonType3Type1
            | None
        ):
            if data is None:
                return data
            try:
                if not isinstance(data, str):
                    raise TypeError()
                cancellation_reason_type_1 = JobCancellationReasonType1(data)

                return cancellation_reason_type_1
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            try:
                if not isinstance(data, str):
                    raise TypeError()
                cancellation_reason_type_2_type_1 = JobCancellationReasonType2Type1(
                    data
                )

                return cancellation_reason_type_2_type_1
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            try:
                if not isinstance(data, str):
                    raise TypeError()
                cancellation_reason_type_3_type_1 = JobCancellationReasonType3Type1(
                    data
                )

                return cancellation_reason_type_3_type_1
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            return cast(
                JobCancellationReasonType1
                | JobCancellationReasonType2Type1
                | JobCancellationReasonType3Type1
                | None,
                data,
            )

        cancellation_reason = _parse_cancellation_reason(d.pop("cancellationReason"))

        job = cls(
            job_id=job_id,
            type_=type_,
            state=state,
            created_at=created_at,
            started_at=started_at,
            completed_at=completed_at,
            deadline_at=deadline_at,
            session_id=session_id,
            logical_revision=logical_revision,
            source_snapshot_id=source_snapshot_id,
            progress=progress,
            result=result,
            completeness=completeness,
            error=error,
            diagnostics=diagnostics,
            cancellation_reason=cancellation_reason,
        )

        job.additional_properties = d
        return job

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
