#!/usr/bin/env bash
# Enable the spike AccessibilityService on the connected emulator/device.
# NOTE: this overwrites any other enabled accessibility services — fine for a
# dedicated spike emulator. Re-enable yours afterward if you reuse the device.
set -euo pipefail

PKG="org.agentnativeos.spike"
SVC="$PKG/$PKG.SpikeAccessibilityService"

adb shell settings put secure enabled_accessibility_services "$SVC"
adb shell settings put secure accessibility_enabled 1
echo "Enabled: $SVC"
echo "Verify: adb shell settings get secure enabled_accessibility_services"
