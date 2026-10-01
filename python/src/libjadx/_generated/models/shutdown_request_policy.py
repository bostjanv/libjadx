from enum import StrEnum


class ShutdownRequestPolicy(StrEnum):
    DISCARD = "discard"
    REFUSE_IF_DIRTY = "refuse_if_dirty"
    SAVE = "save"

    def __str__(self) -> str:
        return str(self.value)
