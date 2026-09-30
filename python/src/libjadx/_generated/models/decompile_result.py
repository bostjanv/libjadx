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

from ..models.decompile_result_outcome import DecompileResultOutcome
from ..models.decompile_result_status import DecompileResultStatus

if TYPE_CHECKING:
    from ..models.effective_source_settings import EffectiveSourceSettings
    from ..models.source_annotation import SourceAnnotation
    from ..models.source_capabilities import SourceCapabilities
    from ..models.source_range import SourceRange
    from ..models.source_variable import SourceVariable
    from ..models.symbol_info import SymbolInfo
    from ..models.symbol_ref import SymbolRef


T = TypeVar("T", bound="DecompileResult")


@_attrs_define
class DecompileResult:
    """
    Attributes:
        outcome (DecompileResultOutcome):
        queried_ref (SymbolRef): Exact original JVM/DEX identity. CLASS omits member fields or carries null; METHOD and
            FIELD require non-null values for both. Class names use Lpackage/Top$Inner; with nonempty slash-delimited
            segments, no dots or array prefix. Method descriptors contain zero or more nonvoid field types in parentheses
            and one return type, which may be V. Field descriptors contain exactly one nonvoid type. No whitespace, generic
            syntax or normalization is accepted.
        source_owner_ref (None | SymbolRef):
        candidates (list[SymbolInfo]):
        session_id (UUID):
        logical_revision (int):
        source_snapshot_id (None | str):
        effective_settings (EffectiveSourceSettings):
        status (DecompileResultStatus):
        representation (Literal['JAVA']):
        source (None | str): Exact Jadx getCodeStr text
        method_range_available (bool | None):
        method_range (None | SourceRange):
        method_source (None | str):
        annotations (list[SourceAnnotation]):
        variables (list[SourceVariable]):
        raw_debug_lines (None): Original debug line mapping is unproved in Jadx 1.5.6.
        class_error_count (None): A class-local error count is not available from verified public APIs.
        diagnostics (list[str]):
        capabilities (SourceCapabilities):
    """

    outcome: DecompileResultOutcome
    queried_ref: SymbolRef
    source_owner_ref: None | SymbolRef
    candidates: list[SymbolInfo]
    session_id: UUID
    logical_revision: int
    source_snapshot_id: None | str
    effective_settings: EffectiveSourceSettings
    status: DecompileResultStatus
    representation: Literal["JAVA"]
    source: None | str
    method_range_available: bool | None
    method_range: None | SourceRange
    method_source: None | str
    annotations: list[SourceAnnotation]
    variables: list[SourceVariable]
    raw_debug_lines: None
    class_error_count: None
    diagnostics: list[str]
    capabilities: SourceCapabilities

    def to_dict(self) -> dict[str, Any]:
        from ..models.source_range import SourceRange  # noqa: PLC0415
        from ..models.symbol_ref import SymbolRef  # noqa: PLC0415

        outcome = self.outcome.value

        queried_ref = self.queried_ref.to_dict()

        source_owner_ref: dict[str, Any] | None
        if isinstance(self.source_owner_ref, SymbolRef):
            source_owner_ref = self.source_owner_ref.to_dict()
        else:
            source_owner_ref = self.source_owner_ref

        candidates = []
        for candidates_item_data in self.candidates:
            candidates_item = candidates_item_data.to_dict()
            candidates.append(candidates_item)

        session_id = str(self.session_id)

        logical_revision = self.logical_revision

        source_snapshot_id: None | str
        source_snapshot_id = self.source_snapshot_id

        effective_settings = self.effective_settings.to_dict()

        status = self.status.value

        representation = self.representation

        source: None | str
        source = self.source

        method_range_available: bool | None
        method_range_available = self.method_range_available

        method_range: dict[str, Any] | None
        if isinstance(self.method_range, SourceRange):
            method_range = self.method_range.to_dict()
        else:
            method_range = self.method_range

        method_source: None | str
        method_source = self.method_source

        annotations = []
        for annotations_item_data in self.annotations:
            annotations_item = annotations_item_data.to_dict()
            annotations.append(annotations_item)

        variables = []
        for variables_item_data in self.variables:
            variables_item = variables_item_data.to_dict()
            variables.append(variables_item)

        raw_debug_lines = self.raw_debug_lines

        class_error_count = self.class_error_count

        diagnostics = self.diagnostics

        capabilities = self.capabilities.to_dict()

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "outcome": outcome,
                "queriedRef": queried_ref,
                "sourceOwnerRef": source_owner_ref,
                "candidates": candidates,
                "sessionId": session_id,
                "logicalRevision": logical_revision,
                "sourceSnapshotId": source_snapshot_id,
                "effectiveSettings": effective_settings,
                "status": status,
                "representation": representation,
                "source": source,
                "methodRangeAvailable": method_range_available,
                "methodRange": method_range,
                "methodSource": method_source,
                "annotations": annotations,
                "variables": variables,
                "rawDebugLines": raw_debug_lines,
                "classErrorCount": class_error_count,
                "diagnostics": diagnostics,
                "capabilities": capabilities,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.effective_source_settings import (
            EffectiveSourceSettings,  # noqa: PLC0415
        )
        from ..models.source_annotation import SourceAnnotation  # noqa: PLC0415
        from ..models.source_capabilities import SourceCapabilities  # noqa: PLC0415
        from ..models.source_range import SourceRange  # noqa: PLC0415
        from ..models.source_variable import SourceVariable  # noqa: PLC0415
        from ..models.symbol_info import SymbolInfo  # noqa: PLC0415
        from ..models.symbol_ref import SymbolRef  # noqa: PLC0415

        d = dict(src_dict)
        outcome = DecompileResultOutcome(d.pop("outcome"))

        queried_ref = SymbolRef.from_dict(d.pop("queriedRef"))

        def _parse_source_owner_ref(data: object) -> None | SymbolRef:
            if data is None:
                return data
            try:
                if not isinstance(data, dict):
                    raise TypeError()
                source_owner_ref_type_0 = SymbolRef.from_dict(data)

                return source_owner_ref_type_0
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            return cast(None | SymbolRef, data)

        source_owner_ref = _parse_source_owner_ref(d.pop("sourceOwnerRef"))

        candidates = []
        _candidates = d.pop("candidates")
        for candidates_item_data in _candidates:
            candidates_item = SymbolInfo.from_dict(candidates_item_data)

            candidates.append(candidates_item)

        session_id = UUID(d.pop("sessionId"))

        logical_revision = d.pop("logicalRevision")

        def _parse_source_snapshot_id(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        source_snapshot_id = _parse_source_snapshot_id(d.pop("sourceSnapshotId"))

        effective_settings = EffectiveSourceSettings.from_dict(
            d.pop("effectiveSettings")
        )

        status = DecompileResultStatus(d.pop("status"))

        representation = cast(Literal["JAVA"], d.pop("representation"))
        if representation != "JAVA":
            raise ValueError(
                f"representation must match const 'JAVA', got '{representation}'"
            )

        def _parse_source(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        source = _parse_source(d.pop("source"))

        def _parse_method_range_available(data: object) -> bool | None:
            if data is None:
                return data
            return cast(bool | None, data)

        method_range_available = _parse_method_range_available(
            d.pop("methodRangeAvailable")
        )

        def _parse_method_range(data: object) -> None | SourceRange:
            if data is None:
                return data
            try:
                if not isinstance(data, dict):
                    raise TypeError()
                method_range_type_0 = SourceRange.from_dict(data)

                return method_range_type_0
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            return cast(None | SourceRange, data)

        method_range = _parse_method_range(d.pop("methodRange"))

        def _parse_method_source(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        method_source = _parse_method_source(d.pop("methodSource"))

        annotations = []
        _annotations = d.pop("annotations")
        for annotations_item_data in _annotations:
            annotations_item = SourceAnnotation.from_dict(annotations_item_data)

            annotations.append(annotations_item)

        variables = []
        _variables = d.pop("variables")
        for variables_item_data in _variables:
            variables_item = SourceVariable.from_dict(variables_item_data)

            variables.append(variables_item)

        raw_debug_lines = d.pop("rawDebugLines")

        class_error_count = d.pop("classErrorCount")

        diagnostics = cast(list[str], d.pop("diagnostics"))

        capabilities = SourceCapabilities.from_dict(d.pop("capabilities"))

        decompile_result = cls(
            outcome=outcome,
            queried_ref=queried_ref,
            source_owner_ref=source_owner_ref,
            candidates=candidates,
            session_id=session_id,
            logical_revision=logical_revision,
            source_snapshot_id=source_snapshot_id,
            effective_settings=effective_settings,
            status=status,
            representation=representation,
            source=source,
            method_range_available=method_range_available,
            method_range=method_range,
            method_source=method_source,
            annotations=annotations,
            variables=variables,
            raw_debug_lines=raw_debug_lines,
            class_error_count=class_error_count,
            diagnostics=diagnostics,
            capabilities=capabilities,
        )

        return decompile_result
