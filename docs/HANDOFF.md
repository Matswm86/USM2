# USM2 Rebuild — Session Handoff (2026-06-14)

Continuation doc for a fresh session. Phase 1 is **complete**; next is **Phase 2**.
Read this + `docs/FORMATS.md` (the reverse-engineering log) and you have everything.

## What this project is
Native **Android (Kotlin/Compose)** rebuild of *Ultimate Soccer Manager 2*
(Impressions/Sierra, 1997), reusing the original game's real database + original
artwork, re-laid for touch. Personal project for Mats's own phone, built from his
own owned copy (`backup/Div/Ultimate Soccer Manager 2.zip`).
Approach was chosen by Mats: **native rebuild** (decode assets → reuse art + DB →
reimplement UI + an approximation of the match/management engine). Not emulation.

## Scope (locked with Mats)
- **Playable**: the **English** pyramid only (manage an English club, Premier
  League [division byte 0, 20 clubs] down to the Conference).
- **Full world DB loaded** so you can **buy players from other leagues** and
  **play European competition**: the English DB itself carries **112 European
  clubs** (division byte 255: Holland=Ajax/PSV/Feyenoord, plus Juventus, Atlético,
  Porto, Rangers, Dortmund, Auxerre, Rosenborg…) with real squads (Kluivert,
  Overmars, Davids, Bergkamp, Zidane, Batistuta, Maldini, Raúl). Separate France
  (190) + Germany (220) full pyramids add more foreign depth.
- Leagues not in the original files (Italy/Spain *leagues*, etc.) are out of scope
  unless Mats supplies a dataset — do NOT fabricate league data.

## Repo + copyright boundary (CRITICAL)
- Public repo: **github.com/Matswm86/USM2** (created via `gh`, account Matswm86).
- It is **CODE ONLY**. `original/` and `decoded/` are **gitignored** — they are
  Impressions/Sierra copyright and must NEVER be committed/pushed. Before every
  commit run: `git status --short | grep -iE "original/|decoded/|\.PIC|\.DAT|\.png|\.json"`
  → must be empty. (I did this on every push.)
- Commit msgs end with the Co-Authored-By trailer (see git log). Push only what
  Mats asked; he authorized this repo + pushes.

## Workspace layout
```
projects/mwm-games/usm2/
├── original/   # extracted DOS game (125 files) — SOURCE OF TRUTH, gitignored
├── tools/      # decoders (committed):
│   ├── decode_db.py     teams+players -> decoded/*.json
│   ├── decode_pic.py    PAK2 .PIC/.SPR/.BIT/.ANM -> PNG / raw blob
│   └── decode_misc.py   GAME.TXT, ADVERT.DAT, COACH.DAT -> json
├── decoded/    # decoded assets, gitignored (regenerate by running tools/):
│   ├── teams_{english,french,german}.json     (226/190/220 clubs)
│   ├── players_{english,french,german}.json   (4288/3379/4150; age,key_player,skills)
│   ├── pics/*.png      14 verified screen backgrounds
│   ├── gametext.json   1288 commentary templates  (^t = team-name placeholder)
│   ├── adverts.json    100 hoardings · coach.json 195 staff
│   └── PAK2_analysis_notes.md
├── docs/  FORMATS.md (RE log, AUTHORITATIVE) · ROADMAP via README · this file
├── android/    # EMPTY — Phase 2 goes here
├── README.md  LICENSE(.gitignore)
```
`decoded/` persists on disk in THIS workspace (only excluded from git). A fresh
session here will see it. To regenerate: `python3 tools/decode_db.py && python3
tools/decode_misc.py && python3 tools/decode_pic.py`.

## Phase 1 — DONE (all committed)
- Full DB decoded + verified (real names; Auxerre→Guy Roux, Arsenal→Wenger).
- **PAK2 image codec cracked** (from USM2E.EXE disassembly, decompressor @ VA
  0x9fafc): it is a **2-bit-type RLE/literal** scheme (NOT LZSS), palette external
  in ALL.PAL. Header: "PAK2" + filesize(BE u32) + save1 + save2 + token stream.
  All 14 screens render correctly (visually verified). Same codec decompresses
  the sprite/font/anim files.
- GAME.TXT/adverts/staff decoded; player records structured (age@28, flag@33,
  skills@123-133, byte128=const100).

## Phase 1 deferred refinements (NOT blocking; pick up when relevant)
1. **Skill-column names** — skill block located (bytes 123-133) but which byte =
   tackle/pass/shoot/handle/pace is TBD. Get it from the **player-screen display
   code in USM2E.EXE** (labels sit next to the bytes). Do NOT guess — the 0xFF
   flag@33 is a star-player flag, not goalkeeper position.
2. **Sprite-sheet slicing** — .SPR/.BIT/.ANM decompress to raw blobs; per-frame
   w/h live in the EXE blit code, so slice each sheet per-asset during Phase 2
   UI work, validating each cut visually (TOOLS.BIT = toolbar icons, blob 59160).
3. **Per-screen palette** — decoder uses ALL.PAL set 0; office/bank correct, but
   TITLE globe has colour speckle → some screens may use another of the 8 sets.
   Sweep palette 0-7 per screen.

## Phase 2 — NEXT (Android UI shell)
Goal: first installable APK = navigable office screen with real toolbar art,
wired to browse the live database (English squad, league tables, cross-league
transfer list). No match engine yet (that's Phase 3).
Steps:
1. Scaffold `android/` Gradle + Compose project (minSdk ~24, Kotlin).
2. Bundle decoded assets as app resources — BUT they're copyrighted + gitignored,
   so the CI build must pull them from a private source (private companion repo
   or release artifact), NOT the public repo. Decide this with Mats. For local
   dev, load from decoded/.
3. Build the office screen (MAINSCR.png background + TOOLS.BIT icons sliced) and a
   DB-browser (RecyclerView/LazyColumn over players_english.json).
4. **CI build only** — wire a GitHub Actions workflow that builds the APK and
   publishes a rolling `latest` release (matches Mats's tile-explorer/pcleague
   convention). NEVER run `./gradlew` on this host: 8GB RAM, gradle OOM-kills the
   build AND the session (exit137). This is a hard workspace rule
   (`android_build_via_ci_not_local`).

## Hard lessons from this session (apply them)
- **Verify agent claims visually.** A background agent twice reported PAK2
  "SOLVED" on bad metrics (length match, adjacency); both were noise/false. Only
  rendering the office/bank screen and LOOKING confirmed the real solution. Never
  trust an agent's "done" on a load-bearing claim — check the real artifact.
- **Don't brute-force binary formats** with weak oracles — go to the disassembly.
  Code is NOT packed (entropy 6.15); obj1 @ file 0x33800, vbase 0x10000; disasm:
  `objdump -D -b binary -m i386 -M intel --adjust-vma=0x10000`. capstone won't
  pip-install (PEP668; use --break-system-packages if needed).
- League suffix **N = English** (not Dutch); German DB uses deliberately altered
  names (Bayern Monchen) exactly as the original shipped — preserve as-is.

## Task list state
#1 Phase 1 decode = COMPLETED. #2 Phase 2 UI, #3 Phase 3 engine, #4 Phase 4
CI/balance = pending. Re-create these in the fresh session if the task list resets.
