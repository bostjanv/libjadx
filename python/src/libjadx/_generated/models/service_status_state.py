from enum import StrEnum


class ServiceStatusState(StrEnum):
    FAILED = "FAILED"
    LOADING = "LOADING"
    READY = "READY"
    RELOADING = "RELOADING"
    SHUTTING_DOWN = "SHUTTING_DOWN"
    STARTING = "STARTING"
    STOPPED = "STOPPED"

    def __str__(self) -> str:
        return str(self.value)
