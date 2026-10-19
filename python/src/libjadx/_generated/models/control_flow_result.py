from __future__ import annotations

from collections.abc import Mapping
from typing import (
    TYPE_CHECKING,
    Any,
    Literal,
    TypeVar,
    cast,
)
from uuid import UUID

from attrs import define as _attrs_define

from ..models.control_flow_result_availability import ControlFlowResultAvailability
from ..models.control_flow_result_outcome import ControlFlowResultOutcome
from ..models.control_flow_result_precision import ControlFlowResultPrecision

if TYPE_CHECKING:
    from ..models.control_flow_array_data import ControlFlowArrayData
    from ..models.control_flow_branch import ControlFlowBranch
    from ..models.control_flow_instruction import ControlFlowInstruction
    from ..models.control_flow_payload import ControlFlowPayload
    from ..models.control_flow_switch import ControlFlowSwitch
    from ..models.control_flow_try import ControlFlowTry
    from ..models.symbol_info import SymbolInfo
    from ..models.symbol_ref import SymbolRef


T = TypeVar("T", bound="ControlFlowResult")


@_attrs_define
class ControlFlowResult:
    """
    Attributes:
        outcome (ControlFlowResultOutcome):
        queried_ref (SymbolRef): Exact original JVM/DEX identity. CLASS omits member fields or carries null; METHOD and
            FIELD require non-null values for both. Class names use Lpackage/Top$Inner; with nonempty slash-delimited
            segments, no dots or array prefix. Method descriptors contain zero or more nonvoid field types in parentheses
            and one return type, which may be V. Field descriptors contain exactly one nonvoid type. No whitespace, generic
            syntax or normalization is accepted.
        candidates (list[SymbolInfo]):
        session_id (UUID):
        logical_revision (int):
        publication_epoch (int):
        effective_settings_hash (str):
        snapshot_id (None | str):
        representation (Literal['LOADED_DEX_CONTROL_FLOW_FACTS_V1']):
        unit (Literal['DEX_CODE_UNIT_16']):
        base (Literal['LOADED_METHOD_INSNS_START']):
        availability (ControlFlowResultAvailability):
        precision (ControlFlowResultPrecision):
        coverage (Literal['PARTIAL']):
        reason (str):
        code_units (int | None):
        instructions (list[ControlFlowInstruction]):
        payloads (list[ControlFlowPayload]):
        branches (list[ControlFlowBranch]):
        switches (list[ControlFlowSwitch]):
        array_data (list[ControlFlowArrayData]):
        tries (list[ControlFlowTry]):
        diagnostics (list[str]):
    """

    outcome: ControlFlowResultOutcome
    queried_ref: SymbolRef
    candidates: list[SymbolInfo]
    session_id: UUID
    logical_revision: int
    publication_epoch: int
    effective_settings_hash: str
    snapshot_id: None | str
    representation: Literal["LOADED_DEX_CONTROL_FLOW_FACTS_V1"]
    unit: Literal["DEX_CODE_UNIT_16"]
    base: Literal["LOADED_METHOD_INSNS_START"]
    availability: ControlFlowResultAvailability
    precision: ControlFlowResultPrecision
    coverage: Literal["PARTIAL"]
    reason: str
    code_units: int | None
    instructions: list[ControlFlowInstruction]
    payloads: list[ControlFlowPayload]
    branches: list[ControlFlowBranch]
    switches: list[ControlFlowSwitch]
    array_data: list[ControlFlowArrayData]
    tries: list[ControlFlowTry]
    diagnostics: list[str]

    def to_dict(self) -> dict[str, Any]:
        outcome = self.outcome.value

        queried_ref = self.queried_ref.to_dict()

        candidates = []
        for candidates_item_data in self.candidates:
            candidates_item = candidates_item_data.to_dict()
            candidates.append(candidates_item)

        session_id = str(self.session_id)

        logical_revision = self.logical_revision

        publication_epoch = self.publication_epoch

        effective_settings_hash = self.effective_settings_hash

        snapshot_id: None | str
        snapshot_id = self.snapshot_id

        representation = self.representation

        unit = self.unit

        base = self.base

        availability = self.availability.value

        precision = self.precision.value

        coverage = self.coverage

        reason = self.reason

        code_units: int | None
        code_units = self.code_units

        instructions = []
        for instructions_item_data in self.instructions:
            instructions_item = instructions_item_data.to_dict()
            instructions.append(instructions_item)

        payloads = []
        for payloads_item_data in self.payloads:
            payloads_item = payloads_item_data.to_dict()
            payloads.append(payloads_item)

        branches = []
        for branches_item_data in self.branches:
            branches_item = branches_item_data.to_dict()
            branches.append(branches_item)

        switches = []
        for switches_item_data in self.switches:
            switches_item = switches_item_data.to_dict()
            switches.append(switches_item)

        array_data = []
        for array_data_item_data in self.array_data:
            array_data_item = array_data_item_data.to_dict()
            array_data.append(array_data_item)

        tries = []
        for tries_item_data in self.tries:
            tries_item = tries_item_data.to_dict()
            tries.append(tries_item)

        diagnostics = self.diagnostics

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "outcome": outcome,
                "queriedRef": queried_ref,
                "candidates": candidates,
                "sessionId": session_id,
                "logicalRevision": logical_revision,
                "publicationEpoch": publication_epoch,
                "effectiveSettingsHash": effective_settings_hash,
                "snapshotId": snapshot_id,
                "representation": representation,
                "unit": unit,
                "base": base,
                "availability": availability,
                "precision": precision,
                "coverage": coverage,
                "reason": reason,
                "codeUnits": code_units,
                "instructions": instructions,
                "payloads": payloads,
                "branches": branches,
                "switches": switches,
                "arrayData": array_data,
                "tries": tries,
                "diagnostics": diagnostics,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        from ..models.control_flow_array_data import (
            ControlFlowArrayData,  # noqa: PLC0415
        )
        from ..models.control_flow_branch import ControlFlowBranch  # noqa: PLC0415
        from ..models.control_flow_instruction import (
            ControlFlowInstruction,  # noqa: PLC0415
        )
        from ..models.control_flow_payload import ControlFlowPayload  # noqa: PLC0415
        from ..models.control_flow_switch import ControlFlowSwitch  # noqa: PLC0415
        from ..models.control_flow_try import ControlFlowTry  # noqa: PLC0415
        from ..models.symbol_info import SymbolInfo  # noqa: PLC0415
        from ..models.symbol_ref import SymbolRef  # noqa: PLC0415

        d = dict(src_dict)
        outcome = ControlFlowResultOutcome(d.pop("outcome"))

        queried_ref = SymbolRef.from_dict(d.pop("queriedRef"))

        candidates = []
        _candidates = d.pop("candidates")
        for candidates_item_data in _candidates:
            candidates_item = SymbolInfo.from_dict(candidates_item_data)

            candidates.append(candidates_item)

        session_id = UUID(d.pop("sessionId"))

        logical_revision = d.pop("logicalRevision")

        publication_epoch = d.pop("publicationEpoch")

        effective_settings_hash = d.pop("effectiveSettingsHash")

        def _parse_snapshot_id(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        snapshot_id = _parse_snapshot_id(d.pop("snapshotId"))

        representation = cast(
            Literal["LOADED_DEX_CONTROL_FLOW_FACTS_V1"], d.pop("representation")
        )
        if representation != "LOADED_DEX_CONTROL_FLOW_FACTS_V1":
            raise ValueError(
                f"representation must match const 'LOADED_DEX_CONTROL_FLOW_FACTS_V1', got '{representation}'"
            )

        unit = cast(Literal["DEX_CODE_UNIT_16"], d.pop("unit"))
        if unit != "DEX_CODE_UNIT_16":
            raise ValueError(f"unit must match const 'DEX_CODE_UNIT_16', got '{unit}'")

        base = cast(Literal["LOADED_METHOD_INSNS_START"], d.pop("base"))
        if base != "LOADED_METHOD_INSNS_START":
            raise ValueError(
                f"base must match const 'LOADED_METHOD_INSNS_START', got '{base}'"
            )

        availability = ControlFlowResultAvailability(d.pop("availability"))

        precision = ControlFlowResultPrecision(d.pop("precision"))

        coverage = cast(Literal["PARTIAL"], d.pop("coverage"))
        if coverage != "PARTIAL":
            raise ValueError(f"coverage must match const 'PARTIAL', got '{coverage}'")

        reason = d.pop("reason")

        def _parse_code_units(data: object) -> int | None:
            if data is None:
                return data
            return cast(int | None, data)

        code_units = _parse_code_units(d.pop("codeUnits"))

        instructions = []
        _instructions = d.pop("instructions")
        for instructions_item_data in _instructions:
            instructions_item = ControlFlowInstruction.from_dict(instructions_item_data)

            instructions.append(instructions_item)

        payloads = []
        _payloads = d.pop("payloads")
        for payloads_item_data in _payloads:
            payloads_item = ControlFlowPayload.from_dict(payloads_item_data)

            payloads.append(payloads_item)

        branches = []
        _branches = d.pop("branches")
        for branches_item_data in _branches:
            branches_item = ControlFlowBranch.from_dict(branches_item_data)

            branches.append(branches_item)

        switches = []
        _switches = d.pop("switches")
        for switches_item_data in _switches:
            switches_item = ControlFlowSwitch.from_dict(switches_item_data)

            switches.append(switches_item)

        array_data = []
        _array_data = d.pop("arrayData")
        for array_data_item_data in _array_data:
            array_data_item = ControlFlowArrayData.from_dict(array_data_item_data)

            array_data.append(array_data_item)

        tries = []
        _tries = d.pop("tries")
        for tries_item_data in _tries:
            tries_item = ControlFlowTry.from_dict(tries_item_data)

            tries.append(tries_item)

        diagnostics = cast(list[str], d.pop("diagnostics"))

        control_flow_result = cls(
            outcome=outcome,
            queried_ref=queried_ref,
            candidates=candidates,
            session_id=session_id,
            logical_revision=logical_revision,
            publication_epoch=publication_epoch,
            effective_settings_hash=effective_settings_hash,
            snapshot_id=snapshot_id,
            representation=representation,
            unit=unit,
            base=base,
            availability=availability,
            precision=precision,
            coverage=coverage,
            reason=reason,
            code_units=code_units,
            instructions=instructions,
            payloads=payloads,
            branches=branches,
            switches=switches,
            array_data=array_data,
            tries=tries,
            diagnostics=diagnostics,
        )

        return control_flow_result
