#!/usr/bin/env bash
# End-to-end: run the real core AgentLoop on a live emulator screen through the
# :app AccessibilityService, driven by a scripted plan (no model credential).
# Proves the device adapters + loop work together. Runs inside the emulator job.
set -uo pipefail

APP=org.agentnativeos.app
SVC="$APP/$APP.device.AgentAccessibilityService"
EV="${1:-evidence}"
mkdir -p "$EV"
log() { echo "[app-e2e] $*"; }

log "installing :app"
./gradlew :app:installDebug --console=plain || log "install returned non-zero (continuing)"

adb shell am start -n "$APP/.ui.HomeActivity" >/dev/null 2>&1
sleep 2
adb shell settings put secure enabled_accessibility_services "$SVC"
adb shell settings put secure accessibility_enabled 1
SVC_OK=no
for i in $(seq 1 10); do
  if adb logcat -d -s AGENT_SVC | grep -q "connected"; then SVC_OK=yes; break; fi
  log "waiting for agent service to bind ($i/10)"
  adb shell am start -n "$APP/.ui.HomeActivity" >/dev/null 2>&1
  sleep 3
done
log "agent service connected: $SVC_OK"

# Drive a real, safe intent on the Settings screen via the scripted plan.
# NOTE: pass the whole `am` line as ONE string to adb shell, with device-side
# single quotes around multi-word/semicolon values, so the on-device /system/bin/sh
# doesn't split on spaces or treat ';' as a command separator.
adb logcat -c
adb shell am start -a android.settings.SETTINGS >/dev/null 2>&1
sleep 3
adb shell "am broadcast -n $APP/.device.AgentCommandReceiver -a $APP.RUN_SCRIPTED --es intent 'open battery settings' --es script 'tap:Battery;done:opened battery'" >/dev/null
sleep 5
adb logcat -d -s AGENT AGENT_CMD | tr -d '\r' > "$EV/app-e2e.txt"

EXEC_OK=$(grep -c 'execute action=tap.*ok=true' "$EV/app-e2e.txt")
DONE=$(grep -c 'done summary' "$EV/app-e2e.txt")

{
  echo "## App end-to-end — core AgentLoop on a live screen"
  echo ""
  echo "| Check | Result |"
  echo "|---|---|"
  echo "| Agent service connected | \`$SVC_OK\` |"
  echo "| Loop executed a tap (ok) | \`$EXEC_OK\` |"
  echo "| Loop reached done | \`$DONE\` |"
} | tee -a "${GITHUB_STEP_SUMMARY:-/dev/stdout}"

echo "===== app-e2e.txt ====="; cat "$EV/app-e2e.txt"

if [ "$SVC_OK" = yes ] && [ "${EXEC_OK:-0}" -gt 0 ] && [ "${DONE:-0}" -gt 0 ]; then
  log "loop ran end-to-end on device"
  exit 0
else
  log "app e2e FAILED (svc=$SVC_OK exec=${EXEC_OK:-0} done=${DONE:-0})"
  exit 1
fi
