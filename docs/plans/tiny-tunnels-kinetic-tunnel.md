# Tiny Tunnels Kinetic Tunnel Plan

Tiny Tunnels exists for this: build a room full of Create machinery (water wheels, cogs, gearboxes) that reaches a target RPM, then **use that rotation outside the machine**. This plan adds a **kinetic tunnel** that carries Create rotation and stress through a machine face.

**Status (2026-09-29):** B8 started, ahead of B7. The Create API was checked against the 6.0.8 jar, and the design was corrected where it didn't match (see "Findings from Create 6.0.8"). **K0 to K1e are done** (2026-09-29): `./gradlew build runGameTestServer` passes all **60** required tests (45 earlier, 3 kinetic fallback tests, 12 kinetic tests). `./gradlew runGameTestServer -PnoCreate` passes all **48** (no Create on the classpath). **K2, the in-game pass, is done (2026-09-30)**; results are in `tiny-tunnels-kinetic-tunnel-testing.md`. After that the kinetic tunnel moves into a Create addon on a public API, with a port block replacing the kinetic machine (`tiny-tunnels-api-and-addons.md`). Not covered by GameTests: K2 (gearing inside the room, which is Create's own behaviour) and K5 (overload; see "Build notes").

- **Create testing is paused until this is done.** Create inside a room is mostly tested (checklist 6a-inside). Finish 6a, 6a-inside and the Create steps in section 7 after K1, together with the kinetic in-game pass, so Create gets one full pass with rotation included.

- **Where it's built:** on the **1.21.1 backport branch** (`mc1.21.1/dev`), against real **Create 6.0.8** (the version in your own instance, on NeoForge 21.1.217). See phase B8 in `tiny-tunnels-1-21-1-backport.md`.
- **Order:**
  1. finish the Phase 7 in-game redstone pass
  2. do the 1.21.1 backport (B1–B7)
  3. build this (B8)
- **Why 1.21.1:** Create has no public 26.x build. An earlier version of this plan ported a slice of Create's kinetics to 26.1.2 ourselves; that was dropped (see "History"). On 1.21.1 the tunnel is tested against Create's real network code, and it can ship as a real feature.
- **Porting to 26.x later:** when Create releases on 26.x, port `compat/create` forward. Everything Create-specific lives in that package for exactly this reason.

Create class and method names below were first read from `Creators-of-Create/Create` branch `mc1.21.1/dev` (6.0.11) on 2026-09-26, then **checked against the 6.0.8 jar** (`maven.modrinth:create:88L641Un`, with `javap`) on 2026-09-29. Every earlier **verify** is resolved in the next section.

## Findings from Create 6.0.8 (2026-09-29)

| Question | Answer | What it changes |
|---|---|---|
| Methods the design uses | All exist with the expected signatures. `GeneratingKineticBlockEntity`: `updateGeneratedRotation()`, `notifyStressCapacityChange(float)` (protected). `KineticBlockEntity`: `getGeneratedSpeed()`, `calculateStressApplied()`, `calculateAddedStressCapacity()`, `getTheoreticalSpeed()`, `isOverStressed()`, `getOrCreateNetwork()`, protected fields `capacity` and `stress`. | Nothing. |
| How Create finds a rotating block | `RotationPropagator` checks `state.getBlock() instanceof IRotate`, then calls `hasShaftTowards` and `getRotationAxis`. There's no other hook. | The machine **block** has to implement `IRotate`. So: a Create-only `KineticMachineBlock` subclass per size, with the same ids, class picked at registration. |
| Speed sign | Shaft to shaft on the same axis has a modifier of 1 (`getAxisModifier`). A speed's sign is absolute along the axis: the same number means the same spin in world space. | See "Speed sign" below: identity when the wall's side and the machine face match, a sign flip otherwise. |
| Save keys | `KineticBlockEntity.write` uses `Speed`, `Sequence`, `NeedsSpeedUpdate`, `Source`, `Id`, `Stress`, `Capacity`, `Size`, `AddedStress`, `AddedCapacity`, `Network`. `MachineCore` uses `room`. | No clash. |
| Block entity hooks | `SmartBlockEntity.setRemoved()` and `saveAdditional(...)` are **final**. `setRemoved` calls `remove()` (unless the chunk is unloading) and then `invalidate()`. Saving goes through `write(tag, registries, clientPacket)`, loading through `read(...)`. `onChunkUnloaded()` and `loadAdditional` can be overridden. | `KineticMachineBlockEntity` forwards `core.setRemoved()` from `invalidate()`, save and load from `write`/`read` (skip the core when `clientPacket`), and `onChunkUnloaded` as usual. |
| Joining and leaving a network | A new kinetic block entity joins on its first tick (`KineticBlockEntity.tick` → `attachKinetics()` → `RotationPropagator.handleAdded`). `remove()` → `detachKinetics()` → `handleRemoved`. | Our blocks don't extend `KineticBlock`, and **`switchToBlockState` only leaves the old network for `KineticBlock`s** (found by GameTest `kinetic_port_moves` in K1a: the old shaft kept spinning). So when the shafts change, our block entity leaves the network itself while the old state is still set (`network.remove`, `detachKinetics`, `removeSource`, `reActivateSource = true`), then the state is written, and it rejoins on its next tick. |
| Create's wrench | `IRotate extends IWrenchable`. The default `onWrenched` rotates the block, and `onSneakWrenched` breaks it and hands the drops over (it posts a `BreakEvent` first). **`WrenchEventHandler` handles any `c:tools/wrench` item on an `IWrenchable` block at `EventPriority.HIGH`** and cancels the click, so the Tunnel Wrench never reached `TunnelWrenching` on a kinetic wall (found by GameTests `kinetic_wrench_*` in K1b). It skips an already cancelled event. | `TunnelWrenching` runs at `HIGHEST`, so every wrench, Create's included, edits kinetic tunnels like the others. The `IWrenchable` overrides stay as a backstop: wall `PASS`, machine `PASS` (normal pickaxe pickup; wrench pickup of a machine is under "Later"). |
| Create breaking blocks | `RotationPropagator` destroys a kinetic block (`destroyBlock(pos, true)`) whose speed changed too often (`getFlickerScore()`), or that meets a neighbour spinning the other way. `GeneratingKineticBlockEntity.applyNewSpeed` does the same to a source driven against its direction. GameTest `kinetic_sign` turned the **machine into an item** this way, when moving the port restarted it with the old speed for a tick. | Both ends override `getFlickerScore()` to return 0: their speed follows the other side, so its changes are deliberate. Moving the port clears the machine's old speed before it rejoins. A machine destroyed by an opposite spin still drops with its room (the loot table copies it). **Seen in K2 (2026-09-30):** the machine broke a few times on an opposite spin, as expected with Create. The port block in `tiny-tunnels-api-and-addons.md` moves this to a cheap port. |
| Stress config | `calculateStressApplied()` reads `BlockStressValues.getImpact(getStressConfigKey())` by default. | We override both stress methods, so no `BlockStressValues` entry is needed. |
| Bundled mods | The Create jar carries Flywheel 1.0.5, Ponder 1.0.64 and Registrate 1.3.0+67 in `META-INF/jarjar`. | Compiling a `KineticBlockEntity` subclass needs **Ponder** (`SmartBlockEntity` implements its `VirtualBlockEntity`): `compileOnly net.createmod.ponder:Ponder-NeoForge-1.21.1:1.0.64` from `maven.createmod.net`. Registrate isn't needed: tests look Create blocks up by id instead of through `AllBlocks`. Add Flywheel only if spinning visuals are done. |

## Decisions

| Question | Decision |
|---|---|
| Dependency | Create is **optional**. Build with `compileOnly` and `localRuntime` on Create 6.0.8: Modrinth `maven.modrinth:create:88L641Un`, or the matching `com.simibubi.create:create-1.21.1` build on maven.createmod.net. Its Flywheel and Ponder go on `localRuntime` too. Tiny Tunnels must load and run with no Create installed. |
| Where the code lives | Everything that imports `com.simibubi.create.*` lives in `compat/create`. Nothing outside that package references a Create class, so the JVM never loads one when Create is absent. One exception: `gametest/KineticGameTests` (dev only, registered only when `Compat.CREATE` is set). |
| Tunnel direction | Same as the redstone tunnel: **OUT** carries rotation from the room to outside (the main use), **IN** carries it from outside into the room. Build OUT first; IN is its mirror image. |
| Machine side | The machine itself becomes the kinetic source or consumer through a **second machine block entity type**, `KineticMachineBlockEntity` (in `compat/create`). It extends Create's `GeneratingKineticBlockEntity` and shares the machine logic with `MachineBlockEntity`. See "Keeping Create optional". |
| Kinetic faces per machine | **One** to start. A Create block entity belongs to exactly one network, and all its shaft faces are connected, so two kinetic faces on one machine would join two networks together. A per-face fix is under "Later". |
| Does the link state need saving? | **No.** Speed and stress are recomputed every tick. Only the tunnel itself (face, wall position, mode) is saved in `Room`. The live values sit in a transient server-side map. The room is loaded exactly while its machine ticks (`RoomTickets`), so both ends tick together. |
| Changing the direction | Right-click the tunnel wall with an empty hand flips IN/OUT, like the redstone tunnel. The wrench works as it does on tunnels. |

## How rotation crosses the tunnel

A Create network (`KineticNetwork`) lives in one level, and a room is in another dimension. So rotation can't propagate through the wall. Instead, each end is its own kinetic block in its own network, and the two ends swap numbers through a shared link.

For an **OUT** tunnel (room → world):

```
 ROOM NETWORK                                                WORLD NETWORK
 water wheels ─ cogs ─ gearbox ─▶ [tunnel wall]   ═link═▶   [machine face] ─▶ shaft ─ press, fan, ...
                                  consumer                   source
                                  impact = D / |S|           generated speed = S
                                                             capacity = A / |S|
```

The inside wall is a **consumer**. Each tick it publishes to the link:

- `S`, its theoretical speed: `getTheoreticalSpeed()`. This is the speed the room's gearing produces, even if the room is overstressed.
- `A`, the capacity it can pass on: `capacity − (stress − ownStress)`. Here `capacity` and `stress` are the network totals the block entity already holds (`KineticBlockEntity.capacity` / `.stress`), and `ownStress = ownImpact × |S|`.
- `overstressed`: `isOverStressed()`.

The outside machine face is a **source**:

- `getGeneratedSpeed()` returns `S` (sign mapped, see below).
- `calculateAddedStressCapacity()` returns `A / |S|`, or `0` if the inside is overstressed.
- Each tick it publishes `D` to the link: the world network's stress not counting itself, that is `stress − ownStress`.

The inside wall then claims `calculateStressApplied() = D / |S|`. So the load outside counts against the room's water wheels, which is the whole point. Both of these methods are overridden, so no `BlockStressValues` entry is needed for our blocks.

**Why this settles instead of flapping.** Create charges stress at a block's theoretical speed (`KineticNetwork.getActualStressOf` uses `getTheoreticalSpeed()`), not at its effective speed, which is 0 when overstressed.

- If the outside load is more than the room can supply, the room goes overstressed. The outside source then drops its capacity to 0, so the outside goes overstressed too.
- The outside demand `D` is still counted at the theoretical speed, so the inside stays overstressed.
- Both sides stop and stay stopped until the player removes load or adds wheels. That's how Create itself behaves.

The link runs one tick behind, the same trade-off as the redstone tunnel's one tick per hop.

**Updating Create correctly.** Don't only set fields when a value changes. Use the paths Create uses:

- source side: `updateGeneratedRotation()` when `S` changes, `notifyStressCapacityChange(...)` when `A` changes
- consumer side: `getOrCreateNetwork().updateStressFor(this, calculateStressApplied())` then `network.updateStress()`

Only call these when a value changed by more than a small epsilon. Otherwise every tick triggers a network update.

**Speed sign.** A Create speed is absolute along its axis (see the findings). The tunnel acts like one shaft that goes **into** the wall (direction `inward.getOpposite()`) and comes **out of** the machine face (direction `face`). The spin around the direction of travel stays the same:

```
outSpeed = inSpeed × sign(inward.getOpposite()) × sign(face)
sign(d) = +1 if d.getAxisDirection() == POSITIVE, else −1
```

- The wall's side and the machine face are **independent**. `TunnelPlacement` puts a tunnel on any wall position and maps it to the next free face, and the wrench moves it to another face. So the two axes can differ, for example a wall on the room's west side mapped to the machine's top face.
- When the wall is on the same side as the face (the usual case), the factor is +1.
- The same formula works in both directions (IN swaps which end is "in").
- GameTest K3 (`kinetic_sign`) checks it on the 6 same-side pairs and 6 pairs whose axes differ, with a rule that doesn't use the formula: a creative motor inside pushing into the wall at 32 RPM must turn the outside like a creative motor on that machine face pushing outwards at 32 RPM (`32 × step(face)`).

## Keeping Create optional

`MachineBlockEntity` can't extend a Create class, because the class would fail to load without Create. So there are two block entity types for the machine blocks:

1. **Done on `main` on 2026-09-27, before branching.** The machine logic is in `machine/MachineCore`: the room id, binding, the inside capability caches, load/unload/removal, and save/load. `machine/MachineHost` is the interface: `core()`, `getRoomId()`, `getRoom()`, `hostedRoom()`, `bindOnPlace(...)`, `insideCapability(...)`.
   - `MachineBlockEntity` implements it and delegates to its core.
   - Every caller checks `instanceof MachineHost`: `RoomTickets`, `CapabilityUpdates`, `TunnelCapabilities`, `RedstoneTunnels`, `ShrinkerItem`, Jade `ServerData`, the command and the GameTests. The only places that still name `MachineBlockEntity` are its block entity type registration, `MachineBlock.newBlockEntity` and the Jade plugin's class registration.
   - B8 adds the kinetic type in those three places. The Jade plugin needs a second `registerBlockDataProvider` for the kinetic class.
2. `compat/create/KineticMachineBlockEntity extends GeneratingKineticBlockEntity implements MachineHost`, with its own `MachineCore`.
   - Lifecycle forwarding follows the findings: `invalidate()` → `core.setRemoved()`, `write`/`read` → `core.save`/`core.load` (not for client packets), `onLoad`, `onChunkUnloaded`, and the implicit components as in `MachineBlockEntity`.
   - Ticking: the machine block's `getTicker` returns one that calls `tick()` for the kinetic type only.
3. **The machine blocks themselves:** `compat/create/KineticMachineBlock extends MachineBlock implements IRotate`, registered under the same six ids when Create is loaded (see the findings: Create only rotates `instanceof IRotate` blocks).
   - `getRotationAxis(state)`: the axis of the face whose port is `KINETIC`, or `Y` when there's none. `hasShaftTowards(..., face)`: that face's port is `KINETIC`. Both read only the block state, never the block entity.
   - `newBlockEntity` returns the kinetic type. Plain `MachineBlock` still returns the plain one.
   - The `IWrenchable` overrides from the findings.
4. **Class loading.** Create classes must never load without Create, and the verifier can load a class just because a method returns it. So `ModBlocks` calls `compat/create/CreateBlocks.machine(size, props)` whose declared return type is `MachineBlock`, and only when a `Compat.CREATE` flag (`ModList.get().isLoaded("create")`) is true. Same for the wall block and the block entity factory.
5. **Block entity ids.** Both `tinytunnels:machine` and `tinytunnels:kinetic_machine` are **always registered**:
   - With Create, `kinetic_machine` builds `KineticMachineBlockEntity`.
   - Without Create, it builds a plain `MachineBlockEntity` under the `kinetic_machine` type. This needs a `MachineBlockEntity(BlockEntityType<?>, pos, state)` constructor.
   - Why: a chunk drops a block entity whose saved id isn't registered. The machine would lose its room id, and its room would be stranded. With both ids always registered, a world moved between with-Create and without-Create keeps every room. Both types save the core under `room`, and Create's own keys are ignored when it's absent.
   - Both types are valid for all six machine blocks, and with Create both build the kinetic machine.
6. **Machines placed before Create was added**, or before the kinetic tunnel existed: with Create, the plain `tinytunnels:machine` id **also builds `KineticMachineBlockEntity`** (changed 2026-09-29, after the first in-game try refused every existing machine). So every machine becomes kinetic when its chunk loads, with nothing to pick up. Swapping the block entity in place was dropped earlier because removing the old one runs `core.setRemoved()`, which releases the room's tickets; building the right class at load time avoids that. The item's "Pick up this machine and place it again" refusal stays as a defensive check for a plain block entity (GameTest `kinetic_plain_machine_refused` builds one by hand).
7. The tunnel wall block is always registered as `kinetic_tunnel_wall`, but its class depends on Create:
   - With Create: `compat/create/CreateKineticTunnelWallBlock` (implements `IRotate`, has a block entity).
   - Without Create: `tunnel/KineticTunnelWallBlock`, an inert `RoomWallBlock` subclass with the same states (`FACE`, `INWARD`, `MODE`). The Create class extends it.
   - Either way the id exists, so removing Create never leaves a hole in a room's shell. The wall just goes inert.
   - The `kinetic_tunnel_wall` block entity id is Create-only. Without Create the chunk drops that block entity with a log warning, which is fine: the link is transient.
   - The `kinetic_tunnel` item is registered only with Create.

## Build notes

**K0.5 and K1a (2026-09-29):**

- **Data:** `room/KineticTunnel(pos, mode)` reuses `RedstoneMode` for IN/OUT. `Room.kinetic` is saved as `"kinetic"`. `RoomData.setKineticTunnel` replaces any other kinetic tunnel, since a room has at most one. `PortKind.KINETIC` has brass port textures (`machine_kinetic_port_<face>.png`, the blue tunnel ports recoloured) and hand-written overlay models.
- **`RoomGeometry.wallCenter(side)`** replaces the GameTest-only helper.
- K1a had a temporary `/tinytunnels debug kinetic` command to set the link by hand. It was removed in K1b, when the real link replaced it.

**K1b to K1e (2026-09-29).** K1b and K1c were built together: both ends run the same per-tick code, which carries speed one way and capacity and demand the other.

- **Classes (`compat/create`):**
  - `LinkedKineticBlockEntity`: the shared end logic from "How rotation crosses the tunnel". Each tick an end works out its `Link` (room, wall position, driving or not, speed factor). When its role flips (IN/OUT) it leaves the network and rejoins in the new role: a source through `reActivateSource`, a consumer through `updateSpeed`.
  - `KineticMachineBlockEntity` and `KineticTunnelBlockEntity` extend it. The machine drives on OUT, the wall on IN.
  - `KineticLinks`: the transient link state, keyed by **room and wall position**. With a key per room, a tunnel placed on another wall read the old wall's speed for a tick. Cleared on server stop.
  - `CreateKineticTunnelWallBlock`: the shaft points into the room; an empty-hand click flips the direction.
  - `KineticTunnelItem`: refuses a second kinetic tunnel, and refuses a machine whose block entity is plain (loaded host only). Default OUT.
- **Create-free:** `tunnel/KineticTunnelWallBlock` (the inert wall), `tunnel/KineticTunnels` (flip, wrench cycle and remove), `TunnelPlacement` gained a `Check` step that refuses without using the item, and `RoomBuilder`, `ShellProtection` and `TunnelWrenching` know the kinetic wall.
- **Jade:** the machine shows "Rotation: E out, 64 RPM, 128 SU", and the wall shows "Links to the east side: out, 64 RPM, 128 SU", plus "overstressed" when it is. SU is the stress the end claims for the far side.
- **Recipe:** shapeless Tunnel + Create Shaft + Andesite Casing, with a `neoforge:mod_loaded` condition on `create`.
- **Textures:** the kinetic wall (`kinetic_tunnel_wall_<face>_<in|out>`) and the item are the redstone ones recoloured to brass.
- **`-PnoCreate`** leaves Create off the dev runtime (`build.gradle`).
- **GameTests.** Kinetic tests, only with Create:

  | Test | Covers |
  |---|---|
  | `kinetic_out` | K1 |
  | `kinetic_sign` | K3 |
  | `kinetic_stress` | K4, plus "available excludes the tunnel's own claim" |
  | `kinetic_shared_capacity` | K6: a fan inside, a millstone outside |
  | `kinetic_wrench_remove` | K7 |
  | `kinetic_machine_replaced` | K8 |
  | `kinetic_one_per_machine` | K9 |
  | `kinetic_in` | K10 |
  | `kinetic_plain_machine_refused` | K12 |
  | `kinetic_wrench_cycle` | K13 |
  | `kinetic_toggle` | the empty-hand flip |
  | `kinetic_machine_keeps_room` | the saved machine keeps its room |

  Fallback tests, run with and without Create (K11): `kinetic_id_keeps_room` (the `kinetic_machine` id loads as a machine with its room), `machine_id_keeps_room` (the plain `machine` id too, and with Create it loads kinetic) and `kinetic_shell_repair` (the shell repair builds the kinetic wall).
- **Not in GameTests:**
  - **K2** (gearing inside the room) is Create's own behaviour.
  - **K5** (overload) needs a source with small capacity. The creative motor's is too large to overload, and lowering it in Create's stress registry would change it for every kinetic test running in parallel. Test it in game with water wheels, the real use.
  - **The world-save half of K11** (make a world with Create, open it without) is manual.

## Build order

Each step ends with `./gradlew build runGameTestServer` green, and the earlier 45 tests still pass.

| Step | Work | Done when |
|---|---|---|
| K0 | **Seams, Create-free.** `Compat.CREATE` flag. `MachineBlockEntity(type, pos, state)` constructor. Always register `kinetic_machine` (plain fallback). `MachineBlock.applyPorts`. Make `TunnelPlacement` public. Create on `compileOnly` plus `localRuntime`, and an optional `create` dependency `[6.0.8,)` in `neoforge.mods.toml`. | Nothing changes in game; existing tests pass. **Done.** |
| K0.5 | **Data, Create-free.** `KineticTunnel`, `Room.kinetic`, `RoomData` setters, `PortKind.KINETIC`, `withPorts`, face-used checks, **K** overlay. | A room saved and loaded with a hand-written kinetic entry keeps it. **Done.** |
| K1a | **Machine side.** `KineticMachineBlock` ×6, `KineticMachineBlockEntity`, ticker, `IWrenchable` overrides. A temporary debug command set the link by hand. | A shaft on the machine's kinetic face turns. Old plain machines still load. **Done.** |
| K1b | **Wall side and link.** `KineticTunnelWallBlock` and its block entity, `KineticLinks`, stale-link rule, the item, wrench cycle and removal, `RoomBuilder` and `ShellProtection`. | K1, K3, K7, K9, K12, K13 pass. **Done.** |
| K1c | **Stress.** Capacity and demand across the link. | K4, K6 pass; K5 moves to K2. **Done.** |
| K1d | **IN mode.** | K10 passes. **Done.** |
| K1e | **Polish.** Jade lines, textures, recipe, lang, `-PnoCreate` run. | K8, K11 pass. **Done.** |
| K2 | **In-game pass**, together with the paused Create checklist sections. | See "Phase K2". **Done 2026-09-30** (K11 skipped until the API split). |

## Phase K1: the kinetic tunnel (1.21.1)

The steps mirror the redstone tunnel from Phase 7a (`docs/plans/tiny-tunnels-phase-7.md`). Reuse its patterns.

### Data

1. `room/KineticTunnel(BlockPos pos, RedstoneMode mode)`: reuse `RedstoneMode`'s IN/OUT, or rename it to `TunnelDirection`.
2. `Room` gains `Map<Direction, KineticTunnel> kinetic`, stored as the optional codec field `"kinetic"`. `nextFreeFace` / `faceAt` check all three maps. `withKinetic(...)`.
   - This is Create-free, so it lives in `room/` and is saved even without Create.
3. `RoomData`: `setKineticTunnel`, `removeKineticTunnel`, `setKineticMode`.
4. `compat/create/KineticLinks`: a transient `Map<(UUID room, Direction face), LinkState>`. `LinkState` is a mutable `speed`, `available`, `demand`, `overstressed` and `lastTick`. It's cleared on server stop, never saved, and removed when the tunnel is removed.
5. `PortKind` gains `KINETIC`, with a letter on the face label.

### Blocks

6. `compat/create/KineticTunnelWallBlock extends RoomWallBlock implements IRotate, EntityBlock`:
   - `getRotationAxis` = the **inward** axis (the wall's normal), not the machine face's axis. The shaft sticks out into the room, and the face the tunnel maps to can be on any axis (see "Speed sign").
   - `hasShaftTowards(dir)` = `dir == INWARD`
   - state `FACE`, `INWARD`, `MODE`
   - The wrench moving the tunnel to another face changes only `FACE`, so the wall's shaft stays put. On the machine the kinetic port moves, so that goes through `applyPorts` (step 8).
   - add it to `ShellProtection`, `RoomBuilder`'s repair pass (leave an intact wall alone, like the redstone wall) and the tunnel-wall tags
7. `compat/create/KineticTunnelWallBlockEntity extends GeneratingKineticBlockEntity`:
   - **OUT** (consumer): `getGeneratedSpeed() = 0`, `calculateStressApplied() = demand / |speed|` (0 when speed is 0). In `tick()`, publish `speed`, `available` and `overstressed`, read `demand`, and update the stress if it changed.
   - **IN** (source): the mirror of the machine's OUT behaviour below.
8. **Machine ports.** `KineticMachineBlock` (see "Keeping Create optional", step 3) gives the machine its `IRotate` side.
   - `MachineBlock.withPorts` sets `PortKind.KINETIC` on the room's kinetic face.
   - Every place that writes a machine's port state must leave the network first when the **kinetic** face changes, or the old network keeps a shaft that's gone. The seam is `MachineBlock.applyPorts(level, pos, newState)`: a plain `setBlock`, and `KineticMachineBlock` overrides it to call `KineticMachineBlockEntity.leaveNetwork()` first when the kinetic face differs (see the findings on joining and leaving). Its only caller is `CapabilityUpdates.syncMachineFaces`. Redstone lit changes (`RedstoneTunnels.updateMachineLook`) don't move shafts and stay a plain `setBlock`.
   - **State count:** a fifth `PortKind` on six face properties takes each machine block from 4⁶ = 4,096 states to 5⁶ = 15,625, about 94k across the six sizes. That's acceptable. If load time or memory shows it, the fallback is one `KINETIC_FACE` property (7 values) instead of a port value.
   - Face label: a **K** overlay, like the redstone and tunnel letters.
   - `TunnelPlacement` is package-private in `tunnel/`. Make `place(...)` and `Target` public so `compat/create` can use them.
9. `KineticMachineBlockEntity`:
   - **OUT face** (source): `getGeneratedSpeed()` = the link speed, sign mapped. `calculateAddedStressCapacity()` = `overstressed ? 0 : available / |speed|`. In `tick()`, publish `demand` and call `updateGeneratedRotation()` / `notifyStressCapacityChange` on change.
   - **IN face** (consumer): the mirror of the wall's OUT behaviour.
   - With no kinetic face it generates 0 and has no shafts, so Create ignores it.
10. `compat/create/KineticTunnelItem`:
    - the same placement checks as the other tunnels (`TunnelPlacement`)
    - refuses if the room already has a kinetic tunnel (one per machine)
    - default mode: **OUT**, the main use
    - message: "Rotation out to North"
11. Removal (wrench sneak-use, machine picked up, room removed): set the link to speed 0, capacity 0 and demand 0 before removing, so the other end drops cleanly instead of spinning on a stale value.
12. Rendering: a static shaft-stub model on the wall's inward face and on the machine's kinetic face. Spinning visuals (Create's `KineticBlockEntityRenderer` / Flywheel `SingleAxisRotatingVisual`) are optional polish. The gauges are enough to test with.
13. Jade: show speed, the stress passed through, and overstress on both ends, in `compat/jade`, only when Create is loaded. Create's goggles already show speed and stress on both block entities, because they're `KineticBlockEntity`s.
14. Datagen:
   - wall and item models, the **K** overlay, lang lines
   - the recipe, with a `neoforge:mod_loaded` condition on `create`, because it uses Create items (for example a tunnel plus a Create shaft and an andesite casing)
   - `runData` runs with Create on the classpath, so the Create-only item is generated. Its JSON is harmless without Create.

### Why rotation can't loop

**Before A7:** with one kinetic face per machine, each machine joined exactly two networks, the one around it and the one in its room, and rooms can't nest inside themselves (`wouldNestInItself`). So machines and networks formed a tree, and one tunnel's output could never feed back into its own input.

**From A7 (several kinetic tunnels per machine) that no longer holds.** Example: walls A and B on one inside network, A OUT and B IN, with ports A and B joined outside. The room drives the outside through A, and the outside drives the room through B. With the motor gone, wall B is still a source in the room, so the loop keeps itself spinning (free energy). With the motor running, the capacity goes round and is counted again. The same can happen across several machines and nested rooms. It isn't only cycles, either. Two OUT tunnels from one room network into one outside network (a "diamond") count the room's spare capacity twice.

**Design (A7.2, agreed and built 2026-10-02):** a guard over the whole link graph, worked out once per server tick in `KineticLinks`.

- **Graph.**
  - The nodes are Create networks, keyed by (dimension, `KineticNetwork.id`).
  - Each link is an edge from its consumer end's network to its driving end's network. The driving end's network is null while that end doesn't turn.
  - A **root** is a network with a real source: any entry in `KineticNetwork.sources` that isn't a link end (a motor, a water wheel).
- **What the ends publish.** Each tick, both ends write to their link's state:
  - their network key, or null;
  - whether that network is a root;
  - the tick, under the same freshness rule as speed (see "Stale links").
- **The pass** runs on `ServerTickEvent.Post`. It decides for each link whether it may drive (`allowed`); the driving end reads that on its next tick, one tick behind, like everything else on the link.
  1. Every root network gets the root set {itself}.
  2. Repeat until nothing changes. For each link that isn't allowed yet, in a fixed order (room id, then wall position):
     - `Rc` = the root set of the consumer's network, from the edges allowed so far.
     - Skip the link if `Rc` is empty: it isn't powered by anything real.
     - If the driving network is null (that end isn't turning yet), allow the link.
     - Otherwise, `Rd` = the driving network's root set (its own root, plus what edges allowed so far bring in). Allow the link only if `Rc` and `Rd` share no root.
     - When a link is allowed, add `Rc` to the driving network's root set, and pass it on downstream.
  3. Root sets only grow, so the pass ends. Links not allowed at the end are blocked.
- **A blocked link** behaves like a stale one: its driving end generates 0 and offers no capacity, and publishes no demand. Its consumer claims nothing. Jade and the goggles say "Blocked: same source on both sides" (`jade.tinytunnels_create.blocked_same_source`). One message for both reasons, because a diamond isn't a loop: either way the network it would drive is already fed by the same source. A link that isn't allowed only because nothing powers it (an empty `Rc`) isn't "blocked": it generates nothing either way, and shows as an idle link ("Links to the east side: In, 0 RPM, 0 SU"). That case was split out in A7.5, after the user saw the "same source" message on an unpowered IN tunnel.
- **The cases:**
  - **Loop, motor running:** A is allowed (`Rc` = {room}, the outside has no root), and the outside's set becomes {room}. B then has `Rc` = {room} and `Rd` = {room}: they share a root, so B is blocked. The room drives the outside, and nothing comes back.
  - **Loop, motor gone:** nothing is a root, so every `Rc` is empty and every link is blocked. It all stops within a couple of ticks. No free energy, and it can't start by itself.
  - **Diamond:** the first link in the fixed order is allowed and the second shares its root, so it's blocked. Capacity is counted once.
  - **Independent sources:** two rooms with their own wheels feeding one outside line, or an outside motor plus a room. The root sets don't overlap, so both are allowed and the capacities add up. Two sources at different speeds in one network is Create's normal overpower-or-break behaviour, as before.
  - **Several machines, nested rooms:** the same graph; keying networks by dimension keeps rooms and worlds apart.
- **No flicker.** Edges and root sets follow the network layout, not who is driving right now. A blocked driving end stays a member of the network it's in, which is turned from elsewhere, so its edge and the decision stay the same tick after tick. The decision changes only when the layout or a root changes: a block placed or broken, a motor stopped, a tunnel moved or flipped.
- **Stress, a related fix.** The driving end publishes as demand its share of its network's stress, `stress × (its capacity / the network's capacity)`, instead of all of it. With one link and nothing else it's all of it, as before. With an outside motor or a second room on the line, each upstream only pays its share. Today, a link into a line that also has a motor charges the room for the whole load. Together with the root rule, capacity and demand are each counted once.
- **Hardening after `loop_nested` failed once (2026-10-02).** The pass had accepted readings up to 2 ticks old, so one pass could mix the state before and after a motor was removed and let a loop link start for a moment. If, during that blip, a port and the shaft next to it became each other's Create source, they kept turning with no generator. A blocked end never checked for that. Three changes:
  1. The pass uses readings at most 1 tick old (was 2).
  2. Starting needs the link allowed in this pass and in at least 2 of the last 3 (`mayDrive`); stopping is immediate.
  3. Every end, whatever its role and whether blocked, runs the source-loop check every tick. It drops the end only on a real loop: the source chain comes back to the end, or goes round in a circle. That clears the blocks it held. A dropped consumer is told to rejoin (`updateSpeed`) whatever real network is next to it.
  - **A first version of this hardening** (readings from this tick only, two passes in a row, and also dropping a chain that ends at a block driving nothing) left links that never started under full-suite load. A consumer dropped by the extra condition, for example while its upstream hadn't started yet, was never told to rejoin. It stayed at 0, published no network, and its link never got a root. Points 1 and 2 were briefly the strict version ("this tick only", "two passes in a row"). That made links fail to start now and then under full-suite load (`loop_nested`, `kinetic_toggle`). The looser rules are back (user's decision, 2026-10-02): they ride over a tick that one end misses under server load, which a lagging real server can hit too, not only GameTests.
- **The A6.8 source-loop guard** now runs on every end (point 3 above). It works inside one network, on Create's per-block `source` pointers; this guard works between networks, across links. A blocked end stops through the normal speed-to-0 path (detach while its speed holds), so it doesn't leave a Create source loop behind.
- **Stale links:** an end that isn't fresh publishes no network, so its edge is missing for that tick. A missing edge only takes power away; it never adds a loop. A fresh consumer with a stale driving end counts as "driving network null" and is allowed, then checked properly once that end is fresh.
- **Cost:** one pass over the links per server tick, nothing per block. Links are few.
- **The link already driving keeps the line (2026-10-02, found in game at step 30):** the guard visits links allowed on the last pass first, so in a diamond a newcomer (a port placed later, or a tunnel flipped to drive the same line) is the one blocked. A driving end that changes role (flip, or a new port) also restarts its link's start history. Before, the newcomer could take over at once while the old link still turned the line, and two sources turning it opposite ways made Create break the new port.
- **Scope:** the guard covers loops that pass through a tunnel. A loop made only of Create blocks, with no tunnel in it, is Create's own behaviour, not ours.

**A7.1 (several per machine)** needs only `maxPerRoom` = 6. `KineticLinks` is already keyed by room and wall. The port finds its tunnel by its own face, and the wall by its own position, so nothing else assumed one per room.

### Stale links

The ends tick in different levels, so one can briefly run without the other: while a machine chunk loads, or during the 20-tick `RoomTickets` reconcile. If `LinkState.lastTick` is more than **2 ticks** old, the reading end treats the link as speed 0 and capacity 0. That's why the link stores `lastTick`.

## Phase K2: tests

**GameTests** in `gametest/KineticGameTests.java`, registered only when Create is loaded. Use Create's creative motor and real consumers, not water, where possible. Check what the wall and machine report with Create's gauges.

| # | Test | |
|---|---|--|
| K1 | Motor at 64 RPM inside, OUT tunnel. The machine face outside reports 64 RPM within 3 ticks. ||
| K2 | Speed-up inside (large cog to small cog). Outside gets 128. ||
| K3 | Sign: for each of the 6 faces, clockwise going in is clockwise coming out from the same viewpoint. Same idea as the redstone T1 face matrix. | No idea what this needs? **Reply:** GameTest `kinetic_sign` already covers it on 12 wall/face pairs. In game, just do a visual check: put an Encased Fan on the outside line and note which way it blows. Then set the inside Creative Motor to the negative of its speed (scroll it). The fan must reverse. Also check that the outside shaft spins the same way as the inside one, seen from behind the direction the rotation travels. That's rarely visible in practice, so skip it if it's hard to see. **In game 2026-09-30: passed.** |
| K4 | Stress: a known capacity inside and a known load outside (e.g. a mechanical press). Neither side is overstressed. The inside stressometer shows the outside load. | I put a millstone which does 1024SU, SU reads on jade when looking at tunnel from inside, but outside says 0SU **Reply: bug, fixed 2026-09-30.** The machine is the source outside, and a source claims no stress itself, so Jade showed its own claim (0). Jade now shows the stress going **through** the tunnel at both ends, so both read 1024 SU. GameTest `kinetic_stress` checks it. Restart the client to pick it up. Also check that a Stressometer inside shows the 1024 SU load. **In game 2026-09-30: Jade shows 1024 SU on both sides. Passed.** |
| K5 | Overload: outside load above what the inside can supply. Both sides become overstressed and stay that way for 40 ticks (no flapping). Remove the load and both recover. | |
| K6 | Inside load plus tunnel: the inside's own consumers and the tunnel share the capacity correctly (`available` excludes the tunnel's own claim). | Unsure what this needs? **Reply:** it checks that loads inside the room and the tunnel share the room's capacity correctly. Inside: the wheels (or a motor) drive a gearbox, which drives both an inside load (for example a Millstone) and the tunnel wall. Outside: a Stressometer on the machine's line. The outside **capacity** should be the room's capacity minus the inside load. Wrong would be minus the inside load twice, or also minus the outside load. Inside, a Stressometer should show inside load + outside load. GameTest `kinetic_shared_capacity` covers it (a fan inside, a millstone outside), so in game it's optional. **In game 2026-09-30: passed.** |
| K7 | Tunnel removed with the wrench. The outside source goes to 0 within 2 ticks. | |
| K8 | Machine picked up and placed again. No leftover link. It works again after placing. |  |
| K9 | A second kinetic tunnel in the same room is refused. | |
| K10 | IN mode: an outside motor drives an inside load. This is the mirror of K1 and K4. | |
| K11 | Without Create: the mod loads, and a saved room with a kinetic tunnel loads with an inert wall and no hole. **The machine keeps its room** (the `kinetic_machine` id loads as a plain machine). Run this as a separate dev run with Create taken off the runtime classpath, for example a `-PnoCreate` Gradle property. The saved world half is manual: make it with Create, then open it without. | This wouldn't apply after split **Reply:** right. After the split (`tiny-tunnels-api-and-addons.md`) it becomes "core without the Create addon": the room keeps its tunnel entry, an inert wall, and the tunnel comes back with the addon (A5). The `kinetic_machine` id goes away with the port block. For now it's optional; skip it in K2. |
| K12 | A machine placed with a plain block entity (as if placed before Create): the kinetic tunnel item refuses with the "pick up and place again" message. | |
| K13 | Wrench moves a kinetic tunnel to another face: the old face outside stops at once, the new face turns, and neither network keeps a ghost shaft. | |

**In-game pass** (`docs/plans/tiny-tunnels-kinetic-tunnel-testing.md`, written when K1 is done):

- Build the target setup: 4 water wheels, then cogs and gearbox up to a target RPM, then an OUT tunnel.
- Outside: shaft, speedometer, stressometer, then real loads (press, fan, millstone).
- Walk in and out with the Shrinker. Unload the chunk and reload it. Save, quit and reload.
- Put a Create contraption next to the machine, then a nested machine with a kinetic tunnel inside a room.

## Later

- **More than one kinetic face per machine.** This needs a block entity per face, for example a thin port block the machine manages on each kinetic face. Revisit after the in-game pass shows whether one face is enough.
- **A gear ratio or speed cap per tunnel**, as an upgrade.
- **Create's wrench picking up a machine** (sneak-use). K1 returns `PASS`, so machines are picked up the normal way. Allow it once it's checked that the drop keeps the room id.
- **Porting forward to 26.x when Create ships there:**
  - port `compat/create` against the new Create API
  - `MachineHost` / `MachineCore` already exist on both branches
  - check whether Create 26.x changed `IRotate`, `KineticNetwork` or the stress APIs

## History

- **2026-09-26:** the first version of this plan (`tiny-tunnels-create-kinetics-prototype.md`) ported a slice of Create's kinetics to 26.1.2: a Create fork, Catnip from source, and Flywheel `1.0.6-mc26.1-9`. It was dropped for the 1.21.1 route: real Create, and no fork to maintain.
- **R&D notes, 2026-09-27:**
  - **Compact Machines** never had rotation tunnels. Its tunnels were only ever items, fluids, energy and redstone, and CM7 has none. Nobody has shipped this before.
  - **Other kinetics mods:** no NeoForge 26.1.2 mod has a Create-style rotation network. Searched on Modrinth only; CurseForge-only mods weren't checked.
  - **Create Fly** (`ZurrTum/Create-Fly`, CC0 port code) is an unofficial **Fabric-only** port of Create 6.0.9 to 26.1.2 and 26.2. It's a useful reference for how Create's code changes on 26.x, but it can't run alongside Tiny Tunnels.
- **Licence:** Create's code is MIT, but its assets are All Rights Reserved. We depend on Create; we never bundle its assets.
