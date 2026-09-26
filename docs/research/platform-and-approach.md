# Tiny Tunnels R&D: Platform and Approach

Research date: 2026-09-25. This doc answers three questions:

1. Should we target a newer Minecraft/NeoForge than 1.21.1?
2. Is "machine = block linked to a room in another dimension" the best core design?
3. Can we also serve the miniature-building niche (Tiny Redstone, Smaller Units) without reinventing the wheel?

Items marked **(unconfirmed)** come from a single weak source or from memory. Check them before relying on them.

---

## TL;DR

- **Core design: keep the real room dimension.** It's the only approach where any modded machine works inside a room. It's also cheap when idle, and it has been proven for years by Compact Machines. Fake worlds inside a block entity (Smaller Units, Sable) are the fragile route: expect crashes with Mekanism, AE2, Lithium and shaders.
- **The market gap is exactly our mod.** Compact Machines 7 has had no tunnels since the 1.20 cycle. Its author has said a replacement will be built on NeoForge's new transfer API, but it's blocked on an open issue. Players currently use Entangled (All Rights Reserved) as a workaround.
- **Version: move to 26.x and build on the new transfer API.** Transactions are what passthrough tunnels need, and the old `IItemHandler` API was deleted in NeoForge 26.3. The catch: Create and Mekanism have no stable 26.x release yet, and the modpack consensus version is still 1.21.1.
- **Miniature: add it as rendering on top of real rooms, not as simulation.** The machine block can show a scaled snapshot of its room ("the block looks like a tiny version of what's inside"), and entering can play a shrink animation. That serves most of the miniature fantasy with none of the fake-world fragility. Tiny Redstone already covers self-simulated logic panels well, on 26.x too, so there's no reason to duplicate it.

---

## 1. Platform: which Minecraft/NeoForge

### Current state

Mojang switched to year-based versions after 1.21.11. Latest NeoForge per line (NeoForged maven, 2026-09-25):

| MC | NeoForge | Status | Notes |
|---|---|---|---|
| 1.21.1 | 21.1.251 | stable | Where most modpacks are. Old capability API. |
| 1.21.11 | 21.11.45 | stable | `ResourceLocation` renamed to `Identifier` |
| 26.1.2 | 26.1.2.109 | stable | Java 25, unobfuscated, new transfer API, old API deprecated |
| 26.2 | 26.2.0.88 | stable | |
| 26.3 | 26.3.0.22-beta | beta (~10 days) | **Old `items`/`energy` packages removed** (NeoForge #3489, 2026-09-16) |
| 26.4 | snapshots | — | **Vulkan becomes the default renderer** (26.4 snapshot 1, 2026-09-22) |

Sources: https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge, https://minecraft.wiki/w/Java_Edition_version_history, https://neoforged.net/news/26.1release/, https://www.minecraft.net/en-us/article/minecraft-26-4-snapshot-1

### The transfer API rework (NeoForge 21.9+)

Announced at https://neoforged.net/news/21.9-transfer-rework/.

- **Replacements:**
  - `IItemHandler` → `ResourceHandler<ItemResource>`
  - `IFluidHandler` → `ResourceHandler<FluidResource>`
  - `IEnergyStorage` → `EnergyHandler`
  - Capabilities are exposed as `Capabilities.Item.BLOCK`, `Capabilities.Fluid.BLOCK` and `Capabilities.Energy.BLOCK`.
- **Transactions replace `simulate`.** `try (var tx = Transaction.openRoot()) { ...; tx.commit(); }`. Nesting uses `Transaction.open(parent)`. Anything not committed rolls back.
- **Why it matters for us:** with `simulate`, a proxy chain (pipe → machine face → tunnel → inside block) ran twice, and the two runs could disagree. With transactions, the caller's transaction flows through every proxy and the whole chain commits or rolls back together.
- **Proxy helpers:** NeoForge ships `DelegatingResourceHandler`, `CombinedResourceHandler`, `EmptyResourceHandler` and others, which suit passthrough tunnels directly.
- **Still our job:** loop and recursion guards, and checks for unloaded endpoints. The new API doesn't handle either **(unconfirmed that NeoForge offers nothing here)**.
- **Deprecation timeline:** deprecated in 21.9–26.2, **removed in 26.3**. Code written against the 1.21.1 API is throwaway for anything past 26.2.

### Other API differences from our current plan

These affect `docs/plans/tiny-tunnels-implementation.md` if we move:

- `SavedData` → codec-based `SavedDataType(Identifier, factory, Codec, DataFixTypes)` with `computeIfAbsent(type)` (https://docs.neoforged.net/docs/datastorage/saveddata/).
- `DimensionTransition` → `TeleportTransition` **(unconfirmed which version)**. `ServerPlayer#teleportTo(ServerLevel, ...)` is probably still there, but check.
- Block entity renderers use the 1.21.9 split: `createRenderState` / `extractRenderState` / `submit(SubmitNodeCollector)`.
- Data-driven vanilla GameTests (1.21.5+). NeoForge's `GameTestHolder` no longer exists on 26.3.
- `TicketController` / `RegisterTicketControllersEvent` still exist on 26.3. Vanilla's ticket storage changed in 1.21.5, so check signatures.
- Data attachments exist on both 1.21.1 and 26.x, with built-in sync on newer versions.
- No runtime dimension creation API on either version. Our single static datapack dimension avoids the problem.
- Dimension-type JSON has changed across these versions **(unconfirmed details)**. Rewrite it per version.

### Availability of the mods we test tunnels against

Checked on Modrinth/GitHub, 2026-09-25.

| Mod | 1.21.1 | 26.x |
|---|---|---|
| Mekanism | 10.7.19 | 26.1/26.2/26.3 branches under active development, **no release** |
| Create | 6.0.10 | none |
| AE2 | yes | **26.1.2 betas** |
| Jade, JEI, Pipez | yes | up to 26.3 |
| Compact Machines | 7.0.81 | none |
| Tiny Redstone | yes | 26.1–26.3 |

Most of the pipe, tooltip and recipe mods we need are already on 26.x. Mekanism is the only must-have that's missing, and it's clearly on its way.

### Options

| Target | Pros | Cons |
|---|---|---|
| **Stay on 1.21.1** | Biggest audience today; Create and Mekanism available; our plan is already written for it | Tunnels built on the API being deleted; must be rewritten for any later port; competes head-on with CM7's home version |
| **26.1.2** (stable) | Transfer API with transactions; stable NeoForge; AE2 available; unobfuscated; Java 25 | Old API still present, so porting to 26.3 means removing any stray use; no Create or Mekanism releases yet |
| **26.3** (beta) | Old API already gone, so we can't accidentally use it; Mekanism is actively working on it | NeoForge beta for ~10 days; 26.4 with Vulkan right behind it |

**Recommendation: develop on 26.1.2 now and plan to ship on whichever 26.x line Mekanism and Create release on.**

- Our mod is mostly server logic: saved data, capabilities, tickets and teleports. Porting between 26.x minor versions should be mechanical.
- The one fragile area is rendering, and the miniature preview is a later phase.
- If reaching today's players matters more than a clean API, the fallback is 1.21.1 with the transfer code behind a small internal interface. Expect to rewrite that layer.

---

## 2. Core approach: is "a block linked to a room" the best?

Six approaches compared, from the techniques research:

| Approach | Performance | Modded machines inside | Effort | Fragility |
|---|---|---|---|---|
| **A. Real room in a dedicated dimension** | Near zero cost when idle | **All** | Medium | Low |
| B. Fake `Level` inside a block entity (Smaller Units, Sable, Create contraptions) | Poor at high detail | Poor | Very high | Very high |
| C. Scaled render of a real region | Fine if cached | n/a (client only) | Medium | Medium (GPU memory, shaders, Vulkan) |
| D. Shrink the player (`minecraft:scale`, 0.0625–16) | Fine | n/a | Low | Low (as cosmetics only) |
| E. Self-simulated voxel grid (Tiny Redstone) | Excellent | **None**, only our own parts | Medium–high per component | Low |
| F. Hybrids of A with C or D | Fine | All | Medium | Low–medium |

### Why A wins

- **Idle cost is tiny.**
  - An empty dimension's `ServerLevel` stops ticking entities 300 ticks after the last player and forced chunk leave. We verified this in the 1.21.1 `ServerLevel.tick` source.
  - All-air chunk sections skip random ticks.
  - The real cost is what players build inside, which they'd pay anywhere.
- **Every mod works.** Blocks sit in a real `ServerLevel` with real chunks. Nothing needs mixins or compat patches.
- **Proven.** Compact Machines, Dimensional Pockets II and PersonalDimension all use it.
- **Known edge:** a forced chunk is fully entity-ticking with no border, so entities at its edge can pull in neighbouring chunks (https://github.com/MinecraftForge/MinecraftForge/issues/5406). Our fix is already in the design: rooms fit inside one chunk with wall padding, and rooms are spaced two chunks apart.

### Why not B, even though it sounds like "miniature for free"

- **Smaller Units** is the most complete attempt (read on its `1.19.2-multiloader` branch).
  - It lazily creates fake `ServerLevel` and `ClientLevel` subclasses per region, with its own chunk cache, tick lists, save format and "networking hackery", plus heavy rendering mixins.
  - It never got past Forge 1.20.1 beta. It has 68 open issues and is incompatible with Mekanism, AE2, OptiFine shaders and Lithium-family mods.
  - https://github.com/GiantLuigi4/Smaller-Units (MIT)
- **Sable / Create Aeronautics** sub-levels: the README itself warns of "extensive compatibility issues". The license is PolyForm Shield, so its code can't be used in a competing mod. https://github.com/ryanhcode/sable
- **Create contraptions** crash when block entities call `setChanged()` inside the virtual world (Create #5034, #6381).
- This is years of compatibility whack-a-mole with no maintained library to lean on.

### Lessons from Compact Machines

- **Tunnels (CM4–6).** Robotgryphon removed them after repeated failures (https://github.com/CompactMods/CompactMachines/issues/609):
  - pipes wouldn't connect (Mekanism, Powah: #567, #587)
  - items were voided (#565)
  - AE2 storage buses couldn't see through tunnels (#531)
  - the chunk-loading buffers confused players
- **Our spec already avoids every one of these:**
  - no buffers, ever
  - capability caches with invalidation
  - no data returned from unloaded endpoints
  - on 26.x, transactions instead of `simulate`
  - AE2 testing needs to be added to the Phase 6 checks
- **Room preview leak (CM7).** GPU and off-heap buffers are never freed; a 45³ room is about 1 GB, and packed rooms have been reported at 50 GB+. It was fixed by a third-party mod (https://www.curseforge.com/minecraft/mc-mods/compact-machines-preview-fixer). Any rendering we do must free buffers deterministically.
- **What players ask for in CM issues:** tunnels (items, fluids, energy, redstone and Mekanism chemicals, since nuclear waste is the showcase case), AE2/RS connections, resizable or upgradable rooms, a safe preview, and machine sizes back.

**Answer: yes, keep the linked-room block as the core.** It's the best choice, not just a default.

---

## 3. Miniature: serving both niches

What the miniature players want is the feeling of "a whole working build inside one block". There are two ways to deliver that:

1. **Simulate tiny blocks** (B or E). B is fragile. E is solid but limited to parts we re-implement, and Tiny Redstone already does it well on 1.21.1 and 26.x (GPL-3.0, so we can read it but not copy it).
2. **Show a real room tiny** (C on top of A). All the simulation happens in the real room; the host block just displays it at 1/16 scale. A maximum-size room is 15 blocks across with walls, so at 1/16 scale it fits inside a single block face.

Option 2 is the one that fits our design.

### Tier 1: shrink-in animation (cheap, v1)

- On enter, lerp the vanilla `minecraft:scale` attribute from 1 to 0.0625 over about 10 ticks toward the machine, then teleport and reset the scale.
- It needs no dependency; Pehkui has no 1.21 NeoForge build anyway. It makes entering feel like shrinking into the block.
- Reach doesn't scale with it (`Player.blockInteractionRange()` ignores scale), which is fine because the effect is only cosmetic.

### Tier 2: "living miniature" on the machine block (headline feature, later phase)

- **Server:** when a room changes (a dirty counter bumped by block updates in that chunk), send a compressed block-state palette of its interior to players within about 16 blocks of the host. Rate-limit to one update every few seconds.
- **Client:**
  - mesh the snapshot once into a cached buffer per render type
  - draw it scaled inside the machine block's renderer
  - keep an LRU cache with a hard cap (e.g. 16 rooms)
  - free buffers on block entity removal, level unload, resource reload and cache eviction
- **Detail levels:** full detail up close, a colour-averaged voxel LOD further out, and nothing beyond a set distance. Config toggle to turn it off.
- **Limits:**
  - static block models only: no block entity renderers or entities inside the miniature, which avoids fake-world problems
  - animations appear as snapshots
- **Risks:**
  - the 26.x render-state/submit model
  - Vulkan becomes the default in 26.4, so custom GPU-buffer code is exposed
  - Iris shader packs **(unconfirmed)**
- **Mitigation:** first try submitting cached quads through vanilla `RenderType`s via `SubmitNodeCollector` rather than managing raw GPU buffers. Measure before optimizing.
- **Borrowing:** Create's Ponder (MIT, separate repo, https://github.com/Creators-of-Create/Ponder) and Compact Machines (MIT) are fine to read and borrow from. **Unconfirmed** whether Ponder's virtual level works as a clean dependency.
- **What makes it different:** nobody on modern NeoForge offers "every mod works inside, and you can see it working from outside". MiniScaled did the see-it part with Immersive Portals, but it's Fabric-only and at most 1.20.4 (Apache-2.0).

### Tier 3: things to skip or defer

- **Walking into a truly tiny real region** (shrink the player, no teleport). Vanilla has no sub-block geometry, so this needs approach B. Skip.
- **Our own logic panels (E).** They would duplicate Tiny Redstone. Only reconsider if a specific component set would complement tunnels (for example, a tiny "I/O routing" panel as the room's control surface).
- **Clicking the miniature to interact with the room remotely.** Interesting, but deferred.

---

## 4. Proposed changes to the plan

If the 26.1.2 recommendation is accepted:

1. Toolchain: Java 25, MDG for 26.1.2 (the official 26.1 MDK), NeoForge 26.1.2.x.
2. Rewrite the "Verified API signatures" section against 26.1.2 sources: `Identifier`, `SavedDataType`, `TeleportTransition`, `TicketController`, the transfer API classes, and BER render state.
3. Phase 6 tunnels: `ResourceHandler<ItemResource>`, `ResourceHandler<FluidResource>` and `EnergyHandler` built on `DelegatingResourceHandler`. Keep the recursion guard. Remove the `simulate` notes. Add an AE2 storage-bus check to acceptance.
4. Phase 4: add the scale-lerp shrink animation (Tier 1).
5. New phase after tunnels: the living miniature (Tier 2), starting with a spike to measure mesh cost, the submit path on 26.x, and Iris behaviour.
6. Test setup: AE2 26.1.2 beta and Pipez now; Mekanism once it releases for our line.

## Sources (licenses)

| Can borrow | Read only (copying makes us GPL) | Avoid |
|---|---|---|
| Compact Machines (MIT), Compact Crafting (MIT), Smaller Units (MIT), Chisels & Bits (MIT), Pehkui (MIT), MiniScaled (Apache-2.0), Ponder (MIT) | Tiny Redstone (GPL-3.0) | Dimensional Pockets II (ARR), Entangled (ARR), Sable (PolyForm Shield) |

LittleTiles, FramedBlocks and Valkyrien Skies 2 are LGPL-3.0: reuse is possible, but that code has to stay LGPL.
