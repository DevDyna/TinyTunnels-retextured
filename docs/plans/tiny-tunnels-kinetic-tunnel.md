# Tiny Tunnels Kinetic Tunnel Plan

Tiny Tunnels exists for this: build a room full of Create machinery (water wheels, cogs, gearboxes) that reaches a target RPM, then **use that rotation outside the machine**. This plan adds a **kinetic tunnel** that carries Create rotation and stress through a machine face.

**Status (2026-09-27):** planned. Nothing is implemented yet.

- **Where it's built:** on the **1.21.1 backport branch** (`mc1.21.1/dev`), against real **Create 6.0.11**. See phase B8 in `tiny-tunnels-1-21-1-backport.md`.
- **Order:**
  1. finish the Phase 7 in-game redstone pass
  2. do the 1.21.1 backport (B1–B7)
  3. build this (B8)
- **Why 1.21.1:** Create has no public 26.x build. An earlier version of this plan ported a slice of Create's kinetics to 26.1.2 ourselves; that was dropped (see "History"). On 1.21.1 the tunnel is tested against Create's real network code, and it can ship as a real feature.
- **Porting to 26.x later:** when Create releases on 26.x, port `compat/create` forward. Everything Create-specific lives in that package for exactly this reason.

Create class and method names below come from `Creators-of-Create/Create` branch `mc1.21.1/dev` (6.0.11), read on 2026-09-26. Anything marked **verify** hasn't been checked.

## Decisions

| Question | Decision |
|---|---|
| Dependency | Create is **optional**. Build with `compileOnly "com.simibubi.create:create-1.21.1:6.0.11-<build>"` (maven.createmod.net; **verify** the classifier, e.g. `:slim`) plus `localRuntime` for the full jar. Tiny Tunnels must load and run with no Create installed. |
| Where the code lives | Everything that imports `com.simibubi.create.*` lives in `compat/create`. Nothing outside that package references a Create class, so the JVM never loads one when Create is absent. |
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

**Speed sign.** Create's speed sign is relative to the rotation axis (**verify** the convention in `RotationPropagator`). The wall's inward face and the machine's outward face are opposite directions on the same axis. The mapping is a per-face constant, and GameTest K3 pins it down: a shaft spinning clockwise going in must come out spinning clockwise, from the same point of view.

## Keeping Create optional

`MachineBlockEntity` can't extend a Create class, because the class would fail to load without Create. So there are two block entity types for the machine blocks:

1. **Pull the machine logic out into `MachineCore`**, a plain object holding the room id and the binding, load, unload and removal logic. Also add a `MachineHost` interface: `core()`, `getRoom()`, `hostedRoom()`, `insideCapability(...)`.
   - `MachineBlockEntity` implements it and delegates to its core.
   - Code that checks `instanceof MachineBlockEntity` switches to `instanceof MachineHost`: `RoomTickets`, `CapabilityUpdates`, the tunnel items, Jade `ServerData`, commands, GameTests.
   - This refactor is Create-independent. It can happen on both branches, which keeps the seam the same.
2. `compat/create/KineticMachineBlockEntity extends GeneratingKineticBlockEntity implements MachineHost`, with its own `MachineCore`.
   - Its block entity type is registered only when `ModList.get().isLoaded("create")`, and it's valid for all six machine blocks.
3. `MachineBlock.newBlockEntity` returns the kinetic type when Create is loaded, else the plain one.
   - Machines placed before Create was added keep their plain block entity until they're picked up and placed again. That's acceptable; document it.
   - Save format: both types write the core's data under the same keys, so a world moved between with-Create and without-Create keeps its rooms.
   - Create's `write`/`read(CompoundTag, HolderLookup.Provider, boolean clientPacket)` wraps the core's save/load. **Verify** there are no key clashes with Create's `Speed`, `Network`, `Source` and similar keys.
4. The tunnel wall block is always registered as `kinetic_tunnel_wall`, but its class depends on Create:
   - With Create: `compat/create/KineticTunnelWallBlock` (implements `IRotate`, has a block entity).
   - Without Create: an inert `RoomWallBlock` subclass.
   - Either way the id exists, so removing Create never leaves a hole in a room's shell. The wall just goes inert.
   - The `kinetic_tunnel` item is registered only with Create.

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
   - `getRotationAxis` = the face axis
   - `hasShaftTowards(face)` = `face == INWARD`
   - state `FACE`, `INWARD`, `MODE`
   - add it to `ShellProtection` and the tunnel-wall tags
7. `compat/create/KineticTunnelWallBlockEntity extends GeneratingKineticBlockEntity`:
   - **OUT** (consumer): `getGeneratedSpeed() = 0`, `calculateStressApplied() = demand / |speed|` (0 when speed is 0). In `tick()`, publish `speed`, `available` and `overstressed`, read `demand`, and update the stress if it changed.
   - **IN** (source): the mirror of the machine's OUT behaviour below.
8. `MachineBlock` gets an `IRotate` side through the compat layer:
   - `getRotationAxis` = the kinetic face's axis
   - `hasShaftTowards(face)` = the face has a `KINETIC` port
   - `MachineBlock` itself can't implement `IRotate` without loading Create. **Verify** how Create looks for `IRotate`: `RotationPropagator` checks `state.getBlock() instanceof IRotate`. If that's the only check, register a Create-only `KineticMachineBlock` subclass per size, the same trick as the wall block: same ids, class chosen by `ModList` at registration.
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
13. Jade: show speed, the stress passed through, and overstress on both ends, in `compat/jade`, only when Create is loaded.

### Stale links

The ends tick in different levels, so one can briefly run without the other: while a machine chunk loads, or during the 20-tick `RoomTickets` reconcile. If `LinkState.lastTick` is more than **2 ticks** old, the reading end treats the link as speed 0 and capacity 0. That's why the link stores `lastTick`.

## Phase K2: tests

**GameTests** in `gametest/KineticGameTests.java`, registered only when Create is loaded. Use Create's creative motor and real consumers, not water, where possible. Check what the wall and machine report with Create's gauges.

| # | Test |
|---|---|
| K1 | Motor at 64 RPM inside, OUT tunnel. The machine face outside reports 64 RPM within 3 ticks. |
| K2 | Speed-up inside (large cog to small cog). Outside gets 128. |
| K3 | Sign: for each of the 6 faces, clockwise going in is clockwise coming out from the same viewpoint. Same idea as the redstone T1 face matrix. |
| K4 | Stress: a known capacity inside and a known load outside (e.g. a mechanical press). Neither side is overstressed. The inside stressometer shows the outside load. |
| K5 | Overload: outside load above what the inside can supply. Both sides become overstressed and stay that way for 40 ticks (no flapping). Remove the load and both recover. |
| K6 | Inside load plus tunnel: the inside's own consumers and the tunnel share the capacity correctly (`available` excludes the tunnel's own claim). |
| K7 | Tunnel removed with the wrench. The outside source goes to 0 within 2 ticks. |
| K8 | Machine picked up and placed again. No leftover link. It works again after placing. |
| K9 | A second kinetic tunnel in the same room is refused. |
| K10 | IN mode: an outside motor drives an inside load. This is the mirror of K1 and K4. |
| K11 | Without Create: the mod loads, and a saved room with a kinetic tunnel loads with an inert wall and no hole. Run this as a separate dev run with Create taken off the runtime classpath. |

**In-game pass** (`docs/plans/tiny-tunnels-kinetic-tunnel-testing.md`, written when K1 is done):

- Build the target setup: 4 water wheels, then cogs and gearbox up to a target RPM, then an OUT tunnel.
- Outside: shaft, speedometer, stressometer, then real loads (press, fan, millstone).
- Walk in and out with the Shrinker. Unload the chunk and reload it. Save, quit and reload.
- Put a Create contraption next to the machine, then a nested machine with a kinetic tunnel inside a room.

## Later

- **More than one kinetic face per machine.** This needs a block entity per face, for example a thin port block the machine manages on each kinetic face. Revisit after the in-game pass shows whether one face is enough.
- **A gear ratio or speed cap per tunnel**, as an upgrade.
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
