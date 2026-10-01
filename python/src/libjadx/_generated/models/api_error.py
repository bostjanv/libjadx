from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar, cast

from attrs import define as _attrs_define
from attrs import field as _attrs_field

from ..models.api_error_code import ApiErrorCode
from ..types import UNSET, Unset

if TYPE_CHECKING:
    from ..models.api_error_details_type_0 import ApiErrorDetailsType0
    from ..models.item_error import ItemError


T = TypeVar("T", bound="ApiError")


@_attrs_define
class ApiError:
    """
    Attributes:
        code (ApiErrorCode): Stable machine-readable error code.
        message (str):
        retryable (bool | Unset):
        request_id (None | str | Unset):
        details (ApiErrorDetailsType0 | None | Unset):
        causes (list[ApiError] | Unset):
        item_errors (list[ItemError] | Unset):
    """

    code: ApiErrorCode
    message: str
    retryable: bool | Unset = UNSET
    request_id: None | str | Unset = UNSET
    details: ApiErrorDetailsType0 | None | Unset = UNSET
    causes: list[ApiError] | Unset = UNSET
    item_errors: list[ItemError] | Unset = UNSET
    additional_properties: dict[str, Any] = _attrs_field(init=False, factory=dict)

    def to_dict(self) -> dict[str, Any]:
        from ..models.api_error_details_type_0 import (
            ApiErrorDetailsType0,  # noqa: PLC0415
        )

        code = self.code.value

        message = self.message

        retryable = self.retryable

        request_id: None | str | Unset
        if isinstance(self.request_id, Unset):
            request_id = UNSET
        else:
            request_id = self.request_id

        details: dict[str, Any] | None | Unset
        if isinstance(self.details, Unset):
            details = UNSET
        elif isinstance(self.details, ApiErrorDetailsType0):
            details = self.details.to_dict()
        else:
            details = self.details

        causes: list[dict[str, Any]] | Unset = UNSET
        if not isinstance(self.causes, Unset):
            causes = []
            for causes_item_data in self.causes:
                causes_item = causes_item_data.to_dict()
                causes.append(causes_item)

        item_errors: list[dict[str, Any]] | Unset = UNSET
        if not isinstance(self.item_errors, Unset):
            item_errors = []
            for item_errors_item_data in self.item_errors:
                item_errors_item = item_errors_item_data.to_dict()
                item_errors.append(item_errors_item)

        field_dict: dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update(
            {
                "code": code,
                "message": message,
            }
        )
        if retryable is not UNSET:
            field_dict["retryable"] = retryable
        if request_id is not UNSET:
            field_dict["requestId"] = request_id
        if details is not UNSET:
            field_dict["details"] = details
        if causes is not UNSET:
            field_dict["causes"] = causes
        if item_errors is not UNSET:
            field_dict["itemErrors"] = item_errors

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.api_error_details_type_0 import (
            ApiErrorDetailsType0,  # noqa: PLC0415
        )
        from ..models.item_error import ItemError  # noqa: PLC0415

        d = dict(src_dict)
        code = ApiErrorCode(d.pop("code"))

        message = d.pop("message")

        retryable = d.pop("retryable", UNSET)

        def _parse_request_id(data: object) -> None | str | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            return cast(None | str | Unset, data)

        request_id = _parse_request_id(d.pop("requestId", UNSET))

        def _parse_details(data: object) -> ApiErrorDetailsType0 | None | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            try:
                if not isinstance(data, dict):
                    raise TypeError()
                details_type_0 = ApiErrorDetailsType0.from_dict(data)

                return details_type_0
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            return cast(ApiErrorDetailsType0 | None | Unset, data)

        details = _parse_details(d.pop("details", UNSET))

        _causes = d.pop("causes", UNSET)
        causes: list[ApiError] | Unset = UNSET
        if _causes is not UNSET:
            causes = []
            for causes_item_data in _causes:
                causes_item = ApiError.from_dict(causes_item_data)

                causes.append(causes_item)

        _item_errors = d.pop("itemErrors", UNSET)
        item_errors: list[ItemError] | Unset = UNSET
        if _item_errors is not UNSET:
            item_errors = []
            for item_errors_item_data in _item_errors:
                item_errors_item = ItemError.from_dict(item_errors_item_data)

                item_errors.append(item_errors_item)

        api_error = cls(
            code=code,
            message=message,
            retryable=retryable,
            request_id=request_id,
            details=details,
            causes=causes,
            item_errors=item_errors,
        )

        api_error.additional_properties = d
        return api_error

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
