from enum import StrEnum


class EditItemResultKind(StrEnum):
    RENAME = "RENAME"
    RENAME_PARAMETER = "RENAME_PARAMETER"
    SET_COMMENT = "SET_COMMENT"

    def __str__(self) -> str:
        return str(self.value)
