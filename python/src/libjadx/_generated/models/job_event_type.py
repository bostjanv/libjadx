from enum import StrEnum


class JobEventType(StrEnum):
    JOB_CANCELLED = "job.cancelled"
    JOB_CANCEL_REQUESTED = "job.cancel_requested"
    JOB_COMPLETED = "job.completed"
    JOB_FAILED = "job.failed"
    JOB_PROGRESS = "job.progress"
    JOB_QUEUED = "job.queued"
    JOB_STARTED = "job.started"

    def __str__(self) -> str:
        return str(self.value)
