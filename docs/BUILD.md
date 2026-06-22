# Build & run

This guide covers the **agentic shell** (the part you can build and run today).
The AOSP ROM track lives in [`../platform/`](../platform/) and is documented
separately.

> ⚠️ **Heads-up:** the shell was authored in a cloud container **without Android
> tooling**, so it has not been compiled here. The project uses only standard,
> pinned, well-known dependencies and conventional structure. Treat the first
> local build as the real compile — open it in Android Studio, sync, and fix any
> environment-specific nits (SDK versions, etc.). Versions are pinned below.

## Requirements

| Tool | Version |
| --- | --- |
| Android Studio | Ladybug (2024.2) or newer |
| JDK | 17 |
| Android Gradle Plugin | 8.7.2 |
| Kotlin | 2.0.21 |
| Compile/Target SDK | 35 (Android 15) |
| Min SDK | 29 (Android 10) |

These are declared in `system/agentic-shell/gradle/libs.versions.toml`.

## Run it

1. **Open the project**
   ```
   Android Studio → Open → system/agentic-shell
   ```
   Let Gradle sync. (CLI alternative: `cd system/agentic-shell && ./gradlew :app:assembleDebug` once a Gradle wrapper is generated — see note below.)

2. **Pick an AI provider** — three ways, in order of zero-config:
   - **Mock (default):** runs fully offline, no key. Boots immediately so you
     can feel the OS. Selected automatically when no key is present.
   - **Anthropic API:** put your key in `local.properties`:
     ```
     ANTHROPIC_API_KEY=sk-ant-...
     ```
     (Already git-ignored.) The app reads it via `BuildConfig`. In production
     this moves to the on-device secure store / Settings, never the APK.
   - **Subscription:** stub — see [ARCHITECTURE.md](ARCHITECTURE.md).

3. **Run** on an emulator (Pixel, API 35) or a device.

4. **Make AMOS your home screen** to get the OS feel:
   `Settings → Apps → Default apps → Home app → AMOS`
   (Press Home; Android offers to pick a launcher.) Revert anytime by switching
   the Home app back.

## Gradle wrapper

To keep the repo lean, the Gradle **wrapper jar** is not committed. Generate it
once:
```
cd system/agentic-shell
gradle wrapper --gradle-version 8.9
```
(Android Studio also offers to set this up on first sync.)

## Project layout

```
system/agentic-shell/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml          ← all dependency versions, one place
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml         ← registers MainActivity as HOME
        ├── res/                        ← theme, strings, icons
        └── kotlin/os/amos/shell/
            ├── MainActivity.kt          ← the launcher entry point
            ├── ShellApplication.kt
            ├── ai/                      ← Claude provider layer
            ├── agent/                   ← agent runtime + service + samples
            ├── capability/              ← capability bus + built-ins
            ├── apps/                     ← app/capability model & registry
            └── ui/                       ← conversational home, agent tray, theme
```

## Troubleshooting

- **"No key" but you want real Claude:** confirm `ANTHROPIC_API_KEY` is in
  `local.properties` and you re-synced; otherwise the app falls back to Mock by
  design.
- **Compose/Kotlin compiler mismatch:** this project uses the Kotlin 2.0 Compose
  Compiler Gradle plugin (no manual `kotlinCompilerExtensionVersion` pin needed).
  Keep Kotlin and the Compose plugin on the same `2.0.x`.
- **App isn't offered as a launcher:** ensure the `HOME` + `DEFAULT` intent
  categories are present in `AndroidManifest.xml` (they are) and reinstall.
