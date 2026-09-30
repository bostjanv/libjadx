from enum import StrEnum


class SearchRequestMatchMode(StrEnum):
    CONTAINS = "CONTAINS"
    EXACT = "EXACT"
    REGEX = "REGEX"

    def __str__(self) -> str:
        return str(self.value)
