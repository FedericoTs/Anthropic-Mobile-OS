# T0 — Hostile-app Accessibility spike: VERDICT

> Status: **GO (Tiers 1-2 proven on a real Android 14 emulator).** Tier 3 (a real
> banking / Play-Integrity app) is pending a physical device. Committing this file
> with a GO/PIVOT verdict is the Definition of Done for issue #1.

- Tracking issue: #1
- Run date: 2026-06-22
- Where: GitHub Actions `spike-matrix` job, [run 27957984264](https://github.com/FedericoTs/Anthropic-Mobile-OS/actions/runs/27957984264) (commit `124958b`)
- Emulator: `sdk_gphone64_x86_64`, **Android 14 / API 34**, Google APIs image
- Spike build: `:spike` v0.1.0, `:flagsecure-testapp` v0.1.0
- Evidence artifact: `spike-evidence` (full a11y trees) on the run above

## How this was run

The CI `spike-matrix` job (`scripts/ci-spike-matrix.sh`) on a KVM emulator:
installs both apps, enables the AccessibilityService via adb, then perceives +
acts on Settings (Tier 1) and the FLAG_SECURE app (Tier 2). Locally this is
`./scripts/run-matrix.sh` after `start-emulator.sh`. Tier 3 is manual on a phone.

## Evidence matrix

| Tier | App (package) | Installs | Runs | Perceives (tree) | Acts (tap) | Acts (type) | Blocked by |
|------|---------------|----------|------|------------------|------------|-------------|------------|
| 1. Cooperative | `com.android.settings` | ✅ | ✅ | ✅ 66 nodes, real labels | ✅ `ACTION_CLICK` Battery → true | n/a | — |
| 2. Controlled FLAG_SECURE | `org.agentnativeos.flagsecuretest` | ✅ | ✅ | ✅ 6 nodes, **marker readable** | n/a | ✅ `ACTION_SET_TEXT` → true | — |
| 3. Real hostile | _a real banking / Integrity app_ | _?_ | _?_ | _?_ | _?_ | _?_ | _pending physical device_ |

Tier-1 perceived labels included: Network & internet, Connected devices, Apps,
Notifications, **Battery** (100%), Storage, Security & privacy, Location,
Passwords & accounts, System — plus content-descriptions ("Profile picture,
double tap to open Google Account").

### Tier 2 specific question — does FLAG_SECURE hide the a11y tree?
**No.** The dump exposed the full window under `FLAG_SECURE`:
- `text="FLAGSECURE_MARKER_7F3A — account balance $1,234.56"` (read verbatim)
- the editable field `text="Secret field (type here)" flags=[CFE---]`, and
- `TYPE(target=Secret) -> ACTION_SET_TEXT -> true` (the agent typed into it).

So `FLAG_SECURE` blocks **screenshots / screen capture** but does **not** remove
text from the accessibility tree, and it does not block accessibility actions.

## Latency note
The 66-node Settings tree was traversed, rendered, logged, and written to file
within the same logcat millisecond bucket (dump logged at `14:00:05.207`, file
written `14:00:05.217`) — effectively sub-second at this tree size, so a11y-tree
retrieval is not a per-step bottleneck for T2's replanning at this scale. Precise
millisecond timing is a small follow-up (add a timer around the traversal).

## Verdict

> **GO.**

The risky assumption that gated all of Milestone 0 holds: a normal Android app on
a stock emulator, via an `AccessibilityService`, can **perceive and act on
third-party apps**. Tier 1 proves full perception + a real `ACTION_CLICK` tap on a
cooperative app. Tier 2 proves that even a `FLAG_SECURE` window is fully
perceivable and that text injection (`ACTION_SET_TEXT`) lands. The Accessibility
foundation is real; M0 proceeds on it.

**App classes M0 can target now:** AOSP/cooperative apps and standard third-party
apps, *including* apps that set `FLAG_SECURE` on their display (perception and
action both work through it).

**Still open (Tier 3, needs a physical device):** apps that actively **refuse to
run when an AccessibilityService is enabled** (some banking apps do this as
anti-fraud), and **Play Integrity / hardware attestation** on a real device or a
forked AOSP build (Milestone 1). The emulator cannot answer these (no Play Store,
fails Integrity). Treat banking-class apps as *unconfirmed* until run on a phone.

**Security implication (feeds T4):** because the agent can read secret financial
text even under `FLAG_SECURE`, the credential redaction (password fields already
redacted in the dumper) and the out-of-model confirm gate are load-bearing, not
optional. The agent perceives more than the screen-capture defense suggests.

**Follow-ups:**
- File the rest of Milestone 0 (T1-T6, E2-1..E2-4) against the a11y foundation.
- File a Tier-3 task: run the matrix on a physical phone with a real
  banking/Integrity app; confirm install/run/perceive/act or record the block.
