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

## Multi-agent coordinator hardening (for the gated stretch goal — from /plan-ceo-review)
- **What:** When the coordinator spawns N parallel sub-agents, define the partial-result/merge policy (1A abort+handoff generalized to N) and an N-times token-cost budget.
- **Why:** Parallel agents multiply both the failure surface and the cost; the headline OpenClaw aha is only credible if partial failures are handled cleanly.
- **Context:** Only relevant once multi-agent orchestration starts (gated on the single-agent loop landing first).
- **Priority:** P2 (gated).

## Reserve aha candidates (parked — from /plan-ceo-review cherry-pick)
- **What:** record-once/replay automations (open, forkable "skills"); an open capability/skill marketplace seed; voice-driven hands-free orchestration.
- **Why:** Each is a strong aha amplifier but not needed for the first demo.
- **Context:** Surface any of these for a decision when M0 is landing. Voice was deferred from M0 in the eng review.
- **Priority:** P3.
