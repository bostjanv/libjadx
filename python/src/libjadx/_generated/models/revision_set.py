from __future__ import annotations

from collections.abc import Mapping
from typing import Any, TypeVar, cast

from attrs import define as _attrs_define
from attrs import field as _attrs_field

from ..models.revision_set_persisted_identity_state import (
    RevisionSetPersistedIdentityState,
)

T = TypeVar("T", bound="RevisionSet")


@_attrs_define
class RevisionSet:
    """
    Attributes:
        session_id (str):
        logical_revision (int):
        index_revision (int):
        persisted_identity (None | str): Content fingerprint; null while background hashing is pending or failed.
        persisted_identity_state (RevisionSetPersistedIdentityState):
    """

    session_id: str
    logical_revision: int
    index_revision: int
    persisted_identity: None | str
    persisted_identity_state: RevisionSetPersistedIdentityState
    additional_properties: dict[str, Any] = _attrs_field(init=False, factory=dict)

    def to_dict(self) -> dict[str, Any]:
        session_id = self.session_id

        logical_revision = self.logical_revision

        index_revision = self.index_revision

        persisted_identity: None | str
        persisted_identity = self.persisted_identity

        persisted_identity_state = self.persisted_identity_state.value

        field_dict: dict[str, Any] = {}
        field_dict.update(self.additional_properties)
        field_dict.update(
            {
                "sessionId": session_id,
                "logicalRevision": logical_revision,
                "indexRevision": index_revision,
                "persistedIdentity": persisted_identity,
                "persistedIdentityState": persisted_identity_state,
            }
        )

        return field_dict

    @classmethod
    def from_dict(cls: type[T], src_dict: Mapping[str, Any]) -> T:
        d = dict(src_dict)
        session_id = d.pop("sessionId")

        logical_revision = d.pop("logicalRevision")

        index_revision = d.pop("indexRevision")

        def _parse_persisted_identity(data: object) -> None | str:
            if data is None:
                return data
            return cast(None | str, data)

        persisted_identity = _parse_persisted_identity(d.pop("persistedIdentity"))

        persisted_identity_state = RevisionSetPersistedIdentityState(
            d.pop("persistedIdentityState")
        )

        revision_set = cls(
            session_id=session_id,
            logical_revision=logical_revision,
            index_revision=index_revision,
            persisted_identity=persisted_identity,
            persisted_identity_state=persisted_identity_state,
        )

        revision_set.additional_properties = d
        return revision_set

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
