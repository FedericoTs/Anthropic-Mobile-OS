#!/usr/bin/env bash
# Type text into an editable field.
# Usage: ./scripts/type.sh "hello world" ["field hint or text to target"]
set -euo pipefail

PKG="org.agentnativeos.spike"
if [ "$#" -lt 1 ]; then
  echo "usage: $0 <text-to-type> [target-field-text]" >&2
  exit 1
fi

VALUE="$1"
TARGET="${2:-}"

if [ -n "$TARGET" ]; then
  adb shell am broadcast -n "$PKG/.CommandReceiver" -a "$PKG.TYPE" \
    --es text "$VALUE" --es target "$TARGET" >/dev/null
else
  adb shell am broadcast -n "$PKG/.CommandReceiver" -a "$PKG.TYPE" \
    --es text "$VALUE" >/dev/null
fi
sleep 1
adb logcat -d -s SPIKE_ACT SPIKE_CMD | tail -8
