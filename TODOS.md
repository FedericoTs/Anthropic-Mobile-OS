# TODOS

Captured during /plan-eng-review (2026-06-22). Each item has enough context to pick up cold.

## AOSP custom-build vs Play Integrity (RESOLVED 2026-06-22 in /plan-ceo-review)
- **What:** A custom AOSP build (Milestone 1) trips Play Integrity / hardware attestation, so Integrity-checked apps (banking, etc.) may refuse to run on it.
- **Resolution:** Non-goal for this project. The goal is an open-source "aha" demo, not running your bank. The demo drives open / installed / web apps out of the box; Integrity-gated banking is explicitly out of scope. UnifiedAttestation is the long-term open path IF banking-class apps ever matter. No longer a blocker.
- **Context:** Surfaced by the plan-eng-review outside voice (Play Integrity May 2025 hardware attestation). The M0 hostile-app spike still validates the Accessibility foundation on cooperative apps.

## a11y-tree context/token budget (perf — v1)
- **What:** With per-step replanning, every action ships the Accessibility tree to the model. Busy screens = big trees = high token cost + latency.
- **Why:** Cost and latency could make real multi-step intents impractical on a phone.
- **Context:** Mitigation: send only interactive/visible elements, truncate, cache stable structure. Do this once the M0 per-step latency is actually measured (don't optimize before measuring).

## Human-judged "ambient correctness" eval (v1)
- **What:** An eval that scores whether a proactive surface feels right vs creepy/noisy, not just task completion.
- **Why:** The plan's own biggest open UX question (proactive precision) has no test today; the thing most likely to kill the product is currently untested.
- **Context:** Needed when the proactive/learning home lands in v1. Accepted in plan-eng-review (cross-model Tension 2).

## Proactive prediction battery + cost (v1)
- **What:** When proactive prediction + always-on perception land (v1), continuous perception + periodic model calls hit battery and API cost hard.
- **Why:** Could make the differentiated feature impractical on real hardware.
- **Context:** M0 home is static, so no cost yet. Design the v1 proactive layer with a perception/inference budget from the start.

## iOS bridge via PWA (research — v1+)
- **What:** Decide whether a PWA / web-app marketplace is the cross-platform answer for the iOS world, or iOS support is cut entirely.
- **Why:** Native iOS-app execution is impossible on non-Apple hardware; "run both marketplaces" needs a real answer or an explicit cut.
- **Context:** From the design doc open questions.

## Fully-local / offline model backend (headline v1.1 differentiator — from /plan-ceo-review)
- **What:** Run the agent loop on an on-device model with zero cloud, plugged into the same `ModelProvider` interface as the cloud dual-auth path.
- **Why:** The deepest open/private flex and the one thing Gemini Intelligence structurally cannot do. The cloud dual-auth path carries the openness message for v1; local is the v1.1 upgrade.
- **Context:** Needs real model-quality eval (small phone models are weaker planners). Deferred in the cherry-pick ceremony. Plugs into the ModelProvider seam built in M0.
- **Priority:** P2 (v1.1 headline).

## Multi-agent coordinator hardening (gated stretch — from /plan-ceo-review + eng review #2)
- **What:** Design the coordinator as 1A (parallel planning, serialized actuation). A single device-action scheduler owns the one foreground screen; sub-agents reason in parallel; non-UI tools run parallel ONLY if they don't touch shared observable state (content-provider writes, notifications, permission dialogs are NOT parallel-safe).
- **Stale-plan fix (required):** extend the screen-state settling protocol to the scheduler — re-validate each queued action against the live a11y tree at dequeue; on mismatch RE-PLAN the sub-agent's step (not just abort). Serializing taps does not serialize the world the taps assumed.
- **Confirm gate with N agents:** keep the out-of-model gate per high-side-effect action; surface ONE consolidated review when multiple confirmable actions queue (no N-prompt fatigue, no lead-confirms-for-you); conflicting actions block and ask.
- **Scheduler policy:** FIFO + per-sub-agent deadline + fairness (no starvation stalling the queue).
- **Merge/partial-result is day-one** for multi-agent, not later; N-times token budget.
- **Why:** Parallel agents multiply the failure surface and cost; the headline OpenClaw aha mis-taps live without the stale-plan fix.
- **Context:** Gated on the single-agent loop landing first.
- **Priority:** P2 (gated).

## Reserve aha candidates (parked — from /plan-ceo-review cherry-pick)
- **What:** record-once/replay automations (open, forkable "skills"); an open capability/skill marketplace seed; voice-driven hands-free orchestration.
- **Why:** Each is a strong aha amplifier but not needed for the first demo.
- **Context:** Surface any of these for a decision when M0 is landing. Voice was deferred from M0 in the eng review.
- **Priority:** P3.

## Design system / DESIGN.md (from /plan-design-review)
- **What:** Run `/design-consultation` to produce a DESIGN.md: type scale, color system, spacing, motion.
- **Why:** The design review specified states + accessibility but there is no pinned visual system; without one, the type/color/spacing get reinvented per screen.
- **Context:** Approved idle-home direction is the calm/minimal variant A (mockup in ~/.gstack/projects/.../designs/agent-home-narration-20260622/). Classified APP UI; single violet accent, no purple gradient backgrounds. Do before heavy UI implementation.
- **Priority:** P2.
