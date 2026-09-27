# Tiny Tunnels TODO

One line per item; details are in `docs/todo/`. Items outside the phase plan (`docs/plans/tiny-tunnels-implementation.md`).

- Limit nesting, maybe with a setting?
- Able to duplicate rooms, say you make a redstone clock you should be able to dupe room
- Spawning on blocks, how to safely spawin inside a machine without getting stuck?
    - Save position per player on exiting?
- How to pick up rooms in creative?
- I still think machine needs letters outside before tunnels are added inside, it makes it hard to know what's what
- On load error on 1.21

## Open

| Item | Size | Details |
|---|---|---|
| Room names (anvil, command, tooltip) | M | [room-names](todo/room-names.md) |
| Debug config flag, split admin vs debug commands, remove `TT-DEBUG` logs | S | [debug-output](todo/debug-output.md) |
| Catch players and items falling out of rooms | S | [void-safety](todo/void-safety.md) |

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

- 2026-09-26: Entering a room starts where you last left it, falling back to a clear spot, so you don't get stuck in blocks ([room-entry-point](todo/room-entry-point.md)). GameTests pass; manual check pending.
- 2026-09-26: Room and tunnel walls unbreakable for everyone ([unbreakable-walls](todo/unbreakable-walls.md)).
- 2026-09-26: Letters on machine faces that have tunnels ([machine-face-labels](todo/machine-face-labels.md)). Tested.
- 2026-09-26: Pipez extract flange across tunnels, fixed with client-side empty handlers ([pipe-visuals-across-tunnels](todo/pipe-visuals-across-tunnels.md)). Tested.
- 2026-09-26: Tunnel Wrench for cycling and removal, plus any `c:tools/wrench` ([tunnel-removal](todo/tunnel-removal.md)). Tested.
