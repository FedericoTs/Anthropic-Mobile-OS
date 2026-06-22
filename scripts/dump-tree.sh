#!/usr/bin/env bash
# Trigger an a11y-tree dump of whatever is on screen right now, then show it.
set -euo pipefail

PKG="org.agentnativeos.spike"

adb logcat -c || true
adb shell am broadcast -n "$PKG/.CommandReceiver" -a "$PKG.DUMP" >/dev/null
sleep 1

echo "=== latest dump files on device ==="
adb shell ls -t "/sdcard/Android/data/$PKG/files/dumps" 2>/dev/null | head -3 || \
  echo "(none yet)"

echo
echo "=== SPIKE_TREE logcat ==="
adb logcat -d -s SPIKE_TREE SPIKE_CMD | tail -60
