#!/usr/bin/env python3
"""Stage the match crowd SFX from the original game's WAVs into android res/raw.

The originals (original/*.WAV, gitignored — Impressions/Sierra audio) are 8-bit
22050 Hz mono. We transcode the handful the match view uses to small Ogg Vorbis
files under res/raw/ (committed, lowercase names so they map to R.raw.*), exactly
the way the screen art is decoded to slim committed PNGs. Re-run after changing
the picks; needs ffmpeg on PATH.

    python3 tools/stage_audio.py
"""
from __future__ import annotations

import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ORIG = ROOT / "original"
RAW = ROOT / "android/app/src/main/res/raw"

# original WAV stem -> res/raw resource name (R.raw.<name>). Kept faithful to the
# real cues: short ref whistle to start/restart, long whistle for full time, the
# big roar + a cheer when you score (alternated), a jeer/groan when you concede,
# and the long initial cheer as the crowd welcome at kick-off.
SOUNDS = {
    "WHISHORT": "r_whistle",
    "WHISLONG": "r_fulltime",
    "ROAR": "crowd_roar",
    "CHEER": "crowd_cheer",
    "JEER": "crowd_jeer",
    "INICHEER": "crowd_welcome",
}


def main() -> int:
    if not shutil.which("ffmpeg"):
        print("ffmpeg not found on PATH", file=sys.stderr)
        return 1
    RAW.mkdir(parents=True, exist_ok=True)
    n = 0
    for stem, name in SOUNDS.items():
        src = ORIG / f"{stem}.WAV"
        if not src.exists():
            print(f"  MISSING {src.name}", file=sys.stderr)
            continue
        dst = RAW / f"{name}.ogg"
        # -q:a 3 Vorbis is plenty for an 8-bit mono crowd sample; -ac 1 keeps mono.
        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error", "-i", str(src),
             "-ac", "1", "-c:a", "libvorbis", "-q:a", "3", str(dst)],
            check=True,
        )
        n += 1
        print(f"  {stem}.WAV -> res/raw/{name}.ogg ({dst.stat().st_size // 1024} KB)")
    print(f"staged {n} match sounds")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
