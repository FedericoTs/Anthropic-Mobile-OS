#!/usr/bin/env bash
# Runs the T0 spike Tiers 1-2 on a CI emulator and captures evidence.
#   Tier 1: cooperative app (Settings)        -> perceive + tap
#   Tier 2: controlled FLAG_SECURE test app   -> perceive (marker?) + type
# Tier 3 (a real banking / Play-Integrity app) is NOT possible on an emulator
# and is left for a physical device. Designed to run inside
# reactivecircus/android-emulator-runner (emulator already booted).
#
# Evidence for the checks comes from logcat (the dumper logs the full tree to
# SPIKE_TREE), which avoids scoped-storage issues when pulling external files.
set -uo pipefail

SPIKE=org.agentnativeos.spike
SECURE=org.agentnativeos.flagsecuretest
SVC="$SPIKE/$SPIKE.SpikeAccessibilityService"
EV="${1:-evidence}"
SUMMARY="${GITHUB_STEP_SUMMARY:-/dev/stdout}"
mkdir -p "$EV"

log() { echo "[spike-matrix] $*"; }
capture() { adb logcat -d -s SPIKE_TREE SPIKE_ACT SPIKE_CMD SPIKE | tr -d '\r' > "$1"; }

adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
log "device booted"
printf 'Android %s (API %s)\n' \
  "$(adb shell getprop ro.build.version.release | tr -d '\r')" \
  "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" > "$EV/device.txt"

log "installing apps"
./gradlew :spike:installDebug :flagsecure-testapp:installDebug --console=plain \
  || log "gradle install returned non-zero (continuing)"

log "enabling accessibility service"
adb shell am start -n "$SPIKE/.MainActivity" >/dev/null 2>&1
sleep 2
adb shell settings put secure enabled_accessibility_services "$SVC"
adb shell settings put secure accessibility_enabled 1
SVC_OK=no
for i in $(seq 1 10); do
  if adb logcat -d -s SPIKE | grep -q "connected"; then SVC_OK=yes; break; fi
  log "waiting for service to bind ($i/10)"
  adb shell am start -n "$SPIKE/.MainActivity" >/dev/null 2>&1
  sleep 3
done
log "service connected: $SVC_OK"

# ---------- Tier 1: cooperative (Settings) ----------
log "Tier 1: Settings"
adb logcat -c
adb shell am start -a android.settings.SETTINGS >/dev/null 2>&1
sleep 4
adb shell am broadcast -n "$SPIKE/.CommandReceiver" -a "$SPIKE.DUMP" >/dev/null
sleep 2
adb shell am broadcast -n "$SPIKE/.CommandReceiver" -a "$SPIKE.TAP" --es text "Battery" >/dev/null
sleep 2
capture "$EV/tier1.txt"
T1_NODES=$(grep -oE 'nodeCount=[0-9]+' "$EV/tier1.txt" | head -1)
T1_KNOWN=$(grep -ciE 'text="(Battery|Network|System|Apps|Settings|Display|Connected|Storage|Sound)' "$EV/tier1.txt")
T1_TAP=$(grep -oE 'TAP\(text=Battery[^)]*\) -> (true|false)' "$EV/tier1.txt" | head -1)

# ---------- Tier 2: controlled FLAG_SECURE ----------
log "Tier 2: FLAG_SECURE app"
adb logcat -c
adb shell monkey -p "$SECURE" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 4
adb shell am broadcast -n "$SPIKE/.CommandReceiver" -a "$SPIKE.DUMP" >/dev/null
sleep 2
adb shell am broadcast -n "$SPIKE/.CommandReceiver" -a "$SPIKE.TYPE" --es text "ci-typed-secret" --es target "Secret" >/dev/null
sleep 2
capture "$EV/tier2.txt"
if grep -q "FLAGSECURE_MARKER_7F3A" "$EV/tier2.txt"; then T2_MARKER=PRESENT; else T2_MARKER=ABSENT; fi
T2_NODES=$(grep -oE 'nodeCount=[0-9]+' "$EV/tier2.txt" | head -1)
T2_TYPE=$(grep -oE 'TYPE\(target=Secret\) -> (true|false)' "$EV/tier2.txt" | head -1)

# Best-effort: full tree files into the artifact (logcat may truncate huge trees).
adb root >/dev/null 2>&1 || true
adb wait-for-device
adb pull "/storage/emulated/0/Android/data/$SPIKE/files/dumps" "$EV/dumps" >/dev/null 2>&1 \
  || log "full-tree pull not permitted (logcat trees captured above)"

# ---------- summary ----------
{
  echo "## T0 spike — CI emulator (Tiers 1-2)"
  echo ""
  echo "Device: $(cat "$EV/device.txt")"
  echo ""
  echo "| Check | Result |"
  echo "|---|---|"
  echo "| Service connected | \`$SVC_OK\` |"
  echo "| Tier 1 (Settings) tree | \`${T1_NODES:-none}\` |"
  echo "| Tier 1 known-text matches | \`$T1_KNOWN\` |"
  echo "| Tier 1 tap Battery | \`${T1_TAP:-n/a}\` |"
  echo "| Tier 2 FLAG_SECURE marker | \`$T2_MARKER\` |"
  echo "| Tier 2 tree | \`${T2_NODES:-none}\` |"
  echo "| Tier 2 type into secure field | \`${T2_TYPE:-n/a}\` |"
} | tee -a "$SUMMARY"

echo "===== tier1.txt ====="; cat "$EV/tier1.txt"
echo "===== tier2.txt ====="; cat "$EV/tier2.txt"

if [ "$SVC_OK" = yes ] && [ "${T1_KNOWN:-0}" -gt 0 ]; then
  log "core perceive path OK"
  exit 0
else
  log "core perceive path FAILED (service=$SVC_OK known=${T1_KNOWN:-0})"
  exit 1
fi
