#!/usr/bin/env bash
# One-time: install a headless Android SDK sufficient to BUILD and RUN the spike.
# Safe to re-run. Writes local.properties. Requires curl, unzip, and a JDK 17+.
set -euo pipefail

SDK="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
CMDLINE_VER="11076708"            # cmdline-tools (latest channel) build number
API="34"
IMAGE="system-images;android-${API};google_apis;x86_64"

mkdir -p "$SDK/cmdline-tools"
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  tmp="$(mktemp -d)"
  echo "Downloading Android command-line tools..."
  curl -sSL -o "$tmp/cmdtools.zip" \
    "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_VER}_latest.zip"
  unzip -q -o "$tmp/cmdtools.zip" -d "$SDK/cmdline-tools"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$tmp"
fi

export ANDROID_SDK_ROOT="$SDK" ANDROID_HOME="$SDK"
SDKMGR="$SDK/cmdline-tools/latest/bin/sdkmanager"

echo "Accepting licenses..."
yes | "$SDKMGR" --licenses >/dev/null 2>&1 || true

echo "Installing packages (platform $API, build-tools, platform-tools, emulator, system image)..."
yes | "$SDKMGR" \
  "platform-tools" \
  "platforms;android-${API}" \
  "build-tools;${API}.0.0" \
  "emulator" \
  "$IMAGE"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
echo "sdk.dir=$SDK" > "$ROOT/local.properties"
echo
echo "Done. SDK: $SDK"
echo "Wrote $ROOT/local.properties (sdk.dir=$SDK)"
echo "Next: ./scripts/create-avd.sh && ./scripts/start-emulator.sh"
