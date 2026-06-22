# Architecture

AMOS is an AOSP-derived operating system whose differentiator is a
**system-level agentic layer**. This document describes the layers from the
silicon up and how the agentic pieces fit in.

## Layered overview

```
┌─────────────────────────────────────────────────────────────────────┐
│  AGENTIC EXPERIENCE LAYER          ← what makes AMOS, AMOS            │
│                                                                       │
│   Agentic Shell (launcher/home)   Conversational, intent-first home   │
│   Agent Runtime (system service)  Plans, schedules, runs agents       │
│   Capability Bus                  Apps/system exposed as tools (MCP)   │
│   Claude Provider Layer           API · Subscription · Mock           │
│   Trust & Permission Center       Visible, scoped, revocable agency    │
├─────────────────────────────────────────────────────────────────────┤
│  ANDROID APP RUNTIME (AOSP)                                           │
│   ART, Activity/Window/Package managers, Binder, app sandbox          │
│   Play-app compatibility via microG or certified GMS                  │
├─────────────────────────────────────────────────────────────────────┤
│  ANDROID PLATFORM SERVICES (AOSP)                                     │
│   SystemServer, HALs, SELinux, Treble vendor interface                │
├─────────────────────────────────────────────────────────────────────┤
│  LINUX KERNEL (device/vendor)                                         │
└─────────────────────────────────────────────────────────────────────┘
```

Everything **below** the agentic layer we inherit from AOSP and the device
vendor. Everything **in** the agentic layer is what this repo builds.

## Components in the agentic layer

### 1. Agentic Shell (`system/agentic-shell`)
The **launcher** — i.e. the Home app. In Android, the launcher is just an app
that registers for the `HOME` intent category; replacing it replaces the entire
home experience without touching the rest of the OS. This is why the shell is
both (a) runnable today as a normal installable app and (b) shippable later as a
baked-in system app.

Responsibilities:
- The conversational home surface (primary input = intent).
- Rendering agent activity, results, and the app/capability space.
- Hosting the on-device session with the Claude provider layer.

### 2. Agent Runtime (`agent/`)
A foreground/`system` service that owns **long-lived agents**: registration,
scheduling, execution, lifecycle, and surfacing their state to the UI. An
*agent* is a goal + a policy + a set of capabilities it may use. The runtime is
where "background agents" live (monitors, summarizers, watchers).

```
Agent ──registered in──▶ AgentRuntime ──runs──▶ AgentService (foreground)
  │                                                    │
  │  uses                                              │ surfaces state
  ▼                                                    ▼
Capability Bus  ◀──tool calls──  Claude Provider   Agent Tray (UI)
```

### 3. Capability Bus
The bridge between the model's **tool calls** and real device actions. A
capability is a typed action the agent can invoke: "open app X," "create
calendar event," "send intent," "read notes." Built-in capabilities wrap
Android intents and system functions; third-party apps can publish capabilities
via a small manifest. This maps cleanly onto **tool use / MCP**.

### 4. Claude Provider Layer (`ai/`)
A single interface, `ClaudeProvider`, with swappable implementations:

| Provider | State | Notes |
| --- | --- | --- |
| `AnthropicApiProvider` | ✅ Works | Official Messages API (developer key). Supports tool use → Capability Bus. |
| `MockProvider` | ✅ Works | Deterministic offline responses. Lets the OS boot with zero config. |
| `SubscriptionProvider` | 🚧 Stub | Clean seam for a future Claude.ai consumer-subscription path. No supported programmatic interface exists today; documented and isolated. |

The rest of the system depends only on the `ClaudeProvider` interface, so
swapping/adding providers never touches the shell or runtime. Selection is
runtime config (Settings), defaulting to `Mock` until a key is supplied.

### 5. Trust & Permission Center
Every autonomous action is **attributable, visible, and reversible**. Agents
hold scoped, revocable grants. The UI always answers "what did an agent just
do, and why?" This is a hard product requirement, not a feature flag.

## Data flow: a single request

```
User intent ("text Sam I'm late")
      │
      ▼
Agentic Shell ──▶ Claude Provider ──▶ model plans, emits tool call(s)
      ▲                                        │
      │ result + transcript                    ▼
      └──────────────── Capability Bus ◀── "send_message(to=Sam, body=…)"
                              │
                              ▼
                    Android intent / app action  (with permission)
```

## Why "launcher first" is the right wedge

Replacing the launcher gives ~80% of the *felt* experience of a new OS (it owns
the home screen, the primary input, app launching, and the always-present agent)
while being installable on any Android phone today — no root, no flashing. The
remaining 20% (deep system integration, baked-in services, a flashable ROM) is
the `platform/` track and comes later. We get user-testable product immediately
and a credible path to a full OS in parallel. See [ROADMAP.md](ROADMAP.md).

## Module map

| Path | What it is |
| --- | --- |
| `system/agentic-shell/app/.../ai` | Claude provider layer |
| `system/agentic-shell/app/.../agent` | Agent runtime, service, sample agents |
| `system/agentic-shell/app/.../capability` | Capability bus + built-in capabilities |
| `system/agentic-shell/app/.../ui` | Conversational home, agent tray, theme |
| `system/agentic-shell/app/.../apps` | App/capability model & registry |
| `platform/` | AOSP overlay + ROM manifest (the "real OS" track) |
