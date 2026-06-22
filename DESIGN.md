# Design System — Anthropic-Mobile-OS

> The one thing to remember: **"I can watch it think, so I trust it."**
> Every choice below exists to make watching an open agent act on your phone feel
> calm and safe, not clinical. Created by /design-consultation (2026-06-22) from the
> locked design direction (`docs/designs/DESIGN-DIRECTION.md`).

## Product Context
- **What this is:** an open, agent-native mobile OS where agents drive apps out of the box; the user watches the agent perceive, plan, and act, and can approve or stop it.
- **Who it's for:** the open-source / power-user / research crowd who want an agent phone they own (model-agnostic, forkable), not a closed assistant.
- **Space:** AI agent OS. Peer/foil: Google Gemini Intelligence (closed). Our axis: open + transparent + warm.
- **Project type:** mobile OS UI (Android app in Milestone 0).

## Aesthetic Direction
- **Direction:** Anthropic warm, editorial-calm.
- **Decoration level:** minimal-intentional (warm paper, no ornament; type does the work).
- **Mood:** unhurried, human, considered. A warm sheet of paper that thinks with you.
- **Reference:** the approved mockups in `docs/designs/mockups/` are the canonical look.

## Typography
- **Display / wordmark / large headers:** **Instrument Serif** — warm editorial serif; gives a tech OS a human face. (RISK: serif in a tech UI, on purpose.)
- **Body + UI:** **Instrument Sans** — humanist warm sans, pairs with Instrument Serif (same foundry). Body never below 16px.
- **Agent thinking + code:** **JetBrains Mono** — reserved ONLY for the agent's raw thinking lines and code. Monospace = "the machine is talking," never decoration.
- **Data / tabular:** Instrument Sans with `font-variant-numeric: tabular-nums`.
- **Loading:** Google Fonts —
  `https://fonts.googleapis.com/css2?family=Instrument+Serif:ital@0;1&family=Instrument+Sans:wght@400;500;600;700&family=JetBrains+Mono:wght@400;500&display=swap`
  (self-host the same files for the shipped OS; no system-font fallback as the primary face).
- **Scale (mobile, px):** caption 13 / body 16 / body-lg 18 / h3 20 / h2 24 / h1 32 / display 40-48 (serif). Line-height: body 1.45, headings 1.2.

## Color
- **Approach:** restrained — one accent (coral), everything else warm neutrals. Color is rare and meaningful.

```css
:root {
  /* Light (canonical) — warm paper */
  --paper:        #F0EEE6; /* app background */
  --surface:      #FFFDF7; /* raised cards, input, confirm card */
  --surface-sunk: #E9E6DC; /* wells, pressed */
  --text:         #1F1F1C; /* primary */
  --muted:        #6B6862; /* secondary / meta (>=4.5:1 on paper) */
  --faint:        #8A877F; /* tertiary, >=3:1 — large text / non-essential only */
  --border:       #DAD6C9;
  --accent:       #D97757; /* Claude coral — the only accent */
  --accent-press: #C45F3F;
  /* Semantic (warm-tuned) */
  --success: #5E8C6A;
  --warning: #C99A3B;
  --error:   #C0573F;
  --info:    #5B7A99;
}
:root[data-theme="dark"] {
  /* Warm-dark — night / OLED; saturation reduced ~10% */
  --paper:        #14130F;
  --surface:      #1E1C17;
  --surface-sunk: #100F0C;
  --text:         #EDEAE0;
  --muted:        #A19D92;
  --faint:        #6F6B61;
  --border:       #2C2A23;
  --accent:       #E08A6B;
  --accent-press: #C9714F;
  --success: #6FA67C;
  --warning: #D8AC55;
  --error:   #D46B52;
  --info:    #6E8FAE;
}
```

- **Dark mode:** redesign surfaces (not invert); coral lightens slightly so it holds on near-black; reduce semantic saturation ~10%.
- **Contrast rule:** body text and any essential label >= 4.5:1; `--faint` is only for large or non-essential text.

## Spacing
- **Base unit:** 4px.
- **Density:** comfortable (generous vertical rhythm; the home breathes).
- **Scale:** `2xs 2 · xs 4 · sm 8 · md 12 · lg 16 · xl 24 · 2xl 32 · 3xl 48 · 4xl 64`.
- **Screen gutter:** 16-20px. **Min touch target:** 48dp (non-negotiable).

## Layout
- **Approach:** mobile-first, single column, grid-disciplined.
- **Grid:** one column; the active narration step is the single visual anchor per screen.
- **Max content width:** device width minus gutters (tablet: cap text column ~640px).
- **Border radius:** `sm 8 · md 12 · lg 16 · full 9999`. Inputs/cards md-lg; pills full.
- **Thumb zone:** primary actions and high-side-effect confirms anchor to the bottom third.

## Motion
- **Approach:** minimal-functional + intentional. Motion clarifies state, never decorates.
- **Easing:** enter `ease-out`, exit `ease-in`, move `ease-in-out`.
- **Duration:** micro 80ms (taps) / short 180ms (state) / medium 300ms (surface in/out) / long 500ms (orchestration).
- **Signatures:** the ACTIVE narration step gets a slow coral live-pulse; done steps settle with a brief coral check; confirm card slides up from the thumb zone.
- **Accessibility:** honor `prefers-reduced-motion` — drop the pulse and slides, keep instant state changes.

## Components (the OS vocabulary)
- **Intent input:** rounded `--surface` field, coral focus ring + coral send arrow. The hero on the idle home.
- **Narration step row:** line glyph (eye = perceive, spark = plan, phone = act) + one plain-text line + status. Done = muted + coral check; ACTIVE = larger, soft coral highlight, `aria-live="polite"` on the active step ONLY (never every step), with a mono thinking line. Each row carries a `task/step/agent` id.
- **Confirm card (the trust gate):** appears inline at the high-side-effect step AND echoes to the bottom thumb zone. Warm caution label, the drafted action text, `Approve` (coral) + `Edit / Skip` (outline). One consolidated card if multiple actions queue.
- **Model-provider pill:** full-radius `--surface` pill, dot + model name + "swap model". Tap = swap (takes effect next task).
- **Stop:** coral square, always reachable while the agent runs.
- **App grid (fallback):** minimal icon; the always-available escape hatch.
- **Cards earn their existence** — no decorative card grids, no icons-in-colored-circles.

## Accessibility (this is an Accessibility-driven OS; its own UI must pass)
- 48dp touch targets; body >= 16px; contrast >= 4.5:1 (essential), >= 3:1 (large).
- `aria-live="polite"` on the active narration step only; full step detail on demand.
- Every interactive element has a visible focus state and a content description.
- Confirm/Stop reachable one-handed (bottom thumb zone).
- The narration feed redacts credentials/secrets at the source (never render an OTP, password field, or token).

## Approved Mockups
| Screen | Path | Milestone |
|--------|------|-----------|
| Idle home (calm) | `docs/designs/mockups/idle-home.png` | M0 |
| Active narration feed | `docs/designs/mockups/active-narration.png` | M0 |
| Predictive home | `docs/designs/mockups/predictive-home.png` | v1 |

## Decisions Log
| Date | Decision | Rationale |
|------|----------|-----------|
| 2026-06-22 | Anthropic warm theme, coral accent | Locked design direction; open + warm vs the closed dark "AI" look |
| 2026-06-22 | Instrument Serif + Instrument Sans + JetBrains Mono | Warm editorial face; serif display is the deliberate human departure; mono reserved for the agent's thinking |
| 2026-06-22 | Memorable thing = "I can watch it think, so I trust it" | Transparency-as-trust; the whole system serves making agent autonomy feel safe |
