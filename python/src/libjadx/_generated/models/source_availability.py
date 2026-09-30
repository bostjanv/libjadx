from enum import StrEnum


class SourceAvailability(StrEnum):
    EXACT = "EXACT"
    PARTIAL = "PARTIAL"
    UNAVAILABLE = "UNAVAILABLE"
    UNKNOWN = "UNKNOWN"

    def __str__(self) -> str:
        return str(self.value)
