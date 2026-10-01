from enum import StrEnum


class CapabilityPrecisionType3Type1(StrEnum):
    APPROXIMATE = "APPROXIMATE"
    EXACT = "EXACT"
    UNAVAILABLE = "UNAVAILABLE"
    UNKNOWN = "UNKNOWN"

    def __str__(self) -> str:
        return str(self.value)
