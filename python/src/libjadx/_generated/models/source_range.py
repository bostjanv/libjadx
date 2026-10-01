from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar

from attrs import define as _attrs_define

if TYPE_CHECKING:
    from ..models.source_point import SourcePoint


T = TypeVar("T", bound="SourceRange")


@_attrs_define
class SourceRange:
    """Half-open range in the exact returned Java string.

    Attributes:
        start_offset_utf_16 (int):
        end_offset_utf_16 (int):
        start (SourcePoint): On CRLF, the CR boundary belongs to the preceding line and the LF boundary belongs to the
            same line; the boundary after LF starts the next line.
        end (SourcePoint): On CRLF, the CR boundary belongs to the preceding line and the LF boundary belongs to the
            same line; the boundary after LF starts the next line.
        source_snapshot_id (str):
    """

    start_offset_utf_16: int
    end_offset_utf_16: int
    start: SourcePoint
    end: SourcePoint
    source_snapshot_id: str

    def to_dict(self) -> dict[str, Any]:
        start_offset_utf_16 = self.start_offset_utf_16

        end_offset_utf_16 = self.end_offset_utf_16

        start = self.start.to_dict()

        end = self.end.to_dict()

        source_snapshot_id = self.source_snapshot_id

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "startOffsetUtf16": start_offset_utf_16,
                "endOffsetUtf16": end_offset_utf_16,
                "start": start,
                "end": end,
                "sourceSnapshotId": source_snapshot_id,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.source_point import SourcePoint  # noqa: PLC0415

        d = dict(src_dict)
        start_offset_utf_16 = d.pop("startOffsetUtf16")

        end_offset_utf_16 = d.pop("endOffsetUtf16")

        start = SourcePoint.from_dict(d.pop("start"))

        end = SourcePoint.from_dict(d.pop("end"))

        source_snapshot_id = d.pop("sourceSnapshotId")

        source_range = cls(
            start_offset_utf_16=start_offset_utf_16,
            end_offset_utf_16=end_offset_utf_16,
            start=start,
            end=end,
            source_snapshot_id=source_snapshot_id,
        )

        return source_range
