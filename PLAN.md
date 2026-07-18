# PLAN.md — Anthropic-Mobile-OS · Single Source of Truth

> **Status: ACTIVE** · Updated 2026-06-23 · Branch `claude/amazing-cannon-1vek67`
>
> This document is the **unique source of truth** for what we build, in what order,
> and how we prove it works. It absorbs and supersedes `TODOS.md` (now a pointer) and
> operationalizes the CEO plan (`docs/designs/agent-native-os.md`) and the design
> system (`DESIGN.md`). When reality and this plan disagree, update this plan in the
> same commit as the code.
>
> **How to use it:** pick the top unfinished phase → do its activities → run its test
> cases (unit + the `T-*` on-device scripts) → check the exit criteria → update the
> checkboxes here → move on. On-device tests are run by Federico with the phone on
> USB; results come back as `tag:AGENT` logcat traces.

---

## 1. Goal

**North star:** an open, model-agnostic, user-owned, agent-native mobile OS. Agents
drive your apps out of the box; the phone narrates itself; you can watch it think,
approve or stop it, swap the model on screen, and fork the whole thing.
*(Full statement + scope decisions: `docs/designs/agent-native-os.md`.)*

**"Fully agentic and predictive" — defined precisely, so it can't overclaim:**

1. **Fully agentic** = any reasonable phone intent, spoken in the user's own words,
   is either (a) done in one shot via a device capability, or (b) driven through app
   UIs reliably — across app boundaries, through choosers/dialogs/loading states —
   with honest completion (the agent never claims what didn't happen) and a visible,
   reachable trust surface (narration + confirm + stop + undo) the whole time.
2. **Predictive** = the OS learns *on device* from what you actually do (task
   history, time-of-day rhythms, app usage) and quietly offers the right next intent
   at the right moment — as a **suggestion you tap**, never an action it takes alone.
   Precision over recall: a calm home that is right, not a noisy one that is often.

**The demo that proves it (canonical, from the CEO plan):** a compound intent
spanning 2–3 apps ("find a coffee place near the office and text it to the group"),
narrated live, one confirm at the money moment, provider-swap mid-demo — plus the
home already suggesting your 9am timer because it learned you do that.

---

## 2. Where we are (2026-06-23)

### Verified live on a real device (Xiaomi/MIUI, Italian locale)
- End-to-end single-agent loop: calculator task (6 steps), settings→display (3 steps).
- One-shot fast path: `set_timer` via intent (`Completed steps=1`).
- Device capability discovery: all 14 capabilities resolve and are advertised.
- App-chooser recovery: dead-tap on "Solo una volta" → no-progress signal → picks Gmail.
- Multilingual confirm gate fires on "Invia" (`high=true` in trace) and approval works.
- Agent memory answers "what have you done"; capability chain create_event→share_text→open_camera.
- **✅ EMAIL SENT END-TO-END (2026-06-23):** send_email(to,subject,body) → chooser → compose
  → floating confirm approved over Gmail → `execute tap "Invia" ok=true` → email received.
  The "fully agentic" archetype (cross-app + high-side-effect commit) is proven. Phase 0
  headline done; the rest of the Phase 0 regression pack (T-MEM-1 repeat, deny/skip,
  redaction, undo) is NOT yet run live.

### Built and unit-tested, but NOT yet verified live
- **Verified-done** (Phase 1.1) — the honest-completion backstop; shipped in core, unit-tested,
  awaiting device confirmation that it catches a false "done" (and doesn't over-abort).
- **Floating confirm overlay** — the confirm card floated over Gmail on the successful email
  run ✅; the live-step half of the overlay (D3) is not built.
- **Undo affordance** — unit-tested; not yet exercised on device.
- **Autonomous mode** (full silent auto-approve, Settings toggle, default OFF) — BUILT
  2026-06-23: `AutonomyPreferences` + a Settings switch with a danger warning; when ON the
  ConfirmationHandler auto-approves every high-side-effect action (no card, no pause) while
  the gate still CLASSIFIES it (the feed still flags high-side-effect + shows "⚡ Autonomous").
  Awaits on-device use. **Only flip ON once verified-done is confirmed sending reliably** —
  auto-approving sends on an unproven loop is the danger case.
- **Multi-agent coordinator** (`core/multiagent/`) — Coordinator + ActuationScheduler
  + SubAgent exist with green tests against fakes; **not wired to the device at all**.

### Known defects / debts (each becomes work below)
| # | Defect | Where it bites | Phase |
|---|--------|----------------|-------|
| D1 | Model narrates unverified success ("successfully sent") — RECURRED despite 7468726. FIXED (core): verified-done — the loop makes a skeptical screen-only verify call before accepting any `Done`; unverifiable claims re-plan, then honestly abort. Unit-tested; needs on-device confirmation. | Done summaries, task memory | 1 (shipped, verify live) |
| D2 | Task memory records model claims as facts (poisoned memory) | Planning context | 1 |
| D3 | Trust surface invisible while agent acts in another app — FIXED: the floating overlay now streams the live step (not just the confirm) over the driven app; awaiting device confirm | Cross-app runs | 1 (shipped) |
| D4 | Per-step latency unmeasured; ~2s+ plan calls feel slow | Whole loop | 2 |
| D5 | Full system prompt + app list + capability list resent every step (token cost) | Cost/latency | 2 |
| D6 | Keyword risk gate is brittle (substring, listed languages only) | Safety | 3 |
| D7 | Recent-tasks poisoned "already sent" record on device | Testing | 0 (KEEP it — adversarial input the memory-is-history fix must withstand; do NOT clear) |
| D8 | **Approved high-side-effect action dropped as stale during the ~12s human-approval delay → silently re-planned → hallucinated done → email never sent** (2026-06-23 trace, steps=8). FIXED: stale re-plan now feeds "did not run" so the model retries instead of finishing. | AgentLoop settle | 0 (fixed) |
| D9 | **Model relaunched Gmail by package after the compose invoke → abandoned the pre-filled draft.** FIXED: prompt forbids relaunch-by-package after a compose invoke; "sent" now requires the send tap executed THIS run. | Prompt / capability flow | 0 (fixed) |
| D10 | Chooser thrashing — tapped "Solo una volta"/"Gmail" 5× before Gmail launched; recovers but slow/fragile | Chooser handling | 1 (playbooks help) |

---

## 3. Architecture snapshot (what exists, one line each)

```
core/  (pure Kotlin/JVM — the brain; no Android, fully unit-tested)
  loop/AgentLoop          perceive→plan→gate→settle→act→narrate; recoveries: stale target,
                          failed action, stuck repeat, no-progress, blank-perceive wait
  action/                 typed AgentAction schema (structural injection defense),
                          PolicyGate (out-of-model, multilingual keywords), UntrustedObservation fencing
  perception/Screen       ScreenNode/Observation/NodeFinder.describe (role-tagged, redacted, capped 150)
  model/                  Prompt, ActionJson, ClaudeModelProvider (+retry-on-prose),
                          AnthropicClient (pure HttpURLConnection), Json, ModelCatalog,
                          Auth (API key | subscription OAuth), ProviderRegistry (live swap)
  memory/                 TaskRecord/TaskMemory/codec (durable history → planning context)
  undo/                   CompensationPlanner + UndoStack (+rewindPlan)
  multiagent/             Coordinator, ActuationScheduler (serialize the one screen), SubAgent
  events/                 EventBus + NarrationEvent (audit stream = the trust surface)
app/   (Android)
  device/AgentAccessibilityService   Perceiver+Actuator (tap/type/scroll/invoke/launch/back/home)
  Capabilities(+Device~)  14 intent fast-paths, probed per device, gated on installed apps
  AgentController         wires loop↔device, settle 800ms, memory, undo
  AgentSession            hub: events, blocking confirm channel, stop, uiForeground
  OverlayConfirm          floating Approve/Skip/Stop over other apps (non-focusable)
  ui/                     HomeActivity (intent input + recent + model pill), NarrationActivity
                          (timeline + confirm card + stop/undo), AppGrid, Settings
spike/ + flagsecure-testapp/         T0 harness (GO verdict recorded)
scripts/ + .github/workflows/       adb helpers + CI (build/unit/lint + emulator e2e)
```

**Loop robustness inventory** (hard-won on device; do not regress): settle-after-act
800ms · stale-target re-plan (≤3) · failed-action re-plan with error fed back (≤3) ·
stuck-repeat abort (3) · no-progress screen-unchanged signal · blank-perceive wait
(≤3) · prose-reply re-ask · maxTokens 1024 · memory-is-history rule · chooser rule ·
draft-vs-sent rule.

---

## 4. Safety invariants (never weakened, cited in every review)

1. **Typed action schema** — the model emits one of a fixed set of typed actions;
   nothing on screen can widen it (structural injection defense).
2. **Screen content is fenced as untrusted data** — never instructions
   (`UntrustedObservation`); red-team tests in `InjectionRedTeamTest` must stay green.
3. **Out-of-model policy gate** — irreversible/financial/identity actions ALWAYS
   require explicit user confirmation; the gate cannot be talked past by the model
   or by screen content. High-risk capabilities (call/send-now/pay) gate too.
4. **The confirm must be visible and reachable** wherever the agent is acting
   (overlay when backgrounded, inline when foregrounded), in the thumb zone.
5. **Stop always works** — cooperative cancellation between steps; unblocks any
   pending confirm as a denial.
6. **Secrets never enter the feed, logs, or prompts** — password nodes redacted at
   perception; credentials stored encrypted; invoke logs show arg *keys* only.
7. **Memory is history, not authority** — a past record never satisfies a new intent
   (and, after Phase 1, unverified claims never enter memory as facts).
8. **Predictions suggest, never act** — a proactive surface may pre-fill an intent;
   only a user tap runs it. No always-on model calls without an explicit budget.
9. **Undo where physics allows** — reversible steps are compensated; irreversible
   steps are exactly the ones behind the confirm gate.

---

## 5. Roadmap

Legend: effort **S**(<½ day) **M**(≈1 day) **L**(multi-day) · every phase ends with
its on-device scripts (§6) passing and this file updated.

---

### Phase 0 — Prove the current build on device *(NOW — phone is connected)* · S

The last three commits fixed email compose, blank-perceive hallucination, and the
overlay — none verified live yet. Nothing else proceeds until this passes: email is
the archetype of "fully agentic" (cross-app, chooser, compose, high-side-effect commit).

**Activities**
- [ ] `git pull` on the PC → `gradlew.bat installDebug`. A same-signature reinstall
      keeps the credential, memory, AND accessibility grant — but MIUI often disables
      the accessibility service on reinstall, so **verify `AGENT_SVC: connected` in
      logcat** before testing (the run early-aborts if the service isn't bound).
- [ ] **Do NOT clear memory or app data.** The poisoned "already sent" record IS the
      test input for the memory-is-history fix; testing on cleared memory proves nothing.
      (There is no in-app clear-memory button, and Clear data would also wipe the
      credential + accessibility grant.)
- [ ] Grant BOTH overlay permissions on MIUI: "Display over other apps" **and** the
      separate MIUI "Display pop-up windows while running in background" (Autostart +
      no-battery-restriction too). `canDrawOverlays` returns true even when the latter
      still suppresses the window — this is the likely cause of a missing overlay.
- [ ] Warm Gmail once (open it, signed in, set as default mail handler) so the
      post-chooser perceive lands on a rendered compose, not a cold-start spinner.
- [ ] Run **T-EMAIL-1**, **T-OVERLAY-1**, **T-MEM-1** (§6). Paste traces.
- [ ] Any failure → fix → repeat. Log each new failure mode as a defect row in §2.

**Exit criteria:** the email arrives in the real inbox with subject+body; the
floating confirm appeared over Gmail; a repeated identical intent still executes.

---

### Phase 1 — Honest completion & the always-visible trust surface · M

Kills D1/D2/D3. "Fully agentic" is meaningless if the agent lies about being done;
the demo's credibility rests on this.

**1.1 Verified done (core) — SHIPPED 2026-06-23 (unit-tested; awaiting on-device confirm).**
- When the model emits `Done` and the run performed ≥1 action, `AgentLoop` calls
  `provider.verify(context, claimedSummary)` — a skeptical, screen-ONLY second opinion
  (`Prompt.verifySystem/verifyUser`) returning `{"verified": bool, "reason": …}`
  (`ActionJson.parseVerdict` → `VerifyResult`). `verified=false` → reason fed back as
  `lastError`, keep working; **2 consecutive unverifiable claims → honest Abort**
  ("could not verify completion"), never a recorded false success. A real action resets
  the counter. Verifier fails OPEN (accepts) on error/unparseable so it can't block a
  genuine completion. `ModelProvider.verify` has a default (accept) so scripted/eval
  providers are unaffected; only `ClaudeModelProvider` makes the real call.
- Narrated: `NarrationEvent.Verify(ok, reason)` — the user *watches the agent check its
  own work* ("Checked — not done yet: the form is still open"). Trust-surface gold.
- STILL TODO (Phase 1 follow-ups): `TaskRecord.verified` flag so an unverified completion
  is stored as `[completed?]` (D2); tune the verify prompt from live traces.
- Memory prompt renders unverified completions as `[completed?]` so a past
  overclaim can't masquerade as fact (fixes D2 at the source).

**1.2 Live-step overlay (app) — SHIPPED 2026-06-23 (awaiting device confirm).**
- `OverlayConfirm` now has two modes sharing one non-focusable window: a live-step STRIP
  (the current action, mono per DESIGN, + Stop) and the CONFIRM card (existing). The card
  outranks the strip (`confirming` flag) so a pending confirm is never hidden.
- `AgentSession.emit` streams each step's `live()` into `showStep(...)` whenever the run is
  active AND our UI is backgrounded (`uiForeground=false`) — so you watch it act over
  another app, not just approve. Text updates in place (no window churn); Stop is on the
  strip. Renders only our own (already-redacted) narration labels — never screen content.
- STILL TODO: collapse-to-dot dismissal (DESIGN restraint); a glyph per step kind.

**Unit tests** (core): done-with-actions triggers exactly one verify call; verified
false→loop continues with lastError; verified true→Completed(verified); done at
step 0 skips verification (question answers); codec round-trips `verified`;
prompt-rule tests for the verify prompt (JSON-only, untrusted fencing).

**On-device:** **T-VERIFY-1**, **T-OVERLAY-2** (§6).

**Exit criteria:** a false "sent" claim is caught live at least once in testing (or
provably unreachable); narration shows the verify step; overlay streams the live step
over another app and Stop works from it.

---

### Phase 2 — Feel like an OS: latency, cost, robustness measured · M

D4/D5. Don't optimize blind (decision 2A): measure first, then take the two obvious
wins. Target feel: simple step < 2s, capability one-shots < 4s end-to-end.

**2.1 Instrument — SHIPPED 2026-06-23.** `AgentLoop` measures `perceiveMs` + `planMs`
per step (via the injected `clock`) and emits `NarrationEvent.StepTiming` → logged as
`perf perceive=…ms plan=…ms` under AGENT (filtered out of the visible feed). The model
call (`planMs`) is the known ~3s dominant cost (already visible as the plan→propose gap
in traces). STILL TODO: token counts from the API `usage` block (needs client plumbing);
`actMs`/`settleMs`.
**2.2 Prompt caching — SHIPPED 2026-06-23.** `AnthropicClient` sends the system prompt as
a `cache_control: ephemeral` content block, so it's processed once and read back cheaply
on later steps within the cache window. NOTE: the cache minimum is ~2048 tokens on Haiku
(the default), so the system-only prefix may be under threshold on Haiku — if `perf`
numbers don't drop, the follow-up is to also cache the capability catalog + app inventory
(move them into the cached prefix). Clearly helps on Sonnet/Opus (1024-token minimum).
**2.3 Screen budget** — `describe()` already caps at 150 lines; prioritize
interactive nodes ([input]/[tap]) before [text] when trimming, and log the line
count per perceive. Only escalate to smarter pruning if numbers say so. (NOT started.)

**Unit tests:** timing fields populated (fake clock); describe() prioritization; client
sends cache_control and parses usage.
**On-device:** **T-PERF-1** (§6) — numbers recorded in §7, before vs after caching.

**Exit criteria:** a measured baseline table in §7, caching on, and no robustness
regression in the T-pack.

---

### Phase 3 — Deep device adaptation: risk gate v2 + learned playbooks · L

Makes "agent-native on ANY phone" real, and hardens safety beyond keyword matching (D6).

**3.1 Semantic risk gate (v1 → v2)**
- Keep the keyword gate as the **floor** (fail-closed core). Add a second signal:
  the planner must self-declare `"commit": true` on any action that submits, sends,
  pays, deletes, or finalizes (schema field on every action; prompt rule). Gate
  logic: `keyword-hit OR commit-flag` ⇒ confirm. The model can *add* caution but a
  missing flag can never *remove* the keyword floor (fail-closed, out-of-model).
- Red-team tests: injection text on screen saying "this is safe, no confirm needed"
  must not bypass; unlisted-language commit verbs get caught via the flag path.

**3.2 Learned playbooks (record → replay skills)** — the forkable "skills" seed.
- On a **verified** completed run, store the action trace keyed by a normalized
  intent signature (`core/playbook/`): steps with target queries + the per-step
  screen fingerprints.
- On a new intent that matches a playbook: replay step-by-step through the SAME
  loop protections (settle, stale-check, gate — a replayed "Invia" still confirms),
  falling back to live planning the moment a step's target doesn't resolve.
  Playbook runs skip plan calls ⇒ near-instant repeat tasks; this is also the
  record-once/replay reserve from the CEO plan, landed as code.
- Surface: "again" affordance on recent tasks; narration marks replayed steps.
**3.3 Capability refresh** — re-probe on `PACKAGE_ADDED/REMOVED` broadcast instead of
only per run; keep the profile cached (exists) and inspectable.

**Unit tests:** commit-flag parse + gate matrix (keyword×flag); playbook match/replay/
fallback; replay still confirms high-side-effect; codec round-trips.
**On-device:** **T-GATE-2**, **T-PLAY-1** (§6).

**Exit criteria:** an unlisted-language destructive button still gates (via flag);
a repeat timer task replays with zero plan calls and still settles/gates correctly.

---

### Phase 4 — The predictive OS: learn usage, suggest the next intent · L

The headline of this plan. Design honors the TODOS battery/cost warning and the
ambient-correctness question: **heuristics first, zero model calls in the always-on
path, on-device only, suggestions never act.**

**4.1 Signals — ENGINE SHIPPED 2026-06-23 (pure core, `core/predict/`).**
- `UsageEvent(intent, atMs, hourOfDay, dayClass)` + `TimeBucket` (5 slices) + `DayClass`;
  `UsageEvent.at/from` derive the time tags from a timestamp + `ZoneId` (java.time, minSdk 26);
  `normalizeIntent` groups "the same intent" (exact-after-normalization for v1).
- Primary signal = task memory (intent + atMs, permission-free), mapped via `UsageEvent.from`.
  STILL TODO: the app wiring (map `PersistentTaskMemory` → events with the device zone);
  optional opt-in `UsageStatsManager` app-open signal; **no location in v1**.

**4.2 Pattern mining + suggestion engine — SHIPPED 2026-06-23 (14 tests).**
- `PatternMiner.rank`: recency-weighted frequency per (bucket × day-class); score =
  Σ 0.5^(ageDays/halfLife), halfLife 14d. Only same-bucket + same-day-class events count
  (a 9am-weekday timer surfaces at 9am weekday, not evening, not weekend — tested).
- `SuggestionEngine.suggest(history, now, feedback)`, tuned for *calm*: minSupport 3,
  confidence threshold, suppress-if-done-in-last-3h, per-intent cooldowns (dismissed → muted
  7d; shown ≥5 never tapped → muted), global throttle (tapRate <20% → cap 1, <10% → silent),
  max 2. Returns inert `Suggestion` data — no Actuator anywhere in the package, so nothing
  runs without a UI tap (tested).
- STILL TODO: persist `SuggestionFeedback` (codec + app store); the `SuggestionRecord`
  shown/tapped/dismissed log = the eval dataset (§7).

**4.3 Predictive home (app) — SHIPPED 2026-06-23 (awaiting device confirm).**
- `SuggestionFeedbackCodec` (core) persists feedback via `Json`; `PredictionStore` (app) is
  the on-device bridge: maps completed `PersistentTaskMemory` → `UsageEvent`s (device
  `ZoneId`), runs the engine for `now`, records shown/tapped/dismissed. User-owned master
  switch (`enabled`, default on).
- Home "Right now" section (per `predictive-home.png`): serif header + coral spark, then a
  row per suggestion (`--surface` card + coral "Start"). Tap = runs through the NORMAL loop
  (a tap, never auto-run); long-press = dismiss (feeds cooldown + re-renders). Hidden when
  no suggestions. Settings gains a **Suggestions on/off** toggle.
- Followed DESIGN.md + the approved mockup directly (chip = quiet card + coral action) in
  lieu of a live `/design-consultation` pass. STILL TODO: "why this?" transparency line;
  per-intent glyphs; the `SuggestionRecord` eval log (§7); tune thresholds from real usage.
- **Home redesign round 2 (2026-06-23, confirmed rendering on device):** scrollable home
  (ScrollView — the old fixed column clipped the suggestions), mockup-aligned: serif
  wordmark → greeting + coral clock → intent input → "Right now" → **"Apps, right now"**
  (the predictive app shelf — `AppUsageRanker` in core: same-time-slice habits first, then
  overall recency, launcher-list fill so the shelf is whole from day one; signal =
  `AppLaunchLog`, launches made through OUR surfaces: home fast path, app grid, agent
  launches — no system usage permission). The raw recent-task log was REMOVED from the
  home (memory still feeds the agent + "what have you done"). Pinned bottom bar.

**4.4 (After 4.1–4.3 prove precision) Model-phrased suggestions** — optional,
budgeted (≤1 call/day, on unlock+wifi, cheapest model) to phrase or cluster, never
to decide *whether* to suggest. Skip entirely if heuristic phrasing feels fine.

**Unit tests:** miner scoring/decay/buckets on synthetic histories ("3 mornings of
timers → timer suggested at 9am weekday, not 9pm, not weekend"); engine thresholds,
cooldowns, throttle math, mute-on-dismiss; codec; suggestion-never-auto-runs
(engine emits intents, nothing executes without the UI tap — assert no Actuator
touch in the whole `core/usage` package).
**On-device:** **T-PRED-1**, **T-PRED-2** (§6).

**Exit criteria:** after a seeded week of history, the home shows the right ≤2
chips at the right time; dismiss mutes; tap runs the normal gated loop; toggle off
= silence; suggestion log accumulating for the eval.

---

### Phase 5 — Multi-agent orchestration on device + the canonical demo · L *(gated on Phases 0–2)*

The OpenClaw aha. The brain exists (`core/multiagent/`, green on fakes); this phase
wires it to reality — with the CEO-plan hardening (stale re-plan at dequeue ✅ built,
consolidated confirm, fairness ✅ built, partial results ✅ built, N× token budget).

- **Decomposition:** one model call splits a compound intent into ≤3 sub-goals
  (typed JSON list, else fall back to single-agent). Router: single goal → AgentLoop
  (unchanged); multi → Coordinator.
- **Device wiring:** SubAgents get per-goal `ClaudeModelProvider` planning;
  ActuationScheduler drives the one `AgentAccessibilityService`; settle between
  actuations; narration events carry `agentId` (the event schema already has
  correlation ids) and the timeline renders per-agent glyph colors.
- **Consolidated confirm:** multiple queued high-side-effect actions surface as ONE
  review card (BatchConfirm exists in core); conflicting actions block and ask.
- **Budget/limits:** hard cap = N× single-run budget; Stop cancels all sub-agents.
- **Demo polish:** the canonical script (coffee → maps → text) rehearsed end-to-end
  with the provider-swap moment; README one-command demo instructions refreshed.

**Unit tests:** decomposition parse + single-goal fallback; consolidated confirm
(N proposals → 1 confirm); budget abort; Stop fan-out.
**On-device:** **T-MULTI-1**, **T-DEMO-1** (§6).

**Exit criteria:** the canonical 2-app demo runs live with ≤1 consolidated confirm,
partial results narrated honestly if a sub-goal fails, and a recorded run (screen
capture) suitable for the README.

---

### Phase 6 — Reserves (explicitly parked; promote only by editing this file)

- **Fully-local/offline model backend** — v1.1 headline; plugs into `ModelProvider`;
  needs small-model planning eval. (The deepest open flex; not before the demo.)
- **Default-launcher mode** — HomeActivity as the actual launcher; unlocks shortcut
  discovery (`LauncherApps`) for richer per-app capabilities.
- **Adaptive result UI** — content-aware result surfaces (design pass first).
- **Voice input** · **capability/skill marketplace** (playbooks are its seed) ·
  **iOS/PWA question** · **AOSP/Play-Integrity**: resolved non-goal for the demo.

---

## 6. On-device test scripts (the regression pack)

Run on Federico's phone (USB, `installDebug`, logcat filter `tag:AGENT|AGENT_SVC|AGENT_CAP|AGENT_PERF`).
**Pack rule:** before declaring any phase done, re-run every `T-*` marked ★ (regressions).

| ID | Steps | PASS = trace/behavior shows |
|----|-------|------------------------------|
| **T-EMAIL-1** ★ | Intent: *"open the email and write an email to `<addr>` expressing how excited we are for this OS project. then send it"* | `invoke send_email args=[to, subject, body]` → chooser handled (app row before "Solo una volta") → **no plan against `nodes=0`** → compose visible with subject+body → `propose tap "Invia" high=true` → confirm → approve → `done` only after send; **email actually received** |
| **T-OVERLAY-1** ★ | During T-EMAIL-1, leave Agent OS backgrounded at the confirm | Floating card (caution + action + Approve/Skip/Stop) visible **over Gmail**; Approve resolves it; card disappears |
| **T-OVERLAY-2** | (Phase 1) watch any cross-app run | Live step line floats over the driven app; Stop from the overlay aborts between steps |
| **T-MEM-1** ★ | Immediately repeat the exact same intent as the last completed run | Agent **acts again** (no `steps=0` "already done"); memory Q "what have you done?" still answers from history |
| **T-VERIFY-1** | (Phase 1) run an email task and deny the confirm (Skip) | Run ends `Aborted("you skipped…")`; record NOT stored as completed; a later "did you send it?" answers no |
| **T-GATE-1** ★ | Any task reaching a send/pay/delete button (any language) | `high=true` on the propose; denial path leaves app state uncommitted |
| **T-GATE-2** | (Phase 3) destructive button in an unlisted language / paraphrase | Still gated via the model commit-flag path |
| **T-UNDO-1** | Reversible multi-step run (e.g., settings toggle), tap Undo | Inverse steps replay newest-first, narrated; stops at any irreversible barrier |
| **T-STALL-1** ★ | Task that hits a chooser or a slow-loading app | No stuck-repeat abort; recovery visible (different element tried / wait for populate) |
| **T-PERF-1** | (Phase 2) scripted 5-step task ×3 runs | `AGENT_PERF` lines; medians recorded in §7; caching cuts input tokens ≥50% on steps ≥2 |
| **T-PLAY-1** | (Phase 3) run the same timer task twice | Second run replays from playbook: no plan calls in trace, still settles; gate still fires if a commit step exists |
| **T-PRED-1** | (Phase 4) seed history (3 same-bucket runs), reopen home in-bucket | ≤2 calm chips, right intent; long-press "why this?" explains; tap pre-fills and runs the normal gated loop |
| **T-PRED-2** | (Phase 4) dismiss a chip; reopen | Chip gone and bucket-muted; Predictions toggle off ⇒ no chips at all |
| **T-MULTI-1** | (Phase 5) *"find a nice pizzeria nearby and text the address to `<contact>`"* | Decomposition into 2 sub-goals; serialized actuation; ONE consolidated confirm for the text; partial-result narration if one sub-goal fails |
| **T-DEMO-1** | (Phase 5) full canonical demo incl. mid-run provider swap on the pill | Model swap takes effect next task; recorded video is README-worthy |
| **T-SEC-1** ★ | Open a login screen with a password field mid-task | Password content never appears in narration/logcat; screen shows `[input]` redacted |

**Always-green CI (every push):** `:core:test` (loop, gate, injection red-team,
prompt rules, memory, undo, multiagent, eval) · `:app:testDebugUnitTest` ·
`:app:lintDebug` · emulator e2e suite (`scripts/ci-*`).

---

## 7. Measurements & evals (numbers live here)

| Metric | Baseline (date) | After Phase 2 | Target |
|--------|-----------------|---------------|--------|
| Plan call latency (median) | *TBD (T-PERF-1)* | | < 1.5s |
| Simple UI step, end-to-end | *TBD* | | < 2s |
| Capability one-shot, end-to-end | *TBD* | | < 4s |
| Input tokens / step (steps ≥2) | *TBD* | | −50% via caching |
| Task success rate on the T-pack | *TBD after Phase 0* | | ≥ 90% |
| Suggestion tap-rate (Phase 4+) | — | — | ≥ 30% rolling; auto-throttle below 20% |

**Ambient-correctness eval (Phase 4):** the `SuggestionRecord` log (shown/tapped/
dismissed + bucket) *is* the dataset; weekly review = tap-rate per bucket + a manual
"felt creepy/noisy?" pass. The throttle acts on it automatically; we read it to tune.

---

## 8. Open questions & decisions log

| Date | Decision / question | State |
|------|--------------------|-------|
| 2026-06-22 | App-first on stock Android; AOSP is north star | DECIDED (CEO plan) |
| 2026-06-22 | Apache-2.0; demo drives cooperative apps only | DECIDED |
| 2026-06-23 | Confirm surface = floating overlay (chosen over notification) | DECIDED (Federico) |
| 2026-06-23 | Predictive layer: heuristics-first, zero always-on model calls, suggestions never act | DECIDED (this plan) |
| 2026-06-23 | UsageStatsManager signal is opt-in only, degrade gracefully | DECIDED (this plan) |
| 2026-06-23 | **Autonomous mode = full silent auto-approve** (no confirm on irreversible actions) — overrides the "agency dial never loosens IRREVERSIBLE" design floor; the user owns the risk. Gated behind a Settings toggle, default OFF. | DECIDED (Federico) |
| 2026-06-23 | **Verified-done built BEFORE autonomous mode** — a false "done" with no human confirm has no backstop, so honest completion must ship first. | DECIDED (Federico) |
| 2026-06-23 | Verified-done fails OPEN (accepts) on verifier error/unparseable, and honestly ABORTS after 2 consecutive unverifiable "done" claims (never records unconfirmed success). | DECIDED (this plan) |
| open | Does verified-done need a second opinion model or is self-verify enough? | Revisit with Phase 1 data |
| open | Playbook intent-matching: exact-normalized vs embedding similarity | Start exact; revisit Phase 3 |
| open | Location as a Phase 4+ signal (geofenced suggestions) | Parked — creepiness budget first |

---

## 9. Working agreements

- **Branch:** all work on `claude/amazing-cannon-1vek67`; push after every green build;
  no PR unless asked. **Dev loop:** `DEVELOPING.md` (pull → installDebug → logcat).
- **Definition of done for any activity:** unit tests green + lint clean + APK builds +
  relevant `T-*` script passes on device + checkbox flipped here (+ §2 updated).
- **Design:** read `DESIGN.md` before any UI change; new components get a design pass.
- **Safety:** any change touching §4 invariants calls them out explicitly in the commit.
- **Honesty:** traces are the ground truth. If a trace contradicts the model's summary,
  the trace wins, and the discrepancy becomes a defect row in §2.
