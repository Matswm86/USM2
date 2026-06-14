#!/usr/bin/env bash
# Publish the local copyrighted assets to the PRIVATE Matswm86/USM2-assets repo.
#
# decoded/ and original/ are gitignored in this public repo on purpose; they only
# ever live in (a) your local backup and (b) the private assets repo that CI reads
# read-only. Re-run this whenever you re-decode (tools/decode_*.py + stage_assets).
#
# This must be run BY YOU (it pushes copyrighted material, which the assistant's
# safety guard blocks). From a Claude Code session, prefix with `!`:
#     ! bash tools/push_private_assets.sh
#
# Requires: gh auth (account Matswm86) so the https push is credential-helped.
set -euo pipefail
cd "$(dirname "$0")/.."

[ -d decoded ] || { echo "decoded/ missing — run tools/decode_db.py + decode_pic.py first"; exit 1; }

REMOTE="https://github.com/Matswm86/USM2-assets.git"
WT="$(mktemp -d)"
trap 'rm -rf "$WT"' EXIT

cp -r decoded "$WT/decoded"
[ -d original ] && cp -r original "$WT/original"
cat > "$WT/README.md" <<'EOF'
# USM2 assets (PRIVATE — never make public)

Copyrighted material for the public USM2 Android rebuild (Impressions/Sierra,
1997). `original/` = the original DOS files; `decoded/` = JSON DB + PNG screens
produced by the public repo's tools/. CI checks this out read-only and runs
tools/stage_assets.py to bundle a slim asset set into a PRIVATE-artifact APK.
EOF

cd "$WT"
git init -q -b main
git add -A
git -c user.email=matswm86@yahoo.no -c user.name=Matswm86 \
    commit -q -m "Update USM2 private assets ($(date -u +%Y-%m-%d))"
# Force-push: this repo is a snapshot mirror, history is not meaningful here.
git push -f -q "$REMOTE" main
echo "Pushed decoded/$( [ -d original ] && echo ' + original/') to $REMOTE"
echo "Now re-run the 'Build Android APK' workflow on Matswm86/USM2 (Actions tab)."
