from enum import StrEnum


class SearchCoverageAvailability(StrEnum):
    SUPPORTED = "SUPPORTED"
    UNSUPPORTED = "UNSUPPORTED"

    def __str__(self) -> str:
        return str(self.value)
