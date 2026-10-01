from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar, cast

from attrs import define as _attrs_define
from attrs import field as _attrs_field

from ..models.service_status_state import ServiceStatusState
from ..types import UNSET, Unset

if TYPE_CHECKING:
    from ..models.api_error import ApiError
    from ..models.progress import Progress


T = TypeVar("T", bound="ServiceStatus")


@_attrs_define
class ServiceStatus:
    """
    Attributes:
        state (ServiceStatusState):
        stage (str): Active initialization or lifecycle stage.
        progress (Progress):
        inputs (list[str]):
        project_path (None | str | Unset): Native .jadx path when started from an existing native project.
        error (ApiError | None | Unset):
    """

    state: ServiceStatusState
    stage: str
    progress: Progress
    inputs: list[str]
    project_path: None | str | Unset = UNSET
    error: ApiError | None | Unset = UNSET
    additional_properties: dict[str, Any] = _attrs_field(init=False, factory=dict)

    def to_dict(self) -> dict[str, Any]:
        from ..models.api_error import ApiError  # noqa: PLC0415

        state = self.state.value

        stage = self.stage

        progress = self.progress.to_dict()

        inputs = self.inputs

        project_path: None | str | Unset
        if isinstance(self.project_path, Unset):
            project_path = UNSET
        else:
            project_path = self.project_path

        error: dict[str, Any] | None | Unset
        if isinstance(self.error, Unset):
            error = UNSET
        elif isinstance(self.error, ApiError):
            error = self.error.to_dict()
        else:
            error = self.error

        field_dict: dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update(
            {
                "state": state,
                "stage": stage,
                "progress": progress,
                "inputs": inputs,
            }
        )
        if project_path is not UNSET:
            field_dict["projectPath"] = project_path
        if error is not UNSET:
            field_dict["error"] = error

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.api_error import ApiError  # noqa: PLC0415
        from ..models.progress import Progress  # noqa: PLC0415

        d = dict(src_dict)
        state = ServiceStatusState(d.pop("state"))

        stage = d.pop("stage")

        progress = Progress.from_dict(d.pop("progress"))

        inputs = cast(list[str], d.pop("inputs"))

        def _parse_project_path(data: object) -> None | str | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            return cast(None | str | Unset, data)

        project_path = _parse_project_path(d.pop("projectPath", UNSET))

        def _parse_error(data: object) -> ApiError | None | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            try:
                if not isinstance(data, dict):
                    raise TypeError()
                error_type_0 = ApiError.from_dict(data)

                return error_type_0
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            return cast(ApiError | None | Unset, data)

        error = _parse_error(d.pop("error", UNSET))

        service_status = cls(
            state=state,
            stage=stage,
            progress=progress,
            inputs=inputs,
            project_path=project_path,
            error=error,
        )

        service_status.additional_properties = d
        return service_status

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
