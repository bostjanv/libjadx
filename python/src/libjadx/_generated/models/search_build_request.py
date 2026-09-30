from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar

from attrs import define as _attrs_define

from ..models.search_build_request_domains_item import SearchBuildRequestDomainsItem
from ..types import UNSET, Unset

T = TypeVar("T", bound="SearchBuildRequest")


@_attrs_define
class SearchBuildRequest:
    """
    Attributes:
        domains (list[SearchBuildRequestDomainsItem] | Unset):
        force (bool | Unset):  Default: False.
    """

    domains: list[SearchBuildRequestDomainsItem] | Unset = UNSET
    force: bool | Unset = False

    def to_dict(self) -> dict[str, Any]:
        domains: list[str] | Unset = UNSET
        if not isinstance(self.domains, Unset):
            domains = []
            for domains_item_data in self.domains:
                domains_item = domains_item_data.value
                domains.append(domains_item)

        force = self.force

        field_dict: dict[str, Any] = {}

        field_dict.update({})
        if domains is not UNSET:
            field_dict["domains"] = domains
        if force is not UNSET:
            field_dict["force"] = force

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        _domains = d.pop("domains", UNSET)
        domains: list[SearchBuildRequestDomainsItem] | Unset = UNSET
        if _domains is not UNSET:
            domains = []
            for domains_item_data in _domains:
                domains_item = SearchBuildRequestDomainsItem(domains_item_data)

                domains.append(domains_item)

        force = d.pop("force", UNSET)

        search_build_request = cls(
            domains=domains,
            force=force,
        )

        return search_build_request
