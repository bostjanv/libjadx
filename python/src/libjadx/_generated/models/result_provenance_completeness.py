from enum import StrEnum


class ResultProvenanceCompleteness(StrEnum):
    COMPLETE = "COMPLETE"
    PARTIAL = "PARTIAL"

    def __str__(self) -> str:
        return str(self.value)
