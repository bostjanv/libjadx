from enum import StrEnum


class ShutdownBusyDetailsJobsItemState(StrEnum):
    CANCELLING = "CANCELLING"
    QUEUED = "QUEUED"
    RUNNING = "RUNNING"

    def __str__(self) -> str:
        return str(self.value)
