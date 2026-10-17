from enum import StrEnum


class ReferencePageScope(StrEnum):
    JADX_REPORTED = "JADX_REPORTED"
    LOADED_DEX_METHOD = "LOADED_DEX_METHOD"

    def __str__(self) -> str:
        return str(self.value)
