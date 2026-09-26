# Tiny Tunnels Implementation Plan

Implementation plan for the design in `tmp/initial.md` (the "Pocket Rooms" spec), **targeting Minecraft 26.1.2 / NeoForge 26.1.2.x**. The version decision is explained in `docs/research/platform-and-approach.md`. This plan follows the spec's architecture and phases. It adds names, file layout, API signatures verified against 26.1.2, and the decisions the spec left open.

Where it conflicts with the spec, this plan wins. The spec was written for 1.21.1's `IItemHandler` API, which no longer applies.

A 1.21.1 backport comes after the MVP; see `docs/plans/tiny-tunnels-1-21-1-backport.md`.

## Status

| Phase | State |
|---|---|
| 1 Skeleton | Done. Client boots, datagen clean. |
| 2 Dimension and walls | Done. Tested in-game 2026-09-26. |
| 3 Machine and room binding | Done. Tested in-game 2026-09-26. |
| 4 Enter and exit | Done. Tested in-game 2026-09-26. |
| 5 Follow-the-host loading | Done. Tested in-game 2026-09-26 after the ticking fix (see notes). |
| 6a Tunnels (+ occupancy loading, unbreakable shell) | Code done 2026-09-26. Dev server boots with the test mods. In-game checks: `tiny-tunnels-phase-6a-testing.md`. |
| 6b GameTests | Not started |

Implementation notes that differ from the text below:
- `RoomWallBlock` is a plain `Block` configured through properties. A class gets added only if behaviour is needed.
- Package moved from `co.flippingbits.tinytunnels` to `dev.thefern2.tinytunnels` (2026-09-26).
- Machines have explosion resistance 1200, since losing the machine item would strand its room.
- The tunnel wall has no item; the tunnel item places it.
- Config is `SERVER` type (per world): `shrinkAnimation` and `shrinkTicks`.
- Room walls, tunnel walls and machines are **not redstone conductors** (2026-09-26). Chests open underneath them, and power never soaks through. Future redstone tunnels will emit and read signals on purpose instead.
- Grid spacing is 4 chunks (was 2); see Phase 2.
- Tunnels, as built:
  - `TunnelBlockEntity` stores nothing. The room comes from the chunk, the face from blockstate `face`, the inward side from geometry, and the host from `RoomData` at query time.
  - Invalidations and neighbour updates are deferred to end of tick (`CapabilityUpdates`), because a cache's invalidation listener must not query capabilities.
  - Caches only notify after being queried, so both ends are nudged on machine load, tunnel load, and any chunk loading next to a machine.
  - `ProxyGuard.MAX_DEPTH` is 8, not 3, to allow deep nesting.
  - Test mods are `localRuntime` from Modrinth maven, pinned by version ID in `build.gradle`.
- **Follow-the-host follows ticking, not load/unload.** A room ticket keeps chunks around it in memory without ticking them, so a machine standing near a room never got `onChunkUnloaded` and held its room forever. Found in testing on 2026-09-26: a nested machine kept its room listed after teleporting away. Now `RoomTickets` tracks loaded machine block entities and, every 20 ticks (plus right after any load, unload or bind), holds a room's ticket only while its machine's chunk passes `shouldTickBlocksAt`. This also handles the spec's "loaded but not ticking" edge.
- Placing a machine inside its own room, directly or through nesting, is refused (`RoomData.wouldNestInItself`). Otherwise the room would keep itself loaded forever.
- 26.x uses `Player#sendOverlayMessage` for action-bar messages (1.21.1: `displayClientMessage(c, true)`), which goes on the backport list.

## Identity

| Property | Value |
|---|---|
| `mod_id` | `tinytunnels` |
| `mod_name` | `Tiny Tunnels` |
| Base package / `mod_group_id` | `dev.thefern2.tinytunnels` |
| Main class | `TinyTunnels` (`MODID = "tinytunnels"`) |
| Room dimension | `tinytunnels:rooms` |
| Commands | `/tinytunnels ...` |

## Toolchain (done)

- Project is the official **`MDK-26.1.2-ModDevGradle`**: ModDevGradle `net.neoforged.moddev` 2.0.147, **NeoForge 26.1.2.109**, Gradle 9.2.1, **Java 25** (toolchain auto-provisioned by the foojay plugin).
- **No Parchment.** 26.1+ ships unobfuscated with official names.
- `neoforge.mods.toml` template is at `src/main/templates/META-INF/`, expanded by `generateModMetadata`.
- `./gradlew compileJava jar` passes.
- **Sources for verification:**
  - Vanilla: `build/moddev/artifacts/minecraft-patched-26.1.2.109-sources.jar`
  - NeoForge: `~/.gradle/caches/modules-2/files-2.1/net.neoforged/neoforge/26.1.2.109/*/neoforge-26.1.2.109-sources.jar`
- **IDE:** reload Gradle in IntelliJ and set Project SDK and Gradle JVM to a JDK 25. None is installed locally yet; Gradle downloads one for compiling, but IntelliJ needs one too. `build/moddev/artifacts/` still has stale `21.1.251` jars; delete them whenever convenient.

## Verified API signatures (26.1.2.109)

**Transfer API** (`net.neoforged.neoforge.transfer`). The old `items`/`fluids` handler packages are deprecated here and removed in 26.3; never use them.

- Capabilities:
  - `Capabilities.Item.BLOCK`: `BlockCapability<ResourceHandler<ItemResource>, @Nullable Direction>`
  - `Capabilities.Fluid.BLOCK`: `BlockCapability<ResourceHandler<FluidResource>, @Nullable Direction>`
  - `Capabilities.Energy.BLOCK`: `BlockCapability<EnergyHandler, @Nullable Direction>`
- `ResourceHandler<T>`:
  - `size()`, `getResource(int)`, `getAmountAsLong(int)`, `getCapacityAsLong(int, T)`, `isValid(int, T)`
  - `insert(int index, T, int amount, TransactionContext)`, `extract(int index, T, int amount, TransactionContext)`
  - default index-less `insert(T, int, TransactionContext)` and `extract(T, int, TransactionContext)`
- `EnergyHandler`: `getAmountAsLong()`, `getCapacityAsLong()`, `insert(int, TransactionContext)`, `extract(int, TransactionContext)`.
- Transactions:
  - `Transaction.openRoot()` and `Transaction.open(@Nullable TransactionContext parent)`; `commit()`.
  - `TransactionContext` is sealed (only `Transaction`), so we never implement it, only pass it through.
- Delegating wrappers:
  - `DelegatingResourceHandler<T>(ResourceHandler<T>)` or `(Supplier<ResourceHandler<T>>)`
  - `DelegatingEnergyHandler(Supplier<EnergyHandler>)`
  - also `EmptyResourceHandler` and `EmptyEnergyHandler`

**Capabilities and chunk loading**

- `RegisterCapabilitiesEvent#registerBlock(BlockCapability<T,C>, IBlockCapabilityProvider<T,C>, Block...)`.
- `BlockCapabilityCache.create(cap, ServerLevel, BlockPos, C context, BooleanSupplier isValid, Runnable invalidationListener)`.
- `Level#invalidateCapabilities(BlockPos)` and `BlockEntity#invalidateCapabilities()` (NeoForge extensions).
- `TicketController(Identifier id, @Nullable LoadingValidationCallback callback)`, registered via `RegisterTicketControllersEvent`.
  - `forceChunk(ServerLevel, UUID owner, int chunkX, int chunkZ, boolean add, boolean ticking)`.
  - The BlockPos and Entity overloads now take `forceNaturalSpawning` as their last argument; the UUID overload is unchanged.
- `LoadingValidationCallback#validateTickets(ServerLevel, TicketHelper)`, with `TicketHelper#getEntityTickets()` and `removeAllTickets(UUID)`.

**Block entity lifecycle**

- `IBlockEntityExtension#onLoad()` / `onChunkUnloaded()`.
- `BlockEntity#preRemoveSideEffects(BlockPos, BlockState)`: runs when the block is actually removed, not on chunk unload. It replaces the old `onRemove` pattern.
- `BlockBehaviour#affectNeighborsAfterRemoval(...)` exists too.
- Save and load use `loadAdditional(ValueInput)` / `saveAdditional(ValueOutput)`, not `CompoundTag`.
- `applyImplicitComponents(DataComponentGetter)` / `collectImplicitComponents(DataComponentMap.Builder)` carry the room ID between item and block entity.

**Saved data**

- `SavedDataType<T>(Identifier id, Supplier<T> constructor, Codec<T> codec)` is codec-based.
- **`MinecraftServer#getDataStorage()` is server-global**, with `computeIfAbsent(type)`. There's no need to attach it to the overworld.

**Attachments**

- `AttachmentType.builder(Supplier<T>)`, `.serialize(MapCodec<T>)` (a **MapCodec** now, not a `Codec`), `.copyOnDeath()`, `.sync(StreamCodec)`.

**Teleport**

- `ServerPlayer#teleport(TeleportTransition)`.
- `TeleportTransition(ServerLevel, Vec3 pos, Vec3 speed, float yRot, float xRot, PostTeleportTransition)`; use `TeleportTransition.DO_NOTHING` for the last argument.
- `teleportTo(ServerLevel, x, y, z, Set<Relative>, yRot, xRot, boolean resetCamera)` also exists.

**Other**

- `ResourceLocation` → **`Identifier`**. `UUIDUtil.CODEC` / `UUIDUtil.STREAM_CODEC`. `Attributes.SCALE` / `Attributes.BLOCK_INTERACTION_RANGE`.
- Block/item interaction:
  - `useWithoutItem(BlockState, Level, BlockPos, Player, BlockHitResult)` and `useItemOn(...)`
  - `neighborChanged(..., @Nullable Orientation, boolean)`
  - `Item#useOn(UseOnContext)` and `Item#use(Level, Player, InteractionHand)`
  - `Block#setPlacedBy(...)`
- **Dimension type JSON** (now codec fields plus environment attributes). Required fields:
  - `has_skylight`, `has_ceiling`, `has_ender_dragon_fight`, `coordinate_scale`, `min_y`, `height`, `logical_height`, `infiniburn`, `ambient_light`, and monster settings
  - Optional: `has_fixed_time`, `skybox` (`none|overworld|end`), `cardinal_light`, `attributes`, `timelines`, `default_clock`
  - Gameplay rules are now attributes: `minecraft:gameplay/bed_rule`, `respawn_anchor_works`, `can_start_raid` and others
  - Template: vanilla `data/minecraft/dimension_type/the_end.json` inside `minecraft-patched-26.1.2.109-merged.jar`
- GameTests: `RegisterGameTestsEvent` exists and vanilla tests are data-driven. Check the exact registration when Phase 6b starts.

## Decisions on open questions

| Question | Decision |
|---|---|
| Duplicated bound machine (Create, Building Gadgets, creative middle-click) | On placement, if `room.hostPos` points to a loaded, live `MachineBlockEntity` somewhere else, **refuse placement** (cancel, give the item back, show an action-bar message). If the recorded host no longer exists, overwrite it. If the host's chunk is unloaded, treat it as live and refuse. |
| Return stack on death | **Cleared** (no `copyOnDeath`). |
| Room sizes | One `MachineBlock` class with **six registered blocks**: tiny 3, small 5, normal 7, large 9, giant 11, maximum 13 (interior). |
| Tunnel placement | Wall faces, floor, and ceiling allowed. Edges and corners are not. |
| Invalidation mechanism | Primary: each `BlockCapabilityCache`'s `invalidationListener` propagates to our exposed position. `neighborChanged` is only a fallback. |
| Proxy handlers | **Always return a thin guarded delegate around a snapshot of the endpoint's handler** (the handler resolved at query time, not a `Supplier`, so indexes can't drift). The caller's `TransactionContext` passes straight through, and the delegate only adds the reentrancy guard. Wrapping always is required to catch loops at transfer time (CM #285, #382). |
| Face has a tunnel but the far side is unloaded, missing, or exposes nothing | Return **`EmptyResourceHandler` / `EmptyEnergyHandler`**, not null, so pipes connect immediately (the root of CM's biggest issue category). Return `null` only when the face has no tunnel. |
| Player inside a room while the machine's area is unloaded (found in testing, 2026-09-26) | **Occupancy keeps the host loaded.** While at least one player is inside a room, a ticket (owned by the room UUID, separate from the follow-the-host ticket) keeps the machine's chunk loaded. For nested rooms it walks up the chain: B's host chunk is in room A, so A's host chunk is kept too, with a depth limit. Released when the last player leaves or the machine is broken. Without this, tunnels would go dead while you're inside. |
| Out of scope for MVP | AE2/RS **grid** cables (buses work because they use capabilities), Mekanism chemicals, heat/air/EU/MJ, redstone. See `docs/research/compact-machines-tunnel-issues.md`. |
| Room storage | `SavedDataType` in `server.getDataStorage()`, id `tinytunnels:rooms`. |
| Assets | Datagen for blockstates, models, lang, and loot. Dimension JSON written by hand. |
| Entering | Scale-lerp shrink animation before teleporting (see research doc, Tier 1). |

## Package layout

```
dev.thefern2.tinytunnels
├── TinyTunnels.java              @Mod; wires registers to the mod bus
├── TinyTunnelsClient.java        client-only setup
├── registry/
│   ├── ModBlocks.java            DeferredRegister.Blocks
│   ├── ModItems.java             DeferredRegister.Items
│   ├── ModBlockEntities.java
│   ├── ModDataComponents.java    ROOM_ID: DataComponentType<UUID>
│   ├── ModAttachments.java       RETURN_STACK, SHRINK_ANIM
│   └── ModCreativeTabs.java
├── room/
│   ├── Room.java                 id, gridIndex, size, host (Optional<GlobalPos>), tunnels (Codec)
│   ├── RoomData.java             SavedData, SavedDataType, server-global
│   ├── RoomGeometry.java         gridIndex -> ChunkPos/origin/spawn; wall and edge tests
│   ├── RoomBuilder.java          places walls in ROOM_DIM
│   └── RoomDimension.java        ROOM_DIM key, getRoomLevel(server)
├── machine/
│   ├── MachineBlock.java
│   ├── MachineBlockEntity.java
│   └── MachineSize.java          enum TINY..MAXIMUM(interior)
├── wall/RoomWallBlock.java
├── tunnel/
│   ├── TunnelWallBlock.java
│   ├── TunnelBlockEntity.java
│   ├── TunnelItem.java
│   ├── TunnelCapabilities.java   RegisterCapabilitiesEvent handler
│   ├── Endpoint.java             record(ServerLevel, BlockPos, Direction side) + isLoaded()
│   └── ProxyGuard.java           depth guard + Guarded{Resource,Energy}Handler
├── teleport/
│   ├── ShrinkerItem.java
│   ├── ReturnPoint.java          record(dim, pos, yRot, xRot) + Codec
│   ├── ReturnStack.java          record(List<ReturnPoint>) + MapCodec
│   └── RoomTeleporter.java       shrink animation, enter, exit
├── loading/RoomTickets.java      TicketController + helpers
├── command/TinyTunnelsCommand.java
├── gametest/                     tunnel GameTests (Phase 6b)
└── datagen/                      providers + GatherDataEvent
```

`GlobalPos` (dimension + pos) replaces the spec's separate `hostDim` / `hostPos`, and it already has a codec.

---

## Phase 1: Skeleton

**Tasks**
1. `gradle.properties`: `mod_id=tinytunnels`, `mod_name=Tiny Tunnels`, `mod_group_id=dev.thefern2.tinytunnels`, and pick `mod_license`. Compact Machines and Smaller Units are MIT, so MIT keeps borrowing simple.
2. Move the sources to `dev/thefern2/tinytunnels/`. Rename `ExampleMod` → `TinyTunnels` and `ExampleModClient` → `TinyTunnelsClient`. Delete the example content and `Config` unless a config value is planned (the miniature toggle will need one later).
3. Rename `assets/examplemod` → `assets/tinytunnels`.
4. Create the `registry/*` classes and register them on the mod bus.
5. Creative tab `tinytunnels:main`.
6. Register placeholder blocks and items: six machines, room wall, tunnel wall, shrinker, tunnel item.
7. Datagen via `GatherDataEvent` (MDK run config `data` uses `clientData()`), with model, lang, and loot providers. Run `./gradlew runData`.

**Acceptance**: the `Client` run config launches, and every block and item is in the creative tab with a name and texture.

## Phase 2: Dimension and walls

**Tasks**
1. `data/tinytunnels/dimension_type/room.json`. Start from vanilla `the_end.json` and change:
   - `has_skylight: false`, `has_ender_dragon_fight: false`, `has_fixed_time: true`, `skybox: "none"`, `ambient_light: 0.0`
   - `monster_spawn_light_level: 0`, `monster_spawn_block_light_limit: 0`
   - `attributes`: `bed_rule` never/never/no explosion, `respawn_anchor_works: false`, `can_start_raid: false`, plus visual colours to taste
   - drop `default_clock` and `timelines` (or keep an empty list)
   - `min_y: 0`, `height: 256`, `logical_height: 256`, `infiniburn: #minecraft:infiniburn_overworld`
2. `data/tinytunnels/dimension/rooms.json`: `minecraft:flat` generator with `layers: []`, `biome: minecraft:the_void`, **`features: false`**, `lakes: false`, `structure_overrides: []`. Without `features: false`, the void biome places its `void_start_platform` at the origin.
3. `RoomDimension.ROOM_DIM = ResourceKey.create(Registries.DIMENSION, id("rooms"))`.
4. `RoomWallBlock`: `strength(-1, 3_600_000)`, `noLootTable()`, `pushReaction(PushReaction.BLOCK)`, `isValidSpawn` returns false, light level about 12.
5. `RoomGeometry`:
   - chunk = `((i % 256) * 4, (i / 256) * 4)`. **4-chunk spacing, not 2:** a forced room chunk fully loads every chunk within 2 of it. At spacing 2, a machine inside a neighbouring room stays loaded and keeps its own room forced, a loop that never releases (found in testing on 2026-09-26).
   - outer size `S = size + 2`, offset `(16 - S) / 2`
   - floor y = 64, spawn at interior center, y 65
   - `isWall`, `isEdgeOrCorner`, `inwardNormal`
6. `RoomBuilder.build(ServerLevel, gridIndex, size)`: fill the shell with flag 2 (no neighbor updates) and clear the interior.
7. `/tinytunnels debug build <gridIndex> <size>`, permission level 2.

**Acceptance**: the dimension is void with no mobs (leave a world in it for a few minutes), and walls can't be broken in survival or pushed by pistons. The dimension JSON loads without codec errors in the log.

## Phase 3: Machine and room binding

**Tasks**
1. `Room` with a `Codec`: `UUID id`, `int gridIndex`, `int size`, `Optional<GlobalPos> host`, `Map<Direction, BlockPos> tunnels`.
2. `RoomData extends SavedData`:
   - `TYPE = new SavedDataType<>(id("rooms"), RoomData::new, CODEC)`
   - `get(server) = server.getDataStorage().computeIfAbsent(TYPE)`
   - holds a room map, `nextGridIndex`, and a chunk index for `byChunk`
   - every mutator calls `setDirty()`
3. `ModDataComponents.ROOM_ID`: `DataComponentType<UUID>` with `persistent(UUIDUtil.CODEC)` and `networkSynchronized(UUIDUtil.STREAM_CODEC)`.
4. `MachineBlockEntity`: `@Nullable UUID roomId` via `ValueInput`/`ValueOutput`, plus implicit components for `ROOM_ID`.
5. `MachineBlock` (`EntityBlock`, `MachineSize`):
   - `setPlacedBy` (server): if unbound, allocate a room, build it, and set the host. If bound, apply the duplicate rule, then set the host.
   - `MachineBlockEntity#preRemoveSideEffects`: clear the host.
   - Loot: `copy_components` for `ROOM_ID`.
   - Item tooltip shows the room ID when bound.
6. `/tinytunnels debug room <uuid>` and `/tinytunnels debug rooms`.

**Acceptance**: placing a machine creates a room. Break and re-place keeps the same UUID and room. It survives reload. A duplicate placement is refused.

## Phase 4: Enter and exit

**Tasks**
1. `ReturnPoint(ResourceKey<Level> dim, Vec3 pos, float yRot, float xRot)` + `Codec`. `ReturnStack(List<ReturnPoint>)` + `MapCodec` (`fieldOf("points")`).
   - `ModAttachments.RETURN_STACK = AttachmentType.builder(ReturnStack::empty).serialize(ReturnStack.MAP_CODEC)`, no `copyOnDeath`.
   - Treat it as immutable and replace it on push and pop.
2. `RoomTeleporter.enter(player, room)`:
   1. Run the shrink animation: add a transient `Attributes.SCALE` modifier that lerps from 1 to 0.0625 over about 10 ticks, driven by a small per-player ticking attachment or server tick event.
   2. Push the return point.
   3. `player.teleport(new TeleportTransition(roomLevel, spawn, Vec3.ZERO, yRot, xRot, TeleportTransition.DO_NOTHING))`.
   4. Remove the scale modifier.
   - Include a config flag to skip the animation.
3. `RoomTeleporter.exit(player)`: pop and teleport. If the stack is empty, go to the respawn/overworld spawn.
4. `ShrinkerItem#useOn` enters, and `ShrinkerItem#use` in `ROOM_DIM` exits. Server only, with a cooldown.

**Acceptance**: enter and exit work, including nested rooms two levels deep. Exiting after the machine was broken returns you to where you stood. Relog inside a room works. Death clears the stack. Scale is always back to 1 afterwards, including if the player disconnects mid-animation.

## Phase 5: Follow-the-host loading

**Tasks**
1. `RoomTickets.CONTROLLER = new TicketController(id("rooms"), (level, helper) -> helper.getEntityTickets().keySet().forEach(helper::removeAllTickets))`. Copy the key set before removing. Register it in `RegisterTicketControllersEvent`.
2. `MachineBlockEntity`: `onLoad`, `onChunkUnloaded`, and `setRemoved` call `setRoomLoaded(boolean)`. Server only, skipped when unbound, idempotent via a `ticketHeld` flag.
3. Defer `onLoad` work to the next server tick if `RoomData` or the room level isn't ready.
4. `/tinytunnels debug tickets`.
5. Check that the 1.21.5+ vanilla ticket storage rework doesn't persist our tickets in a way that bypasses the validation callback (inspect `TicketStorage` after a reload).

**Acceptance**: a furnace inside keeps smelting while you're near the machine, stops beyond simulation distance, and resumes on return. Nested rooms load in a chain. No leftover tickets after a crash and reload.

## Phase 6a: Tunnels

**Tasks**
0. **Occupancy loading** (see Decisions):
   - Track the players in each room: on dimension change, login, logout and death, compare the player's position with `RoomData.byChunk`.
   - While the count is above 0, force the host's chunk in the host level with a second `RoomTickets` controller, `tinytunnels:occupancy`. It needs its own validation callback that also drops everything on load.
   - Walk up nested hosts, depth limit 8.
   - Acceptance:
     - While standing inside a room, `debug tickets` shows its host chunk held.
     - A hopper outside feeding the machine keeps working while you watch from inside.
     - Leaving releases it.
0b. **Unbreakable shell** (decided 2026-09-26, see `docs/todo.md`). Applies only to `room_wall` and `tunnel_wall`. Blocks built inside a room are untouched and behave normally.
   - Cancel `BlockEvent.BreakEvent` for both wall blocks, for every player and game mode. That covers creative instant-break and "fake player" miners.
   - Safety net for mods that remove blocks directly without firing break events: when a room or tunnel wall is replaced by something else outside our own code paths (tunnel placement or removal, `RoomBuilder`), rebuild it on the next tick. Hook this in `onRemove`/`affectNeighborsAfterRemoval`, guarded against our own replacements.
   - Acceptance:
     - Creative and survival can't break a wall. No break animation completes and no hole appears.
     - `/setblock` on a wall is reverted on the next tick, unless it's our own tunnel code.
     - A furnace or modded machine placed inside a room can still be broken and mined normally.
1. `TunnelWallBlock` plus `TunnelBlockEntity { UUID roomId; Direction face; Direction inward; }`.
2. `TunnelItem#useOn` in `ROOM_DIM` on a non-edge `RoomWallBlock`: resolve the room via `RoomData.byChunk`, take the first free face, replace the block, and record it.
3. `TunnelWallBlock#useWithoutItem`: cycle to the next free face. Shift-use with an empty hand: restore the wall and return the item.
4. **Endpoints:**
   - Outside endpoint: `Endpoint(hostLevel, hostPos.relative(face), face.getOpposite())`.
   - Inside endpoint: `Endpoint(roomLevel, tunnelPos.relative(inward), inward.getOpposite())`.
   - `isLoaded()` checks `level.isLoaded(pos)`.
5. **Capability providers** for `Capabilities.Item.BLOCK`, `Capabilities.Fluid.BLOCK`, and `Capabilities.Energy.BLOCK`:
   - On every `MachineBlock`: `side` → room tunnel for that face → the inside endpoint's cached capability.
   - On `TunnelWallBlock`: only `side == inward` → the outside endpoint's cached capability. Any other side returns null.
   - Tunnel exists but the endpoint is unloaded, missing, or exposes nothing → **empty handler**.
   - No tunnel on that face, or client side → `null`.
   - **Never throw.** Wrap the provider body; if the server, `RoomData`, room level, or binding is missing (including during world load), log once and return empty or null.
   - **Never load chunks.** Check `isLoaded` before touching the cache.
   - **Resolve the host at query time** from `RoomData`. The tunnel block entity stores only `roomId`, `face`, and `inward`.
6. **Caches:** `BlockCapabilityCache` per endpoint and capability type, created lazily on the block entity, with `isValid = () -> !isRemoved()`. Rebuild them on mapping, host, or room change.
7. **Passthrough, not buffering:** pipes call `insert/extract(..., transaction)`, and the call reaches the real handler inside their own transaction. That gives atomic commit and rollback across dimensions, with no simulate/execute pair to disagree.
8. **Recursion guard (`ProxyGuard`), in two layers:**
   - Lookup: a `ThreadLocal` depth counter around each provider lookup, max depth 3.
   - Transfer: **always** return `Guarded{Resource,Energy}Handler(snapshot)`, extending `DelegatingResourceHandler` / `DelegatingEnergyHandler`. It re-enters the counter around `insert`/`extract` and returns 0 past the limit.
   - Iteration methods pass straight through.
9. **Invalidation.** Every point below calls `invalidateCapabilities` on **both** ends **and** `updateNeighborsAt`, for pipes that only react to block updates:
   - a cache `invalidationListener` fires (the block behind changed)
   - tunnel placed, removed, or cycled
   - machine placed, broken, rebound, or moved
   - machine block entity `onLoad`
   - **tunnel block entity `onLoad`**: the room loads after the host, so outside pipes need a nudge (CM #78, #532)
   - room ticket added or removed
   - Guard propagation with the same depth counter. `neighborChanged` is only a fallback.
10. **Face indicator:** the tunnel texture or overlay shows its mapped face (blockstate property `face`), and so does the Jade tooltip once Jade is added.
11. **Test mods** (`localRuntime`), all with 26.1.2 NeoForge builds:
   - Pipez: item, fluid, and energy pipes
   - Energized Power or Powah: FE sources
   - Storage Drawers, Refined Storage 3
   - AE2 26.1.2 beta: storage bus through a tunnel
   - Mekanism once 10.8.0 releases
   - Find these on Modrinth maven (`maven.modrinth.com`) or each mod's maven.

**Acceptance**: every Phase 6 check in spec §6, plus:
- An AE2 storage bus on a machine face sees the inventory behind the tunnel.
- A pipe that attached before the tunnel existed connects once the tunnel is placed, with no re-placing.
- A cancelled transaction (e.g. a pipe that simulates or rolls back) leaves both sides unchanged.

## Phase 6b: GameTests

Tunnel bugs are the biggest risk, so automated tests come right after tunnels rather than in Phase 7.

**Tasks**
1. Register tests via `RegisterGameTestsEvent` (check the data-driven 26.x format). Run them with the `gameTestServer` run config.
2. Cover these cases. Each maps to a Compact Machines failure; see the "GameTests and acceptance additions" section of `docs/research/compact-machines-tunnel-issues.md`:
   - push-only transfer in both directions for items, fluid, and FE (no pulling pipe)
   - the full 6 wall × 6 face matrix
   - pipe placed before the tunnel connects afterwards
   - hot-swapping the block behind a tunnel
   - pipes reconnect after save and reload
   - host moved: tunnels follow
   - unloaded endpoint → empty handler, no chunk load
   - two machines feeding each other, and a pipe network looping through a machine: no StackOverflow
   - nested machine
   - rolled-back transaction changes nothing on either side
   - removing a tunnel mid-transfer causes no dupe or void
   - capability query before `RoomData` exists: no exception

**Acceptance**: `./gradlew runGameTestServer` passes in CI (the MDK's `.github/workflows/build.yml` can call it).

## Phase 7+: Later

- From the spec: recipes and progression, typed or multiple tunnels per face, redstone tunnel, Mekanism chemicals, Jade tooltip, 2×2 rooms.
- From research: the **living miniature** (Tier 2 in `docs/research/platform-and-approach.md`). Start with a spike to measure meshing cost and the 26.x `extractRenderState`/`submit` path, and check shader and Vulkan (26.4) behaviour. Every buffer needs a deterministic free.
- Post-MVP: the 1.21.1 backport (`docs/plans/tiny-tunnels-1-21-1-backport.md`).

## Cross-cutting rules

- All logic runs on the server. Every provider, ticket, teleport, and `RoomData` access checks `!level.isClientSide()`.
- Tunnels never buffer. No inventory, tank, or energy store exists anywhere in a tunnel.
- `RoomData` is the only writer of room state, and it calls `setDirty()` on every change.
- Never import `net.neoforged.neoforge.items.*`, `fluids.capability.*`, or `energy.IEnergyStorage`. They're gone in 26.3.
- Keep version-specific API calls behind a few small classes (`TunnelCapabilities`, `ProxyGuard`, `RoomTeleporter`, `RoomData`'s serialization, block entity load/save helpers, block lifecycle hooks, datagen, `gametest/`) so the backport touches those files only. See "Seams" in the backport plan.
- Write Java 21-compatible syntax: no unnamed `_` variables, flexible constructor bodies, or module imports. Pure logic (`RoomGeometry`, allocation, face cycling) should copy to 1.21.1 unchanged.
- Before relying on any API not in the verified list, check it against the 26.1.2 source jars.
