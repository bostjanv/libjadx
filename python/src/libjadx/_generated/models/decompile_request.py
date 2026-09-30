from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar
from uuid import UUID

from attrs import define as _attrs_define

from ..models.decompile_request_decompilation_mode import (
    DecompileRequestDecompilationMode,
)
from ..types import UNSET, Unset

if TYPE_CHECKING:
    from ..models.symbol_ref import SymbolRef


T = TypeVar("T", bound="DecompileRequest")


@_attrs_define
class DecompileRequest:
    """Only CLASS and METHOD refs are accepted. Revision preconditions must occur together. Only JAVA is currently
    supported; other representations return 422 UNSUPPORTED_CAPABILITY. Requests are limited to 64 KiB.

        Attributes:
            ref (SymbolRef): Exact original JVM/DEX identity. CLASS omits member fields or carries null; METHOD and FIELD
                require non-null values for both. Class names use Lpackage/Top$Inner; with nonempty slash-delimited segments, no
                dots or array prefix. Method descriptors contain zero or more nonvoid field types in parentheses and one return
                type, which may be V. Field descriptors contain exactly one nonvoid type. No whitespace, generic syntax or
                normalization is accepted.
            representation (str | Unset):  Default: 'JAVA'.
            decompilation_mode (DecompileRequestDecompilationMode | Unset):
            include_annotations (bool | Unset):  Default: True.
            include_raw_debug_lines (bool | Unset):  Default: False.
            strict (bool | Unset):  Default: False.
            expected_session_id (UUID | Unset):
            expected_logical_revision (int | Unset):
            expected_source_snapshot_id (str | Unset):
    """

    ref: SymbolRef
    representation: str | Unset = "JAVA"
    decompilation_mode: DecompileRequestDecompilationMode | Unset = UNSET
    include_annotations: bool | Unset = True
    include_raw_debug_lines: bool | Unset = False
    strict: bool | Unset = False
    expected_session_id: UUID | Unset = UNSET
    expected_logical_revision: int | Unset = UNSET
    expected_source_snapshot_id: str | Unset = UNSET

    def to_dict(self) -> dict[str, Any]:
        ref = self.ref.to_dict()

        representation = self.representation

        decompilation_mode: str | Unset = UNSET
        if not isinstance(self.decompilation_mode, Unset):
            decompilation_mode = self.decompilation_mode.value

        include_annotations = self.include_annotations

        include_raw_debug_lines = self.include_raw_debug_lines

        strict = self.strict

        expected_session_id: str | Unset = UNSET
        if not isinstance(self.expected_session_id, Unset):
            expected_session_id = str(self.expected_session_id)

        expected_logical_revision = self.expected_logical_revision

        expected_source_snapshot_id = self.expected_source_snapshot_id

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "ref": ref,
            }
        )
        if representation is not UNSET:
            field_dict["representation"] = representation
        if decompilation_mode is not UNSET:
            field_dict["decompilationMode"] = decompilation_mode
        if include_annotations is not UNSET:
            field_dict["includeAnnotations"] = include_annotations
        if include_raw_debug_lines is not UNSET:
            field_dict["includeRawDebugLines"] = include_raw_debug_lines
        if strict is not UNSET:
            field_dict["strict"] = strict
        if expected_session_id is not UNSET:
            field_dict["expectedSessionId"] = expected_session_id
        if expected_logical_revision is not UNSET:
            field_dict["expectedLogicalRevision"] = expected_logical_revision
        if expected_source_snapshot_id is not UNSET:
            field_dict["expectedSourceSnapshotId"] = expected_source_snapshot_id

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.symbol_ref import SymbolRef  # noqa: PLC0415

        d = dict(src_dict)
        ref = SymbolRef.from_dict(d.pop("ref"))

        representation = d.pop("representation", UNSET)

        _decompilation_mode = d.pop("decompilationMode", UNSET)
        decompilation_mode: DecompileRequestDecompilationMode | Unset
        if isinstance(_decompilation_mode, Unset):
            decompilation_mode = UNSET
        else:
            decompilation_mode = DecompileRequestDecompilationMode(_decompilation_mode)

        include_annotations = d.pop("includeAnnotations", UNSET)

        include_raw_debug_lines = d.pop("includeRawDebugLines", UNSET)

        strict = d.pop("strict", UNSET)

        _expected_session_id = d.pop("expectedSessionId", UNSET)
        expected_session_id: UUID | Unset
        if isinstance(_expected_session_id, Unset):
            expected_session_id = UNSET
        else:
            expected_session_id = UUID(_expected_session_id)

        expected_logical_revision = d.pop("expectedLogicalRevision", UNSET)

        expected_source_snapshot_id = d.pop("expectedSourceSnapshotId", UNSET)

        decompile_request = cls(
            ref=ref,
            representation=representation,
            decompilation_mode=decompilation_mode,
            include_annotations=include_annotations,
            include_raw_debug_lines=include_raw_debug_lines,
            strict=strict,
            expected_session_id=expected_session_id,
            expected_logical_revision=expected_logical_revision,
            expected_source_snapshot_id=expected_source_snapshot_id,
        )

        return decompile_request
