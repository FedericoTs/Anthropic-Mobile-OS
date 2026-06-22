#!/usr/bin/env bash
# Runs the full on-emulator suite inside the android-emulator-runner step and
# aggregates the exit code, so the job is red if any part fails but every part
# still runs (and uploads evidence).
set -uo pipefail

EV="${1:-evidence}"
mkdir -p "$EV"
rc=0

echo "== connected instrumented test (spike perceive guard) =="
./gradlew :spike:connectedDebugAndroidTest --stacktrace > "$EV/connected-test.txt" 2>&1 \
  || echo "[suite] connected test non-zero (captured in $EV/connected-test.txt)"

echo "== T0 spike matrix (Tiers 1-2) =="
bash scripts/ci-spike-matrix.sh "$EV" || rc=1

echo "== app end-to-end (loop on device) =="
bash scripts/ci-app-e2e.sh "$EV" || rc=1

exit "$rc"
