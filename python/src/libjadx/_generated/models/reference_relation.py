from enum import StrEnum


class ReferenceRelation(StrEnum):
    CALL = "CALL"
    CLASS_DEPENDENCY = "CLASS_DEPENDENCY"
    FIELD_READ = "FIELD_READ"
    FIELD_USE = "FIELD_USE"
    FIELD_WRITE = "FIELD_WRITE"
    UNRESOLVED_CALL = "UNRESOLVED_CALL"

    def __str__(self) -> str:
        return str(self.value)
