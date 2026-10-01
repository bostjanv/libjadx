from __future__ import annotations

from collections.abc import AsyncIterator, Iterator, Sequence
from typing import TYPE_CHECKING, TypedDict, Unpack
from uuid import UUID

from ._common import RevisionCache
from ._generated.api.analysis import build_search_index, decompile_java, search
from ._generated.api.project import (
    apply_edit_batch,
    export_mappings,
    export_pending_edits,
    get_project,
    get_project_settings,
    import_mappings,
    reload_project,
    save_project,
    update_project_settings,
)
from ._generated.api.symbols import list_classes, query_references, resolve_symbol
from ._generated.models import (
    ClassPage,
    DecompileRequest,
    DecompileRequestDecompilationMode,
    DecompileResult,
    EditBatchRequest,
    EditBatchResult,
    FixedProject,
    ListClassesNameDomain,
    MappingExportReceipt,
    MappingExportRequest,
    MappingImportReceipt,
    MappingImportRequest,
    MethodSymbolRef,
    PendingEdits,
    ProjectSettings,
    ReferencePage,
    ReferenceQuery,
    ReferenceQueryDirection,
    ReferenceRelation,
    ReloadProjectRequest,
    RenameOperation,
    RenameParameterOperation,
    SaveProjectRequest,
    SearchBuildRequest,
    SearchIndexStatus,
    SearchPage,
    SearchRequest,
    SearchRequestDomainsItem,
    SearchRequestMatchMode,
    SetCommentOperation,
    SymbolRef,
    SymbolRefKind,
    SymbolResolution,
    SymbolResolveRequest,
    UpdateProjectSettingsRequest,
)
from ._generated.models.job import Job as WireJob
from ._generated.types import UNSET, Unset
from .errors import UnexpectedResponseError
from .jobs import AsyncJob, Job
from .results import PendingEditsResult, SourceResult
from .search import AsyncSearchCursor, SearchCursor
from .symbols import (
    AsyncJavaClass,
    AsyncJavaField,
    AsyncJavaMethod,
    JavaClass,
    JavaField,
    JavaMethod,
    _Symbol,
    reference,
)

if TYPE_CHECKING:
    from .async_client import AsyncClient
    from .client import Client


class Preconditions(TypedDict, total=False):
    expected_session_id: UUID | str | None | Unset
    expected_logical_revision: int | None | Unset


class _ProjectBase:
    def __init__(self) -> None:
        self._revisions = RevisionCache()

    @property
    def session_id(self) -> str | None:
        return self._revisions.session_id

    @property
    def logical_revision(self) -> int | None:
        return self._revisions.logical_revision

    @property
    def index_revision(self) -> int | None:
        return self._revisions.index_revision

    def _expected(
        self, options: Preconditions, required: bool = False
    ) -> tuple[UUID | Unset, int | Unset]:
        return self._revisions.expected(
            options.get("expected_session_id", UNSET),
            options.get("expected_logical_revision", UNSET),
            required=required,
        )

    def _required(self, options: Preconditions) -> tuple[UUID, int]:
        session, revision = self._expected(options, required=True)
        assert isinstance(session, UUID) and isinstance(revision, int)
        return session, revision


class Project(_ProjectBase):
    def __init__(self, client: Client) -> None:
        super().__init__()
        self._client = client

    def snapshot(self) -> FixedProject:
        return self._client._invoke(get_project, FixedProject)

    def refresh(self) -> FixedProject:
        return self.snapshot()

    def settings(self) -> ProjectSettings:
        return self._client._invoke(get_project_settings, ProjectSettings)

    def pending_edits(self) -> PendingEditsResult:
        return PendingEditsResult(
            self._client._invoke(export_pending_edits, PendingEdits)
        )

    def save(
        self, target_path: str | None = None, **options: Unpack[Preconditions]
    ) -> FixedProject:
        session, revision = self._expected(options)
        return self._client._invoke(
            save_project,
            FixedProject,
            body=SaveProjectRequest(
                target_path=target_path if target_path is not None else UNSET,
                expected_session_id=session,
                expected_logical_revision=revision,
            ),
        )

    def reload(
        self, *, discard_unsaved: bool = False, **options: Unpack[Preconditions]
    ) -> FixedProject:
        session, revision = self._required(options)
        return self._client._invoke(
            reload_project,
            FixedProject,
            body=ReloadProjectRequest(discard_unsaved, session, revision),
        )

    def update_settings(
        self, mappings_path: str | None, **options: Unpack[Preconditions]
    ) -> ProjectSettings:
        session, revision = self._required(options)
        return self._client._invoke(
            update_project_settings,
            ProjectSettings,
            body=UpdateProjectSettingsRequest(mappings_path, revision, session),
        )

    def export_mappings(
        self, target_path: str, **options: Unpack[Preconditions]
    ) -> MappingExportReceipt:
        session, revision = self._required(options)
        return self._client._invoke(
            export_mappings,
            MappingExportReceipt,
            body=MappingExportRequest(target_path, "TINY_V2", session, revision),
        )

    def import_mappings(
        self, source_path: str, **options: Unpack[Preconditions]
    ) -> MappingImportReceipt:
        session, revision = self._required(options)
        return self._client._invoke(
            import_mappings,
            MappingImportReceipt,
            body=MappingImportRequest(
                source_path, "TINY_V2", "MERGE_FAIL_ON_CONFLICT", session, revision
            ),
        )

    def apply_edits(
        self,
        items: Sequence[
            RenameOperation | SetCommentOperation | RenameParameterOperation
        ],
        **options: Unpack[Preconditions],
    ) -> EditBatchResult:
        required = any(
            isinstance(item, RenameParameterOperation)
            or isinstance(item, RenameOperation)
            and item.propagate_related is True
            for item in items
        )
        session, revision = self._expected(options, required)
        return self._client._invoke(
            apply_edit_batch,
            EditBatchResult,
            body=EditBatchRequest(list(items), session, revision),
        )

    def rename(
        self,
        target: SymbolRef | _Symbol,
        new_name: str,
        *,
        propagate_related: bool = False,
        **options: Unpack[Preconditions],
    ) -> EditBatchResult:
        ref = reference(target)
        if propagate_related and ref.kind != SymbolRefKind.METHOD:
            raise ValueError("Related propagation requires an original method ref")
        return self.apply_edits(
            [RenameOperation("RENAME", ref, new_name, propagate_related)], **options
        )

    def set_comment(
        self, target: SymbolRef | _Symbol, text: str, **options: Unpack[Preconditions]
    ) -> EditBatchResult:
        return self.apply_edits(
            [SetCommentOperation("SET_COMMENT", reference(target), text, "LINE")],
            **options,
        )

    def rename_parameter(
        self,
        method_ref: SymbolRef,
        parameter_index: int,
        new_name: str,
        *,
        source_snapshot_id: str,
        **options: Unpack[Preconditions],
    ) -> EditBatchResult:
        if method_ref.kind != SymbolRefKind.METHOD:
            raise ValueError("Parameter rename requires a method ref")
        method = MethodSymbolRef.from_dict(method_ref.to_dict())
        return self.apply_edits(
            [
                RenameParameterOperation(
                    "RENAME_PARAMETER",
                    method,
                    parameter_index,
                    source_snapshot_id,
                    new_name,
                )
            ],
            **options,
        )

    def resolve(self, ref: SymbolRef) -> SymbolResolution:
        return self._client._invoke(
            resolve_symbol, SymbolResolution, body=SymbolResolveRequest(ref)
        )

    def resolve_object(
        self, ref: SymbolRef
    ) -> JavaClass | JavaMethod | JavaField | SymbolResolution:
        result = self.resolve(ref)
        if (
            result.outcome == "RESOLVED"
            and not isinstance(result.symbol, Unset)
            and result.symbol is not None
        ):
            if ref.kind == SymbolRefKind.CLASS:
                return JavaClass(self, result.symbol)
            if ref.kind == SymbolRefKind.METHOD:
                return JavaMethod(self, result.symbol)
            return JavaField(self, result.symbol)
        return result

    def class_pages(
        self,
        *,
        package_prefix: str | Unset = UNSET,
        name_contains: str | Unset = UNSET,
        name_domain: ListClassesNameDomain | str = "original",
        include_inner: bool = True,
        page_size: int = 50,
        max_pages: int | None = None,
    ) -> Iterator[ClassPage]:
        if max_pages is not None and max_pages < 1:
            raise ValueError("max_pages must be positive")
        cursor: str | Unset = UNSET
        seen: set[str] = set()
        pages = 0
        while True:
            page = self._client._invoke(
                list_classes,
                ClassPage,
                cursor=cursor,
                package_prefix=package_prefix,
                name_contains=name_contains,
                name_domain=ListClassesNameDomain(name_domain),
                include_inner=include_inner,
                page_size=page_size,
            )
            yield page
            pages += 1
            if (
                page.complete
                or page.next_cursor is None
                or (max_pages is not None and pages >= max_pages)
            ):
                return
            if page.next_cursor in seen:
                raise UnexpectedResponseError("Server repeated a class cursor")
            seen.add(page.next_cursor)
            cursor = page.next_cursor

    def classes(
        self,
        *,
        package_prefix: str | Unset = UNSET,
        name_contains: str | Unset = UNSET,
        name_domain: ListClassesNameDomain | str = "original",
        include_inner: bool = True,
        page_size: int = 50,
        max_pages: int | None = None,
    ) -> Iterator[JavaClass]:
        for page in self.class_pages(
            package_prefix=package_prefix,
            name_contains=name_contains,
            name_domain=name_domain,
            include_inner=include_inner,
            page_size=page_size,
            max_pages=max_pages,
        ):
            for item in page.items:
                yield JavaClass(self, item)

    def decompile(
        self,
        ref: SymbolRef | _Symbol,
        *,
        strict: bool = False,
        decompilation_mode: DecompileRequestDecompilationMode | str | Unset = UNSET,
        include_annotations: bool = True,
        include_raw_debug_lines: bool = False,
    ) -> SourceResult:
        mode = (
            UNSET
            if isinstance(decompilation_mode, Unset)
            else DecompileRequestDecompilationMode(decompilation_mode)
        )
        return SourceResult(
            self._client._invoke(
                decompile_java,
                DecompileResult,
                body=DecompileRequest(
                    reference(ref),
                    decompilation_mode=mode,
                    strict=strict,
                    include_annotations=include_annotations,
                    include_raw_debug_lines=include_raw_debug_lines,
                ),
            )
        )

    def references(
        self,
        ref: SymbolRef | _Symbol,
        *,
        direction: ReferenceQueryDirection | str = "INCOMING",
        relations: Sequence[ReferenceRelation | str] | None = None,
        page_size: int = 50,
        cursor: str | None = None,
        include_source_sites: bool = False,
        strict: bool = False,
    ) -> ReferencePage:
        query = ReferenceQuery(
            reference(ref),
            ReferenceQueryDirection(direction),
            relations=UNSET
            if relations is None
            else [ReferenceRelation(r) for r in relations],
            page_size=page_size,
            cursor=cursor,
            include_source_sites=include_source_sites,
            strict=strict,
        )
        return self._client._invoke(query_references, ReferencePage, body=query)

    def reference_pages(self, query: ReferenceQuery) -> Iterator[ReferencePage]:
        # Clone caller-owned request before advancing its cursor.
        request = ReferenceQuery.from_dict(query.to_dict())
        seen: set[str] = set()
        while True:
            page = self._client._invoke(query_references, ReferencePage, body=request)
            yield page
            if page.page_complete or page.next_cursor is None:
                return
            if page.next_cursor in seen:
                raise UnexpectedResponseError("Server repeated a reference cursor")
            seen.add(page.next_cursor)
            request.cursor = page.next_cursor

    def search(
        self,
        query: str,
        *,
        domains: Sequence[SearchRequestDomainsItem | str] | None = None,
        match_mode: SearchRequestMatchMode | str = "CONTAINS",
        case_sensitive: bool = True,
        page_size: int = 50,
        strict: bool = False,
        require_complete: bool = False,
    ) -> SearchCursor | Job:
        request = SearchRequest(
            query,
            domains=UNSET
            if domains is None
            else [SearchRequestDomainsItem(d) for d in domains],
            match_mode=SearchRequestMatchMode(match_mode),
            case_sensitive=case_sensitive,
            page_size=page_size,
            strict=strict,
            require_complete=require_complete,
        )
        result = self._client._invoke(search, object, body=request)
        if isinstance(result, WireJob):
            return Job(self._client, result)
        if isinstance(result, SearchPage):
            return SearchCursor(self, request, result)
        raise UnexpectedResponseError("Expected SearchPage or Job")

    def build_search_index(
        self, request: SearchBuildRequest | None = None
    ) -> SearchIndexStatus | Job:
        result = self._client._invoke(
            build_search_index,
            object,
            body=request if request is not None else SearchBuildRequest(),
        )
        if isinstance(result, WireJob):
            return Job(self._client, result)
        if isinstance(result, SearchIndexStatus):
            return result
        raise UnexpectedResponseError("Expected SearchIndexStatus or Job")


class AsyncProject(_ProjectBase):
    def __init__(self, client: AsyncClient) -> None:
        super().__init__()
        self._client = client

    async def snapshot(self) -> FixedProject:
        return await self._client._invoke(get_project, FixedProject)

    async def refresh(self) -> FixedProject:
        return await self.snapshot()

    async def settings(self) -> ProjectSettings:
        return await self._client._invoke(get_project_settings, ProjectSettings)

    async def pending_edits(self) -> PendingEditsResult:
        return PendingEditsResult(
            await self._client._invoke(export_pending_edits, PendingEdits)
        )

    async def save(
        self, target_path: str | None = None, **options: Unpack[Preconditions]
    ) -> FixedProject:
        session, revision = self._expected(options)
        return await self._client._invoke(
            save_project,
            FixedProject,
            body=SaveProjectRequest(
                target_path=target_path if target_path is not None else UNSET,
                expected_session_id=session,
                expected_logical_revision=revision,
            ),
        )

    async def reload(
        self, *, discard_unsaved: bool = False, **options: Unpack[Preconditions]
    ) -> FixedProject:
        session, revision = self._required(options)
        return await self._client._invoke(
            reload_project,
            FixedProject,
            body=ReloadProjectRequest(discard_unsaved, session, revision),
        )

    async def update_settings(
        self, mappings_path: str | None, **options: Unpack[Preconditions]
    ) -> ProjectSettings:
        session, revision = self._required(options)
        return await self._client._invoke(
            update_project_settings,
            ProjectSettings,
            body=UpdateProjectSettingsRequest(mappings_path, revision, session),
        )

    async def export_mappings(
        self, target_path: str, **options: Unpack[Preconditions]
    ) -> MappingExportReceipt:
        session, revision = self._required(options)
        return await self._client._invoke(
            export_mappings,
            MappingExportReceipt,
            body=MappingExportRequest(target_path, "TINY_V2", session, revision),
        )

    async def import_mappings(
        self, source_path: str, **options: Unpack[Preconditions]
    ) -> MappingImportReceipt:
        session, revision = self._required(options)
        return await self._client._invoke(
            import_mappings,
            MappingImportReceipt,
            body=MappingImportRequest(
                source_path, "TINY_V2", "MERGE_FAIL_ON_CONFLICT", session, revision
            ),
        )

    async def apply_edits(
        self,
        items: Sequence[
            RenameOperation | SetCommentOperation | RenameParameterOperation
        ],
        **options: Unpack[Preconditions],
    ) -> EditBatchResult:
        required = any(
            isinstance(item, RenameParameterOperation)
            or isinstance(item, RenameOperation)
            and item.propagate_related is True
            for item in items
        )
        session, revision = self._expected(options, required)
        return await self._client._invoke(
            apply_edit_batch,
            EditBatchResult,
            body=EditBatchRequest(list(items), session, revision),
        )

    async def rename(
        self,
        target: SymbolRef | _Symbol,
        new_name: str,
        *,
        propagate_related: bool = False,
        **options: Unpack[Preconditions],
    ) -> EditBatchResult:
        ref = reference(target)
        if propagate_related and ref.kind != SymbolRefKind.METHOD:
            raise ValueError("Related propagation requires an original method ref")
        return await self.apply_edits(
            [RenameOperation("RENAME", ref, new_name, propagate_related)], **options
        )

    async def set_comment(
        self, target: SymbolRef | _Symbol, text: str, **options: Unpack[Preconditions]
    ) -> EditBatchResult:
        return await self.apply_edits(
            [SetCommentOperation("SET_COMMENT", reference(target), text, "LINE")],
            **options,
        )

    async def rename_parameter(
        self,
        method_ref: SymbolRef,
        parameter_index: int,
        new_name: str,
        *,
        source_snapshot_id: str,
        **options: Unpack[Preconditions],
    ) -> EditBatchResult:
        if method_ref.kind != SymbolRefKind.METHOD:
            raise ValueError("Parameter rename requires a method ref")
        method = MethodSymbolRef.from_dict(method_ref.to_dict())
        return await self.apply_edits(
            [
                RenameParameterOperation(
                    "RENAME_PARAMETER",
                    method,
                    parameter_index,
                    source_snapshot_id,
                    new_name,
                )
            ],
            **options,
        )

    async def resolve(self, ref: SymbolRef) -> SymbolResolution:
        return await self._client._invoke(
            resolve_symbol, SymbolResolution, body=SymbolResolveRequest(ref)
        )

    async def resolve_object(
        self, ref: SymbolRef
    ) -> AsyncJavaClass | AsyncJavaMethod | AsyncJavaField | SymbolResolution:
        result = await self.resolve(ref)
        if (
            result.outcome == "RESOLVED"
            and not isinstance(result.symbol, Unset)
            and result.symbol is not None
        ):
            if ref.kind == SymbolRefKind.CLASS:
                return AsyncJavaClass(self, result.symbol)
            if ref.kind == SymbolRefKind.METHOD:
                return AsyncJavaMethod(self, result.symbol)
            return AsyncJavaField(self, result.symbol)
        return result

    async def class_pages(
        self,
        *,
        package_prefix: str | Unset = UNSET,
        name_contains: str | Unset = UNSET,
        name_domain: ListClassesNameDomain | str = "original",
        include_inner: bool = True,
        page_size: int = 50,
        max_pages: int | None = None,
    ) -> AsyncIterator[ClassPage]:
        if max_pages is not None and max_pages < 1:
            raise ValueError("max_pages must be positive")
        cursor: str | Unset = UNSET
        seen: set[str] = set()
        pages = 0
        while True:
            page = await self._client._invoke(
                list_classes,
                ClassPage,
                cursor=cursor,
                package_prefix=package_prefix,
                name_contains=name_contains,
                name_domain=ListClassesNameDomain(name_domain),
                include_inner=include_inner,
                page_size=page_size,
            )
            yield page
            pages += 1
            if (
                page.complete
                or page.next_cursor is None
                or (max_pages is not None and pages >= max_pages)
            ):
                return
            if page.next_cursor in seen:
                raise UnexpectedResponseError("Server repeated a class cursor")
            seen.add(page.next_cursor)
            cursor = page.next_cursor

    async def classes(
        self,
        *,
        package_prefix: str | Unset = UNSET,
        name_contains: str | Unset = UNSET,
        name_domain: ListClassesNameDomain | str = "original",
        include_inner: bool = True,
        page_size: int = 50,
        max_pages: int | None = None,
    ) -> AsyncIterator[AsyncJavaClass]:
        async for page in self.class_pages(
            package_prefix=package_prefix,
            name_contains=name_contains,
            name_domain=name_domain,
            include_inner=include_inner,
            page_size=page_size,
            max_pages=max_pages,
        ):
            for item in page.items:
                yield AsyncJavaClass(self, item)

    async def decompile(
        self,
        ref: SymbolRef | _Symbol,
        *,
        strict: bool = False,
        decompilation_mode: DecompileRequestDecompilationMode | str | Unset = UNSET,
        include_annotations: bool = True,
        include_raw_debug_lines: bool = False,
    ) -> SourceResult:
        mode = (
            UNSET
            if isinstance(decompilation_mode, Unset)
            else DecompileRequestDecompilationMode(decompilation_mode)
        )
        return SourceResult(
            await self._client._invoke(
                decompile_java,
                DecompileResult,
                body=DecompileRequest(
                    reference(ref),
                    decompilation_mode=mode,
                    strict=strict,
                    include_annotations=include_annotations,
                    include_raw_debug_lines=include_raw_debug_lines,
                ),
            )
        )

    async def references(
        self,
        ref: SymbolRef | _Symbol,
        *,
        direction: ReferenceQueryDirection | str = "INCOMING",
        relations: Sequence[ReferenceRelation | str] | None = None,
        page_size: int = 50,
        cursor: str | None = None,
        include_source_sites: bool = False,
        strict: bool = False,
    ) -> ReferencePage:
        query = ReferenceQuery(
            reference(ref),
            ReferenceQueryDirection(direction),
            relations=UNSET
            if relations is None
            else [ReferenceRelation(r) for r in relations],
            page_size=page_size,
            cursor=cursor,
            include_source_sites=include_source_sites,
            strict=strict,
        )
        return await self._client._invoke(query_references, ReferencePage, body=query)

    async def reference_pages(
        self, query: ReferenceQuery
    ) -> AsyncIterator[ReferencePage]:
        # Clone caller-owned request before advancing its cursor.
        request = ReferenceQuery.from_dict(query.to_dict())
        seen: set[str] = set()
        while True:
            page = await self._client._invoke(
                query_references, ReferencePage, body=request
            )
            yield page
            if page.page_complete or page.next_cursor is None:
                return
            if page.next_cursor in seen:
                raise UnexpectedResponseError("Server repeated a reference cursor")
            seen.add(page.next_cursor)
            request.cursor = page.next_cursor

    async def search(
        self,
        query: str,
        *,
        domains: Sequence[SearchRequestDomainsItem | str] | None = None,
        match_mode: SearchRequestMatchMode | str = "CONTAINS",
        case_sensitive: bool = True,
        page_size: int = 50,
        strict: bool = False,
        require_complete: bool = False,
    ) -> AsyncSearchCursor | AsyncJob:
        request = SearchRequest(
            query,
            domains=UNSET
            if domains is None
            else [SearchRequestDomainsItem(d) for d in domains],
            match_mode=SearchRequestMatchMode(match_mode),
            case_sensitive=case_sensitive,
            page_size=page_size,
            strict=strict,
            require_complete=require_complete,
        )
        result = await self._client._invoke(search, object, body=request)
        if isinstance(result, WireJob):
            return AsyncJob(self._client, result)
        if isinstance(result, SearchPage):
            return AsyncSearchCursor(self, request, result)
        raise UnexpectedResponseError("Expected SearchPage or Job")

    async def build_search_index(
        self, request: SearchBuildRequest | None = None
    ) -> SearchIndexStatus | AsyncJob:
        result = await self._client._invoke(
            build_search_index,
            object,
            body=request if request is not None else SearchBuildRequest(),
        )
        if isinstance(result, WireJob):
            return AsyncJob(self._client, result)
        if isinstance(result, SearchIndexStatus):
            return result
        raise UnexpectedResponseError("Expected SearchIndexStatus or Job")
