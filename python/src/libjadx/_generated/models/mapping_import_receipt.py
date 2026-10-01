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

from ..models.mapping_import_receipt_outcome import MappingImportReceiptOutcome

if TYPE_CHECKING:
    from ..models.mapping_export_counts import MappingExportCounts
    from ..models.mapping_import_edit_counts import MappingImportEditCounts
    from ..models.mapping_import_receipt_omissions_item import (
        MappingImportReceiptOmissionsItem,
    )


T = TypeVar("T", bound="MappingImportReceipt")


@_attrs_define
class MappingImportReceipt:
    """
    Attributes:
        format_ (Literal['TINY_V2']):
        mode (Literal['MERGE_FAIL_ON_CONFLICT']):
        source_path (str):
        sha256 (str):
        bytes_ (int):
        parsed (MappingExportCounts):
        applied (MappingImportEditCounts):
        unchanged (MappingImportEditCounts):
        session_id (UUID):
        before_logical_revision (int):
        after_logical_revision (int):
        before_index_revision (int):
        after_index_revision (int):
        dirty (bool):
        saved (bool):
        mapping_attached (bool):
        outcome (MappingImportReceiptOutcome):
        omissions (list[MappingImportReceiptOmissionsItem]):
    """

    format_: Literal["TINY_V2"]
    mode: Literal["MERGE_FAIL_ON_CONFLICT"]
    source_path: str
    sha256: str
    bytes_: int
    parsed: MappingExportCounts
    applied: MappingImportEditCounts
    unchanged: MappingImportEditCounts
    session_id: UUID
    before_logical_revision: int
    after_logical_revision: int
    before_index_revision: int
    after_index_revision: int
    dirty: bool
    saved: bool
    mapping_attached: bool
    outcome: MappingImportReceiptOutcome
    omissions: list[MappingImportReceiptOmissionsItem]

    def to_dict(self) -> dict[str, Any]:
        format_ = self.format_

        mode = self.mode

        source_path = self.source_path

        sha256 = self.sha256

        bytes_ = self.bytes_

        parsed = self.parsed.to_dict()

        applied = self.applied.to_dict()

        unchanged = self.unchanged.to_dict()

        session_id = str(self.session_id)

        before_logical_revision = self.before_logical_revision

        after_logical_revision = self.after_logical_revision

        before_index_revision = self.before_index_revision

        after_index_revision = self.after_index_revision

        dirty = self.dirty

        saved = self.saved

        mapping_attached = self.mapping_attached

        outcome = self.outcome.value

        omissions = []
        for omissions_item_data in self.omissions:
            omissions_item = omissions_item_data.to_dict()
            omissions.append(omissions_item)

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "format": format_,
                "mode": mode,
                "sourcePath": source_path,
                "sha256": sha256,
                "bytes": bytes_,
                "parsed": parsed,
                "applied": applied,
                "unchanged": unchanged,
                "sessionId": session_id,
                "beforeLogicalRevision": before_logical_revision,
                "afterLogicalRevision": after_logical_revision,
                "beforeIndexRevision": before_index_revision,
                "afterIndexRevision": after_index_revision,
                "dirty": dirty,
                "saved": saved,
                "mappingAttached": mapping_attached,
                "outcome": outcome,
                "omissions": omissions,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.mapping_export_counts import MappingExportCounts  # noqa: PLC0415
        from ..models.mapping_import_edit_counts import (
            MappingImportEditCounts,  # noqa: PLC0415
        )
        from ..models.mapping_import_receipt_omissions_item import (
            MappingImportReceiptOmissionsItem,  # noqa: PLC0415
        )

        d = dict(src_dict)
        format_ = cast(Literal["TINY_V2"], d.pop("format"))
        if format_ != "TINY_V2":
            raise ValueError(f"format must match const 'TINY_V2', got '{format_}'")

        mode = cast(Literal["MERGE_FAIL_ON_CONFLICT"], d.pop("mode"))
        if mode != "MERGE_FAIL_ON_CONFLICT":
            raise ValueError(
                f"mode must match const 'MERGE_FAIL_ON_CONFLICT', got '{mode}'"
            )

        source_path = d.pop("sourcePath")

        sha256 = d.pop("sha256")

        bytes_ = d.pop("bytes")

        parsed = MappingExportCounts.from_dict(d.pop("parsed"))

        applied = MappingImportEditCounts.from_dict(d.pop("applied"))

        unchanged = MappingImportEditCounts.from_dict(d.pop("unchanged"))

        session_id = UUID(d.pop("sessionId"))

        before_logical_revision = d.pop("beforeLogicalRevision")

        after_logical_revision = d.pop("afterLogicalRevision")

        before_index_revision = d.pop("beforeIndexRevision")

        after_index_revision = d.pop("afterIndexRevision")

        dirty = d.pop("dirty")

        saved = d.pop("saved")

        mapping_attached = d.pop("mappingAttached")

        outcome = MappingImportReceiptOutcome(d.pop("outcome"))

        omissions = []
        _omissions = d.pop("omissions")
        for omissions_item_data in _omissions:
            omissions_item = MappingImportReceiptOmissionsItem.from_dict(
                omissions_item_data
            )

            omissions.append(omissions_item)

        mapping_import_receipt = cls(
            format_=format_,
            mode=mode,
            source_path=source_path,
            sha256=sha256,
            bytes_=bytes_,
            parsed=parsed,
            applied=applied,
            unchanged=unchanged,
            session_id=session_id,
            before_logical_revision=before_logical_revision,
            after_logical_revision=after_logical_revision,
            before_index_revision=before_index_revision,
            after_index_revision=after_index_revision,
            dirty=dirty,
            saved=saved,
            mapping_attached=mapping_attached,
            outcome=outcome,
            omissions=omissions,
        )

        return mapping_import_receipt
