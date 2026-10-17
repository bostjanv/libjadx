from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar, cast

from attrs import define as _attrs_define

from ..models.reference_edge_evidence import ReferenceEdgeEvidence
from ..models.reference_edge_resolution import ReferenceEdgeResolution
from ..models.reference_edge_source_site_coverage import ReferenceEdgeSourceSiteCoverage
from ..models.reference_relation import ReferenceRelation
from ..types import UNSET, Unset

if TYPE_CHECKING:
    from ..models.reference_instruction import ReferenceInstruction
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
            instruction (None | ReferenceInstruction | Unset):
    """

    source_ref: SymbolRef
    target_ref: SymbolRef
    relation: ReferenceRelation
    resolution: ReferenceEdgeResolution
    evidence: ReferenceEdgeEvidence
    source_sites: list[ReferenceSite]
    source_site_coverage: ReferenceEdgeSourceSiteCoverage
    original_offset: None
    instruction: None | ReferenceInstruction | Unset = UNSET

    def to_dict(self) -> dict[str, Any]:
        from ..models.reference_instruction import ReferenceInstruction  # noqa: PLC0415

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

        instruction: dict[str, Any] | None | Unset
        if isinstance(self.instruction, Unset):
            instruction = UNSET
        elif isinstance(self.instruction, ReferenceInstruction):
            instruction = self.instruction.to_dict()
        else:
            instruction = self.instruction

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
        if instruction is not UNSET:
            field_dict["instruction"] = instruction

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.reference_instruction import ReferenceInstruction  # noqa: PLC0415
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

        def _parse_instruction(data: object) -> None | ReferenceInstruction | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            try:
                if not isinstance(data, dict):
                    raise TypeError()
                instruction_type_0 = ReferenceInstruction.from_dict(data)

                return instruction_type_0
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            return cast(None | ReferenceInstruction | Unset, data)

        instruction = _parse_instruction(d.pop("instruction", UNSET))

        reference_edge = cls(
            source_ref=source_ref,
            target_ref=target_ref,
            relation=relation,
            resolution=resolution,
            evidence=evidence,
            source_sites=source_sites,
            source_site_coverage=source_site_coverage,
            original_offset=original_offset,
            instruction=instruction,
        )

        return reference_edge
