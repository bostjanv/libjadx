"""Bounded parser for LibJadx job frames (UTF-8, LF/CRLF)."""

import json
from uuid import UUID

from ._generated.models.job_event import JobEvent
from .errors import UnexpectedResponseError

MAX_FRAME = 1024 * 1024
MAX_LINE = 1024 * 1024
TERMINAL = {"SUCCEEDED", "FAILED", "CANCELLED"}


class Parser:
    def __init__(self, job_id: UUID, last_event_id: str | None) -> None:
        self.job_id = job_id
        self.last_id = int(last_event_id) if last_event_id is not None else 0
        self.pending = bytearray()
        self.lines: list[str] = []
        self.frame_bytes = 0
        self.terminal = False

    def feed(self, chunk: bytes) -> list[JobEvent]:
        events: list[JobEvent] = []
        # Bound before allocating a line or frame; caller supplies bounded chunks.
        self.pending.extend(chunk)
        while b"\n" in self.pending:
            end = self.pending.index(b"\n")
            if end > MAX_LINE:
                raise UnexpectedResponseError("Oversized SSE line")
            try:
                line = self.pending[:end].decode("utf-8").rstrip("\r")
            except UnicodeError as exc:
                raise UnexpectedResponseError("Invalid SSE UTF-8") from exc
            del self.pending[: end + 1]
            self.frame_bytes += end + 1
            if self.frame_bytes > MAX_FRAME:
                raise UnexpectedResponseError("Oversized SSE frame")
            if line:
                self.lines.append(line)
            else:
                event = self._event()
                self.lines.clear()
                self.frame_bytes = 0
                if event is not None:
                    events.append(event)
                    if event.state in TERMINAL:
                        self.terminal = True
                        break
        if len(self.pending) > MAX_LINE:
            raise UnexpectedResponseError("Oversized SSE line")
        if self.frame_bytes + len(self.pending) > MAX_FRAME:
            raise UnexpectedResponseError("Oversized SSE frame")
        return events

    def _event(self) -> JobEvent | None:
        data: list[str] = []
        event_id = event_type = None
        for line in self.lines:
            if line.startswith(":"):
                continue
            key, sep, value = line.partition(":")
            if not sep:
                raise UnexpectedResponseError("Malformed SSE field")
            value = value.removeprefix(" ")
            if key == "data":
                data.append(value)
            elif key == "id":
                event_id = value
            elif key == "event":
                event_type = value
            else:
                raise UnexpectedResponseError("Unknown SSE field")
        if not data:
            if event_id is not None or event_type is not None:
                raise UnexpectedResponseError("SSE frame lacks data")
            return None
        try:
            if event_id is None or not event_id.isascii() or not event_id.isdecimal():
                raise ValueError("Missing numeric event ID")
            number = int(event_id)
            event = JobEvent.from_dict(json.loads("\n".join(data)))
            if (
                number <= self.last_id
                or event.sequence != number
                or event.type_ != event_type
                or event.job_id != self.job_id
            ):
                raise ValueError("Inconsistent event identity/type/sequence")
        except (ValueError, TypeError, AttributeError, KeyError) as exc:
            raise UnexpectedResponseError("Malformed job SSE event") from exc
        self.last_id = number
        return event
