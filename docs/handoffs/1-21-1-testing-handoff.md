# Tiny Tunnels 1.21.1 Testing Handoff

Written 2026-09-28, on branch `mc1.21.1/dev` (last commit `2a2200c buffered tunnels`). Read this first, then:

1. `docs/plans/tiny-tunnels-1-21-1-testing.md`: the in-game checklist being worked through
2. `docs/plans/tiny-tunnels-1-21-1-backport.md`: what changed per phase, B1–B6 notes
3. `docs/plans/tiny-tunnels-buffered-tunnels.md`: the feature added during testing
4. `docs/todo.md`: backlog

## Where things stand

| Area | State |
|---|---|
| Backport B1–B5: build files, renames, persistence/teleport/lifecycle/registration, transfer layer, datagen | Done |
| B6: GameTests on 1.21.1 | Done: **45 required tests pass** (`./gradlew runGameTestServer`) |
| B6: in-game checklist (`tiny-tunnels-1-21-1-testing.md`) | **In progress.** Sections 1–4 mostly done; section 3 was rewritten with a new 3b for buffered tunnels, now done; section 6 (mod matrix) mostly still to do |
| Buffered tunnels (BT1, BT2) | Done on 1.21.1 and **tested in game**: Pipez item and fluid pipe → tunnel → pipe, both directions, buckets, double sneak + wrench discard |
| Buffered tunnels on `main` (BT3) | Not started; queued in `docs/patches/to-main.md` |
| B8: kinetic tunnel (Create rotation) | Not started; `docs/plans/tiny-tunnels-kinetic-tunnel.md` |

**Confirmed in game on 1.21.1 so far:**
- items (hoppers, Pipez) and fluids (Pipez, Energized Power tanks) through tunnels
- buffered tunnels
- beds don't explode in rooms (`roomBedsExplode` config)
- redstone tunnels
- loading follows the machine, when the machine is away from spawn
- Create funnels were seen working inside a room

## What to test next

Continue in `tiny-tunnels-1-21-1-testing.md`. **Sections 1–4 are mostly done.**

1. ~~Section 3b, buffered tunnels~~: **done 2026-09-28.**
2. **Any unticked steps left in sections 2–4**, for example 2.8–2.11 (machine broken while you're inside, room copies, nested, explosions).
3. **Section 6a-inside:** Create factories inside a room feeding tunnel walls, including the small press/mixer factory and save/reload. That's the main use case. Where a Create funnel or pipe meets a Pipez pipe through a tunnel, use a buffered tunnel.
4. **Sections 6a, 6b, 6c, 6d:**
   - Create from outside (funnels, chutes, belts, pumps)
   - Mekanism (energy, logistical transporters push/pull, mechanical pipes)
   - AE2 buses
   - Pipez and Storage Drawers
   - the pipe loop
5. **Section 7:** save and reload, including a buffered tunnel with contents.

**Rules of thumb while testing:**
- **Pipe → tunnel → pipe for items or fluids needs a buffered tunnel.**
  - Empty-hand right-click on the tunnel wall inside cycles Pass-through → Buffered in → Buffered out.
  - The far pipe's arm at the tunnel must extract.
  - Energy works in pass-through.
  - A container directly against the tunnel works in pass-through.
- **Pipez extract:** sneak + right-click the pipe's **arm** with the Pipe Wrench, and a flange appears. Sneak-clicking the same arm again turns it off **and disconnects** it.
- **Energized Power Creative Fluid Tank:** on 1.21.1 a bucket right-click doesn't fill it. Open its GUI with an empty hand, then click the fluid bar with a bucket held on the cursor.
- **Spawn chunks:** a machine near world spawn never unloads on 1.21.1, which is vanilla behaviour. Test loading with a machine at least 300 blocks from spawn.
- **Server config** in dev is `run/config/tinytunnels-server.toml`: `roomBedsExplode`, `bufferItemSlots`, `bufferFluidCapacity`, and the shrink settings.
- Record results in the checklist's boxes and **Notes** section.

## Environment (1.21.1 branch)

- **Minecraft 1.21.1, NeoForge 21.1.217** (floor `[21.1.200,)`), **Java 21**, Parchment `2024.11.17`, ModDevGradle 2.0.147.
- **Test mods** (`localRuntime`, Modrinth version IDs in `build.gradle`):
  - Create 6.0.8 (bundles Flywheel, Ponder and Registrate)
  - Mekanism 10.7.19.85
  - AE2 19.2.17 with GuideME
  - Pipez 1.2.31, Energized Power 3.0.1, Storage Drawers 13.11.4
  - Jade 15.10.6
  - JEI 19.39.0.372 (newer JEI needs NeoForge 21.1.238)
- **GameTests** run in their own directory, `run/gametest/`, with a fresh world each run (deleted in `doFirst`). The old `run/world` (341 MB of test leftovers from both branches) is unused and can be deleted by hand.
- **Sources for API checks:** `build/moddev/artifacts/neoforge-21.1.217-sources.jar` (includes vanilla).
- **Branch model** (`docs/plans/tiny-tunnels-1-21-1-backport.md`):
  - `main` is the newest line (26.1.2).
  - `mc1.21.1/dev` is the backport.
  - Never merge between them. Changes move by copy, cherry-pick or by-hand redo, tracked in `docs/patches/to-main.md`.
  - Docs are kept identical with `scripts/sync-docs.sh`, run on the other branch after committing doc changes. It's one-way and warns if it would remove lines.

## Known issues (not blockers)

- **GameTest server shutdown hang** (1.21.1 only; the user decided to ignore it because it doesn't happen in game):
  - Sometimes after "All N required tests passed" the process never exits.
  - The cause is vanilla 1.21.1's stop loop spinning on a chunk that's still loading.
  - Kill the process; the test results are already in the log.
  - A CI timeout on the GameTest step is in the backlog.
- **Flaky GameTest `redstone_item_and_ports`:** it fails about 1 in 10 runs with a null `actual`, likely a timing race. In the backlog.
- **"Resource reload" error at the title screen:** Jade's dev-only check tripping on Pipez's missing translation `config.jade.plugin_pipez.pipe`. It isn't our code, and it's harmless.
- **JEI lookup-history error** about `tinytunnels:room_wall` in old worlds: the Room Wall item was removed, which is harmless.

## Temporary debug code (remove after testing; in the backlog)

- `/tinytunnels debug cap <x y z>`: for each tunnel face of the machine at that position, prints what a pipe sees (the handler class, tanks, simulated fill), the inside block, and the wall. The output also goes to the log as `[TT-DEBUG] cap`. It's useful while testing the mod matrix: if something doesn't flow, run it and read the log.
- `loading/ShutdownWatchdog`: logs chunks still pending unload if the server stop takes more than 10 s.
- `[TT-DEBUG]` log lines in `MachineCore` and `RoomTickets` (load, unload, tickets, occupancy).

## Code map: what's new on this branch

- `machine/MachineCore`, `MachineHost`: shared machine logic, so a Create kinetic block entity can host rooms later (B8).
- **Buffered tunnels:**
  - `room/TunnelMode`
  - `tunnel/TunnelBuffer`: an item stack handler plus a fluid tank, with one-way views and active push
  - `tunnel/TunnelBlockEntity` (buffer, ticker, `cycleMode`, `bufferEnd`) and `tunnel/TunnelWallBlock` (the `MODE` state, empty-hand and bucket clicks)
  - routing in `TunnelCapabilities.fromTunnel` and `MachineCore.insideCapability`
  - wrench removal in `TunnelWrenching`: hands items back, two-click fluid discard
  - Jade in `compat/jade`
  - GameTests in `gametest/BufferedTunnelGameTests`
- `room/RoomBeds` and the `roomBedsExplode` config: 1.21.1 only.
- `RoomBuilder.build` now leaves intact tunnels alone, so the mode and buffer survive the repair pass on entry.

## After the in-game pass

1. The backlog in `docs/todo.md`: CI timeout, the flaky test, debug cleanup.
2. The `main` queue (`docs/patches/to-main.md`):
   - BT3 buffered tunnels
   - the cauldron GameTests
   - the `MachineSize` codec
   - then `scripts/sync-docs.sh`
3. B8, the kinetic tunnel with Create 6.0.8.

## Working agreements (from the user)

- The user handles all git. Never commit, and never mention git status.
- A prompt ending in `?` is a question: answer it and change no files.
- When the user says stop, stop and wait.
- Test docs: checkbox steps with exact expected results, every command in its own code block.
- Plans go in `docs/plans/`, the plan name is the title. Todo items go in `docs/todo.md`.
