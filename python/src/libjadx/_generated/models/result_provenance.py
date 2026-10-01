from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar, cast

from attrs import define as _attrs_define
from attrs import field as _attrs_field

from ..models.result_provenance_completeness import ResultProvenanceCompleteness

if TYPE_CHECKING:
    from ..models.revision_set import RevisionSet


T = TypeVar("T", bound="ResultProvenance")


@_attrs_define
class ResultProvenance:
    """
    Attributes:
        completeness (ResultProvenanceCompleteness):
        revisions (RevisionSet):
        diagnostics (list[str]):
    """

    completeness: ResultProvenanceCompleteness
    revisions: RevisionSet
    diagnostics: list[str]
    additional_properties: dict[str, Any] = _attrs_field(init=False, factory=dict)

    def to_dict(self) -> dict[str, Any]:
        completeness = self.completeness.value

        revisions = self.revisions.to_dict()

        diagnostics = self.diagnostics

        field_dict: dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update(
            {
                "completeness": completeness,
                "revisions": revisions,
                "diagnostics": diagnostics,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.revision_set import RevisionSet  # noqa: PLC0415

        d = dict(src_dict)
        completeness = ResultProvenanceCompleteness(d.pop("completeness"))

        revisions = RevisionSet.from_dict(d.pop("revisions"))

        diagnostics = cast(list[str], d.pop("diagnostics"))

        result_provenance = cls(
            completeness=completeness,
            revisions=revisions,
            diagnostics=diagnostics,
        )

        result_provenance.additional_properties = d
        return result_provenance

    @property
    def additional_keys(self) -> list[str]:
        return list(self.additional_properties.keys())

    def __getitem__(self, key: str) -> Any:
        return self.additional_properties[key]

    def __setitem__(self, key: str, value: Any) -> None:
        self.additional_properties[key] = value

    def __delitem__(self, key: str) -> None:
        del self.additional_properties[key]

    def __contains__(self, key: str) -> bool:
        return key in self.additional_properties
