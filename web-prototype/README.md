# Discnct — Web Prototype

A single-file, browser-runnable prototype of the Discnct app, built so the UI/UX
and all six mini-games can be clicked through and evaluated **without** an Android
device or emulator (which the CI sandbox can't run — see `docs/emulator-setup.md`).

Open `index.html` in any modern browser. No build step, no network — everything
(including the brand fonts) is embedded in the one file.

## What it faithfully reproduces

- **Design system** — colors (`DiscnctColors`), type scale (`DiscnctType`), shapes
  and the Space Mono / Space Grotesk / Doto fonts are ported straight from the app's
  Kotlin sources. Both the dark ("instrument panel") and light themes are included and
  toggle live.
- **Flows** — onboarding permission steps, the Block List (Level 1 + the Level 3
  restricted-launcher settings), the block screen (Level 2: hold-to-unlock for 30s
  **or** play a game), the restricted launcher grid, and a components gallery.
- **All six mini-games** — Wordle, 2048, Minesweeper, Tic-Tac-Toe, Chess (mate-in-1)
  and Breathing. The game rules and reward tables are a direct port of the pure
  functions in the `game-logic` module — the same logic covered by that module's
  JUnit tests.

## What it is *not*

This is a UX/logic prototype in the browser, not the Android build. Real permission
grants, the accessibility service, DataStore persistence, and actually being set as
the OS Home app only exist in the Android app itself; here those are simulated so the
screens and interactions can be reviewed end to end.
