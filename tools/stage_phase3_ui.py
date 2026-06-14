#!/usr/bin/env python3
"""Stage the faithful-landscape UI assets, derived ONLY from the real game files.

Produces, under android/app/src/main/assets/ (git-tracked, self-contained build):
  img/tb/ic_NN.png     24 toolbar icons cropped from the real baked toolbar
  img/scene/<NAME>.png  each room PIC with the baked toolbar band removed
  data/formations.json  the 18 real formations (base set) from FORM.DAT,
                        normalised to [0,1] pitch coords (GK bottom, attack top)
  img/match/pitch.png   MATCHSCR.PIC at ALL.PAL set 1 (the live green pitch)
  img/match/{h,a}_*.png the real PITCH.SPR player run cycle (home red kit;
                        away = red kit recoloured blue), transparent index 0
  data/pitch_quad.json  the playable-pitch trapezoid corners in [0,1] of pitch.png

Run: python3 tools/stage_phase3_ui.py   (then re-run --verify to eyeball crops)
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parent))
from decode_pic import decode_pak2, load_palette, pixels_to_image  # noqa: E402
from decode_sprites import decode_pak2_full  # noqa: E402

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


# --- match view: pitch background + real player sprites ----------------------
# MATCHSCR renders as the live green pitch under ALL.PAL set 1 (set 0 is grey;
# ART_NOTES.md "match family"). PITCH.SPR is a PAK2 sprite bank whose first block
# is 30x32 outfield players (autocorrelation gives row width 30, frame stride 960
# = 30x32; verified by rendering the contact sheet). The kit is baked RED; the
# away kit is that same sprite with the saturated-red shirt indices remapped to
# blue (skin and the blue ground-shadow are left untouched).
MATCH_PALETTE = 1
SPR_W, SPR_H = 30, 32
SPR_IDLE = 1                     # front-facing standing frame
SPR_RUN = [15, 16, 17, 18, 19]  # a clean left-facing run cycle (flip for right)
TRANSPARENT = 0


def _away_palette(pal: bytes) -> bytes:
    """Recolour the saturated-red kit indices to blue (keep skin + shadow).
    A shirt index is red-dominant (R>G+50, R>B+50) and not skin (G<70); it maps
    to (G, G, R) so a bright red becomes a matching bright blue."""
    out = bytearray(pal)
    for i in range(256):
        r, g, b = pal[3 * i], pal[3 * i + 1], pal[3 * i + 2]
        if r > g + 50 and r > b + 50 and g < 70:
            out[3 * i], out[3 * i + 1], out[3 * i + 2] = g, g, r
    return bytes(out)


def _sprite(buf: bytes, idx: int, pal: bytes) -> Image.Image:
    """One 30x32 PITCH.SPR frame as RGBA with index 0 transparent."""
    fp = SPR_W * SPR_H
    seg = buf[idx * fp:(idx + 1) * fp]
    im = Image.new("P", (SPR_W, SPR_H))
    im.putpalette(list(pal))
    im.putdata(seg)
    rgba = im.convert("RGBA")
    a = np.array(rgba)
    a[np.frombuffer(seg, np.uint8).reshape(SPR_H, SPR_W) == TRANSPARENT, 3] = 0
    return Image.fromarray(a)


def _pitch_quad(img: Image.Image) -> dict:
    """The playable-pitch trapezoid in [0,1], from the green region of pitch.png.
    Pitch pixels are green (G>=R-5, G>B+8, G>40); the warm crowd is excluded. The
    top/bottom rows with a wide green span give the far/near touchlines; the 2nd/
    98th column percentiles give the left/right edges (ignoring stray crowd green)."""
    a = np.array(img.convert("RGB"))
    h, w, _ = a.shape
    r, g, b = a[:, :, 0].astype(int), a[:, :, 1].astype(int), a[:, :, 2].astype(int)
    pitch = (g >= r - 5) & (g > b + 8) & (g > 40)
    rows = [y for y in range(h) if pitch[y].sum() > 180]
    top, bot = min(rows), max(rows)

    def extent(y):
        xs = np.where(pitch[y])[0]
        lo, hi = np.percentile(xs, [2, 98])
        return int(lo), int(hi)

    tl, tr = extent(top + 3)
    bl, br = extent(bot - 3)
    return {
        "tl": [round(tl / w, 4), round(top / h, 4)],
        "tr": [round(tr / w, 4), round(top / h, 4)],
        "br": [round(br / w, 4), round(bot / h, 4)],
        "bl": [round(bl / w, 4), round(bot / h, 4)],
    }


def export_match():
    out = IMG / "match"
    out.mkdir(parents=True, exist_ok=True)
    home_pal = load_palette(ORIG / "ALL.PAL", MATCH_PALETTE)
    away_pal = _away_palette(home_pal)

    # 1. pitch background (full 640x480 MATCHSCR at set 1)
    pixels = decode_pak2((ORIG / "MATCHSCR.PIC").read_bytes())
    pitch = pixels_to_image(pixels, 640, 480, home_pal)
    pitch.save(out / "pitch.png")

    # 2. player sprites (home red kit, away blue kit) from PITCH.SPR
    buf = decode_pak2_full((ORIG / "PITCH.SPR").read_bytes())
    _sprite(buf, SPR_IDLE, home_pal).save(out / "h_idle.png")
    _sprite(buf, SPR_IDLE, away_pal).save(out / "a_idle.png")
    for k, fr in enumerate(SPR_RUN):
        _sprite(buf, fr, home_pal).save(out / f"h_run{k}.png")
        _sprite(buf, fr, away_pal).save(out / f"a_run{k}.png")

    # 3. playable-pitch quad in [0,1] of pitch.png
    (ASSETS / "data" / "pitch_quad.json").write_text(json.dumps(_pitch_quad(pitch)))
    return 2 + 2 * len(SPR_RUN)


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
    nm = export_match()
    verify()
    print(f"icons={ni}  scenes={ns}  formations={nf}  open_play_set={open_play_set}  match_assets={nm}")


if __name__ == "__main__":
    main()
