from enum import StrEnum


class ReferencePageOutcome(StrEnum):
    AMBIGUOUS = "AMBIGUOUS"
    NOT_FOUND = "NOT_FOUND"
    PROVENANCE_UNAVAILABLE = "PROVENANCE_UNAVAILABLE"
    RESOLVED = "RESOLVED"

    def __str__(self) -> str:
        return str(self.value)
