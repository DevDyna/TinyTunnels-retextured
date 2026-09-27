# Tiny Tunnels Phase 7 Plan

Phase 7 covers three things: the **redstone tunnel**, the **Jade tooltip**, and **recipes and progression**. The rest of the old "7+" list moves to Phase 8+ (see the end of this doc).

**Status (2026-09-26):** 7a, 7b and 7c are implemented, with 11 redstone GameTests (T1–T10 plus the shell-repair regression, `gametest/RedstoneGameTests.java`) passing alongside the 15 existing ones. Next is the in-game pass (`tiny-tunnels-phase-7-testing.md`). Where the code differs from the text below, see "Implementation notes" at the end.

Read `docs/handoffs/phase-7-handoff.md` first, especially the hard-won lessons. Every API below was checked against the 26.1.2.109 sources on 2026-09-26 unless marked **verify**.

## Decisions

| Question | Decision |
|---|---|
| Redstone: a separate item, or a mode on the existing tunnel? | **A separate item.** The Redstone Tunnel takes a machine face of its own, so a face carries either items/fluids/energy or a signal, never both. |
| Direction | One-way per tunnel. **IN** carries the signal from outside into the room; **OUT** carries it from the room to outside. A new tunnel starts as IN. |
| Changing the direction | **Right-click the redstone tunnel with an empty hand**, like a comparator. No settings screen. The wrench works as it does on tunnels: right-click cycles the face, sneak + right-click removes the tunnel. |
| Signal strength | Analog 0–15, carried unchanged. |
| Timing | Each hop takes **one game tick** (a scheduled block tick), like a repeater. That makes loops safe: a loop through tunnels becomes a clock at worst, never recursion. No depth guard is needed, because nothing recurses. |
| Strong or weak output | The emitting end gives **both** on its one side, like a repeater's output. A solid block placed against it gets powered. |
| Where the signal is stored | In `RoomData`, per face: `RedstoneLink(mode, power)`. The reading end writes it, and the emitting end reads it when asked for its signal. It survives either end unloading, and an end that loads emits the stored value straight away. |
| How it's stored in `Room` | A **separate map**, `redstone: Map<Direction, RedstoneTunnel(pos, mode, power)>`, next to `tunnels`. The item, fluid and energy code never sees redstone faces, so it doesn't need to change. A face is free only if it's in neither map. |
| Machine face flags | Replace the six `BooleanProperty` flags with one `EnumProperty<PortKind>` per face: `none`, `tunnel`, `redstone` (3⁶ = 729 states per machine). Redstone faces get a red letter. Old worlds lose the boolean values, and the face sync already rewrites them when the machine loads. |
| Jade | `compileOnly` on the Jade jar that's already in the build. The plugin is found through `@WailaPlugin`, so nothing in our code references Jade and the mod runs without it. |
| Recipes | Each machine size is crafted **from raw materials, never from a smaller machine.** Vanilla shaped recipes ignore components, so upgrading would eat a bound machine and strand its room. |

## 7a: Redstone tunnel

### Data

1. `room/RedstoneMode` enum `IN`, `OUT` (serialized name, codec, `flip()`).
2. `room/RedstoneTunnel(BlockPos pos, RedstoneMode mode, int power)` record plus codec.
3. `Room` gains `Map<Direction, RedstoneTunnel> redstone`, stored as the optional codec field `"redstone"` (default empty, so existing saves still load). Update these:
   - `nextFreeFace` skips faces in either map.
   - `faceAt(pos)` checks both maps. Add a `redstoneFaceAt(pos)` next to it.
   - Add `withRedstone(...)`.
4. `RoomData`: add `setRedstoneTunnel(id, face, pos, mode)`, `removeRedstoneTunnel(id, face)`, `setRedstoneMode(id, face, mode)` (resets power to 0), and `setRedstonePower(id, face, power)`.
   - Each of them calls `setDirty()`, the existing `update(...)` does that.
   - A fast clock marks the data dirty every few ticks. That's fine: the cost is only paid on autosave.

### Blocks and items

5. `tunnel/RedstoneTunnelWallBlock extends RoomWallBlock implements EntityBlock`. It is not a `TunnelWallBlock`, so it gets no capabilities.
   - State: `FACE`, `INWARD` (reuse the properties from `TunnelWallBlock`), `MODE` (in/out), `POWERED`. `MODE` and `POWERED` only affect the look. `RoomData` is authoritative.
   - `isSignalSource`: true. `getSignal` / `getDirectSignal`: the stored power when `MODE == IN` and the query comes from the inward side, else 0. See "Signal direction" below.
   - `canConnectRedstone`: true on the inward side only, so dust inside connects to it. **Verify** the direction argument's meaning in `IBlockExtension`.
   - `neighborChanged(state, level, pos, block, orientation, moved)`: when `MODE == OUT`, `level.scheduleTick(pos, this, 1)`.
   - `tick(...)`: see "Propagation" below.
   - `useWithoutItem`: flips the mode (see step 11).
   - Registered as `redstone_tunnel_wall` in `ModBlocks`, with `wallProperties`. That means it isn't a conductor and is unbreakable.
   - Add it to `ShellProtection` next to `tunnel_wall` (break cancel and next-tick repair), and to the block tags the tunnel wall is in.
6. `tunnel/RedstoneTunnelBlockEntity`: stateless. `onLoad` schedules a tick of its block, so that inside neighbours pick up a value that changed while the room was unloaded. It also fixes `INWARD`, the same way `TunnelBlockEntity.fixInwardState` does.
7. `tunnel/RedstoneTunnelItem`: the same placement rules as `TunnelItem` (room level, shell, no edges, first free face). Move the shared checks into one helper so they don't drift apart. It records the tunnel with `RoomData.setRedstoneTunnel(..., IN)`.
   - Registered as `redstone_tunnel` in `ModItems` and added to the creative tab.
   - Message: "Redstone in from North".
8. `MachineBlock`:
   - `PORTS: Map<Direction, EnumProperty<PortKind>>` replaces `TUNNEL_FACES`.
   - `hasTunnel(state, face)` becomes `== TUNNEL`, so the client-side capability answers stay the same. Add `hasRedstone(state, face)`.
   - `withTunnelFaces` becomes `withPorts(state, room)`, which reads both maps. It's called from `CapabilityUpdates.syncMachineFaces`.
   - `isSignalSource`: true if any port is `REDSTONE`. `getSignal` / `getDirectSignal`: the stored power of an `OUT` face, read through the block entity's room. Return 0 when the level isn't a `ServerLevel` or the machine isn't the room's current host.
   - `canConnectRedstone`: true on `REDSTONE` ports.
   - `neighborChanged`: schedule a tick if any port is `REDSTONE`. `tick`: read the `IN` faces (see "Propagation").
9. `MachineBlockEntity`:
   - `onLoad` and `bindOnPlace` schedule a machine tick. That re-reads the inputs and re-emits the outputs.
   - `preRemoveSideEffects` sets every `IN` face's power to 0 and notifies the tunnel walls, so the inside goes dark when the machine is picked up.

### Signal direction

`SignalGetter.getSignal(pos, direction)` is called by the receiver as `getSignal(receiverPos.relative(d), d)`. So `direction` points **from the receiver towards the emitter** (see `DiodeBlock.getInputSignal`).

- A machine emitting on face F answers when `direction == F.getOpposite()`.
- A wall emitting inward answers when `direction == INWARD.getOpposite()`.

The GameTest face matrix (T1) is what proves this for all six faces.

### Propagation (one tick per hop)

**Reading end.** This is the machine for IN faces, and the wall for an OUT tunnel. On its scheduled tick, for each face it reads:

```java
int power = level.getSignal(pos.relative(side), side);
// Dust only powers what it points at; read its level anyway, as DiodeBlock.getInputSignal does.
if (power < 15 && level.getBlockState(pos.relative(side)).is(Blocks.REDSTONE_WIRE)) power = Math.max(power, wire POWER);
```

If the value differs from the stored one, it calls `RoomData.setRedstonePower` and notifies the emitting end.

**Notifying the emitting end** (`tunnel/RedstoneUpdates`):

- If the far end isn't loaded, do nothing. Never load chunks; the far end catches up on its next load.
- Otherwise:
  - `level.updateNeighborsAt(pos, block, null)`
  - `level.updateNeighborsAtExceptFromFacing(pos.relative(side), block, side.getOpposite(), null)`, for strong power, the same as `DiodeBlock.updateNeighborsInFront`.
  - For a wall, also set `POWERED`, wrapped in `ShellProtection.edit(...)`.

The emitting end does nothing on its own tick apart from this refresh.

**Loops.** An IN face and an OUT face wired to each other, inside and outside, toggle once per hop and nothing more. That's a clock, and it's the player's to build. Two machines feeding each other behave the same way.

### Wrench and mode

10. `TunnelWrenching` also handles `RedstoneTunnelWallBlock`:
    - **Cycle:** moves the link to the next free face and keeps its mode. For IN, it resets power to 0 and schedules a machine tick so the new face gets read. It notifies the machine (both the old face and the new one) and the wall.
    - **Remove:** clears the link, notifies both ends, and gives back a Redstone Tunnel item.
    - `mappedRoom` checks `room.redstone()` for this block.
11. **Toggle** (`useWithoutItem`, server side): flip the mode, reset power to 0, then schedule a tick for the new reading end and notify both ends.
    - Play the comparator click sound.
    - Message: "Redstone in from North" or "Redstone out to North".
    - With the wrench in hand the wrench handler runs first and cancels the event, so there's no conflict.

### Assets and lang

12. Textures (placeholder pixel art, using the same script approach as the existing ones):
    - `redstone_tunnel_wall_<face>` in red. Add a lit `_on` variant for `POWERED`, and draw a small in/out arrow if it's cheap to do.
    - `machine_redstone_port_<face>` overlays: the same letter as the machine port, in red.
    - `redstone_tunnel` item.
13. `ModModelProvider`:
    - The machine multipart becomes `condition(PORTS.get(face), TUNNEL)` → `machine_port_<face>`, and `REDSTONE` → `machine_redstone_port_<face>` (hand-written JSON, like the existing ones).
    - The wall dispatch covers `FACE × MODE × POWERED` and ignores `INWARD`.
14. Lang: block, item, the messages, and the face words that already exist.
15. `debug room` prints the redstone links.

## 7b: Jade tooltip

16. Add `compileOnly "maven.modrinth:jade:5jKeKXwB"` to `build.gradle`, next to the existing `localRuntime` line. Everything stays optional: `neoforge.mods.toml` gets no dependency entry.
17. `compat/jade/TinyTunnelsJadePlugin` (`@WailaPlugin`, `IWailaPlugin`). Nothing else references this package.
    - **Machine** (`IBlockComponentProvider` plus `StreamServerDataProvider<BlockAccessor, MachineInfo>`). The server sends the room size and the list of redstone links (face, mode, power). The client takes the tunnel faces from the block state. Shows:
      - `Room 7×7×7`
      - `North: tunnel`
      - `East: redstone in (7)`
    - **Tunnel wall** (client only, from state): `Machine face: North`.
    - **Redstone tunnel wall** (a server provider sends the power): `Machine face: North`, `Redstone in, power 7`.
    - Provider UIDs are `tinytunnels:machine`, `tinytunnels:tunnel` and `tinytunnels:redstone_tunnel`. Lang keys are `config.jade.plugin_tinytunnels.<uid>`. **Verify** that key format against Jade 26.1.11.
18. Verified API (Jade 26.1.11 jar):
    - `IWailaPlugin.register(IWailaCommonRegistration)` / `registerClient(IWailaClientRegistration)`
    - `registerBlockDataProvider(IServerDataProvider<BlockAccessor>, Class<?>)`
    - `registerBlockComponent(IComponentProvider<BlockAccessor>, Class<? extends Block>)`
    - `IComponentProvider.appendTooltip(ITooltip, T, IPluginConfig)`
    - `StreamServerDataProvider.streamData(T)` / `streamCodec()` / `decodeFromData(T)`
    - `IJadeProvider.getUid()`: **verify** the return type (`Identifier`).

## 7c: Recipes and progression

19. `datagen/ModRecipeProvider` (`RecipeProvider` plus its nested `Runner`, registered in `DataGenerators`). Every recipe gets `unlockedBy`, so it appears in the recipe book. **Verify** how `Runner` is wired up in 26.1.2 (`RecipeProvider.java:864`).
20. Proposed recipes. These are starting values; adjust after playing. (2026-09-27: redstone dust replaced the ender pearl in the machine, Shrinker and Tunnel recipes, so the early game doesn't need Endermen.)

| Item | Recipe |
|---|---|
| Tiny machine (3) | 8 copper ingots around 1 redstone dust |
| Small machine (5) | 8 iron ingots around 1 redstone dust |
| Normal machine (7) | 8 gold ingots around 1 redstone dust |
| Large machine (9) | 8 diamonds around 1 redstone dust |
| Giant machine (11) | 8 emeralds around 1 eye of ender |
| Maximum machine (13) | 4 netherite ingots + 4 obsidian around 1 nether star |
| Shrinker | iron, redstone, glass: a lens on a handle. Stacks to 1. |
| Tunnel ×2 | hopper + bucket + 2 redstone (shaped, with 4 copper) |
| Redstone Tunnel | shapeless: tunnel + comparator |
| Tunnel Wrench | 3 iron ingots + 1 copper ingot in a wrench shape |
| Room wall | no recipe (unobtainable in survival) |

21. After `./gradlew runData`, check the recipe book in survival: every item appears once you hold its unlock item.

## GameTests (add to `TunnelGameTests`, or a new `RedstoneGameTests`)

Rooms fall back to the overworld on the GameTest server (`RoomDimension`). Use the `TestRooms` helpers.

| # | Test | Passes when |
|---|---|---|
| T1 | IN face matrix: a redstone block next to each of the 6 machine faces | the matching wall emits 15 inward, and a lamp inside turns on |
| T2 | OUT face matrix: a redstone block next to the wall inside, for each face | the machine emits 15 on that face, and a lamp outside turns on |
| T3 | Analog: dust with falloff feeding the machine | the stored power equals the dust level |
| T4 | Toggle mode | the direction swaps, the old output drops to 0, and the new direction works |
| T5 | Loop: IN on N and OUT on S, wired together inside and outside | 100 ticks with no exception, and the power stays within 0–15 |
| T6 | A redstone face exposes no item, fluid or energy capability, from either side | `null` from both sides, and an item tunnel on another face still moves items |
| T7 | Machine removed | the wall inside drops to 0 |
| T8 | Wrench cycle and remove | the output moves with the face, and after removal both ends emit 0 |
| T9 | Nested: a signal through two machines | it arrives, taking one extra tick per hop |
| T10 | Port flags | the machine state shows `redstone` for that face and `tunnel` for the others, and removal clears it |

Run the full suite (the 15 existing tests plus these) after every tunnel or loading change.

## Manual test doc

Write `docs/plans/tiny-tunnels-phase-7-testing.md` once 7a–7c work. Follow the usual rules: checkbox steps, exact expected results, one command per code block. It should cover:

- a lever outside lighting a lamp inside, and the reverse
- dust connecting to both ends
- a comparator reading through a tunnel (analog)
- a clock through a loop
- a signal surviving a room unload and reload
- the Jade lines for all three blocks
- the recipe book in survival
- the face letters: red for redstone ports

## Order of work

1. 7a steps 1–4 (data), then 8 (the machine port enum). Run the existing 15 GameTests: the port enum must not break the capability or face-flag tests.
2. 7a steps 5–11, then T1–T10.
3. 7a assets (steps 12–15).
4. 7b Jade.
5. 7c recipes.
6. The manual test doc, then an in-game pass. Update the Status table in `tiny-tunnels-implementation.md`.

## Phase 8+ (moved out of 7)

- Typed or multiple tunnels per face (Compact Machines #108, #568). This would build on the separate-map approach above.
- Mekanism chemicals, once Mekanism ships for 26.1.2.
- 2×2-chunk rooms.
- Living miniature (spike first).
- Outside the phases: room names, the debug flag and command split, void safety (`docs/todo.md`).

## Implementation notes

- **Verified:**
  - `canConnectRedstone`'s direction points from the dust to our block (`RedStoneWireBlock.getConnectingSide`).
  - `RecipeProvider.Runner` only needs `createRecipeProvider` and `getName`.
  - Jade's `getUid()` returns `Identifier`, and settings keys are `config.jade.plugin_<namespace>.<path>`.
- **Logic** is in `tunnel/RedstoneTunnels`: emit, read, sync both ends, toggle, and the refresh-after-load queue.
- **Load-time refresh is queued to the end of the tick.** `server.execute(...)` runs immediately on the server thread, and scheduling a block tick during chunk load pauses the game in the IDE (`LevelTicks.schedule` → `logAndPauseIfInIde`).
- **Machine face sync also re-announces redstone.** Whether a machine emits follows its port state, which is synced at the end of the tick.
- **Placement checks** for both tunnel items are shared in `tunnel/TunnelPlacement`.
- **Toggle needs an empty hand.** `useItemOn` returns `PASS` with anything in hand, so blocks can still be placed against the wall.
- **Machine removal zeroes the IN signals,** but the room stops ticking once its machine is gone, so lamps inside keep their last look until the room loads again. The signal they're sent is 0, and T7 checks that.
- **GameTest gotcha:** vanilla `GameTestHelper.relativePos` inverts the rotation wrongly (it adds 180°), so `useBlock` can't target room positions. `RedstoneGameTests.click` does the same click sequence on absolute positions.
- **Textures** are generated by recolouring the tunnel textures: red ports, yellow corner marks for out, brighter while powered. The Jade plugin lives in `compat/jade` (`TinyTunnelsJadePlugin`, `ServerData`, `Tooltips`).
- **Shell repair on entry** (`RoomBuilder.build`, called by `RoomTeleporter` every time a player enters) now also restores redstone tunnels. Before this fix it turned them into plain walls while `RoomData` still listed them. Found in-game; covered by the `redstone_shell_repair` GameTest.
- **Jade is compact.** Found in-game: the first version used about half of a small window. The machine shows at most "Tunnels: D U" and "Redstone: N in 15"; each wall shows one line. There's no room-size line, because the block name has the size.
- **Redstone port art** (found in-game): item tunnel ports are coloured per side, and south is already red, so "red means redstone" didn't work, and the first recolour only matched north's blue. Redstone ports now use a dark frame with a redstone-red letter on every side: dim when off, bright on a reddish fill when on, yellow corner marks for out. The machine's redstone port uses the bright letter.
- **On and off are obvious, outside too** (found in-game: the first on/off difference was barely visible). Off is dark with a dark letter; on is a bright red port with a pale letter. The machine's port shows the same, via a fourth `PortKind`, `redstone_on` (4⁶ = 4096 states per machine). `RedstoneTunnels.updateMachineLook` flips it with `UPDATE_CLIENTS` only, so a signal change causes no extra neighbour or shape updates. Adding and removing ports still goes through the face sync. The face-matrix and toggle GameTests check the machine's look.

