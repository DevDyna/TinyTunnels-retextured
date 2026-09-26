# Tiny Tunnels Phase 1–5 In-Game Testing

Manual checks for Phases 1–5 of `tiny-tunnels-implementation.md`, to run before starting Phase 6 (tunnels). Tick each box as you go, and note anything odd under the step.

## Setup (once)

26.1.2 needs **Java 25**. IntelliJ was still on Java 21, which causes this error: `Unrecognized option: --sun-misc-unsafe-memory-access=allow`.

1. **Project SDK:** *File → Project Structure → Project → SDK → Add SDK → JDK…*, pointed at the JDK 25 Gradle already downloaded:
   `~/.gradle/jdks/eclipse_adoptium-25-aarch64-os_x.2/jdk-25.0.4.1+1/Contents/Home`
   Or use *Download JDK… → Eclipse Temurin 25*.
2. **Gradle JVM:** *Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JVM* → the same JDK 25 (or "Project SDK").
3. **Reload Gradle** (Gradle tool window → reload). ModDevGradle regenerates the **Client**, **Server**, **Data** and **GameTestServer** run configs.
4. **Delete stale run configs:** *Run → Edit Configurations…* → remove the four `MicroMachines: …` entries left over from before the rename.

Use the **Client** run config. Create a **Creative** world with **cheats on**.

> **2026-09-26:** room spacing changed from 2 to 4 chunks to fix a loading loop found in 5.2. Rooms in a world created before this fix are at their old positions, so **start a new world** and redo from section 3.

**Handy commands**

| Command | What it does |
|---|---|
| `/tinytunnels debug rooms` | Lists every room: ID (click it to get a machine for that room), grid slot, size, host position |
| `/tinytunnels debug tickets` | Lists rooms currently kept loaded by their machine |
| `/tinytunnels debug give <id>` | Gives a machine bound to that room |
| `/tinytunnels debug build <slot> <size>` | Builds a test room and teleports you in. **Use slots of 1000 or more**, because real rooms fill slots 0, 1, 2… and building over one resets it. Doesn't record a way back. |

**Getting a machine's coordinates:** look at it with F3 open. The right side shows *Targeted Block: X, Y, Z*. Or read it from `debug rooms`. Each line looks like:

```
<id>  #1  7x7  at 36, 64, 4  host: 28, 66, 31 in tinytunnels:rooms  tunnels: 0
```

- **`at …`** is the room's own corner, where its walls start in the room dimension. It's not a machine.
- **`host: X, Y, Z in <dimension>`** is where the machine bound to this room stands. Commands that target the machine need **both** the coordinates and that dimension, e.g. `/execute in <dimension> run …`. A machine in the overworld shows `in minecraft:overworld`. A machine placed inside another room shows `in tinytunnels:rooms`.

## 1. Skeleton

- [ ] The **Tiny Tunnels** creative tab lists six machines (Tiny 3×3 to Maximum 13×13), Shrinker, Tunnel, and Room Wall.
- [ ] Every entry has a texture (no purple/black checkerboard) and a proper name (no raw `block.tinytunnels…` keys).

## 2. Dimension and walls

1. Build a test room and go inside:
   ```
   /tinytunnels debug build 1000 7
   ```
   - [ ] You're in a lit room with 7×7 floor space and walls on every side.
2. Look outside the room in spectator mode, then switch back:
   ```
   /gamemode spectator
   ```
   ```
   /gamemode creative
   ```
   - [ ] The void outside has no stone platform, sky or weather.
3. Switch to survival, try to break a wall, then switch back:
   ```
   /gamemode survival
   ```
   ```
   /gamemode creative
   ```
   - [ ] The wall doesn't break.
4. A placed piston faces you, so set one with commands. Stand with your feet 2 blocks from the **north** wall (F3: "Facing: north"), then run these two commands, one after the other:
   ```
   /setblock ~ ~ ~-1 minecraft:piston[facing=north]
   ```
   ```
   /setblock ~ ~1 ~-1 minecraft:redstone_block
   ```
   - [ ] The piston stays retracted and the wall doesn't move.
5. Set difficulty to normal and wait 3 minutes in the room:
   ```
   /difficulty normal
   ```
   - [ ] No mobs spawn.
6. Place a bed and right-click it.
   - [ ] You can't sleep or set spawn.
7. Leave by right-clicking the air with the Shrinker. You land at world spawn with a "Couldn't find the way back" message. That's expected, because `debug build` doesn't record a way back.

## 3. Machine and room binding

1. Place a **Normal Machine** in the overworld, then list rooms:
   ```
   /tinytunnels debug rooms
   ```
   - [ ] A new 7×7 room is listed, with this machine's position as host.
   - [ ] Hovering the mouse over a machine item taken fresh from the creative tab shows a grey "New room" line under its name.
2. Machines only drop in survival, when broken with a pickaxe. Run these two commands, then break the machine:
   ```
   /gamemode survival
   ```
   ```
   /give @s minecraft:diamond_pickaxe
   ```
   - [ ] The machine drops, and its tooltip shows "Room xxxxxxxx".
   - [ ] `/tinytunnels debug rooms` shows that room with host "not placed".
3. Place the dropped machine somewhere else, list rooms again, then switch back to creative:
   ```
   /tinytunnels debug rooms
   ```
   ```
   /gamemode creative
   ```
   - [ ] Same room ID, with the new host position. No extra room appeared.
4. Leave that machine placed. List rooms, click the room's blue ID, and press Enter. Clicking fills in the give command for you:
   ```
   /tinytunnels debug rooms
   ```
   Try to place the machine you receive.
   - [ ] Placement is refused, and the action bar says where the room is already in use.
5. **No machine inside its own room.** Enter a room, list rooms, and click **that room's** ID to get a machine bound to it. Try to place it inside the room.
   ```
   /tinytunnels debug rooms
   ```
   - [ ] Placement is refused with "A machine can't go inside its own room".
6. Place one fresh machine of each size from the creative tab, then list rooms:
   ```
   /tinytunnels debug rooms
   ```
   - [ ] Six new rooms appear, sized 3, 5, 7, 9, 11 and 13.

## 4. Enter and exit

1. Hold the **Shrinker** and right-click a machine.
   - [ ] You shrink over about half a second, then appear in the middle of that room's floor at normal size.
2. Right-click the air with the Shrinker.
   - [ ] You're back exactly where you stood, facing the same way.
3. **Nesting.** Enter a room (A), place a fresh machine inside it, and enter that one (B). Right-click the air with the Shrinker twice.
   - [ ] The first exit puts you back in room A where you stood. The second puts you back in the overworld where you stood.
4. **Overworld machine broken while you're inside.**
   1. Note the machine's coordinates X Y Z (F3, or the `host:` part of `debug rooms`; see "Getting a machine's coordinates").
   2. Enter it with the Shrinker.
   3. While you're inside, nobody is near the machine, so its chunk is unloaded and a plain `setblock` fails with "That position is not loaded". Run these **three** commands in order, replacing X Y Z. `forceload` takes only X and Z.
      ```
      /execute in minecraft:overworld run forceload add X Z
      ```
      ```
      /execute in minecraft:overworld run setblock X Y Z minecraft:air destroy
      ```
      ```
      /execute in minecraft:overworld run forceload remove X Z
      ```
      `destroy` drops the machine as an item, as if mined.
   4. Right-click the air with the Shrinker.
   - [ ] You're back where you stood outside, and the dropped machine is on the ground where it was.
   - [ ] `/tinytunnels debug rooms` shows that room with host "not placed".
5. **Nested machine broken while you're inside.** Room B's machine stands inside room A, which is in the room dimension, so target that dimension. No `forceload` is needed, because room A is close enough to stay loaded while you're in B.
   1. Note B's machine coordinates: F3 while in room A, or B's line in `debug rooms`, which reads `host: X, Y, Z in tinytunnels:rooms`.
   2. Enter B.
   3. Run this **one** command, replacing X Y Z:
      ```
      /execute in tinytunnels:rooms run setblock X Y Z minecraft:air destroy
      ```
   4. Right-click the air with the Shrinker.
   - [ ] You're back in room A where you stood, and B's machine is on A's floor as an item.
6. **Relog.** Enter a room, then *Esc → Save and Quit to Title*, and reopen the world.
   - [ ] You're still in the room, at normal size.
   - [ ] The Shrinker takes you back out.
7. **Death.** Enter a room with the Shrinker, then run:
   ```
   /kill
   ```
   - [ ] You respawn normally.

   Then build a test room. It teleports you without recording a way back:
   ```
   /tinytunnels debug build 1001 5
   ```
   Right-click the air with the Shrinker.
   - [ ] You go to world spawn with the "Couldn't find the way back" message, which shows dying cleared the old way back.
8. **Disconnect mid-shrink.** Singleplayer pauses when Esc is open, which freezes the animation. Right-click a machine with the Shrinker and **immediately** press Esc, then *Save and Quit to Title*. Reopen the world.
   - [ ] You're outside the room at normal size. F3 shows your eye height is normal.

## 5. Follow-the-host loading

A room should run exactly while its machine's chunk is loaded.

1. Enter a room. Place a furnace with 1 coal and 8 raw iron (each takes 10 s). Exit, stand next to the machine, and run:
   ```
   /tinytunnels debug tickets
   ```
   - [ ] The room is listed.
   - [ ] After about 40 s, re-enter. The furnace has 3 or more iron ingots, so it kept smelting while you were outside.
2. Note the ingot count (N) and exit. Teleport far away:
   ```
   /tp @s ~3000 ~ ~
   ```
   Wait 60 s, then run:
   ```
   /tinytunnels debug tickets
   ```
   - [ ] The room is **not** listed.
3. Teleport back and check tickets:
   ```
   /tp @s ~-3000 ~ ~
   ```
   ```
   /tinytunnels debug tickets
   ```
   Immediately re-enter the room.
   - [ ] The room is listed again.
   - [ ] The furnace has at most N+1 ingots, so it paused while you were away. After another 20 s it has made more, so it resumed.
4. **Nested chain.** In room A, place a machine, enter it (room B), and put a burning furnace in B. Exit twice to the overworld, stand next to A's machine, and run:
   ```
   /tinytunnels debug tickets
   ```
   - [ ] Both A and B are listed.

   Teleport away, wait 60 s, and check:
   ```
   /tp @s ~3000 ~ ~
   ```
   ```
   /tinytunnels debug tickets
   ```
   - [ ] Neither is listed.

   Teleport back and check:
   ```
   /tp @s ~-3000 ~ ~
   ```
   ```
   /tinytunnels debug tickets
   ```
   - [ ] Both are listed again.

## 6. Persistence

1. *Save and Quit to Title*, then reopen the world and run:
   ```
   /tinytunnels debug rooms
   ```
   - [ ] The same rooms, IDs and hosts as before.
2. Stand next to a machine that has a furnace inside and run:
   ```
   /tinytunnels debug tickets
   ```
   - [ ] Its room is listed, and its furnace keeps working (repeat 5.1).
3. Check `run/logs/latest.log`.
   - [ ] No `ERROR` lines mentioning `tinytunnels`.

## Notes

Record failures here, with the step number and anything from the log (`run/logs/latest.log`).
