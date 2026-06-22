#!/usr/bin/env bash
# Create the 'spike34' AVD (API 34, google_apis, x86_64). Safe to re-run.
set -euo pipefail

SDK="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT="$SDK" ANDROID_HOME="$SDK"
AVDMGR="$SDK/cmdline-tools/latest/bin/avdmanager"
NAME="${1:-spike34}"
IMAGE="system-images;android-34;google_apis;x86_64"

if "$AVDMGR" list avd 2>/dev/null | grep -q "Name: $NAME"; then
  echo "AVD '$NAME' already exists."
else
  echo "Creating AVD '$NAME'..."
  echo "no" | "$AVDMGR" create avd -n "$NAME" -k "$IMAGE" --device "pixel_6" --force
fi
echo "Start it with: ./scripts/start-emulator.sh $NAME"
