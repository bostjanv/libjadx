from enum import StrEnum


class ReferenceEdgeSourceSiteCoverage(StrEnum):
    PARTIAL = "PARTIAL"
    UNAVAILABLE = "UNAVAILABLE"

    def __str__(self) -> str:
        return str(self.value)
