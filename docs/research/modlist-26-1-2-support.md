# Modlist Support on 26.1.2 NeoForge

Checked 2026-09-25 against the Prism instance `1.21.1 Local` (32 jars).

**How it was checked:**
- Modrinth: file-hash lookup, then per-project version queries filtered to `loaders=neoforge`, `game_versions=26.1.2`.
- CurseForge: via cfwidget, for mods not on Modrinth.
- GitHub: port branches, for anything without a release.

## Summary

| Status | Jars |
|---|---|
| ✅ Released for 26.1.2 NeoForge | 16 |
| 🟡 Port in progress (26.x branch, no release yet) | 5 |
| ❌ No 26.x build or port found | 8 |
| 👤 Yours | 2 |
| ⚪ Can be dropped (nothing depends on it) | 1 |

Counting only mods with a release or an active port, **21 of 30 third-party jars** are covered or on their way. The big gaps are **Create and Mekanism**, and both are actively porting to 26.1.2 (details below).

## ✅ Released for 26.1.2 NeoForge

| Mod | 26.1.2 version | Channel | Date |
|---|---|---|---|
| Architectury API | 20.1.15+neoforge | release | 2026-09-18 |
| Balm | 26.1.2.15+neoforge-26.1.2 | release | 2026-09-25 |
| Better Days | 26.1.2-4.1.1.7-NEOFORGE | release | 2026-06-21 |
| Cable Facades | 2.1.4 | release | 2026-08-25 |
| CC: Tweaked | 1.120.0 | alpha | 2026-06-15 |
| Chunk Loaders | 1.2.9-neoforge-mc26.1 | release | 2026-07-04 |
| Easy Netherite Ingot | 1.4.0 | release | 2026-04-20 |
| Inventory Essentials | 26.1.2.6+neoforge-26.1.2 | release | 2026-09-25 |
| Jade | 26.1.11+neoforge | beta | 2026-09-11 |
| JEI | 29.43.0.105 | beta | — |
| JourneyMap | 26.1.2-6.0.9+neoforge | release | 2026-09-19 |
| More Red (CurseForge) | 26.1.2.3 | release | 2026-07-11 |
| Refined Storage | 3.2.1 (2.x → 3.x, expect changes) | release | 2026-06-07 |
| Storage Drawers | 26.1.0.2+neoforge | release | 2026-09-18 |
| SuperMartijn642's Config Lib | 1.1.8-neoforge-mc26.1 | release | — |
| SuperMartijn642's Core Lib | 1.1.24b-neoforge-mc26.1 | release | — |
| Inventory Management Deluxe  | inventorymanagement-neoforge-2.0.2+26.1 | release | — |

## 🟡 Port in progress

| Mod | Evidence | Notes |
|---|---|---|
| **Create** | Official status page: "26.1 — In Progress". Flywheel (Create's renderer) has an active `26.1.2/dev` branch, last commit 2026-09-25. | No ETA by policy. Ponder and Flywheel have no 26.1.2 release yet. |
| **Mekanism** | `26.1` branch at MC 26.1.2, NeoForge 26.1.2.74, mod version 10.8.0. Also `26.2`/`26.3` branches, pushed today. | No release on Modrinth or CurseForge yet. |
| **Mekanism Generators** | Same repo and branch as Mekanism. | Ships together with Mekanism. |
| Farmer's Delight | `26.1` branch at MC 26.1.2, mod version 1.3.0. | No release yet. |
| Building Gadgets 2 | Branch `21.6` at MC 26.1.2, mod version 1.4.6. | No release found. Your BG2 GUI depends on it. |

## ❌ No 26.x build or port found

| Mod | Notes |
|---|---|
| Immersive Engineering | Remove do not use at all! |
| RSInfinityBooster | Built against Refined Storage 2.x; RS 3.x on 26.1.2 may make it unnecessary or incompatible. |
| More Red x CC:Tweaked Compat | Both parent mods are on 26.1.2, so a port is plausible. |
| Smart Nether Star Recipe | Small recipe mod. A datapack could replace it. |
| Slime Recipe | Small recipe mod. A datapack could replace it. |
| BetterFly | 1.21.1 only. |
| Todo List (ENC_Euphony) | 1.21.1 only. |


## 👤 Yours

| Mod | Notes |
|---|---|
| Time Status | You'd port it. Probably small: rendering/GUI changes (`GuiGraphics` → `GuiGraphicsExtractor`, unobfuscated names, Java 25). |
| Building Gadgets 2 GUI | Blocked on Building Gadgets 2's 26.1.2 release, then the same kind of port. |

## ⚪ Can be dropped

- **Blueprint** (Team Abnormals library). No other jar in the instance depends on it. It's 1.21.x only anyway.

---

## Power mods on 26.1.2 NeoForge

You only use Mekanism for power. These already have 26.1.2 NeoForge builds:

| Mod | 26.1.2 version | Channel | What it is |
|---|---|---|---|
| **Energized Power** | 3.0.1+26.1.x | release (2026-09-20) | Tech mod with generators (solar, coal, heat), machines, cables and batteries. Closest "Mekanism-lite" option. |
| **Powah! (Rearchitected)** | 7.0.4-alpha | alpha (2026-05-02) | Pure power: reactors, thermo/solar generators, energy cells and cables. |
| **Oritech** | 2.0.0-exp6 | beta (2026-08-31) | Big animated tech mod with its own power generation. The largest Mekanism-scale alternative. |
| Applied Flux | 26.1-1.0.1 | release | Stores and moves FE through an AE2 network. |
| Modular Routers | 26.1.2.2 | release | Moves items, fluids and energy. |
| Pipez | 1.2.31+26.1.2 | beta | Simple pipes, including energy. Also good for testing tunnels. |

Not on 26.1.2 yet: Flux Networks, Industrial Foregoing, Extreme Reactors, Thermal Expansion, XNet and Actually Additions.

**Testing tunnels:** Energized Power or Powah covers FE generation, and Pipez covers item, fluid and energy transport. Mekanism can be added once 10.8.0 releases.

---

## Why Create is still on 1.21.1

Create doesn't follow every Minecraft version. It picks one long-lived version per era and skips the rest. From the official status page (https://wiki.createmod.net/users/development-status):

| MC | Create |
|---|---|
| 26.1 | **In progress** |
| 1.21.2 – 1.21.11 | Skipped |
| 1.21.1 | Continued support |
| 1.20.2 – 1.21.0 | Skipped |
| 1.20.1 | Available (6.0.8) |
| 1.19.4 | Skipped |
| 1.19.2 / 1.18.2 | Available (0.5.1) |

**Reasons:**

1. **Porting cost.** Create is one of the biggest mods and does unusual rendering: contraptions, kinetic block entities and Ponder, all on its own engine, Flywheel.
   - Rendering changed repeatedly across 1.21.x: `RenderPipeline` in 1.21.5, the render-state/submit split in 1.21.9, the `RenderType`/`BakedQuad` rework in 1.21.11.
   - 26.1 also dropped obfuscation.
   - Porting each intermediate version would have meant redoing Flywheel several times, so skipping to 26.1 means porting once.
2. **Modpack cycle.** Packs settle on one version for a long time (1.16.5, 1.18.2, 1.20.1, 1.21.1). Create picks the version packs will move to, and packs in turn wait for Create. That's also why Mekanism and packs still sit on 1.21.1.
3. **No ETAs by policy:** "We are not a game studio and progress on this project depends on many unpredictable factors."

**What it signals:** Flywheel's `26.1.2/dev` branch is active this week, and Mekanism's port branch also targets 26.1.2. Create's next version will very likely be **26.1.2**, the same target recommended in [platform-and-approach.md](platform-and-approach.md), and it's likely to become the next modpack version.

The unofficial "Create Fly" port (CC0, ~570K downloads) is **Fabric only**, so it doesn't help a NeoForge instance.

## Bottom line

- **Today:** a 26.1.2 version of your instance would lose Create, Mekanism, Farmer's Delight, Building Gadgets 2 and Immersive Engineering, plus a few small mods. That isn't playable for you yet.
- **Once Create and Mekanism release:** about 21 of 30 third-party jars would be covered. Only Immersive Engineering and a handful of small mods would be missing, and the recipe-tweak mods can be replaced with a datapack.
- **For Tiny Tunnels:** developing on 26.1.2 now lines up with where Create, Mekanism and AE2 are heading. Until they ship, test tunnels with Energized Power or Powah, Pipez, Storage Drawers, Refined Storage 3 and AE2.

## Sources

- Modrinth API (`/v2/version_files`, `/v2/version_files/update`, `/v2/project/{slug}/version`), queried 2026-09-25
- cfwidget (`api.cfwidget.com`) for CurseForge file lists
- Create status: https://wiki.createmod.net/users/development-status
- GitHub branches: `Creators-of-Create/Create`, `Engine-Room/Flywheel` (`26.1.2/dev`), `mekanism/Mekanism` (`26.1`), `vectorwing/FarmersDelight` (`26.1`), `Direwolf20-MC/BuildingGadgets2` (`21.6`), `BluSunrize/ImmersiveEngineering`
