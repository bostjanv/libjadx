from enum import StrEnum


class ListClassesNameDomain(StrEnum):
    ALIAS = "alias"
    ORIGINAL = "original"

    def __str__(self) -> str:
        return str(self.value)
