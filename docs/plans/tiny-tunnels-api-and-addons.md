# Tiny Tunnels API and Addons Plan

Split Tiny Tunnels into a **core mod** and **addons**:

- **Core:** mostly vanilla things. Machines, rooms, the Shrinker, item, fluid and energy tunnels (pass-through and buffered), redstone tunnels, and the wrench.
- **Public API**, shipped inside core, so anyone can add a tunnel kind for their mod: "here's the API, go at it".
- **Addons:** tunnel kinds that depend on another mod. The first is the **Create addon** (the kinetic tunnel). Mekanism chemicals (phase 8) would be the second.

**Status (2026-09-30):** planned. K2, the in-game pass of the kinetic tunnel as it is now, is **done** (`tiny-tunnels-kinetic-tunnel-testing.md`): the link math (speed, stress, overload with water wheels) is confirmed, and it moves to the addon unchanged. A1's design on paper is agreed (`tiny-tunnels-api-design.md`, 2026-10-01).

**Status (2026-10-01):** the API split team is working (`docs/handoffs/api-split-team.md`, tasks in `docs/handoffs/api-split-tasks.md`). The user agreed to **A0 first**: the Gradle subprojects (`core/`, empty `addons/create/`) before parallel work. A0, A1 and **A2 are done** (GameTests 62 with Create, 50 without). **A3 is done** too, with its in-game pass (GameTests 62 with Create, 51 without). **A4 and A5 are done.** **A6 and A7 are done** (2026-10-02), each with its in-game pass (GameTests: core 56, core + addon 80). Next: A8 (publishing).

## Why

- **Dependencies follow the split.** Core runs on plain NeoForge. The Create addon depends on Create and gets released when Create does. `main` (26.x) has no Create today, so the addon only exists on 1.21.1, and core carries no dead code.
- **Other mods can add tunnel kinds** without forking. Our own addons are built on the same public API, so it stays usable.
- **It fixes the kinetic design's weak spots** (see "The port block"):
  - there are no Create classes on the machine
  - more than one kinetic tunnel per machine becomes possible
  - the old-machine refusal goes away

## Decisions

| Question | Decision |
|---|---|
| What's in core | Machines, rooms, loading, the Shrinker, item, fluid and energy tunnels, redstone tunnels, the wrench, Jade for those. Redstone stays in core: it's vanilla, so an addon would add a jar and a release for nothing. |
| Where the API lives | A package in core, `dev.thefern2.tinytunnels.api`, published with the core jar. **No separate library mod**: it would be one more jar for players to install. |
| How addons depend on core | `compileOnly` on the core API jar, and a `required` dependency on `tinytunnels` in `neoforge.mods.toml`. |
| Core's own tunnels | Registered through the same API as addons. Items/fluids/energy and redstone are the first two kinds, which keeps the API honest. |
| Kinetic machine end | A **port block** owned by the addon, against the outside of the machine face. The machine block stays plain. |
| Save format | Moves from the hard-coded `tunnels`, `redstone` and `kinetic` fields to one map of tunnels by kind. **Done before the first public release**, so there's no data fixer. |
| Timing | Design and core work **before the first public release (B7)**. K2 first. |

## The port block

```
      ROOM (inside)                              WORLD (outside)
  motor ─ shaft ─▶ [kinetic wall]  ══link══  [machine │ port] ─▶ shaft ─ press
                    addon block                core     addon block
                    + block entity             block    + block entity
                    (Create kinetic)           (plain)  (Create kinetic)
```

- **What it is:** a thin plate, like a flange, hugging the machine face, with a shaft stub pointing out. It occupies the block space next to the face, but its model is a few pixels deep, so it reads as part of the machine. It shows the face letter in brass.
- **Attachment:** it only survives against a machine, the way a button needs a wall (`canSurvive`). When the machine is picked up, the port pops off as an item.
- **Linking:** each tick the port asks the API which machine is behind it, and whether its room has a kinetic tunnel on this face. If yes, it's that tunnel's outside end. If not, it doesn't turn, and Jade and the goggles say "not linked".
- **The rest is unchanged from the kinetic tunnel plan:**
  - The port and the wall are the two ends. They swap speed, spare capacity and demand every tick through the link, keyed by room and wall.
  - `LinkedKineticBlockEntity` moves to the addon almost as is. The machine end's `link()` becomes the port's.
  - The speed sign rule still applies. The port's shaft points out along the face, as the machine's does today.
- **Placement:** the player places ports; the mod never puts blocks in the world by itself, which could overwrite something.
- **Wrench moving a tunnel to another face:** the port on the old face unlinks. The player moves it.

**Gains:**
- **No Create classes on core blocks.** This removes `KineticMachineBlock`, `KineticMachineBlockEntity`, the `kinetic_machine` block entity id, `MachineBlock.applyPorts` and the old-machine refusal.
- **More than one kinetic tunnel per machine.** Each port is its own block entity and network, so the one-per-machine rule can go. Check that two ports on one machine can't form a loop back into the same room.
- **The addon can be removed cleanly.** Ports vanish (unknown blocks become air), and core keeps the room and the tunnel entry.
- **An opposite spin breaks the port, not the machine.** Create breaks any source forced against its direction. Today that pops the whole machine off (seen in K2 step 13, 2026-09-30). With the port as the source, it's a cheap port.

**Costs:**
- The port takes the block space in front of the face.
- It's one more item to craft and place.

## The API

The first cut is kept small, and grows only when a real addon needs more. Everything under `api` is public and versioned; everything else in core is internal.

| Part | Contents |
|---|---|
| **Tunnel kinds** | A registry of `TunnelKind`s (NeoForge custom registry). Each kind supplies: its id; a codec for its per-tunnel data (e.g. IN/OUT); its wall block and the state to build for a face, inward direction and data (used by placement and shell repair); a placement check; hooks for the wrench (cycle, remove, item returned) and the empty-hand click; and its face look on the machine. |
| **Queries** | The machine at a position (`MachineHost`, moved into the API); a machine's room; a room's tunnels by face and kind; a tunnel's wall position and data; a room's host; a room's geometry (`inwardNormal`, `wallCenter`, shell checks). |
| **Changes** | Add, move and remove a tunnel, and change its data, through one service (today `RoomData` setters and `TunnelPlacement`). Core does the room-data update, shell protection, face sync and capability refresh. |
| **Events** | Tunnel added, moved, removed and data changed; machine placed and removed; room loaded and unloaded. Posted on the NeoForge game bus. |
| **Guarantees** | A room ticks exactly while its machine ticks (`RoomTickets`). Tunnel walls are unbreakable, and the shell repair puts them back. |
| **Transfer** | Core keeps items, fluids and energy. An addon for another capability (e.g. Mekanism chemicals) gets a helper that resolves "the block on the other side of this tunnel", like `TunnelCapabilities.fromTunnel`, and adds its own capability. |

**Face looks without `PortKind`.** `PortKind` is an enum in the machine's block states, and addons can't add values. The A1 draft proposes looks synced from the machine's block entity and drawn through `ModelData`, with one `signal` block state kept for redstone. A fixed set of generic looks in the block state is the fallback. See "Machine face looks" in `tiny-tunnels-api-design.md`.

**Unknown kinds.** When core loads a room with a tunnel of a kind it doesn't know (its addon was removed), it keeps the entry as raw data and places a generic inert tunnel wall. When the addon comes back, the entry decodes again and the shell repair rebuilds the addon's wall.

**Versioning:** see "How other mods use the API".

## Phases

Each phase ends with `./gradlew build runGameTestServer` green on `mc1.21.1/dev`. Until A6.4, core phases were also run with `-PnoCreate`; since then `:core:runGameTestServer` is core alone and `:addons:create:runGameTestServer` is core plus the addon.

| Phase | Work | Done when |
|---|---|---|
| **K2** | In-game pass of the kinetic tunnel as it is now (`tiny-tunnels-kinetic-tunnel-testing.md`). | The link math is confirmed in game, including overload with water wheels. Findings go in that doc. |
| **A0** | **Gradle subprojects** (moved earlier from A6, agreed 2026-10-01). `src/` moves to `core/src/`; `settings.gradle` includes `core` and an empty `addons:create`. Runs keep the root `run/`. Done first and alone, so no one's paths move during parallel work. | `./gradlew build` passes and the GameTest counts are unchanged, with and without Create. **Done 2026-10-01:** 60 and 48 pass. |
| **A1** | **API design.** On paper first (`tiny-tunnels-api-design.md`): `TunnelKind`, the queries, the change service, the events. Review it against both core kinds and the Create addon before any code moves. Then write the `api` package as interfaces and records only, with Javadoc. | The two core kinds and the kinetic kind can each be written against it on paper. **Design agreed 2026-10-01** (all six decisions confirmed). **Code written 2026-10-01:** the `api` package, interfaces, records and events with Javadoc; nothing implements it until A2. |
| **A2** | **Tunnel-kind registry in core.** Register items/fluids/energy and redstone as kinds. `Room` keeps one map, face to (kind, wall position, data), with a dispatch codec. `RoomData`, `RoomBuilder`, `ShellProtection`, `TunnelWrenching`, `TunnelPlacement` and Jade go through the kinds. | All core GameTests pass unchanged. Dev worlds: `Room.CODEC` still reads the old `tunnels`/`redstone` fields once (cheap to keep until release). **Done 2026-10-01:** the kinds `tinytunnels:transfer`, `redstone` and a temporary Create-free `kinetic` (replaced in A6); one tunnel map saved as `tunnels`; every change through `TunnelChanges`; `TunnelService` found through `ServiceLoader`. GameTests 62 / 50 (one new test, plus `api_service_found`). |
| **A3** | **Face looks.** Replace `PortKind` with generic port looks plus kind textures. | Letters look as they do now for both core kinds. **Done 2026-10-01:** looks synced from the machine block entity and drawn through `ModelData`; the machine has only `signal` (plus Create-only `kinetic_face` until A6); in-game pass in `tiny-tunnels-face-looks-testing.md`. Also from that pass: the up face is navy (yellow marks were invisible on it), and redstone IN walls have marks. |
| **A4** | **Events and queries** implemented. `MachineHost` stays internal (design decision 6); `MachineView` is its public face. The tunnel events and the queries already came with A2.5; A4 adds the machine and room events. | Core's own code (capability refresh, redstone updates) uses them where it can. **Done 2026-10-01:** machine and room events posted; GameTest `api_events` checks all of them in order. GameTests 63 / 52. |
| **A5** | **Unknown kinds.** Raw entries and the generic inert wall. | A GameTest registers a test kind, saves, then loads without it: the room keeps the entry, the shell has no hole, and the entry comes back when the kind is there again. **Done 2026-10-01:** raw entries kept and saved back unchanged; `tinytunnels:unknown_tunnel_wall` fills the shell; the wrench can't move or remove them. GameTests 66 / 55. |
| **A6** | **Create addon.** Fill the `addons/create` subproject made in A0 (see "Repository layout"). Move `compat/create` and the kinetic GameTests there. Add the port block and its item, recipe and model. Remove everything Create from core: `KineticMachineBlock`, `KineticMachineBlockEntity`, `kinetic_machine`, `applyPorts`, `Compat.CREATE`, the Ponder and Create `compileOnly` lines, `-PnoCreate`. | The kinetic GameTests pass against the port. Core has no Create reference left. Core's GameTests pass with and without the addon on the classpath. **Built 2026-10-01:** addon `tinytunnels_create` (own version `0.1.0+mc1.21.1`), kinetic kind `tinytunnels_create:kinetic`, the port block, its own Jade plugin; core has no Create code (two dev-world bridges, to remove before release: the `kinetic_machine` → `machine` alias, and old `kinetic` entries read as unknown). API additions: `wallRemoved`, `placedMessage`, `CoreIds`. GameTests: core 56, addon run 73 (core's count dropped on purpose: kinetic tests moved to the addon). **Done 2026-10-02:** the user's in-game pass (`tiny-tunnels-create-addon-testing.md`) is complete, after three fixes it found: the port's shaft didn't turn (A6.7), a lowered speed stuck after a load (A6.8), and a hole instead of the unknown wall on loading straight into a room (A6.9). |
| **A7** | **Multiple kinetic tunnels per machine.** Drop the one-per-machine rule, and check networks can't loop. | A GameTest with two ports on one machine, each driven from its own wall. **Done 2026-10-02:** up to 6 kinetic tunnels per machine; a loop guard between networks (a link that would feed its own source is blocked, capacity is counted once, a source loop with no real generator can't survive); the stress share rule. In game, closing a loop through tunnels breaks the last piece, as in plain Create. GameTests: core 56, addon run 80. |
| **A8** | **Publishing** (see "Publishing"). A release workflow that uploads core and the Create addon to Modrinth and CurseForge. API docs: Javadoc, plus a short "write a tunnel kind" guide pointing at the Create addon. The import check for in-repo addons. | An addon outside this repo can depend on core from Modrinth Maven and register a kind. |
| **A9** | **Port to `main` (26.x).** Core and the API only; the Create addon waits for Create on 26.x. Tracked in `docs/patches/to-main.md`. | `main` has the same API; its GameTests pass. |

## Repository layout

**One repository, Gradle subprojects** (decided 2026-10-01). There's no separate API repo or library mod: the API is a package inside core.

```
TinyTunnels/                      (one repo, one branch per Minecraft version, as now)
├── settings.gradle               include 'core', 'addons:create'
├── core/                         the mod players install: tinytunnels-<ver>.jar
│   └── src/main/java/dev/thefern2/tinytunnels/
│       ├── api/                  ← the public API (interfaces, records, events)
│       └── machine/ room/ tunnel/ loading/ ...   (internal)
├── addons/
│   └── create/                   tinytunnels_create-<ver>.jar (the kinetic tunnel and port)
│       └── src/main/java/dev/thefern2/tinytunnels/create/
└── docs/
```

- **Core and its API ship as one jar.** Players install only `tinytunnels`.
- **Our addons live in the same repo.** An API change and its use in the Create addon land in the same commit, and the GameTests catch a break right away. In-repo addons depend on `project(':core')`, never on Maven.
- **Branches stay as they are:** `main` (26.x) and `mc1.21.1/dev`. Each one carries core plus the addons that exist for that Minecraft version. The Create addon on `main` waits for Create on 26.x.
- **Our addons use only `api`.** The Create addon compiles against core's `api` package and nothing else. A small Gradle check (or an ArchUnit-style test) fails the build if it imports anything outside `api`, which catches leaks before a third party hits them. **Exception (decided 2026-10-02):** the addon's GameTests (`create.gametest`) may use core internals to build rooms and machines, since the API has no "place a machine" call. They're only loaded when GameTests are on, so the shipped addon never links to them in play. The A8 check excludes that package.
- **Alternative, later:** a separate repository per addon. It's cleaner for outside contributors, but every API change becomes a cross-repository release. Revisit after 1.0.

## How other mods use the API

A third-party addon depends on core the way we depend on Jade and Create today:

```gradle
// their build.gradle
dependencies {
    compileOnly  "maven.modrinth:tiny-tunnels:<version id>"   // compile against core (the API is inside)
    localRuntime "maven.modrinth:tiny-tunnels:<version id>"   // and run it in their dev client
}
```

```toml
# their neoforge.mods.toml
[[dependencies.their_mod]]
    modId="tinytunnels"
    type="required"
    versionRange="[0.2,0.3)"      # the API versions they support
    ordering="AFTER"
    side="BOTH"
```

```java
// register a kind on our registry, then use the service at runtime
public static final DeferredRegister<TunnelKind<?>> KINDS = DeferredRegister.create(TunnelKinds.REGISTRY_KEY, "their_mod");
public static final DeferredHolder<TunnelKind<?>, ChemicalTunnelKind> CHEMICAL = KINDS.register("chemical", ChemicalTunnelKind::new);

TunnelService.get().machineAt(level, pos).flatMap(MachineView::hostedRoom) ...
```

- **Only `dev.thefern2.tinytunnels.api.*` is public.** Everything else in the jar is internal and may change at any release. A note at the top of each internal package says so.
- **Versioning:** before 1.0, a breaking API change bumps the minor version (0.2 → 0.3); after 1.0, the major version. The API version is core's version. Addons declare the range they support, so a mismatch fails at load with a clear message instead of crashing.
- **The Create addon doubles as the example.** It's a real kind built only on `api`, and the "write a tunnel kind" guide (A8) points at it.

## Publishing

**Both Modrinth and CurseForge**, the same jar on each, from one CI job.

| Where | For players | For modders depending on us |
|---|---|---|
| **Modrinth** | Mod page and downloads. | **Modrinth Maven** (`api.modrinth.com/maven`, `maven.modrinth:tiny-tunnels:<version id>`). Nothing to set up; it's already how mods pull Jade, Create and AE2. **The documented way.** |
| **CurseForge** | Mod page and downloads; the CurseForge app and most modpacks. | **Curse Maven** (`cursemaven.com`, `curse.maven:tiny-tunnels-<project id>:<file id>`). A community service, widely used; documented as a second option. |

- **CI:** a release workflow on a version tag builds the jars and uploads them to both sites. Either use the `mc-publish` GitHub Action, which does Modrinth and CurseForge in one step, or the Minotaur (Modrinth) and CurseGradle (CurseForge) Gradle plugins. API tokens go in repository secrets.
- **Each upload declares:** the Minecraft and NeoForge versions, the loader, and the dependencies. The Create addon gets a required dependency on Tiny Tunnels and on Create, as relations on each site.
- **One project per mod on each site:** "Tiny Tunnels", and "Tiny Tunnels: Create" for the addon. Each site lists the addon as depending on core.
- **The same version string everywhere,** for example `0.2.0+mc1.21.1`, so a bug report names one build whichever site it came from.
- **Later, if modders ask:** a separate `-api` jar (an `api` source set) on our own Maven, a smaller compile dependency with no internals visible. Not needed at first.

## Testing

- **Core:** today's GameTests, plus a test kind registered only in GameTests (A5) to exercise the API the way an outside addon would.
- **Create addon:** the kinetic GameTests (K1–K13), rewritten for the port where the machine end matters. In-game: `tiny-tunnels-kinetic-tunnel-testing.md`, updated for the port.
- **Without the addon:** core's GameTests with the addon off the classpath, and the unknown-kind test.

## Open questions

- ~~Port look and size~~ **Decided 2026-10-01:** keep it simple for now (a thin flange plate with a shaft stub, placeholder textures); better textures later.
- ~~Addon id and name~~ **Decided 2026-10-01, confirmed by the user 2026-10-02:** `tinytunnels_create`, "Tiny Tunnels: Create". The port recipe (shaft + andesite casing + copper ingot, shapeless) is kept too.
- ~~Addon versioning~~ **Decided 2026-10-01:** its own version, starting at `0.1.0+mc1.21.1`, with a required dependency range on core.
- **Should core have an item/fluid/energy API for other capabilities**, or only the "block on the other side" helper? Decide when the Mekanism chemicals addon is designed.
- **Contraptions:** what happens when a Create contraption moves a machine or a port? Out of scope for A6, but note what happens in K2 if it comes up.

## Relation to other plans

- `tiny-tunnels-kinetic-tunnel.md`: K0–K1e built the kinetic tunnel inside core. This plan moves it into the addon and replaces the kinetic machine with the port block. The link, stress and sign design carry over.
- `tiny-tunnels-1-21-1-backport.md`: B7 (release) waits for A2–A6, so the first public release already has the API and the new save format.
- `docs/patches/to-main.md`: the kinetic data entry queued on 2026-09-29 is replaced by A9 (core and API to `main`).
