#!/usr/bin/env bash
# Pull all a11y-tree dumps off the device into ./dumps (or $1).
set -euo pipefail

PKG="org.agentnativeos.spike"
DEST="${1:-./dumps}"
mkdir -p "$DEST"

if adb pull "/sdcard/Android/data/$PKG/files/dumps" "$DEST" 2>/dev/null; then
  echo "Pulled dumps into $DEST"
else
  echo "No dumps found yet. Run ./scripts/dump-tree.sh first." >&2
  exit 1
fi
