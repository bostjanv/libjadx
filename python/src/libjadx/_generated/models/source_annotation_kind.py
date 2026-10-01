from enum import StrEnum


class SourceAnnotationKind(StrEnum):
    DECLARATION = "DECLARATION"
    REFERENCE = "REFERENCE"

    def __str__(self) -> str:
        return str(self.value)
