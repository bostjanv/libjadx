from enum import StrEnum


class SymbolInfoProvenance(StrEnum):
    AMBIGUOUS = "AMBIGUOUS"
    EXACT = "EXACT"
    UNAVAILABLE = "UNAVAILABLE"

    def __str__(self) -> str:
        return str(self.value)
