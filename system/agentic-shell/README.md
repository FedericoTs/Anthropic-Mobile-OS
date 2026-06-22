# Agentic Shell

The flagship of AMOS: a native Android launcher (the Home app) whose interface
*is* Claude. This is the part you can build and run today; as the project
matures it becomes a baked-in system app in the ROM (see [`../../platform/`](../../platform/)).

It demonstrates all three pillars of the vision in one app:

- **Conversational launcher** — `ui/HomeScreen.kt` + `ui/HomeViewModel.kt`. Claude
  is the home screen; you state intent and it acts.
- **Background agents** — `agent/` (runtime + foreground service + sample agents),
  surfaced in the agent tray.
- **App ecosystem & capabilities** — `apps/` (app model) and `capability/` (the
  Capability Bus that turns the model's tool calls into real Android actions).

The seam to Claude is `ai/ClaudeProvider.kt`, with three implementations:
`AnthropicApiProvider` (real, OkHttp against the Messages API), `MockProvider`
(offline, zero-config), and `SubscriptionProvider` (documented stub).

## Build & run

See [`../../docs/BUILD.md`](../../docs/BUILD.md). TL;DR: open this folder in
Android Studio, optionally add `ANTHROPIC_API_KEY` to `local.properties`, run on
a device/emulator, then set AMOS as your Home app.

> Authored in a cloud container without Android tooling, so it hasn't been
> compiled here — the first local Android Studio sync is the real compile.
> Dependencies are standard and pinned in `gradle/libs.versions.toml`.

## Layout

```
app/src/main/kotlin/os/amos/shell/
├── MainActivity.kt          HOME launcher entry point
├── ShellApplication.kt      seeds the agent runtime
├── ai/                      Claude provider layer (API · Mock · Subscription)
├── agent/                   Agent runtime, foreground service, sample agents
├── capability/              Capability Bus + built-in device capabilities
├── apps/                    Installed-app model & fuzzy resolver
└── ui/                      Conversational home, agent tray, theme
```
