# Tiny Tunnels Machine Management

What a player can do with a machine block itself: pick it up with its room, delete a room, duplicate one, rotate one. The gameplay inside and through machines works; managing the machine block doesn't yet. This comes before publishing (A8 in `tiny-tunnels-api-and-addons.md`).

**Status (2026-10-02):** controls decided. M0, **M2, M3 and M5 done** (GameTests green, checked in game 2026-10-02). M4's first step (duplicator D0) is done too (checked in game 2026-10-02); the rest of M4 (blueprint, Duplicator block) and the orphaned-rooms question are **after the first release** (user, 2026-10-02). Machine management is complete for the first release. Decisions so far are in "Decisions"; open questions at the end.

## Today

| Action | What happens now | Problem |
|---|---|---|
| Break in survival | Drops the machine item bound to its room (the loot table copies `tinytunnels:room_id`). | Works: one item per break. The pile-up the user saw earlier didn't happen again in a fresh check (2026-10-02); the extra copies most likely came from creative pick-block or `debug give`. Bound items with the same room are identical, so they stack. |
| Break in creative | Drops nothing (vanilla: no drops in creative). | The room is left with no machine and no item: unreachable, short of `/tinytunnels debug give`. |
| Pick-block in creative (Ctrl + middle-click) | Likely copies the block entity's room id into a new item. | A second item bound to the same room: a duplicate machine. |
| Place a second machine of the same room | Core treats it as stale (`hostedRoom()` is empty): it shows nothing and links nothing. | Allowed, but useless and confusing. |
| Delete a room | No way. | Rooms only accumulate. |
| Duplicate a room | No way. | Wanted. |
| Rotate | A machine's faces are world directions (D, U, N, S, W, E). | No way to turn a machine so its tunnels face another way. |

## Decisions

| Question | Decision |
|---|---|
| Duplicating a room | **Yes, it exists** (user, 2026-10-02). In survival it costs all the items the copy needs. Design: `tiny-tunnels-room-duplicator.md`. |
| One machine per room | A room has exactly one machine, as now. Bound copies of the same room shouldn't pile up. |
| Linked instances (editing the original updates every copy) | **Left out for now** (user, 2026-10-02). It could work with careful design, for example only allowing an edit while all instances still match. Revisit after M4; M4 needn't prepare for it. |
| Controls | **The Tunnel Wrench for all three** (user, 2026-10-02): right-click a machine rotates it 90° around the vertical axis; left-click (break) with the wrench picks it up with its room, in creative too; sneak + right-click deletes it, after a second sneak + right-click within a few seconds to confirm ("Click again to delete this room and everything in it"). A plain break without the wrench stays vanilla. |
| Delete: the item | The machine item comes back **unbound** (an empty machine of the same size). |
| Delete: the room's contents | **Come back by default** (user, 2026-10-02). Blocks come back as themselves (as if mined with silk touch: stone gives stone, a double slab gives 2 slabs); chests and other containers give their items; tunnels give their tunnel item and their buffered items; machines nested inside give their bound item, so their rooms survive; item frames, armour stands and dropped items give their items. What doesn't fit in the inventory drops at the player's feet. Lost: fluids in tunnel buffers, mobs, and blocks silk touch can't get (spawners, budding amethyst). Creative gives nothing back. |
| Delete: opting out | Server config **`deletingRoomDestroysContents`**, off by default (user, 2026-10-02). On: deleting a room destroys everything inside, nested rooms too; only the unbound machine comes back. A player who wants something out takes it before deleting. The confirm message says which of the two happens. |
| Delete: who | **Anyone who can break the machine**, for now. No owners yet. |

## Phases

| Phase | Work | Done when |
|---|---|---|
| **M0** | ~~Reproduce the survival pile-up.~~ **Done 2026-10-02:** not reproducible; survival break gives one item. | — |
| ~~**M1**~~ | **Dropped (2026-10-02):** not needed. Normal play can't make two items for one room: survival break gives one (M0), and the client never gets a machine's room id, so creative pick-block gives an unbound machine. Only `/tinytunnels debug give` can. Left: check in game once that Ctrl + middle-click on a placed machine in creative gives an unbound machine. | — |
| **M2** ✅ | **Pick up with the wrench.** Breaking a machine with the Tunnel Wrench gives its bound item, in creative too, so a room is never left unreachable. **Built 2026-10-02:** `MachineWrenching` (break event); in creative the bound item goes into the inventory, in survival the loot table drops it as before. GameTests `wrench_creative_pick_up`, `creative_break_without_wrench`. **Checked in game 2026-10-02.** | In creative you can move a machine with its room. |
| **M3** ✅ | **Delete a room.** Sneak + right-click with the wrench, twice to confirm. Removes the room for good: its contents come back (or are destroyed, with the config on), its grid slot is freed, and an unbound machine item of the same size comes back. Players inside are sent out. **Built 2026-10-02:** `RoomDeletion`, the sneak click in `MachineWrenching`, `RoomData.delete` (and `allocate` takes the lowest free slot), `RoomTickets.release`; exiting skips return points inside deleted rooms. A delete is refused ("still loading") until the room's entities are loaded, so none are left behind in a reused slot. GameTests `delete_keeps_contents`, `delete_destroys_contents`. **Checked in game 2026-10-02.** | A deleted room is gone from `RoomData` and the dimension; its slot is reused. |
| **M4** | **Duplicate a room.** Designed separately in `tiny-tunnels-room-duplicator.md`. **D0 done 2026-10-02 (checked in game):** sneak + right-click a machine holding an empty machine copies its room, paid from your inventory (no blueprint yet). Later: the blueprint (D1, D2) and a Duplicator block with a checklist screen (D3). | See that plan. |
| **M5** ✅ | **Rotate a machine.** Right-click with the wrench turns it 90° around the vertical axis; the room's tunnel faces follow (N→E→S→W), and D/U stay. **Built 2026-10-02:** `MachineWrenching.turn`, `TunnelChanges.turn` (all tunnels move at once, unknown kinds too; each gets a move's wall update, `movedData`/`moved` and `TunnelEvent.Moved`), `RoomData.turnFaces`. The tunnels inside the room stay where they are; only the machine side each links to changes. GameTest `wrench_turn`. **Checked in game 2026-10-02.** | Tunnels keep working after a rotation; face letters move. |

Each phase ends with `./gradlew build runGameTestServer` green, and an in-game checklist for the parts players see.

## In-game checklist

**M2** (creative world) — done 2026-10-02:
- [x] Place a machine, go in, put a block inside, come out. Break the machine with the Tunnel Wrench: a bound machine item ("Room …") lands in your inventory.
- [x] Place that item somewhere else and go in: the block you put inside is there.
- [x] Break a machine with an empty hand or another item: nothing drops (vanilla).
- [x] Survival: break a machine with the wrench, and without: one bound item drops either way.
- [x] Left from M1: Ctrl + middle-click on a placed machine gives an unbound machine.

**M3** (survival world, config default) — done 2026-10-02:
- [x] Furnish a room: blocks, a chest with items, an item frame with an item, a tunnel with items in its buffer, and a second machine (with something in its room) inside.
- [x] Sneak + right-click the machine with the wrench: "Sneak + wrench again to empty this room into your inventory and remove it". Wait over 5 seconds and click again: it asks again, nothing is deleted.
- [x] Click twice quickly: "Room removed; its contents are in your inventory". You get the blocks, chest and its items, the frame and its item, the tunnel and its buffered items, the nested machine bound to its room, and an unbound machine. What didn't fit is on the floor at your feet.
- [x] Place the nested machine: its room is intact. Place the unbound machine: a new empty room.
- [x] With a second player inside the room when it's deleted: they're sent back out, not dropped into the void.
- [x] Set `deletingRoomDestroysContents = true` in `tinytunnels-server.toml`: the messages are "Sneak + wrench again to remove this room; its contents will be lost" and "Room removed with its contents", and only the unbound machine comes back.
- [x] Creative: deleting gives nothing back.

**M5** (survival) — done 2026-10-02:
- [x] A machine with an item tunnel on N (pipe or hopper into it), a redstone tunnel on E (lever outside), and one on top. Right-click with the wrench: "Machine turned…"; the letters move N→E, E→S, the top one stays.
- [x] Move the pipe and the lever to the new sides: items and redstone go through again. The old sides do nothing.
- [x] With the Create addon: a kinetic tunnel on a side; after a turn the shaft on the new side turns, the old one stops.
- [x] Four turns: back where it started.

## Open questions

- **After the first release.** **Orphaned rooms** from before M2 (broken in creative): a command to list and recover or delete them?
