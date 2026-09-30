from enum import StrEnum


class ReferenceEdgeEvidence(StrEnum):
    JADX_CLASS_DEPENDENCY = "JADX_CLASS_DEPENDENCY"
    JADX_CLASS_USE_IN = "JADX_CLASS_USE_IN"
    JADX_FIELD_USE_IN = "JADX_FIELD_USE_IN"
    JADX_METHOD_UNRESOLVED_USED = "JADX_METHOD_UNRESOLVED_USED"
    JADX_METHOD_USED = "JADX_METHOD_USED"
    JADX_METHOD_USE_IN = "JADX_METHOD_USE_IN"

    def __str__(self) -> str:
        return str(self.value)
