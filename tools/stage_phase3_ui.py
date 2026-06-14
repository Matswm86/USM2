#!/usr/bin/env python3
"""Stage the faithful-landscape UI assets, derived ONLY from the real game files.

Produces, under android/app/src/main/assets/ (git-tracked, self-contained build):
  img/tb/ic_NN.png     24 toolbar icons cropped from the real baked toolbar
  img/scene/<NAME>.png  each room PIC with the baked toolbar band removed
  data/formations.json  the 18 real formations (base set) from FORM.DAT,
                        normalised to [0,1] pitch coords (GK bottom, attack top)

Run: python3 tools/stage_phase3_ui.py   (then re-run --verify to eyeball crops)
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "android/app/src/main/assets"
IMG = ASSETS / "img"
ORIG = ROOT / "original"

# --- toolbar geometry, measured from the real 640x480 MANASCR.PIC ------------
# Black title bar y0-14; gold top bevel ~15-18; icons ~19-40; bottom bevel 41-42.
TB_TOP, TB_BOT = 15, 43          # toolbar band (icons + bevels)
ICON_X0, ICON_W, N_ICONS = 8, 26, 24   # 24 uniform icons from x=8, 26px wide
SCENE_TOP = TB_BOT               # room scene = everything below the toolbar

ROOMS = ["MANASCR", "CHAIRSCR", "BENCHSCR", "BANKSCR", "NEWS"]


def crop_icons():
    out = IMG / "tb"
    out.mkdir(parents=True, exist_ok=True)
    src = Image.open(IMG / "MANASCR.png").convert("RGB")
    for i in range(N_ICONS):
        x = ICON_X0 + i * ICON_W
        src.crop((x, TB_TOP, x + ICON_W, TB_BOT)).save(out / f"ic_{i:02d}.png")
    return N_ICONS


def crop_scenes():
    """Room scene = the PIC below the toolbar, DENOISED. The art is 256-colour
    dithered DOS graphics; a 3x3 median filter removes the dither speckle (which
    upscales into noise on a phone) while preserving structure and edges."""
    out = IMG / "scene"
    out.mkdir(parents=True, exist_ok=True)
    n = 0
    for name in ROOMS:
        p = IMG / f"{name}.png"
        if not p.exists():
            continue
        im = Image.open(p).convert("RGB")
        scene = im.crop((0, SCENE_TOP, im.size[0], im.size[1]))
        # Median strips the dither speckle; a light blur then blends the residual
        # ordered-dither into the flat colours the art intends at native size
        # (esp. the heavily-dithered office window onto the stadium).
        cleaned = scene.filter(ImageFilter.MedianFilter(3)).filter(ImageFilter.GaussianBlur(0.8))
        cleaned.save(out / f"{name}.png")
        n += 1
    return n


def export_formations():
    """FORM.DAT = 18 formations x 22 PHASE sets x 11 (x,y). The 22 sets are NOT
    ball-zones — the EXE labels them as match phases ("Attacking/Defending
    Positions in Open Play", plus corners / goal-kicks / kick-off / penalties).

    Set 0 is a SET-PIECE phase (players bunched forward) -> the old "base = set 0"
    gave the unplayable "4-6" shapes. SET_OPEN_PLAY (13, "Defending Positions in
    Open Play") is the canonical formation: GK deepest and central, a clean back
    line, midfield and attack. Verified by ASCII-rendering all 18 formations
    across every set (see docs/FORMATS.md). Each formation is normalised on its
    OWN bounding box so it fills the pitch; y grows toward the GK (bottom)."""
    data = (ORIG / "FORM.DAT").read_bytes()
    STRIDE = 484          # bytes per formation (22 phase sets x 22 bytes)
    SET_OPEN_PLAY = 13
    n_form = len(data) // STRIDE
    pad = 0.08
    forms = []
    for f in range(n_form):
        off = f * STRIDE + SET_OPEN_PLAY * 22
        seg = data[off: off + 22]
        pts = [(seg[2 * i], seg[2 * i + 1]) for i in range(11)]
        xs = [x for x, _ in pts]
        ys = [y for _, y in pts]
        xmin, xmax = min(xs), max(xs)
        ymin, ymax = min(ys), max(ys)
        def nx(x, lo=xmin, hi=xmax): return round(pad + (1 - 2 * pad) * (x - lo) / (hi - lo or 1), 4)
        def ny(y, lo=ymin, hi=ymax): return round(pad + (1 - 2 * pad) * (y - lo) / (hi - lo or 1), 4)
        forms.append([[nx(x), ny(y)] for x, y in pts])
    (ASSETS / "data" / "formations.json").write_text(json.dumps(forms))
    return n_form, SET_OPEN_PLAY


def verify():
    """Render a labelled contact sheet of the icon crops to confirm alignment."""
    icons = sorted((IMG / "tb").glob("ic_*.png"))
    cell, scale = ICON_W, 4
    sheet = Image.new("RGB", (len(icons) * (cell * scale + 4), cell * scale + 18), (0, 0, 0))
    d = ImageDraw.Draw(sheet)
    x = 0
    for i, p in enumerate(icons):
        ic = Image.open(p).resize((cell * scale, cell * scale), Image.NEAREST)
        sheet.paste(ic, (x, 16))
        d.text((x + 2, 2), str(i), fill=(255, 255, 0))
        x += cell * scale + 4
    sheet.save("/tmp/tb_icons_sheet.png")
    print("saved /tmp/tb_icons_sheet.png")


def main():
    if "--verify" in sys.argv:
        verify()
        return
    ni = crop_icons()
    ns = crop_scenes()
    nf, open_play_set = export_formations()
    verify()
    print(f"icons={ni}  scenes={ns}  formations={nf}  open_play_set={open_play_set}")


if __name__ == "__main__":
    main()
