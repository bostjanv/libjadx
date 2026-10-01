from enum import StrEnum


class CoverageStatus(StrEnum):
    COMPLETE = "COMPLETE"
    PARTIAL = "PARTIAL"

    def __str__(self) -> str:
        return str(self.value)
