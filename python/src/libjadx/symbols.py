from __future__ import annotations

from typing import TYPE_CHECKING, Unpack

from ._generated.models.class_info import ClassInfo
from ._generated.models.edit_batch_result import EditBatchResult
from ._generated.models.reference_page import ReferencePage
from ._generated.models.reference_query_direction import ReferenceQueryDirection
from ._generated.models.symbol_info import SymbolInfo
from ._generated.models.symbol_ref import SymbolRef as WireRef
from ._generated.models.symbol_ref_kind import SymbolRefKind
from .results import SourceResult

if TYPE_CHECKING:
    from .project import AsyncProject, Preconditions, Project


class SymbolRef:
    """Construct original identities; helpers return the generated wire model."""

    @staticmethod
    def class_(descriptor: str, *, input_identity: str | None = None) -> WireRef:
        return WireRef(
            SymbolRefKind.CLASS,
            descriptor,
            input_identity=input_identity,
            original_name=None,
            original_descriptor=None,
        )

    @staticmethod
    def method(
        owner: str, name: str, descriptor: str, *, input_identity: str | None = None
    ) -> WireRef:
        return WireRef(SymbolRefKind.METHOD, owner, input_identity, name, descriptor)

    @staticmethod
    def field(
        owner: str, name: str, descriptor: str, *, input_identity: str | None = None
    ) -> WireRef:
        return WireRef(SymbolRefKind.FIELD, owner, input_identity, name, descriptor)


class _Symbol:
    def __init__(self, raw: ClassInfo | SymbolInfo) -> None:
        self.raw = raw
        self.ref = raw.ref

    @property
    def display_name(self) -> str:
        return self.raw.display_name

    @property
    def original_name(self) -> str | None:
        if isinstance(self.raw, ClassInfo):
            return self.raw.original_dotted_name
        return self.raw.original_name

    def __repr__(self) -> str:
        return f"{type(self).__name__}(ref={self.ref.to_dict()!r}, display_name={self.display_name!r})"


class _BoundSymbol(_Symbol):
    def __init__(self, project: Project, raw: ClassInfo | SymbolInfo) -> None:
        super().__init__(raw)
        self._project = project

    def rename(
        self,
        new_name: str,
        *,
        propagate_related: bool = False,
        **options: Unpack[Preconditions],
    ) -> EditBatchResult:
        return self._project.rename(
            self.ref, new_name, propagate_related=propagate_related, **options
        )

    def references(
        self,
        *,
        direction: ReferenceQueryDirection | str = "INCOMING",
        strict: bool = False,
        include_source_sites: bool = False,
    ) -> ReferencePage:
        return self._project.references(
            self.ref,
            direction=direction,
            strict=strict,
            include_source_sites=include_source_sites,
        )


class JavaClass(_BoundSymbol):
    def decompile(self, *, strict: bool = False) -> SourceResult:
        return self._project.decompile(self.ref, strict=strict)


class JavaMethod(_BoundSymbol):
    def decompile(self, *, strict: bool = False) -> SourceResult:
        return self._project.decompile(self.ref, strict=strict)


class JavaField(_BoundSymbol):
    pass


class _AsyncBoundSymbol(_Symbol):
    def __init__(self, project: AsyncProject, raw: ClassInfo | SymbolInfo) -> None:
        super().__init__(raw)
        self._project = project

    async def rename(
        self,
        new_name: str,
        *,
        propagate_related: bool = False,
        **options: Unpack[Preconditions],
    ) -> EditBatchResult:
        return await self._project.rename(
            self.ref, new_name, propagate_related=propagate_related, **options
        )

    async def references(
        self,
        *,
        direction: ReferenceQueryDirection | str = "INCOMING",
        strict: bool = False,
        include_source_sites: bool = False,
    ) -> ReferencePage:
        return await self._project.references(
            self.ref,
            direction=direction,
            strict=strict,
            include_source_sites=include_source_sites,
        )


class AsyncJavaClass(_AsyncBoundSymbol):
    async def decompile(self, *, strict: bool = False) -> SourceResult:
        return await self._project.decompile(self.ref, strict=strict)


class AsyncJavaMethod(_AsyncBoundSymbol):
    async def decompile(self, *, strict: bool = False) -> SourceResult:
        return await self._project.decompile(self.ref, strict=strict)


class AsyncJavaField(_AsyncBoundSymbol):
    pass


def reference(value: WireRef | _Symbol) -> WireRef:
    return value.ref if isinstance(value, _Symbol) else value
