from enum import StrEnum


class ReferenceQueryView(StrEnum):
    INSTRUCTIONS = "INSTRUCTIONS"
    PAIRS = "PAIRS"

    def __str__(self) -> str:
        return str(self.value)
