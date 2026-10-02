# Tiny Tunnels Room Duplicator

Duplicating a room: a new room with a copy of an existing one, bound to a new machine. Phase M4 of `tiny-tunnels-machine-management.md`, split out because it's a design of its own. Lo-fi mockups of the options: `tiny-tunnels-room-duplicator-mockups.html`.

**Status (2026-10-02):** design. **D0 done** (gesture option 2, GameTests green, checked in game 2026-10-02). D0 is what the first release ships; **D1–D3 are after the first release** (user, 2026-10-02). **Chosen: ship C first, add A later** (user, 2026-10-02); both use the same blueprint and the same item list.

## Decisions so far

| Question | Decision |
|---|---|
| Does duplication exist? | **Yes** (user, 2026-10-02). |
| Cost in survival | **All the items the copy needs** (user, 2026-10-02). The hard part is showing the player what that is, in game. |
| Linked instances (edits shared between copies) | Left out for now; see the machine management plan. A copy is independent. |
| Which option | **C first, then A** (user, 2026-10-02). C (the wrench and your inventory) ships first; A (the Duplicator block with a checklist screen) comes later on the same blueprint and item list. B is dropped. |

## What gets copied

A proposal, to confirm:
- **Blocks:** every block inside the room, with its state (facing, slabs, stairs, and so on).
- **Containers:** copied **empty**. You pay for the chest, not for what's in it.
- **Other block entity data:** settings that aren't items are kept (sign text, a Create motor's speed); anything that holds items or fluids starts empty.
- **Tunnels:** copied, costing a tunnel item each (a Tunnel, a Redstone Tunnel, a Kinetic Tunnel). Same faces, same modes.
- **Entities** (mobs, item frames, armour stands): not copied at first.
- **Blocks you can't get in survival** (bedrock, spawners, command blocks, reinforced deepslate): the copy is refused, with a list of which ones. Creative copies everything.

## D0: ship today ✅ (done 2026-10-02)

The least work that still makes a survival copy cost something. It skips the blueprint and any new block or item, and builds on what delete (M3) already does: **a copy costs what deleting the original would give back**, minus container contents.

**Gesture (option 2, user, 2026-10-02):** hold an **empty machine of the same size** and **sneak + right-click** the machine to copy. One of the held empty machines becomes bound to a new room holding the copy; place it anywhere. The wrench isn't involved, so its actions (break, turn, delete) are unchanged. The only thing given up: sneak-placing an empty machine flush against another machine (plain right-click still places). The empty machine's tooltip says "Sneak + right-click a machine of this size to copy its room".

Gestures considered (2026-10-02): (1) wrench + empty machine in the off hand, which overloads the wrench's turn; (2) chosen; (3) plain right-click with an empty machine, which breaks normal building next to machines; (4) a command, operators only; (5) a crafting recipe, which can't take a varying cost or show what's missing.

**What's copied:**
- Every block inside, with its exact state (facing, slabs, door halves), placed without neighbour updates, so the copy matches the original.
- **No block entity data:** chests come out empty, signs blank, a Create motor at its default speed. Copying that data safely means knowing which part holds items in every mod; copying it all would let a modded inventory be duplicated.
- Tunnels: the same kind, wall, side and mode, with empty buffers.
- **Not copied:** entities (item frames, armour stands, mobs) and machines nested inside (left as air, and listed).

**Cost in survival:** each block's silk touch drop (as for delete), plus one tunnel item per tunnel. It's taken from your inventory only if everything is there; otherwise nothing is taken and chat lists what's missing ("Missing: 12 Oak Planks, 3 Glass, … and 4 more"). Upper door halves, bed heads and tall plant tops are free (the other half paid). **Refused** (with the block named): unbreakable blocks (bedrock, command blocks, barriers), and blocks that drop nothing even with silk touch but have an item (spawners, budding amethyst). **Creative:** free, everything is copied.

**Code:** `machine/RoomCopy` (the click, the plan of blocks and cost, paying, building), sharing `RoomDeletion`'s silk touch tool; `RoomData.allocate` + `RoomBuilder.buildNew` for the new room; tunnels through the new `TunnelChanges.copyInto` (same face and wall, data through `movedData`, a placement's follow-ups and `TunnelEvent.Added`). Fluid sources cost a bucket of them; waterlogging is free. Paying takes the plain item or an exact match, never a named item or a filled shulker box.

**GameTests:** `copy_survival` (same blocks and states, empty chest, same tunnel, the original keeps its items, exactly the cost leaves the inventory, one of two held machines is bound), `copy_missing` (nothing taken, no room made), `copy_spawner_refused`, `copy_creative` (free, spawner copied), `copy_different_size`.

**In-game checklist (D0)** — done 2026-10-02:
- [x] Survival: a room with blocks, stairs, a door, a bed, a chest with items, a sign with text, and a tunnel. Hold an empty machine of the same size; its tooltip shows the copy hint. Sneak + right-click the machine with too few items: chat says "Missing to copy this room: …" and nothing is taken.
- [x] With everything: "Room copied into the empty machine"; the items are gone from the inventory; a bound machine is in your hand (or inventory if you held several).
- [x] Place it and go in: same blocks and facings, door and bed whole, the chest empty, the sign blank, the tunnel on the same side and working. The original is unchanged.
- [x] A spawner in the room: "This room can't be copied in survival: Spawner".
- [x] A machine inside the room: the copy says the machines inside weren't copied, and that spot is empty.
- [x] An empty machine of another size: "This empty machine isn't the same size…".
- [x] Creative: copies for free, a spawner too.
- [x] Sneak + right-click a machine with a bound machine or another block: places as before.

**Later, unchanged:** the blueprint and the item list (D1), the have/missing tooltip (D2), the Duplicator block (D3). D0's cost list is the same list D1 would make.

## Options

All three start from a **Room Blueprint**: use the wrench on a machine while holding paper (exact gesture to decide), and you get a blueprint item of that room. It records the room's blocks at that moment; the room itself isn't touched. The options differ in how the copy is paid for and built.

### A. Duplicator block with a checklist screen

A new block. Put the blueprint and an empty machine of the same size in its slots. The screen lists every item the copy needs: needed, have, missing (red). It fills from items put in directly and from containers next to it (chests, hoppers, pipes, our tunnels). A "Build" button is enabled when nothing is missing; the result is a bound machine in the output slot.

- **For:** the clearest; the list is always in front of you; works with automation.
- **Against:** the most work (a block, a screen, a menu, syncing the list); one more block to craft.
- **Like:** Create's Schematicannon with its material checklist.

### B. Duplicator block, no screen

The same block, but no screen. Right-click it with the blueprint and an empty machine to load them; it pulls items from chests next to it. What's missing shows in Jade and the goggles-style tooltip ("Missing: 12 Oak Planks, 3 Glass..."), and in a chat message when you right-click. Builds automatically when complete.

- **For:** much less work than A; still automatic.
- **Against:** a long missing list is hard to read in a tooltip or chat.

### C. No new block: the wrench and your inventory

Use the wrench on an empty machine while holding the blueprint in the other hand. If your inventory (and maybe a backpack-style container you hold) has every item, they're taken and the machine is bound to the new copy. If not, the blueprint's tooltip shows the full list with have/missing from your inventory, and a chat message lists what's missing.

- **For:** the least work; nothing new to craft.
- **Against:** big rooms need far more than an inventory holds; no automation; the list lives in a tooltip.

### Creative

In every option: in creative, copying needs no materials. The wrench gesture can copy straight into a new machine (no blueprint needed).

## Phases

| Phase | Work | Done when |
|---|---|---|
| **D1** | **Room Blueprint and the item list.** The blueprint item, the gesture that makes it, and the list of items a copy needs (what's copied, containers empty, tunnels as items, unobtainable blocks). Shared by C and A. | A blueprint records a room; its tooltip shows the size and block count; GameTests for the list. |
| **D2** | **Option C: copy with the wrench.** The blueprint's tooltip shows have / need from your inventory; wrenching an empty machine with the blueprint in the other hand takes the items and binds the machine to a new copy; a chat message lists what's missing. Creative needs no items. | A copy works on its own and the original is unchanged; in-game checklist. |
| **D3** | **Option A: the Duplicator block** with the checklist screen, fed from its slots and nearby containers. Later. | Built on D1's list; in-game checklist. |

## Open questions

- **Blueprint gesture:** wrench + paper in the other hand? A dedicated item ("Room Scanner")?
- **Tunnels:** copy them (and charge the items), or copy the room without tunnels?
- **Unobtainable blocks:** refuse the whole copy, or copy the rest and leave air?
- **Size:** copy only into a machine of the same size, or allow a bigger one (the copy placed in the middle)?
- **Blueprint updates:** a blueprint is a snapshot; if the room changes, make a new one. Fine?
