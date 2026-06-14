#!/usr/bin/env python3
"""Find the correct ALL.PAL palette set for a PAK2 screen.

The PAK2 decompressor (decode_pic.py) is correct: every .PIC consumes its byte
stream exactly and produces exactly W*H indices. What was wrong in the first
pass was the PALETTE — decode_pic.py hardcodes ALL.PAL set 0 for every screen,
but each screen picks one of the 8 sets. TITLE, for example, is set 6 (clean
blue football + red "SOCCER"); set 0 renders it as muddy speckle.

Usage:
  python3 tools/palette_sweep.py TITLE            # 4x2 contact sheet of all 8 sets
  python3 tools/palette_sweep.py TITLE --set 6    # full-size render with one set

Eyeball the contact sheet, pick the set that looks right, and record it in the
per-screen palette map (see docs/ART_NOTES.md). Background-texture screens
(MAINSCR office, BANKSCR) will still look bare under every set because the desk
/ panels are separate sprite overlays composited at runtime, not part of the
.PIC — those need the sprite pipeline, not a palette.
"""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).parent))
from decode_pic import SCREEN_HEIGHT as H
from decode_pic import SCREEN_WIDTH as W
from decode_pic import TV_HEIGHT, TV_WIDTH, decode_pak2, load_palette

ROOT = Path(__file__).resolve().parent.parent


def render(name: str, palette_set: int) -> Image.Image:
    w, h = (TV_WIDTH, TV_HEIGHT) if name.upper() == "TV" else (W, H)
    raw = (ROOT / "original" / f"{name}.PIC").read_bytes()
    px = decode_pak2(raw, w, h)
    im = Image.new("P", (w, h))
    im.putpalette(list(load_palette(ROOT / "original" / "ALL.PAL", palette_set)))
    im.putdata(px)
    return im.convert("RGB")


def contact_sheet(name: str, out: Path) -> None:
    tw, th, cols, rows, pad = 200, 150, 4, 2, 8
    sheet = Image.new("RGB", (cols * tw + (cols + 1) * pad, rows * (th + 18) + pad), (20, 20, 20))
    d = ImageDraw.Draw(sheet)
    for i in range(8):
        thumb = render(name, i).resize((tw, th))
        c, r = i % cols, i // cols
        x, y = pad + c * (tw + pad), pad + r * (th + 18)
        sheet.paste(thumb, (x, y))
        d.text((x + 4, y + th + 2), f"set {i}", fill=(255, 255, 255))
    sheet.save(out)


def main() -> None:
    ap = argparse.ArgumentParser(description="Sweep ALL.PAL palette sets for a PAK2 screen")
    ap.add_argument("name", help="screen stem, e.g. TITLE (looks in original/<name>.PIC)")
    ap.add_argument("--set", type=int, default=None, help="render full-size with this set instead of a sheet")
    ap.add_argument("--out", default=None, help="output PNG path")
    args = ap.parse_args()

    if args.set is not None:
        out = Path(args.out or f"/tmp/{args.name}_set{args.set}.png")
        render(args.name, args.set).save(out)
    else:
        out = Path(args.out or f"/tmp/{args.name}_palsweep.png")
        contact_sheet(args.name, out)
    print(out)


if __name__ == "__main__":
    main()
