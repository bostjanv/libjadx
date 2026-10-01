from __future__ import annotations

from collections.abc import Mapping
from typing import (
    Any,
    Literal,
    TypeVar,
    cast,
)

from attrs import define as _attrs_define

from ..models.search_coverage_availability import SearchCoverageAvailability
from ..models.search_coverage_domain import SearchCoverageDomain
from ..models.search_coverage_state import SearchCoverageState

T = TypeVar("T", bound="SearchCoverage")


@_attrs_define
class SearchCoverage:
    """
    Attributes:
        domain (SearchCoverageDomain):
        availability (SearchCoverageAvailability):
        state (SearchCoverageState):
        scope (Literal['JADX_VISIBLE_ELIGIBLE']):
        source_input_coverage (Literal['UNVERIFIED']):
        eligible (int):
        indexed (int):
        pending (int):
        skipped (int):
        failed (int):
        evicted (int):
        diagnostics (list[str]):
    """

    domain: SearchCoverageDomain
    availability: SearchCoverageAvailability
    state: SearchCoverageState
    scope: Literal["JADX_VISIBLE_ELIGIBLE"]
    source_input_coverage: Literal["UNVERIFIED"]
    eligible: int
    indexed: int
    pending: int
    skipped: int
    failed: int
    evicted: int
    diagnostics: list[str]

    def to_dict(self) -> dict[str, Any]:
        domain = self.domain.value

        availability = self.availability.value

        state = self.state.value

        scope = self.scope

        source_input_coverage = self.source_input_coverage

        eligible = self.eligible

        indexed = self.indexed

        pending = self.pending

        skipped = self.skipped

        failed = self.failed

        evicted = self.evicted

        diagnostics = self.diagnostics

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "domain": domain,
                "availability": availability,
                "state": state,
                "scope": scope,
                "sourceInputCoverage": source_input_coverage,
                "eligible": eligible,
                "indexed": indexed,
                "pending": pending,
                "skipped": skipped,
                "failed": failed,
                "evicted": evicted,
                "diagnostics": diagnostics,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        domain = SearchCoverageDomain(d.pop("domain"))

        availability = SearchCoverageAvailability(d.pop("availability"))

        state = SearchCoverageState(d.pop("state"))

        scope = cast(Literal["JADX_VISIBLE_ELIGIBLE"], d.pop("scope"))
        if scope != "JADX_VISIBLE_ELIGIBLE":
            raise ValueError(
                f"scope must match const 'JADX_VISIBLE_ELIGIBLE', got '{scope}'"
            )

        source_input_coverage = cast(
            Literal["UNVERIFIED"], d.pop("sourceInputCoverage")
        )
        if source_input_coverage != "UNVERIFIED":
            raise ValueError(
                f"sourceInputCoverage must match const 'UNVERIFIED', got '{source_input_coverage}'"
            )

        eligible = d.pop("eligible")

        indexed = d.pop("indexed")

        pending = d.pop("pending")

        skipped = d.pop("skipped")

        failed = d.pop("failed")

        evicted = d.pop("evicted")

        diagnostics = cast(list[str], d.pop("diagnostics"))

        search_coverage = cls(
            domain=domain,
            availability=availability,
            state=state,
            scope=scope,
            source_input_coverage=source_input_coverage,
            eligible=eligible,
            indexed=indexed,
            pending=pending,
            skipped=skipped,
            failed=failed,
            evicted=evicted,
            diagnostics=diagnostics,
        )

        return search_coverage
