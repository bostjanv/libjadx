from __future__ import annotations

from collections.abc import Mapping
from typing import (
    Any,
    Literal,
    TypeVar,
    cast,
)

from attrs import define as _attrs_define

T = TypeVar("T", bound="ReferenceInstruction")


@_attrs_define
class ReferenceInstruction:
    """Exact loaded-reader coordinate only; no original-input or Java-source mapping is implied.

    Attributes:
        offset_code_units (int):
        unit (Literal['DEX_CODE_UNIT_16']):
        base (Literal['LOADED_METHOD_INSNS_START']):
        precision (Literal['EXACT']):
        opcode (str):
    """

    offset_code_units: int
    unit: Literal["DEX_CODE_UNIT_16"]
    base: Literal["LOADED_METHOD_INSNS_START"]
    precision: Literal["EXACT"]
    opcode: str

    def to_dict(self) -> dict[str, Any]:
        offset_code_units = self.offset_code_units

        unit = self.unit

        base = self.base

        precision = self.precision

        opcode = self.opcode

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "offsetCodeUnits": offset_code_units,
                "unit": unit,
                "base": base,
                "precision": precision,
                "opcode": opcode,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        offset_code_units = d.pop("offsetCodeUnits")

        unit = cast(Literal["DEX_CODE_UNIT_16"], d.pop("unit"))
        if unit != "DEX_CODE_UNIT_16":
            raise ValueError(f"unit must match const 'DEX_CODE_UNIT_16', got '{unit}'")

        base = cast(Literal["LOADED_METHOD_INSNS_START"], d.pop("base"))
        if base != "LOADED_METHOD_INSNS_START":
            raise ValueError(
                f"base must match const 'LOADED_METHOD_INSNS_START', got '{base}'"
            )

        precision = cast(Literal["EXACT"], d.pop("precision"))
        if precision != "EXACT":
            raise ValueError(f"precision must match const 'EXACT', got '{precision}'")

        opcode = d.pop("opcode")

        reference_instruction = cls(
            offset_code_units=offset_code_units,
            unit=unit,
            base=base,
            precision=precision,
            opcode=opcode,
        )

        return reference_instruction
