# `:spike` — T0 Hostile-app Accessibility harness

The first code in this repo. A bare Android app whose only job is to answer one
question with evidence (issue #1):

> Can a normal app on a stock emulator, via an `AccessibilityService`, **perceive
> and act on third-party apps** — including hostile ones (`FLAG_SECURE`,
> Play-Integrity-gated)?

If yes, Milestone 0 proceeds on the Accessibility foundation. If no, M0 pivots to
SDK-cooperating apps. Nothing here is the product; it is the gate.

## What's in the build

| Module | Purpose |
|--------|---------|
| `:spike` | The harness: `SpikeAccessibilityService` + tree dumper + tap/type actuator + `CommandReceiver` (adb-driven) + a one-screen launcher |
| `:flagsecure-testapp` | A controlled Tier-2 target that sets `FLAG_SECURE`, with a known marker string + an editable field |

## Zero to verdict (Linux with KVM)

From a fresh clone, three commands get you to a running spike:

```bash
./scripts/bootstrap-sdk.sh     # install SDK + emulator + API-34 image, write local.properties
./scripts/create-avd.sh        # create the 'spike34' AVD
./scripts/start-emulator.sh    # boot it, wait for ready
./scripts/run-matrix.sh        # build, install, enable, walk Tiers 1-2
```

The emulator needs hardware acceleration (`/dev/kvm` on Linux). On macOS/Windows,
use Android Studio's emulator instead and skip `bootstrap-sdk.sh` /
`start-emulator.sh`.

## Prerequisites (manual setup)

- Android SDK (set `sdk.dir` in `local.properties`, or `ANDROID_HOME`).
- An emulator: **API 34**, a stock image. `adb` on your PATH.
- JDK 17+.

```bash
# one-time: point Gradle at your SDK (bootstrap-sdk.sh does this for you)
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
```

## Run the whole matrix

```bash
./scripts/run-matrix.sh
```

That builds, installs both apps, enables the service, and walks Tiers 1-2.
Tier 3 (a real banking/Integrity app) is manual. Record everything in
[`docs/spikes/t0-hostile-app.md`](../docs/spikes/t0-hostile-app.md).

## Drive it by hand

```bash
./gradlew :spike:installDebug :flagsecure-testapp:installDebug
./scripts/enable-service.sh                 # enable the AccessibilityService

./scripts/dump-tree.sh                       # dump whatever is on screen
./scripts/tap.sh  "Network & internet"       # tap a node by text
./scripts/type.sh "hello" "Search settings"  # type into a field
./scripts/pull-dumps.sh                       # pull tree dumps to ./dumps
```

Raw broadcast form (what the scripts send):

```bash
adb shell am broadcast -n org.agentnativeos.spike/.CommandReceiver \
  -a org.agentnativeos.spike.DUMP
adb shell am broadcast -n org.agentnativeos.spike/.CommandReceiver \
  -a org.agentnativeos.spike.TAP --es text "Battery"
adb shell am broadcast -n org.agentnativeos.spike/.CommandReceiver \
  -a org.agentnativeos.spike.TYPE --es text "hello" --es target "Search"
```

Logcat tags: `SPIKE`, `SPIKE_TREE`, `SPIKE_ACT`, `SPIKE_CMD`.
Dumps on device: `/sdcard/Android/data/org.agentnativeos.spike/files/dumps/`.

## Automatable guard

```bash
./gradlew :spike:connectedDebugAndroidTest
```

`CooperativeTreeDumpTest` proves the platform accessibility-perception path works
(via UiAutomator, the same a11y framework the service uses). The full perceive +
act matrix is the manual run above.

## Notes & caveats

- `CommandReceiver` is `exported="true"` so the adb-shell UID can reach it —
  **spike/emulator only**; any app could trigger these commands. Not for a real
  device.
- `enable-service.sh` overwrites other enabled accessibility services. Use a
  throwaway emulator.
- Password-field text is redacted in dumps by design (see `A11yTreeDumper`).
