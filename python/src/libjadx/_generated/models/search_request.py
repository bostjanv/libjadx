from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define

from ..models.search_request_domains_item import SearchRequestDomainsItem
from ..models.search_request_match_mode import SearchRequestMatchMode
from ..types import UNSET, Unset

T = TypeVar("T", bound="SearchRequest")


@_attrs_define
class SearchRequest:
    """
    Attributes:
        query (str):
        domains (list[SearchRequestDomainsItem] | Unset):
        match_mode (SearchRequestMatchMode | Unset):  Default: SearchRequestMatchMode.CONTAINS.
        case_sensitive (bool | Unset):  Default: True.
        page_size (int | Unset):  Default: 50.
        cursor (None | str | Unset):
        strict (bool | Unset):  Default: False.
        require_complete (bool | Unset):  Default: False.
        expected_session_id (None | str | Unset):
        expected_logical_revision (int | None | Unset):
    """

    query: str
    domains: list[SearchRequestDomainsItem] | Unset = UNSET
    match_mode: SearchRequestMatchMode | Unset = SearchRequestMatchMode.CONTAINS
    case_sensitive: bool | Unset = True
    page_size: int | Unset = 50
    cursor: None | str | Unset = UNSET
    strict: bool | Unset = False
    require_complete: bool | Unset = False
    expected_session_id: None | str | Unset = UNSET
    expected_logical_revision: int | None | Unset = UNSET

    def to_dict(self) -> dict[str, Any]:
        query = self.query

        domains: list[str] | Unset = UNSET
        if not isinstance(self.domains, Unset):
            domains = []
            for domains_item_data in self.domains:
                domains_item = domains_item_data.value
                domains.append(domains_item)

        match_mode: str | Unset = UNSET
        if not isinstance(self.match_mode, Unset):
            match_mode = self.match_mode.value

        case_sensitive = self.case_sensitive

        page_size = self.page_size

        cursor: None | str | Unset
        if isinstance(self.cursor, Unset):
            cursor = UNSET
        else:
            cursor = self.cursor

        strict = self.strict

        require_complete = self.require_complete

        expected_session_id: None | str | Unset
        if isinstance(self.expected_session_id, Unset):
            expected_session_id = UNSET
        else:
            expected_session_id = self.expected_session_id

        expected_logical_revision: int | None | Unset
        if isinstance(self.expected_logical_revision, Unset):
            expected_logical_revision = UNSET
        else:
            expected_logical_revision = self.expected_logical_revision

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "query": query,
            }
        )
        if domains is not UNSET:
            field_dict["domains"] = domains
        if match_mode is not UNSET:
            field_dict["matchMode"] = match_mode
        if case_sensitive is not UNSET:
            field_dict["caseSensitive"] = case_sensitive
        if page_size is not UNSET:
            field_dict["pageSize"] = page_size
        if cursor is not UNSET:
            field_dict["cursor"] = cursor
        if strict is not UNSET:
            field_dict["strict"] = strict
        if require_complete is not UNSET:
            field_dict["requireComplete"] = require_complete
        if expected_session_id is not UNSET:
            field_dict["expectedSessionId"] = expected_session_id
        if expected_logical_revision is not UNSET:
            field_dict["expectedLogicalRevision"] = expected_logical_revision

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        query = d.pop("query")

        _domains = d.pop("domains", UNSET)
        domains: list[SearchRequestDomainsItem] | Unset = UNSET
        if _domains is not UNSET:
            domains = []
            for domains_item_data in _domains:
                domains_item = SearchRequestDomainsItem(domains_item_data)

                domains.append(domains_item)

        _match_mode = d.pop("matchMode", UNSET)
        match_mode: SearchRequestMatchMode | Unset
        if isinstance(_match_mode, Unset):
            match_mode = UNSET
        else:
            match_mode = SearchRequestMatchMode(_match_mode)

        case_sensitive = d.pop("caseSensitive", UNSET)

        page_size = d.pop("pageSize", UNSET)

        def _parse_cursor(data: object) -> None | str | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            return cast(None | str | Unset, data)

        cursor = _parse_cursor(d.pop("cursor", UNSET))

        strict = d.pop("strict", UNSET)

        require_complete = d.pop("requireComplete", UNSET)

        def _parse_expected_session_id(data: object) -> None | str | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            return cast(None | str | Unset, data)

        expected_session_id = _parse_expected_session_id(
            d.pop("expectedSessionId", UNSET)
        )

        def _parse_expected_logical_revision(data: object) -> int | None | Unset:
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            return cast(int | None | Unset, data)

        expected_logical_revision = _parse_expected_logical_revision(
            d.pop("expectedLogicalRevision", UNSET)
        )

        search_request = cls(
            query=query,
            domains=domains,
            match_mode=match_mode,
            case_sensitive=case_sensitive,
            page_size=page_size,
            cursor=cursor,
            strict=strict,
            require_complete=require_complete,
            expected_session_id=expected_session_id,
            expected_logical_revision=expected_logical_revision,
        )

        return search_request
