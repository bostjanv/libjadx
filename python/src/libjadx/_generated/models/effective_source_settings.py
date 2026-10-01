from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar

from attrs import define as _attrs_define

from ..models.effective_source_settings_decompilation_mode import (
    EffectiveSourceSettingsDecompilationMode,
)

T = TypeVar("T", bound="EffectiveSourceSettings")


@_attrs_define
class EffectiveSourceSettings:
    """
    Attributes:
        decompilation_mode (EffectiveSourceSettingsDecompilationMode):
        fingerprint (str):
    """

    decompilation_mode: EffectiveSourceSettingsDecompilationMode
    fingerprint: str

    def to_dict(self) -> dict[str, Any]:
        decompilation_mode = self.decompilation_mode.value

        fingerprint = self.fingerprint

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "decompilationMode": decompilation_mode,
                "fingerprint": fingerprint,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        decompilation_mode = EffectiveSourceSettingsDecompilationMode(
            d.pop("decompilationMode")
        )

        fingerprint = d.pop("fingerprint")

        effective_source_settings = cls(
            decompilation_mode=decompilation_mode,
            fingerprint=fingerprint,
        )

        return effective_source_settings
