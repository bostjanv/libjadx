"""Stable errors independent of generated transport internals."""

from collections.abc import Mapping
from typing import Any

from ._generated.models.api_error import ApiError as WireError


class LibJadxError(Exception):
    pass


class TransportError(LibJadxError):
    """Connection, network or per-request HTTP timeout (see __cause__)."""


class RequestTimeoutError(TransportError):
    """An individual HTTP request exceeded its configured timeout."""


class UnexpectedResponseError(LibJadxError):
    """Malformed protocol, unexpected status or incompatible response schema."""


class ApiError(LibJadxError):
    def __init__(
        self,
        raw: WireError,
        status: int | None = None,
        headers: Mapping[str, str] | None = None,
    ) -> None:
        super().__init__(raw.message)
        body = raw.to_dict()
        self.raw = raw
        self.http_status = status
        self.code = str(raw.code)
        self.message = raw.message
        self.retryable = body.get("retryable", False)
        self.request_id = body.get("requestId")
        self.headers = dict(headers or {})
        self.header_request_id = next(
            (v for k, v in self.headers.items() if k.lower() == "x-request-id"), None
        )
        self.details: dict[str, Any] | None = body.get("details")
        self.causes = body.get("causes", [])
        self.item_errors = body.get("itemErrors", [])


class ProjectNotReadyError(ApiError):
    pass


class ServiceShuttingDownError(ApiError):
    pass


class ProjectLoadFailedError(ApiError):
    pass


class MethodNotAllowedError(ApiError):
    pass


class ProjectBusyError(ApiError):
    pass


class EventHistoryExpiredError(ApiError):
    pass


class InvalidRequestError(ApiError):
    pass


class InvalidEntityIdError(ApiError):
    pass


class NotFoundError(ApiError):
    pass


class UnsupportedCapabilityError(ApiError):
    pass


class IncompleteAnalysisError(ApiError):
    pass


class StaleRevisionError(ApiError):
    pass


class ExternalModificationConflictError(ApiError):
    pass


class MappingMergeConflictError(ApiError):
    pass


class ResourceLimitError(ApiError):
    pass


class CancellationPendingError(ApiError):
    pass


class InputSecurityRejectionError(ApiError):
    pass


class InternalServerError(ApiError):
    pass


class OperationNotImplementedError(ApiError):
    pass


ERROR_TYPES: dict[str, type[ApiError]] = dict(
    zip(
        [
            "PROJECT_NOT_READY",
            "SERVICE_SHUTTING_DOWN",
            "PROJECT_LOAD_FAILED",
            "METHOD_NOT_ALLOWED",
            "PROJECT_BUSY",
            "EVENT_HISTORY_EXPIRED",
            "INVALID_REQUEST",
            "INVALID_ENTITY_ID",
            "NOT_FOUND",
            "UNSUPPORTED_CAPABILITY",
            "INCOMPLETE_ANALYSIS",
            "STALE_REVISION",
            "EXTERNAL_MODIFICATION_CONFLICT",
            "MAPPING_MERGE_CONFLICT",
            "RESOURCE_LIMIT",
            "CANCELLATION_PENDING",
            "INPUT_SECURITY_REJECTION",
            "INTERNAL_ERROR",
            "OPERATION_NOT_IMPLEMENTED",
        ],
        [
            ProjectNotReadyError,
            ServiceShuttingDownError,
            ProjectLoadFailedError,
            MethodNotAllowedError,
            ProjectBusyError,
            EventHistoryExpiredError,
            InvalidRequestError,
            InvalidEntityIdError,
            NotFoundError,
            UnsupportedCapabilityError,
            IncompleteAnalysisError,
            StaleRevisionError,
            ExternalModificationConflictError,
            MappingMergeConflictError,
            ResourceLimitError,
            CancellationPendingError,
            InputSecurityRejectionError,
            InternalServerError,
            OperationNotImplementedError,
        ],
        strict=True,
    )
)


def api_error(
    raw: WireError, status: int | None = None, headers: Mapping[str, str] | None = None
) -> ApiError:
    return ERROR_TYPES.get(str(raw.code), ApiError)(raw, status, headers)


class ServiceLoadFailed(LibJadxError):
    def __init__(self, status: object, error: ApiError | None) -> None:
        super().__init__("Service initialization failed")
        self.status = status
        self.error = error


class WaitReadyTimeout(LibJadxError):
    pass


class JobWaitTimeout(LibJadxError):
    pass


class JobFailed(LibJadxError):
    def __init__(self, raw: object, error: ApiError | None) -> None:
        super().__init__("Server job failed")
        self.raw = raw
        self.error = error


class JobCancelled(LibJadxError):
    def __init__(self, raw: object) -> None:
        super().__init__("Server job cancelled")
        self.raw = raw
