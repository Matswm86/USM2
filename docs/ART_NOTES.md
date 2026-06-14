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

## What's left for faithful art

1. **Per-screen palette map.** Run `palette_sweep.py <screen>` for each of the 14
   screens, eyeball the contact sheet, record the right set. Then make
   `decode_pic.py` take a `{stem: set}` map (default 0) instead of a global index,
   and re-stage. Confirmed so far: `TITLE = 6`. The map probably also exists in
   USM2E.EXE as a per-screen palette index — worth finding it there rather than
   eyeballing all 14.
2. **Sprite pipeline.** Decompress `TOOLS.BIT` / `*.BIT` (same PAK2 codec works on
   them per Phase 1), slice per-frame using the blit dimensions in the EXE, and
   composite onto the background screens so the office/manager/bank views look
   like the game. This is the bigger piece.
3. Only then wire real backgrounds into the app. Until then the app uses clean
   themed headers (see `OfficeScreen`); the decoded `.PIC` PNGs bundled in assets
   are the set-0 versions and are not yet shown.

## Tools
- `tools/decode_pic.py` — PAK2 decompressor + PNG writer (palette index is a CLI flag).
- `tools/palette_sweep.py` — 8-set contact sheet / single-set render, to pick the
  correct palette per screen.
