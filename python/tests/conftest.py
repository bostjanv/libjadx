import json
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
EXAMPLES = ROOT / "openapi/examples"


@pytest.fixture
def example():
    def read(name):
        return json.loads((EXAMPLES / name).read_text())

    return read
