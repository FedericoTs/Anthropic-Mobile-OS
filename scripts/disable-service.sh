#!/usr/bin/env bash
# Disable all accessibility services on the connected emulator/device.
set -euo pipefail

adb shell settings put secure enabled_accessibility_services ""
adb shell settings put secure accessibility_enabled 0
echo "Disabled all accessibility services."
