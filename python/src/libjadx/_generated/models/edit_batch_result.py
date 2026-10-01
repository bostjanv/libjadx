from __future__ import annotations

from collections.abc import Mapping
from typing import (
    TYPE_CHECKING,
    Any,
    Literal,
    TypeVar,
    cast,
)

from attrs import define as _attrs_define

from ..models.edit_batch_result_outcome import EditBatchResultOutcome

if TYPE_CHECKING:
    from ..models.edit_item_result import EditItemResult


T = TypeVar("T", bound="EditBatchResult")


@_attrs_define
class EditBatchResult:
    """
    Attributes:
        outcome (EditBatchResultOutcome): PARTIAL reports a staging failure, including failure before any APPLIED item.
            Inspect items and logicalRevisionAfter for the exact committed state; no applied items means revisions/dirty are
            unchanged.
        session_id (str):
        logical_revision_before (int):
        logical_revision_after (int):
        index_revision_after (int):
        dirty (bool):
        saved (Literal[False]):
        items (list[EditItemResult]):
        diagnostics (list[str]):
    """

    outcome: EditBatchResultOutcome
    session_id: str
    logical_revision_before: int
    logical_revision_after: int
    index_revision_after: int
    dirty: bool
    saved: Literal[False]
    items: list[EditItemResult]
    diagnostics: list[str]

    def to_dict(self) -> dict[str, Any]:
        outcome = self.outcome.value

        session_id = self.session_id

        logical_revision_before = self.logical_revision_before

        logical_revision_after = self.logical_revision_after

        index_revision_after = self.index_revision_after

        dirty = self.dirty

        saved = self.saved

        items = []
        for items_item_data in self.items:
            items_item = items_item_data.to_dict()
            items.append(items_item)

        diagnostics = self.diagnostics

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "outcome": outcome,
                "sessionId": session_id,
                "logicalRevisionBefore": logical_revision_before,
                "logicalRevisionAfter": logical_revision_after,
                "indexRevisionAfter": index_revision_after,
                "dirty": dirty,
                "saved": saved,
                "items": items,
                "diagnostics": diagnostics,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.edit_item_result import EditItemResult  # noqa: PLC0415

        d = dict(src_dict)
        outcome = EditBatchResultOutcome(d.pop("outcome"))

        session_id = d.pop("sessionId")

        logical_revision_before = d.pop("logicalRevisionBefore")

        logical_revision_after = d.pop("logicalRevisionAfter")

        index_revision_after = d.pop("indexRevisionAfter")

        dirty = d.pop("dirty")

        saved = cast(Literal[False], d.pop("saved"))
        if saved != False:
            raise ValueError(f"saved must match const False, got '{saved}'")

        items = []
        _items = d.pop("items")
        for items_item_data in _items:
            items_item = EditItemResult.from_dict(items_item_data)

            items.append(items_item)

        diagnostics = cast(list[str], d.pop("diagnostics"))

        edit_batch_result = cls(
            outcome=outcome,
            session_id=session_id,
            logical_revision_before=logical_revision_before,
            logical_revision_after=logical_revision_after,
            index_revision_after=index_revision_after,
            dirty=dirty,
            saved=saved,
            items=items,
            diagnostics=diagnostics,
        )

        return edit_batch_result
