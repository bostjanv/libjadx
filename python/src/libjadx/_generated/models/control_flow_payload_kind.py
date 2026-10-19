from enum import StrEnum


class ControlFlowPayloadKind(StrEnum):
    ARRAY_DATA = "ARRAY_DATA"
    PACKED_SWITCH = "PACKED_SWITCH"
    SPARSE_SWITCH = "SPARSE_SWITCH"

    def __str__(self) -> str:
        return str(self.value)
