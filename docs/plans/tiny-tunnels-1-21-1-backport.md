# Tiny Tunnels 1.21.1 Backport Plan

This plan comes **after the MVP**. Once the 26.1.2 MVP (Phases 1–6b of `tiny-tunnels-implementation.md`) and Phase 7 (redstone tunnel, Jade, recipes) are done, ship the same feature set on **Minecraft 1.21.1 / NeoForge 21.1.217** (the version in your own instance). That's the version most modpacks still use, and it's where Create, Mekanism 10.7 and Compact Machines 7 live today (`docs/research/modlist-26-1-2-support.md`).

- **Primary version:** 26.1.2. New work lands there first.
- **1.21.1:** a maintained port with the same features. The optional living miniature is the only exception (see B7).
- **Order (decided 2026-09-27):**
  1. finish the Phase 7 in-game redstone pass
  2. do this backport (B1–B7)
  3. build the **kinetic tunnel** on 1.21.1 against real Create (B8)
  - Create has no public 26.x build. So 1.21.1 is where kinetics and the rest of the mod matrix (Create, Mekanism, AE2) can be tested for real. Moving from transactions to `simulate` also shows how these mods behave on the older transfer API.

## Why it's worth doing

- It reaches today's players and packs, including your own instance.
- Compact Machines 7 on 1.21.1 still has no tunnels. That's the gap players ask about most (CM #609).
- Testing gets richer: Create, Mekanism 10.7, AE2 and Pipez all have stable 1.21.1 builds.
- It's the only place the **kinetic tunnel** (Create rotation through a machine face), the reason Tiny Tunnels exists, can be built against real Create today.

## Strategy: one branch per MC version, with seams

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **Branch per version** (`main`, `mc1.21.1/dev`) | What Create, Mekanism and AE2 do; each branch is plain, idiomatic code; simple builds | Fixes have to be ported by hand |
| Stonecutter-style preprocessor (one source tree, comment directives) | One place for fixes | Directives everywhere, because the gap covers the transfer API, codecs, saving, datagen and renames; worse IDE experience |
| Common module plus version adapter modules | Clean in theory | Minecraft types (`Identifier` vs `ResourceLocation`, `ValueInput` vs `CompoundTag`) leak through almost every signature, so "common" ends up tiny |

**Decision: a branch per version.** Keep the parts that differ by version in a few seam classes, so each port or fix touches known files only.

**Branch names (decided 2026-09-27):**

- **`main`** is always the newest Minecraft line, today 26.1.2. There's no `mc26.1.2/dev` branch.
- **`mc1.21.1/dev`** is created from the tag `v0.1.0+mc26.1.2`. That tag marks Phase 7, the redstone recipe change, JEI in dev, and the `MachineCore` refactor.
- **A version branch is only created when `main` moves on.** When a future line (for example 27.x) comes and `main` starts porting to it, first create `mc26.x/dev` from main's last 26.x commit, then port `main`.
- **Never merge between version branches.** Merging would bring the other line's APIs back. Fixes move with `git cherry-pick -x <sha>`: expect conflicts in seam files, fix those by hand, and plain-logic files apply cleanly.

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
8. **Mod compat:** code that imports another mod's classes lives only in `compat/<modid>/` (today `compat/jade`, later `compat/create`), and nothing outside it references those classes. The mod must load with every compat mod absent.
9. **Pure logic in plain Java** (no Minecraft types, or only stable ones like `BlockPos` and `Direction`): `RoomGeometry`, room allocation, face-cycling rules, the recursion counter. Those files should copy across unchanged.

## API differences, 26.1.2 → 1.21.1

Signatures below were checked against `neoforge-21.1.251-sources.jar`, apart from the rows marked *(verify)*. The branch builds against **21.1.217**, so recheck any signature that fails to compile against 217.

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

1. Create `mc1.21.1/dev` from tag `v0.1.0+mc26.1.2`. Swap the build files for `MDK-1.21.1-ModDevGradle`: MDG 2.0.147, NeoForge **21.1.217**, Parchment `2024.11.17`, Java 21. The repo's first MDG commit already had these files.
   - Change `java-version` in `.github/workflows/build.yml` to `21` on this branch. It's `25` on `main`.
2. Set `mod_version` to `0.1.0+mc1.21.1`; `main` uses `0.1.0+mc26.1.2`.
3. Pin `minecraft_version_range=[1.21.1]` and `neo_version_range=[21.1.200,)`. That's the same floor as Create 6.0.8, so the jar runs anywhere that Create version runs.
4. Test mods: replace the 26.1.2 `localRuntime` pins with their 1.21.1 builds. Create **6.0.8** (Modrinth `88L641Un`) needs Flywheel `[1.0.0,2.0)` and Ponder `1.0.64` or newer.
5. Check that the Client run config launches with the Example mod stripped.

**B1 notes (done 2026-09-27):**

- **`gradle.properties`:**
  - `neo_version=21.1.217`, `neo_version_range=[21.1.200,)`, `loader_version_range=[4,)`
  - `parchment_minecraft_version=1.21.1`, `parchment_mappings_version=2024.11.17`
- **Mods.toml template:** gained `modLoader="javafml"` and `loaderVersion`, which FML 4 requires. The `neoforge` dependency now uses `${neo_version_range}`.
- **`build.gradle`:**
  - Java 21 toolchain, the `parchment {}` block, and `data()` in place of `clientData()`.
  - `mavenCentral()` plus `compileOnly "org.jspecify:jspecify:1.0.0"`. Minecraft 26.x ships jspecify but 1.21.1 doesn't, and keeping the same annotations keeps cherry-picks clean.
- **Test mods:** each one accepts NeoForge 21.1.217.
  - Pipez `BPGKb8pi`, Energized Power `gGPYiXzr`, Storage Drawers `px0CCB06`
  - Jade 15.10.6 `eYz2YBGT`, used for both `compileOnly` and runtime
  - AE2 19.2.17 `kfyIqgJ6` with GuideME `hFpGwC6q`
  - Mekanism 10.7.19.85 `5KzzycBT`
  - Create 6.0.8 `88L641Un`, which bundles Flywheel 1.0.5, Ponder 1.0.64 and Registrate
  - **JEI 19.39.0.372 `zHNxmOqp`**. Every JEI from 19.42 onward needs NeoForge 21.1.238. The 1.21.1 JEI doesn't need MezzConfig.
- **CI:** `build.yml` uses JDK 21.
- **Compile result:** the toolchain resolves. `compileJava` stops at javac's 100-error cap, and every error is an expected 26.x API:
  - `Identifier` (B2)
  - `ValueInput`/`ValueOutput`, `SavedDataType`, `TeleportTransition`, `Orientation`, `DataComponentGetter` (B3)
  - `ResourceHandler`, `TransactionContext`, `EnergyHandler` and the resource classes (B4)
  - `TooltipDisplay` (item tooltips, added in 1.21.5)
  - GameTest classes (B6)
- **Missing from the seam list:**
  - `wall/ShellProtection` uses `BreakBlockEvent`. On 1.21.1 that's `BlockEvent.BreakEvent`. **Verify** the name.
  - `TooltipDisplay` in the machine item and Jade tooltips.
  - Handle both in B3.

**Acceptance:** `./gradlew compileJava` fails only in the seam files.

### B2: Mechanical renames

`Identifier` → `ResourceLocation`, plus import fixes across the tree. Replace any Java 22+ syntax that slipped in.

**Acceptance:** only seam files still fail to compile.

**B2 notes (done 2026-09-27):**

- **Renames and 1:1 API swaps:**
  - `Identifier` → `ResourceLocation`, and `dimension().identifier()` → `dimension().location()`
  - `player.sendOverlayMessage(c)` → `player.displayClientMessage(c, true)`
  - `Commands.hasPermission(level)` → `source -> source.hasPermission(level)`
  - `new ClickEvent.SuggestCommand(s)` / `new HoverEvent.ShowText(c)` → `new ClickEvent(Action.SUGGEST_COMMAND, s)` / `new HoverEvent(Action.SHOW_TEXT, c)`
  - Jade `CompoundTag` reads: the 26.x `Optional`/`getIntOr` versions → `contains` + `getString`/`getInt`
  - Item cooldowns take the `Item` (`this`), not the `ItemStack`
  - `MachineItem.appendHoverText` uses the 1.21.1 `List<Component>` signature, so there's no `TooltipDisplay`
  - `updateNeighborsAtExceptFromFacing` has no `Orientation` argument
  - `RoomDimension`: `ServerModLoader.isGameTestServer()` → `server instanceof GameTestServer`
- **Temporary:** `build.gradle` sets `-Xmaxerrs 1000`, so every error shows. It's marked `TODO(backport)`; remove it once the code compiles.
- **What's left:** 121 errors. javac prints each one twice, so its count says 242. Grouped by the phase that fixes them:
  - **B3** (persistence, teleport, lifecycle, attachments, **plus registration**, which wasn't in the original list):
    - `MachineCore`, `MachineBlockEntity`: `ValueInput`/`ValueOutput`, `DataComponentGetter`, `preRemoveSideEffects`
    - `MachineBlock`, `RoomWallBlock`, `RedstoneTunnelWallBlock`: `Orientation`, `affectNeighborsAfterRemoval`, `useItemOn` / `TRY_WITH_EMPTY_HAND`
    - `RoomData`: `SavedDataType`
    - `RoomTeleporter`, `TinyTunnelsCommand`: `TeleportTransition`, `RespawnData`
    - `ShrinkerItem`: `use` returns `InteractionResultHolder<ItemStack>`
    - `ModAttachments`: `serialize(Codec)`
    - `ShellProtection`: `BreakBlockEvent` → `BlockEvent.BreakEvent`
    - **Registration** (`ModBlocks`, `ModItems`, `ModBlockEntities`): `registerBlock(name, factory, Properties)` takes a `Properties` value, not a function; there's no `useBlockDescriptionPrefix`; and block entity types are built with `BlockEntityType.Builder.of(...).build(null)`.
  - **B4:** `TransferKind`, `GuardedResourceHandler`, `GuardedEnergyHandler`
  - **B5:** everything in `datagen/`
  - **B6:** everything in `gametest/`

### B3: Persistence, teleport, lifecycle, attachments

- `RoomData`: switch to `SavedData.Factory`, overworld storage, and `save(CompoundTag, Provider)`. Keep the same `Room` codec and use `Codec` ↔ NBT through `NbtOps`.
- Block entities: `CompoundTag` load/save helpers.
- `RoomTeleporter`: `teleportTo(ServerLevel, …)`.
- `ReturnStack` attachment: serialize with a `Codec`.
- Removal: `onRemove` in `MachineBlock` and `TunnelWallBlock`. `neighborChanged` and `useItemOn` signatures.

- Phase 7: the redstone tunnel wall and machine `PortKind` ports use the same `neighborChanged` / removal changes. `RoomData`'s `redstone` map goes through the same `Room` codec, unchanged.

**Acceptance:** Phases 1–5 acceptance checks pass on 1.21.1.

**B3 notes (code done 2026-09-27; acceptance waits until B4–B6 compile):**

- **Machine save/load:** `MachineCore.load/save(CompoundTag)`. The room id is stored with `putUUID("room")`, the same int-array format as the 26.x `UUIDUtil.CODEC`.
  - `BlockEntity.DataComponentInput` is a protected nested type, so the core can't take it. On this branch the core has `applyImplicitRoom(@Nullable UUID)`, and `MachineBlockEntity` reads the component and passes the UUID in.
- **Removal:** 1.21.1 has no `preRemoveSideEffects` / `affectNeighborsAfterRemoval`, so each block overrides `onRemove(state, level, pos, newState, movedByPiston)`, guarded by `!state.is(newState.getBlock())`:
  - `MachineBlock` calls `core().preRemoveSideEffects(pos)` **before** `super.onRemove`, because `super` removes the block entity. After `super`, it re-notifies the faces it strongly powered.
  - `RoomWallBlock` calls `ShellProtection.onShellRemoved` after `super`. `TunnelWallBlock` inherits this.
  - `RedstoneTunnelWallBlock` calls `super` (the shell repair), then `notifyEmitter`.
  - `MachineBlockEntity` no longer overrides a removal hook.
- **`neighborChanged`:** takes the 1.21.1 parameters `(…, Block neighborBlock, BlockPos neighborPos, boolean)`.
- **Redstone tunnel `useItemOn`:** returns `ItemInteractionResult`:
  - empty hand → `PASS_TO_DEFAULT_BLOCK_INTERACTION` (goes on to `useWithoutItem`, which flips the mode)
  - anything else → `SKIP_DEFAULT_BLOCK_INTERACTION` (goes on to the item's own use, the same as 26.x `PASS`)
  - `useWithoutItem` returns `InteractionResult.sidedSuccess(...)`.
- **`ShrinkerItem.use`:** returns `InteractionResultHolder<ItemStack>`.
- **`RoomData`:** `SavedData.Factory(RoomData::new, RoomData::load, null)` in **overworld** storage, as the file `tinytunnels_rooms.dat`. The same `CODEC` runs through `NbtOps` with the registry context, both ways.
- **Teleport:**
  - `RoomTeleporter.teleport(player, level, pos, yRot, xRot)` is now the only way players are moved. It wraps the boolean `teleportTo(level, x, y, z, Set.of(), yRot, xRot)`, which lets a failed entry still restore the return stack. The debug `build` command uses it too.
  - The world-spawn fallback uses `overworld.getSharedSpawnPos()` / `getSharedSpawnAngle()`, since there's no `RespawnData`.
- **Attachments:** `serialize(ReturnStack.MAP_CODEC.codec())`.
- **Registration:**
  - `registerBlock(name, factory, BlockBehaviour.Properties.of()…)`, where `wallProperties()` now builds its own `Properties`.
  - `registerItem(name, factory[, new Item.Properties()…])`. Block items don't need `useBlockDescriptionPrefix`.
  - `BlockEntityType.Builder.of(factory, blocks…).build(null)`.
- **Events:** `ShellProtection` listens to `BlockEvent.BreakEvent`.
- **Check in game once it runs (behaviour that could differ):**
  - breaking a machine re-powers and de-powers its neighbours correctly
  - wrenching a tunnel back to a room wall doesn't make the shell repair put the old block back (`ShellProtection.edit` should cover this)
  - the empty-hand flip on the redstone tunnel works, and placing blocks against it still works
  - Shrinker exit with no return point lands at world spawn

### B4: Transfer layer

- Rewrite `TunnelCapabilities` for `Capabilities.ItemHandler`, `FluidHandler` and `EnergyStorage`.
- Rewrite `ProxyGuard` with the three `simulate`-forwarding delegates.
- Swap the empty handlers.
- Keep caching, invalidation (including `updateNeighborsAt`) and endpoint rules unchanged.

**Acceptance:** Phase 6a acceptance passes on 1.21.1.

**B4 notes (code done 2026-09-27; acceptance waits until B6 compiles):**

- **`TransferKind`:**
  - `ITEM` = `Capabilities.ItemHandler.BLOCK` / `EmptyItemHandler.INSTANCE`
  - `FLUID` = `Capabilities.FluidHandler.BLOCK` / `EmptyFluidHandler.INSTANCE`
  - `ENERGY` = `Capabilities.EnergyStorage.BLOCK` / `EmptyEnergyStorage.INSTANCE`
- **Guarded handlers:** `GuardedResourceHandler` and `GuardedEnergyHandler` are replaced by three plain delegates, `GuardedItemHandler`, `GuardedFluidHandler` and `GuardedEnergyStorage`.
  - They forward every method 1:1, with `simulate` / `FluidAction` passed through unchanged.
  - Only insert/extract, fill/drain and receive/extract go through `ProxyGuard`.
  - Past the depth limit they accept nothing: the item insert returns the stack, and extract/drain return empty or 0.
- **Unchanged:** `ProxyGuard`, `TunnelCapabilities`, `EndpointCaches` and `CapabilityUpdates`. They're generic over `TransferKind`, so the seam held.

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

**B5 notes (done 2026-09-27; the dimension load is checked with B6/in game):**

- **Models:** `ModModelProvider` now extends NeoForge's `BlockStateProvider`, and item models go through `itemModels()`.
  - Machines: a multipart of the `cube_all` base plus the hand-written port overlays (`models().getExistingFile`), with conditions on `PortKind`. That's 19 parts.
  - Tunnel walls: `forAllStates` over `FACE` (36 variants).
  - Redstone tunnel walls: `forAllStates` over `FACE`/`MODE`/`POWERED` (144 variants).
  - `simpleBlockWithItem` for the room wall, and `simpleBlockItem` for the machine items.
  - Items: `basicItem` for the Shrinker and the two tunnels, `handheldItem` for the wrench.
- **`DataGenerators`:**
  - One `GatherDataEvent` on the mod bus (`bus = MOD`), with `generator.addProvider(include…, …)`.
  - The item tags need the block tags' `contentsGetter()`.
  - The tag providers take an `ExistingFileHelper`.
- **Recipes:** `ModRecipeProvider(PackOutput, CompletableFuture<Provider>)` with `buildRecipes(RecipeOutput)`, `ShapedRecipeBuilder` / `ShapelessRecipeBuilder`, and no `Runner`. The recipes are unchanged, including redstone dust instead of the ender pearl.
- **Loot:** `CopyComponentsFunction.copyComponents(Source.BLOCK_ENTITY)`.
- **Dimension type:** rewritten in the 1.21.1 flat format listed above.
- **Generated output:** `src/generated` was deleted and regenerated, 86 files.
  - Compared with 26.x: the `assets/*/items/*.json` item definitions are gone, replaced by `models/item/*` for all 11 items.
  - Recipes use the 1.21.1 `{"item": …}` / `{"tag": …}` ingredient objects.
  - Lang, loot tables, tags and block models are identical.
- **How it was run:** the GameTests didn't compile yet, so they were moved aside for the one `runData` run and put back straight after. Rerun `runData` after B6 to confirm nothing changes.

### B6: Tests on 1.21.1

1. Port the GameTests to the 1.21.1 format, including `RedstoneGameTests` (T1–T10 plus the shell-repair regression). Same cases; the transaction tests become simulate tests.
2. Manual mod matrix, the payoff of this branch:
   - **Create 6.0.8:** belts, funnels and chutes into and out of a machine; mechanical pump fluids. Create funnels and chutes push and pull every tick, so run them early against the `simulate`-based guards. Rotation is B8.
   - **Mekanism 10.7:** universal cables and a logistical transporter, pushing and pulling.
     - Chemicals (gas, infuse, pigment, slurry) stay out of scope. They use Mekanism's own capabilities, so they would be a new tunnel kind, not a test case.
     - Record whether players will want them, and decide after the matrix.
   - AE2 storage, import and export buses on a machine face.
   - Pipez, Storage Drawers, Refined Storage 2.
   - **Compact Machines 7 installed alongside**: both mods load, their dimensions don't collide, and a CM machine inside a Tiny Tunnels room works.

**Acceptance:** all Phase 6b cases pass, and the mod matrix shows no void, no dupe and no crash.

**B6 notes (GameTests done 2026-09-27; the manual mod matrix is still to do):**

- **Registration:** `TinyTunnelsGameTests` keeps the same name → function map. It's registered through `RegisterGameTestsEvent.register(TinyTunnelsGameTests.class)`, and a `@GameTestGenerator` builds one `TestFunction` per entry:
  - named `tinytunnels.<name>`
  - structure `tinytunnels:empty_5x5x5`
  - batch `tinytunnels`, required, same tick limits as before
  - The generated tests pass the `neoforge.enabledGameTestNamespaces=tinytunnels` filter because their structure is in the `tinytunnels` namespace.
- **Handler helpers:** `TestRooms.insert(IItemHandler, ItemStack)` (via `ItemHandlerHelper.insertItem`, executed) and `TestRooms.fill(IFluidHandler, FluidStack)`. The tests use the 1.21.1 capabilities and empty handlers (`EmptyItemHandler.INSTANCE`, `EmptyEnergyStorage.INSTANCE`).
- **`rollback` became `simulate_no_side_effects`:** a simulated insert of 5 reports everything accepted, and the chest inside stays empty. The plan's other simulate case, "remove the tunnel between the simulate and execute calls", isn't written yet; add it with the mod matrix if it's wanted.
- **Redstone test `click`:** follows 1.21.1's `ItemInteractionResult`. `PASS_TO_DEFAULT_BLOCK_INTERACTION` → `useWithoutItem`, then the item's `useOn`.
- **Result:** `runGameTestServer`: **all 30 required tests pass** on 1.21.1. That's the same 30 tests `main` registers; the 26.x run counts 31 because its test server runs one extra test that isn't ours.
- **Other checks:**
  - `runData` with the full mod gives exactly the B5 output (0 files written).
  - `./gradlew build` produces `tinytunnels-0.1.0+mc1.21.1.jar` with loader `[4,)`, NeoForge `[21.1.200,)` and Minecraft `[1.21.1]`.
  - The temporary `-Xmaxerrs` line in `build.gradle` has been removed.
- **Beds (added 2026-09-27, 1.21.1 only):**
  - The 1.21.1 dimension type can only turn beds off with `bed_works: false`, which makes them explode.
  - `room/RoomBeds` denies bed use inside rooms (`RightClickBlock` → `setUseBlock(FALSE)`, plus an action-bar message), unless the server config `roomBedsExplode` (default `false`) is on.
  - This matches 26.x, where beds simply don't work.
  - GameTest `bed_refused_in_room`, so the suite is now 31 tests.
  - `main` doesn't need it, because 26.x's `bed_rule` has `explodes: false`.
- **GameTest shutdown hang (found 2026-09-28, 1.21.1 only, test harness):**
  - **Symptom:** sometimes, after "All N required tests passed", the GameTest server never exits. It's stuck in `stopServer`'s wait-for-unloads loop: `ChunkMap.processUnloads` keeps re-queuing `scheduleUnload` for a chunk that isn't `isReadyForSaving()`, on the main thread that chunk still needs.
  - **Cause of the high rate:** the 1.21.1 GameTest server reuses the `level-name` world (`run/world`), which grew with every run (341 MB) and was shared with `runServer` and with `main`.
  - **Fix for that:** `build.gradle` gives `gameTestServer` its own `run/gametest/` directory and deletes its world before each run. The hang rate went from about 2 in 3 to 1 in 15.
  - **Ruled out:** the new cauldron tests, the test mods (Create, Mekanism and others), and releasing or freezing room tickets at shutdown. Both ticket changes made it worse.
  - **`main` (26.x):** 10 of 10 clean. 26.x resets its own `gametestserver/gametestworld`, and its chunk system is different.
  - **Debug:** `loading/ShutdownWatchdog` (`TODO(debug)`) logs the chunks still pending unload if a stop takes over 10 s. Remove it once a hang has been captured or ruled out.
  - **CI:** its checkout gives a fresh world anyway. Still, add a timeout to the GameTest step.
- **Still manual (needs the client), checklist in `tiny-tunnels-1-21-1-testing.md`:**
  - B1's "client launches" check
  - the B3 in-game checks (see the B3 notes)
  - the mod matrix above: Create 6.0.8, Mekanism 10.7, AE2, Pipez, Storage Drawers, and Compact Machines 7 alongside
  - Compact Machines 7 isn't in `build.gradle` yet; add it when running that step.

### B7: Release and maintenance

- Release on CurseForge and Modrinth as separate files per MC version, with a changelog that tags which versions a fix applies to.
- **Fix flow:** fix on `main` first, then cherry-pick. For a bug that exists only on 1.21.1, fix it there and note it. Keep a porting-debt list at the top of the 1.21.1 branch's changelog.
- **Feature policy:** 1.21.1 gets every gameplay feature. The **living miniature** (post-MVP rendering) is optional: its renderer uses the 26.x extract/submit model, so it would need a separate immediate-mode implementation on 1.21.1. Decide once it exists on 26.1.2.
- **End of life:** keep 1.21.1 maintained while it's the modpack consensus version. Freeze it (critical fixes only) once Create and Mekanism have been stable on 26.x for about a season.

### B8: Kinetic tunnel (Create rotation)

This comes after B7, once the 1.21.1 branch is stable. The full design is in `docs/plans/tiny-tunnels-kinetic-tunnel.md`:

- both ends swap speed, spare capacity and demand through a transient link
- Create is optional, through `compat/create`
- one kinetic face per machine to start

Summary of the work:

1. **Refactor, Create-free: done on `main` on 2026-09-27, before branching.** The machine logic is in `machine/MachineCore`, and every caller uses the `machine/MachineHost` interface. `mc1.21.1/dev` inherits both from the tag.
2. **Data, Create-free:** add `Room.kinetic` and `PortKind.KINETIC`. Also on both branches, so saves stay compatible.
3. **Compat:** `compat/create` on 1.21.1 only: the kinetic wall block and block entity, the kinetic machine block entity, the item, links and Jade lines.
4. **Tests:** GameTests K1–K11, then the in-game water-wheel pass.

**Acceptance:**

- A room with water wheels geared to a target RPM drives Create machines outside through an OUT tunnel.
- Overload stops both sides cleanly.
- The mod loads with Create removed.

## Effort estimate

| Phase | Size |
|---|---|
| B1–B2 | Small: toolchain and renames, about a day |
| B3 | Small to medium: about five files |
| B4 | Medium: three delegates plus the provider rewrite; the logic is unchanged |
| B5 | Small to medium: mostly datagen API differences |
| B6 | Medium: porting tests plus the manual mod matrix |
| B8 | Medium: compat blocks and block entities, link logic, 11 GameTests. The `MachineCore` refactor is already done. |

The seam rules in B0 are what keep this small. If transfer or persistence code spreads outside the seam files during the MVP, B3/B4 grow in proportion.
