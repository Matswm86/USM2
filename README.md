# USM2 — native Android rebuild

A from-scratch native Android (Kotlin/Compose) rebuild of *Ultimate Soccer
Manager 2* (Impressions/Sierra, 1997), reusing the original game's real
database (teams, managers, players, stadiums) and original artwork, re-laid for
touch. Personal project for Mats's own phone, built from his own owned copy.

> Approach chosen 2026-06-14: **native rebuild** (decode original assets → reuse
> art + full DB → reimplement UI and an approximation of the match/management
> engine). The match-sim behaviour approximates the original; the DOS binary's
> exact logic cannot be perfectly recovered.

## Layout
```
usm2/
├── original/   # extracted DOS game (source of truth, not redistributed)
├── tools/      # Python decoders (DAT→JSON, PAK2/PIC→PNG, etc.)
├── decoded/    # decoded open assets (JSON db, PNG art)
├── docs/       # FORMATS.md (RE log), ROADMAP.md
└── android/    # Kotlin/Compose app (built via GitHub Actions CI)
```

## Scope / rollout
The original game files contain three databases: **England, France, Germany**.
- **v1: England** — full pyramid (Premier League + lower divisions), playable
  first.
- **v2: top tiers** of the other originals — French Ligue 1 + German Bundesliga
  (top-division clubs, division byte `0`).
- Leagues not in the original files (Italy, Spain, …) would need non-original
  data and are out of scope unless a licensed/community dataset is supplied.

## Build phases (tracked in the session task list)
1. **Decode assets** — DB ✅ (636 teams / ~11.8k players → JSON); graphics 🟡
   (PAK2 header solved, body packer pending).
2. **Rebuild UI** — office/team/transfers/finance/chairman/news/match screens in
   Compose, original art re-laid for touch, original navigation flow.
3. **Reimplement engine** — fixtures, tables, transfers, finances, training,
   board confidence, match simulation driven by decoded ratings.
4. **CI build + balance** — APK via GitHub Actions (never local gradle, per the
   8GB-host OOM constraint), rolling `latest` release + README download link;
   balance & playtest.

## Assets & how to build a complete game
This repo is **code only**. No copyrighted game data or artwork is included.
To produce a populated build you must own a copy of USM2 and extract its assets
yourself:
```bash
# place your USM2 files in original/ (gitignored), then:
python3 tools/decode_db.py      # -> decoded/*.json   (database)
python3 tools/decode_pic.py     # -> decoded/pics/*.png (artwork)  [WIP]
```
The decoded assets land in `decoded/` (gitignored). How those personal assets
get bundled into the CI-built APK without publishing them is a packaging step
handled outside this public repo (private asset source / release artifact).

## Decode toolkit
- `tools/decode_db.py` → `decoded/teams_*.json`, `decoded/players_*.json`
- `tools/decode_pic.py` → PNG screens (pending PAK2 body crack)

See `docs/FORMATS.md` for the full reverse-engineering log.
