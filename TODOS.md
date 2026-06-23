# TODOS

Captured during /plan-eng-review (2026-06-22). Each item has enough context to pick up cold.

## Shipped since — M0 live on real hardware (2026-06-23)
Single-agent loop proven end-to-end on a real device (Xiaomi/MIUI, Italian locale):
a calculator task and a one-shot timer via the fast path. Built this session, below
the altitude of the plan items below:
- Dual auth (API key + bring-your-own Claude subscription token) + model catalog with
  per-task selection (default = cheapest, Haiku 4.5).
- Live planner in pure :core (AnthropicClient + tiny Json) — round-trip unit-tested
  plus a gated live smoke test; added INTERNET + SET_ALARM permissions.
- Action set grown: `scroll` (wheel pickers / lists) and `invoke` — a direct-intent
  fast path (set_timer/alarm, dial, open_url, web_search, sms/email) that does known
  tasks in ONE action instead of UI-driving.
- Robustness: settle-after-act, re-plan on stale target, stuck-detection, re-plan on a
  failed action (error fed back), re-ask on a prose (non-JSON) reply.
- Perception upgraded to a structured, role-tagged screen (text AND contentDescription;
  partially addresses the a11y-tree budget item below).
- App inventory (launch by package) + agent memory (durable task history, fed back into
  planning and shown on the home screen).

## Device Adaptation — capability discovery ("scan the phone", from on-device testing)
- **What:** On install / first-run (and on package changes) probe the actual device to
  build an adaptive capability profile instead of a hardcoded catalog: which fast-path
  intents actually resolve here, which apps are installed + their entry points, and
  (later) app deep-links / shortcuts. Advertise to the planner only what THIS device can
  do, and cache the profile (a form of device memory).
- **Why:** The capability catalog is static today; on another ROM/locale some capabilities
  have no handler (no mail app, no maps) and advertising dead ones wastes steps. This is
  the layer that makes the OS agent-native on ANY phone — and it feeds the parked open
  capability/skill marketplace.
- **Design / caveats (do not skip):**
  - Probe with `PackageManager.resolveActivity` per representative intent — BUT Android 11+
    package visibility hides handlers unless the queried intents are declared in `<queries>`
    (avoid `QUERY_ALL_PACKAGES`). A naive probe yields false negatives and would wrongly drop
    working capabilities, so discovery REQUIRES matching `<queries>` entries designed with it.
  - Permissions are a SEPARATE axis: SET_ALARM was a permission, not a missing handler.
    Resolution proves a handler exists, not that we're allowed to call it.
  - Cache the device profile (prefs/file via the Json codec); refresh on PACKAGE_ADDED/REMOVED.
  - Security: discovery only probes + advertises; never auto-invoke while probing. Keep the
    fixed allow-list shape — the model names a capability; the device resolves it.
- **Context:** Realizes the "adapt natively to the device" idea raised during on-device
  testing (2026-06-23). Distinct from, but feeds, the capability/skill marketplace reserve.
- **Priority:** P2 — the next capability layer; land before broad multi-app tasks.

## Semantic high-risk classifier for the policy gate (v1)
- **What:** Replace the gate's localized keyword list with a model-based (or richer) risk
  classifier so "is this action irreversible/financial/identity" doesn't depend on matching
  a word in the right language. v0 ships a multilingual keyword list (EN/IT/ES/FR/DE/PT);
  it covers the common cases but a keyword match is brittle (false positives like "ordina" in
  "coordina"; misses for unlisted languages/phrasings).
- **Why:** The confirm gate is the safety centerpiece ("trust"); it must not fail open on a
  localized device. The keyword list is the v0 stopgap; a classifier is the durable fix.
- **Context:** raised 2026-06-23 — the test phone is Italian and the original list was
  English-only, so destructive buttons ("Invia"/"Paga"/"Elimina") weren't gated. Keep the
  classifier OUT of the model loop (the gate must stay an out-of-model check vs injection).
- **Priority:** P2 (v1 trust).

## Persistent narration overlay — watch it act over other apps (future)
- **What:** Today the agent backgrounds the Agent OS app whenever it acts in another app,
  so you can't watch the narration during the act — the core "I can watch it think, so I
  trust it" promise breaks exactly when it matters. Keep a small floating window (the live
  step + Stop/Confirm) pinned over whatever app the agent is driving.
- **Why:** Trust + the confirm gate need to be visible AND reachable while the agent acts on
  a third-party screen. Right now a high-side-effect confirm would surface in a backgrounded
  app — the user might not see it.
- **Design/approach:** Android SYSTEM_ALERT_WINDOW overlay (or a Bubble) hosting a compact
  narration surface: active step + a reachable Stop and, crucially, the Confirm card in the
  thumb zone. Honor the design's restraint (small, calm, dismissible). Needs the
  "Display over other apps" special permission (user-granted) — graceful fallback to the
  in-app feed when not granted. Mind the FLAG_SECURE/overlay interaction from the T0 spike.
- **Context:** raised 2026-06-23 during on-device testing (settings→display worked, but the
  feed vanished while acting). Pairs with the default-launcher path.
- **Priority:** P2 (M0+ trust UX) — high value once real multi-app tasks are common.

## Adaptive result UI — content-aware presentation of the agent's output (future)
- **What:** Give the agent's RESULT its own surface that adapts to what it presents, instead
  of the single settled "✓ summary" row it uses today. By kind: an answer/explanation as a
  calm result block (serif lead + body, distinct from the mono thinking lines and step rows);
  an opened link / search as a tappable chip ("Open Rome in Maps", "Spotify ▸ lo-fi beats");
  a timer/alarm as a compact confirmation ("⧗ 5:00 running"); a plain action task stays a
  settled ✓ — no surface where none is earned.
- **Why:** The payoff of the whole "watch it think" arc is the result, but it currently renders
  the same as any step row, under-serving substantive answers (capabilities list, search…).
  This is functional clarity, not decoration, so it fits the design ethos.
- **Design (DESIGN.md):** stay anti-ornament — "cards earn their existence", "type does the
  work", motion clarifies never decorates; honor prefers-reduced-motion; never render secrets.
  "Evolves with content" = content-appropriate rendering, NOT a generative canvas. Run a
  /design-consultation pass to lock the result-surface pattern + variants against the approved
  mockups BEFORE building — it adds a new component to the OS visual vocabulary.
- **How:** start by inferring the result kind from the last action + the done content (no schema
  change). A structured result type the agent emits (kind + payload) is the fuller adaptive-UI
  version — more power, more surface area; defer unless the inferred approach proves limiting.
- **Context:** raised 2026-06-23 after on-device runs; deferred as a planned future step.
- **Priority:** P2 (v1 UX) — after the core capability/trust work; design pass first.

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
