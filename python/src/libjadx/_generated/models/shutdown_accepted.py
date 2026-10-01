from __future__ import annotations

from collections.abc import Mapping
from typing import (
    Any,
    Literal,
    TypeVar,
    cast,
)

from attrs import define as _attrs_define

from ..models.shutdown_accepted_policy import ShutdownAcceptedPolicy

T = TypeVar("T", bound="ShutdownAccepted")


@_attrs_define
class ShutdownAccepted:
    """
    Attributes:
        state (Literal['SHUTTING_DOWN']):
        policy (ShutdownAcceptedPolicy):
    """

    state: Literal["SHUTTING_DOWN"]
    policy: ShutdownAcceptedPolicy

    def to_dict(self) -> dict[str, Any]:
        state = self.state

        policy = self.policy.value

        field_dict: dict[str, Any] = {}

        field_dict.update(
            {
                "state": state,
                "policy": policy,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        state = cast(Literal["SHUTTING_DOWN"], d.pop("state"))
        if state != "SHUTTING_DOWN":
            raise ValueError(f"state must match const 'SHUTTING_DOWN', got '{state}'")

        policy = ShutdownAcceptedPolicy(d.pop("policy"))

        shutdown_accepted = cls(
            state=state,
            policy=policy,
        )

        return shutdown_accepted
