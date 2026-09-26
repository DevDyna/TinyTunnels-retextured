# Tiny Tunnels TODO

One line per item; details are in `docs/todo/`. Items outside the phase plan (`docs/plans/tiny-tunnels-implementation.md`).

## Open

| Item | Size | Details |
|---|---|---|
| Show which machine faces have tunnels (letters on the outside). **Implemented, needs in-game test.** | M | [machine-face-labels](todo/machine-face-labels.md) |
| Pipe extract flange disappears across a tunnel. **Cause found (our client-side capability answer); fixed, needs in-game test.** | S–M | [pipe-visuals-across-tunnels](todo/pipe-visuals-across-tunnels.md) |
| Easier tunnel removal: **Tunnel Wrench implemented (any `c:tools/wrench` works), needs in-game test.** | S | [tunnel-removal](todo/tunnel-removal.md) |
| Room names (anvil, command, tooltip) | M | [room-names](todo/room-names.md) |
| Debug config flag, split admin vs debug commands, remove `TT-DEBUG` logs | S | [debug-output](todo/debug-output.md) |
| Catch players and items falling out of rooms | S | [void-safety](todo/void-safety.md) |

## Planned phases, for context

From `docs/plans/tiny-tunnels-implementation.md`:
- **6b** GameTests for tunnels (next in the plan).
- **7+:**
  - redstone tunnel
  - typed or multiple tunnels per face
  - Mekanism chemicals
  - Jade tooltip
  - recipes and progression
  - 2×2 rooms
  - living miniature
- **Post-MVP:** 1.21.1 backport.

## Done

- 2026-09-26: Room and tunnel walls unbreakable for everyone ([unbreakable-walls](todo/unbreakable-walls.md)).
