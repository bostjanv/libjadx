from __future__ import annotations

from collections.abc import Mapping
from typing import (
    TYPE_CHECKING,
    Any,
    Literal,
    TypeVar,
    cast,
)
from uuid import UUID

from attrs import define as _attrs_define

if TYPE_CHECKING:
    from ..models.class_info import ClassInfo


T = TypeVar("T", bound="ClassPage")


@_attrs_define
class ClassPage:
    """
    Attributes:
        session_id (UUID):
        logical_revision (int):
        snapshot_id (str):
        scope (Literal['JADX_VISIBLE']):
        source_coverage (Literal['UNVERIFIED']):
        items (list[ClassInfo]):
        next_cursor (None | str):
        complete (bool):
    """

    session_id: UUID
    logical_revision: int
    snapshot_id: str
    scope: Literal["JADX_VISIBLE"]
    source_coverage: Literal["UNVERIFIED"]
    items: list[ClassInfo]
    next_cursor: None | str
    complete: bool

    def to_dict(self) -> dict[str, Any]:
        session_id = str(self.session_id)

        logical_revision = self.logical_revision

        snapshot_id = self.snapshot_id

        scope = self.scope

        source_coverage = self.source_coverage

        items = []
        for items_item_data in self.items:
            items_item = items_item_data.to_dict()
            items.append(items_item)

        next_cursor: None | str
        next_cursor = self.next_cursor

        complete = self.complete

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "sessionId": session_id,
                "logicalRevision": logical_revision,
                "snapshotId": snapshot_id,
                "scope": scope,
                "sourceCoverage": source_coverage,
                "items": items,
                "nextCursor": next_cursor,
                "complete": complete,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.class_info import ClassInfo  # noqa: PLC0415

        d = dict(src_dict)
        session_id = UUID(d.pop("sessionId"))

        logical_revision = d.pop("logicalRevision")

        snapshot_id = d.pop("snapshotId")

        scope = cast(Literal["JADX_VISIBLE"], d.pop("scope"))
        if scope != "JADX_VISIBLE":
            raise ValueError(f"scope must match const 'JADX_VISIBLE', got '{scope}'")

        source_coverage = cast(Literal["UNVERIFIED"], d.pop("sourceCoverage"))
        if source_coverage != "UNVERIFIED":
            raise ValueError(
                f"sourceCoverage must match const 'UNVERIFIED', got '{source_coverage}'"
            )

        items = []
        _items = d.pop("items")
        for items_item_data in _items:
            items_item = ClassInfo.from_dict(items_item_data)

            items.append(items_item)

        def _parse_next_cursor(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        next_cursor = _parse_next_cursor(d.pop("nextCursor"))

        complete = d.pop("complete")

        class_page = cls(
            session_id=session_id,
            logical_revision=logical_revision,
            snapshot_id=snapshot_id,
            scope=scope,
            source_coverage=source_coverage,
            items=items,
            next_cursor=next_cursor,
            complete=complete,
        )

        return class_page
