from enum import StrEnum


class JobCancellationReasonType3Type1(StrEnum):
    DEADLINE = "DEADLINE"
    SHUTDOWN = "SHUTDOWN"
    USER = "USER"

    def __str__(self) -> str:
        return str(self.value)
