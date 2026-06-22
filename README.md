# Anthropic Mobile OS (codename **AMOS**)

> An experimental, **agentic mobile operating system** in which Claude is not an
> app you open — Claude *is* the system. You talk to your phone, and it acts:
> launching apps, running background agents, managing your data, and completing
> tasks on your behalf.

**Status:** Phase 1 — foundation & flagship agentic shell. Early, in active development.

> ⚠️ This is an independent, experimental project. It is **not** an official
> Anthropic product and is not affiliated with or endorsed by Anthropic.
> "Claude" and "Anthropic" are trademarks of Anthropic. "AMOS" is a working
> codename.

---

## The honest scope (read this first)

Building "a real OS that replaces Android/iOS" means making deliberate choices
about what is actually achievable. We are explicit about it:

| Goal | Verdict | Approach |
| --- | --- | --- |
| Run real **Apple App Store** apps | ❌ Not possible | Apple's licensing/DRM legally and technically prohibits this on non-Apple hardware. No third-party OS does it. We don't pretend otherwise. |
| Run real **Google Play / Android** apps | ✅ Achievable | Android is open source (AOSP). AMOS is an **AOSP-derived OS**, so it runs Android apps natively and ships Play-compatible services via standard routes (e.g. microG or certified GMS). |
| A **real, installable OS** that replaces stock Android | ✅ Achievable (multi-phase) | Fork AOSP, replace the experience layer, build a flashable ROM — the same path GrapheneOS / CalyxOS / /e/OS took. |
| A **from-scratch kernel/OS** | ❌ Out of scope | A multi-year, multi-team effort with no product upside. We stand on the Linux kernel + AOSP, like every viable alternative OS. |
| **Claude as the core agentic layer** | ✅ The whole point | A system-level AI runtime + agentic launcher. This is our differentiator and where most of the work lives. |

In short: **AMOS = AOSP base + a system-level agentic Claude experience layer.**
That is a real operating system you can flash onto a phone, and it is genuinely
novel because the *interface paradigm* is agentic, not app-grid.

## What's in this repo right now

```
.
├── docs/                     Architecture, roadmap, vision, build guide
├── platform/                 The "real OS" path: AOSP overlay & ROM manifest
└── system/agentic-shell/     FLAGSHIP: native Android launcher + agent runtime
                              + Claude provider layer (Kotlin / Jetpack Compose)
```

The `agentic-shell` is the part you can **build and run today** on an Android
device or emulator. It is the home screen of AMOS. As the project matures it
becomes a system app baked into the ROM (`platform/`).

## Quick start

See [`docs/BUILD.md`](docs/BUILD.md). In short:

1. Open `system/agentic-shell/` in Android Studio.
2. Add an Anthropic API key (or run in offline **Mock** mode — no key needed).
3. Run on an emulator or device. Set AMOS as your Home app to feel the OS.

## The three pillars (the MVP shows all of them)

1. **Conversational launcher** — Claude *is* the home screen. "Open my notes,"
   "draft an email to Sam," "what's on my calendar" → the OS routes and acts.
2. **Background agents** — persistent agents that run over time (monitor,
   summarize, notify) via a system Agent Runtime, shown in a live agent tray.
3. **App ecosystem** — the app model + built-in apps, with Claude woven through
   every surface.

## Claude connectivity

AMOS talks to Claude through a pluggable provider layer
([`ai/ClaudeProvider.kt`](system/agentic-shell/app/src/main/kotlin/os/amos/shell/ai/ClaudeProvider.kt)):

- **Anthropic API** — official Messages API with a developer key (works today).
- **Mock** — fully offline, deterministic responses so the OS runs with no key.
- **Subscription** — architected as a first-class provider. There is currently
  **no officially supported way** for a third-party OS to drive a Claude.ai
  consumer subscription programmatically, so this provider is a documented stub
  with a clean seam to fill in if/when such an interface exists.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full picture and
[`docs/ROADMAP.md`](docs/ROADMAP.md) for how we get from here to a flashable ROM.

## License

Apache-2.0 (matching AOSP). See [`LICENSE`](LICENSE).
