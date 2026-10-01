from enum import StrEnum


class MappingImportReceiptOutcome(StrEnum):
    APPLIED = "APPLIED"
    NO_CHANGE = "NO_CHANGE"

    def __str__(self) -> str:
        return str(self.value)
