from enum import StrEnum


class SearchHitDomain(StrEnum):
    CLASS_NAME = "CLASS_NAME"
    MEMBER_NAME = "MEMBER_NAME"
    SOURCE_TEXT = "SOURCE_TEXT"
    STRING_LITERAL = "STRING_LITERAL"

    def __str__(self) -> str:
        return str(self.value)
