# Tiny Tunnels API Split Handoff

Written 2026-10-01, on branch `mc1.21.1/dev`. It replaces `1-21-1-testing-handoff.md` as the starting point. That file is still accurate for the 1.21.1 environment and testing notes, but not for what's next.

**If you're on the agent team, read `api-split-team.md` next.** It has the roles and the rules. Tasks are in `api-split-tasks.md`.

**Since A6.1 (2026-10-01) there's also `addons/create/`** (mod `tinytunnels_create`, its own version). Its runs load core, the addon and Create: `./gradlew :addons:create:runGameTestServer` (GameTests of both, in `run/gametest-create`) and `./gradlew :addons:create:runClient`. Since A6.4, core's runs (`./gradlew :core:runGameTestServer`, `run/gametest`; `./gradlew :core:runClient`) have no Create at all; `-PnoCreate` is gone. `./gradlew runGameTestServer` runs both, core first, then the addon (not in parallel: under the load of two servers, the known "room frozen after a fast re-place" issue showed up in `kinetic_machine_replaced`). Add `--continue` to run the addon's even if core's fails.

**Since A0 (2026-10-01), the mod's sources are under `core/`** (`core/src/...`, `core/build.gradle`). The paths below that start with a package folder are relative to `core/src/main/java/dev/thefern2/tinytunnels/`.

Read these, in order:

1. `docs/plans/tiny-tunnels-api-and-addons.md`: the plan. Core, a public API, addons; phases A1–A9.
2. `docs/plans/tiny-tunnels-api-design.md`: the agreed API design, with Java sketches and the six confirmed decisions.
3. `docs/plans/tiny-tunnels-kinetic-tunnel.md`: the kinetic tunnel as built inside core (K0–K1e). The "Findings from Create 6.0.8" table is required reading for anyone touching Create code.
4. `docs/plans/tiny-tunnels-kinetic-tunnel-testing.md`: the K2 in-game results and the known issues.
5. `docs/todo.md`: the backlog.

## Where things stand

| Area | State |
|---|---|
| 1.21.1 backport B1–B6 | Done. The in-game checklist (`tiny-tunnels-1-21-1-testing.md`) is mostly done. **Paused:** the Create sections 6a and 6a-inside, and the Create steps in section 7. |
| Buffered tunnels (BT1, BT2) | Done and tested in game. BT3 (port to `main`) is queued in `docs/patches/to-main.md`. |
| B8 kinetic tunnel, K0–K1e | **Built inside core** in `compat/create`. GameTests: **60 pass with Create, 48 with `-PnoCreate`.** |
| B8 kinetic tunnel, K2 in-game pass | **Done 2026-09-30.** All steps passed. K11 (without Create) was skipped until the API split. |
| API split, A1 design | **Agreed 2026-10-01** (`tiny-tunnels-api-design.md`, all six decisions confirmed). |
| API split, A1 code | **Not started.** The `api` package doesn't exist yet. It was about to be written when this handoff was asked for. |
| A2–A9 | Not started. |
| B7 (first public release) | Waits for A2–A6, so the first release already has the API and the new save format. |

## Next steps

1. **A0 (proposed in `api-split-team.md`): restructure into Gradle subprojects first.** Move the mod to `core/` and add an empty `addons/create/`, before anyone works in parallel, so nobody's paths move under them. This moves part of A6 earlier. The user hasn't confirmed it yet; ask.
2. **A1 code: write the `api` package** (`dev.thefern2.tinytunnels.api`), interfaces, records and events only, with Javadoc, following `tiny-tunnels-api-design.md`. Choices already made for it:
   - **`TunnelService.get()`** finds core's implementation with `ServiceLoader.load(TunnelService.class, TunnelService.class.getClassLoader())` (core ships `META-INF/services/...`). No public setter. Until A2 provides the implementation, `get()` throws "Tiny Tunnels isn't loaded".
   - **The kind registry object lives in the API** (`TunnelKinds.REGISTRY_KEY` and `TunnelKinds.REGISTRY`, built with `new RegistryBuilder<>(key).sync(true).create()`), so addons can look kinds up. Core registers it in `NewRegistryEvent` (A2). `sync(true)` means a client without an addon can't join a server that has it, which is right: the addon has blocks.
   - **`RoomView.isFaceUsed(face)`** counts tunnels of unknown kinds (their mod removed); `tunnels()` returns only known kinds.
   - **`FaceLook`** is a texture prefix; the per-face texture is `prefix + "_" + face`. Redstone's lit look is a second prefix.
   - Events extend `net.neoforged.bus.api.Event`; none can be cancelled.
   - Nullability: `org.jspecify.annotations.Nullable`, as in the rest of the code.
3. **Then A2 → A9** as in the plan's phase table.

## Environment

- **Minecraft 1.21.1, NeoForge 21.1.217**, Java 21, ModDevGradle 2.0.147, Parchment `2024.11.17`.
- **Create 6.0.8:**
  - `compileOnly` and `localRuntime` from Modrinth Maven (`maven.modrinth:create:88L641Un`).
  - **Ponder 1.0.64** on `compileOnly` from `maven.createmod.net`: `SmartBlockEntity` implements a Ponder interface, so compiling a Create block entity subclass needs it.
  - Create has no sources jar. Read its API with `javap` on the jar in the Gradle cache (`~/.gradle/caches/modules-2/files-2.1/maven.modrinth/create/88L641Un/`), unzipped into the session scratchpad.
- **Other test mods:** Pipez, Energized Power, Storage Drawers, Jade, AE2 with GuideME, JEI, Mekanism (versions in `build.gradle` and `1-21-1-testing-handoff.md`).
- **Commands.** Each command below goes in its own code block in test docs.

```
./gradlew build runGameTestServer
```

```
./gradlew :core:runGameTestServer
```

```
./gradlew :addons:create:runGameTestServer
```

```
./gradlew runData
```

```
./gradlew runClient
```

- **Run times:** a GameTest run takes about 25–35 s. On macOS there's no `timeout` command. Run long builds in the background and wait on the log for "required tests passed" or "failed".
- **GameTests run in `run/gametest/`.** Its world is deleted at the start of every run, so **two GameTest runs at once will break each other.**
- **Branch model:** `main` is 26.x, `mc1.21.1/dev` is the backport, and they never merge. Changes move by hand, tracked in `docs/patches/to-main.md`. Docs are synced with `scripts/sync-docs.sh`. The kinetic data entry in `to-main.md` is superseded by A9.

## Code map: kinetic tunnel inside core (moves to the addon in A6)

- **`compat/Compat.java`:** the `Compat.CREATE` flag. Create classes are only touched behind it, through `compat/create/CreateBlocks` (its signatures use no Create types).
- **`compat/create/`:**
  - `LinkedKineticBlockEntity`: the shared logic of both ends, the link math.
  - `KineticMachineBlock`, `KineticMachineBlockEntity`: the machine end. With Create, **both** machine block entity ids build it, so old machines load kinetic.
  - `CreateKineticTunnelWallBlock`, `KineticTunnelBlockEntity`: the wall end.
  - `KineticLinks`: the transient link state, keyed by room and wall.
  - `KineticTunnelItem`.
- **Create-free parts:**
  - `room/KineticTunnel`, `Room.kinetic`, the `RoomData` kinetic setters, `PortKind.KINETIC`
  - `tunnel/KineticTunnelWallBlock` (the inert wall), `tunnel/KineticTunnels` (flip, wrench)
  - `MachineBlock.applyPorts`, `RoomGeometry.wallCenter`
  - the `Check` step in `TunnelPlacement`
  - `TunnelWrenching` at `EventPriority.HIGHEST`: Create's own wrench handler runs at HIGH and would otherwise take the click
- **GameTests:**
  - `gametest/KineticGameTests` (Create only, 13 tests)
  - `gametest/KineticFallbackGameTests` (both runs, 3 tests)
  - `TinyTunnelsGameTests.Test` is package-private so the kinetic tests can add themselves
- **`build.gradle`:** (before A6.4) `-PnoCreate` left Create off the dev runtime. Since A6.4 Create is only in `addons/create`.

## Known issues (not blockers)

- **Room frozen after a very fast machine re-place:** a few ticks between pickup and placing. It's not reachable by hand; it may be reachable by contraptions or `/setblock`. The likely cause is in `RoomTickets`. Low priority; details in `tiny-tunnels-kinetic-tunnel-testing.md`, under Notes.
- **Create breaks a source forced against its direction.** For the machine, that drops it as an item, with its room kept. The port block in A6 moves this to a cheap port.
- **From before (still open):**
  - the GameTest server sometimes hangs on shutdown (1.21.1): kill it, the results are already in the log
  - the flaky `redstone_item_and_ports`
  - Jade's dev-only Pipez translation error
- **Temporary debug code and dev-world bridges: removed 2026-10-02.** The `[TT-DEBUG]` logs, `ShutdownWatchdog`, `/tinytunnels debug cap`, the `kinetic_machine` alias and `Room.CODEC`'s old-format readers are gone. Worlds from before that need starting fresh.

## Working agreements (from the user)

- **The user handles all git.** Never commit, and never mention git status, staging or committing.
- **A prompt ending in `?` is a question:** answer it and change no files.
- **When the user says stop, stop** and wait.
- **Stay on the question asked.** Don't wander into side investigations (for example chasing a flaky test) without saying so and asking first. A finding goes in the docs and a short report; the fix waits for a go.
- **Plans go in `docs/plans/`,** with the plan name as the title. Todo items go in `docs/todo.md`, and handoffs in `docs/handoffs/`.
- **Test docs:** checkbox steps with exact expected results, and every command in its own code block.
- **In-game testing is the user's.** Write the checklist, and record the user's results in it.
