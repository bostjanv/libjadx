from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar, cast
from uuid import UUID

from attrs import define as _attrs_define

from ..models.reference_query_direction import ReferenceQueryDirection
from ..models.reference_relation import ReferenceRelation
from ..types import UNSET, Unset

if TYPE_CHECKING:
    from ..models.symbol_ref import SymbolRef


T = TypeVar("T", bound="ReferenceQuery")


@_attrs_define
class ReferenceQuery:
    """Direction is always required. FIELD accepts only INCOMING/FIELD_USE; CLASS accepts CLASS_DEPENDENCY; METHOD INCOMING
    accepts CALL and OUTGOING accepts CALL/UNRESOLVED_CALL. Omitted relations selects all compatible relations. Empty,
    repeated or incompatible relations (including unproved READ/WRITE) return 400 INVALID_REQUEST. Unknown/repeated
    fields and bodies over 64 KiB are rejected. Revision preconditions are checked after admission.

        Attributes:
            ref (SymbolRef): Exact original JVM/DEX identity. CLASS omits member fields or carries null; METHOD and FIELD
                require non-null values for both. Class names use Lpackage/Top$Inner; with nonempty slash-delimited segments, no
                dots or array prefix. Method descriptors contain zero or more nonvoid field types in parentheses and one return
                type, which may be V. Field descriptors contain exactly one nonvoid type. No whitespace, generic syntax or
                normalization is accepted.
            direction (ReferenceQueryDirection):
            relations (list[ReferenceRelation] | Unset):
            page_size (int | Unset):  Default: 50.
            cursor (None | str | Unset):
            include_source_sites (bool | Unset):  Default: False.
            strict (bool | Unset):  Default: False.
            expected_session_id (UUID | Unset):
            expected_logical_revision (int | Unset):
    """

    ref: SymbolRef
    direction: ReferenceQueryDirection
    relations: list[ReferenceRelation] | Unset = UNSET
    page_size: int | Unset = 50
    cursor: None | str | Unset = UNSET
    include_source_sites: bool | Unset = False
    strict: bool | Unset = False
    expected_session_id: UUID | Unset = UNSET
    expected_logical_revision: int | Unset = UNSET

    def to_dict(self) -> dict[str, Any]:
        ref = self.ref.to_dict()

        direction = self.direction.value

        relations: list[str] | Unset = UNSET
        if not isinstance(self.relations, Unset):
            relations = []
            for relations_item_data in self.relations:
                relations_item = relations_item_data.value
                relations.append(relations_item)

        page_size = self.page_size

        cursor: None | str | Unset
        if isinstance(self.cursor, Unset):
            cursor = UNSET
        else:
            cursor = self.cursor

        include_source_sites = self.include_source_sites

        strict = self.strict

        expected_session_id: str | Unset = UNSET
        if not isinstance(self.expected_session_id, Unset):
            expected_session_id = str(self.expected_session_id)

        expected_logical_revision = self.expected_logical_revision

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "ref": ref,
                "direction": direction,
            }
        )
        if relations is not UNSET:
            field_dict["relations"] = relations
        if page_size is not UNSET:
            field_dict["pageSize"] = page_size
        if cursor is not UNSET:
            field_dict["cursor"] = cursor
        if include_source_sites is not UNSET:
            field_dict["includeSourceSites"] = include_source_sites
        if strict is not UNSET:
            field_dict["strict"] = strict
        if expected_session_id is not UNSET:
            field_dict["expectedSessionId"] = expected_session_id
        if expected_logical_revision is not UNSET:
            field_dict["expectedLogicalRevision"] = expected_logical_revision

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.symbol_ref import SymbolRef  # noqa: PLC0415

        d = dict(src_dict)
        ref = SymbolRef.from_dict(d.pop("ref"))

        direction = ReferenceQueryDirection(d.pop("direction"))

        _relations = d.pop("relations", UNSET)
        relations: list[ReferenceRelation] | Unset = UNSET
        if _relations is not UNSET:
            relations = []
            for relations_item_data in _relations:
                relations_item = ReferenceRelation(relations_item_data)

                relations.append(relations_item)

        page_size = d.pop("pageSize", UNSET)

        def _parse_cursor(data: object) -> None | str | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            return cast(None | str | Unset, data)

        cursor = _parse_cursor(d.pop("cursor", UNSET))

        include_source_sites = d.pop("includeSourceSites", UNSET)

        strict = d.pop("strict", UNSET)

        _expected_session_id = d.pop("expectedSessionId", UNSET)
        expected_session_id: UUID | Unset
        if isinstance(_expected_session_id, Unset):
            expected_session_id = UNSET
        else:
            expected_session_id = UUID(_expected_session_id)

        expected_logical_revision = d.pop("expectedLogicalRevision", UNSET)

        reference_query = cls(
            ref=ref,
            direction=direction,
            relations=relations,
            page_size=page_size,
            cursor=cursor,
            include_source_sites=include_source_sites,
            strict=strict,
            expected_session_id=expected_session_id,
            expected_logical_revision=expected_logical_revision,
        )

        return reference_query
