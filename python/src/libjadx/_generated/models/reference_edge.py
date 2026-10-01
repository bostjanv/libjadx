from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar

from attrs import define as _attrs_define

from ..models.reference_edge_evidence import ReferenceEdgeEvidence
from ..models.reference_edge_resolution import ReferenceEdgeResolution
from ..models.reference_edge_source_site_coverage import ReferenceEdgeSourceSiteCoverage
from ..models.reference_relation import ReferenceRelation

if TYPE_CHECKING:
    from ..models.reference_site import ReferenceSite
    from ..models.symbol_ref import SymbolRef


T = TypeVar("T", bound="ReferenceEdge")


@_attrs_define
class ReferenceEdge:
    """OBSERVED means a reported node has no verified standalone visible declaration; per-input provenance is always
    unavailable.

        Attributes:
            source_ref (SymbolRef): Exact original JVM/DEX identity. CLASS omits member fields or carries null; METHOD and
                FIELD require non-null values for both. Class names use Lpackage/Top$Inner; with nonempty slash-delimited
                segments, no dots or array prefix. Method descriptors contain zero or more nonvoid field types in parentheses
                and one return type, which may be V. Field descriptors contain exactly one nonvoid type. No whitespace, generic
                syntax or normalization is accepted.
            target_ref (SymbolRef): Exact original JVM/DEX identity. CLASS omits member fields or carries null; METHOD and
                FIELD require non-null values for both. Class names use Lpackage/Top$Inner; with nonempty slash-delimited
                segments, no dots or array prefix. Method descriptors contain zero or more nonvoid field types in parentheses
                and one return type, which may be V. Field descriptors contain exactly one nonvoid type. No whitespace, generic
                syntax or normalization is accepted.
            relation (ReferenceRelation):
            resolution (ReferenceEdgeResolution):
            evidence (ReferenceEdgeEvidence):
            source_sites (list[ReferenceSite]):
            source_site_coverage (ReferenceEdgeSourceSiteCoverage):
            original_offset (None):
    """

    source_ref: SymbolRef
    target_ref: SymbolRef
    relation: ReferenceRelation
    resolution: ReferenceEdgeResolution
    evidence: ReferenceEdgeEvidence
    source_sites: list[ReferenceSite]
    source_site_coverage: ReferenceEdgeSourceSiteCoverage
    original_offset: None

    def to_dict(self) -> dict[str, Any]:
        source_ref = self.source_ref.to_dict()

        target_ref = self.target_ref.to_dict()

        relation = self.relation.value

        resolution = self.resolution.value

        evidence = self.evidence.value

        source_sites = []
        for source_sites_item_data in self.source_sites:
            source_sites_item = source_sites_item_data.to_dict()
            source_sites.append(source_sites_item)

        source_site_coverage = self.source_site_coverage.value

        original_offset = self.original_offset

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "sourceRef": source_ref,
                "targetRef": target_ref,
                "relation": relation,
                "resolution": resolution,
                "evidence": evidence,
                "sourceSites": source_sites,
                "sourceSiteCoverage": source_site_coverage,
                "originalOffset": original_offset,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.reference_site import ReferenceSite  # noqa: PLC0415
        from ..models.symbol_ref import SymbolRef  # noqa: PLC0415

        d = dict(src_dict)
        source_ref = SymbolRef.from_dict(d.pop("sourceRef"))

        target_ref = SymbolRef.from_dict(d.pop("targetRef"))

        relation = ReferenceRelation(d.pop("relation"))

        resolution = ReferenceEdgeResolution(d.pop("resolution"))

        evidence = ReferenceEdgeEvidence(d.pop("evidence"))

        source_sites = []
        _source_sites = d.pop("sourceSites")
        for source_sites_item_data in _source_sites:
            source_sites_item = ReferenceSite.from_dict(source_sites_item_data)

            source_sites.append(source_sites_item)

        source_site_coverage = ReferenceEdgeSourceSiteCoverage(
            d.pop("sourceSiteCoverage")
        )

        original_offset = d.pop("originalOffset")

        reference_edge = cls(
            source_ref=source_ref,
            target_ref=target_ref,
            relation=relation,
            resolution=resolution,
            evidence=evidence,
            source_sites=source_sites,
            source_site_coverage=source_site_coverage,
            original_offset=original_offset,
        )

        return reference_edge
