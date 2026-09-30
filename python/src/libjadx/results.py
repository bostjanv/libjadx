"""Bounded representations with full typed provenance and raw model access."""

from dataclasses import dataclass
from typing import Literal
from uuid import UUID

from ._generated.models import (
    DecompileResult,
    DecompileResultOutcome,
    DecompileResultStatus,
    EffectiveSourceSettings,
    PendingEdits,
    PendingEditsCodeData,
    SourceAnnotation,
    SourceCapabilities,
    SourceRange,
    SourceVariable,
    SymbolInfo,
    SymbolRef,
)
from ._generated.types import Unset


@dataclass(frozen=True, repr=False)
class SourceResult:
    raw: DecompileResult

    def __repr__(self) -> str:
        return f"SourceResult(session_id={self.raw.session_id!r}, logical_revision={self.raw.logical_revision!r})"

    @property
    def outcome(self) -> DecompileResultOutcome:
        return self.raw.outcome

    @property
    def queried_ref(self) -> SymbolRef:
        return self.raw.queried_ref

    @property
    def source_owner_ref(self) -> None | SymbolRef:
        return self.raw.source_owner_ref

    @property
    def candidates(self) -> list[SymbolInfo]:
        return self.raw.candidates

    @property
    def session_id(self) -> UUID:
        return self.raw.session_id

    @property
    def logical_revision(self) -> int:
        return self.raw.logical_revision

    @property
    def source_snapshot_id(self) -> None | str:
        return self.raw.source_snapshot_id

    @property
    def effective_settings(self) -> EffectiveSourceSettings:
        return self.raw.effective_settings

    @property
    def status(self) -> DecompileResultStatus:
        return self.raw.status

    @property
    def representation(self) -> Literal["JAVA"]:
        return self.raw.representation

    @property
    def source(self) -> None | str:
        return self.raw.source

    @property
    def method_range_available(self) -> bool | None:
        return self.raw.method_range_available

    @property
    def method_range(self) -> None | SourceRange:
        return self.raw.method_range

    @property
    def method_source(self) -> None | str:
        return self.raw.method_source

    @property
    def annotations(self) -> list[SourceAnnotation]:
        return self.raw.annotations

    @property
    def variables(self) -> list[SourceVariable]:
        return self.raw.variables

    @property
    def raw_debug_lines(self) -> None:
        return self.raw.raw_debug_lines

    @property
    def class_error_count(self) -> None:
        return self.raw.class_error_count

    @property
    def diagnostics(self) -> list[str]:
        return self.raw.diagnostics

    @property
    def capabilities(self) -> SourceCapabilities:
        return self.raw.capabilities


@dataclass(frozen=True, repr=False)
class PendingEditsResult:
    raw: PendingEdits

    def __repr__(self) -> str:
        return f"PendingEditsResult(session_id={self.raw.session_id!r}, logical_revision={self.raw.logical_revision!r})"

    @property
    def session_id(self) -> str:
        return self.raw.session_id

    @property
    def logical_revision(self) -> int:
        return self.raw.logical_revision

    @property
    def dirty(self) -> bool:
        return self.raw.dirty

    @property
    def code_data(self) -> PendingEditsCodeData:
        return self.raw.code_data

    @property
    def mappings_path(self) -> None | str | Unset:
        return self.raw.mappings_path
