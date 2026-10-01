from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar

from attrs import define as _attrs_define

from ..models.shutdown_request_policy import ShutdownRequestPolicy
from ..types import UNSET, Unset

T = TypeVar("T", bound="ShutdownRequest")


@_attrs_define
class ShutdownRequest:
    """
    Attributes:
        policy (ShutdownRequestPolicy | Unset):  Default: ShutdownRequestPolicy.DISCARD.
    """

    policy: ShutdownRequestPolicy | Unset = ShutdownRequestPolicy.DISCARD

    def to_dict(self) -> dict[str, Any]:
        policy: str | Unset = UNSET
        if not isinstance(self.policy, Unset):
            policy = self.policy.value

        field_dict: dict[str, Any] = {}

        field_dict.update({})
        if policy is not UNSET:
            field_dict["policy"] = policy

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        _policy = d.pop("policy", UNSET)
        policy: ShutdownRequestPolicy | Unset
        if isinstance(_policy, Unset):
            policy = UNSET
        else:
            policy = ShutdownRequestPolicy(_policy)

        shutdown_request = cls(
            policy=policy,
        )

        return shutdown_request
