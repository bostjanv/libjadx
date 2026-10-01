from enum import StrEnum


class CapabilityStatus(StrEnum):
    PARTIAL = "PARTIAL"
    SUPPORTED = "SUPPORTED"
    UNKNOWN = "UNKNOWN"
    UNSUPPORTED = "UNSUPPORTED"

    def __str__(self) -> str:
        return str(self.value)
