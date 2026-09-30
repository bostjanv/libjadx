from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar, cast

from attrs import define as _attrs_define

from ..models.source_variable_kind import SourceVariableKind
from ..models.source_variable_persistability import SourceVariablePersistability

if TYPE_CHECKING:
    from ..models.method_symbol_ref import MethodSymbolRef
    from ..models.source_range import SourceRange


T = TypeVar("T", bound="SourceVariable")


@_attrs_define
class SourceVariable:
    """Exact declaration token from this emitted source snapshot. This is partial discovery, not an original-variable
    census. LOCAL entries are read-only and have null parameterIndex; unsupported positional identities also use null.
    No stable local ID is offered. Scope collision validation conservatively treats all declared variables in a method
    as overlapping. Parameters in methods with unverified catch declarations are UNSUPPORTED with null parameterIndex.
    No register or SSA identity is part of this contract.

        Attributes:
            kind (SourceVariableKind):
            method (MethodSymbolRef):
            parameter_index (int | None):
            display_name (str):
            range_ (SourceRange): Half-open range in the exact returned Java string.
            source_snapshot_id (str):
            persistability (SourceVariablePersistability):
    """

    kind: SourceVariableKind
    method: MethodSymbolRef
    parameter_index: int | None
    display_name: str
    range_: SourceRange
    source_snapshot_id: str
    persistability: SourceVariablePersistability

    def to_dict(self) -> dict[str, Any]:
        kind = self.kind.value

        method = self.method.to_dict()

        parameter_index: int | None
        parameter_index = self.parameter_index

        display_name = self.display_name

        range_ = self.range_.to_dict()

        source_snapshot_id = self.source_snapshot_id

        persistability = self.persistability.value

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "kind": kind,
                "method": method,
                "parameterIndex": parameter_index,
                "displayName": display_name,
                "range": range_,
                "sourceSnapshotId": source_snapshot_id,
                "persistability": persistability,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.method_symbol_ref import MethodSymbolRef  # noqa: PLC0415
        from ..models.source_range import SourceRange  # noqa: PLC0415

        d = dict(src_dict)
        kind = SourceVariableKind(d.pop("kind"))

        method = MethodSymbolRef.from_dict(d.pop("method"))

        def _parse_parameter_index(data: object) -> int | None:
            if data is None:
                return data
            return cast(int | None, data)

        parameter_index = _parse_parameter_index(d.pop("parameterIndex"))

        display_name = d.pop("displayName")

        range_ = SourceRange.from_dict(d.pop("range"))

        source_snapshot_id = d.pop("sourceSnapshotId")

        persistability = SourceVariablePersistability(d.pop("persistability"))

        source_variable = cls(
            kind=kind,
            method=method,
            parameter_index=parameter_index,
            display_name=display_name,
            range_=range_,
            source_snapshot_id=source_snapshot_id,
            persistability=persistability,
        )

        return source_variable
