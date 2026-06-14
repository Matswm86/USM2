# Artwork decode notes

Status of the screen-graphics pipeline, after the 2026-06-14 diagnosis.

## TL;DR

- The **PAK2 decompressor is correct.** Every `.PIC` consumes its compressed
  byte stream *exactly* (0 leftover bytes) and emits exactly `W*H` palette
  indices. For a wrong token model that near-never happens across multiple files,
  so the 2-bit-type RLE scheme in `decode_pic.py` is right. The indices are good.
- The thing that made screens "look like noise" is **palette selection**, not the
  codec. `decode_pic.py` hardcodes ALL.PAL **set 0** for every screen, but each
  screen uses one of the 8 sets. **TITLE is set 6** (clean blue football + red
  "SOCCER"); under set 0 it is muddy speckle. Use `tools/palette_sweep.py` to find
  the right set per screen.
- Some screens are **background textures, not finished scenes.** MAINSCR (the
  "office") decodes to a dithered carpet/wall under every palette — the desk,
  panels and buttons are **separate sprite overlays** the game composites at
  runtime (TOOLS.BIT and the `.BIT` files), not part of MAINSCR.PIC. No palette
  turns MAINSCR into an office; that needs the sprite pipeline.

## Evidence (diagnostic run)

| screen  | stream consumed | out px      | body entropy | note |
|---------|-----------------|-------------|--------------|------|
| MAINSCR | 227396/227396   | 307200/307200 | 2.66 b/px (16 idx, 234-239) | dithered texture, set 0 = red |
| TITLE   | 85415/85415     | 307200/307200 | 4.12 b/px (235 idx)        | real image; **set 6** is correct |
| BANKSCR | 243541/243541   | 307200/307200 | 5.45 b/px                  | photo background |
| NEWS    | 158737/158737   | 307200/307200 | 1.95 b/px (idx 48-50)      | flat panel base |

Exact stream consumption on all four ⇒ decompression is sound.

## Per-screen palette map — RESOLVED (2026-06-14 session 3)

Swept all 14 screens (`palette_sweep.py <screen>` → contact sheet) and inspected
each full-size. Result is now baked into `decode_pic.py` as `PER_SCREEN_PALETTE`
(default `DEFAULT_PALETTE = 0`):

| screen(s)                                   | set | basis |
|---------------------------------------------|-----|-------|
| TITLE                                       | **6** | pristine blue football + red "SOCCER" + Impressions credit; set 0 = speckle. **Shipped: app home hero.** |
| BANKSCR, CHAIRSCR, MANASCR, BENCHSCR, NEWS, START | 0 | render with natural colour at set 0 (skin tones, wood, green pitch). Already the staged versions. |
| MAINSCR                                     | 0 | bare carpet/wall texture under every set — needs the sprite pipeline, not a palette. |
| TV                                          | 0 | 83×932 ticker/scoreboard strip, not a full screen background. |
| MATCHSCR, MATCHMUD, MATCHWET, MATCHICE, MATCHBAR | (none) | **NOT a static map entry — see below.** |

**Why the match family is not in the static map.** USM2E.EXE carries live
"Pitch Quality : Wet / Mud / Ice / ..." strings (file ~0xedcd0) and only one
palette file (`all.pal`); the game tints the pitch from live pitch-condition
state. So the match-screen palette belongs to the **Phase-3 match renderer**, not
this static decode. Visual finding for when that lands: **set 1** keeps the
weather distinct (green grass + brown mud/worn patches), while set 5 over-saturates
everything to flat green and loses the condition; MATCHICE wants set 0/6 (blue
frost). These are unused until a match view exists, so they are left at the
default (0) in staging rather than baking a guess.

There was **no static screen→palette index table** to recover from the EXE: the
`.pic` filenames sit in a packed C-string literal block with no interleaved
palette bytes, and palette selection is code/state-driven.

## What's left for faithful art

1. ~~Per-screen palette map~~ — DONE (above).
2. **Sprite pipeline.** Decompress `TOOLS.BIT` / `*.BIT` / `pitch.spr` (same PAK2
   codec works on them per Phase 1), slice per-frame using the blit dimensions in
   the EXE, and composite onto the background screens so the office/manager/bank
   views look like the game. This is the bigger piece, and the only thing that
   turns MAINSCR into an office.
3. Wire the remaining real backgrounds into the app **as their game screens get
   built** (Phase 3): BANK/CHAIR/MANA/BENCH/NEWS render correctly at set 0 today
   but have no screen to attach to yet. TITLE (set 6) is already wired as the
   `OfficeScreen` home hero.

## Tools
- `tools/decode_pic.py` — PAK2 decompressor + PNG writer (palette index is a CLI flag).
- `tools/palette_sweep.py` — 8-set contact sheet / single-set render, to pick the
  correct palette per screen.
