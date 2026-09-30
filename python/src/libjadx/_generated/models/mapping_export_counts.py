from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar

from attrs import define as _attrs_define

T = TypeVar("T", bound="MappingExportCounts")


@_attrs_define
class MappingExportCounts:
    """
    Attributes:
        classes (int):
        methods (int):
        fields (int):
        comments (int):
    """

    classes: int
    methods: int
    fields: int
    comments: int

    def to_dict(self) -> dict[str, Any]:
        classes = self.classes

        methods = self.methods

        fields = self.fields

        comments = self.comments

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "classes": classes,
                "methods": methods,
                "fields": fields,
                "comments": comments,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        classes = d.pop("classes")

        methods = d.pop("methods")

        fields = d.pop("fields")

        comments = d.pop("comments")

        mapping_export_counts = cls(
            classes=classes,
            methods=methods,
            fields=fields,
            comments=comments,
        )

        return mapping_export_counts
