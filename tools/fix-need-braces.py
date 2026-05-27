#!/usr/bin/env python3
"""Wrap single-statement if/while/for/else-if bodies in braces (Checkstyle NeedBraces)."""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCES = [ROOT / "auction-common" / "src", ROOT / "auction-server" / "src"]

# if / while / for, optional leading "} else if" or "} else"
CTRL = re.compile(
    r"^(\s*)((?:\}\s*)?(?:else\s+)?)(if|while|for)\s*\(",
    re.MULTILINE,
)


def matching_paren_index(text: str, open_index: int) -> int:
    depth = 0
    in_str = None
    escape = False
    for i in range(open_index, len(text)):
        ch = text[i]
        if in_str:
            if escape:
                escape = False
            elif ch == "\\":
                escape = True
            elif ch == in_str:
                in_str = None
            continue
        if ch in "\"'":
            in_str = ch
            continue
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
            if depth == 0:
                return i
    return -1


def transform_line(line: str) -> str:
    text = line.rstrip("\n\r")
    if not text.rstrip().endswith(";"):
        return line
    # do-while: } while (cond); — do not wrap the while header
    if re.search(r"\}\s*while\s*\(", text):
        return line
    m = CTRL.match(text)
    if not m:
        return line
    open_paren = text.index("(", m.end() - 1)
    close_paren = matching_paren_index(text, open_paren)
    if close_paren < 0:
        return line
    after = text[close_paren + 1 :].lstrip()
    if not after or after.startswith("{") or not after.endswith(";"):
        return line
    # Do not touch for-loop headers like for (;;)
    if m.group(3) == "for" and after == ";":
        return line
    indent = m.group(1)
    prefix = m.group(2)
    kw = m.group(3)
    condition = text[open_paren : close_paren + 1]
    body = after[:-1].strip()  # drop trailing ;
    inner = indent + "  "
    header = f"{indent}{prefix}{kw}{condition} {{"
    return f"{header}\n{inner}{body};\n{indent}}}\n"


def process_file(path: Path) -> bool:
    original = path.read_text(encoding="utf-8")
    lines = original.splitlines(keepends=True)
    out: list[str] = []
    changed = False
    for line in lines:
        new_line = transform_line(line)
        if new_line != line:
            changed = True
            if not new_line.endswith("\n"):
                new_line += "\n"
            out.append(new_line)
        else:
            out.append(line)
    if changed:
        path.write_text("".join(out), encoding="utf-8")
    return changed


def main() -> int:
    count = 0
    for base in SOURCES:
        for path in base.rglob("*.java"):
            if process_file(path):
                count += 1
                print(path.relative_to(ROOT))
    print(f"Updated {count} files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
