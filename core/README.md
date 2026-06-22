# `:core` — the agent brain (Milestone 0)

Pure Kotlin/JVM, **Android-free on purpose** so the agent logic is fast to
unit-test with no device. The Android app supplies the device-facing impls
(`Perceiver`, `Actuator`, real `ModelProvider`) by adapting `AccessibilityNodeInfo`
into these types — the same node shape the T0 spike proved it can read.

## What's here (maps to the Milestone 0 backlog)

| Package | Backlog | What it is |
|---------|---------|------------|
| `perception` | seeds T2 | `ScreenNode` / `Observation` / `NodeFinder` — Android-free perceived tree + search + non-password text extraction |
| `events` | E2-1 | One typed `NarrationEvent` stream + `EventBus`; `AuditLog` keeps everything, `LiveFeed` coalesces + bounds (backpressure); `Correlation` (task/step/agent) on every event |
| `action` | T4 | Fixed typed `AgentAction` schema; `UntrustedObservation` delimited channel; out-of-model `PolicyGate` that forces confirm on high-side-effect actions |
| `model` | T1, E2-2 | `ModelProvider` seam + dual `AuthMode` (OAuth / API key); `ProviderRegistry` swap-at-next-task with non-fatal failure; `ScriptedModelProvider` for tests |
| `loop` | T2, T3 | `AgentLoop`: perceive → plan (screen as untrusted data) → gate → settle → act → narrate; abort + transparent handoff on failure/stale target |

## The safety story (why the gate is outside the model)

The model can only emit one of a fixed set of typed actions, and screen text is
fed in a delimited **untrusted** channel — but the load-bearing defense is the
`PolicyGate`: it runs *after* the model and *cannot be overridden* by anything the
model or the screen says. Every irreversible / money / identity action requires an
explicit confirm regardless of model confidence. So a prompt injection that fools
the planner still cannot auto-execute a high-side-effect action. This is exercised
by `InjectionRedTeamTest`.

## Tests (T6 safety set, run on every build)

`./gradlew :core:test` — 19 tests: event bus durability/backpressure, policy-gate
classification, injection red-team, provider-swap semantics, the agent loop
(happy path, action-failure abort+handoff, stale-tree settling, confirm gate), and
the scripted-intent eval.

## Not here yet (next)

The real Claude `ModelProvider` (network, OkHttp — lives in the Android app, not
core), the device adapters (`AccessibilityNodeInfo` → `ScreenNode`, the
`AccessibilityService` that runs the loop), and the static ambient home UI (T5).
The on-device wiring is gated only by being device/UI work, not by the brain.
