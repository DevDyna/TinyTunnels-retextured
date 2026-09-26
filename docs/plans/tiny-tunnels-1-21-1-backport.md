# Tiny Tunnels 1.21.1 Backport Plan

This plan comes **after the MVP**. Once the 26.1.2 MVP (Phases 1–6b of `tiny-tunnels-implementation.md`) is done, ship the same feature set on **Minecraft 1.21.1 / NeoForge 21.1.x**. That's the version most modpacks still use, and it's where Create, Mekanism 10.7 and Compact Machines 7 live today (`docs/research/modlist-26-1-2-support.md`).

- **Primary version:** 26.1.2. New work lands there first.
- **1.21.1:** a maintained port with the same features. The optional living miniature is the only exception (see B7).

## Why it's worth doing

- It reaches today's players and packs, including your own instance.
- Compact Machines 7 on 1.21.1 still has no tunnels. That's the gap players ask about most (CM #609).
- Testing gets richer: Create, Mekanism 10.7, AE2 and Pipez all have stable 1.21.1 builds.

## Strategy: one branch per MC version, with seams

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **Branch per version** (`mc26.1.2/dev`, `mc1.21.1/dev`) | What Create, Mekanism and AE2 do; each branch is plain, idiomatic code; simple builds | Fixes have to be ported by hand |
| Stonecutter-style preprocessor (one source tree, comment directives) | One place for fixes | Directives everywhere, because the gap covers the transfer API, codecs, saving, datagen and renames; worse IDE experience |
| Common module plus version adapter modules | Clean in theory | Minecraft types (`Identifier` vs `ResourceLocation`, `ValueInput` vs `CompoundTag`) leak through almost every signature, so "common" ends up tiny |

**Decision: a branch per version.** Keep the parts that differ by version in a few seam classes, so each port or fix touches known files only.

### Seams to keep during MVP development (rules for the 26.1.2 code)

1. **Transfer:** everything that touches `ResourceHandler`, `EnergyHandler` or `Transaction` lives in `tunnel/TunnelCapabilities.java` and `tunnel/ProxyGuard.java`.
2. **Persistence:**
   - `RoomData`'s `SavedDataType` / codec setup
   - `ValueInput`/`ValueOutput` code in the machine and tunnel block entities, kept in small `load`/`save` helpers
3. **Teleport:** `teleport/RoomTeleporter.java` is the only caller of `TeleportTransition`.
4. **Block lifecycle hooks:** removal (`preRemoveSideEffects`), `neighborChanged`, `useItemOn` return types.
5. **Datagen and hand-written JSON:** `datagen/` and `data/tinytunnels/dimension_type/`.
6. **GameTests:** `gametest/`.
7. **Java syntax:** write Java 21-compatible code. Avoid the unnamed `_` variable (22), flexible constructor bodies (25), primitive patterns and module imports.
8. **Pure logic in plain Java** (no Minecraft types, or only stable ones like `BlockPos` and `Direction`): `RoomGeometry`, room allocation, face-cycling rules, the recursion counter. Those files should copy across unchanged.

## API differences, 26.1.2 → 1.21.1

Signatures below were checked against `neoforge-21.1.251-sources.jar`, apart from the rows marked *(verify)*.

| Area | 26.1.2 | 1.21.1 |
|---|---|---|
| Java / mappings | Java 25, unobfuscated | Java 21, Mojang names + **Parchment** 2024.11.17 |
| IDs | `Identifier` | `ResourceLocation` |
| **Item capability** | `Capabilities.Item.BLOCK` → `ResourceHandler<ItemResource>` | `Capabilities.ItemHandler.BLOCK` → `IItemHandler` |
| **Fluid capability** | `Capabilities.Fluid.BLOCK` → `ResourceHandler<FluidResource>` | `Capabilities.FluidHandler.BLOCK` → `IFluidHandler` |
| **Energy capability** | `Capabilities.Energy.BLOCK` → `EnergyHandler` | `Capabilities.EnergyStorage.BLOCK` → `IEnergyStorage` |
| **Transfer semantics** | `Transaction` passed through, atomic rollback | `simulate` boolean passed through; no rollback |
| Empty handlers | `EmptyResourceHandler`, `EmptyEnergyHandler` | `EmptyItemHandler.INSTANCE`, `EmptyFluidHandler.INSTANCE`, `EmptyEnergyStorage` |
| Saved data | `SavedDataType(id, ctor, codec)`, `server.getDataStorage()` | `SavedData.Factory(ctor, (tag, provider) -> …)`, `server.overworld().getDataStorage().computeIfAbsent(factory, "tinytunnels_rooms")`, `save(CompoundTag, Provider)` |
| Block entity I/O | `loadAdditional(ValueInput)` / `saveAdditional(ValueOutput)` | `loadAdditional(CompoundTag, HolderLookup.Provider)` / `saveAdditional(...)` |
| Implicit components | `applyImplicitComponents(DataComponentGetter)` | `applyImplicitComponents(BlockEntity.DataComponentInput)` *(verify)* |
| Removal hook | `BlockEntity#preRemoveSideEffects` | `BlockBehaviour#onRemove(state, level, pos, newState, movedByPiston)`, guarded with `!state.is(newState.getBlock())` |
| `neighborChanged` | `(…, @Nullable Orientation, boolean)` | `(…, Block neighborBlock, BlockPos neighborPos, boolean)` |
| `useItemOn` return | `InteractionResult` | `ItemInteractionResult` |
| Teleport | `ServerPlayer#teleport(TeleportTransition)` | `ServerPlayer#teleportTo(ServerLevel, x, y, z, yRot, xRot)` |
| Attachments | `.serialize(MapCodec<T>)` | `.serialize(Codec<T>)` |
| Tickets | `TicketController(Identifier, callback)`, UUID `forceChunk(…, ticking)` | Same, with `ResourceLocation` |
| Capability caches | `BlockCapabilityCache.create(…, isValid, listener)` | Same |
| Scale attribute | `Attributes.SCALE` | Same (added in 1.20.5) |
| Dimension type JSON | Codec fields + `attributes` (`gameplay/bed_rule`, …), `skybox`, `timelines` | Old flat fields: `bed_works`, `respawn_anchor_works`, `has_raids`, `piglin_safe`, `ultrawarm`, `natural`, `effects`, `fixed_time` |
| Datagen models | Model provider and item model definitions (1.21.4+) | `BlockStateProvider` / `ItemModelProvider`, no `assets/*/items/` definitions |
| GameTests | Data-driven, `RegisterGameTestsEvent` | `@GameTestHolder` + `@GameTest` methods with structure templates *(verify)* |
| BER rendering (post-MVP miniature) | `extractRenderState` / `submit` | Immediate-mode `render(be, partialTick, poseStack, buffers, light, overlay)` |

### The one real design difference: `simulate` versus transactions

The design stays the same: passthrough, no buffers, and a guarded delegate around a snapshot of the endpoint handler.

- **Delegates:** implement `IItemHandler`, `IFluidHandler` and `IEnergyStorage` delegates that forward every call 1:1, **including the `simulate` flag**, inside the reentrancy guard.
  - `IItemHandler`: `getSlots`, `getStackInSlot`, `insertItem`, `extractItem`, `getSlotLimit`, `isItemValid`
  - `IFluidHandler`: `getTanks`, `getFluidInTank`, `getTankCapacity`, `isFluidValid`, `fill`, `drain` ×2
  - `IEnergyStorage`: `receiveEnergy`, `extractEnergy`, `getEnergyStored`, `getMaxEnergyStored`, `canExtract`, `canReceive`
- **Why no rollback is fine:** a simulate call followed by an execute call reaches the same real handler, and we hold no state in between, so the two can only disagree if the world changed between the calls. That's the same exposure as a pipe next to a normal chest.
- **Tests that change:**
  - "rolled-back transaction changes nothing" becomes "`simulate=true` has no side effects on either side".
  - "removing a tunnel mid-extract" becomes "remove the tunnel between the simulate and execute calls: execute returns empty or 0, with no void".

## Phases

### B0: During the MVP (no extra work)

Follow the seam rules above. When the MVP is tagged, note which files changed since the last port. That list is the porting checklist.

### B1: Branch and toolchain

1. Create `mc1.21.1/dev` from the MVP tag. Swap the build files for `MDK-1.21.1-ModDevGradle`: MDG 2.0.147, NeoForge **21.1.251** or later, Parchment `2024.11.17`, Java 21. The repo's first MDG commit already had these files.
2. Set `mod_version` to `x.y.z+mc1.21.1`; the main branch uses `x.y.z+mc26.1.2`.
3. Pin `minecraft_version_range=[1.21.1]` and a `neo_version` range starting at 21.1.x.
4. Check that the Client run config launches with the Example mod stripped.

**Acceptance:** `./gradlew compileJava` fails only in the seam files.

### B2: Mechanical renames

`Identifier` → `ResourceLocation`, plus import fixes across the tree. Replace any Java 22+ syntax that slipped in.

**Acceptance:** only seam files still fail to compile.

### B3: Persistence, teleport, lifecycle, attachments

- `RoomData`: switch to `SavedData.Factory`, overworld storage, and `save(CompoundTag, Provider)`. Keep the same `Room` codec and use `Codec` ↔ NBT through `NbtOps`.
- Block entities: `CompoundTag` load/save helpers.
- `RoomTeleporter`: `teleportTo(ServerLevel, …)`.
- `ReturnStack` attachment: serialize with a `Codec`.
- Removal: `onRemove` in `MachineBlock` and `TunnelWallBlock`. `neighborChanged` and `useItemOn` signatures.

**Acceptance:** Phases 1–5 acceptance checks pass on 1.21.1.

### B4: Transfer layer

- Rewrite `TunnelCapabilities` for `Capabilities.ItemHandler`, `FluidHandler` and `EnergyStorage`.
- Rewrite `ProxyGuard` with the three `simulate`-forwarding delegates.
- Swap the empty handlers.
- Keep caching, invalidation (including `updateNeighborsAt`) and endpoint rules unchanged.

**Acceptance:** Phase 6a acceptance passes on 1.21.1.

### B5: Data and assets

- Rewrite the dimension type in the 1.21.1 format:
  - `has_skylight: false`, `has_ceiling: false`, `fixed_time: 6000`, `natural: false`
  - `bed_works: false`, `respawn_anchor_works: false`, `has_raids: false`, `piglin_safe: false`, `ultrawarm: false`
  - `ambient_light: 0.0`, `monster_spawn_light_level: 0`, `monster_spawn_block_light_limit: 0`
  - `effects: minecraft:the_end`, `infiniburn: #minecraft:infiniburn_overworld`
  - `min_y: 0`, `height: 256`, `logical_height: 256`
- `dimension/rooms.json` is unchanged (`features: false`).
- Datagen: the 1.21.1 `BlockStateProvider` / `ItemModelProvider`. Loot and lang are nearly identical. Regenerate `src/generated`.

**Acceptance:** `runData` works and the dimension loads with no codec errors.

### B6: Tests on 1.21.1

1. Port the GameTests to the 1.21.1 format. Same cases; the transaction tests become simulate tests.
2. Manual mod matrix, the payoff of this branch:
   - **Create:** belts, funnels and chutes into and out of a machine; mechanical pump fluids.
   - **Mekanism 10.7:** universal cables and a logistical transporter, pushing and pulling. Chemicals stay out of scope.
   - AE2 storage, import and export buses on a machine face.
   - Pipez, Storage Drawers, Refined Storage 2.
   - **Compact Machines 7 installed alongside**: both mods load, their dimensions don't collide, and a CM machine inside a Tiny Tunnels room works.

**Acceptance:** all Phase 6b cases pass, and the mod matrix shows no void, no dupe and no crash.

### B7: Release and maintenance

- Release on CurseForge and Modrinth as separate files per MC version, with a changelog that tags which versions a fix applies to.
- **Fix flow:** fix on `mc26.1.2/dev` first, then port. For a bug that exists only on 1.21.1, fix it there and note it. Keep a porting-debt list at the top of the 1.21.1 branch's changelog.
- **Feature policy:** 1.21.1 gets every gameplay feature. The **living miniature** (post-MVP rendering) is optional: its renderer uses the 26.x extract/submit model, so it would need a separate immediate-mode implementation on 1.21.1. Decide once it exists on 26.1.2.
- **End of life:** keep 1.21.1 maintained while it's the modpack consensus version. Freeze it (critical fixes only) once Create and Mekanism have been stable on 26.x for about a season.

## Effort estimate

| Phase | Size |
|---|---|
| B1–B2 | Small: toolchain and renames, about a day |
| B3 | Small to medium: about five files |
| B4 | Medium: three delegates plus the provider rewrite; the logic is unchanged |
| B5 | Small to medium: mostly datagen API differences |
| B6 | Medium: porting tests plus the manual mod matrix |

The seam rules in B0 are what keep this small. If transfer or persistence code spreads outside the seam files during the MVP, B3/B4 grow in proportion.
