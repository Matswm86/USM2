#!/usr/bin/env python3
"""Stage decoded USM2 data + artwork into the Android app's assets tree.

Reads the full decoded JSON (tools/decode_db.py output) and the rendered screen
PNGs (tools/decode_pic.py output), and writes a slim, app-ready bundle into
``android/app/src/main/assets/`` (which is gitignored — copyrighted material is
never committed to the public repo).

Merge policy (avoids triplicating the shared European pool):
  * English file  -> canonical for the English pyramid (div 0-4) AND the
                     European club pool (div 255: Ajax, Juventus, Porto, ...).
  * French file   -> French domestic clubs only (div 0-3); its div-255 entries
                     are duplicates of the English European pool and are dropped.
  * German file   -> German domestic clubs only (div 0-5); ditto.

Club identity is global: ``<LG>-<team_index>`` (LG in EN/FR/DE). A player's club
key is ``<own LG>-<club_id - 1>`` (club_id is 1-based; see decode_db.py). Players
with no real club (id 0/255 or out of range) get ``club = null`` -> Free Agents.
"""
from __future__ import annotations

import json
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DECODED = ROOT / "decoded"
ASSETS = ROOT / "android" / "app" / "src" / "main" / "assets"

# (league file suffix, short code, UI group). English carries Europe; FR/DE are
# domestic-only so the European pool is not duplicated three times.
LEAGUES = [("english", "EN", "England"), ("french", "FR", "France"), ("german", "DE", "Germany")]

EUROPE_DIV = 255  # div byte marking a club in the shared European pool

# Human labels for the top of each domestic ladder; deeper tiers fall back to a
# generic "Division N". Europe gets its own group label.
DIVISION_LABELS = {
    "EN": {0: "Premier League", 1: "Division One", 2: "Division Two", 3: "Division Three", 4: "Conference"},
    "FR": {0: "Division 1", 1: "Division 2", 2: "National", 3: "Division 4"},
    "DE": {0: "Bundesliga", 1: "2. Bundesliga", 2: "Regionalliga", 3: "Tier 4", 4: "Tier 5", 5: "Tier 6"},
}


def division_label(code: str, div: int) -> str:
    return DIVISION_LABELS.get(code, {}).get(div, f"Division {div + 1}")


def build() -> dict:
    clubs: list[dict] = []
    players: list[dict] = []

    for league, code, group in LEAGUES:
        teams = json.loads((DECODED / f"teams_{league}.json").read_text())
        squad = json.loads((DECODED / f"players_{league}.json").read_text())
        by_index = {t["index"]: t for t in teams}

        # English file owns Europe; French/German contribute domestic clubs only.
        keep_europe = code == "EN"
        for t in teams:
            div = t["division"]
            is_europe = div == EUROPE_DIV
            if is_europe and not keep_europe:
                continue
            clubs.append(
                {
                    "id": f"{code}-{t['index']}",
                    "name": t["name"],
                    "short": t["short_name"],
                    "manager": t["manager"],
                    "stadium": t["stadium"],
                    "group": "Europe" if is_europe else group,
                    "division": -1 if is_europe else div,
                    "divisionName": "International" if is_europe else division_label(code, div),
                }
            )

        known = set(by_index)  # valid 0-based team indices in this file
        for p in squad:
            club_idx = p["club_id"] - 1  # 1-based -> 0-based team index
            club_key = f"{code}-{club_idx}" if club_idx in known else None
            if club_key is not None:
                club = by_index[club_idx]
                # Drop FR/DE players attached to a European-pool club: those are
                # duplicates of the English file's canonical European squads.
                if code != "EN" and club["division"] == EUROPE_DIV:
                    continue
            players.append(
                {
                    "name": f"{p['first_name']} {p['surname']}".strip(),
                    "age": p["age"],
                    "key": p["key_player"],
                    "skills": p["skills"],
                    "club": club_key,
                    "league": code,
                }
            )

    return {"clubs": clubs, "players": players}


def main() -> int:
    if not DECODED.exists():
        print("decoded/ missing — run tools/decode_db.py first", file=sys.stderr)
        return 1

    data = build()
    data_dir = ASSETS / "data"
    img_dir = ASSETS / "img"
    data_dir.mkdir(parents=True, exist_ok=True)
    img_dir.mkdir(parents=True, exist_ok=True)

    (data_dir / "clubs.json").write_text(json.dumps(data["clubs"], ensure_ascii=False, separators=(",", ":")))
    (data_dir / "players.json").write_text(json.dumps(data["players"], ensure_ascii=False, separators=(",", ":")))

    pics = DECODED / "pics"
    copied = 0
    if pics.exists():
        for png in sorted(pics.glob("*.png")):
            shutil.copy2(png, img_dir / png.name)
            copied += 1

    free = sum(1 for p in data["players"] if p["club"] is None)
    print(
        json.dumps(
            {
                "clubs": len(data["clubs"]),
                "players": len(data["players"]),
                "free_agents": free,
                "images": copied,
                "out": str(ASSETS),
            },
            indent=2,
        )
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
