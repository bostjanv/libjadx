from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar

from attrs import define as _attrs_define

T = TypeVar("T", bound="SourcePoint")


@_attrs_define
class SourcePoint:
    """On CRLF, the CR boundary belongs to the preceding line and the LF boundary belongs to the same line; the boundary
    after LF starts the next line.

        Attributes:
            offset_utf_16 (int): Zero-based UTF-16 code-unit boundary.
            line (int):
            column_code_points (int):
    """

    offset_utf_16: int
    line: int
    column_code_points: int

    def to_dict(self) -> dict[str, Any]:
        offset_utf_16 = self.offset_utf_16

        line = self.line

        column_code_points = self.column_code_points

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "offsetUtf16": offset_utf_16,
                "line": line,
                "columnCodePoints": column_code_points,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        offset_utf_16 = d.pop("offsetUtf16")

        line = d.pop("line")

        column_code_points = d.pop("columnCodePoints")

        source_point = cls(
            offset_utf_16=offset_utf_16,
            line=line,
            column_code_points=column_code_points,
        )

        return source_point
