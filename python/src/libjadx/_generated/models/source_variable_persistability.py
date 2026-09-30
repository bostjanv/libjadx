from enum import StrEnum


class SourceVariablePersistability(StrEnum):
    SUPPORTED = "SUPPORTED"
    UNSUPPORTED = "UNSUPPORTED"

    def __str__(self) -> str:
        return str(self.value)
