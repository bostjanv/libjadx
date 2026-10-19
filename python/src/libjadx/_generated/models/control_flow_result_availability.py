from enum import StrEnum


class ControlFlowResultAvailability(StrEnum):
    AVAILABLE = "AVAILABLE"
    FAILED = "FAILED"
    UNAVAILABLE = "UNAVAILABLE"

    def __str__(self) -> str:
        return str(self.value)
