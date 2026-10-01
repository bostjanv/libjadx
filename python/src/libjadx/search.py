from __future__ import annotations

from collections.abc import AsyncIterator, Iterator
from typing import TYPE_CHECKING

from ._generated.api.analysis import search
from ._generated.models.search_coverage import SearchCoverage
from ._generated.models.search_hit import SearchHit
from ._generated.models.search_page import SearchPage
from ._generated.models.search_request import SearchRequest
from .errors import UnexpectedResponseError

if TYPE_CHECKING:
    from .project import AsyncProject, Project


class _Cursor:
    def __init__(self, query: SearchRequest, raw: SearchPage) -> None:
        self._query = query.to_dict()
        self.raw = raw
        self._seen: set[str] = set()
        self._started = False

    @property
    def query(self) -> SearchRequest:
        return SearchRequest.from_dict(self._query)

    @property
    def coverage(self) -> list[SearchCoverage]:
        return self.raw.coverage

    @property
    def complete(self) -> bool:
        """Analysis coverage, independent of page exhaustion."""
        return all(c.state == "COMPLETE" for c in self.coverage)

    @property
    def diagnostics(self) -> list[str]:
        return self.raw.diagnostics

    @property
    def result_snapshot_id(self) -> str:
        return self.raw.result_snapshot_id

    @property
    def cursor(self) -> str | None:
        return self.raw.next_cursor

    def _next_request(self) -> SearchRequest | None:
        if self.raw.page_complete or self.cursor is None:
            return None
        if self.cursor in self._seen:
            raise UnexpectedResponseError("Server repeated a search cursor")
        self._seen.add(self.cursor)
        query = self.query
        query.cursor = self.cursor
        return query

    def __repr__(self) -> str:
        return f"{type(self).__name__}(complete={self.complete}, page_complete={self.raw.page_complete})"


class SearchCursor(_Cursor):
    def __init__(self, project: Project, query: SearchRequest, raw: SearchPage) -> None:
        super().__init__(query, raw)
        self._project = project

    def __iter__(self) -> Iterator[SearchHit]:
        if self._started:
            raise ValueError("Search cursor is a single-use iterator")
        self._started = True
        while True:
            yield from self.raw.hits
            request = self._next_request()
            if request is None:
                return
            self.raw = self._project._client._invoke(search, SearchPage, body=request)


class AsyncSearchCursor(_Cursor):
    def __init__(
        self, project: AsyncProject, query: SearchRequest, raw: SearchPage
    ) -> None:
        super().__init__(query, raw)
        self._project = project

    async def __aiter__(self) -> AsyncIterator[SearchHit]:
        if self._started:
            raise ValueError("Search cursor is a single-use iterator")
        self._started = True
        while True:
            for hit in self.raw.hits:
                yield hit
            request = self._next_request()
            if request is None:
                return
            self.raw = await self._project._client._invoke(
                search, SearchPage, body=request
            )
