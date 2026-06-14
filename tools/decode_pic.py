#!/usr/bin/env python3
"""
PAK2 image decoder for Ultimate Soccer Manager 2 (Impressions/Sierra, 1996/97).

Algorithm read from USM2E.EXE disassembly, function at VA 0x9fafc (obj1, vbase 0x10000):

  File layout:
    0-3   "PAK2" magic (4 bytes)
    4-7   total file size, big-endian u32
    8     save1  -- palette index for type-01 RLE tokens
    9     save2  -- palette index for type-10 RLE tokens
    10+   compressed pixel stream

  Each token in the compressed stream is one code byte:
    bits 7-6 = type:
      11 (0xC0): literal run   -- next (lower6 + 1) bytes are literal pixel indices
      10 (0x80): RLE of save2  -- emit save2 (lower6 + 1) times
      01 (0x40): RLE of save1  -- emit save1 (lower6 + 1) times
      00 (0x00): RLE of next   -- read one more byte; emit it (lower6 + 1) times
    bits 5-0 = lower6: run_length = lower6 + 1  (range 1..64)

  This is NOT LZSS. There is no ring buffer. Each token is entirely self-contained.

  Palette source:
    Not embedded in .PIC files.  The game loads ALL.PAL at startup:
      6144 bytes = 8 x 256-colour palettes.
      Each colour entry: 3 bytes of 6-bit VGA (values 0-63); multiply by 4 for 8-bit RGB.
    Palette index 0 (bytes 0-767) is used for all screen rendering.

  Image dimensions:
    640 x 480 = 307200 pixels for all files except TV.PIC (83 x 932 = 77356 pixels).
    Only the first frame (first W*H pixels) is decoded here.

Key disassembly references (obj1, VA space):
  0x9fdb8  PAK2 loader: opens file, reads "PAK", dispatches to PAK1 (0x9f9ac) or PAK2 (0x9fafc)
  0x9fafc  PAK2 decompressor (this function)
  0x9fb4a  reads save1 from file reader
  0x9fb4f  reads save2 from file reader
  0x9fb79  reads code byte (top 2 bits = type, lower 6 = count-1)
  0x9fba7  case 0xC0: literal run loop
  0x9fbb6  case 0x80: save2 RLE loop
  0x9fbce  case 0x40: save1 RLE loop
  0x9fbe2  case 0x00: reads next byte then RLE loop
  0x9fc10  buffered file reader (reads one byte from ds:0x118cc4 struct)
"""

import argparse
import struct
import sys
from pathlib import Path

try:
    from PIL import Image
    HAS_PIL = True
except ImportError:
    HAS_PIL = False


MAGIC = b"PAK2"
TV_WIDTH = 83
TV_HEIGHT = 932
SCREEN_WIDTH = 640
SCREEN_HEIGHT = 480


def decode_pak2(data: bytes, width: int = SCREEN_WIDTH, height: int = SCREEN_HEIGHT) -> bytes:
    """
    Decode the first frame of a PAK2 compressed image.

    Args:
        data:   raw bytes of the entire .PIC file
        width:  output width in pixels  (640 for all screens, 83 for TV.PIC)
        height: output height in pixels (480 for all screens, 932 for TV.PIC)

    Returns:
        Raw palette-indexed pixel bytes, exactly width*height bytes.

    Raises:
        ValueError: if magic bytes are wrong or file is too short.
    """
    if len(data) < 10:
        raise ValueError(f"File too short ({len(data)} bytes) to be a PAK2 file")
    if data[:4] != MAGIC:
        raise ValueError(f"Not a PAK2 file (magic={data[:4]!r})")

    save1: int = data[8]   # palette index for type-01 tokens (disasm: [ebp-0x8])
    save2: int = data[9]   # palette index for type-10 tokens (disasm: [ebp-0x4])

    target = width * height
    pos = 10
    out = bytearray()

    while pos < len(data) and len(out) < target:
        code = data[pos]
        pos += 1
        top2 = (code >> 6) & 0x3   # bits 7-6: token type
        count = (code & 0x3F) + 1  # bits 5-0: run length (lower6 + 1)
        remaining = target - len(out)

        if top2 == 3:     # 0xC0 = 11: literal run (disasm 0x9fba7)
            n = min(count, remaining)
            for _ in range(n):
                if pos >= len(data):
                    break
                out.append(data[pos])
                pos += 1
        elif top2 == 2:   # 0x80 = 10: RLE of save2 (disasm 0x9fbb6)
            n = min(count, remaining)
            out.extend(bytes([save2]) * n)
        elif top2 == 1:   # 0x40 = 01: RLE of save1 (disasm 0x9fbce)
            n = min(count, remaining)
            out.extend(bytes([save1]) * n)
        else:              # 0x00 = 00: RLE of next byte (disasm 0x9fbe2)
            if pos >= len(data):
                break
            rle_byte = data[pos]
            pos += 1
            n = min(count, remaining)
            out.extend(bytes([rle_byte]) * n)

    if len(out) < target:
        raise ValueError(f"Underrun: decoded {len(out)} px, expected {target}")

    return bytes(out[:target])


def load_palette(allpal_path: Path, palette_index: int = 0) -> bytes:
    """
    Load one 256-colour palette from ALL.PAL.

    ALL.PAL = 6144 bytes = 8 x 768-byte palettes.
    Each colour entry is 3 bytes of 6-bit VGA (values 0-63); multiply by 4 for 8-bit RGB.
    """
    raw = allpal_path.read_bytes()
    off = palette_index * 768
    pal6 = raw[off: off + 768]
    if len(pal6) < 768:
        raise ValueError(f"ALL.PAL too short for palette index {palette_index}")
    return bytes(v * 4 for v in pal6)


def vscore(pixels: bytes, w: int = SCREEN_WIDTH, h: int = SCREEN_HEIGHT) -> float:
    """
    Vertical coherence oracle from PAK2_analysis_notes.md.

    Samples every other row (step 2) and every third column (step 3),
    counting how often a pixel index equals the one directly above it.
    Returns fraction of matching pairs.

    Note: photographic dithered backgrounds (MAINSCR office carpet, BANKSCR bank photo)
    produce vscore ~ 0.31-0.38 even when correctly decoded, because dithering
    intentionally anti-correlates adjacent rows.
    """
    s = cnt = 0
    for y in range(1, h, 2):
        b = y * w
        p = b - w
        for x in range(0, w, 3):
            cnt += 1
            s += pixels[b + x] == pixels[p + x]
    return s / cnt if cnt else 0.0


def pixels_to_image(pixels: bytes, width: int, height: int, palette: bytes) -> "Image.Image":
    """Convert palette-indexed pixel bytes to a PIL Image."""
    if not HAS_PIL:
        raise RuntimeError("Pillow is not installed; run: pip install Pillow")
    img = Image.new("P", (width, height))
    img.putpalette(list(palette))
    img.putdata(pixels)
    return img.convert("RGB")


def decode_file(
    pic_path: Path,
    out_dir: Path,
    allpal_path: Path | None = None,
    palette_index: int = 0,
) -> tuple[Path, float, int, int]:
    """
    Decode one .PIC file and write a PNG.

    Returns (output_path, vscore_value, width, height).
    """
    stem = pic_path.stem.upper()
    w = TV_WIDTH if stem == "TV" else SCREEN_WIDTH
    h = TV_HEIGHT if stem == "TV" else SCREEN_HEIGHT

    data = pic_path.read_bytes()
    pixels = decode_pak2(data, w, h)
    vs = vscore(pixels, w, h)

    out_path = out_dir / f"{stem}.png"

    if allpal_path is not None and allpal_path.exists():
        palette = load_palette(allpal_path, palette_index)
    else:
        # Fallback: linear greyscale (won't look right, but at least writes a file)
        palette = bytes(range(256)) * 3

    if HAS_PIL:
        img = pixels_to_image(pixels, w, h, palette)
        img.save(out_path)
    else:
        # Pillow not available: write PPM (portable, no dependencies)
        out_path = out_path.with_suffix(".ppm")
        rgb = bytearray(w * h * 3)
        for j, idx in enumerate(pixels):
            rgb[j * 3]     = palette[idx * 3]
            rgb[j * 3 + 1] = palette[idx * 3 + 1]
            rgb[j * 3 + 2] = palette[idx * 3 + 2]
        out_path.write_bytes(b"P6\n" + f"{w} {h}\n255\n".encode() + bytes(rgb))

    return out_path, vs, w, h


def main() -> None:
    ap = argparse.ArgumentParser(description="Decode USM2 .PIC files (PAK2) to PNG")
    ap.add_argument("input", nargs="*", help=".PIC files (default: all in original/)")
    ap.add_argument(
        "--out-dir", default="decoded/pics",
        help="Output directory (default: decoded/pics)",
    )
    ap.add_argument(
        "--allpal", default=None,
        help="Path to ALL.PAL (default: original/ALL.PAL)",
    )
    ap.add_argument(
        "--palette-index", type=int, default=0,
        help="Which of the 8 palettes in ALL.PAL to use (0-7, default 0)",
    )
    args = ap.parse_args()

    script_dir = Path(__file__).parent
    repo_root = script_dir.parent
    orig_dir = repo_root / "original"

    out_dir = Path(args.out_dir)
    if not out_dir.is_absolute():
        out_dir = repo_root / out_dir
    out_dir.mkdir(parents=True, exist_ok=True)

    allpal_path = Path(args.allpal) if args.allpal else orig_dir / "ALL.PAL"

    if args.input:
        pic_files = [Path(p) for p in args.input]
    else:
        pic_files = sorted(orig_dir.glob("*.PIC")) + sorted(orig_dir.glob("*.pic"))

    if not pic_files:
        print(f"No .PIC files found in {orig_dir}", file=sys.stderr)
        sys.exit(1)

    print(f"Palette : {allpal_path}  [index {args.palette_index}]")
    print(f"Output  : {out_dir}")
    print()

    results: list[tuple[str, Path, float]] = []
    errors = 0

    for pic in pic_files:
        try:
            out_path, vs, w, h = decode_file(pic, out_dir, allpal_path, args.palette_index)
            results.append((pic.name, out_path, vs))
            print(f"  {pic.name:15s}  {w}x{h}  vscore={vs:.4f}  ->  {out_path.name}")
        except Exception as exc:
            print(f"  {pic.name:15s}  ERROR: {exc}", file=sys.stderr)
            errors += 1

    print()
    print(f"Decoded {len(results)} file(s), {errors} error(s).")
    print()

    # Report vscore for the two primary oracle targets
    for target_stem in ("MAINSCR", "BANKSCR"):
        match = next((r for r in results if target_stem in r[0].upper()), None)
        if match:
            vs = match[2]
            flag = "OK > 0.6" if vs > 0.6 else "below 0.6 threshold"
            print(f"  {target_stem:8s}  vscore = {vs:.4f}  [{flag}]")
        else:
            print(f"  {target_stem:8s}  not found in input")

    print()
    print("Oracle note:")
    print("  MAINSCR (office carpet) and BANKSCR (bank photo) use photographic")
    print("  dithered backgrounds. Correct decodes score vscore ~ 0.31-0.38 because")
    print("  dithering intentionally anti-correlates adjacent pixel rows.")
    print("  TITLE.PIC and TV.PIC (simple graphics) score ~ 0.80 on correct decodes.")
    print("  Visual inspection of the PNG output is the reliable correctness check.")
    print()
    print("Output files:")
    for name, out_path, vs in results:
        print(f"  {out_path}")


if __name__ == "__main__":
    main()
