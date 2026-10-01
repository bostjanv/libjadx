from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar, cast

from attrs import define as _attrs_define

from ..models.search_hit_domain import SearchHitDomain
from ..models.search_hit_match_mode import SearchHitMatchMode

if TYPE_CHECKING:
    from ..models.source_range import SourceRange
    from ..models.symbol_ref import SymbolRef


T = TypeVar("T", bound="SearchHit")


@_attrs_define
class SearchHit:
    """
    Attributes:
        domain (SearchHitDomain):
        ref (None | SymbolRef):
        source_owner_ref (None | SymbolRef):
        display_name (None | str):
        matched_text (str):
        match_mode (SearchHitMatchMode):
        range_ (None | SourceRange):
        source_snapshot_id (None | str):
    """

    domain: SearchHitDomain
    ref: None | SymbolRef
    source_owner_ref: None | SymbolRef
    display_name: None | str
    matched_text: str
    match_mode: SearchHitMatchMode
    range_: None | SourceRange
    source_snapshot_id: None | str

    def to_dict(self) -> dict[str, Any]:
        from ..models.source_range import SourceRange  # noqa: PLC0415
        from ..models.symbol_ref import SymbolRef  # noqa: PLC0415

        domain = self.domain.value

        ref: dict[str, Any] | None
        if isinstance(self.ref, SymbolRef):
            ref = self.ref.to_dict()
        else:
            ref = self.ref

        source_owner_ref: dict[str, Any] | None
        if isinstance(self.source_owner_ref, SymbolRef):
            source_owner_ref = self.source_owner_ref.to_dict()
        else:
            source_owner_ref = self.source_owner_ref

        display_name: None | str
        display_name = self.display_name

        matched_text = self.matched_text

        match_mode = self.match_mode.value

        range_: dict[str, Any] | None
        if isinstance(self.range_, SourceRange):
            range_ = self.range_.to_dict()
        else:
            range_ = self.range_

        source_snapshot_id: None | str
        source_snapshot_id = self.source_snapshot_id

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "domain": domain,
                "ref": ref,
                "sourceOwnerRef": source_owner_ref,
                "displayName": display_name,
                "matchedText": matched_text,
                "matchMode": match_mode,
                "range": range_,
                "sourceSnapshotId": source_snapshot_id,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.source_range import SourceRange  # noqa: PLC0415
        from ..models.symbol_ref import SymbolRef  # noqa: PLC0415

        d = dict(src_dict)
        domain = SearchHitDomain(d.pop("domain"))

        def _parse_ref(data: object) -> None | SymbolRef:
            if data is None:
                return data
            try:
                if not isinstance(data, dict):
                    raise TypeError()
                ref_type_0 = SymbolRef.from_dict(data)

                return ref_type_0
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            return cast(None | SymbolRef, data)

        ref = _parse_ref(d.pop("ref"))

        def _parse_source_owner_ref(data: object) -> None | SymbolRef:
            if data is None:
                return data
            try:
                if not isinstance(data, dict):
                    raise TypeError()
                source_owner_ref_type_0 = SymbolRef.from_dict(data)

                return source_owner_ref_type_0
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            return cast(None | SymbolRef, data)

        source_owner_ref = _parse_source_owner_ref(d.pop("sourceOwnerRef"))

        def _parse_display_name(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        display_name = _parse_display_name(d.pop("displayName"))

        matched_text = d.pop("matchedText")

        match_mode = SearchHitMatchMode(d.pop("matchMode"))

        def _parse_range_(data: object) -> None | SourceRange:
            if data is None:
                return data
            try:
                if not isinstance(data, dict):
                    raise TypeError()
                range_type_0 = SourceRange.from_dict(data)

                return range_type_0
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            return cast(None | SourceRange, data)

        range_ = _parse_range_(d.pop("range"))

        def _parse_source_snapshot_id(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        source_snapshot_id = _parse_source_snapshot_id(d.pop("sourceSnapshotId"))

        search_hit = cls(
            domain=domain,
            ref=ref,
            source_owner_ref=source_owner_ref,
            display_name=display_name,
            matched_text=matched_text,
            match_mode=match_mode,
            range_=range_,
            source_snapshot_id=source_snapshot_id,
        )

        return search_hit
