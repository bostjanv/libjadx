from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar

from attrs import define as _attrs_define

from ..models.source_availability import SourceAvailability

T = TypeVar("T", bound="SourceCapabilities")


@_attrs_define
class SourceCapabilities:
    """
    Attributes:
        source (SourceAvailability):
        declaration_positions (SourceAvailability):
        reference_targets (SourceAvailability):
        method_range (SourceAvailability):
        raw_debug_lines (SourceAvailability):
        original_bytecode_offsets (SourceAvailability):
        class_errors (SourceAvailability):
    """

    source: SourceAvailability
    declaration_positions: SourceAvailability
    reference_targets: SourceAvailability
    method_range: SourceAvailability
    raw_debug_lines: SourceAvailability
    original_bytecode_offsets: SourceAvailability
    class_errors: SourceAvailability

    def to_dict(self) -> dict[str, Any]:
        source = self.source.value

        declaration_positions = self.declaration_positions.value

        reference_targets = self.reference_targets.value

        method_range = self.method_range.value

        raw_debug_lines = self.raw_debug_lines.value

        original_bytecode_offsets = self.original_bytecode_offsets.value

        class_errors = self.class_errors.value

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "source": source,
                "declarationPositions": declaration_positions,
                "referenceTargets": reference_targets,
                "methodRange": method_range,
                "rawDebugLines": raw_debug_lines,
                "originalBytecodeOffsets": original_bytecode_offsets,
                "classErrors": class_errors,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        source = SourceAvailability(d.pop("source"))

        declaration_positions = SourceAvailability(d.pop("declarationPositions"))

        reference_targets = SourceAvailability(d.pop("referenceTargets"))

        method_range = SourceAvailability(d.pop("methodRange"))

        raw_debug_lines = SourceAvailability(d.pop("rawDebugLines"))

        original_bytecode_offsets = SourceAvailability(d.pop("originalBytecodeOffsets"))

        class_errors = SourceAvailability(d.pop("classErrors"))

        source_capabilities = cls(
            source=source,
            declaration_positions=declaration_positions,
            reference_targets=reference_targets,
            method_range=method_range,
            raw_debug_lines=raw_debug_lines,
            original_bytecode_offsets=original_bytecode_offsets,
            class_errors=class_errors,
        )

        return source_capabilities
