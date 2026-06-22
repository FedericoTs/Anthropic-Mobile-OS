#!/usr/bin/env bash
# Tap a node by its visible text / content-description / view-id substring.
# Usage: ./scripts/tap.sh "Network & internet"
set -euo pipefail

PKG="org.agentnativeos.spike"
if [ "$#" -lt 1 ]; then
  echo "usage: $0 <text-to-tap>" >&2
  exit 1
fi

adb shell am broadcast -n "$PKG/.CommandReceiver" -a "$PKG.TAP" --es text "$1" >/dev/null
sleep 1
adb logcat -d -s SPIKE_ACT SPIKE_CMD | tail -8
