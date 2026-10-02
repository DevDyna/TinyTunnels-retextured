# Tiny Tunnels 1.21.1 In-Game Testing

Manual checks for the 1.21.1 backport (`tiny-tunnels-1-21-1-backport.md`, B1–B6), on branch `mc1.21.1/dev`.

- **Already automated:** the 30 GameTests pass on 1.21.1 (`./gradlew runGameTestServer`). They cover tunnel routing on every face, hoppers, fluids, nesting, the depth limit, shell repair, room loading, redstone and entry points.
- **What this pass covers:** what the GameTests can't. That's how things look, clicks, the room dimension, save and reload, and above all other mods' pipes and machines against the 1.21.1 `simulate`-based tunnels.
- Tick each box, and note anything odd in **Notes** at the bottom.

## Setup

- **Run config:** use the **Client** run config. On this branch it loads these test mods:
  - Create 6.0.8, Mekanism 10.7, AE2 (with GuideME)
  - Pipez, Energized Power, Storage Drawers
  - Jade and JEI
- **World:** a **new Creative world** with cheats on. Worlds from `main` (26.1.2) can't be opened on 1.21.1.
- **Finding items:** JEI is the quickest way to other mods' items. Search by name, or `@create`, `@mekanism`, `@ae2`.
- **Tiny Tunnels items:** get them from the creative tab, or run these commands:
  ```
  /give @s tinytunnels:normal_machine 4
  ```
  ```
  /give @s tinytunnels:tunnel 12
  ```
  ```
  /give @s tinytunnels:redstone_tunnel 4
  ```
  ```
  /give @s tinytunnels:tunnel_wrench
  ```
  ```
  /give @s tinytunnels:shrinker
  ```
- **How to count items:** for "nothing lost, nothing duplicated", start with a known number (for example 64 cobblestone). Check that the total across source and destination stays 64. Jade shows container contents when you look at them.
- **Same test blocks as 26.x:** fluid and energy steps use the same Energized Power blocks as `tiny-tunnels-phase-6a-testing.md`, so results compare directly: Creative Fluid Tank and Fluid Tank (Small), Creative Battery Box and Battery Box.
- **Pipez extraction:** Pipez pipes only push into blocks. To pull from a block, set that pipe connection to **extract** (sneak + right-click the connection with the **Pipe Wrench**, or use the pipe's GUI).
  - **Pipe → tunnel → pipe:**
    - **Energy** works on a pass-through tunnel, with the far pipe's connection to the tunnel set to extract. On extracting sides Pipez exposes a real energy storage that feeds its network.
    - **Items and fluids need a buffered tunnel** (empty-hand click on the wall: Buffered in or Buffered out), with the far pipe extracting from the tunnel. On extracting sides Pipez exposes only dummy item and fluid handlers, which hold nothing, so a pass-through tunnel between two such pipes has nothing to hand over. See `tiny-tunnels-buffered-tunnels.md`.
  - A block that holds things (tank, chest, Battery Box) directly against the tunnel works in pass-through with no extra setting.

## 1. Client launch and basics (B1, B5)

1. Start the client.
   - [ ] The game reaches the title screen with no crash. The mods list shows Tiny Tunnels `0.1.0+mc1.21.1`.
2. Open the creative tab and look at every item.
   - [ ] All six machines, the Shrinker, Tunnel, Redstone Tunnel and Tunnel Wrench have textures. No purple-and-black squares.
   - [ ] There's **no Room Wall** item in the creative tab or in JEI. Wall blocks have no item form.
   - [ ] The Tunnel Wrench is held like a tool, angled in hand. The others are flat items.
   - [ ] Every name is proper English, not a raw key like `item.tinytunnels.tunnel`.
3. In JEI, look up the recipes (**R** over an item) for the Tiny Machine, Shrinker, Tunnel, Redstone Tunnel and Tunnel Wrench.
   - [ ] They match the recipe table in `tiny-tunnels-phase-7-testing.md`, section 10 (redstone dust, not ender pearls).
4. Switch to survival (`/gamemode survival`) and pick up a redstone dust.
   - [ ] The recipe book unlocks the tiny, small, normal and large machines, the Shrinker and the Tunnel.

## 2. Machines, rooms and the room dimension (B3, B5)

1. Place a Normal Machine and hover it with the Tunnel Wrench or an empty hand.
   - [ ] The machine item's tooltip said "New room" before placing.
2. Right-click the machine with the Shrinker.
   - [ ] You shrink and arrive inside a 7x7 room with glowing grey walls.
   - [ ] The sky is dark (End-style sky) and stays the same. There's no day and night.
3. Right-click the air with the Shrinker.
   - [ ] You're back outside, where you entered from.
4. Break the machine with a pickaxe in survival and pick it up.
   - [ ] It drops itself, and the tooltip says "Room" plus an 8-character id.
   - [ ] Placed again somewhere else, entering it shows the same room with the same contents.
5. **Bed test.** Inside a room, place a bed and right-click it with an empty hand, then while holding a block.
   - [ ] Empty hand: nothing happens, no explosion, and the action bar says "You can't sleep inside a room".
   - [ ] Holding a block: the block is placed against the bed.
   - [ ] Set `roomBedsExplode = true` in `run/config/tinytunnels-server.toml` (a real instance: `config/tinytunnels-server.toml`), then reload the world. Now the bed **explodes**, like in the Nether. That's vanilla 1.21.1 behaviour for a dimension where beds don't work. Set it back to `false` afterwards.
6. **Spawn fallback (no way back).** Run the command below, which builds a room and moves you in *without* a return point:
   ```
   /tinytunnels debug build 900 5
   ```
   Then right-click the air with the Shrinker.
   - [ ] You land at world spawn, and the action bar says "Couldn't find the way back; sent to world spawn".
7. **Loading follows the machine (away from spawn).** Place a machine **at least 300 blocks from world spawn**, outside the spawn chunks, and put something visibly working inside, such as a hopper chain or a Create funnel line. Teleport 3000 blocks away, wait 10 seconds, run the command below, then teleport back.
   ```
   /tinytunnels debug tickets
   ```
   - [ ] While you're away, the room is **not** listed as loaded. The log shows `[TT-DEBUG] machine onChunkUnloaded` and `ticket REMOVE room=…`.
   - [ ] Nothing moved inside while you were away.
   - [ ] Back near the machine, the log shows `machine onLoad` and `ticket ADD`, and the room starts working again by itself.
   - [ ] For comparison, a machine **near spawn** stays loaded while you're away. That's expected on 1.21.1 (see "Spawn chunks" in 6a-inside).
8. **Machine broken while you're inside.** This stands in for another player, a quarry or a Create drill breaking it.
   - **Setup:** a machine with an item tunnel (chest outside, hopper inside feeding the wall) and a redstone **in** tunnel (lever outside, lamp inside, lever on).
   - **Break it from inside:** enter the room, list rooms to get the machine's position (`host: x, y, z in minecraft:overworld`), then break it as if mined. The item drops on the ground outside.
   ```
   /tinytunnels debug rooms
   ```
   ```
   /execute in minecraft:overworld run setblock <x> <y> <z> minecraft:air destroy
   ```
   - [ ] No crash. You stay in the room, and everything inside is still there.
   - [ ] The lamp inside turns off. The hopper stops emptying into the wall, and its items stay in the hopper (nothing voided).
   - [ ] `/tinytunnels debug rooms` shows `host: not placed` for this room.
   - Now use the Shrinker on the air to leave.
   - [ ] You land where you entered from, next to where the machine stood. The machine item lies there, and its tooltip shows the same room id.
   - Pick it up and place it one block over.
   - [ ] Entering it gives the same room. The tunnels work again (hopper empties into the chest outside, lamp follows the lever), with no re-placing inside.
9. **A second copy of the same room is refused.** While the machine from step 8 is placed, get another machine bound to the same room (the id is in `/tinytunnels debug rooms`; click it to fill in the command) and try to place it.
   ```
   /tinytunnels debug give <room-id>
   ```
   - [ ] Placement is refused, and the action bar says "This machine's room is already in use by a machine at …". Only one machine can host a room at a time, so a copy can't be used to dupe it.
10. **Nested: outer machine broken while you're in an inner room.** Put a second machine inside the first room and enter it (you're now two rooms deep). Break the **outer** machine from there, using the step 8 commands with the outer room's host.
    - [ ] No crash. The inner room keeps working while you're in it.
    - [ ] Using the Shrinker takes you out one level at a time: first into the outer room, then to where you entered it from, with the outer machine item on the ground there.
11. **Explosions don't break machines.** Outside, next to a machine, run the command below and stand back.
    ```
    /summon minecraft:tnt ~ ~ ~ {fuse:40}
    ```
    - [ ] The machine survives (it's blast-proof), and its room is untouched.

## 3. Tunnels (B3, B4, buffered tunnels)

### 3a. Pass-through (the default)

A pass-through tunnel hands everything straight to the block on the other side. It needs a block that **holds** things (chest, tank, drawer, Battery Box, hopper) directly against one end. Two Pipez pipes facing each other through a pass-through tunnel move **nothing** for items and fluids (see 3b, step 10). Energy pipes work.

1. Enter a room. Place a Tunnel on a side wall, one block above the floor, and put a **chest** in front of it inside.
   - [ ] The wall shows a coloured port with a letter. The machine outside shows the same port on that side.
2. Outside, point a **hopper** into that machine face, with 64 cobblestone in it.
   - [ ] All 64 end up in the chest inside. The hopper is empty.
3. Inside, point a **hopper** into the tunnel wall (hopper facing the wall). Outside, put a chest against the matching machine face.
   - [ ] Items arrive in the chest outside, with the count unchanged.
4. **Fluids with Pipez into a tank (pass-through).** Same setup as `tiny-tunnels-phase-6a-testing.md`, section 4.
   - **Why not a bucket:** a water bucket can't be emptied into a machine face or tunnel wall. Vanilla buckets place water in the world and never use a block's fluid capability, so that's expected.
   - **Why not a cauldron:** a cauldron only takes or gives a whole 1000 mB at once, so a pipe never fills it. The `fluid_into_cauldron` GameTest covers the cauldron.
   - **Outside:** an Energized Power **Creative Fluid Tank** (the magenta "C" block), connected by a Pipez **Fluid Pipe** to a machine face with a tunnel.
     - **Setting its fluid on 1.21.1:** right-clicking the tank with a bucket does **nothing**. Right-click it with an **empty hand** to open its GUI, pick up a water bucket onto your cursor, and click the tank's fluid bar. The bar shows water once it's set. An empty creative tank moves nothing, which looks exactly like a broken tunnel.
     - **Extract:** sneak + right-click the pipe's **arm** (the connection piece) touching the tank with the Pipez **Pipe Wrench**. A square flange appears on that arm. Sneak-clicking the same arm again turns extraction off **and disconnects** it; sneak-click the pipe's centre to reconnect.
     - **Face match:** the tunnel inside must be mapped to the machine face the pipe touches (Jade on the machine lists the tunnel letters).
   - **Inside:** an Energized Power **Fluid Tank (Small)** touching the matching tunnel.
   - [ ] The small tank fills with water.
   - [ ] Remove the small tank, then put it back. Filling stops, then resumes, with no error in the log.
5. Right-click the tunnel wall with the Tunnel Wrench.
   - [ ] It moves to the next free machine side. The letter and colour change.
6. Sneak and right-click the tunnel wall with the Tunnel Wrench.
   - [ ] The tunnel turns back into a plain room wall, and you get the Tunnel item back.
   - [ ] **Wait 2 seconds.** It stays a plain wall, and the old tunnel doesn't come back. This checks that 1.21.1's `onRemove` path doesn't trigger shell repair on our own edit.
7. Try to break a room wall in survival, and try `/setblock ~ ~ ~ minecraft:stone` on a wall block.
   - [ ] Survival mining does nothing.
   - [ ] The `/setblock`'d block turns back into a room wall within a second.

### 3b. Buffered tunnels

**Done 2026-09-28.**

Buffered tunnels hold a little (1 item stack and 8000 mB by default) so that a pipe that only pushes and a pipe that only pulls can meet through a tunnel. They're one-way. Plan: `tiny-tunnels-buffered-tunnels.md`. Use a tunnel with a Pipez pipe on each side; the Pipez extraction rules are in the setup section.

8. **Mode cycle.** Inside, right-click a tunnel wall with an **empty hand** four times.
   - [x] 1st: action bar "Tunnel set to Buffered in", a click sound, and **yellow notches** at the middle of each edge of the port.
   - [x] 2nd: "Tunnel set to Buffered out", with **yellow corner marks**.
   - [x] 3rd: "Tunnel set to Pass-through", with the marks gone.
   - [x] Right-clicking it while **holding a block** places the block against the wall and doesn't change the mode.
9. **Fluids, pipe → tunnel → pipe, Buffered in.**
   - **Outside:** a Creative Fluid Tank set to water (through its GUI), then a Pipez Fluid Pipe **extracting at the tank**, then the machine face. The pipe's arm at the machine must **not** extract.
   - **Inside:** the tunnel wall (**Buffered in**), then a Pipez Fluid Pipe **extracting at the wall**, then a Fluid Tank (Small).
   - [x] Water fills the small tank.
   - [x] Jade on the wall shows "Buffered in: … mB Water" while it flows.
10. **For comparison: the same setup in Pass-through.** Click the wall until it says Pass-through. (If the buffer isn't empty, it refuses; see step 13.)
    - [x] Nothing flows. That's expected: the pipes have nothing to hand over without a buffer.
11. **Fluids, Buffered out.** Swap the ends: a full tank and a pipe **extracting at the wall** inside, and a pipe **extracting at the machine face** outside into an empty tank. Set the wall to **Buffered out**.
    - [x] Water moves from inside to outside.
12. **Items, both directions.** Repeat 9 and 11 with Pipez **Item Pipes**: a chest of cobblestone at the source end, and a chest or Storage Drawer at the far end.
    - [x] Items arrive in both directions, with the total unchanged.
13. **Back to pass-through while not empty.** With something in the buffer (Jade shows it), click the wall until the next mode would be Pass-through.
    - [x] It refuses with "Empty the tunnel first" and stays buffered.
14. **Active push, no pipe inside.** Buffered in, with a **chest directly against the wall** inside and a hopper outside pushing into the machine face.
    - [x] Items reach the chest with nothing pulling on the inside.
15. **Buckets.** On a buffered tunnel holding at least 1000 mB of water, right-click the wall with an **empty bucket**, then with the **full bucket**.
    - [x] The empty bucket comes back full, and the buffer drops by 1000 mB (Jade).
    - [x] The full bucket empties into the buffer, which goes back up by 1000 mB.
16. **Removal.**
    - [x] With **items** buffered: sneak + wrench removes the tunnel and hands back the items plus the Tunnel item.
    - [x] With **fluid** buffered: the first sneak + wrench says "This tunnel holds … mB of …. Sneak + wrench again to discard it", and the tunnel stays.
    - [x] A second sneak + wrench **within 5 seconds** removes it. The fluid is gone, and any items and the Tunnel item are handed back.
    - [x] Waiting **more than 5 seconds** before the second click only warns again.
17. **Kept across edits and entries.**
    - [x] Wrench-cycle a buffered tunnel to another face: it keeps its mode and contents.
    - [x] Leave the room with the Shrinker and enter again: the mode and contents are still there. The repair pass on entry must not reset them.

## 4. Redstone tunnels (B3)

1. Place a Redstone Tunnel inside a room. Outside, put a lever on the matching machine face. Inside, put a lamp in front of the wall.
   - [ ] The lever turns the lamp on and off. The ports on both sides turn bright red while powered.
2. Right-click the redstone tunnel wall with an **empty hand**.
   - [ ] It flips to **out**: yellow corner marks, and the action bar says "Redstone out to the machine's … side".
3. While holding a block (for example stone), right-click the redstone tunnel wall.
   - [ ] The stone is **placed** against the wall, and the tunnel does **not** flip.
4. With the tunnel on **out**: inside, put a lever next to the wall. Outside, put a lamp against the machine face.
   - [ ] The lever inside switches the lamp outside.
5. With the lamp outside lit, break the machine.
   - [ ] The lamp outside turns off at once, with no stuck-on lamp or dust.
   - [ ] Placing the machine back restores the signal.

## 5. Jade

1. Look at a machine with an item tunnel and a redstone tunnel, at an item tunnel wall, and at a redstone tunnel wall.
   - [ ] The machine shows "Tunnels: …" and "Redstone: N in 15" (or similar). The item wall shows "Links to the … side". The redstone wall shows "Links to the … side: in 15".

## 6. Mod matrix

For each test, check three things. Note the mod and the step if any of them fails:

- **Moves:** things go through the tunnel in the right direction.
- **No void:** the total doesn't drop.
- **No dupe:** the total doesn't grow.

Leave each setup running for at least 30 seconds, because the way `simulate` behaves under repeated push and pull is what this pass is about.

### 6a. Create 6.0.8 (items and fluids; rotation is B8)

> **Paused 2026-09-29 until B8 (the kinetic tunnel) is built.** Create inside a room is mostly tested. Sections 6a, 6a-inside and the Create steps in section 7 get finished in one pass with the kinetic in-game test (`tiny-tunnels-kinetic-tunnel.md`, phase K2). Sections 6b–6e don't depend on it.

Get a Creative Motor from JEI to power anything that needs rotation.

1. **Funnel out of a machine.** Put an Andesite Funnel on a machine face outside, pulling from the machine onto a belt or into a chest. Inside, put a chest with 64 cobblestone behind the matching tunnel.
   - [ ] Items flow out through the funnel. 64 in total, no dupes.
2. **Funnel into a machine.** Put a Funnel on the machine face, pushing from a belt into the machine. Inside, put an empty chest behind the tunnel.
   - [ ] All items arrive inside.
   - [ ] **Fill the inside chest completely.** The funnel stops. No items are lost, and none spill on the ground.
3. **Chute.** Stack a Chute on top of the machine, pointing into its top face, with items fed in from above. Inside, put a chest behind the U tunnel.
   - [ ] Items arrive inside. When the chest is full, the chute holds its items; nothing is voided.
4. **Belt into a machine.** Run a belt straight into a machine face. Put items on the belt.
   - [ ] They enter the machine and land inside.
5. **Mechanical Pump (fluids).** Outside: a water source, then fluid pipe, then a Mechanical Pump driven by a Creative Motor, then pipe, then the machine face. Inside: a Fluid Tank (or a cauldron) behind the tunnel.
   - [ ] Water fills the tank inside. When it's full, the pump stops filling, and nothing leaks or duplicates.
6. **Pull fluid out.** Reverse the pump so it pulls from the machine face. Inside, put a full Fluid Tank behind the tunnel.
   - [ ] Water comes out, and the tank inside drains by the same amount.

### 6a-inside. Create inside the room

This is the reason Tiny Tunnels exists: Create machinery **inside** the room, feeding the tunnel walls.

- **Setup:** every Create block below goes inside the room, attached to or pointing at a **tunnel wall**. Plain chests and tanks go outside, against the matching machine face.
- **Power:** use a Creative Motor inside the room. Rotation can't cross a tunnel yet; that's B8.
- **Loading:** the room only runs while the machine's chunk is ticking. If you walk far enough away that the machine's chunk unloads, the factory inside pauses too. That's by design.
- **Spawn chunks:** 1.21.1 keeps the chunks around world spawn loaded and ticking (`spawnChunkRadius`, default 2). So a machine near spawn **never unloads, and its room runs forever**, however far away you go. That's vanilla behaviour, not a bug. 26.x removed spawn chunks, so it doesn't happen on `main`. Test unloading with a machine away from spawn (step 2.7).

1. **Funnel pulling from a tunnel wall.** Put an Andesite Funnel on the tunnel wall, feeding a belt. Outside, put a chest with 64 cobblestone against the matching machine face.
   - [ ] Items come in from the chest outside onto the belt. 64 in total.
   - [ ] With a filter set on the funnel, only the filtered item comes through.
2. **Funnel pushing into a tunnel wall.** Run a belt into a funnel on the tunnel wall. Outside, put an empty chest against the machine face.
   - [ ] Items arrive in the chest outside.
   - [ ] **Fill the chest outside completely.** The belt backs up and holds its items. Nothing is lost, and nothing drops on the ground.
3. **Chute into the floor tunnel.** Put a Chute pointing down into a **D** (floor) tunnel, with items fed in from above. Outside, put a chest under the machine.
   - [ ] Items arrive in the chest under the machine. When that chest is full, the chute holds its items.
4. **Pump through a wall.**
   - Inside: a Fluid Tank of water, then pipe, then a Mechanical Pump, then pipe, then a tunnel wall.
   - Outside: an empty Fluid Tank against the matching machine face.
   - [ ] Water fills the tank outside, and the tank inside drains by the same amount.
   - [ ] Reverse the pump (wrench it) so it pulls from the tunnel wall, with a full tank outside. Water comes back in.
5. **A small real factory.** Use two tunnels:
   - **Input tunnel:** a funnel pulls from its wall onto a belt that runs under a Mechanical Press (or into a Basin under a Mechanical Mixer).
   - **Output tunnel:** the product goes by belt and funnel into the second wall.
   - **Outside:** a chest of iron ingots on the input face, and an empty chest on the output face.
   - [ ] Iron ingots go in, and iron sheets come out in the outside chest. The counts match (one sheet per ingot).
   - [ ] Leave it running for a minute. It doesn't jam or dupe, and nothing is lost when the output chest fills up.
6. **Save and reload with the factory running.**
   - [ ] After reloading, it carries on by itself. Funnels and pumps reconnect to the tunnel walls without being re-placed.

### 6b. Mekanism 10.7

1. **Energy.** Outside: a Creative Energy Cube, then Basic Universal Cable, then a machine face. Inside: an Energized Power machine or battery (or a Mekanism Basic Energy Cube) behind the tunnel.
   - [ ] Energy fills the block inside.
2. **Logistical Transporter, pushing.** Outside: a chest, then a Basic Logistical Transporter set to pull from that chest (use the Configurator on the chest-side connection), then the machine face. Inside: a chest behind the tunnel.
   - [ ] Items arrive inside, with counts matching.
3. **Logistical Transporter, pulling.** Set the transporter connection at the machine face to **pull**. Inside: a chest with 64 cobblestone behind the tunnel.
   - [ ] Items come out. 64 in total.
4. **Mechanical Pipe.** Pump water into the machine face with a Basic Mechanical Pipe from a Fluid Tank.
   - [ ] Water arrives in the tank or cauldron inside.

### 6c. AE2

Power the network with a Creative Energy Cell and connect it with cables.

1. **Storage Bus on a machine face.** Inside: a chest with items behind the tunnel.
   - [ ] The ME terminal lists the items in the chest inside. Taking items out through the terminal removes them from the chest inside.
2. **Import Bus** on a machine face, with items in a chest inside.
   - [ ] The items move into the ME system. The count matches.
3. **Export Bus** on a machine face, set to export cobblestone. Inside: an empty chest.
   - [ ] Cobblestone arrives inside, and the ME count drops by the same amount.

### 6d. Pipez and Storage Drawers

1. **Pipez item pipe** from a chest into the machine face (pipe set to extract at the chest). Inside: a **Storage Drawer** behind the tunnel.
   - [ ] Items fill the drawer, including past one stack. Drawers have odd slot sizes.
2. **Pipez energy pipe.** Same setup as `tiny-tunnels-phase-6a-testing.md`, section 5. Outside, an Energized Power **Creative Battery Box**, connected by a Pipez **Energy Pipe** (set to extract from the box) to a machine face with a tunnel. Inside, a **Battery Box** touching that tunnel.
   - [ ] The Battery Box charges. Check with Jade.
   - [ ] Reverse it (Creative Battery Box and extracting pipe inside, Battery Box outside). It charges too.
   - Fluids with Pipez are covered in step 3.4.
3. **Pipe loop.** Run a Pipez item pipe from the machine's N face around to its S face, with tunnels on both. Inside, connect the two tunnels with another pipe.
   - [ ] No crash and no freeze. At most one "Tunnel proxy depth limit" warning in the log.

### 6e. Compact Machines 7 (optional)

Compact Machines isn't in `build.gradle` yet. Ask for it to be added, then:

1. Load both mods.
   - [ ] The game starts. Both mods' rooms work, and their dimensions don't clash.
2. Put a Compact Machine inside a Tiny Tunnels room, and route items to it through a tunnel.
   - [ ] Items reach the Compact Machine's side.

## 7. Save and reload

1. Leave a setup from section 6 running, save and quit to the title screen, then load the world again.
   - [ ] Rooms, tunnels (letters and colours), redstone tunnels (mode and stored signal) and their contents are all still there.
   - [ ] Pipes reconnect to the machines on their own, without breaking and re-placing anything.
   - [ ] The Shrinker still returns you to the right place from inside a room.

## Notes

-
