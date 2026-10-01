from enum import StrEnum


class SymbolRefKind(StrEnum):
    CLASS = "CLASS"
    FIELD = "FIELD"
    METHOD = "METHOD"

    def __str__(self) -> str:
        return str(self.value)
