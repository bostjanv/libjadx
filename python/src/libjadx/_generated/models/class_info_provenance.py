from enum import StrEnum


class ClassInfoProvenance(StrEnum):
    AMBIGUOUS = "AMBIGUOUS"
    EXACT = "EXACT"
    UNAVAILABLE = "UNAVAILABLE"

    def __str__(self) -> str:
        return str(self.value)
