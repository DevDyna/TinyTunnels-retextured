# Tiny Tunnels Test Tracking Plan

**Status (2026-09-28):** planned. Nothing is implemented yet.

## The problem

Manual tests live in per-phase checklists (`docs/plans/*-testing.md`), about 220 checkbox steps in total, and the boxes are ticked in place. That has three problems:

1. **Tests and results live in the same file.** Ticking edits the plan itself, re-running means un-ticking by hand, and there's no record of when something passed or on which build.
2. **Docs are synced across branches** (`scripts/sync-docs.sh`). Ticks made on `mc1.21.1/dev` would appear on `main`, where those tests weren't run. A box can't say which version it passed on.
3. **The same checks are copied across files.** Phase 1–5, 6a, 7 and the 1.21.1 checklist overlap, and the copies drift apart (the "pipe → tunnel → pipe needs two extracts" note did).

## Goals

- One definition of each manual test, with a stable ID.
- Results kept separately, **per Minecraft version**, with a date, a commit and notes, so history lives in git.
- "What's left to test on 1.21.1?" answered by one command.
- Over time, move every check that's really a count or a rule into a GameTest, and keep manual tests for visuals, feel and other mods (option D).

## Layout

```
docs/tests/
  README.md              how to run a session, how to record results, the ID scheme
  catalog.md             every manual test: ID, versions, steps, expected
  results-mc1.21.1.md    results table for 1.21.1
  results-mc26.1.2.md    results table for 26.1.2
  archive/               the old per-phase checklists, moved here unchanged (history)
scripts/
  test-status.sh         summary per version: untested, failing, passing, automated
```

## Test IDs

`AREA-TOPIC-NN`, for example `TUN-BUF-09`. IDs never change or get reused. A test that's replaced is marked `retired` in the catalog, not deleted.

| Area | Covers |
|---|---|
| `BASE` | launch, items, names, recipes, JEI |
| `ROOM` | machines, rooms, the dimension, Shrinker, entry and exit, beds, breaking machines while inside |
| `LOAD` | follow-the-host loading, spawn chunks, occupancy |
| `TUN` | tunnels: placement, wrench, pass-through (`TUN-PT-*`), buffered (`TUN-BUF-*`), shell protection |
| `RS` | redstone tunnels |
| `JADE` | tooltips |
| `MOD` | mod matrix: `MOD-CREATE-*`, `MOD-CREATEIN-*` (Create inside rooms), `MOD-MEK-*`, `MOD-AE2-*`, `MOD-PIPEZ-*`, `MOD-DRAWERS-*`, `MOD-CM-*` |
| `SAVE` | save and reload |
| `KIN` | kinetic tunnel (B8, later) |

## Catalog format (`docs/tests/catalog.md`)

Each test is a heading plus a fixed set of fields, with no checkboxes:

```markdown
### TUN-BUF-09: Fluids, pipe → tunnel → pipe, buffered in
- **Versions:** all
- **Automated by:** buffered_fluid_in (partly)
- **Setup:** Outside: Creative Fluid Tank (water) → Pipez Fluid Pipe extracting at the tank → machine face.
  Inside: wall (Buffered in) → Pipez Fluid Pipe extracting at the wall → Fluid Tank (Small).
- **Expect:**
  - Water fills the small tank.
  - Jade on the wall shows "Buffered in: … mB Water".
```

- **Versions:** `all`, or a list (`mc1.21.1`) for version-only behaviour like beds and spawn chunks.
- **Automated by:** GameTest names that cover it: `—` (none), `partly`, or `fully`. A `fully` automated test is still listed, but the status script counts it as covered by the GameTests and doesn't ask for a manual run.
- **Commands** stay one per code block, as in the current checklists.
- Tests are grouped under `##` area headings, in play order, so a session can still walk it top to bottom.

## Results format (`docs/tests/results-<version>.md`)

One table per version. A line is added (or updated) per test run:

```markdown
| ID | Status | Date | Commit | Notes |
|---|---|---|---|---|
| TUN-BUF-09 | pass | 2026-09-28 | 2a2200c | Pipez fluid pipes, both directions |
| MOD-MEK-02 | fail | 2026-09-29 | 4f1c0de | transporter pulls nothing; see todo |
```

- **Status:** `pass`, `fail`, `skip` (can't run, with the reason in Notes) or `n/a`.
- **Only the latest line per ID counts.** Older lines stay as history, or git keeps it.
- **Commit:** short hash from `git rev-parse --short HEAD` at test time. If the tree was dirty, add `+dirty`.
- A `fail` gets a todo or backlog entry, linked in Notes.

## Status script (`scripts/test-status.sh <version>`)

- Reads the catalog (the IDs, which versions each applies to, and the "Automated by" field) and that version's results file.
- Prints counts and lists:
  - **untested:** applies to this version, no result yet, and not fully automated
  - **failing:** the latest result is `fail`
  - **stale** (optional flag `--since <commit>`): passed at a commit before a given one, for re-testing after big changes
  - **passing / automated:** counts only
- `--markdown` prints a short summary block to paste into a handoff doc.
- It's a Python script called from the shell wrapper (Python 3 ships with macOS). There are no dependencies.

## Branches and syncing

- `results-mc1.21.1.md` is edited on `mc1.21.1/dev`, and `results-mc26.1.2.md` on `main`. The catalog can be edited on either.
- **Change `scripts/sync-docs.sh`:** when syncing onto a branch, never overwrite **that branch's own** results file. It still brings over the other branch's results file and everything else. That fixes the "ticks leak across branches" problem, and each version's results stay with the branch that tests it.
- Catalog edits still follow the "edit docs on one branch at a time" rule.

## Migration

1. **Scaffold:** `docs/tests/README.md`, an empty `catalog.md`, both results files, and `scripts/test-status.sh`.
2. **Build the catalog** from the current checklists, in order: 1.21.1 (it's the most complete and current), then anything only in phases 1–5, 6a and 7. Merge duplicates into one test, drop tests that no longer apply, and mark version-only tests.
3. **Seed `results-mc1.21.1.md`** from the ticked boxes in `tiny-tunnels-1-21-1-testing.md`, including the finished 3b. Use date 2026-09-28 and commit `2a2200c`, with the note "migrated from the checklist".
4. **Seed `results-mc26.1.2.md`** from the ticked boxes in the phase checklists, with their dates where known, otherwise "before 2026-09-28". The commit is unknown, so write `migrated`.
5. **Archive** the old checklists into `docs/tests/archive/` unchanged. Update links in plans, the handoff docs and the todo.
6. **Update `sync-docs.sh`** (see above).
7. **Update the working agreements** (in the handoffs): new tests go into the catalog, and results go into the results file for the current version.

## Option D: automating over time

This is ongoing, not a phase. Pick candidates when a test is run by hand and turns out to be a count or a rule:

- **Good candidates:**
  - item and fluid counts through tunnels (partly done)
  - wrench cycle and remove
  - buffered mode rules (done: the `buffered_*` tests)
  - the bed refusal (done)
  - shell repair
  - entry points
  - machine broken while inside (the host is cleared and the return point still works)
  - the duplicate-room refusal
- **Keep manual:**
  - visuals: textures, marks, sounds, Jade layout
  - how clicks feel
  - anything needing other mods' blocks in a real world: the mod matrix. GameTests only use vanilla blocks; the one exception was a temporary Pipez reflection test.
  - save and reload across a real restart
  - loading at a distance
- When a GameTest covers a test, set its catalog **Automated by** field. Once it's `fully`, the status script stops asking for manual runs of that test on every version the GameTest runs on.

## Effort

| Step | Size |
|---|---|
| Scaffold and status script | Small |
| Catalog migration (about 220 steps into maybe 120 tests after de-duplication) | Medium, mostly careful merging |
| Seeding results and archiving | Small |
| `sync-docs.sh` change | Small |
