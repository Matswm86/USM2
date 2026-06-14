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

## 🟡 Graphics — `PAK2` container header SOLVED, body packer PENDING

`.PIC` = full-screen art. Header:
```
0   4   "PAK2" magic
4   2   palette_size (u16 LE)   768 = 256 colours×3 (8-bit RGB, NOT 6-bit VGA);
                                 0 = use ALL.PAL
6   N   palette bytes (palette_size)
6+N ..  packed image body  <-- packer not yet cracked
```
Body is NOT plain PCX-RLE (over-expands). Likely custom RLE or LZSS — the
literal `PAK2` tag + decompressor live in USM2?.EXE. Repeating 13-byte control
groups (`c3 05 08 06 02 04 30 04 1f 06 01 04 10 …`) suggest run/row structure.
**Next step:** disassemble the PAK2 reader in USM2E.EXE OR brute-force the
scheme so decoded length = a clean rectangle (test 640×480 SVGA), then validate
the rendered PNG against the MobyGames reference screenshots.

PIC inventory: TITLE, START, MAINSCR (office), MANASCR (manager), BANKSCR (bank),
CHAIRSCR (chairman), BENCHSCR (dugout), NEWS, TV, MATCH{SCR,BAR,ICE,MUD,WET}
(pitch states).

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
