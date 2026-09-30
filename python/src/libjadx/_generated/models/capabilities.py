from __future__ import annotations

from collections.abc import Mapping
from typing import TYPE_CHECKING, Any, TypeVar

from attrs import define as _attrs_define
from attrs import field as _attrs_field

if TYPE_CHECKING:
    from ..models.capability import Capability


T = TypeVar("T", bound="Capabilities")


@_attrs_define
class Capabilities:
    """
    Attributes:
        server_version (str):
        jadx_version (str):
        capabilities (list[Capability]):
    """

    server_version: str
    jadx_version: str
    capabilities: list[Capability]
    additional_properties: dict[str, Any] = _attrs_field(init=False, factory=dict)

    def to_dict(self) -> dict[str, Any]:
        server_version = self.server_version

        jadx_version = self.jadx_version

        capabilities = []
        for capabilities_item_data in self.capabilities:
            capabilities_item = capabilities_item_data.to_dict()
            capabilities.append(capabilities_item)

        field_dict: dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update(
            {
                "serverVersion": server_version,
                "jadxVersion": jadx_version,
                "capabilities": capabilities,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.capability import Capability  # noqa: PLC0415

        d = dict(src_dict)
        server_version = d.pop("serverVersion")

        jadx_version = d.pop("jadxVersion")

        capabilities = []
        _capabilities = d.pop("capabilities")
        for capabilities_item_data in _capabilities:
            capabilities_item = Capability.from_dict(capabilities_item_data)

            capabilities.append(capabilities_item)

        capabilities = cls(
            server_version=server_version,
            jadx_version=jadx_version,
            capabilities=capabilities,
        )

        capabilities.additional_properties = d
        return capabilities

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
