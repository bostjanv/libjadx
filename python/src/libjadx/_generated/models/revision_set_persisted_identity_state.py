from enum import StrEnum


class RevisionSetPersistedIdentityState(StrEnum):
    FAILED = "FAILED"
    PENDING = "PENDING"
    READY = "READY"

    def __str__(self) -> str:
        return str(self.value)
