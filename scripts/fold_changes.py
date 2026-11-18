#!/usr/bin/env python3
"""Fold changes/<issue>.md fragments into CHANGELOG.md under a new version, then delete them.

Usage: python scripts/fold_changes.py 1.8.0 [--date 2026-12-01] [--dry-run]

Each fragment is `kind: <kind>` on the first line, then the text. Kinds map to Keep a Changelog
headings; `internal` is dropped. The fragments land under a new `## [version] - date` heading placed
directly after `## [Unreleased]`, together with anything already written under Unreleased.
"""
import argparse
import datetime
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
HEADINGS = {"added": "Added", "changed": "Changed", "fixed": "Fixed", "removed": "Removed", "security": "Security"}
ORDER = ["Added", "Changed", "Fixed", "Removed", "Security"]


def read_fragments():
    items = []
    for path in sorted((ROOT / "changes").glob("*.md"), key=lambda p: p.name):
        if not re.fullmatch(r"\d+(-[a-z0-9-]+)?\.md", path.name):
            continue
        lines = path.read_text(encoding="utf-8").replace("\r\n", "\n").strip().split("\n")
        match = re.fullmatch(r"kind:\s*(\w+)", lines[0].strip())
        if not match or match.group(1) not in HEADINGS | {"internal": ""}:
            sys.exit(f"{path.name}: first line must be 'kind: added|changed|fixed|removed|security|internal'")
        text = " ".join(line.strip() for line in lines[1:] if line.strip())
        if not text:
            sys.exit(f"{path.name}: no text after the kind line")
        items.append((path, match.group(1), text))
    return items


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("version")
    parser.add_argument("--date", default=datetime.date.today().isoformat())
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    items = read_fragments()
    if not items:
        sys.exit("no fragments in changes/")

    changelog = ROOT / "CHANGELOG.md"
    raw = changelog.read_bytes().decode("utf-8")
    crlf = "\r\n" in raw
    text = raw.replace("\r\n", "\n")

    marker = "## [Unreleased]\n"
    start = text.index(marker) + len(marker)
    nxt = re.search(r"^## \[", text[start:], re.M)
    end = start + nxt.start() if nxt else len(text)
    existing = text[start:end].strip("\n")

    sections = {name: [] for name in ORDER}
    for _, kind, body in items:
        if kind != "internal":
            sections[HEADINGS[kind]].append(f"- {body}")
    new_body = []
    for name in ORDER:
        if sections[name]:
            new_body.append(f"### {name}\n" + "\n".join(sections[name]) + "\n")
    folded = f"## [Unreleased]\n\n## [{args.version}] - {args.date}\n\n"
    if existing:
        folded += existing + "\n\n"
    folded += "\n".join(new_body) + "\n"

    result = text[: start - len(marker)] + folded + text[end:]
    if crlf:
        result = result.replace("\n", "\r\n")
    if args.dry_run:
        print(folded)
        return
    changelog.write_bytes(result.encode("utf-8"))
    for path, _, _ in items:
        path.unlink()
    print(f"Folded {len(items)} fragments into CHANGELOG.md as {args.version}")


if __name__ == "__main__":
    main()
