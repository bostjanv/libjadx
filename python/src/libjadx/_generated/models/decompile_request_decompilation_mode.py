from enum import StrEnum


class DecompileRequestDecompilationMode(StrEnum):
    AUTO = "AUTO"
    FALLBACK = "FALLBACK"
    RESTRUCTURE = "RESTRUCTURE"
    SIMPLE = "SIMPLE"

    def __str__(self) -> str:
        return str(self.value)
