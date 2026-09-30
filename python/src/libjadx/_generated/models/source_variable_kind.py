from enum import StrEnum


class SourceVariableKind(StrEnum):
    LOCAL = "LOCAL"
    PARAMETER = "PARAMETER"

    def __str__(self) -> str:
        return str(self.value)
