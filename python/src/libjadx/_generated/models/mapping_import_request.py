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

T = TypeVar("T", bound="MappingImportRequest")


@_attrs_define
class MappingImportRequest:
    """
    Attributes:
        source_path (str):
        format_ (Literal['TINY_V2']):
        mode (Literal['MERGE_FAIL_ON_CONFLICT']):
        expected_session_id (UUID):
        expected_logical_revision (int):
    """

    source_path: str
    format_: Literal["TINY_V2"]
    mode: Literal["MERGE_FAIL_ON_CONFLICT"]
    expected_session_id: UUID
    expected_logical_revision: int

    def to_dict(self) -> dict[str, Any]:
        source_path = self.source_path

        format_ = self.format_

        mode = self.mode

        expected_session_id = str(self.expected_session_id)

        expected_logical_revision = self.expected_logical_revision

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "sourcePath": source_path,
                "format": format_,
                "mode": mode,
                "expectedSessionId": expected_session_id,
                "expectedLogicalRevision": expected_logical_revision,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        source_path = d.pop("sourcePath")

        format_ = cast(Literal["TINY_V2"], d.pop("format"))
        if format_ != "TINY_V2":
            raise ValueError(f"format must match const 'TINY_V2', got '{format_}'")

        mode = cast(Literal["MERGE_FAIL_ON_CONFLICT"], d.pop("mode"))
        if mode != "MERGE_FAIL_ON_CONFLICT":
            raise ValueError(
                f"mode must match const 'MERGE_FAIL_ON_CONFLICT', got '{mode}'"
            )

        expected_session_id = UUID(d.pop("expectedSessionId"))

        expected_logical_revision = d.pop("expectedLogicalRevision")

        mapping_import_request = cls(
            source_path=source_path,
            format_=format_,
            mode=mode,
            expected_session_id=expected_session_id,
            expected_logical_revision=expected_logical_revision,
        )

        return mapping_import_request
