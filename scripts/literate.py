#!/usr/bin/env python3
"""Expand the named file roots and weave their shared engineering manual."""
from pathlib import Path
import argparse
import re
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
chapters = sorted((root / "src").glob("*.nw"))
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--check", action="store_true", help="report generated files that differ")
args = parser.parse_args()
stale = []

def emit(path, data):
    if path.is_file() and path.read_bytes() == data:
        return
    if args.check:
        stale.append(str(path.relative_to(root)))
    else:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)

for chapter in chapters:
    for name in re.findall(r"^<<file: (.+?)>>=$", chapter.read_text(), re.M):
        dest = root / "code" / name
        if not dest.resolve().is_relative_to((root / "code").resolve()):
            raise ValueError(name)
        emit(dest, subprocess.check_output(["notangle", "-Rfile: " + name, str(chapter)]))
html = subprocess.check_output(["noweave", "-html", "-n", "-x", *[str(c.relative_to(root)) for c in chapters]], cwd=root)
emit(root / "docs" / "index.html", html + b"</body></html>\n")
if stale:
    sys.exit("Regenerate: " + ", ".join(stale))
