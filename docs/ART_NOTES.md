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
- **The office screens are already finished art.** CHAIRSCR, MANASCR and BENCHSCR
  decode to complete scenes at set 0 (boardroom, manager's office with a stadium
  window, dugout). MANASCR is now the app's home background. The one bare screen is
  **MAINSCR** — the main-menu desk backdrop (dithered carpet/wall); its toolbar +
  desk furniture are sprite overlays the game composites at runtime, but every
  `*.PIC` already has the toolbar baked in, so MAINSCR is not needed for the rebuild.

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
| MAINSCR                                     | 0 | the **main-menu desk backdrop** (bare red carpet/wall) — NOT "the office". The desk furniture is composited from sprite overlays at runtime. |
| CHAIRSCR, MANASCR, BENCHSCR                  | 0 | **complete, finished scenes** — chairman at his desk + stadium window; the manager's office (desk + window onto the stadium, filing cabinet, TV, trophy); the dugout. Full bitmaps, no sprite work needed. |
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

## Sprite banks (.BIT / .SPR) — 2026-06-14 session 4

The `.BIT` / `.SPR` files are PAK2 sprite *banks* (same header + codec as `.PIC`).
`tools/decode_sprites.py` decompresses the **whole** token stream (no 640×480 cap)
and either renders the bank as one sheet or slices a fixed W×H grid with a
transparent index. Key fact: there is **no embedded dimension table** — the
decompressed buffer starts straight on pixel rows (POINTER.SPR's first rows are
already the arrow), so a bank is either a clean known tiling or its frame sizes
live in the EXE blit code.

| file | hdr | decompressed | status |
|------|-----|--------------|--------|
| **POINTER.SPR** | PAK2 | 4864 = 16×16 × 19 | **DONE.** Clean 16×16 grid: frame 0 = arrow cursor, frame 2 = red **OK** button, frames 3-18 = the animated wait-stopwatch. `decode_sprites.py POINTER.SPR --frame 16x16 --transparent 0`. Verified visually (`decoded/sprites/pointer.contact.png`). |
| TOOLS.BIT | PAK2 | 59160 | **Deferred.** A bank of gold-bevelled toolbar/desk icons (visible as a framed-icon grid at any width). 59160 = 2³·3·5·17·29 → NOT one rectangle; variable-size frames. Autocorrelation peaks at ~26 px with 52/78/104 harmonics, but no global clean width → per-frame dims must come from the EXE blit table. **Low marginal value**: the toolbar is already baked into every `*.PIC`; TOOLS.BIT only adds pressed/highlight states. |
| MANAGERS.BIT | raw (not PAK2/PAK1) | 14720 | **Unknown format.** Header is all-zeros (no PAK magic); renders as noise at every clean width (32/46/64/92/160…). Not raw indexed pixels in row order. Defer — probably a different pack or per-record structure; trace the load in the EXE if manager portraits are wanted. |
| PITCH.SPR | PAK2 | 710568 | Decompresses fine; for the **Phase-3 match renderer** (the animated pitch). Slice when that view exists. |
| GROUND.BIT | PAK2 | 396336 | Decompresses fine; crowd/ground sprites for the match/stadium view. Phase-3. |

## What's left for faithful art

1. ~~Per-screen palette map~~ — DONE.
2. ~~Office looks like an office~~ — **DONE, but not via sprites.** The manager's
   office (MANASCR) is a complete finished bitmap, so it is now wired full-bleed as
   the app's home background (`OfficeScreen`), with a bottom scrim under the menu.
   This supersedes the earlier letterboxed TITLE-on-black hero. The premise that
   "only TOOLS.BIT turns MAINSCR into an office" was wrong — MAINSCR is just the
   main-menu desk backdrop; the office is MANASCR and was already done.
3. **Sprite slicing is only needed for Phase 3** now: PITCH.SPR + GROUND.BIT for the
   match view, and TOOLS.BIT pressed-states if/when an interactive toolbar is built
   (needs the EXE blit dims). POINTER.SPR cursors are decoded but unused (touch app).
4. Wire the remaining finished backgrounds (CHAIRSCR boardroom, BENCHSCR dugout,
   BANKSCR, NEWS) as their Phase-3 screens get built — they render correctly today.

## Tools
- `tools/decode_pic.py` — PAK2 decompressor + PNG writer for the 640×480 `.PIC` screens.
- `tools/palette_sweep.py` — 8-set contact sheet / single-set render, to pick the
  correct palette per screen.
- `tools/decode_sprites.py` — PAK2 sprite-bank decoder: `--probe`, `--sheet W`, or
  `--frame WxH [--transparent IDX]` to slice + contact-sheet a bank.
