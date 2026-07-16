# Anthropic-Mobile-OS

An open, model-agnostic, agent-native mobile OS. Agents drive your apps out of the
box; you watch the agent perceive, plan, and act, and approve or stop it. Built
app-first on a stock Android emulator (Milestone 0), with a forked AOSP priv-app as
the north star (Milestone 1+).

See [`PLAN.md`](PLAN.md) — the **single source of truth** for roadmap, test pack,
and status — plus [`docs/designs/agent-native-os.md`](docs/designs/agent-native-os.md)
for the founding vision and [`DESIGN.md`](DESIGN.md) for the design system.

## Status

**Milestone 0 is live on real hardware** (Xiaomi/MIUI, Italian locale): the
single-agent perceive→plan→gate→act→narrate loop drives real apps end-to-end —
calculator and settings tasks, one-shot device capabilities (timer via intent),
app-chooser recovery, a multilingual confirm gate, agent memory, and undo. The
T0 hostile-app spike returned **GO** (`docs/spikes/t0-hostile-app.md`). Current
work: honest completion verification, the floating trust surface, and the
predictive home — see `PLAN.md` §5 for the phased roadmap.

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
