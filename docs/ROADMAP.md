# Roadmap — from launcher to flashable OS

The strategy: ship a **user-testable agentic experience as an installable app
immediately**, while building the **AOSP ROM track** in parallel. Each phase is
independently valuable.

---

## Phase 0 — Foundation ✅ (this commit)
- Repo, architecture, vision, build docs.
- Flagship `agentic-shell` scaffolding: launcher Activity (HOME intent),
  Claude provider layer (API · Mock · Subscription stub), Agent Runtime +
  service, capability bus skeleton, conversational home UI.
- Honest scope locked: AOSP-derived, Android-app-compatible, no iOS apps.

## Phase 1 — The agentic launcher (installable, no root)
**Goal: a phone you can actually use the AMOS way, today.**
- Conversational home that routes intent → capabilities (open app, search,
  settings toggles, call/message/maps via intents).
- Real Anthropic API tool-use loop wired to the Capability Bus.
- Agent tray: see running agents, their last action, pause/stop.
- Built-in capabilities: launch app, web/app search, place call, compose
  message, navigate, create calendar event, set alarm/timer.
- Trust Center v1: per-action log, undo where possible, permission prompts.
- 2–3 sample background agents (e.g. "morning brief", "package watcher").

**Exit:** install the APK on any Android phone, set as Home, run real tasks.

## Phase 2 — App ecosystem & capability SDK
- Capability manifest spec so **third-party apps publish tools** to the agent.
- MCP bridge: expose device capabilities as MCP servers; consume remote MCP.
- Built-in apps (Notes, Messages surface, Settings) that are agent-native.
- On-device context store (permissioned) for personalization.

## Phase 3 — System integration (rooted / privileged build)
- Convert the launcher into a **privileged system app** (`/system/priv-app`).
- Promote the Agent Runtime to a real **system service** with the privileges to
  act across the device (accessibility-grade control, default-app authority).
- Deep hooks: notifications, quick settings, assistant role, lockscreen agent.

## Phase 4 — The ROM (`platform/`)
**Goal: a flashable AMOS build that replaces stock Android.**
- AOSP manifest overlay that bakes AMOS system apps + services into a build.
- Target a well-supported device first (Pixel — best AOSP support) + emulator.
- Play-app compatibility via **microG** (degoogled) or certified GMS path.
- Reproducible builds, OTA update channel, signing/relock guidance.
- Hardening pass (SELinux policy, verified boot) informed by GrapheneOS prior art.

## Phase 5 — Distribution & trust
- Beta device program, documented flashing, recovery story.
- Independent security review of the agent privilege model.
- Developer program for capability/agent authors.

---

## Subscription connectivity track (parallel, research)
The `SubscriptionProvider` seam exists so AMOS can use a **Claude.ai
subscription** the moment a supported interface is available. Today there is no
sanctioned programmatic path for a third-party OS to drive a consumer
subscription; we will not ship a fragile or ToS-violating hack. The API
provider is the supported path; the subscription provider stays a documented
stub until reality changes.

## Reality checkpoints
- **Device matters.** AOSP work targets devices with good support (Pixels);
  arbitrary phones have closed vendor blobs.
- **Play certification** is a real gate; microG is the pragmatic default.
- **Agent privilege = attack surface.** Phase 3+ requires the Trust Center and a
  security review to be credible, not optional.
