# Tiny Tunnels TODO

One line per item; details are in `docs/todo/`. Items outside the phase plan (`docs/plans/tiny-tunnels-implementation.md`).

- Limit nesting, maybe with a setting?
- Spawning on blocks, how to safely spawin inside a machine without getting stuck?
    - Save position per player on exiting?
- How to pick up rooms in creative, how to delete, how to duplicate?
- I still think machine needs letters outside before tunnels are added inside, it makes it hard to know what's what
- On resorce reload error on 1.21 

## Open

| Item | Size | Details |
|---|---|---|
| Room names (anvil, command, tooltip) | M | [room-names](todo/room-names.md) |
| Debug config flag, split admin vs debug commands, remove `TT-DEBUG` logs | S | [debug-output](todo/debug-output.md) |
| Catch players and items falling out of rooms | S | [void-safety](todo/void-safety.md) |
| **Pinned:** tunnel controls. Who rotates, configures and removes: current setup vs. a wrench screen (B + E) and others | M | [tunnel-controls](todo/tunnel-controls.md) |

## Backlog (1.21.1 backport, to prioritize)

- Test tracking: a catalog of manual tests with IDs, per-version results files, a status script, and more GameTests over time (`plans/tiny-tunnels-test-tracking.md`).
- Buffered tunnels: port to `main` (BT3, `plans/tiny-tunnels-buffered-tunnels.md`), queued in `docs/patches/to-main.md`.
- In-game pass on 1.21.1 (`plans/tiny-tunnels-1-21-1-testing.md`), especially 6a-inside (Create factories) and the mod matrix.
- CI: add a timeout to the GameTest step in `.github/workflows/build.yml`, so a rare 1.21.1 shutdown hang fails fast.
- Flaky GameTest `redstone_item_and_ports`: fails about 1 in 10 runs with a null `actual`, likely a timing race.
- Debug cleanup after testing: remove `loading/ShutdownWatchdog` and the `[TT-DEBUG]` logs in `MachineCore` and `RoomTickets` (goes with the debug-output item above).
- Apply `docs/patches/to-main.md` on `main`: the cauldron GameTests (redo with transactions) and the `MachineSize` codec (copy). Then `scripts/sync-docs.sh`.
- **B8 in progress (started 2026-09-29, ahead of B7):** kinetic tunnel with Create 6.0.8 (`plans/tiny-tunnels-kinetic-tunnel.md`). K0–K1e built, GameTests green (60 with Create, 48 with `-PnoCreate`). K2 in-game pass done 2026-09-30. Next: the paused Create checklist sections, and the core/API/addon split (`plans/tiny-tunnels-api-and-addons.md`), before the first release. The Create sections of the in-game checklist (6a, 6a-inside) are paused until K2.

## Planned phases, for context

From `docs/plans/tiny-tunnels-implementation.md`:
- **7** ([plan](plans/tiny-tunnels-phase-7.md)): redstone tunnel, Jade tooltip, recipes and progression. Implemented; in-game pass pending ([testing](plans/tiny-tunnels-phase-7-testing.md)).
- **8+:**
  - typed or multiple tunnels per face
  - Mekanism chemicals
  - 2×2 rooms
  - living miniature
- **Post-MVP:** 1.21.1 backport.

## Done

- 2026-09-28 (`mc1.21.1/dev`): buffered tunnel mode (empty-hand click: pass-through, buffered in, buffered out), so pipe → tunnel → pipe works for items and fluids. Buckets fill and empty the buffer; sneak + wrench twice discards leftover fluid ([plan](plans/tiny-tunnels-buffered-tunnels.md)). Tested in game with Pipez fluid and item pipes, in and out.
- 2026-09-26: Entering a room starts where you last left it, falling back to a clear spot, so you don't get stuck in blocks ([room-entry-point](todo/room-entry-point.md)). GameTests pass; manual check pending.
- 2026-09-26: Room and tunnel walls unbreakable for everyone ([unbreakable-walls](todo/unbreakable-walls.md)).
- 2026-09-26: Letters on machine faces that have tunnels ([machine-face-labels](todo/machine-face-labels.md)). Tested.
- 2026-09-26: Pipez extract flange across tunnels, fixed with client-side empty handlers ([pipe-visuals-across-tunnels](todo/pipe-visuals-across-tunnels.md)). Tested.
- 2026-09-26: Tunnel Wrench for cycling and removal, plus any `c:tools/wrench` ([tunnel-removal](todo/tunnel-removal.md)). Tested.
