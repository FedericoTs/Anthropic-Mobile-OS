# Developing on a real device

The fast loop for working on the agent against a phone over USB. Once set up,
each fix is **`git pull` → install → watch logcat** — no manual APK handling.

## One-time setup

1. **Phone:** enable Developer Options (Settings → About phone → tap *Build
   number* 7×), then turn on **USB debugging** (Settings → System → Developer
   options). Plug in over USB and accept the "Allow USB debugging?" prompt.
2. **Android Studio:** install it (it bundles the right JDK 17 + Android SDK).
3. **Clone + branch:**
   ```bash
   git clone <repo-url> Anthropic-Mobile-OS
   cd Anthropic-Mobile-OS
   git checkout claude/amazing-cannon-1vek67
   ```
4. **Open** the folder in Android Studio and let it finish the Gradle sync
   (it writes `local.properties` with your SDK path automatically — that file is
   git-ignored, never commit it).

## Build + install to the phone

Either:

- **Android Studio:** select your phone in the device dropdown, press **Run ▶**
  (installs and launches), or
- **Command line** (phone connected):
  ```bash
  ./gradlew installDebug        # Windows: gradlew.bat installDebug
  ```
  `installDebug` builds and installs over USB in one step. To reinstall a
  prebuilt APK in place (keeps your saved key): `adb install -r app-debug.apk`.

A same-signature reinstall keeps your data **and** the Accessibility grant, so
you rarely re-do the on-device config below.

## On-device config (first install only)

1. Open **Agent OS** → tap the **swap-model pill** → in Settings paste your
   **Anthropic API key** (and optionally pick a model). The key is stored
   encrypted on-device.
2. Enable the agent: **Settings → Accessibility → Agent OS → On.** This is what
   lets the agent perceive the screen and act.
3. Back on the home screen, type an intent (e.g. *open settings and turn on
   battery saver*) and watch the narration feed.

## Watch a run / debug a stall

Every run streams to logcat under the **`AGENT`** tag — each perceive/plan/act
step, confirms, the final result, and (on a parse miss) the model's raw reply:

```bash
adb logcat -c                       # clear, then start the run on the phone
adb logcat -s AGENT                 # follow just the agent trace
```

When a run stops unexpectedly, copy the `AGENT` lines — that trace is exactly
what's needed to diagnose the planning/acting loop.

## The iterate loop

```
git pull                       # get the latest fix
./gradlew installDebug         # rebuild + install over USB (gradlew.bat on Windows)
adb logcat -c && adb logcat -s AGENT   # watch the next run
```

## Tests (no device needed)

```bash
./gradlew test                 # all JVM unit tests; the live model test self-skips
ANTHROPIC_API_KEY=sk-... ./gradlew :core:test   # also runs the live smoke test (cheapest model)
```
