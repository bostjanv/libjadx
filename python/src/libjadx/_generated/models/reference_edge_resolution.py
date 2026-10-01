from enum import StrEnum


class ReferenceEdgeResolution(StrEnum):
    OBSERVED = "OBSERVED"
    RESOLVED = "RESOLVED"
    UNRESOLVED = "UNRESOLVED"

    def __str__(self) -> str:
        return str(self.value)
