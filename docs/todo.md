# Tiny Tunnels TODO

Open items that sit outside the phase plan (`docs/plans/tiny-tunnels-implementation.md`). Add the date when an item is added or resolved.

- How to know what side is what? A letter on the outside of the block?

## Room walls and the void

### Should creative players be able to break room walls? (added 2026-09-26, **decided 2026-09-26**)

**Decision: room walls and tunnel walls are unbreakable for everyone, in creative and survival.** No gameplay needs a hand-broken wall. Tunnels come out with sneak-use, admins have `/setblock`, and future resizing would rebuild walls in code. This covers **only the room's own shell** (`room_wall`, `tunnel_wall`). Blocks players build inside a room, from vanilla or any mod, behave exactly as they would anywhere else.

Moved into the plan: Phase 6a, task "Unbreakable shell".

### What happens if a player gets outside a room? (added 2026-09-26)

**Today:** the room dimension is empty void from y = 0 to 256, with room floors at y = 64. A player who gets out (a creative-broken wall, an ender pearl through a gap, spectator mode) falls, drops below y = -64, and **dies from void damage**. Anything they drop is lost.

Options:
- **Catch falls:** if a player in the room dimension falls below `FLOOR_Y - 16`, teleport them back to the spawn of the room whose column they're in (`RoomData.byChunk`), or the nearest room. If there's no room, use the normal exit (the return stack).
- **Also catch items:** item entities that fall below the same line get moved back into the nearest room, or deleted, but never silently lost.
- Keep spectators exempt.

With walls unbreakable (decided above), this only matters for edge cases: spectator mode, other mods' teleports, and mods that remove blocks directly without firing break events. The Phase 6a shell self-repair covers the last one.

## Debug output (added 2026-09-26)

Debug-only code exists right now, marked `TODO(debug)`:
- `[TT-DEBUG]` log lines in `MachineBlockEntity` and `RoomTickets`
- the grey load-state line in `/tinytunnels debug tickets`

Regular players and server admins don't need this. Plan:
- Add a `debug` flag to the server config (`tinytunnels-server.toml`, default `false`).
- Keep the `TODO(debug)` logs and detail lines only when `debug = true`, or delete the logs now that follow-the-host is verified.
- Split commands by audience:
  - `/tinytunnels rooms` and `/tinytunnels room <id>`: normal admin info (permission 2).
  - `/tinytunnels give <id>`: recovery for a lost machine (permission 2). Useful beyond debugging.
  - `/tinytunnels debug build …` and the verbose `debug tickets`: only registered when `debug = true`.

## Room names (added 2026-09-26)

Rooms are only known by UUID, which is hard to read and remember.
- Add `Optional<String> name` to `Room`, with a codec field and a `RoomData.rename`.
- **Ways to set it:**
  - **Anvil:** renaming a machine item in an anvil names its room when it's placed. It needs no extra UI and uses the vanilla `CUSTOM_NAME` component.
  - **Command:** `/tinytunnels rename <name>`, run from inside the room or while looking at its machine.
  - **UI (later):** a small screen when you sneak-use the Shrinker on a machine.
- **Where to show it:**
  - the machine item tooltip (instead of "Room xxxxxxxx")
  - the action bar on entering
  - `rooms` / `tickets` output
  - Jade, once it's integrated
- **Permissions:** decide who can rename. Probably anyone who can enter the room; ownership comes later.

## Better way to remove tunnels (added 2026-09-26)

**Today:** sneak + right-click a tunnel with **both hands empty**. It works, but clearing both hands is awkward.

Options:
- **Sneak + right-click with a Tunnel item** in hand removes it. You're likely holding one while arranging tunnels anyway.
- **Sneak + right-click with anything**, handled in `useItemOn` so it runs before the held item's own use. Simplest, but it can clash with items that have their own sneak action.
- **A dedicated tool** (e.g. a "Tunnel Wrench") that removes on sneak-use and cycles on plain use. It would also suit later per-tunnel settings, like typed tunnels or I/O modes.

Leaning towards the first option now, and the wrench once tunnels get settings.

