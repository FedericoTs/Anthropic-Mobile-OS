#!/usr/bin/env bash
# Guided T0 hostility-matrix run. Installs the apps, enables the service, and
# walks Tiers 1-2 automatically; Tier 3 (a real banking / Integrity app) is
# manual because it depends on what you can sideload onto your emulator.
#
# Record results in docs/spikes/t0-hostile-app.md as you go.
set -euo pipefail

SPIKE_PKG="org.agentnativeos.spike"
SECURE_PKG="org.agentnativeos.flagsecuretest"
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"

require_device() {
  if ! adb get-state >/dev/null 2>&1; then
    echo "No device/emulator. Start one: emulator -avd <name>" >&2
    exit 1
  fi
}

echo "== T0 hostile-app matrix =="
require_device

echo
echo "-- build + install --"
( cd "$ROOT" && ./gradlew :spike:installDebug :flagsecure-testapp:installDebug )

echo
echo "-- enable the spike AccessibilityService --"
"$HERE/enable-service.sh"
sleep 1

echo
echo "== Tier 1: cooperative (AOSP Settings) =="
adb shell am start -a android.settings.SETTINGS >/dev/null
sleep 2
"$HERE/dump-tree.sh"
echo ">> Expect: non-empty tree, real Settings labels, taps land."

echo
echo "== Tier 2: controlled FLAG_SECURE app =="
adb shell monkey -p "$SECURE_PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 2
"$HERE/dump-tree.sh"
echo ">> Check whether FLAGSECURE_MARKER_7F3A appears in the dump above."
echo ">> Present  => FLAG_SECURE did NOT hide a11y text."
echo ">> Absent   => FLAG_SECURE suppressed the a11y text tree."

echo
echo "== Tier 3: real hostile app (manual) =="
echo "1. Install + open a banking / Play-Integrity app on the emulator."
echo "2. If it refuses to run, record that as the result (blocked-by=integrity)."
echo "3. If it runs:  ./scripts/dump-tree.sh   then try ./scripts/tap.sh / type.sh"
echo
echo "Write the verdict (GO / PIVOT) into docs/spikes/t0-hostile-app.md."
