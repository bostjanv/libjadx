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

if TYPE_CHECKING:
    from ..models.method_symbol_ref import MethodSymbolRef


T = TypeVar("T", bound="RenameParameterOperation")


@_attrs_define
class RenameParameterOperation:
    """Snapshot-bound rename of a concrete nonsynthetic ordinary method parameter in AUTO/RESTRUCTURE Java with a verified
    complete unannotated signature. All emitted catch declarations must also have verified variable metadata; methods
    with unannotated catch arguments are unsupported. Zero-based original semantic parameter index excludes this and
    counts wide arguments once. Obtain targets from decompile.variables; display names are not identities. Batch
    expectedSessionId and expectedLogicalRevision are mandatory when this kind is present. Locals remain unsupported.

        Attributes:
            kind (Literal['RENAME_PARAMETER']):
            method (MethodSymbolRef):
            parameter_index (int):
            source_snapshot_id (str):
            new_name (str):
    """

    kind: Literal["RENAME_PARAMETER"]
    method: MethodSymbolRef
    parameter_index: int
    source_snapshot_id: str
    new_name: str

    def to_dict(self) -> dict[str, Any]:
        kind = self.kind

        method = self.method.to_dict()

        parameter_index = self.parameter_index

        source_snapshot_id = self.source_snapshot_id

        new_name = self.new_name

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "kind": kind,
                "method": method,
                "parameterIndex": parameter_index,
                "sourceSnapshotId": source_snapshot_id,
                "newName": new_name,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.method_symbol_ref import MethodSymbolRef  # noqa: PLC0415

        d = dict(src_dict)
        kind = cast(Literal["RENAME_PARAMETER"], d.pop("kind"))
        if kind != "RENAME_PARAMETER":
            raise ValueError(f"kind must match const 'RENAME_PARAMETER', got '{kind}'")

        method = MethodSymbolRef.from_dict(d.pop("method"))

        parameter_index = d.pop("parameterIndex")

        source_snapshot_id = d.pop("sourceSnapshotId")

        new_name = d.pop("newName")

        rename_parameter_operation = cls(
            kind=kind,
            method=method,
            parameter_index=parameter_index,
            source_snapshot_id=source_snapshot_id,
            new_name=new_name,
        )

        return rename_parameter_operation
