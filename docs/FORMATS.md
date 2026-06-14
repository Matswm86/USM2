# USM2 File Format Notes (reverse-engineering log)

Source: retail English build of *Ultimate Soccer Manager 2* (Impressions/Sierra,
1996/97), extracted from `backup/Div/Ultimate Soccer Manager 2.zip`. 125 files.
Launcher `_USM2.BAT` runs `LEAGUE.EXE` (errorlevel selector) then `USM2E/F/G.EXE`
(English/French/German language builds, DOS4GW protected mode, Miles AIL sound).

## Status legend
✅ solved & decoded · 🟡 structure known, refinement pending · 🔴 not yet decoded

---

## ✅ Databases — DECODED (`tools/decode_db.py` → `decoded/*.json`)

Fixed-length records, CP850 text, NUL-padded fields, then binary stat blocks.

### TEAM{F,G,N}.DAT — 246-byte records
| offset | size | field |
|--------|------|-------|
| 0   | 20 | team name |
| 20  | 20 | manager name |
| 40  | 20 | short name |
| 60  | 20 | stadium name |
| 80  | 166 | stats (finances / reputation / division / colours — `stats_raw`) |

Counts: French 190, German 220, English 226 teams (suffix F/G/N; N=English,
verified: Arsenal/Wenger/Highbury, matches the USME0001 career save). Names
verified real (Auxerre → Guy Roux, Bordeaux → Parc Lescure). German set uses
deliberately altered names (Bayern Monchen, 1.FC Koln) exactly as the original
shipped — a 1990s licensing workaround, preserved as-is.

**Division byte = stats_raw[7] (file offset 87).** For the English DB:
`0`=Premier (20), `1`=Div1 (24), `2`=Div2 (24), `3`=Div3 (24), `4`=Conf (22)
→ 114 English clubs (the playable pyramid); `255`=112 **European clubs**
(Ajax, PSV Eindhoven, Feyenoord, Juventus, Atlético Madrid, FC Porto, Rangers,
B. Dortmund, Auxerre, Rosenborg …) used for the foreign transfer market and
European competition. Their squads carry real names (Kluivert, Overmars, Davids,
Seedorf, Cocu, Reiziger, Bergkamp, Zidane, Batistuta, Maldini, Raúl); only 28 of
4288 records in PLAYERN are placeholders.

### PLAYER{F,G,N}.DAT — 144-byte records
| offset | size | field |
|--------|------|-------|
| 0   | 14 | first name |
| 14  | 14 | surname |
| 28  | ~0x4F | flags / position / club ref (`mid_raw`) |
| 0x77 | tail | skill cluster, values 0–100 (`skills_raw`) |

Counts: French 3379, German 4150, Dutch 4288 players (~99% real names).
Skill-column semantics (tackling/passing/shooting/pace/stamina/handling/…)
still being mapped by cross-referencing known players — 🟡.

### Other data files — 🔴 to decode
COACH.DAT (4680), FORM.DAT (8712, formations), ADVERT.DAT (6200),
*.FOR (1522 ea, 18 saved formations), SECTOR.MAP / STADIUMS.MAP,
GAME.TXT (108k, in-game text/commentary strings), MANAGERS.NAM.
Save set: USME0001.{SVE 1.3M, MCH, PHS, THS} = English career snapshot
(real English names: Darren Royle, Michael Carmody…).

---

## ✅ Graphics — `PAK2` SOLVED & visually verified (`tools/decode_pic.py`)

Algorithm read directly from the USM2E.EXE disassembly (obj1, vbase 0x10000,
decompressor at VA `0x9fafc`). It is **NOT LZSS** — it is a simple 2-bit-type
RLE/literal scheme with no ring buffer. (Earlier "embedded 768-byte palette /
LZSS ring" notes were wrong inferences that only ever produced noise; the
`byte[4:7]` field I misread as palette size is the **total file size,
big-endian** — e.g. TITLE.PIC = 0x00014DA7 = 85415 = its exact size.)

```
Header:
  0-3   "PAK2" magic
  4-7   total file size (big-endian u32)
  8     save1  — palette index emitted by type-01 tokens
  9     save2  — palette index emitted by type-10 tokens
  10+   token stream

Each token = 1 code byte: bits[7:6]=type, bits[5:0]=lower6, run = lower6+1 (1..64)
  11 (0xC0): literal run — next (run) bytes are raw pixel indices   (VA 0x9fba7)
  10 (0x80): emit save2  (run) times                                (VA 0x9fbb6)
  01 (0x40): emit save1  (run) times                                (VA 0x9fbce)
  00 (0x00): read 1 more byte, emit it (run) times                  (VA 0x9fbe2)

Palette: external, ALL.PAL (6144 = 8 × 256-colour VGA palettes, 6-bit ×4 → 8-bit).
Dimensions: 640×480 (all screens); TV.PIC = 83×932. First frame only (files may
concatenate further overlay frames after the first image).
```

Verified by **rendering and eye** (the real oracle): TITLE = the globe
"ULTIMATE SOCCER MANAGER" logo; MAINSCR = office toolbar + desk; BANKSCR =
photographic bank-manager scene, crisp. **Lesson recorded:** the vertical-
coherence oracle (`vscore`) only works on flat graphics (TITLE/TV ≈ 0.80);
photographic/dithered screens score ~0.31–0.38 *when perfectly decoded*, so the
`>0.6` threshold was a bad gate — visual inspection is the correctness check.

PIC inventory: TITLE, START, MAINSCR (office), MANASCR (manager), BANKSCR (bank),
CHAIRSCR (chairman), BENCHSCR (dugout), NEWS, TV, MATCH{SCR,BAR,ICE,MUD,WET}
(pitch states). All 14 decode → `decoded/pics/*.png`.

**Open polish:** per-screen palette selection — decoder uses ALL.PAL set 0 for
all; MAINSCR/BANKSCR look correct, but TITLE's globe has colour speckle that may
mean some screens use a different one of the 8 palette sets (the game switches
palettes at load). Refine by testing palette index 0–7 per screen.

## 🔴 Other graphics
- ALL.PAL — 6144 bytes = 8 × 256-colour palettes (0–63 VGA), shared palettes.
- *.BIT — fonts (TITLEFNT, BIGFONT, NEWFONT, SMALLFNT…) + sheets (STADIUM 1.9M,
  GISTAD, SHOP, TOOLS, GROUND, MANAGERS).
- *.SPR — sprites (PITCH.SPR match sprites, POINTER.SPR cursor).
- *.ANM — animations (CHAIRMAN, MANAICON, BANKICON, BENCHICN).
- ANIMS/*.SMK — Smacker video stubs (500 bytes each = placeholders here).

## Audio (lower priority; modern engine can resynthesise)
*.WAV (crowd/SFX: CHEER, ROAR, JEER, WHIS*, BOOLIT…), MUSIC/MUSIC1.WAV,
*.DIG (Miles sound-driver descriptors, not audio).
