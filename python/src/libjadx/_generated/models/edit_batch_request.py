from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar
from uuid import UUID

from attrs import define as _attrs_define

from ..types import UNSET, Unset

if TYPE_CHECKING:
    from ..models.rename_operation import RenameOperation
    from ..models.rename_parameter_operation import RenameParameterOperation
    from ..models.set_comment_operation import SetCommentOperation


T = TypeVar("T", bound="EditBatchRequest")


@_attrs_define
class EditBatchRequest:
    """
    Attributes:
        items (list[RenameOperation | RenameParameterOperation | SetCommentOperation]):
        expected_session_id (UUID | Unset):
        expected_logical_revision (int | Unset):
    """

    items: list[RenameOperation | RenameParameterOperation | SetCommentOperation]
    expected_session_id: UUID | Unset = UNSET
    expected_logical_revision: int | Unset = UNSET

    def to_dict(self) -> dict[str, Any]:
        from ..models.rename_operation import RenameOperation  # noqa: PLC0415
        from ..models.set_comment_operation import SetCommentOperation  # noqa: PLC0415

        items = []
        for items_item_data in self.items:
            items_item: dict[str, Any]
            if isinstance(items_item_data, RenameOperation):
                items_item = items_item_data.to_dict()
            elif isinstance(items_item_data, SetCommentOperation):
                items_item = items_item_data.to_dict()
            else:
                items_item = items_item_data.to_dict()

            items.append(items_item)

        expected_session_id: str | Unset = UNSET
        if not isinstance(self.expected_session_id, Unset):
            expected_session_id = str(self.expected_session_id)

        expected_logical_revision = self.expected_logical_revision

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "items": items,
            }
        )
        if expected_session_id is not UNSET:
            field_dict["expectedSessionId"] = expected_session_id
        if expected_logical_revision is not UNSET:
            field_dict["expectedLogicalRevision"] = expected_logical_revision

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.rename_operation import RenameOperation  # noqa: PLC0415
        from ..models.rename_parameter_operation import (
            RenameParameterOperation,  # noqa: PLC0415
        )
        from ..models.set_comment_operation import SetCommentOperation  # noqa: PLC0415

        d = dict(src_dict)
        items = []
        _items = d.pop("items")
        for items_item_data in _items:

            def _parse_items_item(
                data: object,
            ) -> RenameOperation | RenameParameterOperation | SetCommentOperation:
                try:
                    if not isinstance(data, dict):
                        raise TypeError()
                    componentsschemas_edit_operation_type_0 = RenameOperation.from_dict(
                        data
                    )

                    return componentsschemas_edit_operation_type_0
                except (TypeError, ValueError, AttributeError, KeyError):
                    pass
                try:
                    if not isinstance(data, dict):
                        raise TypeError()
                    componentsschemas_edit_operation_type_1 = (
                        SetCommentOperation.from_dict(data)
                    )

                    return componentsschemas_edit_operation_type_1
                except (TypeError, ValueError, AttributeError, KeyError):
                    pass
                if not isinstance(data, dict):
                    raise TypeError()
                componentsschemas_edit_operation_type_2 = (
                    RenameParameterOperation.from_dict(data)
                )

                return componentsschemas_edit_operation_type_2

            items_item = _parse_items_item(items_item_data)

            items.append(items_item)

        _expected_session_id = d.pop("expectedSessionId", UNSET)
        expected_session_id: UUID | Unset
        if isinstance(_expected_session_id, Unset):
            expected_session_id = UNSET
        else:
            expected_session_id = UUID(_expected_session_id)

        expected_logical_revision = d.pop("expectedLogicalRevision", UNSET)

        edit_batch_request = cls(
            items=items,
            expected_session_id=expected_session_id,
            expected_logical_revision=expected_logical_revision,
        )

        return edit_batch_request
