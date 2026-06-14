#!/usr/bin/env python3
"""
PAK2 sprite-bank decoder for USM2 .BIT/.SPR files.

The .PIC screens are single 640x480 frames (see decode_pic.py). The .BIT/.SPR
files are *sprite banks*: the same PAK2 codec decompresses them to a flat
palette-index buffer that holds several frames back to back. Unlike some sprite
formats there is NO embedded dimension table in the decompressed buffer (the
POINTER.SPR buffer starts straight on the arrow's pixel rows), so frame
dimensions come either from a known clean tiling (POINTER = 16x16) or from the
blit code in USM2E.EXE (TOOLS.BIT, see ART_NOTES.md).

Reuses the verified PAK2 token model from decode_pic.py.

Usage:
  decode_sprites.py --probe FILE...                 # decompress, report size/head
  decode_sprites.py FILE --sheet W [--pal N]        # whole bank as one PNG, width W
  decode_sprites.py FILE --frame WxH [--pal N]      # slice fixed WxH grid -> per-frame PNGs
                       [--transparent IDX] [--out DIR]
Paths resolve against ../original if not found as given.
"""

import argparse
import struct
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from decode_pic import MAGIC, load_palette  # noqa: E402

try:
    from PIL import Image
    HAS_PIL = True
except ImportError:
    HAS_PIL = False


def decode_pak2_full(data: bytes) -> bytes:
    """Decode the ENTIRE PAK2 token stream (no W*H target cap)."""
    if data[:4] != MAGIC:
        raise ValueError(f"Not PAK2 (magic={data[:4]!r})")
    save1, save2 = data[8], data[9]
    pos = 10
    out = bytearray()
    while pos < len(data):
        code = data[pos]
        pos += 1
        top2 = (code >> 6) & 0x3
        count = (code & 0x3F) + 1
        if top2 == 3:        # literal run
            chunk = data[pos:pos + count]
            out.extend(chunk)
            pos += len(chunk)
        elif top2 == 2:      # RLE save2
            out.extend(bytes([save2]) * count)
        elif top2 == 1:      # RLE save1
            out.extend(bytes([save1]) * count)
        else:                # RLE next byte
            if pos >= len(data):
                break
            out.extend(bytes([data[pos]]) * count)
            pos += 1
    return bytes(out)


def _rgba_frame(buf: bytes, w: int, h: int, palette: bytes, transparent: int | None) -> "Image.Image":
    """Build one RGBA frame from palette indices; `transparent` index -> alpha 0."""
    img = Image.new("P", (w, h))
    img.putpalette(list(palette))
    img.putdata(buf[: w * h].ljust(w * h, b"\0"))
    rgba = img.convert("RGBA")
    if transparent is not None:
        idx = buf[: w * h].ljust(w * h, b"\0")
        px = rgba.load()
        for i, v in enumerate(idx):
            if v == transparent:
                px[i % w, i // w] = (0, 0, 0, 0)
    return rgba


def probe(path: Path) -> bytes:
    data = path.read_bytes()
    hdr = struct.unpack(">I", data[4:8])[0]
    out = decode_pak2_full(data)
    print(f"=== {path.name} ===")
    print(f"  compressed   : {len(data)} (header filesize={hdr})")
    print(f"  save1=0x{data[8]:02x} save2=0x{data[9]:02x}")
    print(f"  decompressed : {len(out)} bytes")
    for s in (16, 24, 32, 40, 48, 64):
        if len(out) % (s * s) == 0:
            print(f"    {s}x{s} grid -> {len(out)//(s*s)} frames")
    head = " ".join(f"{b:02x}" for b in out[:24])
    print(f"  head: {head}")
    return out


def write_sheet(path: Path, width: int, palette: bytes, out_dir: Path) -> Path:
    buf = decode_pak2_full(path.read_bytes())
    h = (len(buf) + width - 1) // width
    img = _rgba_frame(buf.ljust(width * h, b"\0"), width, h, palette, None)
    out_dir.mkdir(parents=True, exist_ok=True)
    out = out_dir / f"{path.stem.lower()}.sheet.w{width}.png"
    img.convert("RGB").save(out)
    print(f"  sheet {width}x{h} -> {out}")
    return out


def slice_grid(
    path: Path, fw: int, fh: int, palette: bytes, transparent: int | None, out_dir: Path
) -> int:
    buf = decode_pak2_full(path.read_bytes())
    frame_px = fw * fh
    n = len(buf) // frame_px
    out_dir.mkdir(parents=True, exist_ok=True)
    stem = path.stem.lower()
    # per-frame PNGs
    for i in range(n):
        seg = buf[i * frame_px:(i + 1) * frame_px]
        frame = _rgba_frame(seg, fw, fh, palette, transparent)
        frame.save(out_dir / f"{stem}_{i:02d}.png")
    # contact sheet (10 cols), 3x nearest upscale for inspection
    cols = min(10, n)
    rows = (n + cols - 1) // cols
    pad = 2
    cw, ch = fw + pad, fh + pad
    sheet = Image.new("RGBA", (cols * cw + pad, rows * ch + pad), (30, 30, 30, 255))
    for i in range(n):
        seg = buf[i * frame_px:(i + 1) * frame_px]
        frame = _rgba_frame(seg, fw, fh, palette, transparent)
        x = pad + (i % cols) * cw
        y = pad + (i // cols) * ch
        sheet.alpha_composite(frame, (x, y))
    up = sheet.resize((sheet.width * 3, sheet.height * 3), Image.NEAREST)
    up.save(out_dir / f"{stem}.contact.png")
    print(f"  {n} frames {fw}x{fh} -> {out_dir}/{stem}_NN.png  (+ {stem}.contact.png)")
    return n


def main() -> None:
    ap = argparse.ArgumentParser(description="Decode USM2 .BIT/.SPR sprite banks (PAK2)")
    ap.add_argument("files", nargs="+")
    ap.add_argument("--probe", action="store_true", help="just report decompressed size + head")
    ap.add_argument("--sheet", type=int, metavar="W", help="render whole bank as one PNG of width W")
    ap.add_argument("--frame", metavar="WxH", help="slice a fixed WxH frame grid")
    ap.add_argument("--pal", type=int, default=0, help="ALL.PAL set 0-7 (default 0)")
    ap.add_argument("--transparent", type=int, default=None, help="palette index to make transparent")
    ap.add_argument("--allpal", default=None)
    ap.add_argument("--out", default="decoded/sprites")
    args = ap.parse_args()

    if not HAS_PIL and not args.probe:
        sys.exit("Pillow required for rendering; use --probe or pip install Pillow")

    repo = Path(__file__).parent.parent
    orig = repo / "original"
    allpal = Path(args.allpal) if args.allpal else orig / "ALL.PAL"
    palette = load_palette(allpal, args.pal) if allpal.exists() else bytes(range(256)) * 3
    out_dir = Path(args.out)
    if not out_dir.is_absolute():
        out_dir = repo / out_dir

    for name in args.files:
        p = Path(name)
        if not p.exists():
            p = orig / name
        if args.probe or (not args.sheet and not args.frame):
            probe(p)
            continue
        print(f"=== {p.name}  pal={args.pal} ===")
        if args.sheet:
            write_sheet(p, args.sheet, palette, out_dir)
        if args.frame:
            fw, fh = (int(x) for x in args.frame.lower().split("x"))
            slice_grid(p, fw, fh, palette, args.transparent, out_dir)


if __name__ == "__main__":
    main()
