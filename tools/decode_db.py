#!/usr/bin/env python3
"""Decode USM2 team/player/coach databases into JSON.

Original DOS data files use fixed-length records with CP850-encoded,
NUL-padded text fields followed by binary stat blocks. Record sizes were
recovered by locating the offset delta between consecutive name fields:

    TEAM*.DAT   : 246-byte records  (name@0, manager@20, short@40, stadium@60)
    PLAYER*.DAT : 144-byte records  (first@0[14], surname@14[14], skills@~0x77)

Skill-byte semantics are still being mapped (see attrs_raw); confirmed columns
are labelled, the rest are preserved verbatim so nothing is lost.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

ORIG = Path(__file__).resolve().parent.parent / "original"
OUT = Path(__file__).resolve().parent.parent / "decoded"

TEAM_REC = 246
PLAYER_REC = 144

# Leagues shipped as separate DB triples. Verified by decoded content:
#   F = French  (Auxerre/Guy Roux), G = German (Bayern Monchen, altered names),
#   N = English (Arsenal/Wenger/Highbury) — matches the USME0001 career save.
LEAGUES = {"F": "french", "G": "german", "N": "english"}


def cstr(b: bytes) -> str:
    """Decode a NUL-terminated CP850 field, trimming trailing padding."""
    return b.split(b"\x00", 1)[0].decode("cp850", "replace").strip()


def decode_teams(path: Path) -> list[dict]:
    data = path.read_bytes()
    n = len(data) // TEAM_REC
    teams = []
    for i in range(n):
        r = data[i * TEAM_REC : (i + 1) * TEAM_REC]
        name = cstr(r[0:20])
        if not name:
            continue
        teams.append(
            {
                "index": i,
                "name": name,
                "manager": cstr(r[20:40]),
                "short_name": cstr(r[40:60]),
                "stadium": cstr(r[60:80]),
                "stats_raw": list(r[80:]),  # finances/reputation/division/etc.
            }
        )
    return teams


def decode_players(path: Path) -> list[dict]:
    data = path.read_bytes()
    n = len(data) // PLAYER_REC
    players = []
    for i in range(n):
        r = data[i * PLAYER_REC : (i + 1) * PLAYER_REC]
        first = cstr(r[0:14])
        last = cstr(r[14:28])
        if not first and not last:
            continue
        # Skill cluster observed starting ~0x77; capture the tail for mapping.
        players.append(
            {
                "index": i,
                "first_name": first,
                "surname": last,
                "mid_raw": list(r[28:0x77]),
                "skills_raw": list(r[0x77:]),
            }
        )
    return players


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    summary = {}
    for suffix, league in LEAGUES.items():
        tf = ORIG / f"TEAM{suffix}.DAT"
        pf = ORIG / f"PLAYER{suffix}.DAT"
        if tf.exists():
            teams = decode_teams(tf)
            (OUT / f"teams_{league}.json").write_text(
                json.dumps(teams, ensure_ascii=False, indent=2)
            )
            summary[f"teams_{league}"] = len(teams)
        if pf.exists():
            players = decode_players(pf)
            named = sum(1 for p in players if p["surname"] and not p["surname"].startswith("Player"))
            (OUT / f"players_{league}.json").write_text(
                json.dumps(players, ensure_ascii=False, indent=2)
            )
            summary[f"players_{league}"] = len(players)
            summary[f"players_{league}_real_names"] = named
    print(json.dumps(summary, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
