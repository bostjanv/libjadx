from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define
from attrs import field as _attrs_field

from ..models.capability_precision_type_1 import CapabilityPrecisionType1
from ..models.capability_precision_type_2_type_1 import CapabilityPrecisionType2Type1
from ..models.capability_precision_type_3_type_1 import CapabilityPrecisionType3Type1
from ..models.capability_status import CapabilityStatus
from ..types import UNSET, Unset

T = TypeVar("T", bound="Capability")


@_attrs_define
class Capability:
    """
    Attributes:
        name (str):
        status (CapabilityStatus):
        evidence (str):
        persistence (str): Persistence scope proven for the capability, or UNAVAILABLE.
        precision (CapabilityPrecisionType1 | CapabilityPrecisionType2Type1 | CapabilityPrecisionType3Type1 | None |
            Unset):
    """

    name: str
    status: CapabilityStatus
    evidence: str
    persistence: str
    precision: (
        CapabilityPrecisionType1
        | CapabilityPrecisionType2Type1
        | CapabilityPrecisionType3Type1
        | None
        | Unset
    ) = UNSET
    additional_properties: dict[str, Any] = _attrs_field(init=False, factory=dict)

    def to_dict(self) -> dict[str, Any]:
        name = self.name

        status = self.status.value

        evidence = self.evidence

        persistence = self.persistence

        precision: None | str | Unset
        if isinstance(self.precision, Unset):
            precision = UNSET
        elif isinstance(self.precision, CapabilityPrecisionType1):
            precision = self.precision.value
        elif isinstance(self.precision, CapabilityPrecisionType2Type1):
            precision = self.precision.value
        elif isinstance(self.precision, CapabilityPrecisionType3Type1):
            precision = self.precision.value
        else:
            precision = self.precision

        field_dict: dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update(
            {
                "name": name,
                "status": status,
                "evidence": evidence,
                "persistence": persistence,
            }
        )
        if precision is not UNSET:
            field_dict["precision"] = precision

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        name = d.pop("name")

        status = CapabilityStatus(d.pop("status"))

        evidence = d.pop("evidence")

        persistence = d.pop("persistence")

        def _parse_precision(
            data: object,
        ) -> (
            CapabilityPrecisionType1
            | CapabilityPrecisionType2Type1
            | CapabilityPrecisionType3Type1
            | None
            | Unset
        ):
            if data is None:
                return data
            if isinstance(data, Unset):
                return data
            try:
                if not isinstance(data, str):
                    raise TypeError()
                precision_type_1 = CapabilityPrecisionType1(data)

                return precision_type_1
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            try:
                if not isinstance(data, str):
                    raise TypeError()
                precision_type_2_type_1 = CapabilityPrecisionType2Type1(data)

                return precision_type_2_type_1
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            try:
                if not isinstance(data, str):
                    raise TypeError()
                precision_type_3_type_1 = CapabilityPrecisionType3Type1(data)

                return precision_type_3_type_1
            except (TypeError, ValueError, AttributeError, KeyError):
                pass
            return cast(
                CapabilityPrecisionType1
                | CapabilityPrecisionType2Type1
                | CapabilityPrecisionType3Type1
                | None
                | Unset,
                data,
            )

        precision = _parse_precision(d.pop("precision", UNSET))

        capability = cls(
            name=name,
            status=status,
            evidence=evidence,
            persistence=persistence,
            precision=precision,
        )

        capability.additional_properties = d
        return capability

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
