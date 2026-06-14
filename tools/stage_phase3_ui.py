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
    """FORM.DAT = 18 formations x 22 ball-zone sets x 11 (x,y). The first set of
    each formation is the static base shape used by the line-up screen."""
    data = (ORIG / "FORM.DAT").read_bytes()
    STRIDE = 484          # bytes per formation (22 sets x 22 bytes)
    n_form = len(data) // STRIDE
    raw = []
    for f in range(n_form):
        base = data[f * STRIDE: f * STRIDE + 22]
        raw.append([(base[2 * i], base[2 * i + 1]) for i in range(11)])
    # Normalise on a shared grid so every formation sits consistently on the pitch.
    xs = [x for fm in raw for x, _ in fm]
    ys = [y for fm in raw for _, y in fm]
    xmin, xmax = min(xs), max(xs)
    ymin, ymax = min(ys), max(ys)
    pad = 0.06
    def nx(x): return round(pad + (1 - 2 * pad) * (x - xmin) / (xmax - xmin), 4)
    def ny(y): return round(pad + (1 - 2 * pad) * (y - ymin) / (ymax - ymin), 4)
    forms = [[[nx(x), ny(y)] for x, y in fm] for fm in raw]
    (ASSETS / "data" / "formations.json").write_text(json.dumps(forms))
    return n_form, (xmin, xmax, ymin, ymax)


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
    nf, bounds = export_formations()
    verify()
    print(f"icons={ni}  scenes={ns}  formations={nf}  form_bounds(x/y)={bounds}")


if __name__ == "__main__":
    main()
