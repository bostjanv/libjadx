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
    from ..models.mapping_export_counts import MappingExportCounts
    from ..models.mapping_export_receipt_omissions_item import (
        MappingExportReceiptOmissionsItem,
    )


T = TypeVar("T", bound="MappingExportReceipt")


@_attrs_define
class MappingExportReceipt:
    """
    Attributes:
        format_ (Literal['TINY_V2']):
        target_path (str):
        session_id (UUID):
        logical_revision (int):
        source (Literal['CURRENT_IN_MEMORY_WITH_ATTACHED_MAPPINGS']):
        completeness (Literal['VERIFIED_DECLARATIONS']):
        exported (MappingExportCounts):
        bytes_ (int):
        sha256 (str):
        omissions (list[MappingExportReceiptOmissionsItem]):
        project_mutated (bool):
    """

    format_: Literal["TINY_V2"]
    target_path: str
    session_id: UUID
    logical_revision: int
    source: Literal["CURRENT_IN_MEMORY_WITH_ATTACHED_MAPPINGS"]
    completeness: Literal["VERIFIED_DECLARATIONS"]
    exported: MappingExportCounts
    bytes_: int
    sha256: str
    omissions: list[MappingExportReceiptOmissionsItem]
    project_mutated: bool

    def to_dict(self) -> dict[str, Any]:
        format_ = self.format_

        target_path = self.target_path

        session_id = str(self.session_id)

        logical_revision = self.logical_revision

        source = self.source

        completeness = self.completeness

        exported = self.exported.to_dict()

        bytes_ = self.bytes_

        sha256 = self.sha256

        omissions = []
        for omissions_item_data in self.omissions:
            omissions_item = omissions_item_data.to_dict()
            omissions.append(omissions_item)

        project_mutated = self.project_mutated

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "format": format_,
                "targetPath": target_path,
                "sessionId": session_id,
                "logicalRevision": logical_revision,
                "source": source,
                "completeness": completeness,
                "exported": exported,
                "bytes": bytes_,
                "sha256": sha256,
                "omissions": omissions,
                "projectMutated": project_mutated,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.mapping_export_counts import MappingExportCounts  # noqa: PLC0415
        from ..models.mapping_export_receipt_omissions_item import (
            MappingExportReceiptOmissionsItem,  # noqa: PLC0415
        )

        d = dict(src_dict)
        format_ = cast(Literal["TINY_V2"], d.pop("format"))
        if format_ != "TINY_V2":
            raise ValueError(f"format must match const 'TINY_V2', got '{format_}'")

        target_path = d.pop("targetPath")

        session_id = UUID(d.pop("sessionId"))

        logical_revision = d.pop("logicalRevision")

        source = cast(
            Literal["CURRENT_IN_MEMORY_WITH_ATTACHED_MAPPINGS"], d.pop("source")
        )
        if source != "CURRENT_IN_MEMORY_WITH_ATTACHED_MAPPINGS":
            raise ValueError(
                f"source must match const 'CURRENT_IN_MEMORY_WITH_ATTACHED_MAPPINGS', got '{source}'"
            )

        completeness = cast(Literal["VERIFIED_DECLARATIONS"], d.pop("completeness"))
        if completeness != "VERIFIED_DECLARATIONS":
            raise ValueError(
                f"completeness must match const 'VERIFIED_DECLARATIONS', got '{completeness}'"
            )

        exported = MappingExportCounts.from_dict(d.pop("exported"))

        bytes_ = d.pop("bytes")

        sha256 = d.pop("sha256")

        omissions = []
        _omissions = d.pop("omissions")
        for omissions_item_data in _omissions:
            omissions_item = MappingExportReceiptOmissionsItem.from_dict(
                omissions_item_data
            )

            omissions.append(omissions_item)

        project_mutated = d.pop("projectMutated")

        mapping_export_receipt = cls(
            format_=format_,
            target_path=target_path,
            session_id=session_id,
            logical_revision=logical_revision,
            source=source,
            completeness=completeness,
            exported=exported,
            bytes_=bytes_,
            sha256=sha256,
            omissions=omissions,
            project_mutated=project_mutated,
        )

        return mapping_export_receipt
