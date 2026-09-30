from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar

from attrs import define as _attrs_define

T = TypeVar("T", bound="MappingImportEditCounts")


@_attrs_define
class MappingImportEditCounts:
    """
    Attributes:
        aliases (int):
        comments (int):
    """

    aliases: int
    comments: int

    def to_dict(self) -> dict[str, Any]:
        aliases = self.aliases

        comments = self.comments

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "aliases": aliases,
                "comments": comments,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        aliases = d.pop("aliases")

        comments = d.pop("comments")

        mapping_import_edit_counts = cls(
            aliases=aliases,
            comments=comments,
        )

        return mapping_import_edit_counts
