from enum import StrEnum


class JobCompletenessType2Type1(StrEnum):
    COMPLETE = "COMPLETE"
    PARTIAL = "PARTIAL"

    def __str__(self) -> str:
        return str(self.value)
