# TODOS

Captured during /plan-eng-review (2026-06-22). Each item has enough context to pick up cold.

## AOSP custom-build vs Play Integrity (strategy — revisit in /plan-ceo-review)
- **What:** A custom AOSP build (Milestone 1) trips Play Integrity / hardware attestation, so Integrity-checked apps (banking, etc.) may refuse to run on it.
- **Why:** Conflicts with the core "drives your real apps" promise. The M0 -> M1 trajectory may remove access to the very apps that make Milestone 0 compelling.
- **Context:** Surfaced by the plan-eng-review outside voice. Partly answered by the M0 hostile-app spike: if the spike shows Integrity blocks the high-value apps, the full-custom-OS trajectory needs rethinking (stay userspace longer, target a device that can pass attestation, or lean into SDK-cooperating apps).
- **Depends on:** M0 hostile-app spike result.

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
