from enum import StrEnum


class EditBatchResultOutcome(StrEnum):
    APPLIED = "APPLIED"
    NO_CHANGE = "NO_CHANGE"
    PARTIAL = "PARTIAL"

    def __str__(self) -> str:
        return str(self.value)
