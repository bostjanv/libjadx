from enum import StrEnum


class JobCompletenessType3Type1(StrEnum):
    COMPLETE = "COMPLETE"
    PARTIAL = "PARTIAL"

    def __str__(self) -> str:
        return str(self.value)
