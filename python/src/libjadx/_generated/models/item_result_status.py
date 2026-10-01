from enum import StrEnum


class ItemResultStatus(StrEnum):
    APPLIED = "APPLIED"
    FAILED = "FAILED"
    SKIPPED = "SKIPPED"

    def __str__(self) -> str:
        return str(self.value)
