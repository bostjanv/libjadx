from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar

from attrs import define as _attrs_define

if TYPE_CHECKING:
    from ..models.search_coverage import SearchCoverage


T = TypeVar("T", bound="SearchIndexStatus")


@_attrs_define
class SearchIndexStatus:
    """
    Attributes:
        session_id (str):
        logical_revision (int):
        settings_fingerprint (str):
        index_snapshot_id (str):
        index_generation (int):
        coverage (list[SearchCoverage]):
    """

    session_id: str
    logical_revision: int
    settings_fingerprint: str
    index_snapshot_id: str
    index_generation: int
    coverage: list[SearchCoverage]

    def to_dict(self) -> dict[str, Any]:
        session_id = self.session_id

        logical_revision = self.logical_revision

        settings_fingerprint = self.settings_fingerprint

        index_snapshot_id = self.index_snapshot_id

        index_generation = self.index_generation

        coverage = []
        for coverage_item_data in self.coverage:
            coverage_item = coverage_item_data.to_dict()
            coverage.append(coverage_item)

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "sessionId": session_id,
                "logicalRevision": logical_revision,
                "settingsFingerprint": settings_fingerprint,
                "indexSnapshotId": index_snapshot_id,
                "indexGeneration": index_generation,
                "coverage": coverage,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.search_coverage import SearchCoverage  # noqa: PLC0415

        d = dict(src_dict)
        session_id = d.pop("sessionId")

        logical_revision = d.pop("logicalRevision")

        settings_fingerprint = d.pop("settingsFingerprint")

        index_snapshot_id = d.pop("indexSnapshotId")

        index_generation = d.pop("indexGeneration")

        coverage = []
        _coverage = d.pop("coverage")
        for coverage_item_data in _coverage:
            coverage_item = SearchCoverage.from_dict(coverage_item_data)

            coverage.append(coverage_item)

        search_index_status = cls(
            session_id=session_id,
            logical_revision=logical_revision,
            settings_fingerprint=settings_fingerprint,
            index_snapshot_id=index_snapshot_id,
            index_generation=index_generation,
            coverage=coverage,
        )

        return search_index_status
