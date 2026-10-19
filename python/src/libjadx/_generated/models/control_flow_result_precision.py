from enum import StrEnum


class ControlFlowResultPrecision(StrEnum):
    EXACT = "EXACT"
    UNAVAILABLE = "UNAVAILABLE"

    def __str__(self) -> str:
        return str(self.value)
