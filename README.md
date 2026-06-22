# Anthropic-Mobile-OS

An open, model-agnostic, agent-native mobile OS. Agents drive your apps out of the
box; you watch the agent perceive, plan, and act, and approve or stop it. Built
app-first on a stock Android emulator (Milestone 0), with a forked AOSP priv-app as
the north star (Milestone 1+).

See [`docs/designs/agent-native-os.md`](docs/designs/agent-native-os.md) for the
full plan and [`DESIGN.md`](DESIGN.md) for the design system.

## Status

Milestone 0 starts with **T0, the hostile-app Accessibility spike** ([issue #1](https://github.com/FedericoTs/Anthropic-Mobile-OS/issues/1)),
which gates everything else. The rest of the M0 backlog is filed only after T0's
GO/PIVOT verdict.

## Repository layout

```
core/                  Agent brain (pure Kotlin): loop, policy gate, event bus, model seam
app/                   On-device app: AccessibilityService runs the loop + the T5 home UI
spike/                 T0 harness: AccessibilityService + tree dump + tap/type (issue #1)
flagsecure-testapp/    Controlled FLAG_SECURE target for the spike's Tier 2
scripts/               adb drivers + CI emulator suite (spike matrix + app loop e2e)
docs/designs/          Plan + locked design direction + mockups
docs/spikes/           Spike verdicts (T0 GO/PIVOT lands here)
DESIGN.md              Design system (type, color, spacing, motion, a11y)
```

## Milestone 0 status

T0 spike: **GO** (`docs/spikes/t0-hostile-app.md`). The agent brain (`core/`) is
built and unit-tested; the on-device app (`app/`) runs that loop through an
`AccessibilityService` and ships the static ambient home. CI runs the brain's
tests on every push and exercises the loop on a real emulator (spike Tiers 1-2 +
app end-to-end). Remaining: the live Claude model call (needs a credential),
Tier-3 on a physical device, and the P2/gated multi-agent + undo work.

## Build (Milestone 0 spike)

Requires the Android SDK (API 34) and JDK 17+. Point Gradle at your SDK:

```bash
echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew :spike:installDebug :flagsecure-testapp:installDebug
```

Then run the spike and record results:

```bash
./scripts/run-matrix.sh
# verdict -> docs/spikes/t0-hostile-app.md
```

Full instructions: [`spike/README.md`](spike/README.md).

## License

Apache-2.0 (planned, per the project plan). Published images ship AOSP + this
project's code only; users add their own apps.
