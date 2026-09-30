from enum import StrEnum


class EditItemKind(StrEnum):
    COMMENT = "COMMENT"
    RENAME_CLASS = "RENAME_CLASS"
    RENAME_FIELD = "RENAME_FIELD"
    RENAME_METHOD = "RENAME_METHOD"

    def __str__(self) -> str:
        return str(self.value)
