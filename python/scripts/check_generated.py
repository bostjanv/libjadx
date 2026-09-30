"""Nonmutating drift check; compare both names and bytes."""

import tempfile
from pathlib import Path

from generate import TARGET, generate


def tree(path: Path) -> dict[str, bytes]:
    return {
        str(p.relative_to(path)): p.read_bytes()
        for p in path.rglob("*")
        if p.is_file() and "__pycache__" not in p.parts
    }


if __name__ == "__main__":
    with tempfile.TemporaryDirectory() as directory:
        output = Path(directory) / "_generated"
        generate(output)
        expected, actual = tree(output), tree(TARGET)
        changed = sorted(
            k
            for k in expected.keys() | actual.keys()
            if expected.get(k) != actual.get(k)
        )
        if changed:
            raise SystemExit("Generated drift: " + ", ".join(changed))
        print(f"Generated output matches ({len(actual)} files)")
