"""Offline Markdown links/anchors and current-document milestone language gate.

Scans git-tracked and unignored Markdown throughout the repo, including Python.
HTTP links are intentionally excluded. Uses only Python's standard library.
"""

import html
import re
import subprocess
import sys
import tempfile
import unicodedata
from pathlib import Path
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
CURRENT_DOCS = {
    "README.md",
    "AGENTS.md",
    "DESIGN.md",
    "IMPLEMENTATION.md",
    "docs/compatibility.md",
    "docs/configuration.md",
    "docs/feasibility-matrix.md",
    "docs/phase-6-python-sdk.md",
    "docs/phase-6-release-qualification.md",
    "docs/README.md",
    "python/README.md",
    "python/THIRD_PARTY.md",
    "docs/releases/0.1.0-alpha.1.md",
    "docs/alpha-publication-checklist.md",
}
STALE_PHRASES = (
    "LibJadx has no root open-source license grant",
    "no root license",
    "LicenseRef-Proprietary",
    "all rights reserved for now",
    "no chosen source license",
    "no source license has been chosen",
    "license policy still undecided",
    "human-selected interim policy",
    "Project status — early development",
    "repository described here is a proposed structure",
    "Phase 6 Python SDK work is next",
    "Python release is not yet complete",
    "Python release remains open",
    "Python release remains pending",
    "Phase 6 is next",
    "Python SDK follows in Phase 6",
    "Phase 6.2 qualification pending",
    "Phase 6.2 release qualification remains pending",
    "generated Python transport remains a Phase 6 deliverable",
    "current Gradle application is the initial implementation slice",
    "Other analysis and edit routes remain planned",
)
STALE_QUALIFICATION_PHRASES = (
    "locally release-qualified by PR #22",
    "locally release-qualified after PR #22",
    "PR #22 locally qualified the candidate",
    "requires fresh candidate qualification",
    "PR #24 requires a fresh run",
    "PR #22 is the final v0.1 qualification report",
)


def prose(text):
    """Blank fenced/indented code without shifting diagnostic line numbers."""
    lines = []
    fence = None
    for line in text.splitlines(keepends=True):
        marker = re.match(r"^ {0,3}(`{3,}|~{3,})", line)
        if fence:
            if marker and marker[1][0] == fence[0] and len(marker[1]) >= len(fence):
                fence = None
            lines.append("\n" if line.endswith("\n") else "")
        elif marker:
            fence = marker[1]
            lines.append("\n" if line.endswith("\n") else "")
        elif line.startswith(("    ", "\t")):
            lines.append("\n" if line.endswith("\n") else "")
        else:
            lines.append(line)
    return "".join(lines)


def slug(heading):
    heading = re.sub(r"!?\[([^\]]*)\]\([^)]*\)", r"\1", heading)
    heading = re.sub(r"<[^>]*>", "", heading)
    heading = html.unescape(heading).strip().lower()
    heading = "".join(
        c
        for c in heading
        if c in "-_" or c.isspace() or unicodedata.category(c)[0] in "LN"
    )
    return heading.replace(" ", "-")


def anchors(text):
    body = prose(text)
    found = set()
    for match in re.finditer(r'<[^>]+\b(?:id|name)=[\'"]([^\'"]+)[\'"]', body):
        found.add(match[1])
    counts = {}
    lines = body.splitlines()
    for i, line in enumerate(lines):
        heading = re.match(r"^ {0,3}#{1,6}\s+(.*?)\s*#*\s*$", line)
        if heading:
            name = slug(heading[1])
        elif (
            i + 1 < len(lines)
            and line.strip()
            and re.fullmatch(r" {0,3}(?:=+|-+)\s*", lines[i + 1])
        ):
            name = slug(line)
        else:
            continue
        count = counts.get(name, 0)
        candidate = f"{name}-{count}" if count else name
        while candidate in found:
            count += 1
            candidate = f"{name}-{count}"
        found.add(candidate)
        counts[name] = count + 1
    return found


def normalize(label):
    return " ".join(label.lower().split())


def destination(text, start):
    """Read inline destinations, including balanced parentheses or angle brackets."""
    if text[start : start + 1] == "<":
        end = text.find(">", start + 1)
        return text[start + 1 : end] if end >= 0 else None
    depth = 0
    end = start
    while end < len(text):
        char = text[end]
        if char == "\\":
            end += 2
            continue
        if char == "(":
            depth += 1
        elif char == ")":
            if depth == 0:
                return text[start:end]
            depth -= 1
        elif char.isspace() and depth == 0:
            return text[start:end]
        end += 1
    return None


def links(text):
    body = prose(text)
    # Inline code examples are not links. Blank them while preserving offsets.
    body = re.sub(r"(`+).*?\1", lambda m: " " * len(m[0]), body)
    definitions = {}
    for match in re.finditer(
        r"^ {0,3}\[([^\]]+)\]:\s*(<[^>]+>|\S+)", body, re.MULTILINE
    ):
        definitions[normalize(match[1])] = match[2].strip("<>")
    for match in re.finditer(r"(?<!\\)\[([^\]\n]+)\](\(|\[([^\]\n]*)\])?", body):
        # Reference definitions themselves are checked via their uses/targets below.
        if body[match.end() : match.end() + 1] == ":":
            continue
        target = None
        if match[2] == "(":
            target = destination(body, match.end())
        elif match[2]:
            target = definitions.get(normalize(match[3] or match[1]))
            if target is None:
                yield body[: match.start()].count("\n") + 1, None
                continue
        else:
            target = definitions.get(normalize(match[1]))
        if target is not None:
            yield body[: match.start()].count("\n") + 1, target
    # Validate even unused reference definitions and local HTML href/src links.
    for target in definitions.values():
        yield 1, target
    for match in re.finditer(r'<[^>]+\b(?:href|src)=[\'"]([^\'"]+)[\'"]', body):
        yield body[: match.start()].count("\n") + 1, match[1]


def audit(root, files):
    errors = []
    checked = 0
    cache = {}
    for path in files:
        text = path.read_text(encoding="utf-8")
        relative = path.relative_to(root).as_posix()
        if relative in CURRENT_DOCS:
            normalized = " ".join(text.lower().split())
            for phrase in (*STALE_PHRASES, *STALE_QUALIFICATION_PHRASES):
                if phrase.lower() in normalized:
                    errors.append(
                        f"{relative}: stale current-document phrase: {phrase}"
                    )
        for line, target in links(text):
            if target is None:
                errors.append(f"{relative}:{line}: undefined reference link")
                continue
            url = urlsplit(html.unescape(target))
            if url.scheme or url.netloc:
                continue
            checked += 1
            decoded = unquote(url.path)
            local = (
                (
                    root / decoded.lstrip("/")
                    if decoded.startswith("/")
                    else path.parent / decoded
                )
                if decoded
                else path
            )
            local = local.resolve()
            if not local.exists():
                errors.append(f"{relative}:{line}: missing target {target}")
            elif url.fragment and local.suffix.lower() == ".md":
                if local not in cache:
                    cache[local] = anchors(local.read_text(encoding="utf-8"))
                if unquote(url.fragment) not in cache[local]:
                    errors.append(f"{relative}:{line}: missing anchor {target}")
    return checked, errors


def self_test():
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        doc = root / "README.md"
        historical = root / "historical.md"
        doc.write_text("""# Title
## Same
## Same
## API `Foo` — state
<a id="explicit"></a>
[ok](#same-1) [punctuation](#api-foo--state) [html](#explicit)
[reference][ref] [shortcut]
[ref]: historical.md#old
[shortcut]: <historical.md#old>
[angle](<file with spaces.txt>) [paren](file(1).txt)
[external](https://example.invalid/missing)
```
[ignored](missing.md)
```
`[inline example](missing.md)`
""")
        historical.write_text(
            "# Old\nPhase 6 Python SDK work is next\nLicenseRef-Proprietary\n"
        )
        (root / "file with spaces.txt").touch()
        (root / "file(1).txt").touch()
        _, errors = audit(root, [doc, historical])
        assert not errors, errors
        doc.write_text(
            doc.read_text()
            + "\n[broken](absent.md) [bad anchor](historical.md#absent) [bad ref][undefined]\nPhase 6 Python SDK work is next\n"
        )
        _, errors = audit(root, [doc, historical])
        assert len(errors) == 4, errors
        assert any("missing target" in error for error in errors)
        assert any("missing anchor" in error for error in errors)
        assert any("undefined reference" in error for error in errors)
        assert any("stale current-document" in error for error in errors)
        doc.write_text("# Current\nLibJadx is licensed under Apache-2.0.\n")
        _, errors = audit(root, [doc, historical])
        assert not errors, errors
        for phrase in STALE_PHRASES[:8]:
            doc.write_text("# Current\n" + phrase + "\n")
            _, errors = audit(root, [doc, historical])
            assert len(errors) == 1 and "stale current-document" in errors[0], errors
        # Historical reviews may preserve old qualification language. Each active
        # status document must reject it, including when split across lines.
        historical.write_text(
            "# Historical PR #22 review\n"
            + "\n".join(STALE_QUALIFICATION_PHRASES)
            + "\n"
        )
        status_docs = sorted(CURRENT_DOCS)
        for name in status_docs:
            status_doc = root / name
            status_doc.parent.mkdir(parents=True, exist_ok=True)
            status_doc.write_text(
                "# Current\nPR #24 freshly qualified the Apache-2.0 alpha locally.\n"
                "Historical PR #22 passed its original qualification.\n"
            )
            _, errors = audit(root, [status_doc, historical])
            assert not errors, errors
            for phrase in STALE_QUALIFICATION_PHRASES:
                status_doc.write_text(
                    "# Current\n" + phrase.upper().replace(" ", "\n") + "\n"
                )
                _, errors = audit(root, [status_doc, historical])
                assert len(errors) == 1 and "stale current-document" in errors[0], (
                    errors
                )
    print(
        "Documentation checker self-test passed (valid links, four link/language negatives, "
        f"eight stale-license negatives and {len(CURRENT_DOCS) * len(STALE_QUALIFICATION_PHRASES)} "
        f"stale-qualification negatives across {len(CURRENT_DOCS)} current docs; historical language accepted)"
    )


def main():
    if sys.argv[1:] == ["--self-test"]:
        self_test()
        return
    if sys.argv[1:]:
        raise SystemExit("Usage: validate-documentation.py [--self-test]")
    names = (
        subprocess.check_output(
            ["git", "ls-files", "-z", "-co", "--exclude-standard"],
            cwd=ROOT,
        )
        .decode()
        .split("\0")
    )
    files = [
        ROOT / name
        for name in sorted(set(names))
        if name.lower().endswith(".md") and (ROOT / name).is_file()
    ]
    checked, errors = audit(ROOT, files)
    if errors:
        raise SystemExit("\n".join(errors))
    print(
        f"{len(files)} Markdown files; {checked} relative links/anchors valid; current-document language clean"
    )


if __name__ == "__main__":
    main()
