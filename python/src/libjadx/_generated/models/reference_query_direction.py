from enum import StrEnum


class ReferenceQueryDirection(StrEnum):
    INCOMING = "INCOMING"
    OUTGOING = "OUTGOING"

    def __str__(self) -> str:
        return str(self.value)
