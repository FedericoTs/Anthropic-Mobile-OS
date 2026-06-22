# T0 — Hostile-app Accessibility spike: VERDICT

> Status: **TEMPLATE — not yet run.** Fill this in while running the matrix, then
> commit it. Committing this file with a GO/PIVOT verdict is the Definition of
> Done for issue #1.

- Tracking issue: #1
- Run date: _YYYY-MM-DD_
- Emulator: _AVD name_ · API _34_ · image: _AOSP | Google APIs | Google Play_
- Spike build: `:spike` v0.1.0, `:flagsecure-testapp` v0.1.0

## How this was run

```bash
./scripts/run-matrix.sh        # builds, installs, enables, walks Tiers 1-2
# Tier 3 is manual: install a real banking/Integrity app, then:
./scripts/dump-tree.sh
./scripts/tap.sh  "<label>"
./scripts/type.sh "<value>" "<field>"
./scripts/pull-dumps.sh
```

## Evidence matrix

| Tier | App (package) | Installs | Runs | Perceives (tree) | Acts (tap) | Acts (type) | Blocked by | Dump file |
|------|---------------|----------|------|------------------|------------|-------------|------------|-----------|
| 1. Cooperative | `com.android.settings` | ✅ | ✅ | _?_ | _?_ | _n/a_ | — | _tree-…txt_ |
| 2. Controlled FLAG_SECURE | `org.agentnativeos.flagsecuretest` | ✅ | ✅ | _? (marker present?)_ | _?_ | _?_ | _?_ | _tree-…txt_ |
| 3. Real hostile | _e.g. a banking app_ | _?_ | _?_ | _?_ | _?_ | _?_ | _?_ | _tree-…txt_ |

Legend: Perceives = a11y tree non-empty with the app's real text. Acts (tap) =
ACTION_CLICK or gesture changed state. Acts (type) = ACTION_SET_TEXT landed.

### Tier 2 specific question
Did `FLAGSECURE_MARKER_7F3A` appear in the dump?
- [ ] Present → FLAG_SECURE did **not** hide the a11y text tree.
- [ ] Absent → FLAG_SECURE **suppressed** the a11y text tree.
- Could we type into the FLAG_SECURE field? _yes / no_

## Latency note
Wall-clock to dump one active-window tree (cooperative app): _~__ ms_ (seeds the
per-step replanning budget for T2). Method: _logcat timestamp delta / manual_.

## Verdict

> **GO** or **PIVOT** — pick one and justify in one paragraph.

- **GO** if: perception + at least tap works on cooperative apps, and the hostile
  tiers are understood (even if some block). M0 proceeds as planned: T1-T6 / E2-x
  get filed against the a11y foundation.
- **PIVOT** if: hostile/secure apps fully block perception *and* the cooperative
  surface is too thin to demo. M0 re-scopes to SDK / cooperating apps before any
  agent code, and T1-T6 are re-specced accordingly.

**Decision:** _GO | PIVOT_

**Why:** _one paragraph._

**App classes M0 can target:** _list them._

**Follow-ups created:** _link the issues you file next (T1-T6, E2-x) or the
re-scope issue._
