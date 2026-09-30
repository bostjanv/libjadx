from enum import StrEnum


class ReferencePageDirection(StrEnum):
    INCOMING = "INCOMING"
    OUTGOING = "OUTGOING"

    def __str__(self) -> str:
        return str(self.value)
