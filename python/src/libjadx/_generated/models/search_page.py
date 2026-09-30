from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar, cast

from attrs import define as _attrs_define

if TYPE_CHECKING:
    from ..models.search_coverage import SearchCoverage
    from ..models.search_hit import SearchHit


T = TypeVar("T", bound="SearchPage")


@_attrs_define
class SearchPage:
    """
    Attributes:
        session_id (str):
        logical_revision (int):
        settings_fingerprint (str):
        index_snapshot_id (str):
        index_generation (int):
        result_snapshot_id (str):
        coverage (list[SearchCoverage]):
        hits (list[SearchHit]):
        next_cursor (None | str):
        page_complete (bool):
        diagnostics (list[str]):
    """

    session_id: str
    logical_revision: int
    settings_fingerprint: str
    index_snapshot_id: str
    index_generation: int
    result_snapshot_id: str
    coverage: list[SearchCoverage]
    hits: list[SearchHit]
    next_cursor: None | str
    page_complete: bool
    diagnostics: list[str]

    def to_dict(self) -> dict[str, Any]:
        session_id = self.session_id

        logical_revision = self.logical_revision

        settings_fingerprint = self.settings_fingerprint

        index_snapshot_id = self.index_snapshot_id

        index_generation = self.index_generation

        result_snapshot_id = self.result_snapshot_id

        coverage = []
        for coverage_item_data in self.coverage:
            coverage_item = coverage_item_data.to_dict()
            coverage.append(coverage_item)

        hits = []
        for hits_item_data in self.hits:
            hits_item = hits_item_data.to_dict()
            hits.append(hits_item)

        next_cursor: None | str
        next_cursor = self.next_cursor

        page_complete = self.page_complete

        diagnostics = self.diagnostics

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "sessionId": session_id,
                "logicalRevision": logical_revision,
                "settingsFingerprint": settings_fingerprint,
                "indexSnapshotId": index_snapshot_id,
                "indexGeneration": index_generation,
                "resultSnapshotId": result_snapshot_id,
                "coverage": coverage,
                "hits": hits,
                "nextCursor": next_cursor,
                "pageComplete": page_complete,
                "diagnostics": diagnostics,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.search_coverage import SearchCoverage  # noqa: PLC0415
        from ..models.search_hit import SearchHit  # noqa: PLC0415

        d = dict(src_dict)
        session_id = d.pop("sessionId")

        logical_revision = d.pop("logicalRevision")

        settings_fingerprint = d.pop("settingsFingerprint")

        index_snapshot_id = d.pop("indexSnapshotId")

        index_generation = d.pop("indexGeneration")

        result_snapshot_id = d.pop("resultSnapshotId")

        coverage = []
        _coverage = d.pop("coverage")
        for coverage_item_data in _coverage:
            coverage_item = SearchCoverage.from_dict(coverage_item_data)

            coverage.append(coverage_item)

        hits = []
        _hits = d.pop("hits")
        for hits_item_data in _hits:
            hits_item = SearchHit.from_dict(hits_item_data)

            hits.append(hits_item)

        def _parse_next_cursor(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        next_cursor = _parse_next_cursor(d.pop("nextCursor"))

        page_complete = d.pop("pageComplete")

        diagnostics = cast(list[str], d.pop("diagnostics"))

        search_page = cls(
            session_id=session_id,
            logical_revision=logical_revision,
            settings_fingerprint=settings_fingerprint,
            index_snapshot_id=index_snapshot_id,
            index_generation=index_generation,
            result_snapshot_id=result_snapshot_id,
            coverage=coverage,
            hits=hits,
            next_cursor=next_cursor,
            page_complete=page_complete,
            diagnostics=diagnostics,
        )

        return search_page
