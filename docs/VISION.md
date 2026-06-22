# Vision — an agentic operating system

## The thesis

For 15 years the mobile metaphor has been a **grid of apps**. You are the
router: you decide which app does what, you switch between them, you copy data
across them by hand. The phone is a pile of tools and *you* are the operating
system.

AMOS inverts this. The phone runs an **agent** that understands intent and
operates the device for you. Apps become **capabilities** the agent can call,
not destinations you navigate. You express a goal; the OS plans and executes.

> "Text Sam that I'm running 10 minutes late, then pull up the directions."
>
> A grid-of-apps phone makes you open Messages, find Sam, type, switch to Maps,
> search. AMOS does it — and tells you what it did.

## Design principles

1. **Intent over navigation.** The primary input is what you *want*, not which
   app to tap. The launcher is a conversation, not a screen of icons.
2. **The agent is the OS, not an app.** It lives at the system layer with the
   privileges to actually act: launch apps, send intents, read/write your data
   (with permission), and run work in the background.
3. **Transparent agency.** Every autonomous action is visible, attributable,
   and reversible. You always see what an agent did and why. Trust is earned
   through legibility, not hidden automation.
4. **Local-first, permissioned.** Your data is yours. Agents get scoped,
   revocable permissions. Sensitive context stays on device wherever possible;
   only what's needed leaves the device, and you can see what does.
5. **Apps are capabilities.** A clean capability model lets the agent compose
   third-party apps and system functions into multi-step tasks.
6. **Honest about limits.** We don't fake capabilities. If something can't be
   done safely or legally, the OS says so.

## Why now

- Frontier models (Claude) can reliably **plan and call tools**, which is the
  primitive an agentic OS needs.
- **Tool use / MCP** give a standard way to expose device + app capabilities to
  the model.
- AOSP makes a real, shippable Android-compatible base available to build on.

## What success looks like

- A person can hand AMOS a multi-step real-world goal and watch it complete
  safely, using the apps they already have.
- Developers can expose an app's capabilities to the agent with a small
  manifest, and it "just works" in the conversational launcher.
- The OS is trustworthy enough that people let it act on their behalf — because
  it is transparent and reversible by design.
