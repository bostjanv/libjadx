from enum import StrEnum


class DecompileResultRepresentation(StrEnum):
    JAVA = "JAVA"
    SMALI = "SMALI"

    def __str__(self) -> str:
        return str(self.value)
