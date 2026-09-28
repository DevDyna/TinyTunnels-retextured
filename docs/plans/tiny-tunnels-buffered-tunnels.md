# Tiny Tunnels Buffered Tunnels Plan

**Status (2026-09-28):** planned, blocking the 1.21.1 in-game pass. Nothing is implemented yet.

## The problem

Tunnels are pure pass-through: a request on the machine face goes straight to the block touching the tunnel on the other side, and back. That works when a real container (chest, tank, Battery Box) touches one end. It **fails when both ends are pipes that don't hold anything themselves**.

- **Verified in game on both 26.x and 1.21.1 (2026-09-28):**
  - creative tank → Pipez fluid pipe → tunnel → Pipez fluid pipe → small tank: **no flow**
  - the same with Pipez **energy** pipes and Battery Boxes: **works**
- **Why:** on a pipe side set to extract, Pipez exposes:
  - for energy, a real `PipeEnergyStorage`, which forwards power into its network
  - for fluids and items, a `DummyFluidHandler` / `DummyItemHandler` that holds and accepts nothing
  - So with Pipez fluid or item pipes on both sides, nothing in between can take the fluid or items. The tunnel isn't losing anything; there's nowhere for it to go.
  - This is the same in both pinned jars (Pipez 1.2.31 for 26.1.2 and for 1.21.1).
- **Confirmed with the debug command** `/tinytunnels debug cap`: the machine face returned `EmptyFluidHandler`, because the block inside was a Pipez pipe that exposed no fluid handler on the side facing the wall.
- **Why it matters:** "pipe → tunnel → pipe" is the normal way to build, since a tank or chest won't always fit right against the tunnel. It has to work for any pipe mod.

**Why not always buffer?** Compact Machines 4/5 put a buffer in every tunnel, and it went badly (`docs/research/compact-machines-tunnel-issues.md`):
- Push-only mods never drained the buffer.
- Items sat there or were voided.
- Players couldn't tell what was going on.

The fix here keeps pass-through as the default and makes buffering an explicit, visible, one-way mode, with rules against those failures.

## Decisions

| Question | Decision |
|---|---|
| Default | **Pass-through**, unchanged. Everything that works today keeps working the same way. |
| Buffered mode | Per tunnel: **Pass-through → Buffered in → Buffered out → Pass-through**. |
| Direction | Buffered tunnels are **one-way**, like the redstone tunnel. **In** carries outside → room, **Out** carries room → outside. This avoids ping-pong, and it defines which side fills the buffer and which side drains it. |
| What gets buffered | **Items and fluids.** Energy stays pass-through in every mode, because pipe → tunnel → pipe already works for energy and an energy buffer would only add a place to lose power. |
| Capacity | Config, server side. `bufferItemSlots` (default **1** stack) and `bufferFluidCapacity` (default **8000 mB**, the same as Energized Power's small tank, so basic and upgraded Pipez pipes aren't throttled much). |
| Filling side | The **source side** (the machine face for In, the tunnel wall for Out) exposes the buffer as a real container that **accepts only**. Pipes push into it; extracting from it is refused. |
| Draining side | The **far side** (the tunnel wall for In, the machine face for Out) exposes the buffer as a container that **gives only**. A pipe set to extract there pulls from it. |
| Active push | Every tick the buffer also **pushes forward** into the block on the draining side, if that block accepts items or fluid. A plain chest or tank on the far side then fills without a pipe pulling, which fixes CM's "push-only mods never drained the buffer". Pipes that expose dummies accept nothing from the push, and they pull through their own extraction instead. |
| Changing mode | **Right-click the tunnel wall with an empty hand**, as with the redstone tunnel. Held items still place blocks against it. The action bar names the new mode. **Switching to Pass-through is refused while the buffer isn't empty** ("Empty the tunnel first"). Switching between In and Out is allowed: the contents simply move the other way. |
| Removal | With the wrench (sneak): **items in the buffer drop** at the player, like breaking a chest. **Removal is refused while fluid is in the buffer** ("This tunnel still holds 500 mB of Water"), because fluid can't be dropped. Drain it (or switch the direction and let it flow back), then remove. `/setblock` and other commands bypass this, and their contents are lost, as with any block. |
| Where the buffer lives | In the `TunnelBlockEntity`, in the room, saved with the tunnel. The machine face reaches it through the room data, as it reaches the inside block today. |
| While the room isn't loaded | The machine face answers empty (accepts nothing, gives nothing), as it does for pass-through today. Nothing is lost and no chunk is loaded. |
| Where the mode is stored | In `Room` (next to `tunnels`) for the server logic, and as a `MODE` block state on the tunnel wall for the look and for client-side answers. The client can't see room data, and Pipez's connection check runs on the client. |
| Look | The same letter and colour per face, plus a mark: **In** gets inward chevrons, **Out** gets the redstone tunnel's corner marks. Jade shows the mode and contents, e.g. "Buffered in: 12 Cobblestone, 500 mB Water". |
| Loops | A buffered tunnel ends the pass-through chain: it answers with its own buffer and never forwards a lookup. Its active push goes through `ProxyGuard` like any transfer. One-way flow means a buffer can't feed itself directly. |
| Branch order | **`main` first** (26.x transactions), then port to `mc1.21.1/dev` (the `simulate` flag). The buffer handlers are seam code, in `tunnel/`. |

## Phase BT1: `main` (26.1.2)

### Data

1. `room/TunnelMode` enum: `PASSTHROUGH`, `BUFFERED_IN`, `BUFFERED_OUT`. It has a serialized name, a codec and `next()`.
2. `Room` gains `Map<Direction, TunnelMode> tunnelModes`, stored as the optional codec field `"tunnel_modes"`. The default is empty, meaning every tunnel is pass-through, so existing saves load unchanged. A face's entry is removed when its tunnel is removed.
3. `RoomData.setTunnelMode(id, face, mode)`. Cycling the wrench to a new face carries the mode over, and removing the tunnel clears it.
4. `TunnelWallBlock` gains the block state `MODE` (`EnumProperty<TunnelMode>`), making 108 states. `RoomBuilder` and the wrench keep it in step with `Room`, the same way `FACE` is kept.

### Buffer

5. `tunnel/TunnelBuffer` lives on `TunnelBlockEntity` and holds item slots and one fluid tank.
   - On 26.x it's built on NeoForge's transfer API: `ItemStacksResourceHandler` / `FluidStacksResourceHandler`, which take part in transactions.
   - It's saved with `ValueOutput` / `ValueInput`, and `setChanged()` is called on every change.
6. Two views of the buffer, one for each end: `fillOnly` (insert only, extract returns 0) and `drainOnly` (extract only, insert returns 0). Both go through `ProxyGuard` like the other guarded handlers.
7. **Active push:** `TunnelBlockEntity` gets a server ticker.
   - For a buffered tunnel, each tick it moves as much as the draining-side block accepts: the block against the wall inside for In, the block against the machine face outside for Out.
   - The move is transactional on 26.x (simulate/execute on 1.21.1), with the existing `EndpointCaches`, so there's no lookup cost per tick.
   - It skips the tick when the buffer is empty.

### Capabilities

8. `TunnelCapabilities.fromMachine` / `fromTunnel`: if the face's mode is buffered and the kind is item or fluid, return the tunnel's `fillOnly` or `drainOnly` view, depending on direction and end. Energy and pass-through faces behave exactly as they do now.
   - Machine side: the machine reads the mode from `Room`, then gets the tunnel block entity in the room level, but only if that chunk is loaded. Otherwise it returns the empty handler.
   - Client side: from the `MODE` block state, answer empty-but-present on the right sides, as today.
9. Invalidation: every mode change calls `CapabilityUpdates.roomChanged`, which invalidates both ends and sends neighbour updates, so pipes re-query at once.

### Interaction

10. `TunnelWallBlock.useItemOn` / `useWithoutItem`: an empty hand cycles the mode, with the pass-through-while-not-empty refusal. This reuses `RedstoneTunnels`' click pattern (26.x: `TRY_WITH_EMPTY_HAND`).
11. `TunnelWrenching.remove`: drop the buffered items at the player, and refuse while fluid is present, with the message.
12. Lang:
    - modes: "Pass-through", "Buffered in", "Buffered out"
    - messages: "Tunnel set to %s", "Empty the tunnel first", "This tunnel still holds %s of %s"
    - Jade line: "Buffered %s: %s"
13. Textures: `tunnel_wall_<face>_in.png` and `tunnel_wall_<face>_out.png` (12 new ones), built from the existing letter textures plus the marks. Datagen: the tunnel wall blockstate dispatches on `FACE` and `MODE`.

### Config

14. `Config`:
    - `bufferItemSlots`: 1 to 9, default 1
    - `bufferFluidCapacity`: 1000 to 64000 mB, default 8000
    - Changing either doesn't delete contents. A smaller buffer keeps its existing contents until they drain.

## Phase BT2: tests on `main`

GameTests use vanilla blocks only. A hopper that extracts from a container above it stands in for a "pipe that pulls". The GameTest server builds rooms in the overworld, so cross-dimension access is covered in game.

| # | Test |
|---|---|
| BT-1 | Buffered in, item: a hopper outside pushes into the machine face. Inside there's no container, only a hopper **under a ceiling tunnel** pulling from it, feeding a chest. The items arrive and counts match. This is the "pipe pulls from the tunnel" case. |
| BT-2 | Buffered in, fluid: fill 1000 mB into the machine face. Inside, a tank pulled via the capability (standing in for an extracting pipe) gets 1000 mB. |
| BT-3 | Active push: buffered in with a chest directly inside. Items put into the machine face reach the chest with nothing pulling. |
| BT-4 | Buffer full: with nothing draining, the machine face accepts up to the capacity, then 0. Nothing is lost. |
| BT-5 | Direction: on a buffered-in tunnel, extracting from the machine face gives 0, and inserting into the wall gives 0. Buffered out is the mirror. |
| BT-6 | Mode cycle: pass-through → in → out → pass-through, with an empty hand. Switching to pass-through while not empty is refused, and the mode stays. |
| BT-7 | Removal with items: they drop and the total is conserved. Removal with fluid is refused and the tunnel stays. |
| BT-8 | Energy on a buffered tunnel still passes straight through (a stand-in energy handler on both ends). |
| BT-9 | Transaction (26.x): inserting into the machine face inside a transaction that isn't committed leaves the buffer unchanged. |
| BT-10 | Save format: a room saved without `tunnel_modes` loads with every tunnel pass-through. |

**Manual, in game on `main`:** creative tank → Pipez fluid pipe (extracting at the tank) → machine face (buffered in). Inside, the wall → a Pipez fluid pipe **extracting at the wall** → the small tank. Water flows. Repeat with item pipes, then with Out.

## Phase BT3: port to `mc1.21.1/dev`

- The buffer is built on `ItemStackHandler` / `FluidTank`. The two views pass `simulate` / `FluidAction` through unchanged. The active push uses `ItemHandlerHelper.insertItem` / `FluidUtil.tryFluidTransfer`.
- Save and load through `CompoundTag` (`ItemStackHandler.serializeNBT`, `FluidTank.writeToNBT`).
- `useItemOn` returns `ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION` for an empty hand. Removal happens in `onRemove`.
- Port BT-1 to BT-8 and BT-10. BT-9 becomes "a `simulate=true` insert leaves the buffer unchanged".
- Add to `docs/patches/` as a by-hand ("redo") entry.

## Docs to update when done

- **Both checklists:** fix the "pipe → tunnel → pipe needs two extracts" note.
  - Pass-through + pipes works for **energy** only.
  - Items and fluids need **buffered** mode, with the far pipe set to extract.
  - Update 1.21.1 step 3.4 and 6d, and 26.x section 4.
- `docs/research/compact-machines-tunnel-issues.md`: note that buffered mode is opt-in and one-way, with active push and removal rules, and why that avoids CM's buffer failures.
- Remove the temporary `/tinytunnels debug cap` command once this is tested (debug-cleanup backlog item), or keep it behind the planned debug config flag.
