#!/usr/bin/env bash
# Boot the 'spike34' AVD and wait until it is fully ready, then hand off to
# run-matrix.sh. Needs hardware acceleration (/dev/kvm on Linux) to be usable.
set -euo pipefail

SDK="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="$SDK" ANDROID_HOME="$SDK"
NAME="${1:-spike34}"
ADB="$SDK/platform-tools/adb"

if [ ! -e /dev/kvm ]; then
  echo "WARNING: /dev/kvm not found. The emulator will be unusably slow without" >&2
  echo "hardware acceleration. Run this on a Linux host with KVM, or use Android" >&2
  echo "Studio's emulator on macOS/Windows." >&2
fi

"$SDK/emulator/emulator" -avd "$NAME" -no-snapshot -no-boot-anim -gpu swiftshader_indirect &
echo "Booting '$NAME'... waiting for device."
"$ADB" wait-for-device

echo "Waiting for full boot..."
until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
  sleep 2
done
echo "Emulator '$NAME' ready. Now run: ./scripts/run-matrix.sh"
