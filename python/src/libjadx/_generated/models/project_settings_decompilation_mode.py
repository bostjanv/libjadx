from enum import StrEnum


class ProjectSettingsDecompilationMode(StrEnum):
    AUTO = "AUTO"
    FALLBACK = "FALLBACK"
    RESTRUCTURE = "RESTRUCTURE"
    SIMPLE = "SIMPLE"

    def __str__(self) -> str:
        return str(self.value)
