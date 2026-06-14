#!/usr/bin/env python3
"""Decode USM2 auxiliary resources to JSON: text string-table, staff, adverts.

GAME.TXT  : u32 offset table (offset[0] = start of string data = table size),
            then NUL-terminated CP850 strings. Match-report/commentary templates
            with ^t placeholders (team-name substitution).
ADVERT.DAT: NUL-terminated CP850 advert-hoarding names.
COACH.DAT : staff records ("Page" section marker + names + attribute bytes).
"""
from __future__ import annotations

import json
import re
import struct
import sys
from pathlib import Path

ORIG = Path(__file__).resolve().parent.parent / "original"
OUT = Path(__file__).resolve().parent.parent / "decoded"


def decode_gametext(path: Path) -> list[str]:
    d = path.read_bytes()
    table_size = struct.unpack("<I", d[0:4])[0]  # offset[0] points past the table
    n = table_size // 4
    offsets = struct.unpack(f"<{n}I", d[: n * 4])
    strings = []
    for o in offsets:
        if o == 0 or o >= len(d):
            strings.append("")
            continue
        strings.append(d[o:].split(b"\x00", 1)[0].decode("cp850", "replace"))
    return strings


def decode_adverts(path: Path) -> list[str]:
    d = path.read_bytes()
    # Printable CP850 runs >= 3 chars, de-duplicated in order.
    seen, out = set(), []
    for m in re.finditer(rb"[ -~\x80-\xff]{3,}", d):
        s = m.group().decode("cp850", "replace").strip()
        if s and s not in seen:
            seen.add(s)
            out.append(s)
    return out


def decode_coach(path: Path) -> dict:
    d = path.read_bytes()
    names = [m.group().decode("cp850", "replace")
             for m in re.finditer(rb"[A-Za-z][A-Za-z'\- ]{2,}", d)]
    return {"size": len(d), "name_count": len(names), "names": names}


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    summary = {}

    if (ORIG / "GAME.TXT").exists():
        strings = decode_gametext(ORIG / "GAME.TXT")
        (OUT / "gametext.json").write_text(json.dumps(strings, ensure_ascii=False, indent=2))
        nonempty = [s for s in strings if s]
        summary["gametext_entries"] = len(strings)
        summary["gametext_nonempty"] = len(nonempty)
        summary["gametext_sample"] = nonempty[1:3]

    if (ORIG / "ADVERT.DAT").exists():
        ads = decode_adverts(ORIG / "ADVERT.DAT")
        (OUT / "adverts.json").write_text(json.dumps(ads, ensure_ascii=False, indent=2))
        summary["advert_count"] = len(ads)
        summary["advert_sample"] = ads[:6]

    if (ORIG / "COACH.DAT").exists():
        coach = decode_coach(ORIG / "COACH.DAT")
        (OUT / "coach.json").write_text(json.dumps(coach, ensure_ascii=False, indent=2))
        summary["coach_names"] = coach["name_count"]

    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
