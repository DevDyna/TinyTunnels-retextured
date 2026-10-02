# Tunnel Controls (Undecided)

**Status (2026-09-29):** pinned, to revisit after the 1.21.1 in-game pass. Nothing changes until it's decided.

## The question

How should players rotate a tunnel's face, configure it (pass-through / buffered in / buffered out, redstone in / out), and remove it?

**Today:**
- wrench right-click rotates the face
- wrench sneak removes (two clicks if it holds fluid)
- an empty hand toggles the mode (buffered and redstone)
- a bucket on a buffered wall moves 1000 mB

**The concern:** empty-hand clicks happen by accident, and one stray click changes a mode or reroutes pipes.

## Options

| | Setup | For | Against |
|---|---|---|---|
| A | **Current** (see above) | Built and tested; fast | Accidental mode changes; settings split across two "tools" |
| B | Tunnel item rotates; wrench right-click configures; wrench sneak removes; empty hand does nothing | Safe; one job per tool; other mods' wrenches work | Needs a tunnel item to rotate (the user doesn't like this part) |
| C | Wrench right-click rotates, wrench sneak configures; removal by mining | One tool | Removal loses its gesture and clashes with unbreakable walls; easy to mix up |
| D | Empty hand rotates, sneak + empty hand configures; wrench only removes | No tools needed | Most accident-prone |
| E | Wrench opens a **tunnel screen** | Everything visible; direct face choice; easy to extend | Most work; slower for quick changes |
| F | Tunnel item rotates; sneak + empty hand configures; wrench removes | No tool for modes | Crouching still causes accidents; three gestures |
| G | Wrench with its own modes (Rotate / Configure / Remove, switched by sneak + air click) | One tool, no accidents | Extra step; easy to forget the mode |
| H | A separate configurator item | Explicit | One more item for little gain |

## Leading idea: B + E

- **Wrench right-click** opens the tunnel screen. **Wrench sneak** is the quick remove. An **empty hand does nothing**, and the **tunnel item doesn't rotate** anything.
- **Item tunnel screen:**
  - face buttons `D U N S W E`: the current one highlighted, sides used by other tunnels greyed out
  - mode buttons: Pass-through / Buffered in / Buffered out. Pass-through is disabled while the buffer isn't empty.
  - a read-only buffer panel (item stack, fluid gauge) with **Discard fluid**, which asks to confirm
  - **Remove tunnel**, which asks to confirm if it holds fluid
- **Redstone tunnel screen:** face buttons, In / Out, and a signal readout.
- Buckets on the wall keep working.
- **Build:**
  - a vanilla-style menu and screen; buttons use `clickMenuButton`, so there are no custom packets
  - the server checks every click
  - state syncs through the menu's data
  - GameTests drive the rules through `clickMenuButton`
- **Cost:**
  - medium effort
  - removes the tested empty-hand toggles (Phase 7 redstone, buffered 3b), so tests and checklist sections 3 and 4 change
  - the screen is seam code, written separately on 1.21.1 and 26.x
  - later settings (filters, rates, chemical buffers) become more rows on the same screen
