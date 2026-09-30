from __future__ import annotations

from collections.abc import Mapping
from typing import (
    Any,
    Literal,
    TypeVar,
    cast,
)
from uuid import UUID

from attrs import define as _attrs_define

T = TypeVar("T", bound="MappingExportRequest")


@_attrs_define
class MappingExportRequest:
    """
    Attributes:
        target_path (str):
        format_ (Literal['TINY_V2']):
        expected_session_id (UUID):
        expected_logical_revision (int):
    """

    target_path: str
    format_: Literal["TINY_V2"]
    expected_session_id: UUID
    expected_logical_revision: int

    def to_dict(self) -> dict[str, Any]:
        target_path = self.target_path

        format_ = self.format_

        expected_session_id = str(self.expected_session_id)

        expected_logical_revision = self.expected_logical_revision

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "targetPath": target_path,
                "format": format_,
                "expectedSessionId": expected_session_id,
                "expectedLogicalRevision": expected_logical_revision,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        target_path = d.pop("targetPath")

        format_ = cast(Literal["TINY_V2"], d.pop("format"))
        if format_ != "TINY_V2":
            raise ValueError(f"format must match const 'TINY_V2', got '{format_}'")

        expected_session_id = UUID(d.pop("expectedSessionId"))

        expected_logical_revision = d.pop("expectedLogicalRevision")

        mapping_export_request = cls(
            target_path=target_path,
            format_=format_,
            expected_session_id=expected_session_id,
            expected_logical_revision=expected_logical_revision,
        )

        return mapping_export_request
